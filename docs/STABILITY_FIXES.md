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

Dart async runtime checks and six Python tooling tests pass. Flutter 3.47.6 and
Dart 3.13.5 were obtained for checking. The initial Flutter bootstrap attempted
cloud metadata detection; setting CI=true avoids that request, as verified in
Flutter's BotDetector source. SDK cache initialization subsequently failed to
extract the Gradle wrapper download. Full Flutter analysis, widget tests and APK
assembly must be checked by CI when dependencies and Android artifacts are ready.

## Remaining evidence required

Static fixes and passing unit tests cannot establish that every observed crash on
all devices is eliminated. Device verification is still needed for rapid repeated
connect/cancel/disconnect, network transitions, sleep/Doze, Always-on/lockdown,
root mode and Android low-memory pressure. Native ANR/SIGSEGV and device-specific
failures need the corresponding exit reasons or logcat/tombstone. A stuck native
component can still exceed the existing shutdown deadlines; returning its error
makes this visible but does not safely kill arbitrary Go goroutines.
