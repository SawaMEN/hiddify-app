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

Enter any 1–40 character application name in the copy dialog. The generated random package is shown before creation; Generate another package refreshes it without changing the name.

Disconnect, create the copy, approve installation, then open it **from the original application's privacy page**. This passes a signed migration archive to the new copy. Open the new copy using the provided button before opening it from the launcher: migration refuses to overwrite an existing database. The SQLite database is snapshotted using `VACUUM INTO`, archive paths and sizes are validated, and the archive signature must match the installed copy's signing certificate. Preferences, profiles and profile configuration files are migrated; core caches and local control credentials are regenerated. The private copy reserves its own foreground/background control ports so the original installation can remain installed during verification. Review connectivity in the copy before using the optional Android uninstall dialog for the original.

The original holds the signing key. Removing it deletes that key. Ordinary release APKs update/install the original package; updates of a private copy are not automated. Export profiles before replacing or updating a private installation. Private copies use an original Meet (heart) or Notes icon and a user-selected name. Packages remain random rather than reusing a real dating service package/signature. Native code remains recognizable. This is reduced discoverability, not invisibility.

Ordinary mode still exposes `VpnService`, `TRANSPORT_VPN` and the tunnel interface. Root mode avoids Android VpnService registration but retains a kernel TUN interface. Server IP reputation, geographic mismatches, and traffic fingerprints require server/network changes. These controls cannot guarantee passing RKNHardering.

## Validation

Core tests: `go test -ldflags=-checklinkname=0 ./v2/config ./v2/hcore ./platform/mobile` (the linker flag is already used by the core build).

Binary XML tests: run `./android/gradlew -p tool/kotlin-tests test --tests "*BinaryXmlTest"`. Fixtures cover UTF-8/UTF-16, Unicode, resizing, authorities and preserved node indexes.

Flutter navigation and policy tests: `flutter test test/reliability/navigation_test.dart test/reliability/vpn_privacy_policy_test.dart`.

Dart analysis uses the repository CI flags: `flutter analyze --no-fatal-warnings --no-fatal-infos`.

Device acceptance still requires a standalone release APK: install the renamed copy, transfer profiles, connect, change each privacy setting while connected, restart via Always-on without Flutter, verify local ports/DNS/TCP/UDP, and verify Android lockdown. Build the pinned core submodule before the Android APK because the native API includes `Mobile.applyDevicePrivacy`.

## Automatic routing for Russia

Default mode is `ru-bypass`, active only when the selected configuration region is `ru`. The bundled `android/app/src/main/assets/region_routing.json` supplies example Russian banking, government, shopping and Yandex applications/domains. Installed packages are detected at connection and after package install/removal. Russian packages bypass VpnService altogether; domain suffixes use direct routing and direct DNS. Existing RU geo rules keep their automatic five-day updates. The example catalogue is shipped with app releases: it is not an exhaustive or live database of VPN detectors.

`proxy-selected` proxies the bundled foreign service packages/domains and any user additions, then explicitly routes all other traffic directly. It keeps other apps in the TUN so browsers can reach selected foreign domains. Android 10+ connection-owner support is needed to identify packages; OEM restrictions can prevent identification, in which case domain rules still work. Installed Russian apps remain excluded. User additions are whitespace/comma/semicolon separated ASCII package names or domains (IDNs must be punycode); no URLs. Direct domain/package exceptions precede selected proxy routes. Package availability is not a guarantee that a service is blocked or usable in Russia. No connection probing can universally identify a VPN detector.

Full tunnel wins over both regional modes and ignores these exceptions. Selecting a region or changing settings reconnects the Android service. These policies are applied after subscription overrides and during native tile/boot startup. Automatic regional app routing takes priority over manual per-app include/exclude preferences while active.

## Root connection (experimental)

The privacy page requests access through the installed `su` root manager. It executes only the packaged ARM64 PIE companion `libhiddify-root.so`, built from `cmd/android-root` with the same core features. The root connection uses the ordinary foreground ProxyService for lifecycle/notifications, and creates a kernel TUN without Android VpnService. AdAway's root hosts-file method is a different operation; no AdAway code or hosts-file modification is included.

The root daemon has its own private working directory and a per-install route table/interface name. Root and client UIDs are excluded to avoid routing the core into itself. The core owns its route rules and removes them during Stop/Close; no global iptables flush, SELinux modification, third-party binary download or impersonation is performed. RPC stays loopback-only with the per-install bearer token. Closing the parent stdin pipe, including an app crash, requests graceful shutdown. Native foreground supervision detects daemon exit. Startup/permission/shutdown failures are reported instead of quietly falling back to ordinary VPN mode.

Root mode requires a supported `su`, kernel TUN and working netlink policy routing. Android Always-on/lockdown cannot protect a root connection. It is not a persistent kill switch, and a force-killed daemon/kernel/OEM failure can leave routing state requiring reconnect/reboot. Root traffic and this application's traffic intentionally remain outside the kernel capture policy. A kernel TUN/process/IP/server fingerprint remains detectable. Root mode has not been acceptance-tested on a physical Magisk/KernelSU device.

Build `make android-libs` to produce the AAR and root companion together. The APK extracts native libraries so `su` can execute the companion; private-copy repacking preserves it. Root state/cache and launch credentials are excluded from migration.

## Root availability and lifecycle

Opening the privacy tab or resuming it discovers an executable `su` without running it or displaying a root permission dialog. The toggle is disabled during discovery, when `su` is unavailable, or when the APK has no executable companion. A saved root selection is cleared when either component is absent; Check root again refreshes the state. Enabling requests authorization from the root manager and verifies UID 0. Missing su, denial, execution failure and a 30-second authorization timeout are handled as availability results.

The root daemon supports the existing authenticated control RPC for profile start/stop, reconnection, options, outbounds, logs and traffic statistics. Native background restart passes the selected profile name, logging mode and memory-limit preference. Kernel TUN and policy routing remain owned by sing-box; stop requests graceful cleanup over the parent pipe. A live daemon's nonzero shutdown is reported rather than accepted as confirmed cleanup. SELinux/kernel restrictions can still prevent TUN or netlink access despite an available su; startup reports those failures. Physical root-device acceptance remains required.

Discovery regression fixture: run `./android/gradlew -p tool/kotlin-tests test --tests "*RootAccessTest"`. It checks executable detection, rejection of nonexecutable files/directories, and absence of command execution during discovery.
