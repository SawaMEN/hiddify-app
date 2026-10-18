import Foundation
import NetworkExtension
import Darwin

enum TunnelNetworkPlanError: LocalizedError, Equatable {
    case missingAddress
    case missingDNS
    case invalidIPv4
    case invalidIPv6
    case invalidMask
    case invalidPrefix
    case invalidMTU

    var errorDescription: String? {
        switch self {
        case .missingAddress: "В конфигурации TUN отсутствуют IP-адреса."
        case .missingDNS: "Не указан DNS для полного VPN-туннеля."
        case .invalidIPv4: "Некорректный IPv4-адрес."
        case .invalidIPv6: "Некорректный IPv6-адрес."
        case .invalidMask: "Некорректная IPv4-маска."
        case .invalidPrefix: "Длина IPv6-префикса выходит за пределы 0–128."
        case .invalidMTU: "MTU должен находиться в диапазоне 1280–9000."
        }
    }
}

struct TunnelIPv4Address: Equatable, Sendable {
    let address: String
    let mask: String
}

struct TunnelIPv6Address: Equatable, Sendable {
    let address: String
    let prefixLength: Int
}

/// Native control-plane mapping: the Go engine provides addresses, DNS and
/// routes once at startup; no per-packet Swift conversions or IPC are used.
struct TunnelNetworkPlan: Sendable {
    let ipv4: [TunnelIPv4Address]
    let ipv6: [TunnelIPv6Address]
    let dns: [String]
    let mtu: Int
    let fullTunnel: Bool
    var includedIPv4Routes: [TunnelIPv4Address] = []
    var excludedIPv4Routes: [TunnelIPv4Address] = []
    var includedIPv6Routes: [TunnelIPv6Address] = []
    var excludedIPv6Routes: [TunnelIPv6Address] = []

    func makeSettings() throws -> NEPacketTunnelNetworkSettings {
        guard !ipv4.isEmpty || !ipv6.isEmpty else {
            throw TunnelNetworkPlanError.missingAddress
        }
        guard (1280...9000).contains(mtu) else {
            throw TunnelNetworkPlanError.invalidMTU
        }
        if fullTunnel && dns.isEmpty {
            throw TunnelNetworkPlanError.missingDNS
        }

        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        settings.mtu = NSNumber(value: mtu)

        if !ipv4.isEmpty {
            for item in ipv4 {
                guard Self.isIPv4(item.address) else {
                    throw TunnelNetworkPlanError.invalidIPv4
                }
                guard Self.isIPv4(item.mask) else {
                    throw TunnelNetworkPlanError.invalidMask
                }
            }
            let cfg = NEIPv4Settings(
                addresses: ipv4.map(\.address),
                subnetMasks: ipv4.map(\.mask)
            )
            for route in includedIPv4Routes + excludedIPv4Routes {
                guard Self.isIPv4(route.address) else { throw TunnelNetworkPlanError.invalidIPv4 }
                guard Self.isIPv4(route.mask) else { throw TunnelNetworkPlanError.invalidMask }
            }
            let custom = includedIPv4Routes.map {
                NEIPv4Route(destinationAddress: $0.address, subnetMask: $0.mask)
            }
            cfg.includedRoutes = custom.isEmpty && fullTunnel ? [NEIPv4Route.default()] : custom
            cfg.excludedRoutes = excludedIPv4Routes.map {
                NEIPv4Route(destinationAddress: $0.address, subnetMask: $0.mask)
            }
            settings.ipv4Settings = cfg
        }

        if !ipv6.isEmpty {
            for item in ipv6 {
                guard Self.isIPv6(item.address) else {
                    throw TunnelNetworkPlanError.invalidIPv6
                }
                guard (0...128).contains(item.prefixLength) else {
                    throw TunnelNetworkPlanError.invalidPrefix
                }
            }
            let cfg = NEIPv6Settings(
                addresses: ipv6.map(\.address),
                networkPrefixLengths: ipv6.map { NSNumber(value: $0.prefixLength) }
            )
            for route in includedIPv6Routes + excludedIPv6Routes {
                guard Self.isIPv6(route.address) else { throw TunnelNetworkPlanError.invalidIPv6 }
                guard (0...128).contains(route.prefixLength) else { throw TunnelNetworkPlanError.invalidPrefix }
            }
            let custom = includedIPv6Routes.map {
                NEIPv6Route(destinationAddress: $0.address, networkPrefixLength: NSNumber(value: $0.prefixLength))
            }
            cfg.includedRoutes = custom.isEmpty && fullTunnel ? [NEIPv6Route.default()] : custom
            cfg.excludedRoutes = excludedIPv6Routes.map {
                NEIPv6Route(destinationAddress: $0.address, networkPrefixLength: NSNumber(value: $0.prefixLength))
            }
            settings.ipv6Settings = cfg
        }

        if !dns.isEmpty {
            for server in dns {
                guard Self.isIPv4(server) || Self.isIPv6(server) else {
                    throw TunnelNetworkPlanError.invalidIPv4
                }
            }
            let cfg = NEDNSSettings(servers: dns)
            if fullTunnel {
                cfg.matchDomains = [""]
                cfg.matchDomainsNoSearch = true
            }
            settings.dnsSettings = cfg
        }
        return settings
    }

    private static func isIPv4(_ text: String) -> Bool {
        var address = in_addr()
        return text.withCString { inet_pton(AF_INET, $0, &address) } == 1
    }

    private static func isIPv6(_ text: String) -> Bool {
        var address = in6_addr()
        return text.withCString { inet_pton(AF_INET6, $0, &address) } == 1
    }
}
