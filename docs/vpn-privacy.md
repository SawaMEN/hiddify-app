# VPN privacy (Android)

The bottom Proxy destination is replaced by VPN privacy. Tapping the active server card opens server selection in a modal sheet. The connection power button retains connect/disconnect behavior.

Device policy is stored separately from subscription settings and is applied after profile overrides. Changes recreate the Android VPN service so addresses, DNS, application exclusions and proxy information are updated together. The native Always-on start also applies the latest device policy before starting the saved profile.

Available controls:

- Disable local proxy: removes generated and imported local listeners in VPN mode. Proxy mode retains its listener.
- Disable Clash API: closes the external REST controller while retaining internal traffic accounting, monitoring and selection cache.
- Disable system HTTP proxy: removes the advertised proxy from the VPN network.
- Full tunnel: routes TUN TCP/UDP and DNS through the selected outbound before direct rules; ignores app exclusions except the client itself. Android Always-on/lockdown is needed to block traffic after VPN termination.
- Public DNS address: advertises 1.1.1.1 in the VPN network information; DNS is intercepted by the core.
- Encrypted DNS: uses Cloudflare DoH for remote DNS. VPN endpoint bootstrap resolution can remain direct.
- IPv6 and MTU: use the existing core settings. IPv4-only also removes IPv6 addresses/routes from Android's VPN builder.
- Local core control: Android always supplies a per-installation random bearer token. Unary and streaming RPCs reject missing, wrong and duplicate authorization metadata. This does not remove the loopback TCP listening ports.

## Private application copy

This independently implements the package randomization technique used by Magisk: rebuild binary XML string pools, change the package and label, rename the resource-table package, sign a standalone APK with a locally generated Android Keystore key and invoke Android's installer. It does not use Magisk's stub/updater architecture or require root. Split APK installations are rejected.

Disconnect, create the copy, approve installation, then open it **from the original application's privacy page**. This passes a signed migration archive to the new copy. Open the new copy using the provided button before opening it from the launcher: migration refuses to overwrite an existing database. The SQLite database is snapshotted using `VACUUM INTO`, archive paths and sizes are validated, and the archive signature must match the installed copy's signing certificate. Preferences, profiles and profile configuration files are migrated; core caches and local control credentials are regenerated. Review connectivity in the copy before using the optional Android uninstall dialog for the original.

The original holds the signing key. Removing it deletes that key. Ordinary release APKs update/install the original package; updates of a private copy are not automated. Export profiles before replacing or updating a private installation. The app icon and native code remain recognizable; this is package/name randomization, not invisibility.

Android still exposes `VpnService`, `TRANSPORT_VPN` and the tunnel interface. Server IP reputation, geographic mismatches, and traffic fingerprints require server/network changes. These controls cannot guarantee passing RKNHardering.

## Validation

Core tests: `go test -ldflags=-checklinkname=0 ./v2/config ./v2/hcore ./platform/mobile` (the linker flag is already used by the core build).

Binary XML tests: compile `android/app/src/main/java/com/hiddify/hiddify/privacy/BinaryXml.java` and `tool/tests/BinaryXmlTest.java`, then run `BinaryXmlTest`. Fixtures cover UTF-8/UTF-16, Unicode, resizing, authorities and preserved node indexes.

Flutter navigation and policy tests: `flutter test test/reliability/navigation_test.dart test/reliability/vpn_privacy_policy_test.dart`.

Dart analysis uses the repository CI flags: `flutter analyze --no-fatal-warnings --no-fatal-infos`.

Device acceptance still requires a standalone release APK: install the renamed copy, transfer profiles, connect, change each privacy setting while connected, restart via Always-on without Flutter, verify local ports/DNS/TCP/UDP, and verify Android lockdown. Build the pinned core submodule before the Android APK because the native API includes `Mobile.applyDevicePrivacy`.
