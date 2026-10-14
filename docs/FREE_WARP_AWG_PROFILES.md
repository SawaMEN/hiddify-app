# Free WARP and AmneziaWG profiles

The Android **Add profile → Free** pane includes these entries in every region,
even when the upstream catalog is unavailable:

| Entry | Registration | Saved configuration |
| --- | --- | --- |
| Cloudflare WARP | Direct Cloudflare API request before import | Explicit WireGuard endpoint, including `client_id` reserved bytes |
| WARP + AmneziaWG | Direct Cloudflare API request before import | Explicit AWG endpoint with junk and QUIC signature packets |
| WARP + AWG — backup generators | Sequential requests to three external generators | Explicit AWG endpoint after core validation |

The two direct modes generate an X25519 key locally using Bouncy Castle's
lightweight RFC 7748 implementation. Only its public key is sent to Cloudflare;
the private key is retained in the local profile. All requests run before the
VPN tunnel starts. Failed registration does not create a lazy/empty profile.
There is no silent fallback from direct registration to a third-party generator.

## Repair of the initial implementation

The original four Routerich endpoints were obsolete when tested live:
`warp.llimonix.pw/api/warp` returned HTTP 502, `topor-warp.vercel.app/generate`
returned no configuration, `warp-gen.vercel.app/generate-config` returned HTTP
402, and `config-generator-warp.vercel.app/warp` returned HTML. These observations
are from this environment, not a guarantee of results in every user's network.
The old tests verified response fixtures without confirming that those APIs
still supplied configurations.

The original local entries contained a lazy `type: warp` template. Its stable
core implementation registers only after starting the VPN, selects a random port
behind a DNS hostname, and drops `client_id` when creating the inner WireGuard
endpoint. Core validation does not attempt registration or establish a handshake,
so acceptance of that template did not establish its usability.

