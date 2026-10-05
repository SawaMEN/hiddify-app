include dependencies.properties

MKDIR := mkdir -p
ANDROID_OUT=android/app/libs

ifeq ($(CHANNEL),prod)
TARGET=lib/main_prod.dart
else
TARGET=lib/main.dart
endif

.PHONY: get gen translate common-prepare android-prepare android-apk-prepare \
        android-install-deps android-apk-install-deps android-libs android-apk-libs \
        android-release android-apk-release clean

get:
	flutter pub get

gen:
	dart run build_runner build

translate:
	dart run slang

common-prepare: get translate gen

android-install-deps:
	@echo "No additional Android build tools are required"

android-apk-install-deps: android-install-deps

android-libs:
	bash tool/build_android_core.sh

android-apk-libs: android-libs

android-prepare: common-prepare android-libs

android-apk-prepare: android-prepare

android-release: android-apk-release

android-apk-release:
	flutter build apk \
	  --release \
	  --target=$(TARGET) \
	  --target-platform android-arm64 \
	  --dart-define=sentry_dsn=$(SENTRY_DSN)
	@echo "Android ARM64-v8a APK output:"
	@find build/app/outputs/flutter-apk -maxdepth 1 -type f \( -name 'app-release.apk' -o -name '*arm64-v8a*.apk' \) -print

clean:
	flutter clean
	rm -rf dist out
