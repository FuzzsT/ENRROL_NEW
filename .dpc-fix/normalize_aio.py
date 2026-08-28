from pathlib import Path
p = Path('.github/workflows/build-aio-enrollment.yml')
text = p.read_text('utf-8')
actual = 'check dist/work-profile-validation.json --json dist/work-profile-provisioning.json --qr dist/work-profile-qr.png --apk "dist//work-profile-provisioning.json --qr dist/work-profile-qr.png --apk "dist/$DPC_AIO_RELEASE_APK_NAME" --apksigner "$signer" --expected-apk-url "$DPC_AIO_PROVISIONING_APK_URL" --expected-mode work-profile'
normalized = 'check dist/work-profile-validation.json --json dist/work-profile-provisioning.json --qr dist//work-profile-provisioning.json --qr dist/work-profile-qr.png --apk "dist/$DPC_AIO_RELEASE_APK_NAME" --apksigner "$signer" --expected-apk-url "$DPC_AIO_PROVISIONING_APK_URL" --expected-mode work-profile'
count = text.count(actual)
if count != 1:
    raise SystemExit(f'normalize AIO: expected one current malformed validator, found {count}')
p.write_text(text.replace(actual, normalized, 1), 'utf-8')
print('NORMALIZE_AIO_CURRENT: PASS')