New imports register first and store explicit peer settings. Literal IPs avoid
bootstrap DNS dependence. Cloudflare's returned host port is preferred when it is
in the supported port list, and WireGuard retains `client_id` as three reserved
bytes. Keepalive is set to 25 seconds and MTU to 1280. AWG retains generator
signature packets or adds the WARP-compatible QUIC signature from the maintained
[ImMALWARE generator](https://github.com/ImMALWARE/bash-warp-generator) (MIT).
S1–S4 stay zero and H1–H4 stay 1–4; server-dependent AWG header protection is
rejected. The current AWG endpoint schema has no `reserved` field, so the app does
not emit an unsupported option there.

Exact saved templates from the first implementation are repaired on their next
VPN start, including starts from a tile or always-on VPN. The existing profile
ID, name and user options are preserved; custom edited templates are untouched.
Registration and core validation support Stop/cancellation. A failed repair keeps
the original saved file. Repair occurs once: explicit endpoints do not match the
legacy template afterward.

## Current backup sources

The source order follows the current open
[CodeRoK7/OpenWRT_configs awg_config.sh](https://github.com/CodeRoK7/OpenWRT_configs/blob/main/awg_config.sh).
All three returned configuration material in live checks:

| Endpoint | Method | Response |
| --- | --- | --- |
| `https://warp-config-generator-theta.vercel.app/api/warp` | POST | `success: true`, `content.configBase64` |
| `https://dulcet-fox-556b08.netlify.app/api/warp` | POST | `success: true`, `content.configBase64` |
| `https://santa-atmo.ru/warp/warp.php` | GET | Cloudflare-style `result` with account key and config |

POST requests include `selectedServices: []`, `siteMode: all`, `deviceType:
computer`, and the source's WARP endpoint preset `162.159.195.1:500`.
Each request has a 25-second total timeout; redirects are disabled. Reads use the
existing 8 MiB limit. Formats are detected from validated envelopes rather than
the source's numeric position. Plain `.conf`, JSON config text and base64 config
text remain supported. Credentials, response bodies and exception causes are
excluded from provider errors; the UI reports only source and status categories.

The existing consent dialog names the generators and explains that they can know
the private key. No external shell code is executed. Each candidate is checked by
the actual VPN engine before import confirmation and the file/database transaction.
A rejected candidate tries the next source. Generated credentials remain local;
subscription refresh does not replace them.

## Limits and verification

Registration does not prove that UDP handshakes or traffic work in the user's
network. A provider may block the API, endpoint IPs, ports or WireGuard/AWG.
These entries use free WARP, not a paid WARP+ license. The application does not
fall back to direct Internet traffic if the tunnel fails.

- 21 targeted JVM tests passed: real response formats, local/public key separation,
  `client_id`, address/port conversion, signature preservation, registration
  failure, malformed data, fallback, cancellation and exact legacy recognition.
- Configuration material from Cloudflare and all three live backup APIs produced
  eight explicit WireGuard/AWG configurations; all passed `HiddifyCli check` on
  the stable `sawamen-22fb84fdd93a-3827d3a871dd-panel8` engine.
- Full SOCKS/tunnel attempts in this environment stopped before connection:
  `create netlink socket: operation not permitted`. They do not establish
  successful tunnel connectivity on Android or in the user's network.
- Resource/project checks and whitespace checks passed. Existing Python tools
  and CI also run their normal checks; APK completion is not required to
  distinguish configuration validation from on-device connectivity.

## Registration and startup recovery (October 14)

Direct registration retries network failures using IPv4 DNS-over-HTTPS and a
no-SNI connection to the fixed Cloudflare API. The platform trust manager and
OkHttp hostname verification are retained; Opera's bootstrap certificate
exception is never used. Both registration attempts send the same public key.
DNS, timeouts, certificate failures, TLS handshake failures and resets are
reported as separate sanitized categories.

The direct entries now offer an optional, initially disabled backup-registration
checkbox. It names the three generators and their private-key exposure. If
approved, failed direct registration can use those sources without changing the
requested WireGuard/AWG protocol. Legacy repair does not grant this permission.

In normal Android VPN mode, the small local built-in WARP configurations are
checked before publishing Connected. HTTPS probes use the core's selected
outbound, not the physical network. Failed traffic probes stop the old core
instance and close its TUN before starting the next IP/port with the same keys
and obfuscation. Up to five distinct consumer WireGuard endpoints are tried;
MASQUE ingress is excluded. Each probe round has a six-second limit and startup
retains the existing 90-second watchdog. A successful alternate endpoint is
saved with the existing profile identity and user options. If none passes,
startup fails with an explicit UDP reachability message. Cancellation never
accepts a probe result or starts the next endpoint.

Recognition is limited to catalog-named local profiles with a single built-in
endpoint and no other root configuration. General subscriptions, renamed/custom
profiles, root mode and service-only startup retain their existing lifecycle.
The startup policy tests simulate traffic and cancellation; they are not a
claim that a particular ISP passes WARP. Android device connectivity still
requires checking the updated APK on that network.

## Live engine audit (October 14, 2026)

The opt-in `Free profile live audit` workflow obtains material with production
Kotlin code and checks traffic through the shipped stable Linux engine. Private
keys and proxy tokens stay in a private temporary directory, deleted at the end;
configuration bodies, core process output and credentials are not logged or
uploaded as artifacts.

The first complete WARP/Opera run verified HTTPS traffic for all eight
WireGuard/AWG configurations (direct Cloudflare and each of the three backup
sources) and every one of the ten Opera endpoints. Two WARP attempts needed an
alternate UDP port. The audit now obtains those alternatives directly from
`NativeWarpEndpoints`, the same policy used during Android VPN startup.

Community subscriptions require parsing share links into JSON and building DNS
and dependency settings before startup. The audit covers retrieval, conversion
and a bounded traffic sample of three nodes for these feeds; it does not claim
that hundreds of independently operated public nodes all work. Results are
network- and time-dependent and do not certify a user's Android TUN or ISP.

The current stable app release v1.0.1 predates the WARP registration and startup
fixes in main. Main's development APK includes them; an older installed APK can
still use the obsolete generators or lazy registration templates.
