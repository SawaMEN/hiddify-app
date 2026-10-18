# VetrOFF macOS (native Apple Silicon)

This directory is an **independent Swift 6 / SwiftUI macOS client**.
It does not build, replace or change the existing Android Kotlin client.
The default scope is **macOS 14+ / arm64**.

## Current stage: in-extension core integration (not traffic-verified)

Implemented:
- Native Swift 6 / SwiftUI ARM64 macOS app, menu bar and diagnostics.
- Real Network Extension Packet Tunnel target with native Go/libbox
  PlatformInterface callbacks, Go lifecycle management and utun descriptors.
- Static XCFramework built from a pinned SawaMEN/hiddify-core revision,
  linked inside the extension rather than in the SwiftUI app.
- Native IPv4/IPv6/DNS/MTU settings, included/excluded routes, network
  interface enumeration, Keychain-backed profiles and status diagnostics.
- Cancellation-aware Go startup, stop cleanup and ARM64 CI compilation/tests.

Still requiring on-device verification or implementation:
- Signed runtime test with real TCP/UDP packets and IPv4/IPv6/DNS leak checks.
- Subscription URL fetching/refresh and full format/profile compatibility.
- Fail-safe DNS and IPv6 behavior, default-interface changes, sleep/wake.
- Production signing, distribution, notarization and complete test coverage.

An integrated **Debug-only** client exposes an explicitly labelled manual
connection test for local JSON profiles. The regular project and Release
remain gated. A passing CI build is **not** evidence of a working VPN.

## Architecture

```text
VetrOFF.app (SwiftUI; Swift 6)
  └─ Native app services (Foundation / Keychain / NetworkExtension)
       └─ NETunnelProviderManager
            └─ PacketTunnel.appex (NEPacketTunnelProvider)
                 └─ direct in-process Go/libbox bridge (no proxy daemon or gRPC hop)
                      └─ SawaMEN/hiddify-core (sing-box-based protocol engine)
```

A generated gomobile Objective-C bridge or an explicit C ABI is the one
necessary language boundary for the Go engine; it is not a separate
networking process. The engine **must run inside the extension** for
system packet tunneling. Implement a tested `libbox.PlatformInterface`
adapter for the native packet flow and socket protection; confirm
packet forwarding on a signed test device before enabling Release controls.

The companion [SawaMEN/hiddify-core](https://github.com/SawaMEN/hiddify-core)
can build a desktop C shared library or the **static macOS/arm64
XCFramework used here**. The desktop C ABI alone does not provide the
libbox platform callback required for a Network Extension. The pinned
mobile-binding XCFramework is built and link-tested by GitHub Actions
against the extension; physical-device operation is a separate gate.

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
- The normal app / Release Connect button remains disabled. Only the
  integrated Debug test exposes manual connection for JSON profiles.
  Installing a VPN preference does not prove packet forwarding.
- ARM64 CI builds and **executes** unit tests for source, routing,
  diagnostics, cancellation and lifecycle behavior.

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

## Signing-only manual integration test

The **integrated Xcode generator** adds the internal compile condition
`VETROFF_NATIVE_CORE_TESTING` to the SwiftUI app. With `DEBUG` and that
condition, the home screen permits an explicitly labelled **test** connection
for local **JSON profiles only**. It passes the selected UUID through
`NETunnelProviderManager.connection.startVPNTunnel(options:)`.

The normal lightweight project and every Release configuration do **not**
expose the test connection. The real Go OpenTun callback must return a valid
utun descriptor after macOS network settings have been applied; failures
are reported to NetworkExtension instead of claiming success.

This is not an end-to-end-verified VPN. Install a signed macOS build with
the Network Extension and shared Keychain entitlements on an actual Apple
Silicon Mac, approve the VPN configuration, and independently validate
TCP, UDP, DNS, IPv6, sleep/wake, network switches and leak behavior.

## On-device diagnostics without an RPC daemon

The Diagnostics screen uses Apple's built-in
`NETunnelProviderSession.sendProviderMessage` / `handleAppMessage`
channel. The extension accepts only the exact fixed `VETROFF_STATUS_V1`
request and returns a version, whether the linked Go framework responds,
and the in-process lifecycle state. It never returns profile contents,
credentials, server addresses or subscription URLs.

The `trafficVerified` flag intentionally remains false until a signed
physical-Mac acceptance test proves actual packet forwarding. These
diagnostics **do not** establish an active or private internet connection.

## Next physical-Mac milestone

Follow [ON_DEVICE_TESTING.md](ON_DEVICE_TESTING.md) on an Apple Silicon
Mac to sign both targets, approve VPN permissions and explicitly measure
packet routing, DNS/IPv6 behavior and sleep/wake. Do not mark the Release
connection enabled until those results are reviewed.

### macOS-specific forked Go TUN activation

Pinned `hiddify-core` revision `5bcb778cbe1de37f46e85bd194cbbadc3ad5194d` comes from
[SawaMEN/hiddify-core PR #8](https://github.com/SawaMEN/hiddify-core/pull/8).
The standard `mobile.Start` and default `EnableTun=false` remain unchanged
for Android. The extension instead calls `MobileStartPacketTunnel`, which
explicitly enables the TUN inbound, strict full routes and encrypted DNS
bootstrap, while disabling local SOCKS/mixed/Clash listeners and system proxy.

After Go returns, `NativeTunnelEngine` independently verifies that its
`CorePlatformInterface.OpenTun` callback acquired a real utun FD. Returning
success without the callback is treated as a failed connection. This is a
required *startup invariant*, not proof that TCP/UDP forwarding works on a
signed Mac.

The core CI artifact now contains the pinned Go commit prefix in its filename.
The integrated Swift-Go build refuses to download an artifact from the
old revision, preventing a false-positive integration test.
