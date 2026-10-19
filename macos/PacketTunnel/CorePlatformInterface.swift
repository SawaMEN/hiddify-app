import Foundation
import Darwin
import Network
@preconcurrency import NetworkExtension

#if canImport(HiddifyCore)
import HiddifyCore

private let corePlatformErrorDomain = "app.vetroff.client.macos.CorePlatform"

/// The one native Go <-> macOS boundary. Packet bytes are read/written by
/// sing-tun using the duplicated utun file descriptor; Swift never copies
/// packets, wraps TCP sockets, or inserts a localhost proxy in that path.
final class CorePlatformInterface: NSObject, LibboxPlatformInterfaceProtocol, @unchecked Sendable {
    private let tunnel: NEPacketTunnelProvider
    private let lifecycle: TunnelLifecycle
    private let access = NSLock()
    private var monitor: NativeDefaultNetworkMonitor?
    private var didOpenTun = false

    var hasOpenedTunnel: Bool {
        access.lock()
        defer { access.unlock() }
        return didOpenTun
    }

    init(tunnel: NEPacketTunnelProvider, lifecycle: TunnelLifecycle) {
        self.tunnel = tunnel
        self.lifecycle = lifecycle
        super.init()
    }

    func openTun(_ options: (any LibboxTunOptionsProtocol)?, ret0_: UnsafeMutablePointer<Int32>?) throws {
        guard let options, let ret0_ else {
            throw NSError(domain: corePlatformErrorDomain, code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Missing TUN options or output file descriptor."
            ])
        }
        // Go invokes OpenTun synchronously on its own worker thread.
        // Never block main or resolve the utun descriptor before the OS applies
        // network settings and creates the underlying control socket.
        if Thread.isMainThread {
            throw NSError(domain: corePlatformErrorDomain, code: 2, userInfo: [
                NSLocalizedDescriptionKey: "OpenTun must run off the main thread."
            ])
        }

        let ipv4 = Self.prefixes(options.getInet4Address()).map {
            TunnelIPv4Address(address: $0.address(), mask: $0.mask())
        }
        let ipv6 = Self.prefixes(options.getInet6Address()).map {
            TunnelIPv6Address(address: $0.address(), prefixLength: Int($0.prefix()))
        }

        let dns = try Self.strings(options.getDNSServerAddress())
        let dnsMode = options.getDNSMode()?.value ?? ""
        let automatic = options.getAutoRoute()

        var plan = TunnelNetworkPlan(
            ipv4: ipv4,
            ipv6: ipv6,
            dns: dnsMode == "disabled" ? [] : dns,
            mtu: Int(options.getMTU()),
            fullTunnel: automatic
        )
        plan.includedIPv4Routes = Self.prefixes(options.getInet4RouteAddress()).map {
            TunnelIPv4Address(address: $0.address(), mask: $0.mask())
        }
        plan.excludedIPv4Routes = Self.prefixes(options.getInet4RouteExcludeAddress()).map {
            TunnelIPv4Address(address: $0.address(), mask: $0.mask())
        }
        plan.includedIPv6Routes = Self.prefixes(options.getInet6RouteAddress()).map {
            TunnelIPv6Address(address: $0.address(), prefixLength: Int($0.prefix()))
        }
        plan.excludedIPv6Routes = Self.prefixes(options.getInet6RouteExcludeAddress()).map {
            TunnelIPv6Address(address: $0.address(), prefixLength: Int($0.prefix()))
        }
        let settings = try plan.makeSettings()
        try lifecycle.checkStarting()

        // We must use a bounded wait and treat timeout as a failed start,
        // otherwise NEPacketTunnelProvider can claim success while blackholing.
        let completed = DispatchSemaphore(value: 0)
        let completionState = TunnelSettingsResult()
        tunnel.setTunnelNetworkSettings(settings) { error in
            completionState.set(error)
            completed.signal()
        }
        let deadline = DispatchTime.now() + .seconds(20)
        while completed.wait(timeout: .now() + .milliseconds(200)) == .timedOut {
            try lifecycle.checkStarting()
            if DispatchTime.now() >= deadline {
                throw NSError(domain: corePlatformErrorDomain, code: 3, userInfo: [
                    NSLocalizedDescriptionKey: "Applying macOS VPN network settings timed out."
                ])
            }
        }
        try lifecycle.checkStarting()
        if let error = completionState.get() {
            throw error
        }

