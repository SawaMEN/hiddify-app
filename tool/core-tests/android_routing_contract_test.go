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
			if !foundDomain || !foundApp {
				t.Fatal("selected domains/apps must route to the VPN")
			}
			if foundFallback != (mode == "proxy-selected") {
				t.Fatal("incorrect default-direct fallback for mode", mode)
			}
			if mode == "proxy-selected" {
				last := options.DNS.Rules[len(options.DNS.Rules)-1].DefaultOptions
				if last.RouteOptions.Server != DNSMultiDirectTag {
					t.Fatal("non-selected DNS must use the direct resolver")
				}
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
		if r.RouteOptions.Outbound == OutboundMainDetour {
			t.Fatal("deselected services must not add proxy exceptions")
		}
	}
	if !foundNetwork || !foundApp {
		t.Fatal("Russian destinations must have direct rules")
	}
}

// The app's independent Russian-network switch requires the matching SDK flag.
// Compile this against the exact stable SDK source used by the APK so a build
// cannot silently ship the new UI with a kernel that ignores network priority.
func TestAndroidClientRussianNetworkPriority(t *testing.T) {
	for _, mode := range []string{"off", "ru-bypass", "proxy-selected"} {
		for _, full := range []bool{false, true} {
			for _, community := range []bool{false, true} {
				h := DefaultHiddifyOptions()
				h.Region = "ru"
				h.PrivacyRoutingMode = mode
				h.PrivacyRussianNetworkBypass = true
				h.FullTunnel = full
				h.HandbookRouting = community
				h.PrivacyProxyDomains = []string{"example.ru"}
				h.PrivacyProxyPackages = []string{"com.example.app"}
				options := &option.Options{Route: &option.RouteOptions{}, DNS: &option.DNSOptions{}}
				applyRegionalPrivacy(options, h)
				if len(options.Route.Rules) < 5 || len(options.DNS.Rules) < 2 {
					t.Fatal("stable Android SDK lacks Russian network priority")
				}
				domains := options.Route.Rules[3].DefaultOptions
				geo := options.Route.Rules[4].DefaultOptions
				if domains.RouteOptions.Outbound != OutboundDirectTag ||
					!reflect.DeepEqual([]string(domains.DomainSuffix), []string{"ru", "su", "xn--p1ai"}) ||
					geo.RouteOptions.Outbound != OutboundDirectTag ||
					!reflect.DeepEqual([]string(geo.RuleSet), []string{"geosite-ru", "geoip-ru"}) {
					t.Fatalf("Russian networks must precede VPN rules: mode=%s full=%v community=%v", mode, full, community)
				}
				dns := options.DNS.Rules[1].DefaultOptions
				if dns.RouteOptions.Server != DNSMultiDirectTag ||
					!reflect.DeepEqual([]string(dns.RuleSet), []string{"geosite-ru"}) {
					t.Fatal("Russian DNS must precede VPN DNS")
				}
			}
		}
	}
}
