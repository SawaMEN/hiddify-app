# Hiddify 5.5.5 — native fork feature parity audit

Audit date: **2026-11-05**. Original: [hiddify/hiddify-app v5.5.5](https://github.com/hiddify/hiddify-app/releases/tag/v5.5.5) (`7804cadac01419f31b1ec7802d9e1be599e66de2`). Fork baseline: [SawaMEN/hiddify-app main](https://github.com/SawaMEN/hiddify-app/tree/main), merged PR #105 (`ef3b0fe7efcc6fd8afe295ff38e4a859d6763d3c`).

## Scope and acceptance criteria

The original app uses Flutter/Dart across several platforms; VetrOFF Android is **native Kotlin/Compose** and VetrOFF macOS is **native Swift**, so copying Dart commits does **not** transfer functionality. `Updated` means equivalent native UI, persistence, core RPC, migration handling, regression tests and a checked real connection (where relevant). An existing core protocol field alone does not meet this bar. Items below are **source audits**, not claims about on-device verification.

| 5.5.0–5.5.5 upstream behavior | Native Android fork status | Evidence / gap |
|---|---|---|
| Upload/download speed RPCs, result badges, size budget, clear results | **Implemented in code** | `NativeOutboundsRepository`, `NativeProxySpeedDialog`, fields 19–24 |
| Upload/download sorting: live result before cached; fallback to other speed then ping | **Aligned in this audit branch** | `NativeProxySpeedPolicy.compareBySpeeds`, `NativeProxySpeedPolicyTest` |
| Cached ping used for ordering, not shown as fresh | **Implemented in code** | `NativeOutboundPresentation`, `sort_delay` field 26 |
| Error reason in proxy details | **Implemented in code** | `NativeOutboundInfoDialog`, `error` field 25 |
| Share subscription link, QR and configuration as text/plain file | **Implemented in code** | `NativeProfileShareDialog`, `MainActivity.shareProfileConfig`, FileProvider |
| Individual corrupt/missing-URL profile must not break the whole list | **Aligned in this audit branch** | `NativeProfileRepository.listProfiles` skips bad rows; runtime migration test still needed |
| Keep fastest N proxies (`keep-top`), first-run question, override | **Missing** | No native count picker or persisted keep-top override |
| Manually choose proxy subset (`keep-tags`) and persist choices | **Missing** | Native list selects an outbound; no keep-tags editor |
| Proxy filters: chosen, answering, paused, protocol, country; selection tray | **Missing / partial** | Native list has search and sort, not full 5.5.x filter/picker |
| WARP tunnel selection: Auto/WireGuard/AmneziaWG/MASQUE | **Missing / partial** | `NativeChainRepository` models licence, clean IP and noise but not per-hop tunnel choice |
| Per-hop WARP endpoint source/scanner/MTU/options persistence | **Missing / partial** | Native chain UI does not expose the complete 5.5.x controls |
| Hide WARP-scanned endpoint until tapped | **Unverified / not matched** | No dedicated scanner endpoint disclosure control identified |
| Extra Security WARP must ignore obsolete endpoint overrides | **Needs remediation** | `NativeCoreOptionsProjection.apply` copies every nested WARP key |
| Default proxy selection: balancer, lowest ping or first proxy | **Missing / unverified** | No equivalent choice confirmed in native options |
| Local proxy without mandatory password, separate LAN port/password, keep-open in VPN mode | **Missing / partial** | `NativeInboundOptionsRepository` uses older mixed/direct + LAN model |
| LAN sharing dialog for this device/other devices, inline edit, QR rules | **Partial** | `NativeQuickSettingsSheet` has copy/QR + switch; not unified 5.5.5 dialog |
| Stop automatic subscription update after 3 days of failures | **Aligned in this audit branch** | Native persistent first-failure timestamp suppresses only auto retries; manual retry and successful recovery clear the block |
| Import to Android TV from phone on same LAN with short-lived code | **Missing** | Native add-profile sheet has import/QR reader; no temporary TV HTTP receiver |
| System-services routing rule by platform + migration/duplication | **Unverified / incomplete** | Native routing screens require separate rule-by-rule inventory |
| IPv6 opt-in/off by default and Sing 15 TUN stack defaults | **Not aligned** | `NativeTunnelOptionsRepository` defaults to `prefer_ipv4`; core projection defaults `gvisor` |
| Soar DNS tunnel and soar://, latest MASQUE/WARP/Fake DNS fixes | **Core available, client parity unverified** | SawaMEN/hiddify-core PR #17 merged; check a verified AAR and protocol imports |
| Native notification lifecycle and dynamic status | **Partially equivalent** | Custom `ServiceNotification` uses owned foreground lifecycle; no device coverage |
| Hot reload/reconnect and periodic ping recovery | **Partially equivalent** | Native lifecycle and foreground ping policies exist; 5.5.x runtime regression needed |
| UI transitions, proxy status and short-screen/TV focus | **Partially equivalent** | Compose native animations replace Flutter; screen-reader and TV device testing required |
| Auto-update outside app stores, rolling release behavior | **Different implementation** | Native update channels and APK verification; verify releases and signature continuity |
| macOS App Store, Developer ID, Packet Tunnel, signed DMG | **Separate incomplete track** | `macos/RELEASE_CHECKLIST.md` requires signing, notarization and physical Mac VPN tests |
| Windows/Linux/iOS-only changes | **Not applicable to Android fork** | No native Windows/Linux/iOS client in this repository's supported Android package |

## Blocking follow-ups for an actual 5.5.5 parity claim

1. Implement `keep-top`, `keep-tags`, persistent selection and combined proxy filters, with migration and core-contract tests.
2. Port WARP tunnel selection and per-hop settings, clean the Extra Security override, verify core setting names and defaults.
3. Bring inbound local/LAN proxy controls to 5.5.x behavior; document and test migration of older settings.
4. Verify the 3-day failure suspension, successful-refresh recovery, and manual bypass on real profiles; unit-test the time policy.
5. Add secure short-lived TV LAN import or explicitly de-scope Android TV from supported platforms.
6. Verify system-services rules, default proxy, IPv6/TUN defaults, UI accessibility and network-resume behavior.
7. Run signed APK CI with the intended SawaMEN/hiddify-core AAR, end-to-end VPN tests on physical Android devices (TCP/UDP/DNS/IPv6), and Apple Network Extension acceptance separately.

**Do not describe the fork as fully updated to Hiddify 5.5.5 until every applicable item has passing implementation and acceptance evidence.**
