import contextlib
import io
import subprocess
import unittest
from unittest.mock import patch

from download_core_modules import download


class CoreModuleDownloadTest(unittest.TestCase):
    def run_download(self, results):
        with patch('download_core_modules.subprocess.run', side_effect=results) as run, \
                patch('download_core_modules.time.sleep') as sleep, \
                contextlib.redirect_stdout(io.StringIO()):
            code = download('/core')
        return code, run.call_count, sleep.call_count

    def test_retries_http2_transport_failure(self):
        failed = subprocess.CompletedProcess([], 1, 'go: example.org/lib: stream error: INTERNAL_ERROR\n')
        success = subprocess.CompletedProcess([], 0, '')
        self.assertEqual(self.run_download([failed, success]), (0, 2, 1))

    def test_network_failure_has_bounded_retries(self):
        failed = subprocess.CompletedProcess([], 1, 'go: example.org/lib: i/o timeout\n')
        self.assertEqual(self.run_download([failed] * 3), (1, 3, 2))

    def test_does_not_retry_invalid_dependency(self):
        failed = subprocess.CompletedProcess([], 1, 'go: example.org/lib: unknown revision v9.9.9\n')
        self.assertEqual(self.run_download([failed]), (1, 1, 0))

    def test_mixed_network_and_checksum_errors_fail_immediately(self):
        failed = subprocess.CompletedProcess([], 1,
            'go: example.org/lib: i/o timeout\ngo: example.org/other: checksum mismatch\n')
        self.assertEqual(self.run_download([failed]), (1, 1, 0))

    def test_success_needs_no_retry(self):
        self.assertEqual(self.run_download([subprocess.CompletedProcess([], 0, '')]), (0, 1, 0))


if __name__ == '__main__':
    unittest.main()
