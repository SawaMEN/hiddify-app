# VPN sharing and My Handbook routing

General settings → **Share VPN over Wi-Fi** on Android automatically creates a secured Wi-Fi access point and starts the selected VPN profile. The app displays the actual SSID, Wi-Fi password, AP IP and a Wi-Fi QR code. Android 16+ uses a custom WPA2/2.4 GHz configuration; Android 10–15 chooses compatible credentials/configuration. Stop any existing system hotspot first if Android reports a conflict. Required Wi-Fi permission is requested; Android 10–12 also requires Location enabled. Root mode grants the feature permission to this package and temporarily enables Location on those older releases, restoring its original setting afterward.

Root mode uses the existing root companion and sing-tun `auto_redirect`. No client proxy or manual forwarding setup is required. The device must grant the application's root request and support TUN, iptables and ip6tables. The app enables IPv4 forwarding and installs its own interface-scoped forwarding guards: AP traffic can only cross the root TUN, never the regular uplink. Rollback state is persisted before privileged mutations. Disabling sharing, VPN failure or explicit VPN stop closes the AP; guards are removed only after its old interface/address disappears. A config reload preserves the AP. Startup failure closes it and leaves fail-closed guards in place if Android has not yet removed the interface. No system firewall tables are flushed.

Without root Android's LocalOnlyHotspot supplies Wi-Fi and DHCP, and the VPN core serves an authenticated HTTP/SOCKS proxy at the displayed AP IP. Android does not give ordinary VPN apps transparent control of hotspot traffic; clients must configure the proxy using the illustrated OS tabs. The feature opens the mixed listener on LAN even when local listeners are hidden or the mixed port is disabled. On first enable it creates a random proxy password when none exists. Username: `hiddify`. Wi-Fi and proxy passwords are distinct. Only proxy-aware applications are covered without root. Non-Android hosts retain their existing sharing settings.

VPN privacy → **Additional settings → Routing → My Handbook routing lists** replaces bundled regional/app policy. Built-in selections remain saved, their controls are disabled, and native policy plus core generation prevent both policies from applying simultaneously. Automatic protection switches back to bundled routing and preserves the prior alternative-mode setting in its restore snapshot. Full tunnel takes precedence over external lists.

The two independently selectable catalogues are:

- `https://iplist.my-handbook.ru/ru`: selected services through VPN.
- `https://ru-iplist.my-handbook.ru/ru`: selected Russian services direct.

Optional comma-separated service domains filter each catalogue; empty means all services. Proxy wins when providers overlap. Profile routing remains the fallback for other traffic.

Both deployed services currently return HTTP 404 for `format=singbox`. The core downloads their supported `format=json` exports over HTTPS and converts domains, individual IP addresses and IPv4/IPv6 prefixes into separate inline rule sets. Multiple services use repeated `site` query parameters. Bootstrap downloads happen before VPN starts. Validated catalogues are cached atomically under `data/handbook` and refreshed on configuration rebuild after 24 hours. A valid cache permits offline restarts; invalid/error responses cannot overwrite it. A first-use failure aborts connection rather than silently omitting selected rules.

The APK launcher icon uses a separate caption-free fan/shield foreground on an iridescent cyan/blue/violet background. Android 13+ themed icons use a monochrome version of that foreground; Android supplies the theme colors. In-app, splash and TV logos retain the full VetrOFF caption. `tool/generate_brand_assets.py` keeps these sources separate and regenerates all density/adaptive resources.

Core validation: configuration and hcore tests pass, including fixtures for source parsing, query encoding, cache reuse/failure, alternative routing, full tunnel priority, root auto-redirect and authenticated LAN listeners. Live catalogues were also parsed successfully. A physical root-device smoke test remains necessary for hotspot/kernel/OEM compatibility.

## Illustrated sharing guides

The guide assumes the Android phone running this app is already sharing its
Wi-Fi hotspot through VPN. Every OS tab describes only a receiving device:
joining the phone network, finding its gateway, configuring/authenticating a proxy,
testing the connection and removing the proxy afterward. No desktop Internet
Sharing/ICS, Linux forwarding or iOS hosting setup is included in the tabs.

Illustrated settings frames use Flutter widgets, adapt to themes and text scaling,
and show the actual proxy port. They are labeled as illustrations. In Android
root host mode, every client OS tab instead explains how to join without a proxy
and disable any previously configured proxy; the proxy credentials card is hidden; Wi-Fi credentials remain visible.

OS settings references:
- https://support.google.com/android/answer/9059108
- https://support.microsoft.com/en-us/windows/experience/connectivity-networking/use-a-proxy-server-in-windows
- https://help.gnome.org/users/gnome-help/stable/net-proxy.html.en
- https://support.apple.com/guide/mac-help/mchlp2591/mac
- https://support.apple.com/guide/iphone/iphw5gjwl8k2/ios

Launcher alignment: the dedicated mark uses a centered fan ring with short wind
accents. Asset packaging centers its visible alpha bounds on a square canvas and
scales in both directions consistently for launcher densities. Branded in-app,
splash and TV sources are unchanged.

## Automatic hotspot verification

Before release, test on physical Android 10–12 and 13+ devices, both root and ordinary mode: permission denial, no selected profile, root denial, another active hotspot, VPN startup failure, screen-off operation, IPv4/IPv6 clients, VPN stop and config reload. In root mode verify the client's public IP and DNS through the VPN, and verify the client cannot reach the Internet after the root core is killed. OEM firmware and kernel differences require device validation; JVM/widget tests alone do not prove packet forwarding.
