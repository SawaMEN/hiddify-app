# Android Kotlin migration

The `kotlin-rewrite` branch launches `NativeMainActivity` and uses Compose for Android.
Flutter remains a temporary compatibility UI and build dependency; the migration is not complete.

## Profile transfer and QR

The native profile list now supports:

- QR scanning with the camera and reading QR images through the system document picker.
  Scanning fills the import form; the user reviews it before importing.
- Copying subscription links, sharing them through Android's share sheet, and displaying
  subscription QR codes. Links preserve encoded credentials, IPv6 authorities and custom ports;
  the profile name replaces the URL fragment.
- Copying the source profile configuration or saving it through Android's document picker.
  Exports are the stored source content, not a generated full sing-box configuration. Source
  files may contain JSON, YAML or protocol links, so exports use `.txt` and `text/plain`.
- Reading documents and profile editor content with a streaming 8 MiB limit. Clipboard source
  exports have a 256 KiB limit; larger configurations can be exported to files.

QR processing runs locally through ZXing. Image decoding uses software bitmaps bounded to
2048 pixels on the longest edge. Image/file reads and profile exports run away from the UI thread.
The export picker retains the profile ID across Activity recreation without putting configuration
contents into the saved-instance Bundle. QR/link import drafts are saved up to 64 KiB; larger
file contents must be selected again after recreation.

The native core options screen is now connected to its settings navigation route.
English and Russian strings cover the new actions and errors.

## Connection diagnostics and VPN protection

The native home screen opens connection diagnostics. The checks inspect Android's underlying
Internet-capable networks, presence of the selected profile file, the authenticated local core RPC,
and an HTTP request through the selected core outbound. No direct DNS/socket/HTTP fallback is
used for the tunnel check. The RPC schema matches the pinned core's `ProbeConnection` messages.
`generate_204` and `generate204` paths require HTTP 204; a captive portal returning HTTP 200
cannot pass those checks. Redirects are not counted as success.

Each RPC has a deadline, and the coroutine cancels its RPC on exit. Checks can be cancelled
manually and are cancelled when leaving diagnostics, backgrounding the Activity, or stopping the
service. The diagnostics client supports the core's 30-second adaptive probe budget without
changing normal UI RPC timeouts.

Reports show a preview before Android's share sheet. They contain only fixed stage/result codes,
HTTP status codes, version, timestamp, service state and protection booleans. They do not contain
raw exception messages, profiles, logs, URLs, IP addresses or subscription credentials.

The settings screen opens native VPN protection controls. Always-on and lockdown status are
read from the active Android VPN service; unavailable status remains unknown, rather than being
shown as disabled. The system settings button opens Android VPN settings, with a general settings
fallback if the device does not expose that action. Status refreshes on resume.

## Application routing backups

The native per-app page opens a Compose backup screen with clipboard and document import/export.
The JSON format matches Flutter's `include`/`exclude` objects and `selected`/`deselected` arrays;
legacy double-encoded JSON is also accepted. A restore preview shows counts for both modes and
requires confirmation before replacing manual choices. Restore keeps automatic policy bits and
the currently selected routing mode. It retains choices for apps not installed on this device in
the database, while the VPN settings receive only installed packages.

Imports are validated before the database transaction. Conflicting selections, invalid package
names, wrong data types and unrelated JSON documents are rejected. File reads are bounded to
1 MiB, with at most 10,000 choices across both modes. Large backups can use document export
instead of the clipboard. Database/package-manager operations run on IO and are serialized;
mutations also use the core lifecycle barrier and require the VPN to be stopped.

Native two-state checkboxes now change effective selection in one click, including automatically
selected apps and forced deselections restored from backups. Unknown flag bits are preserved but
cannot accidentally make an application selected. The per-app page uses one lazy scrolling list
for controls and applications, so the controls remain accessible on small screens.

## Native experimental traffic restrictions

The Kotlin settings page now opens a Compose screen for QUIC, STUN/WebRTC, plain HTTP
blocking and LAN isolation. It preserves the compatibility UI's opt-in defaults, limitations,
bulk-enable confirmation and reconnect requirement. The screen displays saved choices rather
than claiming that an already running tunnel has applied them.

A typed filter model reads only actual Boolean values under the existing Flutter preference
keys. Individual changes preserve other filters; bulk changes write all four values in one
SharedPreferences transaction on IO. The core policy reads the same model, avoiding a separate
mapping that could diverge from the UI. Modern-protocol and adaptive-network settings remain
independent. Save failures are surfaced through the native error dialog.

## Native regional routing controls

The Kotlin settings page now opens a regional-routing form with the existing off, Russian-bypass
and selected-services modes, independent Russian domain/IP, Russian app and proxied-service
switches, and additional direct/proxied domains. The form preserves drafts across rotation and
explains when full tunnel or manual routing overrides automatic choices. Changes apply on reconnect.
Application selections and their manual/automatic semantics are retained through the native
regional application editor described below.

