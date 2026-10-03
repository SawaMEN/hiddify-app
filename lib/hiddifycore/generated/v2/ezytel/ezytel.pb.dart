// This is a generated file - do not edit.
//
// Generated from v2/ezytel/ezytel.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports

import 'dart:core' as $core;

import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:protobuf/protobuf.dart' as $pb;

export 'package:protobuf/protobuf.dart' show GeneratedMessageGenericExtensions;

/// Request the metadata card of a Telegram public channel
/// (name, description preview, avatar, last-post timestamp, unread badge).
class ChannelInfoRequest extends $pb.GeneratedMessage {
  factory ChannelInfoRequest({
    $core.String? channelId,
    $fixnum.Int64? lastRead,
    $core.bool? disableInlineImages,
  }) {
    final result = ChannelInfoRequest._();
    if (channelId != null) result.channelId = channelId;
    if (lastRead != null) result.lastRead = lastRead;
    if (disableInlineImages != null)
      result.disableInlineImages = disableInlineImages;
    return result;
  }

  ChannelInfoRequest._();

  factory ChannelInfoRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ChannelInfoRequest()..mergeFromBuffer(data, registry);
  factory ChannelInfoRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ChannelInfoRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ChannelInfoRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'ezytel'),
      createEmptyInstance: ChannelInfoRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'channelId')
    ..aInt64(2, _omitFieldNames ? '' : 'lastRead')
    ..aOB(3, _omitFieldNames ? '' : 'disableInlineImages')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ChannelInfoRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ChannelInfoRequest copyWith(void Function(ChannelInfoRequest) updates) =>
      super.copyWith((message) => updates(message as ChannelInfoRequest))
          as ChannelInfoRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ChannelInfoRequest() / ChannelInfoRequest.new instead')
  static ChannelInfoRequest create() => ChannelInfoRequest._();
  static $pb.GeneratedMessage $_createMessage() => ChannelInfoRequest._();
  @$core.override
  ChannelInfoRequest createEmptyInstance() => ChannelInfoRequest._();
  @$core.pragma('dart2js:noInline')
  static ChannelInfoRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ChannelInfoRequest>(
          ChannelInfoRequest.$_createMessage);
  static ChannelInfoRequest? _defaultInstance;

  /// Channel id, e.g. "durov" — without "@" or "t.me/".
  @$pb.TagNumber(1)
  $core.String get channelId => $_getSZ(0);
  @$pb.TagNumber(1)
  set channelId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasChannelId() => $_has(0);
  @$pb.TagNumber(1)
  void clearChannelId() => $_clearField(1);

  /// Last post id the client has already read; used to compute the
  /// unread badge (newmsg). 0 means "everything is unread".
  @$pb.TagNumber(2)
  $fixnum.Int64 get lastRead => $_getI64(1);
  @$pb.TagNumber(2)
  set lastRead($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasLastRead() => $_has(1);
  @$pb.TagNumber(2)
  void clearLastRead() => $_clearField(2);

  /// By default avatar_path in the response carries a
  /// "data:image/jpeg;base64,…" URI ready to drop into <img src>.
  /// Set this to true to opt out and receive the legacy
  /// "cache/<md5>.jpg" server-side file path instead.
  @$pb.TagNumber(3)
  $core.bool get disableInlineImages => $_getBF(2);
  @$pb.TagNumber(3)
  set disableInlineImages($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasDisableInlineImages() => $_has(2);
  @$pb.TagNumber(3)
  void clearDisableInlineImages() => $_clearField(3);
}

class ChannelInfo extends $pb.GeneratedMessage {
  factory ChannelInfo({
    $core.String? name,
    $core.String? description,
    $core.String? avatarPath,
    $fixnum.Int64? date,
    $core.String? dateStr,
    $core.String? newmsg,
    $fixnum.Int64? lastPostId,
    $core.bool? ok,
  }) {
    final result = ChannelInfo._();
    if (name != null) result.name = name;
    if (description != null) result.description = description;
    if (avatarPath != null) result.avatarPath = avatarPath;
    if (date != null) result.date = date;
    if (dateStr != null) result.dateStr = dateStr;
    if (newmsg != null) result.newmsg = newmsg;
    if (lastPostId != null) result.lastPostId = lastPostId;
    if (ok != null) result.ok = ok;
    return result;
  }

  ChannelInfo._();

  factory ChannelInfo.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ChannelInfo()..mergeFromBuffer(data, registry);
  factory ChannelInfo.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ChannelInfo()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ChannelInfo',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'ezytel'),
      createEmptyInstance: ChannelInfo.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..aOS(2, _omitFieldNames ? '' : 'description')
    ..aOS(3, _omitFieldNames ? '' : 'avatarPath')
    ..aInt64(4, _omitFieldNames ? '' : 'date')
    ..aOS(5, _omitFieldNames ? '' : 'dateStr')
    ..aOS(6, _omitFieldNames ? '' : 'newmsg')
    ..aInt64(7, _omitFieldNames ? '' : 'lastPostId')
    ..aOB(8, _omitFieldNames ? '' : 'ok')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ChannelInfo clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ChannelInfo copyWith(void Function(ChannelInfo) updates) =>
      super.copyWith((message) => updates(message as ChannelInfo))
          as ChannelInfo;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ChannelInfo() / ChannelInfo.new instead')
  static ChannelInfo create() => ChannelInfo._();
  static $pb.GeneratedMessage $_createMessage() => ChannelInfo._();
  @$core.override
  ChannelInfo createEmptyInstance() => ChannelInfo._();
  @$core.pragma('dart2js:noInline')
  static ChannelInfo getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ChannelInfo>(
          ChannelInfo.$_createMessage);
  static ChannelInfo? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  /// Plain-text preview of the most recent post, or "فایل" if it is a media post.
  @$pb.TagNumber(2)
  $core.String get description => $_getSZ(1);
  @$pb.TagNumber(2)
  set description($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasDescription() => $_has(1);
  @$pb.TagNumber(2)
  void clearDescription() => $_clearField(2);

  /// By default this is a "data:image/jpeg;base64,…" URI ready for
  /// <img src>. When the request set disable_inline_images=true it is
  /// the legacy "cache/<md5>.jpg" file path relative to GetCacheDir().
  /// Empty if the avatar could not be fetched.
  @$pb.TagNumber(3)
  $core.String get avatarPath => $_getSZ(2);
  @$pb.TagNumber(3)
  set avatarPath($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasAvatarPath() => $_has(2);
  @$pb.TagNumber(3)
  void clearAvatarPath() => $_clearField(3);

  /// Unix epoch seconds of the most recent post.
  @$pb.TagNumber(4)
  $fixnum.Int64 get date => $_getI64(3);
  @$pb.TagNumber(4)
  set date($fixnum.Int64 value) => $_setInt64(3, value);
  @$pb.TagNumber(4)
  $core.bool hasDate() => $_has(3);
  @$pb.TagNumber(4)
  void clearDate() => $_clearField(4);

  /// Persian-calendar formatted "MM-DD HH:mm" string for the most recent post.
  @$pb.TagNumber(5)
  $core.String get dateStr => $_getSZ(4);
  @$pb.TagNumber(5)
  set dateStr($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasDateStr() => $_has(4);
  @$pb.TagNumber(5)
  void clearDateStr() => $_clearField(5);

  /// Unread badge, e.g. "+12" or "+99" (capped). Empty when caught up.
  @$pb.TagNumber(6)
  $core.String get newmsg => $_getSZ(5);
  @$pb.TagNumber(6)
  set newmsg($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasNewmsg() => $_has(5);
  @$pb.TagNumber(6)
  void clearNewmsg() => $_clearField(6);

  /// Last post id observed on the channel page (server-side cookie replacement).
  @$pb.TagNumber(7)
  $fixnum.Int64 get lastPostId => $_getI64(6);
  @$pb.TagNumber(7)
  set lastPostId($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(7)
  $core.bool hasLastPostId() => $_has(6);
  @$pb.TagNumber(7)
  void clearLastPostId() => $_clearField(7);

  /// True when the channel page was reachable and parsed; false if served
  /// from stale cache (newmsg is then forced to "OFF").
  @$pb.TagNumber(8)
  $core.bool get ok => $_getBF(7);
  @$pb.TagNumber(8)
  set ok($core.bool value) => $_setBool(7, value);
  @$pb.TagNumber(8)
  $core.bool hasOk() => $_has(7);
  @$pb.TagNumber(8)
  void clearOk() => $_clearField(8);
}

/// Request a slab of HTML for the channel timeline.
class ChannelMessagesRequest extends $pb.GeneratedMessage {
  factory ChannelMessagesRequest({
    $core.String? channelId,
    $fixnum.Int64? before,
    $core.bool? disableInlineImages,
  }) {
    final result = ChannelMessagesRequest._();
    if (channelId != null) result.channelId = channelId;
    if (before != null) result.before = before;
    if (disableInlineImages != null)
      result.disableInlineImages = disableInlineImages;
    return result;
  }

  ChannelMessagesRequest._();

  factory ChannelMessagesRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ChannelMessagesRequest()..mergeFromBuffer(data, registry);
  factory ChannelMessagesRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ChannelMessagesRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ChannelMessagesRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'ezytel'),
      createEmptyInstance: ChannelMessagesRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'channelId')
    ..aInt64(2, _omitFieldNames ? '' : 'before')
    ..aOB(3, _omitFieldNames ? '' : 'disableInlineImages')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ChannelMessagesRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ChannelMessagesRequest copyWith(
          void Function(ChannelMessagesRequest) updates) =>
      super.copyWith((message) => updates(message as ChannelMessagesRequest))
          as ChannelMessagesRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use ChannelMessagesRequest() / ChannelMessagesRequest.new instead')
  static ChannelMessagesRequest create() => ChannelMessagesRequest._();
  static $pb.GeneratedMessage $_createMessage() => ChannelMessagesRequest._();
  @$core.override
  ChannelMessagesRequest createEmptyInstance() => ChannelMessagesRequest._();
  @$core.pragma('dart2js:noInline')
  static ChannelMessagesRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ChannelMessagesRequest>(
          ChannelMessagesRequest.$_createMessage);
  static ChannelMessagesRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get channelId => $_getSZ(0);
  @$pb.TagNumber(1)
  set channelId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasChannelId() => $_has(0);
  @$pb.TagNumber(1)
  void clearChannelId() => $_clearField(1);

  /// 0 = latest page; otherwise the "before" cursor returned by Telegram
  /// (the data-before attribute of messages_more_wrap).
  @$pb.TagNumber(2)
  $fixnum.Int64 get before => $_getI64(1);
  @$pb.TagNumber(2)
  set before($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasBefore() => $_has(1);
  @$pb.TagNumber(2)
  void clearBefore() => $_clearField(2);

  /// By default every image URL in the returned html and the
  /// channel_avatar field is a "data:image/jpeg;base64,…" URI ready
  /// to render. Set this to true to opt out: html and channel_avatar
  /// will both carry the legacy "proxy.php?url=<hex>" placeholder
  /// form, requiring per-image ProxyImage calls.
  @$pb.TagNumber(3)
  $core.bool get disableInlineImages => $_getBF(2);
  @$pb.TagNumber(3)
  set disableInlineImages($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasDisableInlineImages() => $_has(2);
  @$pb.TagNumber(3)
  void clearDisableInlineImages() => $_clearField(3);
}

class ChannelMessagesResponse extends $pb.GeneratedMessage {
  factory ChannelMessagesResponse({
    $core.String? html,
    $core.String? channelAvatar,
    $fixnum.Int64? lastPostId,
  }) {
    final result = ChannelMessagesResponse._();
    if (html != null) result.html = html;
    if (channelAvatar != null) result.channelAvatar = channelAvatar;
    if (lastPostId != null) result.lastPostId = lastPostId;
    return result;
  }

  ChannelMessagesResponse._();

  factory ChannelMessagesResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ChannelMessagesResponse()..mergeFromBuffer(data, registry);
  factory ChannelMessagesResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ChannelMessagesResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ChannelMessagesResponse',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'ezytel'),
      createEmptyInstance: ChannelMessagesResponse.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'html')
    ..aOS(2, _omitFieldNames ? '' : 'channelAvatar')
    ..aInt64(3, _omitFieldNames ? '' : 'lastPostId')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ChannelMessagesResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ChannelMessagesResponse copyWith(
          void Function(ChannelMessagesResponse) updates) =>
      super.copyWith((message) => updates(message as ChannelMessagesResponse))
          as ChannelMessagesResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use ChannelMessagesResponse() / ChannelMessagesResponse.new instead')
  static ChannelMessagesResponse create() => ChannelMessagesResponse._();
  static $pb.GeneratedMessage $_createMessage() => ChannelMessagesResponse._();
  @$core.override
  ChannelMessagesResponse createEmptyInstance() => ChannelMessagesResponse._();
  @$core.pragma('dart2js:noInline')
  static ChannelMessagesResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ChannelMessagesResponse>(
          ChannelMessagesResponse.$_createMessage);
  static ChannelMessagesResponse? _defaultInstance;

  /// Pre-rendered HTML fragment ready to be injected into .main_block.
  /// Dates have been converted to the Persian calendar. By default
  /// <img src> and background-image:url(...) carry inline
  /// "data:image/jpeg;base64,…" payloads; with
  /// disable_inline_images=true they carry "proxy.php?url=<hex>"
  /// placeholders.
  @$pb.TagNumber(1)
  $core.String get html => $_getSZ(0);
  @$pb.TagNumber(1)
  set html($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasHtml() => $_has(0);
  @$pb.TagNumber(1)
  void clearHtml() => $_clearField(1);

  /// Channel avatar embedded in the header (only set when before == 0).
  /// "data:image/jpeg;base64,…" by default, "proxy.php?url=<hex>"
  /// placeholder when disable_inline_images=true.
  @$pb.TagNumber(2)
  $core.String get channelAvatar => $_getSZ(1);
  @$pb.TagNumber(2)
  set channelAvatar($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasChannelAvatar() => $_has(1);
  @$pb.TagNumber(2)
  void clearChannelAvatar() => $_clearField(2);

  /// Last post id seen on the page (mirrors the lastread_<chid> cookie).
  @$pb.TagNumber(3)
  $fixnum.Int64 get lastPostId => $_getI64(2);
  @$pb.TagNumber(3)
  set lastPostId($fixnum.Int64 value) => $_setInt64(2, value);
  @$pb.TagNumber(3)
  $core.bool hasLastPostId() => $_has(2);
  @$pb.TagNumber(3)
  void clearLastPostId() => $_clearField(3);
}

/// Fetch a Telegram-hosted image through the translate.goog domain front
/// and cache it locally. Mirrors proxy.php?url=<hex>.
class ProxyImageRequest extends $pb.GeneratedMessage {
  factory ProxyImageRequest({
    $core.String? hexUrl,
  }) {
    final result = ProxyImageRequest._();
    if (hexUrl != null) result.hexUrl = hexUrl;
    return result;
  }

  ProxyImageRequest._();

  factory ProxyImageRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProxyImageRequest()..mergeFromBuffer(data, registry);
  factory ProxyImageRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProxyImageRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ProxyImageRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'ezytel'),
      createEmptyInstance: ProxyImageRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'hexUrl')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProxyImageRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProxyImageRequest copyWith(void Function(ProxyImageRequest) updates) =>
      super.copyWith((message) => updates(message as ProxyImageRequest))
          as ProxyImageRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ProxyImageRequest() / ProxyImageRequest.new instead')
  static ProxyImageRequest create() => ProxyImageRequest._();
  static $pb.GeneratedMessage $_createMessage() => ProxyImageRequest._();
  @$core.override
  ProxyImageRequest createEmptyInstance() => ProxyImageRequest._();
  @$core.pragma('dart2js:noInline')
  static ProxyImageRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ProxyImageRequest>(
          ProxyImageRequest.$_createMessage);
  static ProxyImageRequest? _defaultInstance;

  /// Hex-encoded source URL without the "https://" prefix, exactly as the
  /// PHP version expected (bin2hex of the host+path).
  @$pb.TagNumber(1)
  $core.String get hexUrl => $_getSZ(0);
  @$pb.TagNumber(1)
  set hexUrl($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasHexUrl() => $_has(0);
  @$pb.TagNumber(1)
  void clearHexUrl() => $_clearField(1);
}

class ProxyImageResponse extends $pb.GeneratedMessage {
  factory ProxyImageResponse({
    $core.List<$core.int>? data,
    $core.String? contentType,
    $core.String? cacheName,
  }) {
    final result = ProxyImageResponse._();
    if (data != null) result.data = data;
    if (contentType != null) result.contentType = contentType;
    if (cacheName != null) result.cacheName = cacheName;
    return result;
  }

  ProxyImageResponse._();

  factory ProxyImageResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProxyImageResponse()..mergeFromBuffer(data, registry);
  factory ProxyImageResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProxyImageResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ProxyImageResponse',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'ezytel'),
      createEmptyInstance: ProxyImageResponse.$_createMessage)
    ..a<$core.List<$core.int>>(
        1, _omitFieldNames ? '' : 'data', $pb.PbFieldType.OY)
    ..aOS(2, _omitFieldNames ? '' : 'contentType')
    ..aOS(3, _omitFieldNames ? '' : 'cacheName')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProxyImageResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProxyImageResponse copyWith(void Function(ProxyImageResponse) updates) =>
      super.copyWith((message) => updates(message as ProxyImageResponse))
          as ProxyImageResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ProxyImageResponse() / ProxyImageResponse.new instead')
  static ProxyImageResponse create() => ProxyImageResponse._();
  static $pb.GeneratedMessage $_createMessage() => ProxyImageResponse._();
  @$core.override
  ProxyImageResponse createEmptyInstance() => ProxyImageResponse._();
  @$core.pragma('dart2js:noInline')
  static ProxyImageResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ProxyImageResponse>(
          ProxyImageResponse.$_createMessage);
  static ProxyImageResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $core.List<$core.int> get data => $_getN(0);
  @$pb.TagNumber(1)
  set data($core.List<$core.int> value) => $_setBytes(0, value);
  @$pb.TagNumber(1)
  $core.bool hasData() => $_has(0);
  @$pb.TagNumber(1)
  void clearData() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get contentType => $_getSZ(1);
  @$pb.TagNumber(2)
  set contentType($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasContentType() => $_has(1);
  @$pb.TagNumber(2)
  void clearContentType() => $_clearField(2);

  /// Local cache filename (md5(url) + ".jpg") for clients that prefer to
  /// reload from disk instead of holding the bytes.
  @$pb.TagNumber(3)
  $core.String get cacheName => $_getSZ(2);
  @$pb.TagNumber(3)
  set cacheName($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasCacheName() => $_has(2);
  @$pb.TagNumber(3)
  void clearCacheName() => $_clearField(3);
}

/// Parse the operator-supplied newline-separated channel list into the
/// normalised ids the other RPCs accept.
class ParseChannelsRequest extends $pb.GeneratedMessage {
  factory ParseChannelsRequest({
    $core.String? raw,
  }) {
    final result = ParseChannelsRequest._();
    if (raw != null) result.raw = raw;
    return result;
  }

  ParseChannelsRequest._();

  factory ParseChannelsRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ParseChannelsRequest()..mergeFromBuffer(data, registry);
  factory ParseChannelsRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ParseChannelsRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ParseChannelsRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'ezytel'),
      createEmptyInstance: ParseChannelsRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'raw')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ParseChannelsRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ParseChannelsRequest copyWith(void Function(ParseChannelsRequest) updates) =>
      super.copyWith((message) => updates(message as ParseChannelsRequest))
          as ParseChannelsRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use ParseChannelsRequest() / ParseChannelsRequest.new instead')
  static ParseChannelsRequest create() => ParseChannelsRequest._();
  static $pb.GeneratedMessage $_createMessage() => ParseChannelsRequest._();
  @$core.override
  ParseChannelsRequest createEmptyInstance() => ParseChannelsRequest._();
  @$core.pragma('dart2js:noInline')
  static ParseChannelsRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ParseChannelsRequest>(
          ParseChannelsRequest.$_createMessage);
  static ParseChannelsRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get raw => $_getSZ(0);
  @$pb.TagNumber(1)
  set raw($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasRaw() => $_has(0);
  @$pb.TagNumber(1)
  void clearRaw() => $_clearField(1);
}

class ParseChannelsResponse extends $pb.GeneratedMessage {
  factory ParseChannelsResponse({
    $core.Iterable<$core.String>? channelIds,
  }) {
    final result = ParseChannelsResponse._();
    if (channelIds != null) result.channelIds.addAll(channelIds);
    return result;
  }

  ParseChannelsResponse._();

  factory ParseChannelsResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ParseChannelsResponse()..mergeFromBuffer(data, registry);
  factory ParseChannelsResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ParseChannelsResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ParseChannelsResponse',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'ezytel'),
      createEmptyInstance: ParseChannelsResponse.$_createMessage)
    ..pPS(1, _omitFieldNames ? '' : 'channelIds')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ParseChannelsResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ParseChannelsResponse copyWith(
          void Function(ParseChannelsResponse) updates) =>
      super.copyWith((message) => updates(message as ParseChannelsResponse))
          as ParseChannelsResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use ParseChannelsResponse() / ParseChannelsResponse.new instead')
  static ParseChannelsResponse create() => ParseChannelsResponse._();
  static $pb.GeneratedMessage $_createMessage() => ParseChannelsResponse._();
  @$core.override
  ParseChannelsResponse createEmptyInstance() => ParseChannelsResponse._();
  @$core.pragma('dart2js:noInline')
  static ParseChannelsResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ParseChannelsResponse>(
          ParseChannelsResponse.$_createMessage);
  static ParseChannelsResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<$core.String> get channelIds => $_getList(0);
}

const $core.bool _omitFieldNames =
    $core.bool.fromEnvironment('protobuf.omit_field_names');
const $core.bool _omitMessageNames =
    $core.bool.fromEnvironment('protobuf.omit_message_names');
