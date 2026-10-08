# Native Android bugfix audit — 2026-10-08

The requested target was 200 errors. This audit does **not** establish 200 independent
bugs. The existing `KOTLIN_DART_200_DIFFS.json` records parity differences, not a
new bug count. Changed lines, assertions and defensive checks are not counted as bugs.

## Corrected scenarios

| Area | Before | Correction / regression evidence |
| --- | --- | --- |
| IPv6 | The privacy switch changed a legacy preference while the effective JSON retained its old mode. | Update both settings in one transaction; service-mode tests. |
| IPv6 | Turning the switch off wrote the obsolete `auto` mode. | Use the editor/core's `prefer_ipv4` mode; service-mode tests. |
| Subscriptions | Encoded parent subscriptions hid nested URL lines from expansion. | Decode before expansion; subscription-content tests. |
| Subscriptions | Encoded downloaded child subscriptions remained encoded in the merged content. | Decode downloaded children; subscription-content tests. |
| Imports | URL-safe or whitespace-wrapped Base64 envelopes were not consistently decoded. | Shared decoder with BOM handling; subscription-content tests. |
| Subscriptions | Expansion charged replaced URL text as well as its downloaded replacement against the size limit. | Count actual UTF-8 output, including separators; exact-limit regression. |
| Imports | Invalid UTF-8 bytes were silently replaced, changing configuration content. | Strict decoding for files and downloads; malformed-input regression. |
| Import summary | JSON server definitions with different property order escaped duplicate detection. | Recursively canonicalize object keys; summary tests. |
| Import summary | Quoted YAML proxy types were not recognized. | Normalize quoted type values; summary tests. |
| Metadata | Indented subscription header comments were missed during header merging. | Trim leading whitespace before identifying headers. |
| Diagnostics | Configuration objects inside top-level JSON arrays did not expose their endpoints. | Walk array configurations; endpoint tests. |
| Diagnostics | Base64 configuration envelopes bypassed JSON chain inspection. | Decode before inspecting detours; endpoint tests. |
| Settings | Proxy privacy and regional routing changes omitted the reconnect indication. | Notify the UI after successful persistence. |
| Settings | Exceptions from generic setting writes escaped the event callback. | Report write failures through the existing error UI. |
| Chains | Deleting the selected chain profile left an enabled stage with a null reference. | Disable only the affected active profile stage; projection tests. |
| Chains | Missing or self-referencing profile stages could be saved and failed only at connection time. | Validate before saving and exclude the main profile from the picker; projection tests. |
| QR import | Transparent PNG backgrounds were interpreted as black because the luminance decoder ignores alpha. | Composite over white; transparent-image regression. |
| Diagnostics UI | Pending inspection state survived a switch to another outbound. | Key inspection state by outbound tag. |
| Numeric settings | Narrowing legacy Longs or fractional JSON numbers could produce a different valid port/value. | Validate exact integers before conversion; stored-number tests. |
| Listeners | A legacy zero port could appear enabled and be re-enabled by saving unrelated options. | Honor zero in JSON and legacy preferences. |
| Settings transfer | Import/reset and export serialization blocked the UI thread. | Perform disk/serialization work on the IO dispatcher. |
| Settings transfer | Import/reset could overlap native startup after the initial stopped-state check. | Recheck under the native lifecycle barrier. |
| Settings export | Activity recreation while the document picker was open lost the prepared payload. | Retain payload in a ViewModel. |
| Settings export | Repeated export taps could replace the payload of an existing picker request. | Guard preparation and outstanding requests. |
| Clipboard export | Large configuration payloads could exceed the clipboard IPC limit. | Apply the existing 256 KiB clipboard limit and offer file export in the error message. |
| Clipboard export | An unavailable clipboard service still produced a success toast. | Require the service before reporting success. |
| Connection policy | Changed core protocol policy lacked a reconnect indication. | Compare core policy and mark successful changes. |

Additional boundary hardening rejects invalid QR dimensions and nonpositive sampler
intervals. Export coroutine cancellation is propagated rather than shown as a write error.

## Validation

- `python tool/check_native_project.py`: resource references and standalone native project checks.
- `python -m unittest discover -s tool -p 'test_*.py'`: all 9 tests.
- `python tool/check_dart_parity_audit.py`: existing parity-audit integrity, not evidence of 200 repaired bugs.
- `git diff --check`: formatting checks.
- Portable Kotlin regressions and pinned-core checks run in GitHub Actions.

No Android SDK or Kotlin compiler is available in the local workspace. Activity
recreation, clipboard interactions and full APK compilation require Android/CI
validation; the portable tests do not claim device coverage.
