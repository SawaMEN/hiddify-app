# VetrOFF Client — Android / Kotlin

Android VPN client built with Kotlin and Jetpack Compose. The application has no Dart sources,
Flutter engine, Flutter SDK dependency or cross-platform compatibility UI. The UI uses Android system typography, shared surfaces and Android VPN integration.

The VPN/protocol engine is the **latest stable release** of
[SawaMEN/hiddify-core](https://github.com/SawaMEN/hiddify-core/releases/latest).
Android builds download its verified ARM64 `hiddify-core.aar` and
`libhiddify-root.so` pair (including SHA-256 and source provenance) rather than compiling
a pinned Git submodule. Drafts and prereleases are excluded. The application
repository no longer contains the `hiddify-core` Git submodule. Optional Go
regression tests temporarily fetch source matching the selected stable release,
without committing a pinned core revision. Kotlin communicates with the engine
through the Android AAR and authenticated local RPC.

## Build

Requires Java 17, Android SDK platform 37.0, build-tools 37.0.0 and NDK 30.0.16248370.
Building the APK from the stable core release does not require Go; Go is needed
only for optional source-level core tests. Gradle is pinned by the Android wrapper.

```sh
make android-apk-prepare
make android-apk-release
```

The APK is `build/app/outputs/apk/release/app-release.apk`, for Android 10+ and ARM64.
Set `ANDROID_HOME` or `sdk.dir` in `android/local.properties`. App version/name are declared
in `version.properties`. `CHANNEL=prod` requires a valid `android/key.properties` and keystore;
dev builds can use debug signing. Signing files and local SDK configuration must not be committed.

## Stable release signing

Stable Android APK releases must be signed using the existing production keystore.
Configure these four repository secrets under **Settings → Secrets and variables → Actions**:

- `ANDROID_SIGNING_KEY`: base64-encoded JKS/PKCS12 keystore file, on a single line
- `ANDROID_SIGNING_STORE_PASSWORD`: keystore password
- `ANDROID_SIGNING_KEY_PASSWORD`: alias key password
- `ANDROID_SIGNING_KEY_ALIAS`: signing key alias

Do not commit the keystore or its passwords. Preserve an offline backup: future APK
updates must be signed by the same identity as earlier installed production builds.

Once configured, use **Actions → Release VetrOFF Client → Run workflow** and
select `prod`. With no tag supplied it uses the version from
`version.properties` (currently `v1.0.1`). Release jobs reject missing production
signing keys rather than publishing a debug-signed stable APK.

## Existing installations

The application ID remains `app.vetroff.client`. Existing preference names and profile database
paths are intentionally preserved, including legacy `flutter.*` keys and `app_flutter/db.sqlite`.
These names are a storage compatibility contract, not a dependency on Flutter. The previous
`NativeMainActivity` component redirects through an Android manifest alias to the Kotlin launcher.

Profiles/subscriptions, QR import/export, configuration editing, routing, VPN protection,
connection recovery/health checks, hotspot sharing, core options, server selection, diagnostics,
logs, themes and updates use the native repositories and Compose pages.

See [migration notes](docs/KOTLIN_MIGRATION.md) for implementation details and remaining visual
parity items. Device checks are still needed for VPN, root/private-copy installation and hotspot
behavior; a successful APK build does not establish device behavior.
