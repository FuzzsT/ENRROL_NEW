# Known DPC-AIO CI failure patterns

These patterns were observed in prior DPC-AIO builds. Always verify the current log and source before applying any fix.

## `yes | sdkmanager --licenses` exits non-zero under `pipefail`

Symptom: `yes: standard output: Broken pipe` with shell `-o pipefail` and exit 1. Root cause is SIGPIPE from `yes` after `sdkmanager` closes stdin. Prefer `android-actions/setup-android` license handling instead of a manual `yes` pipeline.

## Preview Android platform package not found

Symptom: `Warning: Failed to find package 'platforms;android-37'`. Verify the current SDK repository. For preview APIs, discover the available package through `sdkmanager --list --channel=3` instead of assuming a stable channel package identifier.

## Gradle wrapper permission denied

Symptom: `./gradlew: Permission denied`, exit 126. Verify `gradlew` exists and run `chmod +x gradlew` before invoking it in CI. Also verify the wrapper JAR/properties exist.

## Hidden `UserHandle` helper fails public-SDK compilation

Symptom: Kotlin `Unresolved reference 'myUserId'` or `getUserId` on `android.os.UserHandle`. Verify the source still contains those hidden framework helpers before changing it. DPC-AIO's public-SDK-compatible helper derives the Android user ID from the UID; do not reintroduce hidden SDK references.

## Commit-SHA mismatch

Symptom: CI repeats an already-fixed source error. Check the run's commit SHA and inspect the exact file at that SHA. A GitHub Actions rerun executes the same commit; push a new commit when the source fix was not present.