        // Public, fork-generated Go helper finds Apple's already opened
        // utun control socket. The Go tun reader duplicates the descriptor.
        let fd = LibboxGetTunnelFileDescriptor()
        guard fd >= 0 else {
            throw NSError(domain: corePlatformErrorDomain, code: 4, userInfo: [
                NSLocalizedDescriptionKey: "macOS utun descriptor was not found."
            ])
        }
        try lifecycle.checkStarting()
        ret0_.pointee = fd
        access.lock()
        didOpenTun = true
        access.unlock()
    }

    private static func prefixes(_ iterator: (any LibboxRoutePrefixIteratorProtocol)?) -> [LibboxRoutePrefix] {
        guard let iterator else { return [] }
        var result: [LibboxRoutePrefix] = []
        while iterator.hasNext() {
            if let prefix = iterator.next() { result.append(prefix) }
        }
        return result
    }

    private static func strings(_ iterator: (any LibboxStringIteratorProtocol)?) -> [String] {
        guard let iterator else { return [] }
        var result: [String] = []
        while iterator.hasNext() {
            result.append(iterator.next())
        }
        return result
    }

    func usePlatformAutoDetectControl() -> Bool { false }
    func autoDetectControl(_ fd: Int32) throws {}
    func useProcFS() -> Bool { false }
    func underNetworkExtension() -> Bool { true }
    func includeAllNetworks() -> Bool { false }
    func clearDNSCache() {}
    func localDNSTransport() -> (any LibboxLocalDNSTransportProtocol)? { nil }
    func readWIFIState() -> LibboxWIFIState? { nil }
    func registerMyInterface(_ name: String?) {}
    func systemCertificates() -> (any LibboxStringIteratorProtocol)? { nil }
    func tailscaleHostname() -> String { Host.current().localizedName ?? "VetrOFF-macOS" }

    func getInterfaces() throws -> any LibboxNetworkInterfaceIteratorProtocol {
        var head: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&head) == 0 else {
            throw NSError(domain: corePlatformErrorDomain, code: 5, userInfo: [
                NSLocalizedDescriptionKey: "getifaddrs failed."
            ])
        }
        defer { if let head { freeifaddrs(head) } }

        struct Snapshot {
            var flags: UInt32
            var addresses: [String]
        }
        var snapshots: [String: Snapshot] = [:]
        var cursor = head
        while let item = cursor {
            let record = item.pointee
            let name = String(cString: record.ifa_name)
            cursor = record.ifa_next
            if name.hasPrefix("utun") { continue }

            var snapshot = snapshots[name] ?? Snapshot(flags: record.ifa_flags, addresses: [])
            if let address = record.ifa_addr, let mask = record.ifa_netmask {
                let family = Int32(address.pointee.sa_family)
                if family == AF_INET || family == AF_INET6 {
                    var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                    let status = host.withUnsafeMutableBufferPointer { buffer in
                        getnameinfo(
                            address,
                            socklen_t(address.pointee.sa_len),
                            buffer.baseAddress,
                            socklen_t(buffer.count),
                            nil,
                            0,
                            NI_NUMERICHOST
                        )
                    }
                    if status == 0 {
                        let ip = String(cString: host).split(separator: "%").first.map(String.init) ?? ""
                        let length: Int
                        if family == AF_INET {
                            let value = UnsafeRawPointer(mask)
                                .assumingMemoryBound(to: sockaddr_in.self)
                                .pointee.sin_addr.s_addr
                            length = Int(value.nonzeroBitCount)
                        } else {
                            let value = UnsafeRawPointer(mask)
                                .assumingMemoryBound(to: sockaddr_in6.self)
                                .pointee.sin6_addr
                            length = withUnsafeBytes(of: value) { bytes in
                                bytes.reduce(0) { $0 + $1.nonzeroBitCount }
                            }
                        }
                        let cidr = "\(ip)/\(length)"
                        if !ip.isEmpty && !snapshot.addresses.contains(cidr) {
                            snapshot.addresses.append(cidr)
                        }
                    }
                }
            }
            snapshots[name] = snapshot
        }

        let interfaces: [LibboxNetworkInterface] = snapshots.map { name, snapshot in
            let descriptor = LibboxNetworkInterface()
            descriptor.name = name
            descriptor.index = Int32(if_nametoindex(name))
            descriptor.flags = Int32(bitPattern: snapshot.flags)
            // macOS may report different MTUs on cellular/Ethernet/Wi-Fi.
            // Never infer a physical interface MTU from the virtual TUN MTU;
            // 1500 is the safe fallback until an ioctl probe is installed.
            descriptor.mtu = 1500
            descriptor.type = LibboxInterfaceTypeOther
            descriptor.metered = false
            descriptor.addresses = NativeStringIterator(values: snapshot.addresses)
            descriptor.dnsServer = NativeStringIterator(values: [])
            descriptor.gateway = NativeStringIterator(values: [])
            return descriptor
        }
        return NativeInterfaceIterator(values: interfaces)
    }

    func startDefaultInterfaceMonitor(_ listener: (any LibboxInterfaceUpdateListenerProtocol)?) throws {
        guard let listener else { return }
        let newMonitor = NativeDefaultNetworkMonitor(listener: listener)
        access.lock()
        let previous = monitor
        monitor = newMonitor
        access.unlock()
        previous?.cancel()
        newMonitor.start()
    }

    func closeDefaultInterfaceMonitor(_ listener: (any LibboxInterfaceUpdateListenerProtocol)?) throws {
        access.lock()
        let current = monitor
        monitor = nil
        access.unlock()
        current?.cancel()
    }

    func findConnectionOwner(
        _ ipProtocol: Int32,
        sourceAddress: String?,
        sourcePort: Int32,
        destinationAddress: String?,
        destinationPort: Int32
    ) throws -> LibboxConnectionOwner {
        throw NSError(domain: corePlatformErrorDomain, code: 6, userInfo: [
            NSLocalizedDescriptionKey: "Per-process ownership needs a privileged macOS provider."
        ])
    }

    func send(_ notification: LibboxNotification?) throws {}
    func cancelNotification(_ identifier: String?, typeID: Int32) throws {}
    func startNeighborMonitor(_ listener: (any LibboxNeighborUpdateListenerProtocol)?) throws {}
    func closeNeighborMonitor(_ listener: (any LibboxNeighborUpdateListenerProtocol)?) throws {}

    func usePlatformShell() -> Bool { false }
    func checkPlatformShell() throws { throw unsupported("shell") }
    func openShellSession(
        _ user: LibboxPlatformUser?,
        command: String?,
        environ: (any LibboxStringIteratorProtocol)?,
        term: String?,
        rows: Int32,
        cols: Int32
    ) throws -> any LibboxShellSessionProtocol {
        throw unsupported("shell session")
    }
    func lookupUser(_ username: String?) throws -> LibboxPlatformUser {
        throw unsupported("user lookup")
    }
    func lookupSFTPServer() throws -> LibboxStringBox {
        throw unsupported("SFTP")
    }
    func readSystemSSHHostKey() throws -> LibboxStringBox {
        throw unsupported("host key")
    }
    func usePlatformBridge() -> Bool { false }
    func createBridge(_ options: LibboxBridgeOptions?) throws -> any LibboxBridgeSessionProtocol {
        throw unsupported("platform bridge")
    }
    func usePlatformAutoRedirect() -> Bool { false }
    func createAutoRedirect(
        _ options: Data?,
        handler: (any LibboxAutoRedirectHandlerProtocol)?
    ) throws -> any LibboxAutoRedirectSessionProtocol {
        throw unsupported("auto-redirect")
    }

    private func unsupported(_ feature: String) -> NSError {
        NSError(domain: corePlatformErrorDomain, code: 7, userInfo: [
            NSLocalizedDescriptionKey: "Unsupported macOS platform feature: \(feature)"
        ])
    }

    func reset() {
        access.lock()
        let current = monitor
        monitor = nil
        didOpenTun = false
        access.unlock()
        current?.cancel()
    }
}

