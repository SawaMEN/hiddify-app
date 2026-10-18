# VetrOFF macOS — signed-device acceptance checklist

**Status:** CI validates compilation and local Swift-Go calls; it cannot
prove a Network Extension actually carries IP packets. These manual
checks are release blockers, not assertions of success.

## 1. Prepare a dedicated Apple Silicon test Mac

Use macOS 14 or later, Xcode 16 or later, Homebrew and XcodeGen.
For the initial target, an M1 Mac with 8 GB RAM is especially useful
for measuring resident Go memory and behavior under memory pressure.

1. Download the latest successful
   [macOS Native Core Build](https://github.com/SawaMEN/hiddify-app/actions/workflows/macos-core-arm64.yml)
   artifact `HiddifyCore-macos-arm64-experiment`.
2. Extract its contents into
   `macos/Core/HiddifyCore.xcframework/`; the resulting folder must
   contain the `macos-arm64/HiddifyCore.framework` slice.
3. Run from the repository root:

   ```sh
   brew install xcodegen
   bash macos/scripts/generate-xcode-project.sh
   open macos/VetrOFF.xcodeproj
   ```

4. Set your own Apple Developer Team and provisioning for both
   `VetrOFF` and `PacketTunnel`. Both must have Network Extension
   packet-tunnel entitlement and the **same**
   `app.vetroff.client.macos.shared` Keychain group with the correct
   Team ID prefix. These identifiers must match the installed app.
5. Build/run the integrated **Debug** scheme, not the UI-only version.
   After granting VPN permission, install one JSON profile using the
   interface, select it and use **Тестировать VPN (Debug)**.

Do not put access tokens, subscriptions, private keys or full JSON
profiles into CI logs, bug reports or public issues.

## 2. Before/after checks

Run these first **with the VPN off**, and again while its system status
shows connected. Compare outputs manually; IP addresses do not have to
change if the profile intentionally routes directly.

```sh
ifconfig -l
scutil --dns
route -n get default
route -n get -inet6 default
curl -4 --connect-timeout 5 --max-time 12 https://api.ipify.org
curl -6 --connect-timeout 5 --max-time 12 https://api6.ipify.org
dig +notcp +time=3 +tries=1 example.org A
dig +notcp +time=3 +tries=1 example.org AAAA
```

IPv6 failure is not proof of protection: first establish whether
underlying IPv6 worked *before* connecting. Compare the intended
routing policy with observed IPv4/IPv6 destinations and route tables.

For DNS-leak review, capture **only your own traffic** on the
underlying physical interface (for example `en0`). Select the actual
interface using `networksetup -listallhardwareports`. Record whether
plain DNS on UDP/TCP port 53 or DoT on TCP port 853 exits the physical
interface unexpectedly; HTTPS DNS requires more careful attribution.

```sh
sudo tcpdump -n -i en0 'udp port 53 or tcp port 53 or tcp port 853'
```

A successful HTTP request alone does not prove that UDP is working.
Test UDP toward an endpoint you control (for example an `iperf3`
server) and separately exercise ordinary HTTPS connections.

## 3. Stop, cancel, reconnect and network transitions

- Connect and disconnect repeatedly while idle and under transfer.
- Press Disconnect **during** startup, including while the OS is
  applying TUN settings; confirm the extension never reports a
  false connected state and returns to disconnected.
- Reconnect immediately after a clean stop; verify stale routes
  and DNS entries were removed first.
- Switch Wi-Fi networks, temporarily lose internet, and sleep/wake.
  Test both IPv4-only and dual-stack physical networks where possible.
- Kill the extension process during a test, reopen the app, and
  confirm OS VPN status and route cleanup rather than stale UI state.
- Run with memory pressure; note whether the extension is terminated.

The app's Diagnostics screen uses Apple's provider-message channel
to request an in-process lifecycle state. Its `trafficVerified=false`
field is **by design** until a complete acceptance run succeeds.

## 4. Requirements before production Release

- Every permitted protocol uses the pinned forked Go core and passes
  real packet tests on macOS.
- Invalid, oversized and partially imported profiles fail closed.
- Keychain permissions survive ordinary app restart; no secrets
  appear in UserDefaults, logs or provider messages.
- DNS, IPv6 and split-routing agree with the selected policy.
- Disconnect/cancellation cannot leave an orphaned VPN route.
- Debug-only `VETROFF_NATIVE_CORE_TESTING` remains absent from Release.
- Signed app and extension pass notarization and Gatekeeper validation.

**Do not remove the Release connection gate based only on CI success.**

**Core revision requirement:** use the artifact named
`HiddifyCore-macos-ddb5a020fc54` from the experimental macOS core
build. Older `HiddifyCore-macos-arm64-experiment` artifacts predate the
explicit TUN activation and cannot safely verify VPN startup.
