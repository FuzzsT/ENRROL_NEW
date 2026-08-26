# DPC-AIO project layout

The repository uses **stable Gradle project IDs** with **domain-oriented physical directories**.
This keeps existing dependency declarations such as `project(":policy-core")` intact while avoiding
more than thirty unrelated application/module folders at repository root.

## Applications

| Gradle project | Directory | Purpose |
|---|---|---|
| `:app-dpc` | `apps/dpc/app` | Final DPC application and provisioning build outputs |

## Core and feature modules

| Gradle project | Directory |
|---|---|
| `:core-model` | `apps/dpc/modules/core/model` |
| `:core-execution` | `apps/dpc/modules/core/execution` |
| `:platform-compat` | `apps/dpc/modules/platform/compat` |
| `:policy-core` | `apps/dpc/modules/policy/core` |
| `:policy-android` | `apps/dpc/modules/policy/android` |
| `:permission-manager` | `apps/dpc/modules/permissions/core` |
| `:permission-android` | `apps/dpc/modules/permissions/android` |
| `:samsung-settings` | `apps/dpc/modules/samsung/core` |
| `:samsung-settings-android` | `apps/dpc/modules/samsung/android` |
| `:account-manager` | `apps/dpc/modules/account/core` |
| `:account-android` | `apps/dpc/modules/account/android` |
| `:app-manager` | `apps/dpc/modules/app-management/core` |
| `:app-android` | `apps/dpc/modules/app-management/android` |
| `:activity-launcher` | `apps/dpc/modules/activity/core` |
| `:activity-android` | `apps/dpc/modules/activity/android` |
| `:installer-core` | `apps/dpc/modules/installer/core` |
| `:installer-android` | `apps/dpc/modules/installer/android` |
| `:delegation-core` | `apps/dpc/modules/delegation/core` |
| `:knox-license-core` | `apps/dpc/modules/knox/license/core` |
| `:knox-mock-core` | `apps/dpc/modules/knox/mock/core` |
| `:knox-mock-android` | `apps/dpc/modules/knox/mock/android` |
| `:knox-zt-core` | `apps/dpc/modules/knox/zero-trust/core` |
| `:knox-zt-android` | `apps/dpc/modules/knox/zero-trust/android` |
| `:network-control` | `apps/dpc/modules/network/core` |
| `:network-android` | `apps/dpc/modules/network/android` |
| `:scenario-core` | `apps/dpc/modules/scenario/core` |
| `:scenario-android` | `apps/dpc/modules/scenario/android` |
| `:nfc-lab-core` | `apps/dpc/modules/nfc-lab/core` |
| `:nfc-lab-android` | `apps/dpc/modules/nfc-lab/android` |

## Integrations and lab modules

| Gradle project | Directory |
|---|---|
| `:dhizuku-compat` | `apps/dpc/integrations/dhizuku` |
| `:shizuku-adapter` | `apps/dpc/integrations/shizuku` |
| `:native-diagnostics` | `apps/dpc/integrations/native-diagnostics` |
| `:knox-license-lab` | `apps/dpc/lab/knox-license` |
| `:lab-tools` | `apps/dpc/lab/tools` |

Non-Gradle components are grouped as `services/provisioning`, `plugins/chatgpt-companion`, and
`lab/license`.

## Invariants

`tools/tests/test_project_layout.py` enforces the physical layout. `tools/verify_project.py` verifies
that every Gradle project ID is included and mapped to its expected `projectDir`. New modules should
be added under the matching domain rather than placed at repository root.
