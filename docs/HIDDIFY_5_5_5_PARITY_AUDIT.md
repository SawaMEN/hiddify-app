# Hiddify 5.5.5 — native fork parity audit

**Updated:** 2026-11-06. **Scope:** [SawaMEN/hiddify-app](https://github.com/SawaMEN/hiddify-app) (native Android/Kotlin and macOS/Swift), compared with [official Hiddify 5.5.5](https://github.com/hiddify/hiddify-app/releases/tag/v5.5.5).

This is a **source-code implementation audit**, not a claim of full 5.5.5 feature parity or successful physical-device VPN tests. The upstream Flutter implementation and this fork's native clients differ, so implementation is evaluated by visible native UI, persistence and core settings, not by matching source files.

## Android feature inventory

| Feature | Native fork status | Evidence / remaining acceptance |
| --- | --- | --- |
| Upload/download tests, size limits, results, speed ordering | **Implemented in code** | `NativeProxySpeedPolicy`, `NativeOutboundPresentation` and unit tests; current results rank ahead of cached, then other direction and live ping |
| Cached latency versus fresh latency; detailed proxy errors | **Implemented in code** | `NativeOutboundPresentation`, `NativeOutboundStatus`, `NativeOutboundInfoDialog`; verify UI on a device |
| Per-profile fastest N (`keep-top`) | **Implemented in code** | `NativeKeepProxiesDialog`, `NativeProfileOverrides`, `NativeProfileRepository.saveKeepSelection`; verify runtime reconnection |
| Per-profile manually kept tags (`keep-tags`) | **Implemented in code** | Same native picker, persistent profile overrides and core options; check removed/renamed subscription nodes on-device |
| Proxy picker filters: selected, responding, unavailable, country, protocol | **Implemented in code** | `NativeKeepProxiesDialog`; verify interaction, focus and accessibility |
| WARP tunnel modes Auto, WireGuard, AmneziaWG, MASQUE | **Implemented in code** | `NativeChainRepository`, `NativeChainScreen`, `NativeCoreOptionsProjection`; verify live tunnels; advanced WARP options remain only partially exposed |
| Default outbound: balancer, lowest, first | **Implemented in code** | `NativeGeneralOptions`, `NativeGeneralOptionsRepository` and tests |
| IPv6 opt-in/IPv4-only and Sing 15 TUN defaults | **Implemented in code** | `NativeTunnelOptionsRepository`, `NativeInboundOptionsRepository`, `NativeCoreOptionsProjection`; device-side IPv6 and TUN acceptance pending |
| Separate local proxy/LAN port and authentication controls | **Implemented in code** | `NativeInboundOptionsRepository` and `NativeInboundOptionsScreen`; validate LAN exposure and older-settings migration |
| Corrupt profile isolation | **Implemented in code** | `NativeProfileRepository.toReadableProfile` skips invalid entries without deleting their rows; migration tests recommended |
| Stop automatic subscription retries after 3 days of failures | **Implemented in code** | SQLite `profile_auto_update_failures`, `NativeSubscriptionMetadata.isAutoUpdateSuspended`; manual refresh remains available, success/deletion clears the failure record |
| Android TV pairing and subscription import over local network | **Implemented in code** | `NativeTvImportPanel` and associated pairing services; real TV/phone network and permission validation pending |
| Subscription share, QR, archive and editor | **Implemented in code** | Native profile share/import/editor components; real-provider integration tests pending |
| DNS/tunnel features (e.g. Soar) and detailed upstream routing parity | **Partially audited** | Native core support alone does not establish matching import, UI or routing behavior |
| Notifications, network resume, auto-connect, UI accessibility | **Partially audited** | Require device and lifecycle regression tests; source presence is not end-to-end evidence |

## macOS and releases

The macOS application is maintained separately in native Swift. Its Network Extension, signing, notarization, real-device VPN behavior and final Developer ID DMG acceptance remain **unverified for complete 5.5.5 parity**. Follow `macos/RELEASE_CHECKLIST.md` and `macos/ON_DEVICE_TESTING.md`. Android CI success does not imply macOS success.

The `main` branch includes the native 5.5.5 work merged through [PR #107](https://github.com/SawaMEN/hiddify-app/pull/107) and the independent development-release workflow from [PR #110](https://github.com/SawaMEN/hiddify-app/pull/110). Stable releases are not updated by this audit.

## Audit branch consolidation

The legacy [PR #106](https://github.com/SawaMEN/hiddify-app/pull/106) was based on an older `main`. Its subscription-failure and corrupt-profile mechanisms were independently implemented in the newer `main` using SQLite and safe row decoding. **Do not copy the old SharedPreferences failure tracker** alongside that mechanism. The useful speed ordering and regression tests are carried forward from PR #106 as a targeted change.

## Remaining acceptance work

1. Run Android VPN tests on real devices: connecting/disconnecting, TCP/UDP/DNS, IPv4/IPv6, WARP modes, keep-top/keep-tags, LAN sharing and TV pairing.
2. Test import/update of damaged, expired and changing subscriptions, including migration from existing user settings and the three-day retry suspension.
3. Confirm UI/TV focus, accessibility, proxy filters and live/cached speed-order semantics.
4. Verify system-service routing, Soar-specific imports and other platform-relevant upstream changes individually.
5. Perform a signed, notarized macOS DMG release check and physical Mac Network Extension tests before claiming macOS parity.

**Status:** source implementations cover many 5.5.5 features; **full functional parity is not yet established by device-level acceptance evidence**.
