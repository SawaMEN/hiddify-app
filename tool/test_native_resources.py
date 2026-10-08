import tempfile
import unittest
from pathlib import Path
from check_native_resources import resource_graph


class ResourceReachabilityTest(unittest.TestCase):
    def scan(self, resources, code, manifest=''):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory)
            res = source / 'main/res'
            for name, content in resources.items():
                path = res / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            (source / 'Screen.kt').write_text(code)
            (source / 'AndroidManifest.xml').write_text(manifest)
            definitions, missing, unused = resource_graph(source, res)
            return set(definitions), missing, unused

    def test_arrays_and_platform_resources(self):
        definitions, missing, unused = self.scan({
            'values/strings.xml': '<resources><string name="unit">MiB</string><string-array name="units"><item>@string/unit</item></string-array></resources>',
        }, 'val units = R.array.units; val cancel = android.R.string.cancel')
        self.assertEqual(definitions, {('array', 'units'), ('string', 'unit')})
        self.assertFalse(missing)
        self.assertFalse(unused)

    def test_all_qualifiers_and_transitive_xml_references(self):
        _, missing, unused = self.scan({
            'values/styles.xml': '<resources><style name="LaunchTheme"><item name="android:windowBackground">@drawable/background</item></style><color name="day">#ffffff</color><color name="night">#000000</color></resources>',
            'drawable/background.xml': '<shape><solid color="@color/day"/></shape>',
            'drawable-night/background.xml': '<shape><solid color="@color/night"/></shape>',
        }, '', '<application theme="@style/LaunchTheme"/>')
        self.assertFalse(missing)
        self.assertFalse(unused)

    def test_missing_and_unused_are_reported_separately(self):
        _, missing, unused = self.scan({
            'values/strings.xml': '<resources><string name="old">Legacy</string></resources>',
        }, 'val icon = R.drawable.missing')
        self.assertEqual(missing, {('drawable', 'missing')})
        self.assertEqual(unused, {('string', 'old')})


if __name__ == '__main__':
    unittest.main()
