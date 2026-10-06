# VPN stability fixes

This branch addresses the source-level defects found in the 2026-10-06 audit of
SawaMEN/hiddify-app at 3ba0e9d5956f57cd165a0e1f1e33c475ddc1ec3b. It includes
coordinated changes in the app, hiddify-core and hiddify-sing-box submodules.

## Correctness and lifecycle

- Normalize stored URI, Clash and JSON profiles when building the runtime config;
  preserve original editable files. Respect full-config parsing and reject trailing
  JSON. Single outbound objects and JSON arrays no longer fail or self-reference.
- Return Setup success without dereferencing a nil error. Publish the gRPC server
  only after binding its socket; synchronize registry access and capture the server
  in its serving goroutine. Keep mTLS CA updates synchronized.
- Convert RPC and close-worker panics to errors. Return actual errors from mobile
  Close and configuration conversion; configuration validation no longer stops the
  active VPN. Remove artificial five-second waits after panic recovery.
- Synchronize core state and settings publication. Validate settings before saving;
  failed settings updates retain the previous configuration. Use immutable settings
  snapshots for builders and atomic log/debug flags.
- Serialize restart across stop, Android delay and start under one lifecycle lock.
  A later stop invalidates its generation and cancels it without waiting for the lock.
- Cancel pending startup before waiting for the lifecycle lock. Observe request
  cancellation during delays and native startup; detach successful service lifetime
  from the request. Reject a queued start invalidated by a subsequent stop.
- Close StartedService observers after shutdown and failed initialization. Propagate
  close errors and continue cleanup after an extension service reports an error.
- Enable the connection button during Connecting so the user can cancel.
- Preserve connection intent during internal setup and failed-start cleanup; explicit
  user stop still clears it. Keep stream retries on a slow schedule after the first
  recovery budget is exhausted and rebuild consumers after core restart.
- Check internet and external IP using dedicated RPCs through the selected outbound,
  including when privacy settings disable the local proxy. Cancel obsolete probes,
  restart after URL/foreground/server changes, reject captive-portal redirects and
  apply a twelve-second deadline.
- Use effective profile bytes and overrides to distinguish configuration changes
  from metadata/304 updates before reconnecting.
- Root boot recovery does not request Android VpnService permission.
- Await asynchronous platform results inside error handlers so native URL launch
  failures return false instead of escaping as unhandled errors.

## Settings usability and attribution

- Remove the routing region selector from onboarding and the legacy privacy page.
  Do not read the saved region or import/export it as an editable preference;
  keep a neutral value only for protocol compatibility. Language selection uses
  the existing device-locale preference instead of country/IP detection.
- Replace historical Russia/Other terminology with a direct explanation of which
  domains and IP addresses use the VPN.
- Organize additional privacy settings into Routing, Connection mode, DNS and
  Proxy/control categories. Each category has a stable key and independent
  expansion state, preserving the open section while preferences are applied
  and the progress indicator appears.
- Identify VetrOFF Client as an independent Hiddify fork in About, with separate
  links to the fork and upstream source. Keep the header flexible on narrow screens.

## Resource use

- Apply a finite Android Go soft-memory budget based on memoryClass, clamped to
  128–512 MiB before the core's 3/4 reserve. This is a Go-runtime soft budget, not
  a total process-memory cap or an Android low-memory-killer guarantee.
- Repair outbound-monitor ticker synchronization, atomic cycle access, missing
  dependency handling and permanently suppressed tests after queue saturation.
  Publish history snapshots instead of mutable state pointers.
- Share the unsorted proxy stream with server selection. Sort only for the visible
  foreground UI, without copying all outbound messages.
- Publish at most one log snapshot per 250 ms; bound retained logs by both count
  and bytes and truncate exceptionally large messages. Batch file flushes while
  flushing error records immediately and closing the sink on disposal.
- Debounce text JSON parsing and move it to an isolate. Keep Save disabled until
  the latest edit is validated. Clone tree values directly without encode/decode.
- Reject configurations exceeding 8 MiB in file import, repository validation and
  core readers, using bounded reads in Go.
- Replace preferences.all copies with typed getters and retain corrupt-type fallback.
  Cache the offline routing catalogue and memoize package UID lookups per policy.
