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
| Home/profile state | Device checks of connection/reconnect state | Same state, data and action targets as Dart HomePage/ProfileTile/ConnectionButton |
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
the Activity publishes a successful import revision. Busy operations show the original text/linear-progress arrangement. Import cancellation now has a repository-level path; the busy sheet stays open until
the cancelled request exits, then restores its existing draft.

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

## DNS preference parity and editable suggestions

The DNS page now follows the original five-row order, with the rounded VPN lock, bidirectional
sync, private connectivity and public icons. Plain preference rows replace draft cards and the
page-level Save button. Its header stays outside the scrolling content. DNS protection settings
remain reachable from the DNS category on the privacy page, rather than adding a sixth row to
DnsOptionsPage. Address inputs focus LTR text and support IME Done; choice rows persist immediately.
Reset persists only that field's original default (including the empty Auto strategy) and closes.
Cancel/back/dismissal leave the saved value intact. Initial loading errors show Retry.

DNS writes merge one chosen key into freshly read core JSON and its legacy typed preference.
Unrelated options, custom resolver schemes and unedited imported addresses are preserved. The
existing nonempty/2048-character/control-character checks apply only to the edited address.
Connected changes request reconnect through the applied-settings signature; startup, stopping,
reconnect and concurrent settings transactions block edits. Snapshot refresh skips an active save.

General and DNS pages now share a preference row and an editable input component. Suggestions
use a dropdown anchored to the field, remain editable with the keyboard open, and follow Dart's
case-insensitive contains filter and pattern-plus-presets fallback. Selecting a suggestion updates
the draft; persistence still requires OK/IME Done. Suggestion text uses bodySmall, LTR and the
source's 10/3 dp item padding. The shared general input also restores the Xray explanation and
corrects the loading-error text that previously described Android application settings.

Four DNS JVM scenarios cover scoped edits with legacy values, bare/IPv6/custom resolvers,
invalid input and reset isolation. They are registered in the JVM suite but have not run locally:
the pinned Gradle distribution remains unavailable. Native boundary/resource, drawable/font
reference and whitespace checks pass. APK completion is not awaited. Dropdown placement with
IME, accessibility, large text and same-device visual/interaction parity still require device QA.

## TLS tricks preferences and nested field saving

The TLS page restores the original six plain rows, rounded cut/ruler/snooze/text/expand icons
and exact EN/RU captions. Its header remains outside the scrolling body. The explanatory card,
extra subtitles and page-level Save button are removed. Fragmentation gates all five subordinate
controls, including the padding size when padding itself is off. That dependency is rechecked
against persisted state under the native lifecycle barrier before a subordinate field is saved.

Switches save immediately. Range dialogs focus their LTR editable input, support IME Done and
persist on OK. Reset writes the original range default and closes; cancellation leaves stored
values unchanged. Display/input formatting canonicalizes valid numeric ranges while preserving
unedited imported values in storage. Empty fragment size/sleep display Not set; empty padding
keeps the original empty subtitle. Imported empty ranges stay valid, but a new empty edit is
rejected, matching OptionalRange.tryParse. The existing Kotlin signed-32-bit/21-character bound
is retained; Dart's wider integer edge cases remain to audit against downstream core consumers.

TLS saves modify only the chosen key inside tls-tricks and mirror its separately named legacy
Flutter preference. All unrelated nested/root keys and switch/range values remain untouched.
Core JSON field names were checked against pinned hiddify-core ffc52b3 v2/config/hiddify_option.go.
Connected changes use the applied-settings signature to request reconnect. Startup, stopping,
reconnect and other settings transactions block saves; snapshot reload skips an in-flight edit.
Initial TLS load failures expose Retry rather than an endless indicator.

Five JVM scenarios cover switch isolation with legacy ranges, canonicalization and bounds,
invalid ranges/booleans, empty imported ranges and per-field reset isolation. The production
model is registered in the standalone JVM suite. Native source/resource, drawable/font-reference
and whitespace checks pass; JVM execution is still blocked by the unavailable pinned Gradle
distribution. No APK completion is awaited. Same-device visuals, focus/keyboard/accessibility
behavior and remaining settings/navigation parity still need verification/work.


