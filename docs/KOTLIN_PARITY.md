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
| Home/profile state | Connection failure color and device checks of connection/reconnect state | Same state, data and action targets as Dart HomePage/ProfileTile/ConnectionButton |
| Profile flows | Match import/add/detail/editor/QR sheets and dialogs, list container/navigation, sorting/filtering and confirmation behavior | Compare each original flow and round-trip identical profiles |
| Proxy presentation | Device checks of country/provider artwork, IP visibility and stream recovery; settings/dialog source audit | Same selected/active proxy, operations, layout and state transitions |
| Settings/navigation | Quick-settings rendering/dialog details; preference pages and picker/input/confirmation dialogs; back/deep-link behavior | One-to-one route/action/preferences audit against original widgets |
| Shared visual system | Remaining Material/Fluent icons, blur and motion in secondary screens; theme/spacing refinements | Device screenshot comparisons in light/dark/black themes, matching font scale |
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

The add form now uses native options/manual bottom sheets. QR scanner styling, exact empty-state artwork and exact sheet drag extents remain outstanding.
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
EN/RU text and help destination. The footer now has the Free switch; camera QR offers
both scanning and image import.

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


## Free providers and profile launch overrides

The Free switch loads the same remote feed as Dart, filters providers by the selected region,
and displays localized titles/tags with English fallback. Loading, empty, error and Retry
states are explicit. Each provider opens its consent dialog before importing, with a 12-hour
update interval and its requested WARP/Psiphon/Fragment flags. Missing feature lists retain
header fallback; empty lists explicitly disable those flags. Feed downloads have bounded
timeouts and the existing 8 MiB limit; closing the pane cancels its request.

Selected profile overrides now merge into the effective core options on startup in both
normal and root mode, without saving them into global preferences. Only the original allowed
configuration keys pass through; explicit user flags override header flags and Psiphon wins
when both security stages are requested. Deep merging retains unrelated security/TLS fields.
Refreshing a subscription retains the user's feature choices.

Consent rendering supports links, bold text, inline code, headings and basic bullets. Full
Markdown styling, feature icons, dynamic sheet extents and same-device screenshots remain
parity work. JVM regression tests cover feed schema/localization/regions and override precedence,
isolation and merging. Resource/source and whitespace checks pass; local Gradle tests remain
blocked by the unavailable Gradle download. APK compilation is not awaited.


## Proxy overview modal and live group updates

The home proxy footer now opens a draggable 900 dp maximum-width bottom sheet with an
85%-screen-height body, matching ProxiesModal instead of navigating to a full page. Its
overview consumes only the first OutboundsInfo group and keeps that group's full item list,
as in Dart watchGroup. Smart-selection/history consumers keep their existing snapshot API.
A shared mapper preserves selected child, traffic, test timestamps and IP metadata.

The sheet holds one live stream while its lifecycle is Started; closing/backgrounding cancels
the Wire call and its reader. Updates are conflated to retain the latest snapshot. Loading,
disconnected, empty, read failure and explicit Retry are separate states. Search follows
Dart tag/type matching; sorting keeps groups first and unknown delays last. Detail dialogs
resolve the current item by tag. Errors from actions use a Snackbar while the sheet is open.
Manual selection persists smart-selection=false before requesting the new outbound, including
when that request fails. Selection and URL-test RPCs have eight-second deadlines.

Country/provider artwork, stream reconnection/backoff, exact sheet insets and same-device
visual/interaction comparison still need work. NativeOutboundPresentationTest exercises raw
ordering/search, group precedence, unknown-delay ordering and usage overflow against the
production model. Native/resource and whitespace checks pass. JVM execution remains blocked
by the Gradle distribution download; no APK build is awaited.


## Offline proxy artwork and home interaction parity

The original 301 country/region SVGs from flutter_circle_flags at the Dart lockfile commit
`19d83cba` are converted to Android vectors, including ir-shir, unknown-region fallback,
rounded corners and masks/clips. Country badges use the original size-minus-8 artwork and
provider overlays at size/2.5. Eleven organization keywords preserve their precedence and
colors; eight brand paths are from the pinned Simple Icons snapshot, and cloud/storage use
the original Material shapes. The Fluent question-circle and WiFi glyphs and Material
add-moderator empty-state artwork replace approximations. The existing Emoji font is now
available to native IP text. Artwork is offline and has packaged license notices.

`tool/vendor_proxy_artwork.py` checks all three source repository commits before conversion.
The flag conversion was rendered and compared with the pinned SVGs using Cairo, with
opaque masks normalized to clips and a one-pixel antialias boundary excluded. This validates
asset geometry, not Android rendering or whole-screen screenshot parity.

The connection button now uses the active proxy delay (<=0 or >=65000 is amber), with
reconnect taking precedence. Idle/connected colors follow the actual AppTheme extension
(primary/tertiary), including Android dynamic colors. Native canvas uses surfaceContainerLowest
as in Dart. Color/scale motion is 180 ms, label fade/size/slide is 250 ms, and TalkBack
exploration disables those transitions; Compose also honors Android animator scale. Connecting
can be cancelled, clearing user intent before broadcasting stop so native startup sees it.
Always-on disconnection opens VPN settings; explicit reconnect remains available.

