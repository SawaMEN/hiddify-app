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
	$(MKDIR) $(ANDROID_OUT)
	@test -f hiddify-core/go.mod || { echo "Run git submodule update --init --recursive first"; exit 1; }
	$(MAKE) -C hiddify-core android-arm64 CODE_VERSION="-X github.com/hiddify/hiddify-core/v2/hcommon/constants.Version=$(core.version)-$$(git -C hiddify-core rev-parse --short=12 HEAD) -X github.com/sagernet/sing-box/constant.Version=sawamen-$$(git -C hiddify-core/hiddify-sing-box rev-parse --short=12 HEAD)"
	install -m 644 hiddify-core/bin/hiddify-core.aar $(ANDROID_OUT)/hiddify-core.aar
	cd hiddify-core && go run ./cmd/internal/build_root
	$(MKDIR) android/app/src/main/jniLibs/arm64-v8a
	install -m 755 hiddify-core/bin/libhiddify-root.so android/app/src/main/jniLibs/arm64-v8a/libhiddify-root.so
	python3 tool/write_core_provenance.py $(ANDROID_OUT)/hiddify-core.aar

android-apk-libs: android-libs

android-prepare: common-prepare android-libs

android-apk-prepare: android-prepare

android-release: android-apk-release

android-apk-release:
	flutter build apk \
	  --release \
	  --target=$(TARGET) \
	  --target-platform android-arm64
	@echo "Android ARM64-v8a APK output:"
	@find build/app/outputs/flutter-apk -maxdepth 1 -type f \( -name 'app-release.apk' -o -name '*arm64-v8a*.apk' \) -print

clean:
	flutter clean
	rm -rf dist out
