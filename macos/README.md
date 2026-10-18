# VetrOFF macOS (native Apple Silicon)

This directory is an **independent Swift 6 / SwiftUI macOS client**.
It does not build, replace or change the existing Android Kotlin client.
The default scope is **macOS 14+ / arm64**.

## Current stage: bootstrap, not a working VPN

Implemented:
- Native SwiftUI macOS application, navigation and diagnostics shell.
- A real macOS Packet Tunnel app-extension target and signing entitlements.
- Isolated ARM64 macOS CI build, independent of Android workflows.

Not implemented:
- Native libbox/Go bridge for `NEPacketTunnelFlow`.
- VPN profile manager, Keychain-backed profile store and subscription import.
- DNS/routing, reconnect/recovery, menu-bar UI, signed DMG and notarization.

The extension deliberately **fails startup** until packets are actually
forwarded. Do not ship or advertise this stage as a working VPN.

## Architecture

```text
VetrOFF.app (SwiftUI; Swift 6)
  └─ Native app services (Foundation / Keychain / NetworkExtension)
       └─ NETunnelProviderManager
            └─ PacketTunnel.appex (NEPacketTunnelProvider)
                 └─ direct Go/libbox bridge (future, no proxy daemon or gRPC hop)
                      └─ SawaMEN/hiddify-core (sing-box-based protocol engine)
```

A generated gomobile Objective-C bridge or an explicit C ABI is the one
necessary language boundary for the Go engine; it is not a separate
networking process. The engine **must run inside the extension** for
system packet tunneling. Implement a tested `libbox.PlatformInterface`
adapter for the native packet flow and socket protection before enabling
connection controls.

The companion [SawaMEN/hiddify-core](https://github.com/SawaMEN/hiddify-core)
contains `make macos-arm64` for a desktop shared library and
`ios-full` for a Go mobile XCFramework. These are not interchangeable:
the desktop C ABI does not implement `NEPacketTunnelFlow`. The current
published core release does not include a macOS binary. The next stage
must validate a macOS libbox build and the Packet Tunnel integration.

## Generate and build

Requires a Mac running Xcode 16+ and [XcodeGen](https://github.com/yonaskolb/XcodeGen).
XcodeGen is a **build-time only** project generator; it adds no dependency
to the compiled application.

```sh
brew install xcodegen
xcodegen generate --spec macos/project.yml --project macos
xcodebuild -project macos/VetrOFF.xcodeproj \
  -scheme VetrOFF -configuration Debug -destination 'generic/platform=macOS' \
  ARCHS=arm64 CODE_SIGNING_ALLOWED=NO build
```

Unsigned CI builds validate compilation only. Running a real tunnel
requires an Apple development team, proper Network Extension entitlements,
signing and explicit macOS VPN permission.

## Engineering rules

1. Never edit, delete or reformat `android/`. macOS workflows must not
   call Android build steps.
2. The app UI is SwiftUI; use Foundation, Security and NetworkExtension
   rather than Flutter, Dart, Kotlin or Electron.
3. Keep the Go engine as the sole protocol implementation: no duplicate
   Swift TLS/proxy stacks, no localhost RPC/proxy hop in the packet path.
4. Do not report connected before `setTunnelNetworkSettings` succeeds
   and the real packet-forwarding loop is active.
5. Keep credentials out of UserDefaults, logs, process arguments and
   plaintext shared containers; use Keychain and carefully scoped
   extension sharing.
6. Add connection, DNS leak, IPv6, sleep/wake, Wi-Fi transition, crash
   recovery, cancellation and subscription compatibility tests before
   the first release.
7. Verify protocol/transport parity *against the pinned forked Go core*;
   available build tags alone do not establish runtime support.

### Compatibility targets

- Preserve VetrOFF's regional routing, profile formats, server selection,
  privacy options and diagnostics where macOS exposes suitable APIs.
- Document platform-specific gaps such as Android per-app VPN and hotspot
  sharing instead of silently presenting unsupported toggles.
- Preserve license notices and review distribution obligations before
  packaging releases.
