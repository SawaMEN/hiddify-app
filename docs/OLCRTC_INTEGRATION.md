# olcRTC / Olcbox integration status

## Scope

[Olcbox](https://github.com/alananisimov/olcbox) is an application built around
**olcRTC**, a separate Go WebRTC tunnel engine. It is not a built-in sing-box
outbound. Olcbox's original repository is archived and labels itself alpha.

This branch provides the **first, non-networking stage** only:
- Parse the documented `olcrtc://provider?transport<key=value&...>@room#key$comment`
  client share format without treating `#key` as the profile name.
- Recognize olcRTC subscription entries (including names containing spaces), detect
  duplicates while ignoring the human-readable comment, validate the 64-hex key,
  and avoid exposing keys in diagnostic `toString()`.
- Fail closed with an explicit **native runtime not integrated** import warning.
  This intentionally **does not** claim that a connection can be established.

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

**Do not remove the unsupported-protocol guard** until the native engine,
packaging and lifecycle checks above are implemented. Otherwise the UI can
incorrectly display a working profile for a connection that cannot start.

References:
- https://github.com/alananisimov/olcbox
- https://github.com/openlibrecommunity/olcrtc/blob/master/docs/uri.md
- https://github.com/ghostlane-project/olcrtc
