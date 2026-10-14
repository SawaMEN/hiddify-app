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
