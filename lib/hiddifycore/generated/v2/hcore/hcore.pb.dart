// This is a generated file - do not edit.
//
// Generated from v2/hcore/hcore.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports

import 'dart:core' as $core;

import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:protobuf/protobuf.dart' as $pb;
import 'package:protobuf/well_known_types/google/protobuf/timestamp.pb.dart'
    as $0;

import '../hcommon/common.pbenum.dart' as $1;
import 'hcore.pbenum.dart';

export 'package:protobuf/protobuf.dart' show GeneratedMessageGenericExtensions;

export 'hcore.pbenum.dart';

class CoreInfoResponse extends $pb.GeneratedMessage {
  factory CoreInfoResponse({
    CoreStates? coreState,
    MessageType? messageType,
    $core.String? message,
  }) {
    final result = CoreInfoResponse._();
    if (coreState != null) result.coreState = coreState;
    if (messageType != null) result.messageType = messageType;
    if (message != null) result.message = message;
    return result;
  }

  CoreInfoResponse._();

  factory CoreInfoResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CoreInfoResponse()..mergeFromBuffer(data, registry);
  factory CoreInfoResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CoreInfoResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CoreInfoResponse',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: CoreInfoResponse.$_createMessage)
    ..aE<CoreStates>(1, _omitFieldNames ? '' : 'coreState',
        enumValues: CoreStates.values)
    ..aE<MessageType>(2, _omitFieldNames ? '' : 'messageType',
        enumValues: MessageType.values)
    ..aOS(3, _omitFieldNames ? '' : 'message')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CoreInfoResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CoreInfoResponse copyWith(void Function(CoreInfoResponse) updates) =>
      super.copyWith((message) => updates(message as CoreInfoResponse))
          as CoreInfoResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use CoreInfoResponse() / CoreInfoResponse.new instead')
  static CoreInfoResponse create() => CoreInfoResponse._();
  static $pb.GeneratedMessage $_createMessage() => CoreInfoResponse._();
  @$core.override
  CoreInfoResponse createEmptyInstance() => CoreInfoResponse._();
  @$core.pragma('dart2js:noInline')
  static CoreInfoResponse getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<CoreInfoResponse>(
          CoreInfoResponse.$_createMessage);
  static CoreInfoResponse? _defaultInstance;

