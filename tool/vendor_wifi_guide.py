#!/usr/bin/env python3
"""Port the last Dart guide's data expressions; no network or Dart runtime required."""
import json
import re
import subprocess
from pathlib import Path

REFERENCE = '8d8655916b115417e1f736966108c29ca87ba530'
GUIDE = 'lib/features/settings/widget/wifi_sharing_guide.dart'
PAGE = 'lib/features/settings/widget/wifi_sharing_instructions_page.dart'
TARGET = Path('android/app/src/main/kotlin/com/hiddify/hiddify/nativeui/NativeWifiGuideContent.kt')

def source(path):
    return subprocess.check_output(['git', 'show', f'{REFERENCE}:{path}'], text=True)

class Expressions:
    def __init__(self, text):
        self.tokens = re.findall(r"'(?:\\.|[^'\\])*'|\.\.\.|[A-Za-z_][A-Za-z_0-9]*|[0-9]+|[^\s]", text)
        self.i = 0
    def take(self, token=None):
        actual = self.tokens[self.i]
        if token is not None and actual != token:
            raise ValueError((token, actual, self.tokens[self.i:self.i + 10]))
        self.i += 1
        return actual
    def peek(self):
        return self.tokens[self.i]
    def expression(self):
        token = self.take()
        if token == 'const':
            return self.expression()
        if token.startswith("'"):
            value = token[1:-1].replace("\\'", "'").replace('\\n', '\n')
            result = json.dumps(value, ensure_ascii=False)
        elif token == '[':
            values = []
            while self.peek() != ']':
                if self.peek() == 'if':
                    self.take(); self.take('('); self.take('!'); self.take('root'); self.take(')')
                    values.append('*(if (!root) listOf(' + self.expression() + ') else emptyList()).toTypedArray()')
                else:
                    values.append(self.expression())
                if self.peek() == ',': self.take()
            self.take(']')
            result = 'listOf(\n' + ',\n'.join(values) + '\n)'
        elif token == '{':
            pairs, spreads = [], []
            while self.peek() != '}':
                if self.peek() == '...':
                    self.take(); spreads.append(self.expression())
                else:
                    key = self.expression(); self.take(':')
                    pairs.append(key + ' to ' + self.expression())
                if self.peek() == ',': self.take()
            self.take('}')
            result = 'linkedMapOf(' + ',\n'.join(pairs) + ')' + ''.join(' + ' + spread for spread in spreads)
        elif self.peek() == '(':
            self.take('('); args = []
            while self.peek() != ')':
                name = ''
                if self.tokens[self.i + 1] == ':': name = self.take() + ' = '; self.take(':')
                args.append(name + self.expression())
                if self.peek() == ',': self.take()
            self.take(')')
            result = ('NativeWifiGuideSection' if token == 'SharingGuideSection' else token) + '(' + ',\n'.join(args) + ')'
        else:
            result = token
        if self.peek() == '?':
            self.take('?'); positive = self.expression(); self.take(':'); negative = self.expression()
            result = f'(if ({result}) {positive} else {negative})'
        if self.peek() == '[':
            self.take('['); index = self.expression(); self.take(']'); result += f'[{index}]'
        return result

def expression(text, offset):
    return Expressions(text[offset:]).expression()

def main():
    guide, page = source(GUIDE), source(PAGE)
    def variable(name):
        offset = guide.index(f'final {name} = ') + len(f'final {name} = ')
        return expression(guide, offset)
    parts = ['''package com.hiddify.hiddify.nativeui

// Generated from Dart 8d86559 by tool/vendor_wifi_guide.py. Keep all source wording.
internal data class NativeWifiGuideSection(val title: String, val steps: List<String>,
    val screenTitle: String? = null, val fields: Map<String, String> = emptyMap())

internal fun nativeWifiGuide(platform: Int, ru: Boolean, host: String, port: Int,
    root: Boolean, ssid: String): List<NativeWifiGuideSection> {
    require(platform in 0..4)
    fun t(russian: String, english: String) = if (ru) russian else english
''']
    for name in ('on', 'manual', 'auth', 'connect', 'proxyPath'):
        parts.append(f'    val {name} = {variable(name)}\n')
    offset = guide.index('return [') + len('return ')
    parts.append('    val sections = if (root) ' + expression(guide, offset) + ' else {\n')
    parts.append('    val address = ' + variable('address') + '\n    val client = when (platform) {\n')
    for index, match in enumerate(re.finditer(r'client = (SharingGuideSection\()', guide)):
        parts.append(('else' if index == 4 else str(index)) + ' -> ' + expression(guide, match.start(1)) + '\n')
    parts.append('    }\n    listOf(connect, address, client)\n    }\n    return sections + listOf(\n')
    for match in re.finditer(r'section: (SharingGuideSection\()', page):
        parts.append(expression(page, match.start(1)) + ',\n')
    parts.append('    )\n}\n')
    TARGET.write_text(''.join(parts))
    print(f'Vendored all guide expressions from {REFERENCE}')

if __name__ == '__main__': main()