IP reveal state lives in an Activity ViewModel, survives rotation, and resets after 20 seconds
without a footer listener. Remaining on the screen does not time out the visible IP. Reveal
uses the original medium-impact Android haptic mapping and honors the stored haptic switch.
Inspection waits for an operation completion revision, then resolves refreshed active metadata;
it cannot open prematurely because a busy transition was missed by composition. Cancellation
is no longer presented as an outbound-operation error.

The primary-group stream reconnects after failure with 1/2/4/8/8-second backoff, retaining
the last snapshot and displaying progress. Five consecutive failures expose Error/Retry.
Receiving a snapshot resets the failure count; closing/backgrounding cancels the Wire read
and backoff. Resource/whitespace checks pass. The JVM task still cannot start because the
Gradle distribution download reports Network is unreachable. No APK build is awaited.

Remaining: secondary-screen icon/dialog audit, measured same-device layouts and interactions,
and connection-error presentation. The Simple Icons snapshot is newer than the Dart package;
brand path differences need device/source comparison before claiming exact provider parity.


## Quick settings chain diagram and LAN actions

The quick sheet now restores the Android phone/filtering icons, actual arrow directions,
webhook header, Psiphon/WARP/Profile mode icons and colors, and the original menu order.
Both active/inactive pills use their matching foreground colors. Final IP uses the original
light/dark green and a 500 ms opacity transition. Material rounded glyphs replace text arrows
and chevrons. LAN copy/QR actions include the original link/QR icons; their row wraps only
when width or font scale cannot accommodate both buttons.

The LAN password dialog opens with the currently saved value, focuses its LTR input and
supports IME Done. Cancel/back/outside dismissal discard the draft. Reset persists the empty
default and closes immediately, matching SettingInputDialog's reset callback. Invalid input
shows an error rather than silently dropping characters. Existing 128-character/control
validation remains. Reset/save retain the LAN switch state.

Copy/QR resolves the current hotspot/core LAN address at the time of the action, using the
existing bounded GetLANIP call, and reads the current persisted port/password. Password
whitespace is retained; UTF-8 credentials are percent encoded once, IPv6 hosts are bracketed,
and disabled/nonpositive mixed ports use the original 12334 fallback. Sheet dismissal cancels
its action scope; no late clipboard or QR action executes after cancellation. QR payload
still includes the original LAN-only title. The short QR link survives rotation.

Quick chain selection now merges only chain-status and the chosen stage's mode into fresh
persisted JSON. It does not rewrite WARP/Psiphon/profile fields from a UI snapshot. Disabling
an inactive stage preserves the active stage. Startup/stopping and settings transactions are
guarded; connected edits continue to request explicit reconnect. The full chain editor retains
its complete save operation.

Four JVM regression scenarios cover LAN URI fallback, whitespace/reserved characters, IPv6/
Unicode round trips and malformed authority rejection. Source/resource/whitespace checks pass.
JVM execution remains blocked by the unavailable Gradle distribution; no APK build is awaited.
Dialog typography, adaptive menu behavior and device screenshot parity remain to verify.

## General preference dialogs and immediate saving

The seven core general options now save per field from their dialog/switch, without a page-level
Save button or a forced VPN disconnect. Saving is guarded during startup, stopping, reconnect
and other settings transactions. Edits merge only the chosen key into fresh config JSON and
its legacy typed preference, preserving unknown and unrelated imported values. General options
are included in the applied-settings signature, so connected edits request reconnect and restoring
the applied value clears that requirement. Health monitoring and smart selection refresh after
saving. Snapshot refresh cannot overwrite an in-flight edit. Initial load failures expose Retry.

Choice rows save on selection. Reset saves the original field default and closes; Cancel discards
input. URL/port dialogs focus LTR input, support IME Done and show invalid-input feedback. URL
suggestions use the original case-insensitive filter and fallback. Interval editing matches the
1–60 minute, 60-division slider; imported second-level intervals remain intact until edited.
The page uses plain preference rows with the source Material rounded icons and a fixed header.
Language Reset selects English; theme Reset selects System, and theme rows are fully selectable.
Memory-limit checked state is the inverse of the stored disable flag. Enabling debug mode shows
the original restart notice before persisting the flag.

Monochrome converted vectors now have 24 dp intrinsic size, correcting oversized Wi-Fi and
LAN-action icons that relied on their resource dimensions. Flag vectors remain 48 dp, and
explicitly sized provider/chain icons retain their requested display size.

Four JVM regression scenarios cover scoped changes with legacy invalid values, URL validation,
invalid typed input and per-field defaults. They are registered in the standalone JVM suite;
execution remains unverified because the pinned Gradle distribution is unavailable locally.
Native source/resource, drawable/font-reference and whitespace checks pass. No APK build is
awaited. Full GeneralPage ordering/navigation, remaining application icons, original URL
suggestion popup geometry and same-device screenshot parity still need verification/work.
