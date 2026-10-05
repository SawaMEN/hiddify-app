import 'package:hiddify/core/model/region.dart';
import 'package:hiddify/features/per_app_proxy/model/per_app_proxy_mode.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

enum AutoSelectionResult {
  success,
  failure,
  notFound;

  bool isSuccess() => this == success;
  bool isFailure() => this == failure;
  bool isNotFound() => this == notFound;
}

abstract interface class AutoSelectionRepository {
  Future<(Set<String>?, AutoSelectionResult)> getByAppProxyMode({AppProxyMode? mode, Region? region});
  Future<(Set<String>?, AutoSelectionResult)> getInclude({Region? region});
  Future<(Set<String>?, AutoSelectionResult)> getExclude({Region? region});
}

/// Legacy compatibility shim.
///
/// Application routing defaults are now bundled in Android's region_routing.json and
/// edited from VPN privacy. Network downloads for automatic app lists are intentionally
/// disabled so old/deep-linked UI cannot silently fetch routing policy from the internet.
class AutoSelectionRepositoryImpl implements AutoSelectionRepository {
  AutoSelectionRepositoryImpl({required Ref ref});

  Future<(Set<String>?, AutoSelectionResult)> _disabled() async => (null, AutoSelectionResult.notFound);

  @override
  Future<(Set<String>?, AutoSelectionResult)> getByAppProxyMode({AppProxyMode? mode, Region? region}) => _disabled();

  @override
  Future<(Set<String>?, AutoSelectionResult)> getInclude({Region? region}) => _disabled();

  @override
  Future<(Set<String>?, AutoSelectionResult)> getExclude({Region? region}) => _disabled();
}
