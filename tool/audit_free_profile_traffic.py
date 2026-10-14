"""Opt-in live engine audit. Never print configurations, process output or credentials."""
import copy
import json
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path

core, directory = sys.argv[1], Path(sys.argv[2])
target_ip = socket.gethostbyname('www.gstatic.com')
for entry in json.loads((directory / 'manifest.json').read_text()):
    path = directory / (entry['id'] + '.json')
    label = entry['label']
    checked = subprocess.run([core, 'check', '-c', str(path)], capture_output=True, timeout=30)
    print(f"RAW CHECK {label}: {'OK' if checked.returncode == 0 else 'conversion required'}", flush=True)
    if checked.returncode:
        # A raw subscription needs the exact engine conversion used by the application.
        built = directory / 'converted.json'
        result = subprocess.run([core, 'parse', str(path), '-o', str(built)], capture_output=True, timeout=60)
        print(f"CONVERSION {label}: {'OK' if result.returncode == 0 else 'FAILED'}", flush=True)
        if result.returncode:
            continue
        root = json.loads(built.read_text())
    else:
        root = json.loads(path.read_text())
    if not entry['warp'] and label != 'Opera':
        # Build default DNS and dependency settings as Mobile.validateProfile/start do.
        parsed = directory / 'parsed.json'
        parsed.write_text(json.dumps(root))
        built = directory / 'built.json'
        result = subprocess.run([core, 'build', '-c', str(parsed), '-o', str(built)], capture_output=True, timeout=60)
        print(f"BUILD {label}: {'OK' if result.returncode == 0 else 'FAILED'}", flush=True)
        if result.returncode:
            continue
        root = json.loads(built.read_text())
    nodes = root.get('endpoints', []) + root.get('outbounds', [])
    nodes = [n for n in nodes if n.get('type') not in ('selector', 'urltest', 'balancer', 'direct', 'block', 'dns')]
    for index, node in enumerate(nodes):
        candidates = [node]
        if entry['warp']:
            candidates = [json.loads((directory / filename).read_text())['endpoints'][0]
                          for filename in entry['variants']] or [node]
        success = False
        for attempt, candidate in enumerate(candidates):
            tag = candidate['tag']
            config = {'log': {'disabled': True}, 'inbounds': [{'type': 'mixed', 'tag': 'audit-in',
                      'listen': '127.0.0.1', 'listen_port': 18991}],
                      'route': {'final': tag},
                      'endpoints' if candidate['type'] in ('awg', 'wireguard', 'warp') else 'outbounds': [candidate]}
            if not entry['warp'] and label != 'Opera':
                config = copy.deepcopy(root)
                config['log'] = {'disabled': True}
                config['inbounds'] = [{'type': 'mixed', 'tag': 'audit-in', 'listen': '127.0.0.1', 'listen_port': 18991}]
                config.setdefault('route', {}).update(final=tag, rules=[])
                config.pop('experimental', None)
            live = directory / 'live.json'
            live.write_text(json.dumps(config))
            errors = tempfile.TemporaryFile()
            process = subprocess.Popen([core, 'srun', '-c', str(live)], stdout=errors, stderr=errors)
            try:
                for _ in range(40):
                    if process.poll() is not None:
                        break
                    try:
                        with socket.create_connection(('127.0.0.1', 18991), timeout=.1):
                            break
                    except OSError:
                        time.sleep(.1)
                if process.poll() is None:
                    result = subprocess.run(['curl', '--silent', '--output', '/dev/null', '--write-out', '%{http_code}',
                              '--max-time', '8', '--noproxy', '', '--socks5', '127.0.0.1:18991',
                              '--resolve', f'www.gstatic.com:443:{target_ip}', 'https://www.gstatic.com/generate_204'],
                              capture_output=True, timeout=10)
                    success = result.returncode == 0 and result.stdout == b'204'
                reason = ''
                if process.poll() is not None:
                    errors.seek(0)
                    diagnostic = errors.read().decode(errors='replace').lower()
                    categories = {
                        'registration': ('registration', 'register', 'warp account'),
                        'DNS': ('dns', 'lookup'),
                        'dependency': ('not found', 'detour', 'outbound'),
                        'permission': ('operation not permitted', 'permission denied'),
                        'format': ('unknown field', 'invalid character', 'unmarshal'),
                    }
                    reason = ' / startup ' + next((category for category, terms in categories.items()
                              if any(term in diagnostic for term in terms)), 'failed')
                print(f"TRAFFIC {label} node {index+1}, candidate {attempt+1}: {'OK' if success else 'FAILED'}{reason}", flush=True)
                if success:
                    break
            finally:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
                errors.close()
        # Large community feeds: validate all, probe a bounded sample explicitly.
        if not entry['warp'] and label != 'Opera' and len(nodes) > 16 and index >= 2:
            print(f"SAMPLE {label}: tested {index+1} of {len(nodes)} nodes", flush=True)
            break
