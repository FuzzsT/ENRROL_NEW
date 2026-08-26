# DPC-AIO checkpoint 0.6.4

Release focus: deterministic, domain-oriented repository layout.

- versionCode 11 / versionName 0.6.4
- stable Gradle project IDs retained through explicit `projectDir` mappings
- final app under `apps/dpc`
- reusable modules grouped under `modules`
- adapters under `integrations`
- services/plugins/lab content separated by responsibility
- root directory reduced to build entry points and major repository domains
- empty orphan `knox-license-android` removed
- project-layout regression test added to host suite
