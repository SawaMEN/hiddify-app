# VPN sharing and My Handbook routing

General settings → **Share VPN over Wi-Fi** enables sharing. Android root mode uses sing-tun `auto_redirect`, including forwarded hotspot/repeater traffic. Enable the system hotspot and grant root permission; the kernel must support TUN and netfilter. The root companion owns the tunnel and network rules, so stopping it cleans up its rules. The app does not programmatically start the system hotspot.

Without root the feature opens the configured HTTP/SOCKS mixed listener on LAN, even when local listeners are hidden or the mixed port is disabled. On first enable it creates a random proxy password when no password exists. Username: `hiddify`. Existing passwords are retained. The app provides OS tabs for Android, Windows, Linux, macOS and iOS/iPadOS, including hotspot creation, client proxy setup, authentication and checks. The Wi-Fi gateway on the connected client is the authoritative host address when LAN-IP detection picks another interface. Ordinary proxy sharing covers proxy-aware applications, not all traffic. Desktop transparent sharing requires operating-system configuration and privileges. iOS cannot transparently share its VPN through Personal Hotspot.

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
and disable any previously configured proxy; the credentials card is hidden.

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
