# Android runtime performance audit — 2026-10-14

Base: `a6b2489d0a8fa7fc3cc9dd5b78430204db503b8e` (main).

## Changes

- Server rows: empty searches reuse the input list. Traffic sorting compares exact unsigned sums of nonnegative counters instead of allocating BigInteger values and a tag-keyed map. This also makes duplicate tags use their own counters. Group priority, unknown-delay order and stable ties are retained.
- Scrolling: preserve the previous visible order in O(items + previous order) instead of sorting again. Removed entries, newly discovered rows, repeated tags and repeated order entries retain the previous stable-sort behavior.
- JSON search: traverse children directly and stop at the existing 1000-result limit. Do not create a list of all children before searching, or allocate JSON pointers for primitive misses. Document order, escaped pointer keys and primitive/null matching are retained.
- Installed apps: share one metadata-only snapshot for 30 seconds, keyed by app identity and resource locale, with a monotonic clock. Concurrent requests share a single load. Package add/remove/change/replace broadcasts invalidate immediately; TTL is a fallback. Invalidation never waits on the package-manager loader. Results loaded across invalidation are not reusable, failures are retried, and loaders/Activity contexts are not retained. Routing selections and preferences are still read on each repository snapshot.
- Logs: cache up to 16 source tails (the previous capacity of 8 thrashed when all 9 possible sources were present), and reuse the parsed 500-line display tail until metadata/limits/expiry change. Retain a rolling window of 500 display lines while consuming the existing byte-bounded source tails; avoid accumulating every prefixed line before `takeLast`. Source presence and file order remain unchanged; published snapshots are independent copies.
- TUN construction: read IPv6/full-tunnel/public-DNS settings once per builder. Previously the IPv6 getter parsed core JSON repeatedly for individual addresses and routes. One builder now consistently uses the same three setting values; this is not an atomic snapshot across all preferences or a change to routing policy.

- Subscription refresh: connect coroutine cancellation to the existing per-operation HTTP/native-validation token. A separate IO worker allows the cancellation waiter to cancel blocking calls immediately; structured completion waits for worker cleanup before releasing the profile mutex. Cancellation is never collected as an ordinary subscription failure and does not start subsequent batch items. Transactions already past `beginCommit` still finish atomically.
- Foreground profile mutations cancel the automatic batch before acquiring the operation mutex, then resume automatic updates when finished in the foreground. This prevents a manual selection/import/deletion from waiting behind an entire slow background batch.
- Empty auto-update batches skip additional profile-list reloads and reconnect-signature work. Attempted batches still resynchronize and reload even after failures, since a post-commit settings/file error can follow a successful DB write. Initial active-profile synchronization belongs to `onStart`; an `onCreate` snapshot no longer starts just to be cancelled by `onStart`.
- Repository instances share the subscription HTTP connection pool and dispatcher instead of creating a new base client for each settings/profile access. Header overrides and nested target validation remain scoped to individual requests/repositories.

## Reproducible microbenchmark

Run from `android`: `./gradlew -p ../tool/kotlin-tests runtimeBench`.

The opt-in harness compiles the production functions directly, checks equality with the previous implementations, warms up each implementation 30 times and takes medians over nine alternating samples of 20 operations each. Allocation bytes are measured with JVM ThreadMXBean when available. No performance threshold is part of ordinary tests.

Local Java 17 / Kotlin 2.4.20 / Gson 2.14.0 results (one process, informational):

| Operation | Previous median µs | New median µs | Previous bytes/op | New bytes/op | Speed ratio |
|---|---:|---:|---:|---:|---:|
| Preserve scrolling order, 5000 servers | 2091.7 | 188.4 | 1,251,536 | 310,880 | 11.10× |
| Traffic sort, 5000 servers | 3471.8 | 738.4 | 913,864 | 45,784 | 4.70× |
| JSON hit, 50,000 values, first 1000 matches | 1139.7 | 106.8 | 4,517,184 | 111,024 | 10.67× |
| JSON miss, 50,000 values | 8701.9 | 6739.5 | 6,854,184 | 2,400,024 | 1.29× |
| Display unchanged logs, 9 sources × 5000 lines | 4021.0 | 55.5 | 11,847,792 | 10,624 | 72.51× |

The unchanged-log comparison deliberately gives both implementations the corrected 16-entry byte-tail cache, isolating the improvement from parsed-line reuse. Sources fit within the production 256 KiB tail limit. File expiry is held fixed for this workload; updated logs still require parsing.

These measurements describe the listed functions on a host JVM, not Android frame rates, battery use, connection startup latency, VPN throughput or native-core crash rates. Package-manager and TUN paths require Android-device measurements. Existing Android benchmark tests remain available for startup and navigation.

## Validation

- 400 JVM regression tests passed with assertions and coroutine debug stack recovery enabled, using the configured Kotlin 2.4.20, coroutines 1.11.0, Gson 2.14.0 and other project dependency versions.
- New coverage: exact traffic sums over random/signed-boundary/negative counters and duplicate tags; scrolling equivalence with duplicate and removed tags; JSON traversal equivalence and limits; cache expiry, locale keys, failed loads, single-flight concurrency and in-flight invalidation; bounded log-window equivalence across large sources; nine-source cache reuse/eviction, parsed-line invalidation; real blocked HTTP cancellation, worker cleanup and commit/mutex ordering; batch partial failure and cancellation.
- 24 Python tests passed; native project/resource validation and `git diff --check` passed.
- No Android device is attached in this environment. CI successfully built and verified the initial Android ARM64 APK at commit `c1d937c9`, including Go policy regressions. Full Android compilation and native checks for the follow-up changes run again in pull-request CI; runtime VPN/reconnect/frame tests have not been executed locally.

## Scoped outbound RPC follow-up — 2026-10-15

- Updated the working branch with main's sheet/TLS/WARP fixes at `5888f0e2`, preserving both sets of changes.
- Outbound snapshots and the shared primary-group stream use Wire 7.1.0 `executeIn` channels in a structured coroutine scope. Cancellation closes both directions and the transport; a first-response snapshot cancels the remaining stream immediately. Normal empty EOF and transport errors remain distinct.
- Active-server polling converts only the first item of the first group, preserving the previous RPC/selection semantics without allocating native models for all other groups/items.
- Manual selection and URL-test RPCs use suspending execution. Snapshot/operation jobs are cancelled on backgrounding or leaving Started; request-generation, connection-session and profile guards prevent stale UI results and obsolete finally blocks from clearing a newer busy indicator. Polling also checks the connection session before publishing.
- Six new channel regression tests cover first-result cleanup, normal EOF versus errors, cancellation while sending/receiving, scoped child cleanup, opening failures and flow short-circuiting. The merged JVM suite passed 412 tests; all 24 Python tests, resource/project checks and whitespace validation passed.
- The preceding PR head `dc7c849a` passed full CI, including the ARM64 APK and native-core race regressions. Android adapter compilation for this follow-up runs again in CI; device connection/reconnect tests remain unverified. No Android latency or battery improvement is claimed from these changes without device measurements.
