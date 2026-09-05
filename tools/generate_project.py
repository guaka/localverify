"""Generate the dependency-free Xcode project; run from any working directory."""
from pathlib import Path
import json
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--local-only', action='store_true', help='Build without App Groups or share extension')
parser.add_argument('--ui-tests', action='store_true', help='Include simulator UI regression tests')
args = parser.parse_args()

root = Path(__file__).resolve().parents[1]
objects = {}
def add(key, isa, **values):
    objects[key] = dict(isa=isa, **values)
    return key
def q(value):
    if isinstance(value, dict):
        return '{ ' + ' '.join(f'{q(k)} = {q(v)};' for k, v in value.items()) + ' }'
    if isinstance(value, list):
        return '( ' + ', '.join(q(v) for v in value) + ' )'
    return json.dumps(str(value))

add('APPFILES', 'PBXFileSystemSynchronizedRootGroup', path='iOS/App', sourceTree='<group>')
add('COREFILES', 'PBXFileSystemSynchronizedRootGroup', path='Sources/TriageCore', sourceTree='<group>')
add('SHAREFILES', 'PBXFileSystemSynchronizedRootGroup', path='iOS/Share', sourceTree='<group>')
add('APPPRODUCT', 'PBXFileReference', explicitFileType='wrapper.application', path='Triage.app', sourceTree='BUILT_PRODUCTS_DIR')
add('SHAREPRODUCT', 'PBXFileReference', explicitFileType='wrapper.app-extension', path='TriageShare.appex', sourceTree='BUILT_PRODUCTS_DIR')
add('PRODUCTS', 'PBXGroup', children=['APPPRODUCT', 'SHAREPRODUCT'], name='Products', sourceTree='<group>')
add('MAIN', 'PBXGroup', children=['APPFILES', 'COREFILES', 'SHAREFILES', 'PRODUCTS'], sourceTree='<group>')
add('EMBEDFILE', 'PBXBuildFile', fileRef='SHAREPRODUCT', settings={'ATTRIBUTES': ['RemoveHeadersOnCopy']})
add('EMBED', 'PBXCopyFilesBuildPhase', buildActionMask=2147483647, dstPath='', dstSubfolderSpec=13, files=['EMBEDFILE'], runOnlyForDeploymentPostprocessing=0)
add('PROXY', 'PBXContainerItemProxy', containerPortal='PROJECT', proxyType=1, remoteGlobalIDString='SHARE', remoteInfo='TriageShare')
add('DEPENDENCY', 'PBXTargetDependency', target='SHARE', targetProxy='PROXY')
add('LICENSEFILE', 'PBXFileReference', lastKnownFileType='text', path='LICENSE', sourceTree='<group>')
objects['MAIN']['children'].append('LICENSEFILE')
add('LICENSEBUILD', 'PBXBuildFile', fileRef='LICENSEFILE')
add('APPRESOURCES', 'PBXResourcesBuildPhase', buildActionMask=2147483647, files=['LICENSEBUILD'], runOnlyForDeploymentPostprocessing=0)
for key, name, groups, product in [('APP', 'Triage', ['APPFILES', 'COREFILES'], 'APPPRODUCT'), ('SHARE', 'TriageShare', ['SHAREFILES'], 'SHAREPRODUCT')]:
    add(key+'SOURCES', 'PBXSourcesBuildPhase', buildActionMask=2147483647, files=[], runOnlyForDeploymentPostprocessing=0)
    add(key+'FRAMEWORKS', 'PBXFrameworksBuildPhase', buildActionMask=2147483647, files=[], runOnlyForDeploymentPostprocessing=0)
    configs = []
    for mode in ['Debug', 'Release']:
        settings = dict(SDKROOT='iphoneos', IPHONEOS_DEPLOYMENT_TARGET='17.0', SWIFT_VERSION='5.0', TARGETED_DEVICE_FAMILY='1,2', CODE_SIGN_STYLE='Automatic', GENERATE_INFOPLIST_FILE='NO', PRODUCT_NAME='$(TARGET_NAME)', CURRENT_PROJECT_VERSION='1', MARKETING_VERSION='0.1.0', SWIFT_OPTIMIZATION_LEVEL='-Onone' if mode == 'Debug' else '-O', SWIFT_ACTIVE_COMPILATION_CONDITIONS='DEBUG' if mode == 'Debug' else '', ENABLE_USER_SCRIPT_SANDBOXING='YES')
        settings.update(PRODUCT_BUNDLE_IDENTIFIER='org.mobiletriage.private' + ('.share' if key == 'SHARE' else ''), INFOPLIST_FILE='iOS/'+('Share' if key == 'SHARE' else 'App')+'-Info.plist', CODE_SIGN_ENTITLEMENTS='iOS/Triage.entitlements')
        if key == 'APP':
            settings.update(SWIFT_INCLUDE_PATHS='$(SRCROOT)/Sources/CZlib', OTHER_LDFLAGS=['$(inherited)', '-lz'], ASSETCATALOG_COMPILER_APPICON_NAME='AppIcon', ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME='AccentColor')
        else:
            settings.update(APPLICATION_EXTENSION_API_ONLY='YES', SKIP_INSTALL='YES')
        configs.append(add(key+mode, 'XCBuildConfiguration', name=mode, buildSettings=settings))
    add(key+'CONFIG', 'XCConfigurationList', buildConfigurations=configs, defaultConfigurationIsVisible=0, defaultConfigurationName='Debug')
    add(key, 'PBXNativeTarget', name=name, productName=name, productReference=product, productType='com.apple.product-type.app-extension' if key == 'SHARE' else 'com.apple.product-type.application', buildConfigurationList=key+'CONFIG', buildPhases=[key+'SOURCES', key+'FRAMEWORKS'] + (['APPRESOURCES', 'EMBED'] if key == 'APP' else []), dependencies=['DEPENDENCY'] if key == 'APP' else [], fileSystemSynchronizedGroups=groups)
