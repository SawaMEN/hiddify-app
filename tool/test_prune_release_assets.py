import unittest
from prune_release_assets import obsolete_assets


class ReleaseAssetTests(unittest.TestCase):
    expected = ['client.apk', 'client.dmg']

    def asset(self, name, **overrides):
        return dict(id=name, name=name, state='uploaded', size=100, **overrides)

    def test_removes_legacy_archives_and_extra_apks_only_after_pair_exists(self):
        assets = [self.asset(name) for name in self.expected + ['old.zip', 'old.sha256', 'README.txt', 'old.apk']]
        self.assertEqual(obsolete_assets({'assets': assets}, self.expected),
                         ['old.zip', 'old.sha256', 'README.txt', 'old.apk'])

    def test_partial_failed_empty_and_duplicate_uploads_never_prune(self):
        for damaged in [[], [self.asset('client.apk')],
                        [self.asset('client.apk'), dict(id=2, name='client.dmg', state='new', size=100)],
                        [self.asset('client.apk'), dict(id=2, name='client.dmg', state='uploaded', size=0)],
                        [self.asset(name) for name in self.expected + ['client.apk']]]:
            with self.subTest(assets=damaged), self.assertRaises(ValueError):
                obsolete_assets({'assets': damaged + [self.asset('old.zip')]}, self.expected)

    def test_clean_pair_is_unchanged(self):
        self.assertEqual(obsolete_assets({'assets': [self.asset(n) for n in self.expected]}, self.expected), [])

    def test_rejects_incorrect_allowlist(self):
        with self.assertRaises(ValueError):
            obsolete_assets({'assets': []}, ['client.apk', 'old.zip'])
