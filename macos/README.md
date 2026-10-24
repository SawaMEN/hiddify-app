# VetrOFF Client for macOS

Native SwiftUI client for macOS 14+ on Apple Silicon. The original VetrOFF
artwork, Dock icon, dark/light/system appearance, sidebar, connection panel,
profiles and searchable server list are included. Android is independent.

## Available workflows

- Add a subscription from the home page, sidebar, toolbar or Command-N.
- Paste HTTPS, `hiddify://import/`, `vetroff://import/`, server links,
  Base64 subscriptions, JSON or Clash YAML. Command-Shift-V opens clipboard import.
- Import a file, rename, export or delete a profile with confirmation.
- Refresh one or all subscriptions, retain working data on download failure,
  preserve the selected URI when a subscription is reordered.
- Select a URI server, search nodes and run bounded TCP port reachability checks
  for supported stream protocols. TCP checks are not VPN latency or speed tests.
- Register native macOS login startup, optionally connect the saved profile on
  launch, view the system connection duration and a bounded status-event journal.
- Choose automatic refresh every 1/6/12/24 hours while the app is open and VPN
  is off. This does not schedule work after the application exits.
- Install the system VPN configuration and connect from a provisioned build
  containing the native Go core; disconnect and switch profiles from the menu bar.

The importer supports the URI schemes of the pinned Go parser (including
Snell, Mieru, TrustTunnel, FPTN, Psiphon and olcRTC), complete URI chains,
Base64 envelopes, sing-box JSON/JSONC, arrays of complete configurations,
Clash YAML and WireGuard/AmneziaWG INI. Protocol decoding and runtime validation
remain in Go. Unknown schemes remain visible and cannot be selected; one unknown
node does not discard the subscription's supported nodes. Configuration admission
is not a claim that every server/transport has passed real-network acceptance.

JSON/YAML node selection retains transitive dialer dependencies. A selected node
uses the packet engine's DNS/routing policy; it does not apply an arbitrary
imported full-system routing policy. Nodes depending on unresolved groups are
shown as unavailable rather than losing the dependency silently.

Names come from profile-title (plain or Base64), Content-Disposition or inline
comments. A manually chosen name survives refresh; the profile menu can restore
the provider name. Traffic and expiry from subscription-userinfo are provider
quota information, not fabricated live tunnel counters. Provider website and
support links are retained in Keychain. Source editing, duplication and per-profile
refresh errors are available.

HTTP/HTTPS subscriptions and public IP addresses are supported. TLS verification
is never disabled, redirects cannot downgrade HTTPS to HTTP, and local addresses
are rejected. Nested subscription expansion is bounded to 16 URLs, depth 2 and
8 MiB with cycle detection. This URL validation is not a DNS-rebinding firewall.

Native traffic counters, per-app routing and Android's generated free-profile
services are not yet implemented in macOS. A real VPN still needs the provisioned
Network Extension and physical-device tests described below.

## Keychain and distribution

The ad-hoc DMG can manage subscriptions using the host application's private
login Keychain. It cannot run Network Extension without Apple's capabilities.
A provisioned app and extension use the shared **data protection Keychain**;
the host migrates private preview entries on access. The extension never falls
back to application-only storage. Only profile metadata goes into UserDefaults;
subscription URLs, credentials and configuration contents stay in Keychain.
Export deliberately writes a user-selected plaintext file containing credentials.

The core-integrated project defines `VETROFF_NATIVE_CORE` in both Debug and
Release. Connect checks actual VPN and shared-Keychain entitlements; a no-core
build or ad-hoc preview explains the missing capability. The extension validates
configuration before installing routes and reports startup success only after
Go opens TUN and network settings complete. A successful build is not proof of
packet forwarding, DNS/IPv6 safety or sleep/wake reliability.

A usable VPN installer still requires Developer ID signing, separate app and
extension provisioning profiles, Apple notarization and physical Mac acceptance.
See [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) and
[ON_DEVICE_TESTING.md](ON_DEVICE_TESTING.md). Never advertise the ad-hoc DMG as a
working VPN installer. The public signed-release workflow retains its physical
acceptance gate.

## Build and validation

On a Mac with Xcode 16+ and XcodeGen:

```sh
brew install xcodegen
bash macos/scripts/generate-xcode-project.sh
xcodebuild -project macos/VetrOFF.xcodeproj -scheme VetrOFF \
  -configuration Debug -destination 'platform=macOS,arch=arm64' \
  CODE_SIGNING_ALLOWED=NO test
```

Without `macos/Core/HiddifyCore.xcframework` this builds the native UI and tests.
With the pinned framework, the script links Go only into PacketTunnel, never
into the main app process. A shared core performs protocol parsing and packet
forwarding; Swift does not duplicate proxy stacks or use a localhost RPC hop.

`macos-dmg.yml` builds one ARM64 DMG and launches the installed app. Its opt-in
smoke test exercises actual private-Keychain profile add/read/select/rename/delete
and checks that the SwiftUI window remains alive. Source tests cover import
wrappers, token preservation, Base64, source formats, vault scope boundaries,
subscription validation, routing and tunnel lifecycle. Device networking tests
remain separate from these checks.

## Engineering rules

1. Keep changes scoped to macOS and its CI. Do not modify Android for macOS work.
2. Use native SwiftUI, Foundation, Security and NetworkExtension.
3. Keep secrets out of logs, defaults, diagnostics and Network Extension options.
4. Trust system VPN status; never display simulated connection success or traffic.
5. Preserve working profiles when imports or subscription downloads fail.
6. Validate signed tunnel traffic, DNS, IPv6, reconnect and sleep/wake on a real Mac.
