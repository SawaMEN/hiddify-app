import 'package:hiddify/core/model/directories.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore_service.pbgrpc.dart';
import 'package:hiddify/singbox/model/core_status.dart';

class CoreInterface {
  late CoreClient fgClient;
  late CoreClient bgClient;

  // Native service events supplement the core RPC status. A native Started
  // event only means the control server is ready, not that the tunnel is up.
  Stream<CoreStatus> get serviceEvents => const Stream.empty();

  Future<String> setup(Directories directories, bool debug, int mode) async {
    return "";
  }

  Future<CoreStatus> setupBackground(String path, String name) async {
    return const CoreStarted();
  }

  Future<bool> restart(String path, String name) async {
    return false;
  }

  Future<bool> stop({bool preserveIntent = false}) async {
    return false;
  }

  Future<bool> isBgClientAvailable() async {
    return true;
  }

  bool isSingleChannel() {
    // return true;
    return fgClient == bgClient;
  }

  Future<bool> resetTunnel() async {
    return false;
  }

  Future<bool> isActiveFg() async {
    return true;
  }

  Future<bool> isActiveBg() async {
    return true;
  }

  Future<void> dispose() async {}

  bool isInitialized() {
    try {
      bgClient; // touch it
      return true;
    } catch (_) {
      return false;
    }
  }
}