The repository validates and normalizes domain lists before an atomic preferences write on IO.
It checks the candidate policy against the real bundled catalogue before saving, including the
256-entry merged limits. The shared parser is used by the core policy and JVM tests. The native
form bounds each domain field to 8192 characters and rejects URLs, ports and IP literals; ASCII
and punycode domain names use the existing routing grammar. Unknown stored modes resolve to off.

## Native regional application selection

The regional form now opens native Russian-app and proxied-service lists, with search by label or
package, selected-first ordering, manual save and an offline reset to the bundled automatic list.
All controls and rows share one lazy scrolling list. Unsaved regional form edits must be saved
before opening these lists. Application drafts and the search query survive Activity recreation;
only package strings enter saved state, not package-manager objects or icons.

Saving follows the compatibility UI: it selects the Russian-bypass routing mode and removes
intersections from the opposite manual list in the same preferences transaction. Automatic and
legacy opposite lists remain intact. A manual empty list (`manual:`) differs from an automatic
reset (empty string). Uninstalled or unavailable manual selections remain visible and are retained
until deselected. The client package cannot be selected. The repository checks the candidate
policy before writing preferences; loading/saving runs on IO and regional operations are serialized.
The UI states that changes require reconnecting, and retains category/full-tunnel/manual overrides.

Both native per-app editors now share a package loader without routing/database side effects.
The policy and editor share manual-package parsing and encoding. Package identifiers preserve
case for exact PackageManager lookup, while domain names are still normalized to lowercase.

## Native connection policy and internet health

The Kotlin settings page now opens protocol and network adaptation controls. The three switches
use existing Flutter keys and opt-in defaults, preserve UDP preference when the protocol restriction
is disabled, and write together on IO. The core policy uses the same typed model. The screen
explains required server compatibility and that core changes apply after reconnecting.

While the native Activity is visible and the service is started, a coroutine checks internet through
the selected core outbound and displays unchecked/checking/available/failed on the home screen.
It waits three seconds after starting, serializes probes, uses a 12-second normal deadline and
30-second interval, and cancels RPCs and timers when the service stops or the Activity backgrounds.
Core options and selected outbound changes restart the check. Captive portals and redirects use
the existing diagnostic response policy; no direct HTTP or DNS fallback is added.

Adaptive mode starts with a 30-second deadline, requires three successes before reducing it,
and bounds its latency-based deadline to 12–30 seconds. A failure restores the longer deadline.
Successful adaptive checks use a 60-second interval; failures use 30 seconds. With no underlying
Internet-capable non-VPN network, it waits 15 seconds without starting a probe or incrementing
failure counters. Timing uses a monotonic clock. A failed health check does not restart the VPN;
the separate native service-recovery loop below handles unexpected service failures.

The standalone JVM suite now includes the pure hotspot helpers needed by the Wi-Fi-sharing tests
merged into main, as well as the new connection models and adaptive timing tests.

## Native bounded service recovery

The native connection settings now expose the existing `auto_reconnect` preference (default true).
Recovery is enabled by that switch or adaptive mode, matching the compatibility UI. It restores an
unexpectedly stopped service only while the Activity is visible and both native connection intent
and the started-by-user flag remain true. Explicit disconnect, cancellation on the home screen,
permission/configuration alerts and exhausted budgets clear or cancel recovery.

The normal retry delays are 2, 4, 8, 16 and 30 seconds; adaptive delays are 5, 10, 20, 40, 60, 90,
120 and 180 seconds. An actual start consumes a slot; observation and offline waits do not. A
15-second observation window separates asynchronous launches from subsequent decisions.
Counters survive Activity recreation through saved state and reset after a fresh manual start or
one minute of stable foreground connection. Going to the background cancels timers; resuming
re-evaluates persisted connection intent.

Before starting, the coroutine acquires the core lifecycle barrier and rechecks intent, visibility,
permission, pending operations and core ownership. It never stops an owned core to repair a stale
binder observation. A running core is rebound instead. Permission loss asks for a manual Connect
without opening permission dialogs from a retry. Health-check failures alone still do not trigger
a restart. The home screen shows the pending attempt and offers Cancel recovery.

## Native appearance and Android settings

The Kotlin app now applies system, light, dark and black theme choices to the whole Compose
interface. The settings picker saves the existing `flutter.theme_mode` value on IO so the
compatibility UI sees the same choice. Missing, unknown or incorrectly typed values follow the
system theme. Black mode uses a black canvas with dark-theme surfaces; cards retain contrast.
System bar icon appearance follows the selected theme, including a forced dark theme on a light
system. Theme changes do not restart or reconfigure the VPN.

The application settings section opens the shared VPN notification settings intent and Android's
battery optimization settings. Both use the application's own package details as a fallback if
the device lacks a specialized settings Activity. No Flutter method channel is needed for these
native actions. Refreshing the native settings snapshot also reloads theme choices made in the
compatibility UI.

## Dart visual parity

Compose now uses the palette and typography from `lib/core/theme/app_theme.dart`, the original
Manrope font and app logo, Android 12+ wallpaper colors just like Flutter DynamicColorBuilder, 24dp outlined cards, 20dp buttons, 28dp dialogs, and the static cyan/
magenta atmosphere with its 64dp grid from `cyber_background.dart`. Black mode skips the atmosphere.
The shared native components apply these tokens across profile, settings, routing and utility screens.
Glass regions use the source gradient and accent border without a full-screen blur.

