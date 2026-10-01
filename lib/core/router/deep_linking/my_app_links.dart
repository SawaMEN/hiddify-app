import 'package:app_links/app_links.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'my_app_links.g.dart';

@riverpod
Stream<String> myAppLinks(Ref ref) async* {
  yield* AppLinks().uriLinkStream.map((event) => event.toString());
}
