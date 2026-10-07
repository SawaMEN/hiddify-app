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
| Settings/navigation | Quick-settings rendering/dialog details; preference pages and picker/input/confirmation dialogs; back/deep-link behavior | One-to-one route/action/preferences audit against original widgets |
| Shared visual system | Exact Material/Fluent/provider icons, Emoji font where used, blur, shimmer and motion; theme/spacing refinements across remaining screens | Device screenshot comparisons in light/dark/black themes, matching font scale |
| Functional edge cases | Audit IP auto-check/manual refresh, notices/consent, subscriptions/errors, profile upgrades and stored preferences; reconnect/network switching/background operation/root/sharing | Reproduce original user-visible behavior without changing persisted settings |
| Device parity verification | Same-device screenshots and interaction comparison; small/large widths, landscape, Russian/English, font scaling and accessibility | Recorded visual comparisons and scenario results; successful CI alone is insufficient |

## Quick settings follow-up

The home quick-settings action now opens a Material 3 bottom sheet instead of the VPN settings
page. Android Proxy/VPN segments, LAN switch/password input, SOCKS link/QR actions and the
Extra Security/Main Profile/Unblocker chain controls are connected to native persistence.
Disabling an inactive chain stage leaves the active stage unchanged, matching ChainModeMenu.
Chain mode selection changes only that stage and the chain status, preserving its configuration.

Quick mode/LAN changes while connected are saved for the next core startup. A persisted dirty
flag changes the connection button to Reconnect; explicit reconnect stops the old service before
rebinding to the selected mode and obtaining VPN permission through the normal startup path.
Successful core startup records an applied-settings signature and clears the flag; a failed
restart retains it. Reverting quick edits to the applied values clears the reconnect indication. Recovery is suppressed during
the explicit stop/rebind sequence. LAN edits merge only LAN keys and allow LAN listeners through
the effective local-proxy privacy policy. Hotspot sharing keeps the switch locked as in Dart.

GetLANIP uses an eight-second deadline and avoids stale inactive-hotspot addresses. Links encode
passwords and bracket IPv6 hosts. QR data preserves the original LAN-only profile title. Exact
menu icons, chain diagram/motion, LAN dialog reset defaults and same-device parity remain to verify.
Existing native forms still have their independent disconnected-only validation rules.

## Active proxy step

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
