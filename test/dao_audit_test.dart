import 'package:drift/drift.dart' hide isNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/core/db/db.dart';
import 'package:hiddify/features/profile/data/profile_data_source.dart';
import 'package:hiddify/features/profile/data/profile_data_mapper.dart';
import 'package:hiddify/features/profile/model/profile_entity.dart';
import 'package:hiddify/features/profile/model/profile_failure.dart';
import 'package:hiddify/features/per_app_proxy/data/app_proxy_data_source.dart';
import 'package:hiddify/features/per_app_proxy/model/per_app_proxy_mode.dart';
import 'package:hiddify/features/per_app_proxy/model/per_app_proxy_backup.dart';
import 'package:hiddify/features/per_app_proxy/model/pkg_flag.dart';

void main() {
  late Db db;
  setUp(() => db = Db(NativeDatabase.memory()));
  tearDown(() => db.close());
  ProfileEntriesCompanion profile(String id, bool active) => ProfileEntriesCompanion.insert(
    id: id,
    type: ProfileType.local,
    active: active,
    name: id,
    lastUpdate: DateTime.now(),
  );

  test('Deleting an active profile uses database state rather than stale caller flag', () async {
    final dao = ProfileDao(db);
    await dao.insert(profile('first', true));
    await dao.insert(profile('second', false));
    await dao.deleteById('first', false);
    expect((await dao.getById('second'))!.active, isTrue);
    expect(await dao.getById('first'), isNull);
  });

  test('Remote metadata update does not restore a stale active flag', () {
    final remote = ProfileEntity.remote(
      id: 'remote',
      active: true,
      name: 'remote',
      url: 'https://example.com/sub',
      lastUpdate: DateTime.now(),
    );
    expect(remote.toUpdateEntry().active.present, isFalse);
    final linked = remote.copyWith(populatedHeaders: {'support-url': 'https://example.com/help'});
    expect(linked.supportUrl, 'https://example.com/help');
  });

  test('Missing profile edit fails instead of reporting successful persistence', () async {
    await expectLater(
      ProfileDao(db).edit('missing', const ProfileEntriesCompanion(name: Value('new'))),
      throwsA(isA<ProfileFailure>()),
    );
  });

  test('Backup replaces old manual flags and preserves automatic selection', () async {
    final dao = AppProxyDao(db);
    await db
        .into(db.appProxyEntries)
        .insert(
          AppProxyEntriesCompanion.insert(
            mode: AppProxyMode.include,
            pkgName: 'old',
            flags: Value(PkgFlag.userSelection.add(PkgFlag.autoSelection.add(0))),
          ),
        );
    await dao.importPkgs(
      backup: const PerAppProxyBackup(include: PerAppProxyBackupMode(selected: ['new'])),
    );
    expect(await dao.getPkgsByFlag(flag: PkgFlag.userSelection, mode: AppProxyMode.include), ['new']);
    expect(await dao.getPkgsByFlag(flag: PkgFlag.autoSelection, mode: AppProxyMode.include), ['old']);
    await expectLater(
      dao.importPkgs(
        backup: const PerAppProxyBackup(
          include: PerAppProxyBackupMode(selected: ['conflict'], deselected: ['conflict']),
        ),
      ),
      throwsFormatException,
    );
    expect(await dao.getPkgsByFlag(flag: PkgFlag.userSelection, mode: AppProxyMode.include), ['new']);
  });
}
