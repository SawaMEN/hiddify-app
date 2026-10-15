# Android routing and automatic setup — 2026-10-15

## Routing choices

The privacy overview now exposes the two device policies directly:

- **Russian bypass:** configured Russian apps, Russian domains/TLDs and Russian IP rules use the direct route; the remaining generated traffic uses the VPN.
- **Selected services:** selected service domains, suggested installed apps and explicitly selected apps use the VPN; the terminal route and DNS fallback are direct. Russian network rules and redundant custom direct-domain exceptions are not injected into this mode.
- **Profile rules:** disable regional device policy and use normal profile routing.

Choosing an active device mode disables competing full-tunnel, handbook, per-app-restriction and full-profile-config switches in one preference commit. It retains other protocol/profile settings. Regional saves validate the policy with the overrides that will actually be committed. Saving app/service choices never changes the mode.

Explicit direct app exclusions are outside Android's TUN and therefore take precedence over service-domain rules. The UI explains this. A Russian browser excluded from the VPN must be removed from direct exclusions to use service rules inside it.

## Restricted services menu

The offline catalogue has 15 named service groups. Their combined domain/package sets exactly match the previous flat catalogue; this is a grouping change, not a claim that every service is blocked on every network. Service selection controls domain suffixes (including browser traffic) and automatic app suggestions. Manual whole-app rules remain independently editable.

Automatic, manual-empty and manual selections are distinct. Shared CDN domains remain selected while another selected service needs them. Removed catalogue IDs and unavailable app choices remain visible rather than being silently discarded. Changing only services retains independent manual/legacy app choices. Reset affects only the chosen side, and proxy reset restores both proxy-service and proxy-app suggestions.

Save remains outside the scrolling list. Drafts are size-bounded and distinguish an automatic list from an explicitly empty list; successful-save revisions clear drafts without clearing them merely on recreation. Search and retry remain available.

## Automatic setup

The button commits a consistent recommended device policy: VPN mode, Russian bypass, automatic app/service suggestions, IPv4-only, MTU 1400, automatic reconnect, encrypted DNS, a public DNS address in VPN network information, strict routing, hidden local/Clash listeners and no system HTTP proxy.

It resets custom routing/domain/service lists and turns off full tunnel, handbook routing, separate per-app restrictions, raw full-config rules, root mode, Wi-Fi/LAN sharing and masked-protocol-only filtering. Otherwise these settings could override the advertised routing/listener policy or exclude the active profile's protocols. Server credentials and unrelated TLS/traffic-filter choices are retained.

Changes are mirrored into core JSON and legacy preference aliases where required. The first backup retains original values; legacy backups are extended only with previously uncaptured fields before those fields are modified. Reapplying keeps the original captured values. Restore writes preferences and core JSON together, including legacy raw-config aliases. Setup requires a stopped VPN; ordinary routing edits apply after reconnecting.

## Verification

After integrating the newest main changes: 432 JVM tests and 24 Python tests passed, along with resource/project and whitespace checks. Coverage includes service catalogue equivalence, selected-only domain/app sets, manual-empty/unknown choices, shared CDNs, mode preservation, conflicting mode overrides, automatic policy completeness and restoration of DNS/LAN/root/legacy backups.

CI also copies app-owned routing contract tests into the released core's config package: selected-service proxy rules, terminal direct route/DNS fallback, Russian direct rules and absent proxy exceptions after deselection. Android APK compilation and these native contract tests run in PR CI. No Android device/network traffic capture is available locally.
