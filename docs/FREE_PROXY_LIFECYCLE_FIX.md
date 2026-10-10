# Free proxy lifecycle fix (2026-10-10)

The upstream free-profile catalogue currently exposes one combined WARP/Psiphon subscription for the `other` region. Other catalogue entries target Iran. The number of catalogue entries is independent of the VPN lifecycle bug.

## Changes

- WARP readiness and dialing no longer wait for Cloudflare registration. The endpoint mutex protects only its published state.
- WARP close cancels bootstrap, waits for the cancelled worker, and prevents a tunnel from being published after shutdown. Registration across fallback dialers is bounded to 90 seconds.
- Invalid WARP peer/port lists are rejected instead of panicking in the bootstrap goroutine; registration errors are retained without an outbound manager.
- Direct Android Psiphon uses its existing DeviceBinder instead of forcing an HTTP CONNECT bridge. This preserves QUIC and inproxy choices; explicit detours and platforms without a device binder retain the protected core dialer bridge.
- The Android startup watchdog uses the same repeated cancellation path as manual disconnect, covering Stop arriving before Go installs the startup cancellation context.
- App CI includes WARP lifecycle race regressions as well as the existing Psiphon tests.

## Validation

- On the original core, `TestH_WARPReadinessAndCloseDuringBlockedRegistration` fails because readiness/dial blocks behind registration.
- On the original core, `TestH_PsiphonDirectAndroidBootstrapDoesNotForceHTTPProxy` fails because direct Android bootstrap creates an HTTP bridge.
- Both protocol test suites pass with `go test -race -tags with_wireguard ./protocol/warp ./protocol/psiphon -count=1` using the pinned Go 1.26.8 toolchain. Tests use fake dialers; they do not establish external tunnels.
- `python3 tool/check_native_project.py` and whitespace checks pass.
- Full Box/WireGuard integration cannot run in this container: creating a netlink socket is denied. Local Kotlin JVM tests cannot fetch the Gradle distribution through the Java wrapper in this environment. Those checks remain for CI.
- Actual WARP/Psiphon connectivity on an Android device and on the affected network is not verified. The fix removes confirmed lifecycle/transport defects; provider availability remains a separate runtime condition.
