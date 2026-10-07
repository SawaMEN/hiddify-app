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

## Validation

`NativeProfileTransferTest` and `NativeQrCodecTest` cover UTF-8, byte-order marks, size limits,
encoded URL tokens, custom ports, IPv6, QR round trips, inverted QR images and oversized QR
payloads. Run with the configured Android/Flutter SDK and prepared core:

```sh
cd android
./gradlew :app:testReleaseUnitTest
```

The Android build workflow runs these tests when `run-tests` is enabled. A full Android build
still requires Flutter, Android SDK/NDK, generated RPC sources and the native core; JVM tests
alone do not validate Compose layout or camera/document-picker behavior on a device.
