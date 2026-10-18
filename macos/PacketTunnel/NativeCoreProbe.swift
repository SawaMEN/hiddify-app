import Foundation

#if canImport(HiddifyCore)
import HiddifyCore
#endif

/// A link-time and runtime probe for the forked Go engine.
/// This never starts a tunnel; packet forwarding requires a separate adapter.
enum NativeCoreProbe {
    static func isLinked() -> Bool {
        #if canImport(HiddifyCore)
        return MobileTest() == "Hello from mobile"
        #else
        return false
        #endif
    }
}
