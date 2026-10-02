include dependencies.properties

MKDIR := mkdir -p
ANDROID_OUT=android/app/libs
CORE_NAME=hiddify-lib

ifeq ($(CHANNEL),prod)
	CORE_URL=https://github.com/hiddify/hiddify-core/releases/download/v$(core.version)
	TARGET=lib/main_prod.dart
else
	CORE_URL=https://github.com/hiddify/hiddify-core/releases/download/draft
	TARGET=lib/main.dart
endif

.PHONY: get gen translate common-prepare android-prepare android-apk-prepare \
        android-install-deps android-apk-install-deps android-libs android-apk-libs \
        android-release android-apk-release clean

get:
	flutter pub get

gen:
	dart run build_runner build --delete-conflicting-outputs

translate:
	dart run slang

common-prepare: get gen translate

android-install-deps:
	dart pub global activate fastforge

android-apk-install-deps: android-install-deps

android-libs:
	$(MKDIR) $(ANDROID_OUT)
	curl -L $(CORE_URL)/$(CORE_NAME)-android.tar.gz | tar xz -C $(ANDROID_OUT)/

android-apk-libs: android-libs

android-prepare: common-prepare android-libs

android-apk-prepare: android-prepare

android-release: android-apk-release

android-apk-release:
	fastforge package \
	  --platform android \
	  --targets apk \
	  --skip-clean \
	  --build-target=$(TARGET) \
	  --build-target-platform=android-arm64 \
	  --build-dart-define=sentry_dsn=$(SENTRY_DSN)
	@echo "Android ARM64-v8a APK output:"
	@find build/app/outputs/flutter-apk -maxdepth 1 -type f \( -name 'app-release.apk' -o -name '*arm64-v8a*.apk' \) -print

clean:
	flutter clean
	rm -rf dist out
