# Native update against Hiddify 5.4.0

Reference: `hiddify/hiddify-app` `017d2241` (release 5.4.0).
Starting fork: `08a1682033f4b580c67623e79f03ebd8c3583b9d`.
Prior audit: `NATIVE_UPSTREAM_5_3_0_UPDATE.md`, reference `5919a4f7`.

The repositories diverge by 457 upstream commits since their common ancestor. This is
not 457 missing features: the fork has replaced Flutter with Kotlin/Compose and
SwiftUI and already contains native adaptations. The release delta from the previous
5.3.0 audit changes 55 paths, inventoried below. Platform-specific Flutter code and
generated files must not be merged into the native application.

## User-visible adaptations

| Area | Native behavior |
| --- | --- |
| Proxy lifecycle | Core READY/CONNECTING/RECONNECTING/SEARCHING/FAILED/REGISTERING take precedence over old ping values in rows, details, home latency and connection health. Absent wire status preserves the previous ping-based behavior; unknown values remain untested. |
| Ping style | Persistent Number/Signal preference in the proxy list menu, long press to toggle, tap to test that server. A stable action area and fade-through keep labels readable. |
| Active group | The active server resolves through a selected balancer's group in the same MainOutboundsInfo response. |
| Old IP data | `IpInfo.from_cache` prevents last-run IP/country/provider/location from being presented as a current lookup. |
| Chain telemetry | `chain_hop` is consumed from that same response. The health sheet orders unblocker before server and extra security after server; unknown telemetry stays unknown. A measured pending hop is slow after 15 seconds and failed after 60, matching upstream presentation. |
| Fastest offer | Home latency area and health sheet offer lowest only for a failed manually selected server, a selectable main group and an actually ready lowest alternative. A picked group never receives that offer. |
| Remote DNS | After profile overrides merge, extra security keeps the user's own resolver. Without extra security, subscription DNS overrides remain effective. Global settings are never rewritten. |
| IPv6 | Fresh/default tunnel settings prefer IPv4 with IPv6 available, consistent across editor, core projection and Android builder. Existing single-family privacy settings remain explicit fork features instead of being silently overwritten. macOS already rejects incomplete full-tunnel IP-family routes. |
| Crash reports | Offer each local JVM/core crash fingerprint once, open the logs, and let the user explicitly export a scrubbed diagnostic archive using the core's own RPC. The archive action is available without enabling raw-log debug sharing. No automatic upload. |
| Connection motion | Short layered progress trails, directional upload/download orbits and softer rays, rate smoothing, a bounded failure emphasis and sequential label fades. Recovery/reconnect renders progress rather than connected traffic. |
| Motion cost | Canvas and graphics-layer animation values are read during drawing rather than page composition. Idle traffic and failures do not keep an infinite clock running. Chain icon movement is calmer. TalkBack/reduced motion/background gates remain in place. |
| macOS motion | Two smooth counter-rotating trails close continuously at the TimelineView boundary. Recovery uses progress; Reduce Motion also disables symbol replacement. Scene inactivity still pauses the clock. This is status decoration, not invented traffic telemetry. |
| Settings reconnect | Existing native stop/rebind lifecycle is retained; the Flutter Windows/Linux two-toggle workaround does not apply. |
| Core streaming | Existing native cancellation, generation guards, conflated snapshots and disconnected stream-error policy are retained. |

## Compatibility and limits

The fork's core source at `5f41161` already merges upstream 5.4.0 and retains custom
protocols. Android continues to fetch the fork's checksum-verified latest stable
release; this change does not replace it with an upstream AAR. Added protobuf fields
and RPCs preserve upstream wire numbers. An older core omitting telemetry cannot
supply chain status or cached-IP provenance; unsupported diagnostic RPCs fail through
the existing export error UI. JVM crash offers still work without a reachable core.

The macOS application remains the existing native SwiftUI/Network Extension target.
This is not an implementation of Flutter's Windows service manager, iOS/TestFlight,
or macOS App Store packaging. Its existing Developer ID/device-validation requirements
still apply. A source audit and unit tests do not establish pixel parity or verified
VPN operation on a physical Android/Mac device.

