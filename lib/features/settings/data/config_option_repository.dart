import 'package:dartx/dartx.dart';
import 'package:fpdart/fpdart.dart';
import 'package:hiddify/core/model/optional_range.dart';
import 'package:hiddify/core/model/region.dart';
import 'package:hiddify/core/utils/exception_handler.dart';
import 'package:hiddify/core/utils/json_converters.dart';
import 'package:hiddify/core/utils/preferences_utils.dart';
import 'package:hiddify/features/log/model/log_level.dart';
import 'package:hiddify/features/profile/data/profile_parser.dart';
import 'package:hiddify/features/settings/model/config_option_failure.dart';
import 'package:hiddify/singbox/model/singbox_config_enum.dart';
import 'package:hiddify/singbox/model/singbox_config_option.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

abstract class ConfigOptions {
  static final serviceMode = PreferencesNotifier.create<ServiceMode, String>(
    "service-mode",
    ServiceMode.defaultMode,
    mapFrom: (value) => ServiceMode.choices.firstWhere((e) => e.key == value),
    mapTo: (value) => value.key,
  );

  static final balancerStrategy = PreferencesNotifier.create<BalancerStrategy, String>(
    "balancer-strategy",
    BalancerStrategy.roundRobin,
    mapFrom: (value) => BalancerStrategy.values.firstWhere((e) => e.key == value),
    mapTo: (value) => value.key,
  );

  // Compatibility value for the core protocol and retired per-app filtering.
  // Stored region preferences no longer change routing or DNS.
  static final region = Provider<Region>((ref) => Region.other);
  static final useXrayCoreWhenPossible = PreferencesNotifier.create<bool, bool>("use-xray-core-when-possible", false);
  static final logLevel = PreferencesNotifier.create<LogLevel, String>(
    "log-level",
    LogLevel.warn,
    mapFrom: LogLevel.values.byName,
    mapTo: (value) => value.name,
  );

  static final resolveDestination = PreferencesNotifier.create<bool, bool>("resolve-destination", false);

  static final ipv6Mode = PreferencesNotifier.create<IPv6Mode, String>(
    "ipv6-mode",
    IPv6Mode.disable,
    mapFrom: (value) => IPv6Mode.values.firstWhere((e) => e.key == value),
    mapTo: (value) => value.key,
  );

  static final remoteDnsAddress = PreferencesNotifier.create<String, String>(
    "remote-dns-address",
    "tcp://8.8.8.8",
    possibleValues: List.of([
      "local",
      "tcp://8.8.8.8",
      "tcp://1.1.1.1",
      "https://1.1.1.1/dns-query",
      "https://dns.cloudflare.com/dns-query",
      "tcp://4.4.2.2",
    ]),
    validator: (value) => value.isNotBlank,
  );

  static final remoteDnsDomainStrategy = PreferencesNotifier.create<DomainStrategy, String>(
    "remote-dns-domain-strategy",
    DomainStrategy.auto,
    mapFrom: (value) => DomainStrategy.values.firstWhere((e) => e.key == value),
    mapTo: (value) => value.key,
  );

  static final directDnsAddress = PreferencesNotifier.create<String, String>(
    "direct-dns-address",
    "udp://1.1.1.1",
    possibleValues: List.of([
      "local",
      "udp://223.5.5.5",
      "udp://1.1.1.1",
      "udp://1.1.1.2",
      "tcp://1.1.1.1",
      "https://1.1.1.1/dns-query",
      "https://dns.cloudflare.com/dns-query",
      "4.4.2.2",
      "8.8.8.8",
    ]),
    validator: (value) => value.isNotBlank,
  );

  static final directDnsDomainStrategy = PreferencesNotifier.create<DomainStrategy, String>(
    "direct-dns-domain-strategy",
    DomainStrategy.auto,
    mapFrom: (value) => DomainStrategy.values.firstWhere((e) => e.key == value),
    mapTo: (value) => value.key,
  );

  static final mixedPort = PreferencesNotifier.create<int, int>(
    "mixed-port",
    12334,
    validator: (value) => isPort(value.toString()),
  );
  static final tproxyPort = PreferencesNotifier.create<int, int>(
    "tproxy-port",
    12335,
    validator: (value) => isPort(value.toString()),
  );
  static final redirectPort = PreferencesNotifier.create<int, int>(
    "redirect-port",
    12336,
    validator: (value) => isPort(value.toString()),
  );
  static final directPort = PreferencesNotifier.create<int, int>(
    "direct-port",
    12337,
    validator: (value) => isPort(value.toString()),
  );

