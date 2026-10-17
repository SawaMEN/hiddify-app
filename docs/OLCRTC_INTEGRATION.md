# olcRTC / Olcbox integration status

## Scope

[Olcbox](https://github.com/alananisimov/olcbox) is an application built around
**olcRTC**, a separate Go WebRTC tunnel engine. It is not a built-in sing-box
outbound. Olcbox's original repository is archived and labels itself alpha.

The app branch provides the profile-import stage:
- Parse the documented `olcrtc://provider?transport<key=value&...>@room#key$comment`
  client share format without treating `#key` as the profile name.
- Recognize olcRTC subscription entries (including names containing spaces), detect
  duplicates while ignoring the human-readable comment, validate the 64-hex key,
  and avoid exposing keys in diagnostic `toString()`.
- Let the already present native `Mobile.validateProfile` API validate real olcRTC
  outbounds using the currently bundled core. The app does not treat a parsed
  URL as proof of network support; old cores reject unknown olcRTC URLs.

## Remaining prerequisites for real VPN support

1. Choose and pin a vetted olcRTC **engine** commit, with compatible authenticated
   record format and server version. Do not vendor a mutable branch or assume
   an Olcbox UI build contains a native sing-box transport.
2. Integrate the Go engine into `SawaMEN/hiddify-core` or
   `SawaMEN/hiddify-sing-box`, and publish a matching Android ARM64 core artifact.
   The app fetches its engine from stable `SawaMEN/hiddify-core` releases.
3. Bridge engine-generated **loopback-only SOCKS5** traffic into the existing
   sing-box routing/tunnel, not a second Android `VpnService`. Keep the engine's
   WebRTC signaling and media sockets off the VPN route to prevent loops.
4. Manage one runtime per active connection, with correct selection, cancellation,
   network migration, restart, stop, deletion, background lifecycle, and local port
   ownership. Mixed subscriptions and proxy chains need their own routing design.
5. Add a full UI (import/deeplinks, provider and transport parameters, protected
   credentials), native integration tests, Android instrumentation tests, and
   authenticated end-to-end interoperability tests against matching servers.
   Ensure OLC2 and OLVC protocol versions match the selected server.

**Native release prerequisite:** No published Android ARM64 core release includes
these development-branch changes yet. The app currently downloads its core from
stable releases. Direct `olcrtc://` imports need a compatible core build;
import failures from the old core are expected until that release exists.
Do not claim a functioning tunnel based solely on successful URI parsing.

References:
- https://github.com/alananisimov/olcbox
- https://github.com/openlibrecommunity/olcrtc/blob/master/docs/uri.md
- https://github.com/ghostlane-project/olcrtc

## Work in progress branches

- [hiddify-core/pull/7](https://github.com/SawaMEN/hiddify-core/pull/7): strict native URI parsing and a matching sing-box submodule SHA.
- [hiddify-sing-box/pull/2](https://github.com/SawaMEN/hiddify-sing-box/pull/2): olcRTC Go-runtime outbound with SOCKS5 authentication and Android network protection.
- Both are intentionally draft changes until compilation, Android packaging, and end-to-end traffic tests succeed.
