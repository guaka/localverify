#!/usr/bin/env python3
"""Build and verify the shared record module. Device runs accept disposable virtual devices only."""
import argparse, json, os, pathlib, subprocess, time
ROOT = pathlib.Path(__file__).resolve().parents[1]
HERE = ROOT / 'Shared'
OUT = HERE / 'build'
OUT.mkdir(exist_ok=True)
ENV = os.environ.copy()
ENV.setdefault('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')
SDK = pathlib.Path(ENV.get('ANDROID_HOME', str(pathlib.Path.home() / 'Library/Android/sdk')))
if not (SDK/'platform-tools/adb').is_file() and (SDK/'sdk/platform-tools/adb').is_file(): SDK = SDK/'sdk'
ENV['ANDROID_HOME'] = str(SDK)
ENV['PATH'] = ENV['JAVA_HOME'] + '/bin:' + ENV.get('PATH', '')

def run(name, args):
    start = time.monotonic()
    log = OUT / (name + '.log')
    with log.open('w') as output:
        result = subprocess.run([str(x) for x in args], cwd=ROOT, env=ENV, stdout=output, stderr=subprocess.STDOUT, timeout=600)
    print(f'{name}: {time.monotonic()-start:.2f}s, exit {result.returncode}', flush=True)
    if result.returncode:
        print(log.read_text()[-8000:]); raise SystemExit(result.returncode)
    return log.read_text()

def swift():
    for platform, sdk, target, kotlin in [('simulator', 'iphonesimulator', 'arm64-apple-ios17.0-simulator', 'iosSimulatorArm64'), ('device', 'iphoneos', 'arm64-apple-ios17.0', 'iosArm64')]:
        run('swift-'+platform, ['xcrun', '--sdk', sdk, 'swiftc', '-O', '-target', target, '-F', HERE/f'record-engine/build/bin/{kotlin}/releaseFramework', '-framework', 'RecordEngine', HERE/'tests/swift/main.swift', '-o', OUT/('swift-'+platform)])

def build():
    run('shared-policy', ['python3', ROOT/'tools/check_shared_offline.py'])
    run('gradle', [ROOT/'Android/gradlew', '-p', HERE, ':record-engine:jvmTest', ':record-engine:linkDebugTestIosSimulatorArm64', ':record-engine:linkReleaseFrameworkIosSimulatorArm64', ':record-engine:linkReleaseFrameworkIosArm64', ':harness:assembleDebug', ':harness:assembleDebugAndroidTest', '--console=plain'])
    swift()

def ios(uuid):
    inventory = json.loads(subprocess.check_output(['xcrun', 'simctl', 'list', 'devices', '--json'], env=ENV))
    selected = [d for ds in inventory['devices'].values() for d in ds if d['udid'] == uuid]
    if not selected or selected[0]['name'] != 'LocalVerify Shared Engine Checks': raise SystemExit('Use a disposable simulator named LocalVerify Shared Engine Checks.')
    if selected[0]['state'] != 'Booted': run('simulator-boot', ['xcrun', 'simctl', 'boot', uuid])
    run('simulator-ready', ['xcrun', 'simctl', 'bootstatus', uuid, '-b'])
    run('native-tests', ['xcrun', 'simctl', 'spawn', uuid, HERE/'record-engine/build/bin/iosSimulatorArm64/debugTest/test.kexe'])
    output = run('ios', ['xcrun', 'simctl', 'spawn', uuid, OUT/'swift-simulator', ROOT/'Fixtures', HERE/'ThreatData'])
    (OUT/'ios.json').write_text(json.dumps(json.loads(output.strip().splitlines()[-1]), indent=2)+'\n')

def android(serial):
    if not serial.startswith('emulator-'): raise SystemExit('Only disposable emulators are accepted.')
    adb = [SDK/'platform-tools/adb', '-s', serial]
    name = subprocess.check_output([*map(str, adb), 'emu', 'avd', 'name'], env=ENV, text=True).splitlines()[0]
    if name != 'LocalVerify_Shared_Engine_Checks': raise SystemExit('Use an AVD named LocalVerify_Shared_Engine_Checks.')
    run('install', adb + ['install', '--no-streaming', '-r', HERE/'harness/build/outputs/apk/debug/harness-debug.apk'])
    run('install-tests', adb + ['install', '--no-streaming', '-r', HERE/'harness/build/outputs/apk/androidTest/debug/harness-debug-androidTest.apk'])
    output = run('android', adb + ['shell', 'am', 'instrument', '-w', '-r', 'org.localverify.sharedchecks.test/androidx.test.runner.AndroidJUnitRunner'])
    if 'FAILURES' in output or 'OK (1 test)' not in output: raise SystemExit('Instrumentation failed. See Shared/build/android.log')
    marker = 'INSTRUMENTATION_STATUS: sharedEngineMeasurements='
    values = [json.loads(line[len(marker):]) for line in output.splitlines() if line.startswith(marker)]
    if len(values) != 1: raise SystemExit('Missing shared-engine measurements')
    (OUT/'android.json').write_text(json.dumps(values[0], indent=2)+'\n')

p = argparse.ArgumentParser(); p.add_argument('stage', choices=['build', 'swift', 'ios', 'android']); p.add_argument('--simulator'); p.add_argument('--serial'); a = p.parse_args()
if a.stage == 'build': build()
elif a.stage == 'swift': swift()
elif a.stage == 'ios':
    if not a.simulator: p.error('--simulator required')
    ios(a.simulator)
else:
    if not a.serial: p.error('--serial required')
    android(a.serial)