  static final enableMixedPort = PreferencesNotifier.create<bool, bool>("enable-mixed-port", true);
  static final enableTproxyPort = PreferencesNotifier.create<bool, bool>("enable-tproxy-port", true);
  static final enableRedirectPort = PreferencesNotifier.create<bool, bool>("enable-redirect-port", true);
  static final enableDirectPort = PreferencesNotifier.create<bool, bool>("enable-direct-port", true);

  static final tunImplementation = PreferencesNotifier.create<TunImplementation, String>(
    "tun-implementation",
    TunImplementation.gvisor,
    mapFrom: TunImplementation.values.byName,
    mapTo: (value) => value.name,
  );

  static final mtu = PreferencesNotifier.create<int, int>(
    "mtu",
    9000,
    validator: (value) => value >= 576 && value <= 65535,
  );

  static final strictRoute = PreferencesNotifier.create<bool, bool>("strict-route", true);

  static final connectionTestUrl = PreferencesNotifier.create<String, String>(
    "connection-test-url",
    "http://captive.apple.com/hotspot-detect.html",
    possibleValues: List.of([
      "http://connectivitycheck.gstatic.com/generate_204",
      "http://www.gstatic.com/generate_204",
      "https://www.gstatic.com/generate_204",
      "https://redirector.googlevideo.com/generate_204",
      "http://cp.cloudflare.com",
      "http://kernel.org",
      "http://detectportal.firefox.com",
      "http://captive.apple.com/hotspot-detect.html",
      "https://1.1.1.1",
      "http://1.1.1.1",
    ]),
    validator: (value) => value.isNotBlank && isUrl(value),
  );

  static final urlTestInterval = PreferencesNotifier.create<Duration, int>(
    "url-test-interval",
    const Duration(minutes: 10),
    mapFrom: const IntervalInSecondsConverter().fromJson,
    mapTo: const IntervalInSecondsConverter().toJson,
  );

  static final enableClashApi = PreferencesNotifier.create<bool, bool>("enable-clash-api", true);

  static final clashApiPort = PreferencesNotifier.create<int, int>(
    "clash-api-port",
    16756,
    validator: (value) => isPort(value.toString()),
  );

  static final allowConnectionFromLan = PreferencesNotifier.create<bool, bool>("allow-connection-from-lan", false);

  static final lanSharingPassword = PreferencesNotifier.create<String, String>("lan_sharing_password", "");

  static final enableFakeDns = PreferencesNotifier.create<bool, bool>("enable-fake-dns", false);

  static final independentDnsCache = PreferencesNotifier.create<bool, bool>("independent-dns-cache", true);

  static final enableTlsFragment = PreferencesNotifier.create<bool, bool>("enable-tls-fragment", false);

  static final fragmentPackets = PreferencesNotifier.create<String, String>(
    "fragment-packets",
    "tlshello",
    possibleValues: ["tlshello", "1-1", "1-2", "1-3", "1-4", "1-5"],
  );

  static final tlsFragmentSize = PreferencesNotifier.create<OptionalRange, String>(
    "tls-fragment-size",
    const OptionalRange(min: 10, max: 30),
    mapFrom: OptionalRange.parse,
    mapTo: const OptionalRangeJsonConverter().toJson,
  );

  static final tlsFragmentSleep = PreferencesNotifier.create<OptionalRange, String>(
    "tls-fragment-sleep",
    const OptionalRange(min: 2, max: 8),
    mapFrom: OptionalRange.parse,
    mapTo: const OptionalRangeJsonConverter().toJson,
  );

  static final enableTlsMixedSniCase = PreferencesNotifier.create<bool, bool>("enable-tls-mixed-sni-case", false);

  static final enableTlsPadding = PreferencesNotifier.create<bool, bool>("enable-tls-padding", false);

  static final tlsPaddingSize = PreferencesNotifier.create<OptionalRange, String>(
    "tls-padding-size",
    const OptionalRange(min: 1, max: 1500),
    mapFrom: OptionalRange.parse,
    mapTo: const OptionalRangeJsonConverter().toJson,
  );

