# Android runtime performance audit — 2026-10-14

Base: `a6b2489d0a8fa7fc3cc9dd5b78430204db503b8e` (main).

## Changes

- Server rows: empty searches reuse the input list. Traffic sorting compares exact unsigned sums of nonnegative counters instead of allocating BigInteger values and a tag-keyed map. This also makes duplicate tags use their own counters. Group priority, unknown-delay order and stable ties are retained.
- Scrolling: preserve the previous visible order in O(items + previous order) instead of sorting again. Removed entries, newly discovered rows, repeated tags and repeated order entries retain the previous stable-sort behavior.
- JSON search: traverse children directly and stop at the existing 1000-result limit. Do not create a list of all children before searching, or allocate JSON pointers for primitive misses. Document order, escaped pointer keys and primitive/null matching are retained.
- Installed apps: share one metadata-only snapshot for 30 seconds, keyed by app identity and resource locale, with a monotonic clock. Concurrent requests share a single load. Package add/remove/change/replace broadcasts invalidate immediately; TTL is a fallback. Invalidation never waits on the package-manager loader. Results loaded across invalidation are not reusable, failures are retried, and loaders/Activity contexts are not retained. Routing selections and preferences are still read on each repository snapshot.
- Logs: retain a rolling window of 500 display lines while consuming the existing byte-bounded source tails; avoid accumulating every prefixed line before `takeLast`. Source presence and file order remain unchanged; published snapshots are independent copies.
- TUN construction: read IPv6/full-tunnel/public-DNS settings once per builder. Previously the IPv6 getter parsed core JSON repeatedly for individual addresses and routes. One builder now consistently uses the same three setting values; this is not an atomic snapshot across all preferences or a change to routing policy.

## Reproducible microbenchmark

Run from `android`: `./gradlew -p ../tool/kotlin-tests runtimeBench`.

The opt-in harness compiles the production functions directly, checks equality with the previous implementations, warms up each implementation 30 times and takes medians over nine alternating samples of 20 operations each. Allocation bytes are measured with JVM ThreadMXBean when available. No performance threshold is part of ordinary tests.

Local Java 17 / Kotlin 2.4.20 / Gson 2.14.0 results (one process, informational):

| Operation | Previous median µs | New median µs | Previous bytes/op | New bytes/op | Speed ratio |
|---|---:|---:|---:|---:|---:|
| Preserve scrolling order, 5000 servers | 2049.6 | 196.8 | 1,251,536 | 310,880 | 10.41× |
| Traffic sort, 5000 servers | 3163.3 | 730.8 | 913,864 | 45,784 | 4.33× |
| JSON hit, 50,000 values, first 1000 matches | 914.9 | 80.2 | 4,517,184 | 111,024 | 11.41× |
| JSON miss, 50,000 values | 8148.9 | 6230.4 | 6,854,184 | 2,400,024 | 1.31× |

These measurements describe the listed functions on a host JVM, not Android frame rates, battery use, connection startup latency, VPN throughput or native-core crash rates. Package-manager and TUN paths require Android-device measurements. Existing Android benchmark tests remain available for startup and navigation.

## Validation

- 391 JVM regression tests passed with assertions and coroutine debug stack recovery enabled, using the configured Kotlin 2.4.20, coroutines 1.11.0, Gson 2.14.0 and other project dependency versions.
- New coverage: exact traffic sums over random/signed-boundary/negative counters and duplicate tags; scrolling equivalence with duplicate and removed tags; JSON traversal equivalence and limits; cache expiry, locale keys, failed loads, single-flight concurrency and in-flight invalidation; bounded log-window equivalence across large sources.
- 24 Python tests passed; native project/resource validation and `git diff --check` passed.
- No Android device is attached in this environment. Full Android compilation and native checks are delegated to the pull-request CI; runtime VPN/reconnect/frame tests have not been executed locally.
