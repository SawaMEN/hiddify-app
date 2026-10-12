include dependencies.properties

MKDIR := mkdir -p
ANDROID_OUT=android/app/libs

.PHONY: get common-prepare android-prepare android-apk-prepare \
        android-install-deps android-apk-install-deps android-libs android-apk-libs \
        android-release android-apk-release clean test

get:
	cd android && ./gradlew :app:dependencies --console=plain

common-prepare:
	python3 tool/check_native_project.py

android-install-deps:
	@echo "No additional Android build tools are required"

android-apk-install-deps: android-install-deps

android-libs:
	$(MKDIR) $(ANDROID_OUT)
	python3 tool/fetch_stable_android_core.py

android-apk-libs: android-libs

android-prepare: common-prepare android-libs

android-apk-prepare: android-prepare

android-release: android-apk-release

android-apk-release:
	cd android && ./gradlew :app:assembleRelease --console=plain
	@echo "Android ARM64-v8a APK output: build/app/outputs/apk/release/app-release.apk"

clean:
	cd android && ./gradlew clean --console=plain
	rm -rf dist out

# Unified JVM/Python regressions; no Android SDK, native AAR or APK required.
test:
	./android/gradlew -p tool/kotlin-tests test --console=plain
	python3 -m unittest discover -s tool -p 'test_*.py'
	python3 tool/check_native_project.py
