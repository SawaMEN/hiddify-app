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

## Validation

`NativeProfileTransferTest`, `NativeQrCodecTest`, `NativeProbePolicyTest` and
`NativeDiagnosticReportTest` cover UTF-8, byte-order marks, size limits,
encoded URL tokens, custom ports, IPv6, QR round trips, inverted QR images and oversized QR
payloads, captive portal responses, probe URL validation and the diagnostic report allowlist. Run with the configured Android/Flutter SDK and prepared core:

```sh
cd android
./gradlew :app:testReleaseUnitTest
```

The Android build workflow runs these tests when `run-tests` is enabled. A full Android build
still requires Flutter, Android SDK/NDK, generated RPC sources and the native core; JVM tests
alone do not validate Compose layout or camera/document-picker behavior on a device.
