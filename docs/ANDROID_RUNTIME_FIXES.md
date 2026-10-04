# Android runtime fixes

Baseline: `77cf73d7a28f5fa1aa9acecacc0ad6ab28b52299`.
Scope: the Android stability audit, including profile/editor data loss and asynchronous UI failures.

| Area | Change |
| --- | --- |
| Android 14+ tile | PendingIntent overload; legacy overload only on older Android |
| Startup | Foreground registration before IO; initialization errors reported through service alerts |
| Native lifecycle | Shared setup/reload/stop/destroy mutex; explicit core ownership; obsolete owner cannot close replacement |
| Backgrounding | Android UI releases subscriptions without calling native Close, which also stops the shared VPN |
| Permissions | Launcher request generation tracked separately; stale results ignored; issued startup stopped on timeout |
| DNS | Cancellable network/query waits, 15 second bound, cancellation signals and one terminal callback |
| Network waiter | Cancelled deferred requests removed from actor queue |
| RPC | Deadlines for unary calls; bounded parsing/restart; status/log reconnect timers and subscription generations |
| Android events | Consume alert once; remove LiveData observer and scoped binder callbacks on destroy |
| VPN include routing | Reject an empty/missing include list rather than implicitly routing every application |
| Boot and shortcuts | Registered boot receiver; exception handling, configuration/permission checks, native start flag |
| Notification polling | Main-thread updates; polling generation; obsolete service does not remove replacement notification |
| Ports | Background 17079 default; migrate accidental foreground port; client cache follows selected port |
| Profile updates | Unique staging paths; serialized lookup/update/delete; atomic Android file replacement and rollback on metadata errors |
| Validation | Never apply an imported profile's options to the running VPN; check override types then native parse into staging |
| Editor | Load raw original document; preserve DNS/routes/custom fields; encode escaped strings with JSON encoder |
| Editor lifecycle | Cancel search/scroll callbacks on dispose; publish valid edits immediately; invalid text disables Save |
| Per-app backups | Single JSON object export; UTF-8 import; accept old double-encoded documents |
| Per-app notifiers | Capture dependencies before await; mounted/generation guards; timer cleanup; avoid late-final build reassignment |
| Logs | Cancel hidden/paused subscriptions instead of buffering broadcast snapshots; dispose debouncer |
| Diagnostics | Preserve app logs across restart; rotate current/previous 2 MiB files; handle sink errors; guard uninitialized Sentry hub |
| Proxy groups | Empty-group handling; deterministic delay comparator; copy protobufs before sorting/selection |
| QR | Ignore duplicate/empty detections and double navigation |
| Throttling | Await async callback and propagate errors; release running guard after failure |
| Settings | Debounce and serialize latest changes; retry pending changes once connected; explicit service recreation for TUN mode |
| HTTP resources | Close owned Dio clients when provider is disposed |
| Native dependency | Pin compatible Android asset ID 609473425 in all build channels and verify archive SHA-256 before extraction |

## Automated verification

`dart tool/check_async_runtime.dart` includes queue ordering, failure recovery and awaited asynchronous throttler checks, alongside the existing startup/metadata checks.

`test/android_stability_regression_test.dart` covers ordinary and legacy backup round trips, invalid backup rejection, editor disposal during pending search/delayed scroll, JSON string escaping, immediate edit publication and invalid-text validation.

The existing GitHub Actions workflow runs code generation, Dart error analysis, Flutter tests and the ARM64 Android build for the pull request. Exact run results are linked in the PR. Local dependency bootstrap was blocked by security review after an attempted cloud instance metadata request; no credentials were retrieved and the command was not bypassed.

## Device acceptance checks

These require an Android device/emulator and are not claimed as completed by static analysis or compilation:

1. Repeat connect/disconnect/reconnect while opening settings and changing TUN mode rapidly.
2. Switch Wi-Fi/mobile data, enable airplane mode during DNS lookup, and return to connectivity.
3. Pause/resume UI, lock/unlock screen and destroy/recreate the activity while VPN stays active.
4. Deny/grant notification and VPN permissions; time out a request before responding to its old dialog.
5. Start/stop from quick tile and shortcut; reboot and replace the installed package.
6. Update and edit the same profile concurrently; fail download/validation; verify old file and metadata survive.
7. Edit JSON containing quotes, backslashes and newlines; save the last keystroke; close during search.
8. Open/pause/close logs repeatedly and scan a QR code repeatedly; check memory use and navigation.
9. Include routing with zero installed applications must report a failure and never become all-app routing.

Native process termination (OOM, vendor restrictions, Go panic, JNI fault) still requires the actual crash trace for attribution. Kotlin exception handling cannot recover a process killed by the OS or a native fatal signal. File/metadata rollback handles runtime failures; a process kill between the file rename and database commit is not a cross-filesystem/database transaction.

The Android adapter requires API types absent in v4.1.0. The pinned compatible snapshot comes from the upstream draft release, fetched by immutable asset ID, never by a moving tag. If the supplier deletes that asset, dependency preparation fails explicitly. SHA-256: `4efd7799c53cacb26594a584ac7ae743dd7071901be8846af846bde4807bd1f1`.
