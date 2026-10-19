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
