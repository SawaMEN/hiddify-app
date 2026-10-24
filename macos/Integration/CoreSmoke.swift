import Foundation
import HiddifyCore

/// This executable proves the forked Go core is directly callable from
/// native Swift without a daemon, IPC or localhost proxy.
let result = MobileTest()
guard result == "Hello from mobile" else {
    fputs("Unexpected HiddifyCore probe result.\n", stderr)
    exit(1)
}
// Probe the macOS-only Go API. An empty profile must fail before the
// native core initializes or starts any listener; this is a safe runtime
// export/link check on an unsigned GitHub Actions macOS runner.
var invalidInputError: NSError?
guard !MobileStartPacketTunnel("", &invalidInputError), invalidInputError != nil else {
    fputs("PacketTunnel entrypoint unexpectedly accepted an empty profile.\n", stderr)
    exit(1)
}
print("VetrOFF direct Swift ↔ Go bridge: OK")
print("VetrOFF macOS-only TUN entrypoint rejects empty profile: OK")


// Native preflight uses the exact pinned Go parser; no VPN setup or
// Network Extension settings are required. Validate a nonsecret fixture.
let validationID = UUID().uuidString
var registrationError: NSError?
guard MobileBeginProfileValidation(validationID, &registrationError) else {
    fputs("Cannot register Go profile preflight.\n", stderr)
    exit(1)
}
let validProfile = #"{"outbounds":[{"type":"socks","tag":"test-node","server":"example.org","server_port":1080}]}"#
var profileError: NSError?
let acceptsValid = MobileValidateProfile(validationID, validProfile, "", &profileError)
MobileFinishProfileValidation(validationID)
guard acceptsValid else {
    fputs("Go profile parser rejected a nonsecret SOCKS fixture.\n", stderr)
    exit(1)
}
let badID = UUID().uuidString
var badRegistrationError: NSError?
guard MobileBeginProfileValidation(badID, &badRegistrationError) else {
    fputs("Cannot register invalid-profile test.\n", stderr)
    exit(1)
}
var invalidProfileError: NSError?
let acceptsInvalid = MobileValidateProfile(badID, "not-a-valid-proxy", "", &invalidProfileError)
MobileFinishProfileValidation(badID)
guard !acceptsInvalid else {
    fputs("Go profile parser accepted malformed input.\n", stderr)
    exit(1)
}
print("VetrOFF Go-native preflight: valid profile accepted, invalid profile rejected")

// Nonsecret compatibility fixtures are parsed by the shipped Go framework,
// without creating a tunnel or contacting a server. Swift admission alone
// cannot establish that the native engine understands a subscription format.
let vmess = Data(#"{"v":"2","ps":"VMess fixture","add":"example.org","port":"443","id":"11111111-1111-4111-8111-111111111111","aid":"0","net":"tcp","type":"none","tls":"tls"}"#.utf8).base64EncodedString()
let key1 = Data(repeating: 1, count: 32).base64EncodedString()
let key2 = Data(repeating: 2, count: 32).base64EncodedString()
let fixtures: [(String, String)] = [
    ("mixed panel subscription", "# profile-title: Fixture\nsnell://psk@example.org:443?version=4\ntrojan://password@example.org:443?security=tls"),
    ("VMess Base64", "vmess://" + vmess),
    ("Mieru share", "mierus://user:password@example.org?port=443&protocol=TCP"),
    ("Naive HTTPS", "naive+https://user:password@example.org:443"),
    ("Clash YAML node", "proxies:\n  - name: Fixture\n    type: ss\n    server: example.org\n    port: 443\n    cipher: aes-128-gcm\n    password: password\n"),
    ("WireGuard INI", "[Interface]\nPrivateKey = \(key1)\nAddress = 10.0.0.2/32\n[Peer]\nPublicKey = \(key2)\nAllowedIPs = 0.0.0.0/0\nEndpoint = example.org:51820\n"),
    ("JSON dialer dependency", #"{"outbounds":[{"type":"socks","tag":"selected","server":"one.example","server_port":1080,"detour":"hop §hide§"},{"type":"socks","tag":"hop §hide§","server":"two.example","server_port":1080}]}"#)
]
for (name, content) in fixtures {
    let id = UUID().uuidString
    var registration: NSError?
    guard MobileBeginProfileValidation(id, &registration) else {
        fputs("Cannot register compatibility test.\n", stderr); exit(1)
    }
    var error: NSError?
    let accepted = MobileValidateProfile(id, content, "", &error)
    MobileFinishProfileValidation(id)
    guard accepted else {
        // Only the fixed fixture label is emitted, never parser errors/source.
        fputs("Native parser rejected fixture: \(name)\n", stderr); exit(1)
    }
    print("Native parser accepted: \(name)")
}
