# Component update

This update preserves SawaMEN's application and networking extensions.

| Component | Selected version |
| --- | --- |
| Flutter / Dart | 3.47.6 / 3.13.5 |
| Material / Cupertino UI | 1.5.0 / 1.1.1 |
| dynamic_color | 2.1.0 |
| Gradle | 9.8.0, distribution checksum verified |
| Android Gradle Plugin / Kotlin | Existing 9.4.1 / 2.4.20 |
| Android NDK / build tools | 30.0.16248370 / 37.0.0 |
| Go | 1.26.8 |
| gomobile | 0.1.13 |
| gRPC / Go protobuf | 1.84.0 / 1.36.12 |
| QUIC / uTLS / DNS | 0.63.0 / 1.8.8 / 1.1.73 |
| browserify / google-protobuf / grpc-web | 17.0.1 / 4.0.3 / 2.1.1 |
| protobufjs-cli | 2.7.0 |

Other direct Dart packages were already current at the time of the audit.
The application imports standalone Material/Cupertino packages and bridges
themes/localizations for third-party widgets still using Flutter's legacy UI.

## Core and Android builds

The nested sing-box fork includes the remaining fixes from upstream v1.14.2.
It retains the fork's newer APIs and features instead of replacing the fork
with a vanilla release. The existing compatible sing and sing-tun snapshots
remain pinned; sing-quic includes the October 2 fixes.

Go 1.27.1 crashes during initialization of the current Psiphon TLS fork because
its TLS connection-state structure differs from the standard library.
Go 1.26.8 is the tested patch release for this fork.

Android builds compile the recursively pinned hiddify-core sources rather than
download an unrelated official draft AAR. Install SDK platforms 24 and 37.0,
build tools 37.0.0 and NDK 30.0.16248370. Platform 24 is used by gomobile's
packaging tools. The app retains target SDK 36 until its Android behavior
changes can be verified on a device.

`make android-libs` builds the ARM64 AAR and writes its source commits, dirty
flags, Go version and SHA-256 to `hiddify-core.provenance.json` beside it.
CI uploads that provenance as a separate artifact.

## Validation

Go tests for `./v2/... ./platform/mobile/...` passed on Go 1.26.8 with
`-ldflags=-checklinkname=0`. Tagged config/core/mobile tests also passed with
gVisor, QUIC, WireGuard, uTLS, Clash API, gRPC and AWG enabled.
The complete ARM64 AAR also built successfully with all production build tags,
NDK 30 and JDK 17; `make android-libs` copied it and wrote its provenance.
The tests reject empty/DNS-only/hidden-only proxy profiles and use a local
HTTP server for profile imports.

`npm ci --ignore-scripts`, JavaScript runtime loading and a protobuf binary
round trip passed. Rebuilding the existing browser bundle remains blocked
by missing generated `extension_grpc_web_pb.js` sources in the repository.
The checked-in bundle is unchanged.

Flutter dependency resolution, analysis, widget tests and APK assembly have
not been verified: automatic security review blocked SDK bootstrap after a
request to cloud metadata address 169.254.169.254. That rejected operation was
not retried. The Dart lockfile was updated from published package metadata;
it still needs validation with the SDK.
