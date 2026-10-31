# VetrOFF native update against Hiddify 5.3.0

Reference: `hiddify/hiddify-app` commit `5919a4f7523793a69da665c7aaa6e2f0da7b7842` (2026-10-31).

This project is intentionally **not** a source mirror of the Flutter desktop/mobile app:
Android uses Kotlin/Jetpack Compose and macOS uses Swift/SwiftUI with Network Extension.
The current native applications do not build the original Flutter `lib/` tree.

## Relevant upstream changes and native adaptation

| Upstream 5.3.0 change | VetrOFF implementation |
| --- | --- |
| Explain connected VPN with an unavailable selected proxy | `NativeConnectionPathPolicy` on Android, using **actual** selected delay/probe results. Shows a non-blocking warning linking to the existing connection path sheet. No false claims about unobservable chain hops. |
| Connection health sheet and staged checks | Native Compose connection path sheet from PR #100, and existing detailed native diagnostics. |
| WARP named noise presets | Native Compose selector with the upstream preset names, Russian/English labels, and **lossless retention** of old custom settings. |
| Open full profile editor promptly | Native Kotlin `NativeProfileDetailsScreen` already has a full content editor; the Flutter editor and Riverpod code are not imported. |
| macOS connection / gRPC lifecycle changes | VetrOFF has its own SwiftUI/Network Extension controller and separate core. Flutter/macOS gRPC patches must be reviewed for applicability in those native components, not copied verbatim. |
| Windows Store / TestFlight / iOS-specific work | Not applicable to this Android + macOS native fork. |

## Core compatibility

Android release builds intentionally fetch and verify binaries from **SawaMEN/hiddify-core**.
At the time of the audit, its latest stable release was
`sawamen-3d4ee155c924-244e333149ec-panel9` (2026-10-19).
The original project's `hiddify/hiddify-core` stable version is **5.3.0** (2026-10-31).

**Do not** replace the fork's verified `.aar` or the macOS PacketTunnel framework
with an unrelated upstream binary: VetrOFF's custom VPN protocols and native interfaces
can depend on fork-specific changes. This update changes only the UI options and
presentation logic. Native core upgrades require a separately verified fork release,
API compatibility checks, and signed artifacts.

## CI and release

- Native Kotlin policy tests cover the named modes, custom values, and measured-only warnings.
- SwiftUI, Go core tests, and Android release APK verification remain in existing Actions.
- Concurrent runs for the same PR/branch are now superseded on the macOS and native-core workflows.
- The development app version is 1.1.3; this does **not** by itself publish a signed stable release.

A green core connection status is not proof of reachability through a chosen proxy or chain.
The native UI intentionally does not fabricate telemetry unavailable from the core.