## Stable core release compatibility

The latest verified core release uses `sawamen-5f41161b08df-0f8a9b74c5a5-mask1-ciphers1`.
The old installer accepted only `-panelN`, preventing builds from consuming the current
stable release at all. Installer and optional source checkout now share a bounded
release-tag grammar; both immutable source hashes, stable publication, exact asset
origin, archive checksum, member checksums, ARM64 ELF/AAR and manifest identity remain
validated. Tests reject malformed suffixes, changed asset origins and mismatched commits.

Source regression preparation downloads the selected transitive graph (`go mod download all`).
Tests of disposable source checkouts use `-mod=mod`, as their injected routing contract and
current stable source require Go's module-graph normalization. Existing versions and checksums
are verified normally; no `go get` upgrade, checksum bypass, runtime fallback or binary change
is performed. This repairs source-test setup, not VPN behavior.

## Validation

- Native resource/boundary check and the existing 200-item source inventory.
- Python regression suite: 29 tests passed locally.
- JVM suite: 567 tests passed locally, including regressions for core-status precedence, unknown/legacy status, ping limits,
  cached geolocation, active balancer resolution, fastest-offer guards, chain ordering,
  pending time limits and resolver override precedence.
- Android Kotlin/resource build and macOS launch checks are verified separately by CI;
  see the PR check results for the exact commit. Device visual/VPN testing remains required.

## Complete release-delta file inventory

Each entry was reviewed for a native equivalent or platform applicability. Generated
Flutter translations/protobufs are represented by native resources/protobuf definitions;
no generated source or Flutter runtime is added.

