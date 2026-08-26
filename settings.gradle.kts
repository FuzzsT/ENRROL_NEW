pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DPC-AIO"

// Keep stable Gradle project IDs while storing sources in domain-oriented folders.
include(
    ":core-model",
    ":core-execution",
    ":enterprise-protection",
    ":platform-compat",
    ":policy-core",
    ":policy-android",
    ":permission-manager",
    ":offline-core",
    ":offline-android",
    ":samsung-settings",
    ":samsung-settings-android",
    ":account-manager",
    ":account-android",
    ":permission-android",
    ":app-manager",
    ":app-android",
    ":activity-launcher",
    ":activity-android",
    ":installer-core",
    ":installer-android",
    ":delegation-core",
    ":dhizuku-compat",
    ":shizuku-adapter",
    ":knox-license-core",
    ":knox-official-core",
    ":knox-official-android",
    ":knox-license-lab",
    ":knox-mock-android",
    ":knox-mock-core",
    ":knox-zt-core",
    ":knox-zt-android",
    ":native-diagnostics",
    ":network-android",
    ":network-control",
    ":scenario-core",
    ":scenario-android",
    ":nfc-lab-core",
    ":nfc-lab-android",
    ":oem-internals-core",
    ":oem-internals-android",
    ":app-dpc",
    ":aio-test-target",
    ":lab-tools"
)

project(":app-dpc").projectDir = file("apps/dpc/app")
project(":core-model").projectDir = file("apps/dpc/modules/core/model")
project(":core-execution").projectDir = file("apps/dpc/modules/core/execution")
project(":enterprise-protection").projectDir = file("apps/dpc/modules/enterprise-protection/core")
project(":platform-compat").projectDir = file("apps/dpc/modules/platform/compat")
project(":policy-core").projectDir = file("apps/dpc/modules/policy/core")
project(":policy-android").projectDir = file("apps/dpc/modules/policy/android")
project(":permission-manager").projectDir = file("apps/dpc/modules/permissions/core")
project(":offline-core").projectDir = file("apps/dpc/modules/offline/core")
project(":offline-android").projectDir = file("apps/dpc/modules/offline/android")
project(":permission-android").projectDir = file("apps/dpc/modules/permissions/android")
project(":samsung-settings").projectDir = file("apps/dpc/modules/samsung/core")
project(":samsung-settings-android").projectDir = file("apps/dpc/modules/samsung/android")
project(":account-manager").projectDir = file("apps/dpc/modules/account/core")
project(":account-android").projectDir = file("apps/dpc/modules/account/android")
project(":app-manager").projectDir = file("apps/dpc/modules/app-management/core")
project(":app-android").projectDir = file("apps/dpc/modules/app-management/android")
project(":activity-launcher").projectDir = file("apps/dpc/modules/activity/core")
project(":activity-android").projectDir = file("apps/dpc/modules/activity/android")
project(":installer-core").projectDir = file("apps/dpc/modules/installer/core")
project(":installer-android").projectDir = file("apps/dpc/modules/installer/android")
project(":delegation-core").projectDir = file("apps/dpc/modules/delegation/core")
project(":dhizuku-compat").projectDir = file("apps/dpc/integrations/dhizuku")
project(":shizuku-adapter").projectDir = file("apps/dpc/integrations/shizuku")
project(":knox-license-core").projectDir = file("apps/dpc/modules/knox/license/core")
project(":knox-official-core").projectDir = file("apps/dpc/modules/knox/official/core")
project(":knox-official-android").projectDir = file("apps/dpc/modules/knox/official/android")
project(":knox-license-lab").projectDir = file("apps/dpc/lab/knox-license")
project(":knox-mock-core").projectDir = file("apps/dpc/modules/knox/mock/core")
project(":knox-mock-android").projectDir = file("apps/dpc/modules/knox/mock/android")
project(":knox-zt-core").projectDir = file("apps/dpc/modules/knox/zero-trust/core")
project(":knox-zt-android").projectDir = file("apps/dpc/modules/knox/zero-trust/android")
project(":native-diagnostics").projectDir = file("apps/dpc/integrations/native-diagnostics")
project(":network-control").projectDir = file("apps/dpc/modules/network/core")
project(":network-android").projectDir = file("apps/dpc/modules/network/android")
project(":scenario-core").projectDir = file("apps/dpc/modules/scenario/core")
project(":scenario-android").projectDir = file("apps/dpc/modules/scenario/android")
project(":nfc-lab-core").projectDir = file("apps/dpc/modules/nfc-lab/core")
project(":nfc-lab-android").projectDir = file("apps/dpc/modules/nfc-lab/android")
project(":oem-internals-core").projectDir = file("apps/dpc/modules/oem-internals/core")
project(":oem-internals-android").projectDir = file("apps/dpc/modules/oem-internals/android")
project(":offline-core").projectDir = file("apps/dpc/modules/offline/core")
project(":offline-android").projectDir = file("apps/dpc/modules/offline/android")
project(":aio-test-target").projectDir = file("apps/aio-test-target")
project(":lab-tools").projectDir = file("apps/dpc/lab/tools")
