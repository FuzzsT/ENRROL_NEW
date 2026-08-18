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

include(
    ":core-model",
    ":core-execution",
    ":platform-compat",
    ":policy-core",
    ":policy-android",
    ":permission-manager",
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
    ":app-dpc",
    ":lab-tools"
)
