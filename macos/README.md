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

## Native profile and system integration milestone

- `VPNController` can load existing matching `NETunnelProviderManager` entries,
  install a single system VPN configuration and observe status changes.
- `ProfileSource` currently validates **only** JSON structure or known URI
  prefixes. It does not parse/convert subscriptions or prove core support.
- `ProfileStore` stores only nonsecret metadata in UserDefaults. Raw URLs,
  subscription tokens, JSON credentials and secrets live in Keychain.
- The Connect button remains disabled by `isCoreReady == false` until
  packet forwarding and the Go lifecycle are integrated. Installing the
  VPN preference entry is not the same as connecting.
- The ARM64 workflow also compiles native unit tests for source validation.

Swift 6 strict concurrency: the Apple NetworkExtension SDK exposes some
async Objective-C methods returning non-Sendable manager objects. The native
controller uses `@preconcurrency import NetworkExtension` pending upstream
SDK annotations; confinement to `@MainActor` is maintained throughout.

## macOS menu bar and persistence

The app and the menu bar share a single `VPNController` instance to avoid
multiple independent connections or competing observers. The selected profile
UUID persists in UserDefaults (the profile content remains in Keychain).
The menu bar currently shows VPN status and permits disconnect; it does not
claim the unavailable core is connected.

The CI job runs (not just compiles) native Swift source-format tests on the
ARM64 macOS runner. Network Extension signing and actual VPN traffic are
separate physical-device acceptance gates.

## Shared Keychain boundary

Both signed targets (app and Packet Tunnel) request
`$(AppIdentifierPrefix)app.vetroff.client.macos.shared` through
`keychain-access-groups`. The Info.plist expands the same group for Keychain
queries. Unsigned builds can compile but cannot exercise the shared Keychain:
an Apple provisioning profile and real device test are required.

The system tunnel start contract is an opaque `profileID` UUID only.
The extension resolves credentials locally from Keychain. It intentionally
refuses startup until the libbox packet engine is wired, and it never stores
configuration secrets in Network Extension preferences or connect options.
Existing experimental default-group Keychain items require an explicit
migration; never assume they are readable under the shared group.

## Verified direct native core smoke-test

`macos/Integration/CoreSmoke.swift` imports the generated
`HiddifyCore` Objective-C/Swift module and calls the forked
`MobileTest()` function in-process. The separate core workflow now
runs the resulting Apple Silicon executable after building the
`macos/arm64` XCFramework and before uploading it. The core artifact
is not bundled with the regular lightweight UI-only CI yet; no user
tunnel connection is advertised by this smoke test.

**Signing hardening:** The Keychain group is now read from the actual
`keychain-access-groups` code-signing entitlement using the public Security
framework, rather than assuming Xcode expands a value in Info.plist. A
missing/unprovisioned group fails closed; neither target silently falls back
to an unshared Keychain.

### ARM64 core artifact optimization

Native Core CI now builds only `macos/arm64`, with `-s -w`
stripping unnecessary debug/linker symbols and `-macosversion=14.0`.
This is a **build size** optimization, not a claim of runtime speed
improvement. The heavy Go framework workflow runs only when its
build script or workflow changes (or manually), rather than rerunning
on every UI change in the draft PR.

The core is a **static** XCFramework. A standalone Swift linker must also
link the Apple frameworks used by Chromium/Cronet inside the core (AppKit,
CoreText, Network, SystemConfiguration, UniformTypeIdentifiers, and related
system frameworks) plus `libresolv`; Xcode must carry these linkage settings
when the core becomes a target dependency. The smoke job verifies this list.

## Optional direct in-extension Go linking

Place the CI-built framework at `macos/Core/HiddifyCore.xcframework`
(or symlink that path to the artifact), then run:

```sh
bash macos/scripts/generate-xcode-project.sh
xcodebuild -project macos/VetrOFF.xcodeproj -scheme VetrOFF \
  -configuration Debug -destination 'generic/platform=macOS' \
  ARCHS=arm64 CODE_SIGNING_ALLOWED=NO build
```

The generator adds the **static** HiddifyCore only to `PacketTunnel` and
sets its required Apple framework linker flags. No Go runtime or additional
networking process is linked into `VetrOFF.app` itself.

`NativeCoreProbe` is compile-time optional; it tests that the bundled
forked Go module can be reached from Swift. A working VPN connection still
requires the callback adapter, native TUN file descriptor and real traffic
verification before the disabled Connect button is unlocked.

For Chromium/Cronet's macOS Mach audit-token APIs the static link must also
include `libbsm` (`-lbsm`). This is a system library, not another process or
runtime networking layer.

## Native TUN routes

The embedded libbox OpenTun callback now maps explicitly supplied IPv4/IPv6
included and excluded routes into NetworkExtension without flattening them
into unconditional default routes. If the Go engine requests auto-route but
no explicit routes, it installs the protocol-family default route.
This preserves the network-routing control plane and avoids per-packet
Swift translation. macOS application-level routing rules need separate
platform-specific design and testing; Android per-app rules are not copied.
