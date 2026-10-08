# Kotlin visual and interaction audit

Reference: Dart UI at `8d8655916b115417e1f736966108c29ca87ba530`.

This is a source audit, not device screenshot verification. All entry points below were
inventoried for shared typography, drawable references, content-color inheritance and headers.

## Shared fixes

- The bundled Manrope variable font defaults to weight 200. Reusing it for every declared
  Compose weight produced much thinner text than intended. Typography now uses actual static
  400/500/600/700 instances derived from that same bundled font, including Cyrillic. The
  original variable source and licence are retained.
- Cards/glass explicitly pair their surfaces with onSurface content. Shared input text, labels
  and placeholders use the original foreground roles. Dynamic Android palettes stay paired.
- Eleven secondary screens replace oversized title/text-back rows with the shared 56 dp
  toolbar and back icon. Shared navigation icons use explicit 24 dp dimensions.
- Installed-app lists restore real 48 dp package icons, loaded on IO with a bounded 4 MiB cache
  and visible fallback. Icons never enter saved-instance state. Per-app rows toggle once with
  a trailing checkbox, retaining the existing manual/automatic routing semantics.
- JSON editor previous/next/copy/dropdown actions use vectors instead of font glyphs.
- Supplemental setting switches have leading artwork while retaining their existing callbacks.
- Logs restore pause/resume, message/severity filters, newest-at-bottom display, compact icon
  actions and readable monospace text. Terminal ANSI sequences no longer appear as broken
  glyphs. Clearing while paused clears the held view; resuming shows the latest snapshot.

Three log model tests cover terminal formatting and filtering. Native/resource checks, all
drawable references, vector XML, static font weights/Cyrillic coverage and whitespace checks
pass. Kotlin tests remain blocked by the Gradle distribution download; APK is not awaited.

## Entry-point inventory

| File | Entry points |
| --- | --- |
| `NativeAboutScreen.kt` | `NativeAboutScreen` |
| `NativeAddProfileSheet.kt` | `NativeAddProfileSheet` |
| `NativeChainScreen.kt` | `NativeChainScreen` |
| `NativeConnectionPolicyScreen.kt` | `NativeConnectionPolicyScreen` |
| `NativeCoreOptionsScreen.kt` | `NativeCoreOptionsScreen` |
| `NativeDiagnosticsScreen.kt` | `NativeDiagnosticsScreen` |
| `NativeDnsOptionsScreen.kt` | `NativeDnsOptionsScreen` |
| `NativeGeneralOptionsScreen.kt` | `NativeGeneralOptionsScreen` |
| `NativeImportPreviewDialog.kt` | `NativeImportPreviewDialog` |
| `NativeInboundOptionsScreen.kt` | `NativeInboundOptionsScreen` |
| `NativeLogsScreen.kt` | `NativeLogsScreen` |
| `NativeOutboundInfoDialog.kt` | `NativeOutboundInfoDialog` |
| `NativeOutboundsScreen.kt` | `NativeOutboundsScreen` |
| `NativeOutboundsSheet.kt` | `NativeOutboundsSheet` |
| `NativePerAppBackupScreen.kt` | `NativePerAppBackupScreen` |
| `NativePerAppScreen.kt` | `NativePerAppScreen` |
| `NativePrivacyOverviewScreen.kt` | `NativePrivacyOverviewScreen` |
| `NativeProfileDetailsScreen.kt` | `NativeProfileDetailsScreen` |
| `NativeProfileShareDialog.kt` | `NativeProfileShareDialog` |
| `NativeProfilesScreen.kt` | `NativeProfilesSheet, NativeProfilesScreen` |
| `NativeProxyPrivacyScreen.kt` | `NativeProxyPrivacyScreen` |
| `NativeQrScannerDialog.kt` | `NativeQrScannerDialog` |
| `NativeQuickSettingsSheet.kt` | `NativeQuickSettingsSheet` |
| `NativeRegionalAppsScreen.kt` | `NativeRegionalAppsScreen` |
| `NativeRegionalRoutingScreen.kt` | `NativeRegionalRoutingScreen` |
| `NativeSettingsOverviewScreen.kt` | `NativeSettingsOverviewScreen` |
| `NativeSettingsScreen.kt` | `NativeSettingsScreen` |
| `NativeTlsOptionsScreen.kt` | `NativeTlsOptionsScreen` |
| `NativeTrafficFiltersScreen.kt` | `NativeTrafficFiltersScreen` |
| `NativeTunnelOptionsScreen.kt` | `NativeTunnelOptionsScreen` |
| `NativeVpnProtectionScreen.kt` | `NativeVpnProtectionScreen` |
| `NativeWifiSharingGuideScreen.kt` | `NativeWifiSharingGuideScreen` |

## Still to verify

Compare every entry point on the same Android device, with identical data/theme/locale/font
scale/orientation. Secondary layouts, original Fluent glyph contours, blur/motion and complete route/action equivalence still need work and verification. These source
fixes do not certify an indistinguishable application. KOTLIN_PARITY.md remains open.

## About and log actions follow-up

Compared with Dart `about_page.dart` and `logs_page.dart` at 8d86559.
About now uses the 64dp logo/version row, inset fork description card, list tiles with
external-link icons, check-update spinner and available-update dialog. Its overflow menu
copies version/build/channel/release/Android information. English/Russian fork text matches
the source. Headers remain outside the scroll area; About/Logs no longer inherit the extra
20dp secondary-page inset.

Logs now have pause, clear and the debug-only overflow menu from Dart. Share core/app logs
copies the chosen original file to a dedicated cache path on IO and opens the Android share
chooser with a FileProvider read grant. App sharing falls back to captured service messages
when no app.log exists; this is not full Android logcat capture. Missing files show an error.
Automatic service updates still refresh the screen; the extra refresh toolbar action was removed.
Resource/reference checks and git whitespace checks pass. Compilation/runtime comparison
is still unavailable locally and these changes do not close whole-app parity.


## Follow-up: 200 source differences

See KOTLIN_DART_200_DIFFS.md / .json for the next 200 unique findings and implemented
changes. Original Fluent vectors now replace the listed approximate glyphs; diagnostics,
per-app routing, Wi-Fi guide, anonymization and protection receive source-based layout
corrections. Log typography and severity filtering now follow Dart. Device rendering and
interaction parity remain unverified; this supersedes the earlier notes for these items.

## Opaque icon backgrounds (2026-10-08)

Removed accidentally painted SVG viewport guides from 26 Material resources in DNS,
TLS, general options, chain controls and LAN sharing. Their `fill="none"` was lost during
monochrome conversion, so Compose tinted the entire viewport into a solid square.
The converter now skips unpainted shapes, including inherited `fill="none"`; the existing
native-resource CI check rejects both integer and decimal viewport-guide encodings.
Intentional flag and launcher/cover backgrounds are excluded.

Compared icon identities with Dart at `8d8655916b115417e1f736966108c29ca87ba530`, including
rounded Material variants and Android AdaptiveIcon's Fluent share/more variants. Rendered
53 attributed Material/Fluent resources at 96px and compared their alpha masks with the
original Google SVGs and Microsoft SVGs at the pinned Fluent commit: all masks matched.
The CI guard rejected all 26 original broken resources; corrected resources and the
existing source inventory passed. These checks verify resource contours, not Android
device screenshots or whole-application visual parity. No APK build was awaited.
