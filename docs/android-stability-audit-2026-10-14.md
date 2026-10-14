# Android stability audit — 2026-10-14

Reviewed the current standalone Kotlin/Compose `main` at `8c51eb43`, focusing on
process termination without an on-screen error, large imports, saved screen state,
foreground notifications, and crash evidence. No device crash trace was supplied;
these are code-level defects and safeguards, not a confirmed reproduction of the
reported intermittent crash.

## Changes

- Bound JSON parsing to 100,000 values, including containers, before allocating each
  element. The existing 8 MiB/depth limits alone allowed millions of tiny elements
  and an uncatchable heap exhaustion. Broad editor searches now retain at most
  1,000 matching paths.
- Bound saved text/list drafts to an estimated 16 KiB per field. Large community
  selections and pasted settings could previously overflow the Activity's shared
  Binder saved-state budget during backgrounding. This applies to import drafts,
  routing lists, settings editors and searches. Large drafts remain editable in
  memory; after Activity/process recreation they fall back to the screen's normal
  initializer. Persisted settings and profiles are unaffected.
- Keep the VPN running if optional screen-notification receiver registration fails.
  Notification removal errors no longer interrupt service destruction and native
  cleanup. Device-idle inspection and optional permission prompts on resume are
  also contained at their Android exception boundaries.
- Treat optional JSON editor templates, clipboard access and document-picker
  availability as recoverable failures rather than uncaught UI exceptions.
- Give Android process-exit reasons readable names in `last-exits.json`. Export
  JVM crash logs **and** system exit evidence together, even if an older `app.log`
  exists. Bound each exported application source to 256 KiB, deduplicate paths and
  avoid exposing private filesystem paths in the report.

The existing JVM crash handler and Android process-exit collection remain enabled.
Native crashes and system termination cannot be reliably caught by a JVM handler.
On Android 11+, the system exit record can distinguish native/JVM crashes, ANR,
memory pressure, permission changes and user/system termination when Android
provides that information. These records alone do not identify a native fault's
source location.

## Validation

- Standalone project/resource checks and `git diff --check`: passed.
- Production JVM sources and the complete JVM regression suite compiled using
  the cached Kotlin 2.4.20 compiler; **362 tests passed**.
- The cached coroutine dependency was 1.8.0; the configured Gradle build uses
  1.11.0. This fallback validates the JVM logic but does not replace CI's exact
  dependency/build checks.
- Standard Gradle test execution was attempted but could not download Gradle
  9.8.1 because the host network was unavailable. No full Android/Compose build,
  APK, emulator test or device stress test was completed locally.

New regressions cover JSON allocation limits, nested containers, large valid
documents, bounded searches, Unicode/aggregate saved-state limits, readable exit
reasons, and combined bounded application-log exports.

## Device verification

Use a fresh build to test repeated connect/disconnect, Wi-Fi/mobile switching,
screen-off/background/resume, and editing large routing selections. If a silent
exit recurs, reopen the app and export the application and core logs before
starting another tunnel. Check the exit time against the observed failure; an
older recorded exit is not evidence of the latest incident.

References:
- https://developer.android.com/develop/ui/compose/state-saving
- https://developer.android.com/reference/android/app/ApplicationExitInfo

## Follow-up: connecting and connected sessions

The first PR's CI tests, analysis, native race regressions and Android ARM64 build
completed successfully. The follow-up reviews service-owned coroutine failures,
native lifecycle replacement, WARP endpoint startup and process-exit evidence.

- `SupervisorJob` isolates child jobs but does not consume uncaught `launch`
  exceptions. Some service tasks (including asynchronous stop cleanup) did not
  have an outer exception boundary. Added a service-specific boundary that records
  an unexpected `Exception` and schedules one orderly disconnect. Cleanup cancels
  pending preparation/start/recovery, holds the shared native lifecycle mutex,
  releases the core/TUN/hotspot, updates the UI and stops the Android service.
  Cancellation remains normal control flow; VM/linkage `Error`s still reach the
  original Android crash handler. A replacement service's ownership is checked
  before cleanup to protect a newer session.
- Service replacement now cancels the old recovery/health tasks and joins its
  startup-cancellation task before closing and setting up the shared Go runtime.
  This prevents an old asynchronous `Mobile.stop()` from overlapping the next
  owner's setup/start.
- WARP candidates now start from inline config content. Previously a temporary
  file path was passed to `Mobile.start()` and deleted after a successful traffic
  probe. The core persists its start request for later restart/hot reload, so that
  persisted path could reference an already deleted file.
- On Android 11+, record allowlisted VPN phases in `setProcessStateSummary()` and
  include the validated phase in historical exit exports. The system can preserve
  this evidence when a native fault or signal prevents the JVM handler from
  running. Optional diagnostic API failures do not interrupt a connection. No
  config contents, error messages, addresses, tokens or other private state are
  included in the summary.
