#!/usr/bin/env python3
"""Reproduce the isolated experiment. Uses only committed synthetic JSON fixtures."""
import argparse, json, os, pathlib, shutil, subprocess, time
ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE / 'build'
OUT.mkdir(exist_ok=True)
ENV = os.environ.copy()
ENV['OPENSPEC_TELEMETRY'] = '0'
SDK = pathlib.Path(ENV.get('ANDROID_HOME', str(pathlib.Path.home() / 'Library/Android/sdk')))
ENV['ANDROID_HOME'] = str(SDK)
if not ENV.get('JAVA_HOME'):
    ENV['JAVA_HOME'] = '/Applications/Android Studio.app/Contents/jbr/Contents/Home'
ENV['PATH'] = ENV['JAVA_HOME'] + '/bin:' + str(pathlib.Path.home()/'.cargo/bin') + ':' + ENV.get('PATH', '')
ENV['CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER'] = str(SDK / 'ndk/27.2.12479018/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android30-clang')
measurements = []
def run(name, args, cwd=HERE):
    path=OUT/(name+'.log'); start=time.monotonic()
    with path.open('w') as log:
        try:
            p=subprocess.run([str(x) for x in args], cwd=cwd, env=ENV, stdout=log, stderr=subprocess.STDOUT, timeout=600)
        except subprocess.TimeoutExpired:
            measurements.append({'step':name,'seconds':round(time.monotonic()-start,3),'error':'timeout'})
            (OUT/'commands.json').write_text(json.dumps(measurements,indent=2)+'\n')
            raise SystemExit('Timed out: '+name+'; see '+str(path))
    item={'step':name,'seconds':round(time.monotonic()-start,3),'exitCode':p.returncode}
    measurements.append(item)
    (OUT/'commands.json').write_text(json.dumps(measurements,indent=2)+'\n')
    print(json.dumps(item),flush=True)
    if p.returncode:
        print(path.read_text()[-8000:]);raise SystemExit(p.returncode)
    return path.read_text()
def gradle(name,*tasks):
    return run(name,[ROOT/'Android/gradlew','-p',HERE,*tasks,'--console=plain'])
def rust(name,*args): return run(name,['cargo',*args,'--locked'],cwd=HERE/'rust')
def build():
    rust('rust-host','build','--release')
    rust('rust-bindgen-build','build','--features','bindings','--bin','uniffi-bindgen')
    run('bindings',['target/debug/uniffi-bindgen','generate','--library','target/release/libtriage_experiment.dylib','--language','swift','--language','kotlin','--out-dir','../build/bindings'],cwd=HERE/'rust')
    rust('rust-mobile','build','--release','--target','aarch64-apple-ios-sim','--target','aarch64-apple-ios','--target','aarch64-linux-android')
    jni=OUT/'jniLibs/arm64-v8a';jni.mkdir(parents=True,exist_ok=True)
    shutil.copy2(HERE/'rust/target/aarch64-linux-android/release/libtriage_experiment.so',jni)
    gradle('kmp-mobile',':kmp:linkReleaseFrameworkIosSimulatorArm64',':kmp:linkReleaseFrameworkIosArm64',':kmp:jvmJar')
    gradle('android-build',':harness:assembleRustDebug',':harness:assembleKmpDebug',':harness:assembleBaselineDebug',':harness:assembleRustDebugAndroidTest',':harness:assembleKmpDebugAndroidTest')
    swift_build()
def swift_build():
    (OUT/'ios').mkdir(exist_ok=True)
    for platform,sdk,target,rust_target,kmp_target in [('simulator','iphonesimulator','arm64-apple-ios17.0-simulator','aarch64-apple-ios-sim','iosSimulatorArm64'),('device','iphoneos','arm64-apple-ios17.0','aarch64-apple-ios','iosArm64')]:
        base=['xcrun','--sdk',sdk,'swiftc','-O','-target',target]
        run('swift-rust-'+platform,base+['-Xcc','-fmodule-map-file='+str(OUT/'bindings/triage_experimentFFI.modulemap'),OUT/'bindings/triage_experiment.swift',HERE/'ios/main.swift',HERE/f'rust/target/{rust_target}/release/libtriage_experiment.a','-o',OUT/f'ios/rust-{platform}'])
        run('swift-kmp-'+platform,base+['-D','KMP','-F',HERE/f'kmp/build/bin/{kmp_target}/releaseFramework','-framework','KmpEngine',HERE/'ios/main.swift','-o',OUT/f'ios/kmp-{platform}'])
    baseline=OUT/'ios/baseline.swift';baseline.write_text('import Foundation\nprint("baseline")\n')
    run('swift-baseline',['xcrun','--sdk','iphonesimulator','swiftc','-O','-target','arm64-apple-ios17.0-simulator',baseline,'-o',OUT/'ios/baseline'])
