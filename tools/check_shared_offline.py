"""Conservative lexical source policy for the shared library, not a network firewall.

The existing production-app policy remains separate and unchanged. Harness/test
I/O is permitted only outside the library's Main source sets. No physical evidence.
"""
from pathlib import Path
import re
import sys
ROOT = Path(__file__).resolve().parents[1]
ALLOWED = {'kotlin.concurrent.atomics.AtomicInt', 'kotlin.concurrent.atomics.AtomicLong',
           'kotlin.time.Instant', 'kotlin.math.roundToLong', 'kotlinx.serialization.Serializable',
           'kotlinx.serialization.json.Json', 'kotlinx.serialization.json.*', 'kotlinx.cinterop.*',
           'platform.CoreCrypto.*', 'platform.Foundation.NSData', 'java.security.MessageDigest'}
FORBIDDEN = re.compile(r'\b(?:java\.net|java\.io|okhttp\w*|ktor|URLSession\w*|NSURLSession\w*|NSURLRequest|NSURLConnection|URLRequest|NSURL|NSFileManager|File|FileInputStream|ProcessBuilder|Runtime|Socket|socket|connect|sendto|recvfrom|getaddrinfo|fopen|open|read|write|contentsOfFile|contentsOfURL)\b')

def issues(text):
    result = []
    for number, line in enumerate(text.splitlines(), 1):
        if FORBIDDEN.search(line): result.append((number, 'unreviewed file/network/process API'))
        for module in re.findall(r'^\s*import\s+([\w.*]+)', line):
            if module not in ALLOWED: result.append((number, 'unreviewed import: '+module))
        for name in re.findall(r'platform\.posix\.(\w+)', line):
            if name != 'memcpy': result.append((number, 'unreviewed POSIX API: '+name))
    return result

def check():
    failures = []
    sources = ROOT/'Shared/record-engine/src'
    for directory in sources.iterdir():
        if directory.name.endswith('Main'):
            for path in directory.rglob('*.kt'):
                failures += [f'{path}:{line}: {message}' for line, message in issues(path.read_text())]
    dependencies = re.findall(r'implementation\("([^"\n]+)"\)', (ROOT/'Shared/record-engine/build.gradle.kts').read_text())
    if dependencies != ['org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0']: failures.append('Unreviewed shared runtime dependency')
    return failures

if __name__ == '__main__':
    if sys.argv[1:] == ['--self-test']:
        for value in ['import java.net.URL', 'java.io.File("x")', 'platform.posix.socket(0,0,0)', 'NSURLSession.sharedSession', 'import unknown.Library']:
            assert issues(value), value
        for value in ['import platform.Foundation.NSData', 'platform.posix.memcpy(target, data.bytes, data.length)', 'val reference = "https://example.invalid"']:
            assert not issues(value), value
        print('Shared offline policy self-test passed.')
    elif sys.argv[1:]: sys.exit('Usage: check_shared_offline.py [--self-test]')
    else:
        failures = check()
        if failures: sys.exit('\n'.join(failures))
        print('Shared offline source policy passed (not a runtime network block).')
