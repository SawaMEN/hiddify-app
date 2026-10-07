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
| Home/profile state | Exact empty-state shield artwork; free-provider feed/consent in the add sheet; connection color/reconnect state | Same state, data and action targets as Dart HomePage/ProfileTile/ConnectionButton |
| Profile flows | Match import/add/detail/editor/QR sheets and dialogs, list container/navigation, sorting/filtering and confirmation behavior | Compare each original flow and round-trip identical profiles |
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

## Shared profile tile and home states

NativeProfileTile now follows the actual HomePage ProfileTile (not the unused ProfileTileMain):
24 dp radius, surfaceContainerLow, active primary border at 55% opacity, 48 dp leading action,
active divider, 16/12 dp content padding, two-line name, home dropdown indicator and 6 dp
consumption progress. Home uses the persisted active profile object and connects the leading
update action independently of the overview action. List tiles use the same content and expose
update/share/edit/delete through an overflow menu instead of four permanent button rows.

Subscription text restores consumed/total GiB, the 10 TiB infinity threshold, expired/quota
precedence and remaining days, including the >365-day infinity label. Expiry parsing accepts
legacy local ISO dates and offset/UTC dates. Labels refresh every minute without downloading
the subscription; counter addition saturates on overflow. EN/RU labels follow Dart translations.

The home plus and the empty-state Add button now open the shared import form directly. Initial
profile reads show the original 56 dp progress area; an empty database shows the centered
72 dp icon/message/Add layout and hides connection controls. Import closes only after a
successful repository operation, preserving the draft after validation/network failures. A
successful profile selection returns from the list; rejected/failed selections leave it open.

The add form now uses native options/manual bottom sheets. Free-provider feed/consent,
QR scanner styling, exact empty-state artwork and exact sheet drag extents remain outstanding.
Initial read-error/retry states are implemented. Source/resource checks alone do not establish
measured visual parity.

## Profile options/manual sheets and list controls

The add action now opens a 456 dp maximum-width, 32 dp top-radius bottom sheet with four
18 dp source tiles: clipboard, file, camera QR and manual. Clipboard/file/QR launch the
repository import directly, as in Dart, instead of requiring a second submission from a
multiline input dialog. QR image import remains available as an additional action. File
reads remain limited to 8 MiB on IO, image processing stays on IO, cancelled pickers do
nothing, and failed imports retain their source for Retry. The sheet closes only after
the Activity publishes a successful import revision. Busy operations show the original text/linear-progress arrangement. Import cancellation
still needs a repository-level cancellation path; the busy sheet currently stays open.

Manual import has required name and HTTP(S) URL fields, the disable-auto-update switch,
a discrete 0–96 hour slider and an Auto value that leaves subscription timing to metadata.
Selecting Auto explicitly clears a previous manual interval when reimporting the same URL.
Manual drafts survive rotation; large configuration contents are excluded from saved state.
The close icon returns to options. Help and the home empty-state paragraph use the original
EN/RU text and help destination. Free providers and their consent/feature overrides still
need implementation; the current image action occupies the footer where Dart has Free.

Home profile selection opens a bottom sheet; settings retains the full list page. Both use
12 dp list spacing, shared tiles, a last-update/name sort with direction, and Update subscriptions.
Active and usable profiles keep priority before the chosen comparator, matching Dart.
Sorting defaults to newest first and is shared across both views during the Activity session;
its labels describe the actual comparator rather than reproducing the inverted Dart labels.
Full-page controls use the 56 dp app bar and add FAB. Selection success closes the sheet;
failed selections leave it open. An empty list closes the selection sheet. Delete/share dialogs
save IDs and resolve refreshed profile objects rather than retaining stale snapshots.

Bulk updates process every remote subscription under the existing profile-operation mutex,
including subscriptions with automatic updates disabled. Failures do not discard successful
updates: the Activity reloads the database and then displays the failure. Initial database read
failures have a separate error/Retry state in home, full list and sheet, rather than appearing
as an empty database. Profile snapshot reads also use the operation mutex.

`tool/check_native_project.py` and `git diff --check` pass. Local Kotlin compilation cannot
start because the pinned Gradle download reports `Network is unreachable`. CI compilation
and same-device screenshots remain necessary. The Material partial-sheet anchor currently
differs from Dart's 35% initial extent; this step does not establish pixel-identical rendering.


## Native profile details and JSON editor

Profile details now uses the Dart 56 dp app bar with Save, the name/URL fields at 16 dp
insets, a selectable full subscription URL, automatic-update switch and interval slider,
last-update row, subscription counters/expiry, and a configuration editor occupying 70%
of the screen height. Existing intervals above 96 hours are retained and expand the slider
range. Save is enabled only for changed, non-empty, valid content; a successful Activity
save revision returns to the previous page. Load failures have an explicit retry state.

NativeJsonEditor restores tree/text modes, expansion/collapse, key/value search with
previous/next matches, copy/format, key renaming, primitive editing, adding objects/arrays/
strings/booleans/numbers/nulls, deleting items, protocol insertion and settings templates.
The 16 protocol templates, 3 settings-template groups and 14 value-choice lists are copied
from the Dart reference into an offline asset. Tree rows retain the 18 dp nesting and 30 dp
base height, with object type/tag summaries. Tree building, parsing, search, formatting and
mutations run off the main thread. A lazy list composes only visible tree rows. Text edits
invalidate Save immediately, before the debounced parser publishes its result.

JSON-pointer operations preserve escaped keys, null values and arbitrary numeric precision;
failed key collisions leave the original document unchanged. Strict parsing rejects trailing
content and malformed JSON. Existing 8 MiB file limits remain, and nesting is bounded to 128
levels. Clipboard copying retains the existing 256 KiB budget. Template merges explicitly
confirm replacement of fields included in the template. Plain configuration formats retain
a text editor.

A ViewModel retains the editor draft across Activity recreation without putting large
configuration contents in a Bundle. The Activity restores the editor profile ID and reloads
its source after recreation/process restart. Unsaved contents survive rotation, but they
are not persisted across process death. Keys, strings and numbers edit inline on focus loss; booleans use an inline checkbox and
known string fields use their value-choice dropdowns. Creation/null editing uses dialogs.
Exact typography/icons/header controls and device comparison remain parity work.

Seven NativeJsonDocumentTest scenarios cover exact number/null round trips, escaped pointers,
array mutation, failed collision atomicity, strict parsing, search, template isolation and
nesting. The JVM suite compiles the production model directly. Native/resource and whitespace
checks pass. Local tests could not start because the Gradle distribution download reports
Network is unreachable. Per user instruction, APK compilation is not awaited for this step.
