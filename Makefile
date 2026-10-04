include dependencies.properties

MKDIR := mkdir -p
ANDROID_OUT=android/app/libs
CORE_DIR=hiddify-core
ANDROID_NDK_VERSION=30.0.16248370
ANDROID_NDK_DIR=$(ANDROID_HOME)/ndk/$(ANDROID_NDK_VERSION)

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
	@echo "Android core is built from the pinned hiddify-core submodule"

android-apk-install-deps: android-install-deps

android-libs:
	$(MKDIR) $(ANDROID_OUT)
	@set -eu; \
	  echo "Initializing pinned hiddify-core and sing-box submodules..."; \
	  git submodule update --init --recursive $(CORE_DIR); \
	  if ! command -v go >/dev/null 2>&1; then \
	    echo "Go toolchain is required to build hiddify-core"; \
	    exit 1; \
	  fi; \
	  ndk="$(ANDROID_NDK_DIR)"; \
	  attempt=0; \
	  while [ ! -f "$$ndk/source.properties" ]; do \
	    attempt=$$((attempt + 1)); \
	    if [ "$$attempt" -ge 120 ]; then \
	      echo "Android NDK $(ANDROID_NDK_VERSION) was not installed at $$ndk"; \
	      exit 1; \
	    fi; \
	    sleep 1; \
	  done; \
	  echo "Building hiddify-core from $$(git -C $(CORE_DIR) rev-parse --short HEAD) with NDK $(ANDROID_NDK_VERSION)..."; \
	  GOTOOLCHAIN=auto ANDROID_NDK_HOME="$$ndk" $(MAKE) -C $(CORE_DIR) android; \
	  test -s $(CORE_DIR)/bin/hiddify-core.aar; \
	  cp $(CORE_DIR)/bin/hiddify-core.aar $(ANDROID_OUT)/hiddify-core.aar; \
	  ls -lh $(ANDROID_OUT)/hiddify-core.aar

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