  @$pb.TagNumber(1)
  CoreStates get coreState => $_getN(0);
  @$pb.TagNumber(1)
  set coreState(CoreStates value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasCoreState() => $_has(0);
  @$pb.TagNumber(1)
  void clearCoreState() => $_clearField(1);

  @$pb.TagNumber(2)
  MessageType get messageType => $_getN(1);
  @$pb.TagNumber(2)
  set messageType(MessageType value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasMessageType() => $_has(1);
  @$pb.TagNumber(2)
  void clearMessageType() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get message => $_getSZ(2);
  @$pb.TagNumber(3)
  set message($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasMessage() => $_has(2);
  @$pb.TagNumber(3)
  void clearMessage() => $_clearField(3);
}

class StartRequest extends $pb.GeneratedMessage {
  factory StartRequest({
    $core.String? configPath,
    $core.String? configContent,
    $core.bool? disableMemoryLimit,
    $core.bool? delayStart,
    $core.bool? enableOldCommandServer,
    $core.bool? enableRawConfig,
    $core.String? configName,
  }) {
    final result = StartRequest._();
    if (configPath != null) result.configPath = configPath;
    if (configContent != null) result.configContent = configContent;
    if (disableMemoryLimit != null)
      result.disableMemoryLimit = disableMemoryLimit;
    if (delayStart != null) result.delayStart = delayStart;
    if (enableOldCommandServer != null)
      result.enableOldCommandServer = enableOldCommandServer;
    if (enableRawConfig != null) result.enableRawConfig = enableRawConfig;
    if (configName != null) result.configName = configName;
    return result;
  }

  StartRequest._();

  factory StartRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StartRequest()..mergeFromBuffer(data, registry);
  factory StartRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StartRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'StartRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: StartRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'configPath')
    ..aOS(2, _omitFieldNames ? '' : 'configContent')
    ..aOB(3, _omitFieldNames ? '' : 'disableMemoryLimit')
    ..aOB(4, _omitFieldNames ? '' : 'delayStart')
    ..aOB(5, _omitFieldNames ? '' : 'enableOldCommandServer')
    ..aOB(6, _omitFieldNames ? '' : 'enableRawConfig')
    ..aOS(7, _omitFieldNames ? '' : 'configName')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StartRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StartRequest copyWith(void Function(StartRequest) updates) =>
      super.copyWith((message) => updates(message as StartRequest))
          as StartRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use StartRequest() / StartRequest.new instead')
  static StartRequest create() => StartRequest._();
  static $pb.GeneratedMessage $_createMessage() => StartRequest._();
  @$core.override
  StartRequest createEmptyInstance() => StartRequest._();
  @$core.pragma('dart2js:noInline')
  static StartRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<StartRequest>(
          StartRequest.$_createMessage);
  static StartRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get configPath => $_getSZ(0);
  @$pb.TagNumber(1)
  set configPath($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasConfigPath() => $_has(0);
  @$pb.TagNumber(1)
  void clearConfigPath() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get configContent => $_getSZ(1);
  @$pb.TagNumber(2)
  set configContent($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasConfigContent() => $_has(1);
  @$pb.TagNumber(2)
  void clearConfigContent() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.bool get disableMemoryLimit => $_getBF(2);
  @$pb.TagNumber(3)
  set disableMemoryLimit($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasDisableMemoryLimit() => $_has(2);
  @$pb.TagNumber(3)
  void clearDisableMemoryLimit() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.bool get delayStart => $_getBF(3);
  @$pb.TagNumber(4)
  set delayStart($core.bool value) => $_setBool(3, value);
  @$pb.TagNumber(4)
  $core.bool hasDelayStart() => $_has(3);
  @$pb.TagNumber(4)
  void clearDelayStart() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.bool get enableOldCommandServer => $_getBF(4);
  @$pb.TagNumber(5)
  set enableOldCommandServer($core.bool value) => $_setBool(4, value);
  @$pb.TagNumber(5)
  $core.bool hasEnableOldCommandServer() => $_has(4);
  @$pb.TagNumber(5)
  void clearEnableOldCommandServer() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.bool get enableRawConfig => $_getBF(5);
  @$pb.TagNumber(6)
  set enableRawConfig($core.bool value) => $_setBool(5, value);
  @$pb.TagNumber(6)
  $core.bool hasEnableRawConfig() => $_has(5);
  @$pb.TagNumber(6)
  void clearEnableRawConfig() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.String get configName => $_getSZ(6);
  @$pb.TagNumber(7)
  set configName($core.String value) => $_setString(6, value);
  @$pb.TagNumber(7)
  $core.bool hasConfigName() => $_has(6);
  @$pb.TagNumber(7)
  void clearConfigName() => $_clearField(7);
}

class CloseRequest extends $pb.GeneratedMessage {
  factory CloseRequest({
    SetupMode? mode,
  }) {
    final result = CloseRequest._();
    if (mode != null) result.mode = mode;
    return result;
  }

  CloseRequest._();

  factory CloseRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CloseRequest()..mergeFromBuffer(data, registry);
  factory CloseRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CloseRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CloseRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: CloseRequest.$_createMessage)
    ..aE<SetupMode>(1, _omitFieldNames ? '' : 'mode',
        enumValues: SetupMode.values)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CloseRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CloseRequest copyWith(void Function(CloseRequest) updates) =>
      super.copyWith((message) => updates(message as CloseRequest))
          as CloseRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use CloseRequest() / CloseRequest.new instead')
  static CloseRequest create() => CloseRequest._();
  static $pb.GeneratedMessage $_createMessage() => CloseRequest._();
  @$core.override
  CloseRequest createEmptyInstance() => CloseRequest._();
  @$core.pragma('dart2js:noInline')
  static CloseRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<CloseRequest>(
          CloseRequest.$_createMessage);
  static CloseRequest? _defaultInstance;

  @$pb.TagNumber(1)
  SetupMode get mode => $_getN(0);
  @$pb.TagNumber(1)
  set mode(SetupMode value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasMode() => $_has(0);
  @$pb.TagNumber(1)
  void clearMode() => $_clearField(1);
}

/// Define the message equivalent of SetupParameters
class SetupRequest extends $pb.GeneratedMessage {
  factory SetupRequest({
    $core.String? basePath,
    $core.String? workingDir,
    $core.String? tempDir,
    $fixnum.Int64? flutterStatusPort,
    $core.String? listen,
    $core.String? secret,
    $core.bool? debug,
    SetupMode? mode,
    $core.bool? fixAndroidStack,
  }) {
    final result = SetupRequest._();
    if (basePath != null) result.basePath = basePath;
    if (workingDir != null) result.workingDir = workingDir;
    if (tempDir != null) result.tempDir = tempDir;
    if (flutterStatusPort != null) result.flutterStatusPort = flutterStatusPort;
    if (listen != null) result.listen = listen;
    if (secret != null) result.secret = secret;
    if (debug != null) result.debug = debug;
    if (mode != null) result.mode = mode;
    if (fixAndroidStack != null) result.fixAndroidStack = fixAndroidStack;
    return result;
  }

  SetupRequest._();

  factory SetupRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetupRequest()..mergeFromBuffer(data, registry);
  factory SetupRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetupRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SetupRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: SetupRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'basePath')
    ..aOS(2, _omitFieldNames ? '' : 'workingDir')
    ..aOS(3, _omitFieldNames ? '' : 'tempDir')
    ..aInt64(4, _omitFieldNames ? '' : 'flutterStatusPort')
    ..aOS(5, _omitFieldNames ? '' : 'listen')
    ..aOS(6, _omitFieldNames ? '' : 'secret')
    ..aOB(7, _omitFieldNames ? '' : 'debug')
    ..aE<SetupMode>(8, _omitFieldNames ? '' : 'mode',
        enumValues: SetupMode.values)
    ..aOB(9, _omitFieldNames ? '' : 'fixAndroidStack')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetupRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetupRequest copyWith(void Function(SetupRequest) updates) =>
      super.copyWith((message) => updates(message as SetupRequest))
          as SetupRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use SetupRequest() / SetupRequest.new instead')
  static SetupRequest create() => SetupRequest._();
  static $pb.GeneratedMessage $_createMessage() => SetupRequest._();
  @$core.override
  SetupRequest createEmptyInstance() => SetupRequest._();
  @$core.pragma('dart2js:noInline')
  static SetupRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<SetupRequest>(
          SetupRequest.$_createMessage);
  static SetupRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get basePath => $_getSZ(0);
  @$pb.TagNumber(1)
  set basePath($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasBasePath() => $_has(0);
  @$pb.TagNumber(1)
  void clearBasePath() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get workingDir => $_getSZ(1);
  @$pb.TagNumber(2)
  set workingDir($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasWorkingDir() => $_has(1);
  @$pb.TagNumber(2)
  void clearWorkingDir() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get tempDir => $_getSZ(2);
  @$pb.TagNumber(3)
  set tempDir($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasTempDir() => $_has(2);
  @$pb.TagNumber(3)
  void clearTempDir() => $_clearField(3);

  @$pb.TagNumber(4)
  $fixnum.Int64 get flutterStatusPort => $_getI64(3);
  @$pb.TagNumber(4)
  set flutterStatusPort($fixnum.Int64 value) => $_setInt64(3, value);
  @$pb.TagNumber(4)
  $core.bool hasFlutterStatusPort() => $_has(3);
  @$pb.TagNumber(4)
  void clearFlutterStatusPort() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get listen => $_getSZ(4);
  @$pb.TagNumber(5)
  set listen($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasListen() => $_has(4);
  @$pb.TagNumber(5)
  void clearListen() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get secret => $_getSZ(5);
  @$pb.TagNumber(6)
  set secret($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasSecret() => $_has(5);
  @$pb.TagNumber(6)
  void clearSecret() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.bool get debug => $_getBF(6);
  @$pb.TagNumber(7)
  set debug($core.bool value) => $_setBool(6, value);
  @$pb.TagNumber(7)
  $core.bool hasDebug() => $_has(6);
  @$pb.TagNumber(7)
  void clearDebug() => $_clearField(7);

  @$pb.TagNumber(8)
  SetupMode get mode => $_getN(7);
  @$pb.TagNumber(8)
  set mode(SetupMode value) => $_setField(8, value);
  @$pb.TagNumber(8)
  $core.bool hasMode() => $_has(7);
  @$pb.TagNumber(8)
  void clearMode() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.bool get fixAndroidStack => $_getBF(8);
  @$pb.TagNumber(9)
  set fixAndroidStack($core.bool value) => $_setBool(8, value);
  @$pb.TagNumber(9)
  $core.bool hasFixAndroidStack() => $_has(8);
  @$pb.TagNumber(9)
  void clearFixAndroidStack() => $_clearField(9);
}

class SystemInfo extends $pb.GeneratedMessage {
  factory SystemInfo({
    $fixnum.Int64? memory,
    $core.int? goroutines,
    $core.int? connectionsIn,
    $core.int? connectionsOut,
    $core.bool? trafficAvailable,
    $fixnum.Int64? uplink,
    $fixnum.Int64? downlink,
    $fixnum.Int64? uplinkTotal,
    $fixnum.Int64? downlinkTotal,
    $core.String? currentOutbound,
    $core.String? currentProfile,
  }) {
    final result = SystemInfo._();
    if (memory != null) result.memory = memory;
    if (goroutines != null) result.goroutines = goroutines;
    if (connectionsIn != null) result.connectionsIn = connectionsIn;
    if (connectionsOut != null) result.connectionsOut = connectionsOut;
    if (trafficAvailable != null) result.trafficAvailable = trafficAvailable;
    if (uplink != null) result.uplink = uplink;
    if (downlink != null) result.downlink = downlink;
    if (uplinkTotal != null) result.uplinkTotal = uplinkTotal;
    if (downlinkTotal != null) result.downlinkTotal = downlinkTotal;
    if (currentOutbound != null) result.currentOutbound = currentOutbound;
    if (currentProfile != null) result.currentProfile = currentProfile;
    return result;
  }

  SystemInfo._();

  factory SystemInfo.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SystemInfo()..mergeFromBuffer(data, registry);
  factory SystemInfo.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SystemInfo()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SystemInfo',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: SystemInfo.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'memory')
    ..aI(2, _omitFieldNames ? '' : 'goroutines')
    ..aI(3, _omitFieldNames ? '' : 'connectionsIn')
    ..aI(4, _omitFieldNames ? '' : 'connectionsOut')
    ..aOB(5, _omitFieldNames ? '' : 'trafficAvailable')
    ..aInt64(6, _omitFieldNames ? '' : 'uplink')
    ..aInt64(7, _omitFieldNames ? '' : 'downlink')
    ..aInt64(8, _omitFieldNames ? '' : 'uplinkTotal')
    ..aInt64(9, _omitFieldNames ? '' : 'downlinkTotal')
    ..aOS(10, _omitFieldNames ? '' : 'currentOutbound')
    ..aOS(11, _omitFieldNames ? '' : 'currentProfile')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SystemInfo clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SystemInfo copyWith(void Function(SystemInfo) updates) =>
      super.copyWith((message) => updates(message as SystemInfo)) as SystemInfo;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use SystemInfo() / SystemInfo.new instead')
  static SystemInfo create() => SystemInfo._();
  static $pb.GeneratedMessage $_createMessage() => SystemInfo._();
  @$core.override
  SystemInfo createEmptyInstance() => SystemInfo._();
  @$core.pragma('dart2js:noInline')
  static SystemInfo getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SystemInfo>(SystemInfo.$_createMessage);
  static SystemInfo? _defaultInstance;

  @$pb.TagNumber(1)
  $fixnum.Int64 get memory => $_getI64(0);
  @$pb.TagNumber(1)
  set memory($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasMemory() => $_has(0);
  @$pb.TagNumber(1)
  void clearMemory() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.int get goroutines => $_getIZ(1);
  @$pb.TagNumber(2)
  set goroutines($core.int value) => $_setSignedInt32(1, value);
  @$pb.TagNumber(2)
  $core.bool hasGoroutines() => $_has(1);
  @$pb.TagNumber(2)
  void clearGoroutines() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.int get connectionsIn => $_getIZ(2);
  @$pb.TagNumber(3)
  set connectionsIn($core.int value) => $_setSignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasConnectionsIn() => $_has(2);
  @$pb.TagNumber(3)
  void clearConnectionsIn() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get connectionsOut => $_getIZ(3);
  @$pb.TagNumber(4)
  set connectionsOut($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasConnectionsOut() => $_has(3);
  @$pb.TagNumber(4)
  void clearConnectionsOut() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.bool get trafficAvailable => $_getBF(4);
  @$pb.TagNumber(5)
  set trafficAvailable($core.bool value) => $_setBool(4, value);
  @$pb.TagNumber(5)
  $core.bool hasTrafficAvailable() => $_has(4);
  @$pb.TagNumber(5)
  void clearTrafficAvailable() => $_clearField(5);

  @$pb.TagNumber(6)
  $fixnum.Int64 get uplink => $_getI64(5);
  @$pb.TagNumber(6)
  set uplink($fixnum.Int64 value) => $_setInt64(5, value);
  @$pb.TagNumber(6)
  $core.bool hasUplink() => $_has(5);
  @$pb.TagNumber(6)
  void clearUplink() => $_clearField(6);

  @$pb.TagNumber(7)
  $fixnum.Int64 get downlink => $_getI64(6);
  @$pb.TagNumber(7)
  set downlink($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(7)
  $core.bool hasDownlink() => $_has(6);
  @$pb.TagNumber(7)
  void clearDownlink() => $_clearField(7);

  @$pb.TagNumber(8)
  $fixnum.Int64 get uplinkTotal => $_getI64(7);
  @$pb.TagNumber(8)
  set uplinkTotal($fixnum.Int64 value) => $_setInt64(7, value);
  @$pb.TagNumber(8)
  $core.bool hasUplinkTotal() => $_has(7);
  @$pb.TagNumber(8)
  void clearUplinkTotal() => $_clearField(8);

  @$pb.TagNumber(9)
  $fixnum.Int64 get downlinkTotal => $_getI64(8);
  @$pb.TagNumber(9)
  set downlinkTotal($fixnum.Int64 value) => $_setInt64(8, value);
  @$pb.TagNumber(9)
  $core.bool hasDownlinkTotal() => $_has(8);
  @$pb.TagNumber(9)
  void clearDownlinkTotal() => $_clearField(9);

  @$pb.TagNumber(10)
  $core.String get currentOutbound => $_getSZ(9);
  @$pb.TagNumber(10)
  set currentOutbound($core.String value) => $_setString(9, value);
  @$pb.TagNumber(10)
  $core.bool hasCurrentOutbound() => $_has(9);
  @$pb.TagNumber(10)
  void clearCurrentOutbound() => $_clearField(10);

  @$pb.TagNumber(11)
  $core.String get currentProfile => $_getSZ(10);
  @$pb.TagNumber(11)
  set currentProfile($core.String value) => $_setString(10, value);
  @$pb.TagNumber(11)
  $core.bool hasCurrentProfile() => $_has(10);
  @$pb.TagNumber(11)
  void clearCurrentProfile() => $_clearField(11);
}

class OutboundInfo extends $pb.GeneratedMessage {
  factory OutboundInfo({
    $core.String? tag,
    $core.String? type,
    $0.Timestamp? urlTestTime,
    $core.int? urlTestDelay,
    IpInfo? ipinfo,
    $core.bool? isSelected,
    $core.bool? isGroup,
    $core.bool? isSecure,
    $core.bool? isVisible,
    $core.int? port,
    $core.String? host,
    $core.String? tagDisplay,
    $core.String? groupSelectedTag,
    $core.String? groupSelectedTagDisplay,
    $fixnum.Int64? upload,
    $fixnum.Int64? download,
    $core.String? detour,
  }) {
    final result = OutboundInfo._();
    if (tag != null) result.tag = tag;
    if (type != null) result.type = type;
    if (urlTestTime != null) result.urlTestTime = urlTestTime;
    if (urlTestDelay != null) result.urlTestDelay = urlTestDelay;
    if (ipinfo != null) result.ipinfo = ipinfo;
    if (isSelected != null) result.isSelected = isSelected;
    if (isGroup != null) result.isGroup = isGroup;
    if (isSecure != null) result.isSecure = isSecure;
    if (isVisible != null) result.isVisible = isVisible;
    if (port != null) result.port = port;
    if (host != null) result.host = host;
    if (tagDisplay != null) result.tagDisplay = tagDisplay;
    if (groupSelectedTag != null) result.groupSelectedTag = groupSelectedTag;
    if (groupSelectedTagDisplay != null)
      result.groupSelectedTagDisplay = groupSelectedTagDisplay;
    if (upload != null) result.upload = upload;
    if (download != null) result.download = download;
    if (detour != null) result.detour = detour;
    return result;
  }

  OutboundInfo._();

  factory OutboundInfo.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OutboundInfo()..mergeFromBuffer(data, registry);
  factory OutboundInfo.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OutboundInfo()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OutboundInfo',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: OutboundInfo.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'tag')
    ..aOS(2, _omitFieldNames ? '' : 'type')
    ..aOM<$0.Timestamp>(3, _omitFieldNames ? '' : 'urlTestTime',
        subBuilder: $0.Timestamp.$_createMessage)
    ..aI(4, _omitFieldNames ? '' : 'urlTestDelay')
    ..aOM<IpInfo>(5, _omitFieldNames ? '' : 'ipinfo',
        subBuilder: IpInfo.$_createMessage)
    ..aOB(6, _omitFieldNames ? '' : 'isSelected')
    ..aOB(7, _omitFieldNames ? '' : 'isGroup')
    ..aOB(8, _omitFieldNames ? '' : 'isSecure')
    ..aOB(9, _omitFieldNames ? '' : 'isVisible')
    ..aI(10, _omitFieldNames ? '' : 'port', fieldType: $pb.PbFieldType.OU3)
    ..aOS(11, _omitFieldNames ? '' : 'host')
    ..aOS(12, _omitFieldNames ? '' : 'tagDisplay')
    ..aOS(13, _omitFieldNames ? '' : 'groupSelectedTag')
    ..aOS(14, _omitFieldNames ? '' : 'groupSelectedTagDisplay')
    ..aInt64(15, _omitFieldNames ? '' : 'upload')
    ..aInt64(16, _omitFieldNames ? '' : 'download')
    ..aOS(17, _omitFieldNames ? '' : 'detour')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OutboundInfo clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OutboundInfo copyWith(void Function(OutboundInfo) updates) =>
      super.copyWith((message) => updates(message as OutboundInfo))
          as OutboundInfo;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use OutboundInfo() / OutboundInfo.new instead')
  static OutboundInfo create() => OutboundInfo._();
  static $pb.GeneratedMessage $_createMessage() => OutboundInfo._();
  @$core.override
  OutboundInfo createEmptyInstance() => OutboundInfo._();
  @$core.pragma('dart2js:noInline')
  static OutboundInfo getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<OutboundInfo>(
          OutboundInfo.$_createMessage);
  static OutboundInfo? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get tag => $_getSZ(0);
  @$pb.TagNumber(1)
  set tag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get type => $_getSZ(1);
  @$pb.TagNumber(2)
  set type($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasType() => $_has(1);
  @$pb.TagNumber(2)
  void clearType() => $_clearField(2);

  @$pb.TagNumber(3)
  $0.Timestamp get urlTestTime => $_getN(2);
  @$pb.TagNumber(3)
  set urlTestTime($0.Timestamp value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasUrlTestTime() => $_has(2);
  @$pb.TagNumber(3)
  void clearUrlTestTime() => $_clearField(3);
  @$pb.TagNumber(3)
  $0.Timestamp ensureUrlTestTime() => $_ensure(2);

  @$pb.TagNumber(4)
  $core.int get urlTestDelay => $_getIZ(3);
  @$pb.TagNumber(4)
  set urlTestDelay($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasUrlTestDelay() => $_has(3);
  @$pb.TagNumber(4)
  void clearUrlTestDelay() => $_clearField(4);

  @$pb.TagNumber(5)
  IpInfo get ipinfo => $_getN(4);
  @$pb.TagNumber(5)
  set ipinfo(IpInfo value) => $_setField(5, value);
  @$pb.TagNumber(5)
  $core.bool hasIpinfo() => $_has(4);
  @$pb.TagNumber(5)
  void clearIpinfo() => $_clearField(5);
  @$pb.TagNumber(5)
  IpInfo ensureIpinfo() => $_ensure(4);

  @$pb.TagNumber(6)
  $core.bool get isSelected => $_getBF(5);
  @$pb.TagNumber(6)
  set isSelected($core.bool value) => $_setBool(5, value);
  @$pb.TagNumber(6)
  $core.bool hasIsSelected() => $_has(5);
  @$pb.TagNumber(6)
  void clearIsSelected() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.bool get isGroup => $_getBF(6);
  @$pb.TagNumber(7)
  set isGroup($core.bool value) => $_setBool(6, value);
  @$pb.TagNumber(7)
  $core.bool hasIsGroup() => $_has(6);
  @$pb.TagNumber(7)
  void clearIsGroup() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.bool get isSecure => $_getBF(7);
  @$pb.TagNumber(8)
  set isSecure($core.bool value) => $_setBool(7, value);
  @$pb.TagNumber(8)
  $core.bool hasIsSecure() => $_has(7);
  @$pb.TagNumber(8)
  void clearIsSecure() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.bool get isVisible => $_getBF(8);
  @$pb.TagNumber(9)
  set isVisible($core.bool value) => $_setBool(8, value);
  @$pb.TagNumber(9)
  $core.bool hasIsVisible() => $_has(8);
  @$pb.TagNumber(9)
  void clearIsVisible() => $_clearField(9);

  @$pb.TagNumber(10)
  $core.int get port => $_getIZ(9);
  @$pb.TagNumber(10)
  set port($core.int value) => $_setUnsignedInt32(9, value);
  @$pb.TagNumber(10)
  $core.bool hasPort() => $_has(9);
  @$pb.TagNumber(10)
  void clearPort() => $_clearField(10);

  @$pb.TagNumber(11)
  $core.String get host => $_getSZ(10);
  @$pb.TagNumber(11)
  set host($core.String value) => $_setString(10, value);
  @$pb.TagNumber(11)
  $core.bool hasHost() => $_has(10);
  @$pb.TagNumber(11)
  void clearHost() => $_clearField(11);

  @$pb.TagNumber(12)
  $core.String get tagDisplay => $_getSZ(11);
  @$pb.TagNumber(12)
  set tagDisplay($core.String value) => $_setString(11, value);
  @$pb.TagNumber(12)
  $core.bool hasTagDisplay() => $_has(11);
  @$pb.TagNumber(12)
  void clearTagDisplay() => $_clearField(12);

  @$pb.TagNumber(13)
  $core.String get groupSelectedTag => $_getSZ(12);
  @$pb.TagNumber(13)
  set groupSelectedTag($core.String value) => $_setString(12, value);
  @$pb.TagNumber(13)
  $core.bool hasGroupSelectedTag() => $_has(12);
  @$pb.TagNumber(13)
  void clearGroupSelectedTag() => $_clearField(13);

  @$pb.TagNumber(14)
  $core.String get groupSelectedTagDisplay => $_getSZ(13);
  @$pb.TagNumber(14)
  set groupSelectedTagDisplay($core.String value) => $_setString(13, value);
  @$pb.TagNumber(14)
  $core.bool hasGroupSelectedTagDisplay() => $_has(13);
  @$pb.TagNumber(14)
  void clearGroupSelectedTagDisplay() => $_clearField(14);

  @$pb.TagNumber(15)
  $fixnum.Int64 get upload => $_getI64(14);
  @$pb.TagNumber(15)
  set upload($fixnum.Int64 value) => $_setInt64(14, value);
  @$pb.TagNumber(15)
  $core.bool hasUpload() => $_has(14);
  @$pb.TagNumber(15)
  void clearUpload() => $_clearField(15);

  @$pb.TagNumber(16)
  $fixnum.Int64 get download => $_getI64(15);
  @$pb.TagNumber(16)
  set download($fixnum.Int64 value) => $_setInt64(15, value);
  @$pb.TagNumber(16)
  $core.bool hasDownload() => $_has(15);
  @$pb.TagNumber(16)
  void clearDownload() => $_clearField(16);

  @$pb.TagNumber(17)
  $core.String get detour => $_getSZ(16);
  @$pb.TagNumber(17)
  set detour($core.String value) => $_setString(16, value);
  @$pb.TagNumber(17)
  $core.bool hasDetour() => $_has(16);
  @$pb.TagNumber(17)
  void clearDetour() => $_clearField(17);
}

class IpInfo extends $pb.GeneratedMessage {
  factory IpInfo({
    $core.String? ip,
    $core.String? countryCode,
    $core.String? region,
    $core.String? city,
    $core.int? asn,
    $core.String? org,
    $core.double? latitude,
    $core.double? longitude,
    $core.String? postalCode,
  }) {
    final result = IpInfo._();
    if (ip != null) result.ip = ip;
    if (countryCode != null) result.countryCode = countryCode;
    if (region != null) result.region = region;
    if (city != null) result.city = city;
    if (asn != null) result.asn = asn;
    if (org != null) result.org = org;
    if (latitude != null) result.latitude = latitude;
    if (longitude != null) result.longitude = longitude;
    if (postalCode != null) result.postalCode = postalCode;
    return result;
  }

  IpInfo._();

  factory IpInfo.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      IpInfo()..mergeFromBuffer(data, registry);
  factory IpInfo.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      IpInfo()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'IpInfo',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: IpInfo.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'ip')
    ..aOS(2, _omitFieldNames ? '' : 'country_code')
    ..aOS(3, _omitFieldNames ? '' : 'region')
    ..aOS(4, _omitFieldNames ? '' : 'city')
    ..aI(5, _omitFieldNames ? '' : 'asn')
    ..aOS(6, _omitFieldNames ? '' : 'org')
    ..aD(7, _omitFieldNames ? '' : 'latitude')
    ..aD(8, _omitFieldNames ? '' : 'longitude')
    ..aOS(9, _omitFieldNames ? '' : 'postal_code')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  IpInfo clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  IpInfo copyWith(void Function(IpInfo) updates) =>
      super.copyWith((message) => updates(message as IpInfo)) as IpInfo;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use IpInfo() / IpInfo.new instead')
  static IpInfo create() => IpInfo._();
  static $pb.GeneratedMessage $_createMessage() => IpInfo._();
  @$core.override
  IpInfo createEmptyInstance() => IpInfo._();
  @$core.pragma('dart2js:noInline')
  static IpInfo getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<IpInfo>(IpInfo.$_createMessage);
  static IpInfo? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get ip => $_getSZ(0);
  @$pb.TagNumber(1)
  set ip($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasIp() => $_has(0);
  @$pb.TagNumber(1)
  void clearIp() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get countryCode => $_getSZ(1);
  @$pb.TagNumber(2)
  set countryCode($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasCountryCode() => $_has(1);
  @$pb.TagNumber(2)
  void clearCountryCode() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get region => $_getSZ(2);
  @$pb.TagNumber(3)
  set region($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasRegion() => $_has(2);
  @$pb.TagNumber(3)
  void clearRegion() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get city => $_getSZ(3);
  @$pb.TagNumber(4)
  set city($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasCity() => $_has(3);
  @$pb.TagNumber(4)
  void clearCity() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.int get asn => $_getIZ(4);
  @$pb.TagNumber(5)
  set asn($core.int value) => $_setSignedInt32(4, value);
  @$pb.TagNumber(5)
  $core.bool hasAsn() => $_has(4);
  @$pb.TagNumber(5)
  void clearAsn() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get org => $_getSZ(5);
  @$pb.TagNumber(6)
  set org($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasOrg() => $_has(5);
  @$pb.TagNumber(6)
  void clearOrg() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.double get latitude => $_getN(6);
  @$pb.TagNumber(7)
  set latitude($core.double value) => $_setDouble(6, value);
  @$pb.TagNumber(7)
  $core.bool hasLatitude() => $_has(6);
  @$pb.TagNumber(7)
  void clearLatitude() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.double get longitude => $_getN(7);
  @$pb.TagNumber(8)
  set longitude($core.double value) => $_setDouble(7, value);
  @$pb.TagNumber(8)
  $core.bool hasLongitude() => $_has(7);
  @$pb.TagNumber(8)
  void clearLongitude() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.String get postalCode => $_getSZ(8);
  @$pb.TagNumber(9)
  set postalCode($core.String value) => $_setString(8, value);
  @$pb.TagNumber(9)
  $core.bool hasPostalCode() => $_has(8);
  @$pb.TagNumber(9)
  void clearPostalCode() => $_clearField(9);
}

class OutboundGroup extends $pb.GeneratedMessage {
  factory OutboundGroup({
    $core.String? tag,
    $core.String? type,
    $core.String? selected,
    $core.bool? selectable,
    $core.bool? isExpand,
    $core.Iterable<OutboundInfo>? items,
  }) {
    final result = OutboundGroup._();
    if (tag != null) result.tag = tag;
    if (type != null) result.type = type;
    if (selected != null) result.selected = selected;
    if (selectable != null) result.selectable = selectable;
    if (isExpand != null) result.isExpand = isExpand;
    if (items != null) result.items.addAll(items);
    return result;
  }

  OutboundGroup._();

  factory OutboundGroup.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OutboundGroup()..mergeFromBuffer(data, registry);
  factory OutboundGroup.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OutboundGroup()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OutboundGroup',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: OutboundGroup.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'tag')
    ..aOS(2, _omitFieldNames ? '' : 'type')
    ..aOS(3, _omitFieldNames ? '' : 'selected')
    ..aOB(4, _omitFieldNames ? '' : 'selectable')
    ..aOB(5, _omitFieldNames ? '' : 'IsExpand', protoName: 'Is_expand')
    ..pPM<OutboundInfo>(6, _omitFieldNames ? '' : 'items',
        subBuilder: OutboundInfo.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OutboundGroup clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OutboundGroup copyWith(void Function(OutboundGroup) updates) =>
      super.copyWith((message) => updates(message as OutboundGroup))
          as OutboundGroup;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use OutboundGroup() / OutboundGroup.new instead')
  static OutboundGroup create() => OutboundGroup._();
  static $pb.GeneratedMessage $_createMessage() => OutboundGroup._();
  @$core.override
  OutboundGroup createEmptyInstance() => OutboundGroup._();
  @$core.pragma('dart2js:noInline')
  static OutboundGroup getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<OutboundGroup>(
          OutboundGroup.$_createMessage);
  static OutboundGroup? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get tag => $_getSZ(0);
  @$pb.TagNumber(1)
  set tag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get type => $_getSZ(1);
  @$pb.TagNumber(2)
  set type($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasType() => $_has(1);
  @$pb.TagNumber(2)
  void clearType() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get selected => $_getSZ(2);
  @$pb.TagNumber(3)
  set selected($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasSelected() => $_has(2);
  @$pb.TagNumber(3)
  void clearSelected() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.bool get selectable => $_getBF(3);
  @$pb.TagNumber(4)
  set selectable($core.bool value) => $_setBool(3, value);
  @$pb.TagNumber(4)
  $core.bool hasSelectable() => $_has(3);
  @$pb.TagNumber(4)
  void clearSelectable() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.bool get isExpand => $_getBF(4);
  @$pb.TagNumber(5)
  set isExpand($core.bool value) => $_setBool(4, value);
  @$pb.TagNumber(5)
  $core.bool hasIsExpand() => $_has(4);
  @$pb.TagNumber(5)
  void clearIsExpand() => $_clearField(5);

  @$pb.TagNumber(6)
  $pb.PbList<OutboundInfo> get items => $_getList(5);
}

class OutboundGroupList extends $pb.GeneratedMessage {
  factory OutboundGroupList({
    $core.Iterable<OutboundGroup>? items,
  }) {
    final result = OutboundGroupList._();
    if (items != null) result.items.addAll(items);
    return result;
  }

  OutboundGroupList._();

  factory OutboundGroupList.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OutboundGroupList()..mergeFromBuffer(data, registry);
  factory OutboundGroupList.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OutboundGroupList()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OutboundGroupList',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: OutboundGroupList.$_createMessage)
    ..pPM<OutboundGroup>(1, _omitFieldNames ? '' : 'items',
        subBuilder: OutboundGroup.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OutboundGroupList clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OutboundGroupList copyWith(void Function(OutboundGroupList) updates) =>
      super.copyWith((message) => updates(message as OutboundGroupList))
          as OutboundGroupList;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use OutboundGroupList() / OutboundGroupList.new instead')
  static OutboundGroupList create() => OutboundGroupList._();
  static $pb.GeneratedMessage $_createMessage() => OutboundGroupList._();
  @$core.override
  OutboundGroupList createEmptyInstance() => OutboundGroupList._();
  @$core.pragma('dart2js:noInline')
  static OutboundGroupList getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<OutboundGroupList>(
          OutboundGroupList.$_createMessage);
  static OutboundGroupList? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<OutboundGroup> get items => $_getList(0);
}

class WarpAccount extends $pb.GeneratedMessage {
  factory WarpAccount({
    $core.String? accountId,
    $core.String? accessToken,
  }) {
    final result = WarpAccount._();
    if (accountId != null) result.accountId = accountId;
    if (accessToken != null) result.accessToken = accessToken;
    return result;
  }

  WarpAccount._();

  factory WarpAccount.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      WarpAccount()..mergeFromBuffer(data, registry);
  factory WarpAccount.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      WarpAccount()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'WarpAccount',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: WarpAccount.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'accountId')
    ..aOS(2, _omitFieldNames ? '' : 'accessToken')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  WarpAccount clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  WarpAccount copyWith(void Function(WarpAccount) updates) =>
      super.copyWith((message) => updates(message as WarpAccount))
          as WarpAccount;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use WarpAccount() / WarpAccount.new instead')
  static WarpAccount create() => WarpAccount._();
  static $pb.GeneratedMessage $_createMessage() => WarpAccount._();
  @$core.override
  WarpAccount createEmptyInstance() => WarpAccount._();
  @$core.pragma('dart2js:noInline')
  static WarpAccount getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<WarpAccount>(
          WarpAccount.$_createMessage);
  static WarpAccount? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get accountId => $_getSZ(0);
  @$pb.TagNumber(1)
  set accountId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasAccountId() => $_has(0);
  @$pb.TagNumber(1)
  void clearAccountId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get accessToken => $_getSZ(1);
  @$pb.TagNumber(2)
  set accessToken($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasAccessToken() => $_has(1);
  @$pb.TagNumber(2)
  void clearAccessToken() => $_clearField(2);
}

class WarpWireguardConfig extends $pb.GeneratedMessage {
  factory WarpWireguardConfig({
    $core.String? privateKey,
    $core.String? localAddressIpv4,
    $core.String? localAddressIpv6,
    $core.String? peerPublicKey,
    $core.String? clientId,
  }) {
    final result = WarpWireguardConfig._();
    if (privateKey != null) result.privateKey = privateKey;
    if (localAddressIpv4 != null) result.localAddressIpv4 = localAddressIpv4;
    if (localAddressIpv6 != null) result.localAddressIpv6 = localAddressIpv6;
    if (peerPublicKey != null) result.peerPublicKey = peerPublicKey;
    if (clientId != null) result.clientId = clientId;
    return result;
  }

  WarpWireguardConfig._();

  factory WarpWireguardConfig.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      WarpWireguardConfig()..mergeFromBuffer(data, registry);
  factory WarpWireguardConfig.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      WarpWireguardConfig()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'WarpWireguardConfig',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: WarpWireguardConfig.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'private-key', protoName: 'private_key')
    ..aOS(2, _omitFieldNames ? '' : 'local-address-ipv4',
        protoName: 'local_address_ipv4')
    ..aOS(3, _omitFieldNames ? '' : 'local-address-ipv6',
        protoName: 'local_address_ipv6')
    ..aOS(4, _omitFieldNames ? '' : 'peer-public-key',
        protoName: 'peer_public_key')
    ..aOS(5, _omitFieldNames ? '' : 'client-id', protoName: 'client_id')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  WarpWireguardConfig clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  WarpWireguardConfig copyWith(void Function(WarpWireguardConfig) updates) =>
      super.copyWith((message) => updates(message as WarpWireguardConfig))
          as WarpWireguardConfig;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core
      .Deprecated('Use WarpWireguardConfig() / WarpWireguardConfig.new instead')
  static WarpWireguardConfig create() => WarpWireguardConfig._();
  static $pb.GeneratedMessage $_createMessage() => WarpWireguardConfig._();
  @$core.override
  WarpWireguardConfig createEmptyInstance() => WarpWireguardConfig._();
  @$core.pragma('dart2js:noInline')
  static WarpWireguardConfig getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<WarpWireguardConfig>(
          WarpWireguardConfig.$_createMessage);
  static WarpWireguardConfig? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get privateKey => $_getSZ(0);
  @$pb.TagNumber(1)
  set privateKey($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasPrivateKey() => $_has(0);
  @$pb.TagNumber(1)
  void clearPrivateKey() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get localAddressIpv4 => $_getSZ(1);
  @$pb.TagNumber(2)
  set localAddressIpv4($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasLocalAddressIpv4() => $_has(1);
  @$pb.TagNumber(2)
  void clearLocalAddressIpv4() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get localAddressIpv6 => $_getSZ(2);
  @$pb.TagNumber(3)
  set localAddressIpv6($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasLocalAddressIpv6() => $_has(2);
  @$pb.TagNumber(3)
  void clearLocalAddressIpv6() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get peerPublicKey => $_getSZ(3);
  @$pb.TagNumber(4)
  set peerPublicKey($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasPeerPublicKey() => $_has(3);
  @$pb.TagNumber(4)
  void clearPeerPublicKey() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get clientId => $_getSZ(4);
  @$pb.TagNumber(5)
  set clientId($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasClientId() => $_has(4);
  @$pb.TagNumber(5)
  void clearClientId() => $_clearField(5);
}

class WarpGenerationResponse extends $pb.GeneratedMessage {
  factory WarpGenerationResponse({
    WarpAccount? account,
    $core.String? log,
    WarpWireguardConfig? config,
  }) {
    final result = WarpGenerationResponse._();
    if (account != null) result.account = account;
    if (log != null) result.log = log;
    if (config != null) result.config = config;
    return result;
  }

  WarpGenerationResponse._();

  factory WarpGenerationResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      WarpGenerationResponse()..mergeFromBuffer(data, registry);
  factory WarpGenerationResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      WarpGenerationResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'WarpGenerationResponse',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: WarpGenerationResponse.$_createMessage)
    ..aOM<WarpAccount>(1, _omitFieldNames ? '' : 'account',
        subBuilder: WarpAccount.$_createMessage)
    ..aOS(2, _omitFieldNames ? '' : 'log')
    ..aOM<WarpWireguardConfig>(3, _omitFieldNames ? '' : 'config',
        subBuilder: WarpWireguardConfig.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  WarpGenerationResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  WarpGenerationResponse copyWith(
          void Function(WarpGenerationResponse) updates) =>
      super.copyWith((message) => updates(message as WarpGenerationResponse))
          as WarpGenerationResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use WarpGenerationResponse() / WarpGenerationResponse.new instead')
  static WarpGenerationResponse create() => WarpGenerationResponse._();
  static $pb.GeneratedMessage $_createMessage() => WarpGenerationResponse._();
  @$core.override
  WarpGenerationResponse createEmptyInstance() => WarpGenerationResponse._();
  @$core.pragma('dart2js:noInline')
  static WarpGenerationResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<WarpGenerationResponse>(
          WarpGenerationResponse.$_createMessage);
  static WarpGenerationResponse? _defaultInstance;

  @$pb.TagNumber(1)
  WarpAccount get account => $_getN(0);
  @$pb.TagNumber(1)
  set account(WarpAccount value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasAccount() => $_has(0);
  @$pb.TagNumber(1)
  void clearAccount() => $_clearField(1);
  @$pb.TagNumber(1)
  WarpAccount ensureAccount() => $_ensure(0);

  @$pb.TagNumber(2)
  $core.String get log => $_getSZ(1);
  @$pb.TagNumber(2)
  set log($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasLog() => $_has(1);
  @$pb.TagNumber(2)
  void clearLog() => $_clearField(2);

  @$pb.TagNumber(3)
  WarpWireguardConfig get config => $_getN(2);
  @$pb.TagNumber(3)
  set config(WarpWireguardConfig value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasConfig() => $_has(2);
  @$pb.TagNumber(3)
  void clearConfig() => $_clearField(3);
  @$pb.TagNumber(3)
  WarpWireguardConfig ensureConfig() => $_ensure(2);
}

class SystemProxyStatus extends $pb.GeneratedMessage {
  factory SystemProxyStatus({
    $core.bool? available,
    $core.bool? enabled,
  }) {
    final result = SystemProxyStatus._();
    if (available != null) result.available = available;
    if (enabled != null) result.enabled = enabled;
    return result;
  }

  SystemProxyStatus._();

  factory SystemProxyStatus.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SystemProxyStatus()..mergeFromBuffer(data, registry);
  factory SystemProxyStatus.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SystemProxyStatus()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SystemProxyStatus',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: SystemProxyStatus.$_createMessage)
    ..aOB(1, _omitFieldNames ? '' : 'available')
    ..aOB(2, _omitFieldNames ? '' : 'enabled')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SystemProxyStatus clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SystemProxyStatus copyWith(void Function(SystemProxyStatus) updates) =>
      super.copyWith((message) => updates(message as SystemProxyStatus))
          as SystemProxyStatus;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use SystemProxyStatus() / SystemProxyStatus.new instead')
  static SystemProxyStatus create() => SystemProxyStatus._();
  static $pb.GeneratedMessage $_createMessage() => SystemProxyStatus._();
  @$core.override
  SystemProxyStatus createEmptyInstance() => SystemProxyStatus._();
  @$core.pragma('dart2js:noInline')
  static SystemProxyStatus getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<SystemProxyStatus>(
          SystemProxyStatus.$_createMessage);
  static SystemProxyStatus? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get available => $_getBF(0);
  @$pb.TagNumber(1)
  set available($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasAvailable() => $_has(0);
  @$pb.TagNumber(1)
  void clearAvailable() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.bool get enabled => $_getBF(1);
  @$pb.TagNumber(2)
  set enabled($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasEnabled() => $_has(1);
  @$pb.TagNumber(2)
  void clearEnabled() => $_clearField(2);
}

class ParseRequest extends $pb.GeneratedMessage {
  factory ParseRequest({
    $core.String? content,
    $core.String? configPath,
    $core.String? tempPath,
    $core.bool? debug,
  }) {
    final result = ParseRequest._();
    if (content != null) result.content = content;
    if (configPath != null) result.configPath = configPath;
    if (tempPath != null) result.tempPath = tempPath;
    if (debug != null) result.debug = debug;
    return result;
  }

  ParseRequest._();

  factory ParseRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ParseRequest()..mergeFromBuffer(data, registry);
  factory ParseRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ParseRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ParseRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: ParseRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'content')
    ..aOS(2, _omitFieldNames ? '' : 'configPath')
    ..aOS(3, _omitFieldNames ? '' : 'tempPath')
    ..aOB(4, _omitFieldNames ? '' : 'debug')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ParseRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ParseRequest copyWith(void Function(ParseRequest) updates) =>
      super.copyWith((message) => updates(message as ParseRequest))
          as ParseRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ParseRequest() / ParseRequest.new instead')
  static ParseRequest create() => ParseRequest._();
  static $pb.GeneratedMessage $_createMessage() => ParseRequest._();
  @$core.override
  ParseRequest createEmptyInstance() => ParseRequest._();
  @$core.pragma('dart2js:noInline')
  static ParseRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ParseRequest>(
          ParseRequest.$_createMessage);
  static ParseRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get content => $_getSZ(0);
  @$pb.TagNumber(1)
  set content($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasContent() => $_has(0);
  @$pb.TagNumber(1)
  void clearContent() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get configPath => $_getSZ(1);
  @$pb.TagNumber(2)
  set configPath($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasConfigPath() => $_has(1);
  @$pb.TagNumber(2)
  void clearConfigPath() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get tempPath => $_getSZ(2);
  @$pb.TagNumber(3)
  set tempPath($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasTempPath() => $_has(2);
  @$pb.TagNumber(3)
  void clearTempPath() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.bool get debug => $_getBF(3);
  @$pb.TagNumber(4)
  set debug($core.bool value) => $_setBool(3, value);
  @$pb.TagNumber(4)
  $core.bool hasDebug() => $_has(3);
  @$pb.TagNumber(4)
  void clearDebug() => $_clearField(4);
}

class ParseResponse extends $pb.GeneratedMessage {
  factory ParseResponse({
    $1.ResponseCode? responseCode,
    $core.String? content,
    $core.String? message,
  }) {
    final result = ParseResponse._();
    if (responseCode != null) result.responseCode = responseCode;
    if (content != null) result.content = content;
    if (message != null) result.message = message;
    return result;
  }

  ParseResponse._();

  factory ParseResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ParseResponse()..mergeFromBuffer(data, registry);
  factory ParseResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ParseResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ParseResponse',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: ParseResponse.$_createMessage)
    ..aE<$1.ResponseCode>(1, _omitFieldNames ? '' : 'responseCode',
        enumValues: $1.ResponseCode.values)
    ..aOS(2, _omitFieldNames ? '' : 'content')
    ..aOS(3, _omitFieldNames ? '' : 'message')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ParseResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ParseResponse copyWith(void Function(ParseResponse) updates) =>
      super.copyWith((message) => updates(message as ParseResponse))
          as ParseResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ParseResponse() / ParseResponse.new instead')
  static ParseResponse create() => ParseResponse._();
  static $pb.GeneratedMessage $_createMessage() => ParseResponse._();
  @$core.override
  ParseResponse createEmptyInstance() => ParseResponse._();
  @$core.pragma('dart2js:noInline')
  static ParseResponse getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ParseResponse>(
          ParseResponse.$_createMessage);
  static ParseResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $1.ResponseCode get responseCode => $_getN(0);
  @$pb.TagNumber(1)
  set responseCode($1.ResponseCode value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasResponseCode() => $_has(0);
  @$pb.TagNumber(1)
  void clearResponseCode() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get content => $_getSZ(1);
  @$pb.TagNumber(2)
  set content($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasContent() => $_has(1);
  @$pb.TagNumber(2)
  void clearContent() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get message => $_getSZ(2);
  @$pb.TagNumber(3)
  set message($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasMessage() => $_has(2);
  @$pb.TagNumber(3)
  void clearMessage() => $_clearField(3);
}

class ChangeHiddifySettingsRequest extends $pb.GeneratedMessage {
  factory ChangeHiddifySettingsRequest({
    $core.String? hiddifySettingsJson,
  }) {
    final result = ChangeHiddifySettingsRequest._();
    if (hiddifySettingsJson != null)
      result.hiddifySettingsJson = hiddifySettingsJson;
    return result;
  }

  ChangeHiddifySettingsRequest._();

  factory ChangeHiddifySettingsRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ChangeHiddifySettingsRequest()..mergeFromBuffer(data, registry);
  factory ChangeHiddifySettingsRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ChangeHiddifySettingsRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ChangeHiddifySettingsRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: ChangeHiddifySettingsRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'hiddifySettingsJson')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ChangeHiddifySettingsRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ChangeHiddifySettingsRequest copyWith(
          void Function(ChangeHiddifySettingsRequest) updates) =>
      super.copyWith(
              (message) => updates(message as ChangeHiddifySettingsRequest))
          as ChangeHiddifySettingsRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use ChangeHiddifySettingsRequest() / ChangeHiddifySettingsRequest.new instead')
  static ChangeHiddifySettingsRequest create() =>
      ChangeHiddifySettingsRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      ChangeHiddifySettingsRequest._();
  @$core.override
  ChangeHiddifySettingsRequest createEmptyInstance() =>
      ChangeHiddifySettingsRequest._();
  @$core.pragma('dart2js:noInline')
  static ChangeHiddifySettingsRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ChangeHiddifySettingsRequest>(
          ChangeHiddifySettingsRequest.$_createMessage);
  static ChangeHiddifySettingsRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get hiddifySettingsJson => $_getSZ(0);
  @$pb.TagNumber(1)
  set hiddifySettingsJson($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasHiddifySettingsJson() => $_has(0);
  @$pb.TagNumber(1)
  void clearHiddifySettingsJson() => $_clearField(1);
}

class GenerateConfigRequest extends $pb.GeneratedMessage {
  factory GenerateConfigRequest({
    $core.String? path,
    $core.String? tempPath,
    $core.bool? debug,
  }) {
    final result = GenerateConfigRequest._();
    if (path != null) result.path = path;
    if (tempPath != null) result.tempPath = tempPath;
    if (debug != null) result.debug = debug;
    return result;
  }

  GenerateConfigRequest._();

  factory GenerateConfigRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GenerateConfigRequest()..mergeFromBuffer(data, registry);
  factory GenerateConfigRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GenerateConfigRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'GenerateConfigRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: GenerateConfigRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'path')
    ..aOS(2, _omitFieldNames ? '' : 'tempPath')
    ..aOB(3, _omitFieldNames ? '' : 'debug')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GenerateConfigRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GenerateConfigRequest copyWith(
          void Function(GenerateConfigRequest) updates) =>
      super.copyWith((message) => updates(message as GenerateConfigRequest))
          as GenerateConfigRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use GenerateConfigRequest() / GenerateConfigRequest.new instead')
  static GenerateConfigRequest create() => GenerateConfigRequest._();
  static $pb.GeneratedMessage $_createMessage() => GenerateConfigRequest._();
  @$core.override
  GenerateConfigRequest createEmptyInstance() => GenerateConfigRequest._();
  @$core.pragma('dart2js:noInline')
  static GenerateConfigRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<GenerateConfigRequest>(
          GenerateConfigRequest.$_createMessage);
  static GenerateConfigRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get path => $_getSZ(0);
  @$pb.TagNumber(1)
  set path($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasPath() => $_has(0);
  @$pb.TagNumber(1)
  void clearPath() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get tempPath => $_getSZ(1);
  @$pb.TagNumber(2)
  set tempPath($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasTempPath() => $_has(1);
  @$pb.TagNumber(2)
  void clearTempPath() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.bool get debug => $_getBF(2);
  @$pb.TagNumber(3)
  set debug($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasDebug() => $_has(2);
  @$pb.TagNumber(3)
  void clearDebug() => $_clearField(3);
}

class GenerateConfigResponse extends $pb.GeneratedMessage {
  factory GenerateConfigResponse({
    $core.String? configContent,
  }) {
    final result = GenerateConfigResponse._();
    if (configContent != null) result.configContent = configContent;
    return result;
  }

  GenerateConfigResponse._();

  factory GenerateConfigResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GenerateConfigResponse()..mergeFromBuffer(data, registry);
  factory GenerateConfigResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GenerateConfigResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'GenerateConfigResponse',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: GenerateConfigResponse.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'configContent')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GenerateConfigResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GenerateConfigResponse copyWith(
          void Function(GenerateConfigResponse) updates) =>
      super.copyWith((message) => updates(message as GenerateConfigResponse))
          as GenerateConfigResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use GenerateConfigResponse() / GenerateConfigResponse.new instead')
  static GenerateConfigResponse create() => GenerateConfigResponse._();
  static $pb.GeneratedMessage $_createMessage() => GenerateConfigResponse._();
  @$core.override
  GenerateConfigResponse createEmptyInstance() => GenerateConfigResponse._();
  @$core.pragma('dart2js:noInline')
  static GenerateConfigResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<GenerateConfigResponse>(
          GenerateConfigResponse.$_createMessage);
  static GenerateConfigResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get configContent => $_getSZ(0);
  @$pb.TagNumber(1)
  set configContent($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasConfigContent() => $_has(0);
  @$pb.TagNumber(1)
  void clearConfigContent() => $_clearField(1);
}

class SelectOutboundRequest extends $pb.GeneratedMessage {
  factory SelectOutboundRequest({
    $core.String? groupTag,
    $core.String? outboundTag,
  }) {
    final result = SelectOutboundRequest._();
    if (groupTag != null) result.groupTag = groupTag;
    if (outboundTag != null) result.outboundTag = outboundTag;
    return result;
  }

  SelectOutboundRequest._();

  factory SelectOutboundRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SelectOutboundRequest()..mergeFromBuffer(data, registry);
  factory SelectOutboundRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SelectOutboundRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SelectOutboundRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: SelectOutboundRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'groupTag')
    ..aOS(2, _omitFieldNames ? '' : 'outboundTag')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SelectOutboundRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SelectOutboundRequest copyWith(
          void Function(SelectOutboundRequest) updates) =>
      super.copyWith((message) => updates(message as SelectOutboundRequest))
          as SelectOutboundRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use SelectOutboundRequest() / SelectOutboundRequest.new instead')
  static SelectOutboundRequest create() => SelectOutboundRequest._();
  static $pb.GeneratedMessage $_createMessage() => SelectOutboundRequest._();
  @$core.override
  SelectOutboundRequest createEmptyInstance() => SelectOutboundRequest._();
  @$core.pragma('dart2js:noInline')
  static SelectOutboundRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SelectOutboundRequest>(
          SelectOutboundRequest.$_createMessage);
  static SelectOutboundRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get groupTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set groupTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasGroupTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearGroupTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get outboundTag => $_getSZ(1);
  @$pb.TagNumber(2)
  set outboundTag($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasOutboundTag() => $_has(1);
  @$pb.TagNumber(2)
  void clearOutboundTag() => $_clearField(2);
}

class UrlTestRequest extends $pb.GeneratedMessage {
  factory UrlTestRequest({
    $core.String? tag,
  }) {
    final result = UrlTestRequest._();
    if (tag != null) result.tag = tag;
    return result;
  }

  UrlTestRequest._();

  factory UrlTestRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      UrlTestRequest()..mergeFromBuffer(data, registry);
  factory UrlTestRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      UrlTestRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'UrlTestRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: UrlTestRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'tag')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  UrlTestRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  UrlTestRequest copyWith(void Function(UrlTestRequest) updates) =>
      super.copyWith((message) => updates(message as UrlTestRequest))
          as UrlTestRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use UrlTestRequest() / UrlTestRequest.new instead')
  static UrlTestRequest create() => UrlTestRequest._();
  static $pb.GeneratedMessage $_createMessage() => UrlTestRequest._();
  @$core.override
  UrlTestRequest createEmptyInstance() => UrlTestRequest._();
  @$core.pragma('dart2js:noInline')
  static UrlTestRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<UrlTestRequest>(
          UrlTestRequest.$_createMessage);
  static UrlTestRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get tag => $_getSZ(0);
  @$pb.TagNumber(1)
  set tag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearTag() => $_clearField(1);
}

class GenerateWarpConfigRequest extends $pb.GeneratedMessage {
  factory GenerateWarpConfigRequest({
    $core.String? licenseKey,
    $core.String? accountId,
    $core.String? accessToken,
  }) {
    final result = GenerateWarpConfigRequest._();
    if (licenseKey != null) result.licenseKey = licenseKey;
    if (accountId != null) result.accountId = accountId;
    if (accessToken != null) result.accessToken = accessToken;
    return result;
  }

  GenerateWarpConfigRequest._();

  factory GenerateWarpConfigRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GenerateWarpConfigRequest()..mergeFromBuffer(data, registry);
  factory GenerateWarpConfigRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GenerateWarpConfigRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'GenerateWarpConfigRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: GenerateWarpConfigRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'licenseKey')
    ..aOS(2, _omitFieldNames ? '' : 'accountId')
    ..aOS(3, _omitFieldNames ? '' : 'accessToken')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GenerateWarpConfigRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GenerateWarpConfigRequest copyWith(
          void Function(GenerateWarpConfigRequest) updates) =>
      super.copyWith((message) => updates(message as GenerateWarpConfigRequest))
          as GenerateWarpConfigRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use GenerateWarpConfigRequest() / GenerateWarpConfigRequest.new instead')
  static GenerateWarpConfigRequest create() => GenerateWarpConfigRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      GenerateWarpConfigRequest._();
  @$core.override
  GenerateWarpConfigRequest createEmptyInstance() =>
      GenerateWarpConfigRequest._();
  @$core.pragma('dart2js:noInline')
  static GenerateWarpConfigRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<GenerateWarpConfigRequest>(
          GenerateWarpConfigRequest.$_createMessage);
  static GenerateWarpConfigRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get licenseKey => $_getSZ(0);
  @$pb.TagNumber(1)
  set licenseKey($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasLicenseKey() => $_has(0);
  @$pb.TagNumber(1)
  void clearLicenseKey() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get accountId => $_getSZ(1);
  @$pb.TagNumber(2)
  set accountId($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasAccountId() => $_has(1);
  @$pb.TagNumber(2)
  void clearAccountId() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get accessToken => $_getSZ(2);
  @$pb.TagNumber(3)
  set accessToken($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasAccessToken() => $_has(2);
  @$pb.TagNumber(3)
  void clearAccessToken() => $_clearField(3);
}

class SetSystemProxyEnabledRequest extends $pb.GeneratedMessage {
  factory SetSystemProxyEnabledRequest({
    $core.bool? isEnabled,
  }) {
    final result = SetSystemProxyEnabledRequest._();
    if (isEnabled != null) result.isEnabled = isEnabled;
    return result;
  }

  SetSystemProxyEnabledRequest._();

  factory SetSystemProxyEnabledRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetSystemProxyEnabledRequest()..mergeFromBuffer(data, registry);
  factory SetSystemProxyEnabledRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetSystemProxyEnabledRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SetSystemProxyEnabledRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: SetSystemProxyEnabledRequest.$_createMessage)
    ..aOB(1, _omitFieldNames ? '' : 'isEnabled')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetSystemProxyEnabledRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetSystemProxyEnabledRequest copyWith(
          void Function(SetSystemProxyEnabledRequest) updates) =>
      super.copyWith(
              (message) => updates(message as SetSystemProxyEnabledRequest))
          as SetSystemProxyEnabledRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use SetSystemProxyEnabledRequest() / SetSystemProxyEnabledRequest.new instead')
  static SetSystemProxyEnabledRequest create() =>
      SetSystemProxyEnabledRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      SetSystemProxyEnabledRequest._();
  @$core.override
  SetSystemProxyEnabledRequest createEmptyInstance() =>
      SetSystemProxyEnabledRequest._();
  @$core.pragma('dart2js:noInline')
  static SetSystemProxyEnabledRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SetSystemProxyEnabledRequest>(
          SetSystemProxyEnabledRequest.$_createMessage);
  static SetSystemProxyEnabledRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get isEnabled => $_getBF(0);
  @$pb.TagNumber(1)
  set isEnabled($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasIsEnabled() => $_has(0);
  @$pb.TagNumber(1)
  void clearIsEnabled() => $_clearField(1);
}

class LogMessage extends $pb.GeneratedMessage {
  factory LogMessage({
    LogLevel? level,
    LogType? type,
    $core.String? message,
    $0.Timestamp? time,
  }) {
    final result = LogMessage._();
    if (level != null) result.level = level;
    if (type != null) result.type = type;
    if (message != null) result.message = message;
    if (time != null) result.time = time;
    return result;
  }

  LogMessage._();

  factory LogMessage.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      LogMessage()..mergeFromBuffer(data, registry);
  factory LogMessage.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      LogMessage()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'LogMessage',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: LogMessage.$_createMessage)
    ..aE<LogLevel>(1, _omitFieldNames ? '' : 'level',
        enumValues: LogLevel.values)
    ..aE<LogType>(2, _omitFieldNames ? '' : 'type', enumValues: LogType.values)
    ..aOS(3, _omitFieldNames ? '' : 'message')
    ..aOM<$0.Timestamp>(4, _omitFieldNames ? '' : 'time',
        subBuilder: $0.Timestamp.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  LogMessage clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  LogMessage copyWith(void Function(LogMessage) updates) =>
      super.copyWith((message) => updates(message as LogMessage)) as LogMessage;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use LogMessage() / LogMessage.new instead')
  static LogMessage create() => LogMessage._();
  static $pb.GeneratedMessage $_createMessage() => LogMessage._();
  @$core.override
  LogMessage createEmptyInstance() => LogMessage._();
  @$core.pragma('dart2js:noInline')
  static LogMessage getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<LogMessage>(LogMessage.$_createMessage);
  static LogMessage? _defaultInstance;

  @$pb.TagNumber(1)
  LogLevel get level => $_getN(0);
  @$pb.TagNumber(1)
  set level(LogLevel value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasLevel() => $_has(0);
  @$pb.TagNumber(1)
  void clearLevel() => $_clearField(1);

  @$pb.TagNumber(2)
  LogType get type => $_getN(1);
  @$pb.TagNumber(2)
  set type(LogType value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasType() => $_has(1);
  @$pb.TagNumber(2)
  void clearType() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get message => $_getSZ(2);
  @$pb.TagNumber(3)
  set message($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasMessage() => $_has(2);
  @$pb.TagNumber(3)
  void clearMessage() => $_clearField(3);

  @$pb.TagNumber(4)
  $0.Timestamp get time => $_getN(3);
  @$pb.TagNumber(4)
  set time($0.Timestamp value) => $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasTime() => $_has(3);
  @$pb.TagNumber(4)
  void clearTime() => $_clearField(4);
  @$pb.TagNumber(4)
  $0.Timestamp ensureTime() => $_ensure(3);
}

class LogRequest extends $pb.GeneratedMessage {
  factory LogRequest({
    LogLevel? level,
  }) {
    final result = LogRequest._();
    if (level != null) result.level = level;
    return result;
  }

  LogRequest._();

  factory LogRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      LogRequest()..mergeFromBuffer(data, registry);
  factory LogRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      LogRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'LogRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: LogRequest.$_createMessage)
    ..aE<LogLevel>(1, _omitFieldNames ? '' : 'level',
        enumValues: LogLevel.values)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  LogRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  LogRequest copyWith(void Function(LogRequest) updates) =>
      super.copyWith((message) => updates(message as LogRequest)) as LogRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use LogRequest() / LogRequest.new instead')
  static LogRequest create() => LogRequest._();
  static $pb.GeneratedMessage $_createMessage() => LogRequest._();
  @$core.override
  LogRequest createEmptyInstance() => LogRequest._();
  @$core.pragma('dart2js:noInline')
  static LogRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<LogRequest>(LogRequest.$_createMessage);
  static LogRequest? _defaultInstance;

  @$pb.TagNumber(1)
  LogLevel get level => $_getN(0);
  @$pb.TagNumber(1)
  set level(LogLevel value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasLevel() => $_has(0);
  @$pb.TagNumber(1)
  void clearLevel() => $_clearField(1);
}

class StopRequest extends $pb.GeneratedMessage {
  factory StopRequest() => StopRequest._();

  StopRequest._();

  factory StopRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StopRequest()..mergeFromBuffer(data, registry);
  factory StopRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StopRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'StopRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: StopRequest.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StopRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StopRequest copyWith(void Function(StopRequest) updates) =>
      super.copyWith((message) => updates(message as StopRequest))
          as StopRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use StopRequest() / StopRequest.new instead')
  static StopRequest create() => StopRequest._();
  static $pb.GeneratedMessage $_createMessage() => StopRequest._();
  @$core.override
  StopRequest createEmptyInstance() => StopRequest._();
  @$core.pragma('dart2js:noInline')
  static StopRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<StopRequest>(
          StopRequest.$_createMessage);
  static StopRequest? _defaultInstance;
}

class LANIPResponse extends $pb.GeneratedMessage {
  factory LANIPResponse({
    $core.String? ip,
  }) {
    final result = LANIPResponse._();
    if (ip != null) result.ip = ip;
    return result;
  }

  LANIPResponse._();

  factory LANIPResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      LANIPResponse()..mergeFromBuffer(data, registry);
  factory LANIPResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      LANIPResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'LANIPResponse',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'hcore'),
      createEmptyInstance: LANIPResponse.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'ip')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  LANIPResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  LANIPResponse copyWith(void Function(LANIPResponse) updates) =>
      super.copyWith((message) => updates(message as LANIPResponse))
          as LANIPResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use LANIPResponse() / LANIPResponse.new instead')
  static LANIPResponse create() => LANIPResponse._();
  static $pb.GeneratedMessage $_createMessage() => LANIPResponse._();
  @$core.override
  LANIPResponse createEmptyInstance() => LANIPResponse._();
  @$core.pragma('dart2js:noInline')
  static LANIPResponse getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<LANIPResponse>(
          LANIPResponse.$_createMessage);
  static LANIPResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get ip => $_getSZ(0);
  @$pb.TagNumber(1)
  set ip($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasIp() => $_has(0);
  @$pb.TagNumber(1)
  void clearIp() => $_clearField(1);
}

const $core.bool _omitFieldNames =
    $core.bool.fromEnvironment('protobuf.omit_field_names');
const $core.bool _omitMessageNames =
    $core.bool.fromEnvironment('protobuf.omit_message_names');
