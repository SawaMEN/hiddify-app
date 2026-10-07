# Kotlin parity work remaining

Reference: Android Dart UI/behavior at `8d8655916b115417e1f736966108c29ca87ba530`.
Scope: preserving appearance and behavior, not merely eliminating `.dart` files.

The app is already standalone Kotlin/Compose. VPN/profile/settings/diagnostics/routing/QR,
privacy and sharing implementations exist. There are no remaining Dart sources to translate
literally, but deleting them did not establish feature or screenshot parity. The Go VPN engine
is intentionally a native dependency, as it was in the Dart application.

This is a source-review backlog, not a complete measured feature inventory or a percentage.
Seven workstreams remain; a stream can contain multiple implementation/review iterations.

| Workstream | Known work remaining | Acceptance evidence |
| --- | --- | --- |
| Home/profile state | Subscription content/actions on the profile card; empty/loading/error states; add action opening the original import sheet; connection color/reconnect state | Same state, data and action targets as Dart HomePage/ProfileTile/ConnectionButton |
| Profile flows | Match import/add/list/detail/editor/QR sheets and dialogs, subscription metadata presentation, sorting/filtering and confirmation behavior | Compare each original flow and round-trip identical profiles |
| Proxy presentation | Original circular country/provider artwork; proxy modal and primary-group-only overview; IP visibility lifetime/haptics; live stream timing and URL-test error/inspection sequence | Same selected/active proxy, operations, layout and state transitions |
| Settings/navigation | Original quick-settings bottom sheet with service/LAN/chain controls; preference pages and picker/input/confirmation dialogs; back/deep-link behavior | One-to-one route/action/preferences audit against original widgets |
| Shared visual system | Exact Material/Fluent/provider icons, Emoji font where used, blur, shimmer and motion; theme/spacing refinements across remaining screens | Device screenshot comparisons in light/dark/black themes, matching font scale |
| Functional edge cases | Audit IP auto-check/manual refresh, notices/consent, subscriptions/errors, profile upgrades and stored preferences; reconnect/network switching/background operation/root/sharing | Reproduce original user-visible behavior without changing persisted settings |
| Device parity verification | Same-device screenshots and interaction comparison; small/large widths, landscape, Russian/English, font scaling and accessibility | Recorded visual comparisons and scenario results; successful CI alone is insufficient |

## Current step

NativeActiveProxyHome restores active proxy name, IP/protocol row, 48 dp country badge area,
proxy info action, delay/timeout/testing presentation and IP masking/reveal. It uses the original
MainOutboundsInfo source and UrlTest("") behavior, rather than inferring the active outbound from
selector names or displaying a generic navigation row. Background data polling has a bounded
RPC deadline and is stopped with the Activity. Flag artwork, exact wifi icon/shimmer/blur,
IP visibility lifetime/haptics and failure handling still need source/device parity refinement.

## Estimation

Language/runtime cutover is complete. UI and behavior parity require at least the seven streams
above; no defensible completion percentage or calendar duration is available before the full
route/preference/scenario inventory and device comparison. Do not equate build success with
an indistinguishable application. Use this checklist to report concrete completed items.