## VPN privacy overview layout and controls

`NativePrivacyOverviewScreen` now mirrors `VpnPrivacyOverviewPage` and `HandbookRoutingTile`
at the parent of Flutter removal commit `7197f9e4`. The fixed app bar, 16/12/16/28 body
padding, status icon container, routing badge and package counts, 52/48 dp setup/restore buttons,
four feature rows and indented dividers, protocol card, conditional UDP row, section captions,
four expandable categories and final limitations text follow the source order. Titles and
summaries use the original English/Russian copy. Material SVG paths are vendored as Android
vectors from Google's material-design-icons repository (Apache-2.0; license in docs/licenses).
Expanded headers and switches use the original theme colors, sizes and 200 ms expansion;
reduced-motion settings disable that animation.

The category children operate existing Kotlin preferences directly: My Handbook and its
conditional list controls/domain dialogs/catalogue links; independent Russian network/apps/
restricted-service toggles; adaptive networking/full tunnel/root; encrypted/public DNS;
local proxy/Clash API/system proxy. Feature rows open the existing direct/proxy app selectors.
Root discovery runs on IO without invoking su. Routing status/counts refresh after selection,
setup, routing, full-tunnel and Handbook changes. The setup status also refreshes after
connection-policy and regional preference saves. In-flight preference transactions disable
controls; existing native restrictions on setup/restore and root mode remain in place.

Native boundary/resource, all screen string/drawable references and whitespace checks pass.
No APK build was started. Compose previews cover light/dark themes; rendered previews,
Kotlin/Android compilation and same-device screenshot/interaction parity remain unverified.
The existing Kotlin requirement to disconnect before automatic setup/root changes is retained.


## QR scanner screen parity

The Scan tile now opens a full-screen Compose dialog directly, matching the active
`QrCodeScannerDialog` in `lib/features/common/qr_code_scanner_screen.dart` before Flutter
removal (`7197f9e4^`). It restores the safe-area camera preview with cover scaling, centered
70%-of-screen-width frame, 16 dp corners, 4 dp primaryContainer border and the top-start
48 dp circular close control with an 8 dp margin. The loading spinner and exact EN/RU
permission-denied text replace ZXing's standard CaptureActivity prompt/laser/finder UI.
No torch/camera-switch toolbar is added: those controls only existed in commented-out Dart.

The existing ZXing BarcodeView decodes QR codes across the full visible preview; the frame
is an overlay rather than a crop, just as the source MobileScanner with no scanWindow.
Only the first nonempty result is imported verbatim. Close/system Back accept cancellation
once and release the camera. Preview/decoding stop on pause/disposal and restart on resume;
camera permission is requested on opening and rechecked after returning from settings.
The existing QR-from-image import remains available through File's image document selection,
so choosing Scan no longer opens an extra camera/image choice dialog.

JourneyApps 4.3.0 CameraPreview/BarcodeView/callback/decoder APIs and MobileScanner 7.2.0's
cover/placeholder/error/overlay ordering were checked against their upstream source.
Native boundary/resource and whitespace checks pass. Light/dark/permission-error Compose
previews are included. APK compilation was not started; camera hardware, permission round
trips, lifecycle/rotation behavior and same-device screenshot parity still need device QA.


## Main settings screen and options menu

The mobile `SettingsPage` from `7197f9e4^` is now mirrored by a dedicated
`NativeSettingsOverviewScreen`: Profiles, General, conditional Chain (with its original
subtitle), DNS, Inbound, TLS tricks, Logs and About. The native-only VPN/core/routing/transfer
cards no longer alter that list. The app bar stays outside the scrolling body; body padding
is 20/12/20/24 dp, cards have 12 dp bottom gaps and 24 dp glass corners, and ListTile content
uses the original 20/8 dp padding and bodyLarge/bodyMedium text. Ten original rounded
Material icons are converted to Android vectors with transparent SVG paths excluded.

The app bar restores the more-vertical menu and Import/Export submenus. Both import sources
require the source's confirmation message before calling the existing import action. Four
export entries distinguish anonymous/all options and clipboard/file; Reset calls the existing
native reset action directly, as the Dart menu did. Menu mutation actions are disabled during
in-flight native settings transactions. Route callbacks, has-profile visibility and the
include-private export flags are connected to the existing native state and repositories.

