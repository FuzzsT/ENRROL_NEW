# DPC-AIO checkpoint 0.6.3

Release focus: continuous work-profile enrollment reliability and release consistency.

- versionCode 10 / versionName 0.6.3
- no `releases/latest/download` fallback in Gradle provisioning
- `dpc-aio-continuous` is the default only for enterpriseDebug
- other variants require an explicit provisioning APK URL
- continuous release publishes the complete provisioning artifact set
- public APK download must match the freshly built APK byte-for-byte
