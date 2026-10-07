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

## Validation

`NativeProfileTransferTest`, `NativeQrCodecTest`, `NativeProbePolicyTest` and
`NativeDiagnosticReportTest`, `NativePerAppBackupTest`, `NativePerAppFlagsTest` `NativeTrafficFiltersTest` `NativeRegionalOptionsTest` and `NativeRegionalPackagesTest` cover UTF-8, byte-order marks, size limits,
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
