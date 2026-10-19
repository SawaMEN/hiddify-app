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
