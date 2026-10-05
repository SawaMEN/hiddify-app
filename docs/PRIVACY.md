# VetrOFF Client: privacy

VetrOFF Client is an open-source VPN client. Profiles, settings and diagnostic logs are stored locally on the device. This client does not include the analytics SDK removed from this fork.

Connections use the servers supplied by your profiles. Subscription refreshes contact subscription URLs; update checks contact GitHub. Optional DNS, WARP and Psiphon settings contact their respective services. Those services and your VPN provider have their own policies and can receive connection data. The client cannot guarantee anonymity from them.

Android VPN and optional root permissions are used for routing. Package access supports per-app routing; battery optimization exemption supports background connections. Private APK generation exports a locally generated APK and its settings backup. Do not share a backup or logs containing credentials.

Source, changes and issue reports: https://github.com/SawaMEN/hiddify-app
