import Foundation
import HiddifyCore

/// This executable proves the forked Go core is directly callable from
/// native Swift without a daemon, IPC or localhost proxy.
let result = MobileTest()
guard result == "Hello from mobile" else {
    fputs("Unexpected HiddifyCore probe result.\n", stderr)
    exit(1)
}
print("VetrOFF direct Swift ↔ Go bridge: OK")