Native boundary/resources, all new string/drawable references and whitespace checks pass.
Light/dark Compose previews are provided. No APK build was started; Android compilation,
submenu placement/keyboard/accessibility and same-device screenshot parity remain unverified.
This iteration covers the main Settings tab; the General/Chain/Inbound child pages remain
separate parity work. Profile/Logs/About cards remain available on wide Android windows until
the original tablet/desktop navigation rail is ported. Existing native import/reset restrictions
and repository behavior are retained, including the requirement to disconnect first.


## Main home screen

The remaining HomePage differences from `7197f9e4^` are ported: original rounded
health/add/power/tune/arrow icons and outlined empty-profile icon, version badge,
empty/no-active profile text with preserved paragraph breaks, the choose-profile notice,
connection failure accent and source status/recovery labels. Latency uses a single rich
text span with the original space before ms. The speed card has 20 dp corners and shows
the original selection reason after a successful automatic server selection. Connection
errors are tracked independently of the dismissible error dialog; restarting clears them.

Light/dark/black previews now include an actual profile and active outbound; empty and
connection-error previews cover the additional states. Native boundary, resource-reference
and whitespace checks pass. No APK build was started. Android compilation, runtime
animations and same-device screenshot parity still require device verification.


## Connection and profile deletion functionality fixes

Android no longer waits for the lifecycle mutex to cancel a blocking `Mobile.start()`.
`Mobile.stop()` cancels Go's startup context concurrently; cancellation is repeated while
startup is active to cover a stop arriving just before Go installs that context. Cleanup
waits for that cancellation worker before releasing ownership or reusing the service.
Destroy/revoke and explicit stop also cancel startup. Native close retries once after a
teardown error, avoiding ownership stuck on a service still bound by the UI. Expected cancellation is cleaned up
without presenting a startup error, and initial setup/configuration errors no longer trigger
an automatic retry loop. Unexpected failures after an established connection retain recovery.

MainActivity marks issued starts immediately, ignores initial binding Stopped snapshots,
blocks duplicate starts and allows stop during pending permission/start/recovery/reconnect.
Stopping cancels queued reconnect and recovery work, clears user intent and ignores stale
Starting/Started callbacks. A start with no running service or owner after 20 seconds returns
a useful error instead of leaving the UI pending indefinitely. Connection start cannot race
profile edits/deletion; offline mutations also acquire the native lifecycle barrier.

Deleting an inactive profile no longer requires disconnecting. Deleting the active or a
chain-referenced profile stops VPN first and waits up to 30 seconds for status and ownership
to clear. Timeout leaves the profile intact. The config file is renamed before the database
transaction, restored on transaction failure, and cleaned up after commit. Chain profile
references move to the next active profile, matching the original Dart deletion flow.

The standalone `tool/check_connection_functionality.kt` runner compiles production helpers
and passes regression checks for initial binding snapshots, failed/retried startup, cancellation
arriving before Go startup, successful file removal, rollback, missing config and invalid file
paths. It was run with Kotlin 2.2.0 and coroutines 1.8.0. All production Kotlin files also pass
PSI syntax parsing, native boundary/resource checks and whitespace checks. No APK was built.
These checks do not establish Android type/link compatibility or on-device VPN connectivity;
real tunnel/permission/OEM behavior and reported device errors still require runtime validation.


## Cancellable profile imports

The adding state exposes Cancel for ordinary and free-provider imports. Each import owns a
separate cancellation token. It cancels the active OkHttp call (including response-body reads),
checks between nested subscriptions and blocks profile persistence when cancellation wins.
Cancellation does not surface a network-error dialog or advance the successful-import revision;
the sheet restores its retained options/manual draft for retry. Busy sheets reject drag dismissal
until their operation finishes, preventing a hidden sheet with an in-flight import.

Cancellation and the start of file/database replacement share one lock. If replacement already
began, it finishes through the existing rollback-capable transaction; late cancellation cannot
leave half a profile. Activity destruction cancels an unfinished import before cancelling its
lifecycle scope. Independent automatic subscription updates are unaffected. File-picker reads
and platform DNS resolution are still synchronous IO work: this change does not claim immediate
cancellation of those operations or preservation of large drafts across process death.

