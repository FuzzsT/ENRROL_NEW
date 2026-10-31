from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
ENG = APP / 'engineering'
ACTIVITY_CORE = ROOT / 'apps/dpc/modules/activity/core/src/main/kotlin/io/dpcaio/activity'


def need(path: Path, needles):
    assert path.exists(), f'missing {path.relative_to(ROOT)}'
    text = path.read_text(encoding='utf-8')
    for needle in needles:
        assert needle in text, f'{path.name}: missing {needle}'
    return text


broadcast = need(ENG / 'BroadcastIntentBuilder.kt', [
    'data class BroadcastRequest',
    'sealed interface BroadcastExtra',
    'Intent(request.action)',
    'setPackage',
    'ComponentName',
    'setData',
    'putExtra',
])
assert 'Runtime.getRuntime' not in broadcast
assert 'Shizuku.newProcess' not in broadcast
assert 'am broadcast' not in broadcast

ussd = need(ENG / 'UssdIntentFactory.kt', [
    'Intent.ACTION_DIAL',
    'Intent.ACTION_CALL',
    'Uri.fromParts("tel"',
])
assert 'sendBroadcast' not in ussd

catalog = need(ENG / 'SamsungSecretCodeCatalog.kt', [
    'enum class SecretCodeRisk',
    'SAFE_INFO',
    'DIAGNOSTIC',
    'SETTINGS',
    'UNKNOWN_RISK',
    'executable',
])

discovery = need(ENG / 'SamsungSecretCodeDiscovery.kt', [
    'SamsungSecretCodeCatalog.normalize',
    'SecretCodeRuntimeVerifier',
    'queryReceivers',
    'requiredPermission',
])
verifier = need(ENG / 'SecretCodeRuntimeVerifier.kt', [
    'TelephonyManager.ACTION_SECRET_CODE',
    'android.provider.Telephony.SECRET_CODE',
    'android_secret_code://',
    'queryBroadcastReceivers',
    'PackageManager.MATCH_DISABLED_COMPONENTS',
    'requiredPermission',
])
for text in (discovery, verifier):
    assert 'Runtime.getRuntime' not in text
    assert 'Shizuku.newProcess' not in text
    assert 'am broadcast' not in text
    assert 'sendBroadcast(' not in text

center = need(APP / 'EngineeringIntentCenterActivity.kt', [
    'Broadcast Lab',
    'USSD / Dial Lab',
    'Samsung Secret Code Center',
    'Developer Activities',
    'sendBroadcast',
    'ACTION_DIAL',
    'CALL_PHONE',
    'SamsungSecretCodeDiscovery',
    'DeveloperActivitiesActivity::class.java',
    'Open calculator',
    'Intent.CATEGORY_APP_CALCULATOR',
    'Signature Code Lab',
    'SignatureCodeLabActivity::class.java',
])
assert 'Runtime.getRuntime' not in center
assert 'Shizuku.newProcess' not in center
assert 'am broadcast' not in center

classifier = need(ACTIVITY_CORE / 'DeveloperActivityClassifier.kt', [
    'object DeveloperActivityClassifier',
    'fun isDeveloperLike',
    'debug',
    'developer',
    'engineering',
    'diagnostic',
    'test',
])

developer = need(APP / 'DeveloperActivitiesActivity.kt', [
    'Hidden Developer Activities',
    'AndroidActivityInventory',
    'DeveloperActivityClassifier::isDeveloperLike',
    'exported=',
    'permission=',
    'ActivityExplorerActivity::class.java',
    'Choose package',
    'Choose user',
    'RuntimeTargetPicker.showPackagePicker',
    'RuntimeTargetPicker.showUserPicker',
])

manifest = need(ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml', [
    '.EngineeringIntentCenterActivity',
    '.DeveloperActivitiesActivity',
    '.SignatureCodeLabActivity',
])

dashboard = need(APP / 'AioDashboardActivity.kt', [
    'Engineering Intent Center',
    'EngineeringIntentCenterActivity::class.java',
])

registry = need(APP / 'DpcModuleRegistry.kt', [
    'Engineering Intent Center',
    'EngineeringIntentCenterActivity::class.java',
])

print('test_engineering_intent_center_contract: PASS')