/// Safe handoff from NetworkExtension completion callback to the Go worker.
private final class TunnelSettingsResult: @unchecked Sendable {
    private let lock = NSLock()
    private var error: Error?
    func set(_ next: Error?) {
        lock.lock()
        error = next
        lock.unlock()
    }
    func get() -> Error? {
        lock.lock()
        defer { lock.unlock() }
        return error
    }
}

private final class NativeStringIterator: NSObject, LibboxStringIteratorProtocol {
    private let items: [String]
    private var offset = 0
    init(values: [String]) { items = values }
    func hasNext() -> Bool { offset < items.count }
    func len() -> Int32 { Int32(items.count) }
    func next() -> String {
        defer { offset += 1 }
        return offset < items.count ? items[offset] : ""
    }
}

private final class NativeInterfaceIterator: NSObject, LibboxNetworkInterfaceIteratorProtocol {
    private let values: [LibboxNetworkInterface]
    private var index = 0
    init(values: [LibboxNetworkInterface]) { self.values = values }
    func hasNext() -> Bool { index < values.count }
    func next() -> LibboxNetworkInterface? {
        guard index < values.count else { return nil }
        defer { index += 1 }
        return values[index]
    }
}

private final class NativeDefaultNetworkMonitor: @unchecked Sendable {
    let listener: any LibboxInterfaceUpdateListenerProtocol
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "app.vetroff.client.macos.network-path")

    init(listener: any LibboxInterfaceUpdateListenerProtocol) {
        self.listener = listener
    }

    func start() {
        monitor.pathUpdateHandler = { [self] path in
            let interface = path.availableInterfaces.first(where: {
                !$0.name.hasPrefix("utun") && !$0.name.hasPrefix("lo")
            })
            listener.updateDefaultInterface(
                interface?.name ?? "",
                interfaceIndex: interface.map { Int32($0.index) } ?? -1,
                isExpensive: path.isExpensive,
                isConstrained: path.isConstrained
            )
            let status = path.status == .satisfied ? "satisfied" : "unsatisfied"
            listener.updateNetworkPath(status)
        }
        monitor.start(queue: queue)
    }

    func cancel() {
        monitor.cancel()
    }
}
#endif