  static final enableMux = PreferencesNotifier.create<bool, bool>("enable-mux", false);

  static final muxPadding = PreferencesNotifier.create<bool, bool>("mux-padding", false);

  static final muxMaxStreams = PreferencesNotifier.create<int, int>(
    "mux-max-streams",
    8,
    validator: (value) => value > 0,
  );

  static final muxProtocol = PreferencesNotifier.create<MuxProtocol, String>(
    "mux-protocol",
    MuxProtocol.h2mux,
    mapFrom: MuxProtocol.values.byName,
    mapTo: (value) => value.name,
  );

  static final chainStatus = PreferencesNotifier.create<ChainStatus, String>(
    "chain-status",
    ChainStatus.off,
    mapFrom: (value) => ChainStatus.values.firstWhere((e) => e.key == value || e.name == value),
    mapTo: (value) => value.name,
  );

  static final extraSecurityPsiphonRegion = PreferencesNotifier.create<PsiphonRegion, String>(
    "extra-security-psiphon-region",
    PsiphonRegion.auto,
    mapFrom: (value) => PsiphonRegion.values.firstWhere((e) => e.key == value || e.name == value),
    mapTo: (value) => value.key,
  );

  static final extraSecurityPsiphonConduitPairingId = PreferencesNotifier.create<String, String>(
    "extra-security-psiphon-conduit-pairing-id",
    "",
  );

  static final unblockerPsiphonRegion = PreferencesNotifier.create<PsiphonRegion, String>(
    "unblocker-psiphon-region",
    PsiphonRegion.auto,
    mapFrom: (value) => PsiphonRegion.values.firstWhere((e) => e.key == value || e.name == value),
    mapTo: (value) => value.key,
  );

  static final unblockerPsiphonConduitPairingId = PreferencesNotifier.create<String, String>(
    "unblocker-psiphon-conduit-pairing-id",
    "",
  );

  static final extraSecurityMode = PreferencesNotifier.create<ChainMode, String>(
    "extra-security-mode",
    ChainMode.warp,
    mapFrom: ChainMode.values.byName,
    mapTo: (value) => value.name,
  );

  static final unblockerMode = PreferencesNotifier.create<ChainMode, String>(
    "unblocker-mode",
    ChainMode.psiphon,
    mapFrom: ChainMode.values.byName,
    mapTo: (value) => value.name,
  );

  static final extraSecurityProfileId = PreferencesNotifier.create<String?, String?>(
    "extra-security-profile-id",
    null,
    mapFrom: (value) => value,
    mapTo: (value) => value,
  );
  static final unblockerProfileId = PreferencesNotifier.create<String?, String?>(
    "unblocker-profile-id",
    null,
    mapFrom: (value) => value,
    mapTo: (value) => value,
  );

  static final extraSecurityWarpLicenseKey = PreferencesNotifier.create<String, String>(
    "extra-security-warp-license-key",
    "",
  );

  static final unblockerWarpLicenseKey = PreferencesNotifier.create<String, String>("unblocker-warp-license-key", "");

  static final unblockerWarpCleanIp = PreferencesNotifier.create<String, String>("unblocker-warp-clean-ip", "auto");

  static final unblockerWarpPort = PreferencesNotifier.create<int, int>(
    "unblocker-warp-port",
    0,
    validator: (value) => value == 0 || isPort(value.toString()),
  );

  static final unblockerWarpNoise = PreferencesNotifier.create<OptionalRange, String>(
    "unblocker-warp-noise",
    const OptionalRange(min: 1, max: 3),
    mapFrom: (value) => OptionalRange.parse(value, allowEmpty: true),
    mapTo: const OptionalRangeJsonConverter().toJson,
  );

  static final unblockerWarpNoiseMode = PreferencesNotifier.create<String, String>("unblocker-warp-noise-mode", "m4");

  static final unblockerWarpNoiseDelay = PreferencesNotifier.create<OptionalRange, String>(
    "unblocker-warp-noise-delay",
    const OptionalRange(min: 10, max: 30),
    mapFrom: (value) => OptionalRange.parse(value, allowEmpty: true),
    mapTo: const OptionalRangeJsonConverter().toJson,
  );
  static final unblockerWarpNoiseSize = PreferencesNotifier.create<OptionalRange, String>(
    "unblocker-warp-noise-size",
    const OptionalRange(min: 10, max: 30),
    mapFrom: (value) => OptionalRange.parse(value, allowEmpty: true),
    mapTo: const OptionalRangeJsonConverter().toJson,
  );

