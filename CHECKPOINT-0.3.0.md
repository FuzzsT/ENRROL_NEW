# DPC-AIO Development Checkpoint 0.3.0

Branch: `feature/delegation-provisioning-0.3`

## Added in 0.3.0

- Typed delegated-capability broker with package + UID + user + certificate + scope authorization.
- Safe Dhizuku-compatible provider/AIDL subset based on the uploaded MIT-licensed Dhizuku-API contracts.
  - Supports provider handshake, version/permission checks, and delegated-scope read/write through public DevicePolicyManager APIs.
  - Explicitly rejects raw Binder forwarding, arbitrary remote processes, and arbitrary Dhizuku user-service loading.
- Official Shizuku API/provider dependency target 13.1.5.
  - Binder/permission/UID/version runtime probe.
  - Typed Shizuku UserService for explicit activity start and explicit broadcast.
  - Shizuku ActivityRoute executor integrated with the Activity Engine.
- Provisioning server using Node.js built-ins only.
  - Cryptographically random one-time enrollment tokens.
  - TTL and consume-once semantics.
  - Android Enterprise provisioning payload builder using URL-safe base64 SHA-256 APK checksum.
  - HTTP health, enrollment creation, payload, and consume endpoints.
- Android provisioning activities for GET_PROVISIONING_MODE and ADMIN_POLICY_COMPLIANCE.
- Release gate extended to reject deprecated Shizuku newProcess and Dhizuku raw-forwarding implementation dependencies.

## Verification

Fresh `./tools/run_host_tests.sh` result:

- 15 Kotlin host executable tests: PASS.
- Android static contract tests: PASS.
- Dhizuku compatibility contract: PASS.
- Shizuku compatibility contract: PASS.
- Android provisioning contract: PASS.
- 3 Node provisioning tests: PASS.
- Project verifier: PASS.
- Release gate: PASS.
- Final status: `HOST_TEST_SUITE: PASS`.

## Android build status

Attempted:

```text
./gradlew :app-dpc:assembleEnterpriseDebug --offline
```

The Android APK build is not verified in this sandbox because the Gradle 9.7.0 distribution is not cached locally and DNS/network access to `services.gradle.org` fails with `java.net.UnknownHostException`.

No APK is claimed as built by this checkpoint.

## Security/release boundaries

`enterpriseRelease` does not include Xposed/LSPosed, HiddenApiBypass, arbitrary Dhizuku remote process execution, unrestricted Binder forwarding, or Shizuku arbitrary-command strings. Shizuku routes retain the actual shell/root identity returned by the official API.
