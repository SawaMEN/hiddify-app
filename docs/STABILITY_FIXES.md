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
