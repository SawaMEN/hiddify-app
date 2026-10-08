# VetrOFF Client — Android / Kotlin

Android VPN client built with Kotlin and Jetpack Compose. The application has no Dart sources,
Flutter engine, Flutter SDK dependency or cross-platform compatibility UI. The UI uses Android system typography, shared surfaces and Android VPN integration.

The VPN/protocol engine is the pinned `hiddify-core` Go submodule, exposed to Kotlin through
its Android AAR and authenticated local RPC. Protocol parsing and transport implementations
remain in that native engine.

## Build

Requires Java 17, Android SDK platform 37.0, build-tools 37.0.0, NDK 30.0.16248370,
and the Go toolchain specified by `hiddify-core/go.mod`. Gradle is pinned by the Android wrapper.

```sh
git submodule update --init --recursive
make android-apk-prepare
make android-apk-release
```

The APK is `build/app/outputs/apk/release/app-release.apk`, for Android 10+ and ARM64.
Set `ANDROID_HOME` or `sdk.dir` in `android/local.properties`. App version/name are declared
in `version.properties`. `CHANNEL=prod` requires a valid `android/key.properties` and keystore;
dev builds can use debug signing. Signing files and local SDK configuration must not be committed.

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