for mode in ['Debug', 'Release']:
    add('PROJECT'+mode, 'XCBuildConfiguration', name=mode, buildSettings={'CLANG_ENABLE_MODULES': 'YES'})
add('PROJECTCONFIG', 'XCConfigurationList', buildConfigurations=['PROJECTDebug', 'PROJECTRelease'], defaultConfigurationIsVisible=0, defaultConfigurationName='Debug')
add('PROJECT', 'PBXProject', attributes={'LastUpgradeCheck': '2600'}, buildConfigurationList='PROJECTCONFIG', compatibilityVersion='Xcode 16.0', developmentRegion='en', knownRegions=['en', 'Base'], mainGroup='MAIN', productRefGroup='PRODUCTS', projectDirPath='', projectRoot='', targets=['APP', 'SHARE'])
# Stable, valid 24-digit object identifiers.
if args.local_only:
    objects['PROJECT']['targets'] = ['APP']
    objects['APP']['dependencies'] = []
    objects['APP']['buildPhases'] = ['APPSOURCES', 'APPFRAMEWORKS', 'APPRESOURCES']
    objects['MAIN']['children'].remove('SHAREFILES')
    objects['PRODUCTS']['children'] = ['APPPRODUCT']
    for mode in ['Debug', 'Release']:
        settings = objects['APP'+mode]['buildSettings']
        settings['CODE_SIGN_ENTITLEMENTS'] = ''
        settings['SWIFT_ACTIVE_COMPILATION_CONDITIONS'] += ' LOCAL_ONLY'
if args.ui_tests:
    add('UIFILES', 'PBXFileSystemSynchronizedRootGroup', path='iOS/UITests', sourceTree='<group>')
    objects['MAIN']['children'].append('UIFILES')
    add('UIPRODUCT', 'PBXFileReference', explicitFileType='wrapper.cfbundle', path='TriageUITests.xctest', sourceTree='BUILT_PRODUCTS_DIR')
    objects['PRODUCTS']['children'].append('UIPRODUCT')
    add('UISOURCES', 'PBXSourcesBuildPhase', buildActionMask=2147483647, files=[], runOnlyForDeploymentPostprocessing=0)
    add('UIPROXY', 'PBXContainerItemProxy', containerPortal='PROJECT', proxyType=1, remoteGlobalIDString='APP', remoteInfo='Triage')
    add('UIDEPENDENCY', 'PBXTargetDependency', target='APP', targetProxy='UIPROXY')
    for mode in ['Debug', 'Release']:
        add('UI'+mode, 'XCBuildConfiguration', name=mode, buildSettings=dict(SDKROOT='iphoneos', IPHONEOS_DEPLOYMENT_TARGET='17.0', SWIFT_VERSION='5.0', TARGETED_DEVICE_FAMILY='1,2', PRODUCT_NAME='$(TARGET_NAME)', PRODUCT_BUNDLE_IDENTIFIER='org.mobiletriage.uitests', GENERATE_INFOPLIST_FILE='YES', TEST_TARGET_NAME='Triage', CODE_SIGN_STYLE='Automatic'))
    add('UICONFIG', 'XCConfigurationList', buildConfigurations=['UIDebug', 'UIRelease'], defaultConfigurationIsVisible=0, defaultConfigurationName='Debug')
    add('UITEST', 'PBXNativeTarget', name='TriageUITests', productName='TriageUITests', productReference='UIPRODUCT', productType='com.apple.product-type.bundle.ui-testing', buildConfigurationList='UICONFIG', buildPhases=['UISOURCES'], dependencies=['UIDEPENDENCY'], fileSystemSynchronizedGroups=['UIFILES'])
    objects['PROJECT']['targets'].append('UITEST')
ids = {key: f'{i:024X}' for i, key in enumerate(objects, 1)}
def refs(value):
    if isinstance(value, list): return [refs(v) for v in value]
    if isinstance(value, dict): return {k: refs(v) for k, v in value.items()}
    return ids.get(value, value) if isinstance(value, str) else value
project = {'archiveVersion': 1, 'classes': {}, 'objectVersion': 77, 'objects': {ids[k]: refs(v) for k,v in objects.items()}, 'rootObject': ids['PROJECT']}
folder = root / ('TriageLocal.xcodeproj' if args.local_only else 'Triage.xcodeproj')
folder.mkdir(exist_ok=True)
(folder / 'project.pbxproj').write_text('// !$*UTF8*$!\n' + q(project) + '\n')
print(folder)
if args.ui_tests:
    scheme_dir = folder / 'xcshareddata' / 'xcschemes'
    scheme_dir.mkdir(parents=True, exist_ok=True)
    def build_ref(key, name, product):
        return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{ids[key]}" BuildableName="{product}" BlueprintName="{name}" ReferencedContainer="container:{folder.name}"/>'
    app = build_ref('APP', 'Triage', 'Triage.app')
    tests = build_ref('UITEST', 'TriageUITests', 'TriageUITests.xctest')
    (scheme_dir / 'TriageChecks.xcscheme').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="2600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries>
<BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="YES">{app}</BuildActionEntry>
<BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="YES">{tests}</BuildActionEntry>
</BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">{tests}</TestableReference></Testables></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{app}</BuildableProductRunnable></LaunchAction>
</Scheme>''')