def ios(device):
    inventory=json.loads(subprocess.check_output(['xcrun','simctl','list','devices','--json'],env=ENV))
    selected=[d for ds in inventory['devices'].values() for d in ds if d['udid']==device]
    if not selected or selected[0]['name']!='LocalVerify Engine Experiment': raise SystemExit('Use a disposable simulator named LocalVerify Engine Experiment.')
    if selected[0]['state'] != 'Booted':
        run('simulator-boot',['xcrun','simctl','boot',device])
    run('simulator-ready',['xcrun','simctl','bootstatus',device,'-b'])
    for engine in ['rust','kmp']:
        output=run('ios-'+engine,['xcrun','simctl','spawn',device,OUT/f'ios/{engine}-simulator',ROOT/'Fixtures'])
        result=json.loads(output.strip().splitlines()[-1]); (OUT/f'ios-{engine}.json').write_text(json.dumps(result,indent=2)+'\n')
def android(serial):
    if not serial.startswith('emulator-'): raise SystemExit('Only disposable emulator serials are accepted.')
    adb=[SDK/'platform-tools/adb','-s',serial]
    # Only install synthetic experiment packages, never inspect cases or pull app containers.
    for engine in ['rust','kmp']:
        app=HERE/f'harness/build/outputs/apk/{engine}/debug/harness-{engine}-debug.apk'
        tests=HERE/f'harness/build/outputs/apk/androidTest/{engine}/debug/harness-{engine}-debug-androidTest.apk'
        run('install-'+engine,adb+['install','--no-streaming','-r',app]);run('install-tests-'+engine,adb+['install','--no-streaming','-r',tests])
        output=run('android-'+engine,adb+['shell','am','instrument','-w','-r',f'org.localverify.experiment.{engine}.test/androidx.test.runner.AndroidJUnitRunner'])
        if 'FAILURES' in output or 'OK (1 test)' not in output: raise SystemExit('Instrumentation did not pass: '+engine)
        marker='INSTRUMENTATION_STATUS: engineMeasurements='
        values=[json.loads(line[len(marker):]) for line in output.splitlines() if line.startswith(marker)]
        if len(values)!=1: raise SystemExit('Missing measurements: '+engine)
        (OUT/f'android-{engine}.json').write_text(json.dumps(values[0],indent=2)+'\n')
def sizes():
    paths=list((OUT/'ios').glob('*-simulator'))+[OUT/'ios/baseline']+list((HERE/'harness/build/outputs/apk').glob('*/debug/*.apk'))
    result={str(p.relative_to(HERE)):p.stat().st_size for p in paths if p.is_file()}
    (OUT/'sizes.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
def cost():
    # Clean only the candidates' own library outputs. Dependency downloads stay cached.
    rust('rust-clean','clean')
    rust('rust-clean-build','build','--release','--target','aarch64-apple-ios-sim','--target','aarch64-apple-ios','--target','aarch64-linux-android')
    rust('rust-incremental-build','build','--release','--target','aarch64-apple-ios-sim','--target','aarch64-apple-ios','--target','aarch64-linux-android')
    gradle('kmp-clean',':kmp:clean')
    tasks=(':kmp:linkReleaseFrameworkIosSimulatorArm64',':kmp:linkReleaseFrameworkIosArm64',':kmp:jvmJar')
    gradle('kmp-clean-build',*tasks);gradle('kmp-incremental-build',*tasks)
    (OUT/'build-cost.json').write_text(json.dumps(measurements,indent=2)+'\n')
p=argparse.ArgumentParser();p.add_argument('stage',choices=['build','swift','ios','android','sizes','cost']);p.add_argument('--simulator');p.add_argument('--serial');a=p.parse_args()
if a.stage=='build':build()
elif a.stage=='swift':swift_build()
elif a.stage=='ios':
    if not a.simulator:p.error('--simulator is required')
    ios(a.simulator)
elif a.stage=='android':
    if not a.serial:p.error('--serial is required')
    android(a.serial)
elif a.stage=='sizes':sizes()
else:cost()