| Upstream path | Native handling |
| --- | --- |
| `HISTORY.md` | Release metadata; native version/build system retained |
| `android/app/src/main/protos/v2/hcore/hcore.proto` | Additive status, cached-IP and chain-hop wire fields |
| `assets/translations/ar.i18n.json` | New strings adapted to native EN/RU resources |
| `assets/translations/en.i18n.json` | New strings adapted to native EN/RU resources |
| `assets/translations/es.i18n.json` | New strings adapted to native EN/RU resources |
| `assets/translations/fa.i18n.json` | New strings adapted to native EN/RU resources |
| `assets/translations/fr.i18n.json` | New strings adapted to native EN/RU resources |
| `assets/translations/id.i18n.json` | New strings adapted to native EN/RU resources |
| `assets/translations/pt-BR.i18n.json` | New strings adapted to native EN/RU resources |
| `assets/translations/ru.i18n.json` | New strings adapted to native EN/RU resources |
| `assets/translations/tr.i18n.json` | New strings adapted to native EN/RU resources |
| `assets/translations/zh-CN.i18n.json` | New strings adapted to native EN/RU resources |
| `assets/translations/zh-TW.i18n.json` | New strings adapted to native EN/RU resources |
| `hiddify-core` | Fork already merges 5.4.0; preserve custom-protocol release dependency |
| `ios/Runner.xcodeproj/project.pbxproj` | Platform-specific; not built by this native fork |
| `lib/core/preferences/general_preferences.dart` | Crash fingerprint stored in native preferences |
| `lib/core/preferences/preferences_migration.dart` | Default dual-stack preference; explicit fork privacy modes retained |
| `lib/core/router/dialog/dialog_notifier.dart` | Native canvas/fade-through/state presentation; retain native layout and lifecycle |
| `lib/core/router/dialog/widgets/proxy_info_dialog.dart` | Native status models, active resolution, signal indicator, cached IP and fastest offer |
| `lib/core/widget/animated_text.dart` | Native canvas/fade-through/state presentation; retain native layout and lifecycle |
| `lib/core/widget/frame_load.dart` | Native canvas/fade-through/state presentation; retain native layout and lifecycle |
| `lib/core/widget/shimmer_skeleton.dart` | Native canvas/fade-through/state presentation; retain native layout and lifecycle |
| `lib/features/app/widget/app.dart` | Native canvas/fade-through/state presentation; retain native layout and lifecycle |
| `lib/features/chain/overview/chain_mode_icon.dart` | NativeChainMotionIcon drawing-phase clock and calmer motion |
| `lib/features/connection/widget/connection_health_sheet.dart` | NativeConnectionHealthSheet / NativeChainStepPolicy |
| `lib/features/crash_report/data/diagnostics_exporter.dart` | NativeCrashOffer / NativeCoreDiagnostics / native logs menu |
| `lib/features/crash_report/notifier/crash_report_notifier.dart` | NativeCrashOffer / NativeCoreDiagnostics / native logs menu |
| `lib/features/crash_report/widget/crash_report_dialog.dart` | NativeCrashOffer / NativeCoreDiagnostics / native logs menu |
| `lib/features/home/widget/connection_button.dart` | Native canvas/fade-through/state presentation; retain native layout and lifecycle |
| `lib/features/home/widget/eclipse_button_painter.dart` | Native canvas/fade-through/state presentation; retain native layout and lifecycle |
| `lib/features/home/widget/home_page.dart` | Native canvas/fade-through/state presentation; retain native layout and lifecycle |
| `lib/features/log/overview/logs_modals.dart` | NativeCrashOffer / NativeCoreDiagnostics / native logs menu |
| `lib/features/profile/widget/profile_tile.dart` | Native canvas/fade-through/state presentation; retain native layout and lifecycle |
| `lib/features/proxy/active/active_proxy_card.dart` | Native status models, active resolution, signal indicator, cached IP and fastest offer |
| `lib/features/proxy/active/active_proxy_delay_indicator.dart` | Native status models, active resolution, signal indicator, cached IP and fastest offer |
| `lib/features/proxy/active/active_proxy_notifier.dart` | Native status models, active resolution, signal indicator, cached IP and fastest offer |
| `lib/features/proxy/active/ip_widget.dart` | Native status models, active resolution, signal indicator, cached IP and fastest offer |
| `lib/features/proxy/model/proxy_entity.dart` | Native status models, active resolution, signal indicator, cached IP and fastest offer |
| `lib/features/proxy/overview/proxies_overview_notifier.dart` | Native status models, active resolution, signal indicator, cached IP and fastest offer |
| `lib/features/proxy/overview/proxies_overview_page.dart` | Native status models, active resolution, signal indicator, cached IP and fastest offer |
| `lib/features/proxy/widget/proxy_tile.dart` | Native status models, active resolution, signal indicator, cached IP and fastest offer |
| `lib/features/settings/data/config_option_repository.dart` | NativeProfileOverrides DNS precedence |
| `lib/features/settings/notifier/config_option/config_option_notifier.dart` | Existing native service stop/rebind and reconnect lifecycle |
| `lib/features/settings/overview/sections/routing_options_page.dart` | Default dual-stack preference; explicit fork privacy modes retained |
| `lib/hiddifycore/generated/v2/hcore/hcore.pb.dart` | Generated code replaced by Wire generation from native protos |
| `lib/hiddifycore/generated/v2/hcore/hcore.pbjson.dart` | Generated code replaced by Wire generation from native protos |
| `lib/hiddifycore/generated/v2/hcore/hcore_service.pbgrpc.dart` | Generated code replaced by Wire generation from native protos |
| `lib/hiddifycore/hiddify_core_service.dart` | Existing NativeOutboundsStreamErrorPolicy and cancellation |
| `lib/singbox/model/singbox_config_enum.dart` | Default dual-stack preference; explicit fork privacy modes retained |
| `lib/utils/uri_utils.dart` | Native Android FileProvider chooser; Flutter iOS share anchoring does not apply |
| `pubspec.yaml` | Release metadata; native version/build system retained |
| `test/features/proxy/active_proxy_card_data_test.dart` | Behavior covered by native policies/regressions; Flutter tests not executable here |
| `test/features/proxy/proxies_overview_notifier_test.dart` | Behavior covered by native policies/regressions; Flutter tests not executable here |
| `test/features/settings/data/config_option_repository_test.dart` | Behavior covered by native policies/regressions; Flutter tests not executable here |
| `windows/packaging/msix/make_config.yaml` | Platform-specific; not built by this native fork |
