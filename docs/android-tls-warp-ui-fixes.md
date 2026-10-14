# Android TLS and WARP UI fixes

All four native bottom sheets leave vertical scroll gestures to their child lists,
with no partially expanded anchor. Dismissal remains available with Back/outside
tap and the existing page controls.

TLS trick dependencies use one policy shared by UI and the fresh-state save
boundary. Only the fragmentation switch is editable while fragmentation is off.
Padding size also requires padding. Stale dependent clicks refresh state instead
of throwing a generic `Check failed` error.

The active-profile entry opens a focused network page, with the original profile
editor remaining available from Profiles. Subscription requests have automatic,
VetrOFF, Hiddify, browser and custom User-Agent choices. Provider-specific headers
remain advanced inputs; unknown authorization data is never invented. TLS offers
inherit-from-profile, Chrome fingerprint and gentle TCP fragmentation presets.
Targets and configured SNI are read from supported JSON/link profiles, and controls
respect TCP/REALITY/QUIC compatibility. Existing custom settings remain intact
until the user selects a preset or edits them.

Built-in WARP startup checks the selected core outbound using literal IPv4 URLs:
HTTPS with normal certificate verification (seven seconds), then HTTP (three).
DNS or IPv6 failures no longer determine UDP peer reachability. Six or fewer
candidates include the original peer, alternate ports and both 188.114.96/97
ranges; account keys and AWG signatures remain unchanged. Total probe time stays
below the existing 90-second startup watchdog. General profiles and custom
subscriptions retain their original connection policies.

Production Android/Compose compilation and the JVM regression suite passed.
Live traffic testing exercises all recovery peers for eight configurations
(direct Cloudflare and three generators, each in WireGuard/AWG mode), not only
the first responding peer. Individual peers can fail; every configuration found
working peers, including the new ranges. A CI runner is not an Android device
and does not certify UDP availability on a particular ISP.