- Read the current profile name from the running request snapshot instead of the
  database on each statistics poll.
- Save recent Android process-exit reasons, status and memory figures locally;
  omit configuration, IP addresses and crash traces from this diagnostic file.

## Validation

Go 1.26.8 (the repository version):

```sh
go test -race -ldflags=-checklinkname=0 -p=2 ./v2/config ./v2/hcore ./v2/service_manager
# In hiddify-sing-box:
go test -race -ldflags=-checklinkname=0 -p=2 . ./common/monitoring ./daemon
```

The regression suites cover parser shapes/full-config preservation/trailing JSON,
size limits, transactional settings, failed socket binding followed by retry,
Setup success, cancelled startup, RPC panic recovery, saturated monitoring queues,
close-worker panic recovery and extension cleanup. The real-service lifetime test
requires netlink sockets; it skips only when the runtime prohibits them (EPERM).

Flutter 3.47.6 / Dart 3.13.5: all 134 Flutter tests pass locally. The three
focused editor/recovery suites also pass (13 tests). GitHub CI confirmed the preceding 131-test suite, Dart analysis and an ARM64
APK build. The final 134-test suite and APK build with the settings redesign are
tracked in the application pull request. Local analysis reports
no errors or warnings. Dart async runtime checks and six Python tooling tests
pass. Go race tests pass for the core packages listed above and for sing-box's
root, monitoring and daemon packages. The pinned Android native core builds in
CI; final ARM64 APK validation is tracked in the application pull request.

The editor tests cover preservation of unvalidated text during format/mode
changes and disabling Save until isolated parsing completes. The recovery tests
cover continued thirty-second retries after the initial budget and cancellation
on listener disposal. A core regression test covers invalidation of a queued
restart by a later stop. UI regressions cover open categories during settings
application, retired region defaults, and fork attribution on a narrow screen
with large text.

## Remaining evidence required

Static fixes and passing unit tests cannot establish that every observed crash on
all devices is eliminated. Device verification is still needed for rapid repeated
connect/cancel/disconnect, network transitions, sleep/Doze, Always-on/lockdown,
root mode and Android low-memory pressure. Native ANR/SIGSEGV and device-specific
failures need the corresponding exit reasons or logcat/tombstone. A stuck native
component can still exceed the existing shutdown deadlines; returning its error
makes this visible but does not safely kill arbitrary Go goroutines.

## Connection startup regression: indefinite control streams

Replacing mobile control channels previously awaited `ClientChannel.shutdown()`.
That graceful operation waits for active RPCs to finish, while status/statistics
streams intentionally have no deadline. A subscriber can open a background RPC
as soon as Android reports Started, before setupBackground replaces the channel.
The replacement then waits indefinitely and never sends the core Start RPC.
Warm setup and disposal had the same failure mode.

Use `terminate()` for persistent control channels during replacement and disposal
so ongoing RPCs are cancelled and recovery listeners can use the new client. The
short-lived completed Hello request still uses graceful shutdown. Socket-backed
regressions exercise an event RPC opened during native startup, foreground
replacement and disposal with an active RPC. The regression fails by timeout with
graceful shutdown and completes with termination. Device confirmation of the
reported connection failure still requires the updated APK.

## Relaunch recovery: reuse a live connection

Recovery now probes the native service and daemon inside the connection-operation
queue, immediately before any stale-service stop. A connect that completed while
resume was waiting is reused. Concurrent resume/timer recovery attempts are
coalesced. Manual start also checks for an existing Android core before starting.
A missing control-channel reply is represented as unknown, separately from a
confirmed Stopped response; unknown/Stopping cores are observed again without
stopping their service.

The successful profile signature is retained in preferences across UI process
restarts. A metadata-only subscription refresh no longer restarts a profile whose
bytes and override are unchanged. Failed starts do not overwrite the persisted
successful signature. Queued automatic profile refreshes recheck the signature
before restarting so a burst of identical refreshes produces one restart.

