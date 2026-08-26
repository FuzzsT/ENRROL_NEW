# Build fix: hidden UserHandle user-id APIs

GitHub Actions reached Kotlin compilation and failed because `UserHandle.myUserId()` / `UserHandle.getUserId()` are hidden framework APIs and are not available in the public Android SDK used by AGP.

This checkpoint replaces all such calls with `AndroidUserId.fromUid(uid)`, matching AOSP's user-id extraction (`uid / 100000`). It also keeps the latest CI workflow fixes for Android SDK setup and the executable Gradle wrapper.

Run:

    ./gradlew :app-dpc:assembleEnterpriseDebug

The provisioning QR task remains finalized after the APK assemble task.