Home restores the compact logo header, selectable profile, 176dp circular power control with a 64dp
power icon, centered status, paired speed readings, glass quick-settings entry and rounded 72dp
bottom navigation. Pending connection operations remain disabled; cancellation of automatic
recovery remains available. Logs, About and the compatibility entry are now accessible from the settings overview.
Dark, light and black home previews are available in Android Studio. The middle tab now opens a native VPN privacy overview with the source routing, connection, DNS
and proxy/control categories. Categories retain expansion across child screens and Activity recreation.
Settings use glass icon/chevron rows, with separate general, VPN, DNS, custom-routing and backup pages.
Shared native page history returns children to their actual entry point, including nested selectors
and backups. All switches retain their existing Kotlin preference writers; entire switch rows are
accessible touch targets. Automatic setup and restore now run in Kotlin, as described below. Android device screenshot comparison is still required
before claiming pixel-for-pixel equivalence.

## Native automatic privacy setup and restore

The privacy overview now includes the source one-tap setup and restore actions, with the same glass
status card and filled/outlined buttons. Kotlin snapshots the original Dart field names in
`flutter.privacy-auto-setup-backup` before the first setup. Reapplying keeps that snapshot. Automatic
setup enables the same DNS, regional routing, IPv4-only, 1400 MTU and reconnect preferences while
preserving root, user lists, transport choices and traffic filters. Core JSON and Flutter preference
values are updated together; unrelated core options remain intact.

Restore accepts existing Dart JSON snapshots and uses the source defaults for absent fields.
Invalid types, modes, MTU bounds or oversized/corrupt data fail before writing; the backup remains
available instead of silently replacing settings with defaults. Restoring values and clearing the
backup happen in one SharedPreferences commit on IO. The service lifecycle barrier rechecks that
no core owns the tunnel, and regional writes are serialized. Start/recovery and navigation are
blocked during the transaction. Root service visibility is synchronized after the commit. Overview
status parsing also runs on IO.

Unlike Flutter's immediate service reconfiguration, the native action currently requires a stopped
VPN. The screen explains that changes apply on the next connection. This avoids restarting a VPN
while switching service/root modes; active-service reconfiguration remains migration work.

## Native proxy and control privacy

The proxy/control category now opens a Compose screen for the remaining Dart privacy switches:
disable local HTTP/SOCKS/direct listeners, hide the external Clash REST API, and disable the system
HTTP proxy. It uses the shared glass/card style, accessible whole-row switches and a port/LAN options
entry. The screen explains that changes apply after reconnecting and shows the non-root Wi-Fi sharing
exception without changing the saved local-proxy preference.

`NativeProxyPrivacy` reads and writes the existing three Flutter keys, accepting only Boolean values
and preserving restrictive defaults for missing or malformed values. One IO commit updates only these
keys; DNS, routing, root and hotspot choices are untouched. Startup reads the three choices as one
snapshot and applies the same typed model. `Settings` also shares its key constants and the effective
local-listener rule: non-root sharing keeps that listener available, while root sharing preserves the
restriction. Native saves and automatic setup exclude each other, and navigation/start/recovery are
blocked during the short write. Reload after returning from the compatibility UI runs on IO.

## Validation

`NativeProfileTransferTest`, `NativeQrCodecTest`, `NativeProbePolicyTest` and
`NativeDiagnosticReportTest`, `NativePerAppBackupTest`, `NativePerAppFlagsTest` `NativeTrafficFiltersTest` `NativeRegionalOptionsTest` and `NativeRegionalPackagesTest`, `NativeConnectionOptionsTest`, `NativeHealthProbePolicyTest` `HotspotSharingTest`, `NativeRecoveryPolicyTest` and `NativePrivacySetupTest` and `NativeProxyPrivacyTest` cover UTF-8, byte-order marks, size limits,
encoded URL tokens, custom ports, IPv6, QR round trips, inverted QR images and oversized QR
payloads, captive portal responses, probe URL validation and the diagnostic report allowlist, legacy routing backups, validation limits and manual/automatic flag transitions, typed privacy preferences and bulk filter round trips, regional routing modes, override precedence, domain normalization and merged catalogue limits, automatic/manual empty selections, package case preservation, cross-list conflict removal, reset isolation and package limits. Run with the configured Android/Flutter SDK and prepared core:

```sh
cd android
./gradlew -p ../tool/kotlin-tests test
```

The Android build workflow runs these tests when `run-tests` is enabled, using a separate JVM
Gradle project in `tool/kotlin-tests`. This compiles the production Kotlin files directly and avoids
relying on the Android unit-test task unavailable in the current Flutter/AGP configuration. Test reports are uploaded
as a CI artifact. A full Android build
still requires Flutter, Android SDK/NDK, generated RPC sources and the native core; JVM tests
alone do not validate Compose layout or camera/document-picker behavior on a device.
