import Foundation
import NetworkExtension
import Darwin

enum TunnelNetworkPlanError: LocalizedError, Equatable {
    case missingAddress
    case missingIPv4
    case missingIPv6
    case missingDNS
    case invalidIPv4
    case invalidIPv6
    case invalidMask
    case unsafeExclusion
    case invalidPrefix
    case invalidMTU

    var errorDescription: String? {
        switch self {
        case .missingAddress: "В конфигурации TUN отсутствуют IP-адреса."
        case .missingIPv4: "Полный VPN не может запуститься без IPv4-маршрута через TUN."
        case .missingIPv6: "Полный VPN не может запуститься без IPv6-маршрута через TUN."
        case .missingDNS: "Не указан DNS для полного VPN-туннеля."
        case .invalidIPv4: "Некорректный IPv4-адрес."
        case .invalidIPv6: "Некорректный IPv6-адрес."
        case .invalidMask: "Некорректная или несмежная IPv4-маска."
        case .unsafeExclusion: "Исключающий маршрут 0/0 отключает полный VPN-туннель."
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

        // A complete macOS VPN cannot silently leave either IP family on the
        // physical adapter. Reject malformed single-stack TUN settings rather
        // than showing Connected while the missing family leaks outside.
        if fullTunnel && ipv4.isEmpty {
            throw TunnelNetworkPlanError.missingIPv4
        }
        if fullTunnel && ipv6.isEmpty {
            throw TunnelNetworkPlanError.missingIPv6
        }
        // A full-family exclusion outranks the default included route on
        // macOS and would silently bypass the VPN for that entire family.
        if fullTunnel && (
            excludedIPv4Routes.contains(where: {
                $0.address == "0.0.0.0" && $0.mask == "0.0.0.0"
            }) ||
            excludedIPv6Routes.contains(where: {
                $0.address == "::" && $0.prefixLength == 0
            })
        ) {
            throw TunnelNetworkPlanError.unsafeExclusion
        }


        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        settings.mtu = NSNumber(value: mtu)

        if !ipv4.isEmpty {
            for item in ipv4 {
                guard Self.isIPv4(item.address) else {
                    throw TunnelNetworkPlanError.invalidIPv4
                }
                guard Self.isIPv4Mask(item.mask) else {
                    throw TunnelNetworkPlanError.invalidMask
                }
            }
            let cfg = NEIPv4Settings(
                addresses: ipv4.map(\.address),
                subnetMasks: ipv4.map(\.mask)
            )
            for route in includedIPv4Routes + excludedIPv4Routes {
                guard Self.isIPv4(route.address) else { throw TunnelNetworkPlanError.invalidIPv4 }
                guard Self.isIPv4Mask(route.mask) else { throw TunnelNetworkPlanError.invalidMask }
            }
            let custom = includedIPv4Routes.map {
                NEIPv4Route(destinationAddress: $0.address, subnetMask: $0.mask)
            }
            // Auto-route must always contain a default route. Explicit
            // included networks are additive; they must not replace 0/0.
            let hasDefault = includedIPv4Routes.contains {
                $0.address == "0.0.0.0" && $0.mask == "0.0.0.0"
            }
            cfg.includedRoutes = fullTunnel && !hasDefault
                ? [NEIPv4Route.default()] + custom
                : custom
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
            let hasDefault = includedIPv6Routes.contains {
                $0.address == "::" && $0.prefixLength == 0
            }
            cfg.includedRoutes = fullTunnel && !hasDefault
                ? [NEIPv6Route.default()] + custom
                : custom
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

    /// An IPv4 netmask must have consecutive one bits followed by zeroes.
    /// inet_pton accepts non-contiguous masks, but NetworkExtension must not.
    private static func isIPv4Mask(_ text: String) -> Bool {
        var address = in_addr()
        guard text.withCString({ inet_pton(AF_INET, $0, &address) }) == 1 else {
            return false
        }
        let inverted = ~UInt32(bigEndian: address.s_addr)
        return (inverted & (inverted &+ 1)) == 0
    }

    private static func isIPv6(_ text: String) -> Bool {
        var address = in6_addr()
        return text.withCString { inet_pton(AF_INET6, $0, &address) } == 1
    }
}
