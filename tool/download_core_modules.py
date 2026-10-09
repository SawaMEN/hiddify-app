"""Retry transient Go proxy failures before compiling the pinned Android core."""
from pathlib import Path
import re
import subprocess
import sys
import time


TRANSIENT = re.compile(
    r"stream error:|INTERNAL_ERROR|unexpected EOF|connection reset|"
    r"connection refused|i/o timeout|TLS handshake timeout|"
    r"temporary failure|temporary network|no such host|"
    r"(?:502|503|504) (?:Bad Gateway|Service Unavailable|Gateway Timeout)",
    re.IGNORECASE,
)


def download(directory, attempts=3):
    for attempt in range(1, attempts + 1):
        result = subprocess.run(
            ['go', 'mod', 'download'], cwd=directory,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
        )
        print(result.stdout, end='', flush=True)
        if result.returncode == 0:
            return 0
        # Invalid versions, checksum mismatches and source errors must fail immediately.
        errors = [line for line in result.stdout.splitlines() if line.startswith('go:')]
        if attempt == attempts or not errors or not all(TRANSIENT.search(line) for line in errors):
            return result.returncode
        wait = 5 * attempt
        print(f'Go module download hit a network error; retrying in {wait}s '
              f'({attempt + 1}/{attempts})', flush=True)
        time.sleep(wait)
    return 1


if __name__ == '__main__':
    sys.exit(download(Path(__file__).resolve().parents[1] / 'hiddify-core'))