- Handled service errors have a separate bounded, redacted `service-failure.log`,
  displayed/exported alongside fatal JVM logs and system exit records.

Follow-up local validation: **370 JVM tests passed**, including concurrent service
failures, cancellation, fatal-error delegation, restarting a bound service,
redacted handled-error logs and process-phase payload validation. Production JVM
sources compiled with the same cached Kotlin/dependency fallback described above;
project/resource and whitespace checks passed. The follow-up Android build and
device stress tests still need separate verification. Neither round proves the
cause of a particular device's intermittent crash without its matching logs.

Additional references:
- https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-exception-handler/
- https://developer.android.com/reference/android/app/ActivityManager#setProcessStateSummary(byte[])

## Follow-up: simultaneous stop, deadline and network recovery

The initial PR #74 revision passed configured CI JVM tests, analysis and the ARM64
Android build. Its separate Go race job failed **before tests ran**, at release
metadata lookup with `HTTP Error 403: rate limit exceeded`. The source-checkout
step now supplies the existing read-only GitHub token to the release lookup.
No tests are disabled or downgraded.

- User disconnect, the startup deadline, destruction and service failure could
  race the check/assignment of `startCancellation`, creating multiple native stop
  loops while cleanup joined only the last recorded job. A synchronized task owner
  now assigns one lazy cancellation job before dispatching it and treats both new
  and active jobs as pending. All cleanup/replacement paths join that same job;
  completed jobs can be replaced for a subsequent startup.
- Start/reload/recovery no longer queue `LiveData.postValue(Starting/Started)`.
  Main-thread publication checks current stop/destruction/connection intent before
  updating the notification and status, so a queued connected event cannot undo a
  user disconnect. Health monitoring is not started after stop intent is set.
- Native profile cancellation callbacks can throw after the token is marked
  cancelled. Service stop/destruction and UI import cancellation/destruction now
  contain those callback exceptions, so Android main-thread lifecycle cleanup
  continues and the cancelled import still cannot commit.
- If optional screen-receiver registration fails, traffic-notification polling
  stays disabled. The static foreground notification remains, without a sampler
  that cannot observe screen-off events.

Validation: **374 JVM tests passed**, including 64 simultaneous cancellation
requests, waiting for blocking native stop completion, subsequent-start reuse and
failed native profile-cancel callbacks. All **24 Python release-tool tests**,
project/resource checks and whitespace checks passed. The JVM run uses the same
cached compiler/dependency fallback documented above. Updated Android build and
Go race results are checked separately in CI; device stress testing is still
unavailable here.

## Follow-up: DNS cancellation and rendered QR images

The preceding PR #74 revision (`1cfc3c95`) passed CI including its Android ARM64
build and the separate native Go race tests. The authenticated source lookup
also passed; its earlier 403 occurred before test execution.

- Android DNS signal cancellation previously ran unguarded inside a coroutine
  cancellation handler. An OEM/platform cleanup exception can be delivered as an
  uncaught completion-handler failure rather than the normal DNS error path. The
  extracted production DNS callback bridge now reports cancellation failures,
  issues cancellation once, and allows only one terminal answer/error. On a
  synchronous query-start failure it claims completion before signal cleanup, so
  a reentrant cleanup callback cannot overwrite the original exception.
- Removed immediate `Bitmap.recycle()` from Wi-Fi/LAN QR Compose disposal.
  The rendering system may still reference the image during closing animations;
  collection now follows the lifetime of the last rendering reference. This is
  consistent with the existing profile-share QR dialog. Import-only bitmaps that
  never enter the UI are still released after decoding.
- Optional Wi-Fi QR encoding errors no longer escape their UI coroutine. SSID
  and password text remain available when encoding fails.

Validation: **381 JVM tests passed**. The seven new DNS regressions cover duplicate
and late callbacks, cancellation-cleanup exceptions, failing error reporting,
reentrant cancellation callbacks and preservation of the first failure. Project,
resource and whitespace checks passed. The usual cached JVM dependency fallback
still applies; the latest Android build and device reproduction require separate
verification. No claim is made that these risks explain a particular phone's
observed exit without its crash/exit evidence.

References for these lifecycle constraints:
- https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-cancellable-continuation/invoke-on-cancellation.html
- https://developer.android.com/reference/android/graphics/Bitmap#recycle()

Merge verification found three DNS assertions that assumed exception reference
identity. CI enables coroutine stack recovery, which may copy an exception while
preserving its original in the cause chain. The assertions now verify type,
message and retention of the original failure in that chain. All **381 JVM tests
also passed locally with `-ea -Dkotlinx.coroutines.debug=on`**. This correction
changes tests only; no production behavior is altered and no test is disabled.
