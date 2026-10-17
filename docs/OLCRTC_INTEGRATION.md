# olcRTC integration — development status

olcRTC support is developed across three linked pull requests:

- [Hiddify App #79](https://github.com/SawaMEN/hiddify-app/pull/79) — Android URI/subscription import, provider compatibility checks, duplicate detection, JSON editor template and JVM tests.
- [Hiddify Core #7](https://github.com/SawaMEN/hiddify-core/pull/7) — strict share URI-to-native-outbound conversion and Android ARM64 integration CI.
- [hiddify-sing-box #2](https://github.com/SawaMEN/hiddify-sing-box/pull/2) — `olcrtc` native outbound using the pinned upstream Go WebRTC engine.

All three PRs remain drafts. Changes are **not deployed to main or stable users**.

## Supported configuration

The v1 share format follows the [official olcRTC URI convention](https://github.com/openlibrecommunity/olcrtc/blob/master/docs/uri.md):

```text
olcrtc://jitsi?datachannel@https://meet.jit.si/example-room#<64-hex-character-key>$My server
olcrtc://wbstream?vp8channel<vp8-fps=60&vp8-batch=64>@room-name#<64-hex-character-key>
```

The 64-character encryption key must be a secure 32-byte random value shared with a compatible **server**, not the example string above. Do not log, paste into screenshots, or commit real keys.

To create a profile manually, the core also accepts this native outbound (replace placeholders):

```json
{
  "type": "olcrtc",
  "tag": "olcRTC",
  "provider": "jitsi",
  "transport": "datachannel",
  "room_id": "https://meet.jit.si/example-room",
  "key": "YOUR_64_HEX_CHARACTER_KEY",
  "dns_server": "8.8.8.8:53"
}
```

**Provider/transport combinations:** Jitsi supports the four built-in transports. Telemost does not support datachannel or seichannel. WB Stream guest DataChannel is not supported by the URI importer. Some configurations require a moderator/authorized provider token and cannot be created from the simple URI.

For VP8, SEI or VideoChannel, the editor exposes their corresponding transport parameters. The engine applies defaults when optional parameters are omitted.

## Native networking and lifecycle

The sing-box outbound uses the upstream olcRTC Go mobile runtime (no second Android `VpnService`), a loopback-only SOCKS5 proxy with random per-instance credentials, and Android socket protection for signaling, DNS and Pion ICE. On Android, missing socket protection prevents startup. Stopping an outbound stops its runtime.

## CI and building a test APK

The [core olcRTC CI workflow](https://github.com/SawaMEN/hiddify-core/blob/feature/olcrtc-protocol/.github/workflows/olcrtc-ci.yml) runs Go adapter tests, full native parser tests with production tags, builds Android ARM64 `hiddify-core.aar` and its matching `libhiddify-root.so`, and then builds a **development APK using that exact native core** from the app's `feature/olcrtc-protocol` branch.

The normal Hiddify App workflow still downloads a **stable released core** and therefore cannot prove olcRTC transport availability. Only use the integrated APK from the core olcRTC workflow to test this feature. Artifacts are short-lived and development/debug-signed, **not production releases**.

## Before releasing

- Confirm integration CI is green for the exact application and core commits.
- Test live WebRTC tunnel connectivity, TCP and UDP traffic, DNS resolution, server interoperability (OLC2/OLVC frame compatibility), reconnects, network handover, and failure recovery on real Android hardware.
- Audit application lifecycle (background, cancellation, profile removal, switching, multiple outbounds) and socket protection.
- Release and verify the new native Android core and root companion; then let the normal stable Hiddify App release consume that version.

No successful end-to-end WebRTC traffic test has yet been recorded for this development work. **A green build is not evidence of working connectivity.**