Five JVM tests cover cancellation before request attachment, nested-request isolation, late
cancellation during commit, independent retries and 250 concurrent cancel/commit races. Local
execution could not start because the pinned Gradle download reports `Network is unreachable`.
Native/resource and whitespace checks pass. APK compilation and device parity are not awaited.


## Import preview and confirmation

The Dart ImportSummary/showImportPreview flow now has a native counterpart. Remote/local and
free-provider imports prepare their source, metadata and aggregate summary before persistence.
An AlertDialog restores the original title, four summary lines, 16 dp gaps, scrollable 440 dp
content, Cancel/Import actions and unencrypted-HTTP warning. Outside taps do not dismiss it;
Back and Cancel reject the import. Confirmation uses the already downloaded source rather than
fetching a potentially changed subscription again. Automatic subscription refresh remains
noninteractive. Native strings match the original EN/RU labels, except the hint accurately asks
the user to review the configuration rather than claiming unimplemented core validation.

JSON counts both outbounds and endpoints, including sing-box 1.15 MASQUE endpoint types and
Xray protocol entries. Utility outbounds do not count. JSON fingerprints ignore tag/name; link
fingerprints ignore fragments. Base64 subscriptions and YAML type/routing estimates follow the
original parser. The 39-type supported catalogue matches the Dart reference. Only aggregate
counts and override names reach the UI; source credentials and configuration stay in the
operation's memory and are not put in saved-instance state.

The confirmation await is suspending, with no blocking UI/IO thread. Its decision is tied to
the request-scoped cancellation token, so cancellation or Activity destruction clears the dialog
and cannot commit the prepared profile. The existing profile-operation and native lifecycle
locks remain held to prevent an active-profile/startup change during review. A confirmed commit
uses the previous cancel/commit arbitration and transactional replacement.

Six NativeImportSummaryTest cases cover JSON/endpoints, duplicates, unknown types, fragment and
credential handling, Base64 links, YAML, header names/HTTP warning and malformed inputs. The JVM
test command was attempted but could not download Gradle (`Network is unreachable`). Native
resource/boundary checks, protocol-catalogue comparison and whitespace checks pass. APK build
and on-device rendering are not awaited. The next stage adds native core validation below; full small-screen/device parity remains
outstanding.


## Core validation before persistence

All native profile imports, automatic/manual subscription refreshes and editor saves now validate
the staged source through the pinned hiddify-core parser and sing-box CheckConfigOptions before
file/database replacement. Invalid source, malformed setting types and parser failures preserve
the existing profile. Import previews are shown only after validation succeeds, so the original
Dart EN/RU hint stating core validation is restored. Profile-specific options are merged into a
private JSON snapshot; they are not applied to the active service or saved as global preferences.

The core adds BeginProfileValidation/ValidateProfile/CancelProfileValidation/FinishProfileValidation
to the gomobile API. Each bounded session has its own libbox context and 45-second deadline,
registered before parser entry. It uses DefaultHiddifyOptions plus the supplied typed options
and the same ParseConfig/CheckConfigOptions path as Parse RPC. It never calls Setup, Start, Stop,
ChangeHiddifySettings or database persistence. Cancellation checks bracket parsing and use the
parser context; legacy parser helpers that do not observe context can delay return, but cancelled
validation cannot proceed to preview or commit. No independent TUN or proxy service is started.

The Kotlin bridge attaches the import token to the native session and releases it in finally,
including cancellation before JNI entry, parser exceptions and a native call returning after
cancellation. Five JVM tests cover these cases. Six Go tests cover supported JSON shapes, invalid
source/types, size bounds, cancellation isolation, registration cleanup, session limits and expiry.
The Android CI core-test step now includes platform/mobile. The app pins the new core commit;
new bindings require rebuilding the AAR through the existing build pipeline.

Native/resource checks and whitespace checks pass. JVM tests could not start because Gradle
download is blocked; Go tests could not run because this environment has no Go toolchain. No
APK build is awaited. Native linking, actual parser acceptance across protocol formats and same-
device visual/interaction comparison remain CI/device verification work.
