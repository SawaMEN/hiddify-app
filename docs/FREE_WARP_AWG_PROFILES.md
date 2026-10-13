# Free WARP and AmneziaWG profiles

The Android **Add profile → Free** pane includes three local entries in every region.
They remain usable while the upstream free-profile catalog loads or is unavailable.

| Entry | Registration | Storage |
| --- | --- | --- |
| Cloudflare WARP | The core registers directly with Cloudflare on first connection | Local endpoint definition; account retained by the core cache |
| WARP + AmneziaWG | The same registration with AWG junk packets | Local WARP endpoint with `awg` options and a separate cache identity |
| WARP + AWG — backup generators | Sequential external API requests, skipping failures and invalid configurations | A new local `vpn://` share containing the generated `.conf` |

These entries use free WARP. They do not provide a paid WARP+ license. Actual
registration and tunnel reachability depend on the device's network.

## Engine compatibility

The stable Android core at implementation time is
`sawamen-22fb84fdd93a-3827d3a871dd-panel8`, including sing-box revision
`3827d3a871dd3bcc7e8265538df7ceb157dd0066`. Its WARP endpoint supports an `awg`
object, registers accounts lazily and caches credentials with `unique_identifier`.
The app supplies no shared private keys or access tokens.

The automatic AWG preset adds four junk packets of 40–70 bytes, uses MTU 1280,
keeps S1–S4 at zero and H1–H4 at 1–4, preserving the standard WireGuard format
accepted by Cloudflare. It does not enable server-dependent AWG 3 header protection.

Sources:

- [WARP endpoint options](https://github.com/SawaMEN/hiddify-sing-box/blob/3827d3a871dd3bcc7e8265538df7ceb157dd0066/option/wireguard.go)
- [WARP registration/cache](https://github.com/SawaMEN/hiddify-sing-box/blob/3827d3a871dd3bcc7e8265538df7ceb157dd0066/protocol/warp/endpoint_warp.go)
- [WARP → AWG implementation](https://github.com/SawaMEN/hiddify-sing-box/blob/3827d3a871dd3bcc7e8265538df7ceb157dd0066/protocol/warp/endpoint_warp_awg.go)
- [Native .conf share parser](https://github.com/SawaMEN/hiddify-core/blob/22fb84fdd93a/v2/config/paneluri/wireguard.go)

## Backup generation

The source order and response envelopes follow Routerich's open
[awg_config.sh](https://github.com/routerich/RouterichAX3000_configs/blob/main/awg_config.sh):

| Order | Endpoint | Method | Response |
| --- | --- | --- | --- |
| 1 | `https://warp.llimonix.pw/api/warp` | POST, `selectedServices: []`, `siteMode: all`, `deviceType: computer` | `success: true`, `content.configBase64` |
| 2 | `https://topor-warp.vercel.app/generate` | POST, `platform: all` | Plain `.conf` |
| 3 | `https://warp-gen.vercel.app/generate-config` | GET | JSON `config` |
| 4 | `https://config-generator-warp.vercel.app/warp` | GET | Base64 JSON `content` |

These are configured sources, not a guarantee of current provider availability.
Each request has a 20-second total timeout and supports cancellation. Redirects
are disabled. Response reads are bounded by the existing 8 MiB import limit. URLs
are fixed in the app and cannot be changed by the remote catalog.

The existing consent dialog names the third-party generators and explains that
they generate and can know the private key. No external script is executed.
Only documented envelopes containing an AWG configuration are accepted. Native
fields, including IPv6, routes and signature packets, are preserved in the
base64url `vpn://` share. The core validates each candidate before selecting a
source; the existing import preview precedes the file/database transaction.
Cancellation prevents fallback and saving. Exhaustion returns a localized error
without response bodies or credentials.

Generated keys are not fetched by subscription refresh. Adding the backup entry
again explicitly requests a new local profile. Existing imported configs remain
editable/exportable through the normal profile actions.

## Verification

- 15 targeted JVM tests passed, compiling production Kotlin files directly with
  Kotlin 2.2.20, Gson 2.13.2, OkHttp 4.12.0 and JUnit 4.13.2. The Gradle suite
  includes the same new source and test files for CI's pinned dependencies.
- Tests cover all four response formats, field preservation, invalid payloads,
  HTTP failure, core-validation rejection, fallback, cancellation and errors.
- `python3 tool/check_native_project.py` and `git diff --check` passed.
- Full Android compilation, on-device core validation and actual VPN connectivity
  were not run locally; this environment has no Android SDK or core AAR.
