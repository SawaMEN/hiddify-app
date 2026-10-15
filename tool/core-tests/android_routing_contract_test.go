package config

import (
    "reflect"
    "testing"

    "github.com/sagernet/sing-box/option"
)

// These tests are copied into the released SDK source by the app's CI. They verify
// the routing contract consumed by Kotlin without changing or rebuilding the SDK.
func TestAndroidClientRoutingModeFallback(t *testing.T) {
    for _, mode := range []string{"ru-bypass", "proxy-selected"} {
        t.Run(mode, func(t *testing.T) {
            options := &option.Options{Route: &option.RouteOptions{}, DNS: &option.DNSOptions{}}
            h := DefaultHiddifyOptions()
            h.PrivacyRoutingMode = mode
            h.Region = "other"
            h.PrivacyProxyDomains = []string{"youtube.com", "googlevideo.com"}
            h.PrivacyProxyPackages = []string{"com.google.android.youtube"}
            applyRegionalPrivacy(options, h)
            foundDomain, foundApp, foundFallback := false, false, false
            for _, rule := range options.Route.Rules {
                r := rule.DefaultOptions
                if reflect.DeepEqual([]string(r.DomainSuffix), h.PrivacyProxyDomains) {
                    foundDomain = r.RouteOptions.Outbound == OutboundMainDetour
                }
                if reflect.DeepEqual([]string(r.PackageName), h.PrivacyProxyPackages) {
                    foundApp = r.RouteOptions.Outbound == OutboundMainDetour
                }
                if r.RouteOptions.Outbound == OutboundDirectTag && len(r.DomainSuffix) == 0 && len(r.PackageName) == 0 {
                    foundFallback = true
                }
            }
            if !foundDomain || !foundApp { t.Fatal("selected domains/apps must route to the VPN") }
            if foundFallback != (mode == "proxy-selected") { t.Fatal("incorrect default-direct fallback for mode", mode) }
            if mode == "proxy-selected" {
                last := options.DNS.Rules[len(options.DNS.Rules)-1].DefaultOptions
                if last.RouteOptions.Server != DNSMultiDirectTag { t.Fatal("non-selected DNS must use the direct resolver") }
            }
        })
    }
}

func TestAndroidClientRussianBypassAndDeselectedServices(t *testing.T) {
    options := &option.Options{Route: &option.RouteOptions{}, DNS: &option.DNSOptions{}}
    h := DefaultHiddifyOptions()
    h.PrivacyRoutingMode = "ru-bypass"
    h.Region = "ru"
    h.PrivacyDirectDomains = []string{"bank.example"}
    h.PrivacyDirectPackages = []string{"com.example.bank"}
    h.PrivacyProxyDomains = nil
    h.PrivacyProxyPackages = nil
    applyRegionalPrivacy(options, h)
    foundNetwork, foundApp := false, false
    for _, rule := range options.Route.Rules {
        r := rule.DefaultOptions
        if reflect.DeepEqual([]string(r.DomainSuffix), []string{"ru", "su", "xn--p1ai", "bank.example"}) {
            foundNetwork = r.RouteOptions.Outbound == OutboundDirectTag
        }
        if reflect.DeepEqual([]string(r.PackageName), h.PrivacyDirectPackages) {
            foundApp = r.RouteOptions.Outbound == OutboundDirectTag
        }
        if r.RouteOptions.Outbound == OutboundMainDetour { t.Fatal("deselected services must not add proxy exceptions") }
    }
    if !foundNetwork || !foundApp { t.Fatal("Russian destinations must have direct rules") }
}