Regression tests cover live/starting/stopping/unreachable cores on resume,
concurrent resume requests, a queued resume behind an in-flight connect, confirmed
stale-service recovery, duplicate queued profile refreshes, and signature retention
across repository recreation and failed startup. Device confirmation remains
necessary for the reported intermittent relaunch behavior.

The core-service provider is a global lifecycle owner, matching the native core.
Its previously declared scoped dependencies rejected coordinator reads in debug
mode and made resume recovery fail before it could query the daemon. The focused
recovery tests exercise these reads with native/core adapters rather than
bypassing Riverpod's dependency checks.

## Restored branding and independent Android installation

- Restored the earlier VetrOFF artwork (metallic five-blade fan, blue/grey ring,
  VPN shield and VetrOFF wordmark) from the original image, with alpha transparency.
  The built-in image editor removed the background and extracted separate static
  frame and moving blade layers; platform assets are resized from that artwork.
- Startup rotates only the blades once per second, four times faster than before.
  The ring, shield and wordmark stay still. Reduced motion, background pause and
  immediate handoff after initialization remain supported.
- Android application ID is now `app.vetroff.client`, separate from upstream
  `app.hiddify.com`. The native Kotlin namespace stays `com.hiddify.hiddify`.
  The shortcut targets the fork; FileProvider uses the application ID; service
  actions use the installed package at runtime, including privacy-repacked copies.
- CI checks the actual APK package, provider authority, native activity and
  shortcut, as well as version, minimum SDK and architecture.
- Android treats this as a separate installation with its own data. Before
  switching from the old package, export/share profiles and import them in the
  new installation. Installing the fork does not uninstall or overwrite Hiddify.
  Android permits only one active system VPN at a time.

Brand edit prompt: faithfully extract the original VetrOFF logo with actual
transparent alpha; preserve its metallic fan, split blue/grey ring, wind trails,
VPN shield and wordmark; extract stationary frame and centered five-blade rotor
for animation without redesigning the brand.

## Modern protocol allow-list and adaptive network mode

Two independent switches, both off by default:

- **Скрытие VPN → Только протоколы с маскировкой**: verified TLS AnyTLS and
  Naive TCP by default; a separate opt-in UDP switch adds Hysteria 2, TUIC and
  Naive HTTP/3. VLESS/VMess, opaque nested configs, endpoints and detours are
  excluded. TUIC 0-RTT and Naive insecure concurrency are disabled; AnyTLS uses
  Chrome uTLS. Empty eligible sets fail closed without retry or legacy fallback.
  WARP chains are rejected. Subscription obfuscation/passwords remain unchanged.
  This is a conservative device policy, not a guarantee of DPI concealment.
  See [protocol research and complete build inventory](PROTOCOLS_RUSSIA.md).
  Intentional direct-routing exceptions remain separate.
- **Дополнительные настройки → Режим подключения → Адаптация к нестабильному
  интернету** (also in general settings): automatic server selection/recovery,
  sticky sessions for up to three minutes and no interruption of existing streams
  on selection updates. New flows avoid nodes with failed health samples. Core
  monitoring uses the configured endpoint once per minute. Hysteria 2 uses
  conservative BBR rather than fixed subscription Mbps; TUIC uses BBR with 0-RTT
  disabled. Dial and TLS budgets allow up to 30 seconds, and the TUN MTU is reduced
  where compatible with the transport. Stored profile options are never rewritten.
- Health probes adjust their deadline from 12 to 30 seconds based on recent
  response times/timeouts, require five failed checks before considering recovery,
  and pause while the physical network is absent. Recovery polls for a returning
  network without consuming its bounded retry budget; Stop cancels pending work.
- Both device switches override profile values and are applied during native
  Always-on/root starts even when the Flutter UI is not running. Disabling a mode
  restores the original profile options on the next configuration application.
- Core and Flutter regression tests cover mixed subscriptions, TLS rejection,
  hidden/fallback nodes, WARP conflicts, immutable profiles, longer budgets,
  non-interrupting balancers, offline waiting, cancellation and UI persistence.

References: https://sing-box.sagernet.org/configuration/outbound/hysteria2/,
https://sing-box.sagernet.org/configuration/outbound/tuic/,
https://v2.hysteria.network/docs/advanced/Full-Server-Config/.