  static final hasExperimentalFeatures = Provider.autoDispose<bool>((ref) {
    return false;
  });

  static final privatePreferencesKeys = {
    "extra-security.warp.license-key",
    "unblocker.warp.license-key",
    "lan-sharing-password",
  };

  static final Map<String, StateNotifierProvider<PreferencesNotifier, dynamic>> preferences = {
    "balancer-strategy": balancerStrategy,
    "use-xray-core-when-possible": useXrayCoreWhenPossible,
    "service-mode": serviceMode,
    "log-level": logLevel,
    "resolve-destination": resolveDestination,
    "ipv6-mode": ipv6Mode,
    "remote-dns-address": remoteDnsAddress,
    "remote-dns-domain-strategy": remoteDnsDomainStrategy,
    "direct-dns-address": directDnsAddress,
    "direct-dns-domain-strategy": directDnsDomainStrategy,
    "mixed-port": mixedPort,
    "tproxy-port": tproxyPort,
    "direct-port": directPort,
    "redirect-port": redirectPort,
    "enable-mixed-port": enableMixedPort,
    "enable-tproxy-port": enableTproxyPort,
    "enable-direct-port": enableDirectPort,
    "enable-redirect-port": enableRedirectPort,
    "tun-implementation": tunImplementation,
    "mtu": mtu,
    "strict-route": strictRoute,
    "connection-test-url": connectionTestUrl,
    "url-test-interval": urlTestInterval,
    "enable-clash-api": enableClashApi,
    "enable-fake-dns": enableFakeDns,
    "independent-dns-cache": independentDnsCache,
    "chain-status": chainStatus,
    "extra-security.mode": extraSecurityMode,
    "unblocker.mode": unblockerMode,
    "clash-api-port": clashApiPort,
    "allow-connection-from-lan": allowConnectionFromLan,
    "lan-sharing-password": lanSharingPassword,
    "tls-tricks.enable-fragment": enableTlsFragment,
    "tls-tricks.fragment-packets": fragmentPackets,
    "tls-tricks.fragment-size": tlsFragmentSize,
    "tls-tricks.fragment-sleep": tlsFragmentSleep,
    "tls-tricks.mixed-sni-case": enableTlsMixedSniCase,
    "tls-tricks.enable-padding": enableTlsPadding,
    "tls-tricks.padding-size": tlsPaddingSize,
    "extra-security.warp.license-key": extraSecurityWarpLicenseKey,
    "extra-security.psiphon.region": extraSecurityPsiphonRegion,
    "extra-security.psiphon.conduit-pairing-id": extraSecurityPsiphonConduitPairingId,
    "extra-security.profile.id": extraSecurityProfileId,
    "unblocker.warp.license-key": unblockerWarpLicenseKey,
    "unblocker.warp.clean-ip": unblockerWarpCleanIp,
    "unblocker.warp.clean-port": unblockerWarpPort,
    "unblocker.warp.noise": unblockerWarpNoise,
    "unblocker.warp.noise-size": unblockerWarpNoiseSize,
    "unblocker.warp.noise-mode": unblockerWarpNoiseMode,
    "unblocker.warp.noise-delay": unblockerWarpNoiseDelay,
    "unblocker.psiphon.region": unblockerPsiphonRegion,
    "unblocker.psiphon.conduit-pairing-id": unblockerPsiphonConduitPairingId,
    "unblocker.profile.id": unblockerProfileId,
  };

