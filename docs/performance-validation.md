# Android performance and recovery validation

The background service owns health checks and bounded recovery. The activity only observes state. Recovery requires a previously working, unchanged configuration, user intent and VPN permission; a failed public check endpoint alone does not restart the VPN. Offline waiting does not consume recovery attempts.

The notification and visible activity share one traffic sampler. The proxy list and foreground ranking share one primary-group stream. Logs are read only while the log screen is visible and unpaused. Dock blur records a small static strip instead of the entire page.

## Device checks before a production release

Use a physical arm64 device, including Android 10/11 and Android 12+. No frame-time or network-throughput improvement is claimed without these measurements.

- Check Russian and English at 100%, 150% and 200% font size, narrow portrait and landscape. Test dock labels, home header, proxy tiles and dialogs.
- Connect, background the activity, switch Wi-Fi/mobile, enable airplane mode, then restore connectivity. Kill the root core when using root mode and verify bounded retries without opening the activity.
- Stop manually during startup, recovery and an offline wait. Revoke VPN permission. Confirm no reconnect after either action, no surviving foreground notification and no stale TUN.
- Block one health endpoint, then all health endpoints while proxy traffic works. A probe failure must remain a diagnostic state, without disconnecting a working core.
- Compare notification and screen traffic values. Disconnect/reconnect and reload profiles; the first sample must have no invented speed or spikes from the old session.
- Pause/leave logs and confirm no periodic file reads. Rotate and truncate logs, resume and check refreshed content.
- Scroll a long proxy list during delay/traffic updates; rows retain their order during a drag.
- Run the manual download estimate, cancel it and disconnect during it. It uses the selected VPN outbound, one test at a time, up to 5 MiB and 30 seconds; it measures download only. Automatic server selection pauses while the test runs. A short transfer is an estimate, not a link-capacity certification.

## Frame timings and Baseline Profile

The benchmark module is opt-in and does not affect a normal build. Prepare the Android SDK and the native AAR using the existing core build workflow, connect a physical arm64 device, then run from `android`:

```sh
./gradlew -PperformanceTests=true :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hiddify.hiddify.benchmark.UiPerformanceTest
./gradlew -PperformanceTests=true :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hiddify.hiddify.benchmark.BaselineProfileGenerator
```

Compare cold-start timings and FrameTimingMetric percentiles on the same device, refresh rate, thermal state and profile data. Keep the result JSON/traces produced by AndroidX Benchmark. The checked-in `app/src/main/baseline-prof.txt` is only a conservative handwritten startup seed. Replace it with the generated profile after reviewing the device run; it is not a measured Baseline Profile. The app benchmark variant is release-like, profileable and debug-signed; never distribute it as a production release.

Release builds enable R8/resource shrinking with conservative Go/JNI and Wire API keep rules. Verify a release-signed startup, import, VPN connection, notification, root mode and diagnostics before publishing an APK. Unit/UI compilation checks cannot validate the packaged JNI ABI.

## MTU comparison

Defaults and imported profile MTUs are preserved. Manual presets: 1280, 1380, 1400, 1500 and 9000. Compare only on the same server/protocol/network: latency, transfer estimate, large-page downloads and reconnects after a network change. Restore the previous value if connectivity worsens. There is no universal fastest MTU; do not replace imported settings globally.

The Java/Kotlin bytecode target remains 17. Changing the builder JDK is a separate compatibility decision and does not fix animation, VPN routing or traffic calculations.