  static final singboxConfigOptions = Provider<SingboxConfigOption>((ref) {
    final mode = ref.watch(serviceMode);
    return SingboxConfigOption(
      region: ref.watch(region).name,
      balancerStrategy: ref.watch(balancerStrategy),
      useXrayCoreWhenPossible: ref.watch(useXrayCoreWhenPossible),
      executeConfigAsIs: false,
      logLevel: ref.watch(logLevel),
      resolveDestination: ref.watch(resolveDestination),
      ipv6Mode: ref.watch(ipv6Mode),
      remoteDnsAddress: ref.watch(remoteDnsAddress),
      remoteDnsDomainStrategy: ref.watch(remoteDnsDomainStrategy),
      directDnsAddress: ref.watch(directDnsAddress),
      directDnsDomainStrategy: ref.watch(directDnsDomainStrategy),
      mixedPort: ref.watch(mixedPort),
      tproxyPort: ref.watch(tproxyPort),
      directPort: ref.watch(directPort),
      redirectPort: ref.watch(redirectPort),
      enableMixedPort: ref.watch(enableMixedPort),
      enableTproxyPort: ref.watch(enableTproxyPort),
      enableDirectPort: ref.watch(enableDirectPort),
      enableRedirectPort: ref.watch(enableRedirectPort),
      tunImplementation: ref.watch(tunImplementation),
      mtu: ref.watch(mtu),
      strictRoute: ref.watch(strictRoute),
      connectionTestUrl: ref.watch(connectionTestUrl),
      urlTestInterval: ref.watch(urlTestInterval),
      enableClashApi: ref.watch(enableClashApi),
      clashApiPort: ref.watch(clashApiPort),
      enableTun: mode == ServiceMode.tun,
      setSystemProxy: mode == ServiceMode.systemProxy,
      allowConnectionFromLan: ref.watch(allowConnectionFromLan),
      lanSharingPassword: ref.watch(lanSharingPassword),
      enableFakeDns: ref.watch(enableFakeDns),
      independentDnsCache: ref.watch(independentDnsCache),
      tlsTricks: SingboxTlsTricks(
        enableFragment: ref.watch(enableTlsFragment),
        fragmentSize: ref.watch(tlsFragmentSize),
        fragmentSleep: ref.watch(tlsFragmentSleep),
        mixedSniCase: ref.watch(enableTlsMixedSniCase),
        enablePadding: ref.watch(enableTlsPadding),
        paddingSize: ref.watch(tlsPaddingSize),
      ),
      chainStatus: ref.watch(chainStatus),
      extraSecurity: SingboxExtraSecurityOption(
        mode: ref.watch(extraSecurityMode),
        warp: SingboxExtraSecurityWarpOption(licenseKey: ref.watch(extraSecurityWarpLicenseKey)),
        psiphon: SingboxExtraSecurityPsiphonOption(
          region: ref.watch(extraSecurityPsiphonRegion),
          conduitPairingId: ref.watch(extraSecurityPsiphonConduitPairingId),
        ),
        profile: SingboxExtraSecurityProfileOption(id: ref.watch(extraSecurityProfileId)),
      ),
      unblocker: SingboxUnblockerOption(
        mode: ref.watch(unblockerMode),
        warp: SingboxUnblockerWarpOption(
          licenseKey: ref.watch(unblockerWarpLicenseKey),
          cleanIp: ref.watch(unblockerWarpCleanIp),
          cleanPort: ref.watch(unblockerWarpPort),
          noise: ref.watch(unblockerWarpNoise),
          noiseMode: ref.watch(unblockerWarpNoiseMode),
          noiseSize: ref.watch(unblockerWarpNoiseSize),
          noiseDelay: ref.watch(unblockerWarpNoiseDelay),
        ),
        psiphon: SingboxUnblockerPsiphonOption(
          region: ref.watch(unblockerPsiphonRegion),
          conduitPairingId: ref.watch(unblockerPsiphonConduitPairingId),
        ),
        profile: SingboxUnblockerProfileOption(id: ref.watch(unblockerProfileId)),
      ),
      routeRule: const <String, dynamic>{},
    );
  });
}

class ConfigOptionRepository with ExceptionHandler, InfraLogger {
  ConfigOptionRepository({required this.preferences, required SingboxConfigOption Function() getConfigOptions})
    : _getConfigOptions = getConfigOptions;

  final SharedPreferences preferences;
  final SingboxConfigOption Function() _getConfigOptions;

  Either<ConfigOptionFailure, SingboxConfigOption> fullOptions() =>
      Either.tryCatch(() => _getConfigOptions(), ConfigOptionFailure.unexpected);

  Either<ConfigOptionFailure, SingboxConfigOption> fullOptionsOverrided(String? profileOverride) =>
      Either.tryCatch(() => _getConfigOptions(), ConfigOptionFailure.unexpected).flatMap(
        (options) => Either.tryCatch(() {
          final json = ProfileParser.applyProfileOverride(options.toJson(), profileOverride);
          return SingboxConfigOption.fromJson(json);
        }, ConfigOptionFailure.unexpected),
      );
}
