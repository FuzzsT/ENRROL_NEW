# DPC-AIO Plugin Full Upgrade

Plugin architecture: skills-only

Plugin version: 0.1.0

Skills:
- dpc-aio-build
- dpc-aio-ci-repair
- dpc-aio-enrollment
- dpc-aio-verify

Autopilot-compatible validation: PASS

Deterministic packaging: PASS

Standalone plugin SHA-256:
`9d14e51e18e1b2e2d7715f2c73071908cc40f8ef286de4c863afcfb463ee3ac4`

Android APK build: NOT CLAIMED. The plugin/repository static and host checks pass, but this workspace has not produced a fresh Android APK via a Gradle assemble command exiting 0.

Plugin Directory publication: BLOCKED pending verified publisher identity, stable public listing URLs, and authorized write/publication access.

## Validation boundary

The repository vendors a local validator/packager compatibility snapshot aligned with the installed Plugin Autopilot contract and the official OpenAI plugin packaging documentation re-checked on 2026-08-22. The installed skill resource is not directly materializable as a filesystem file in this runtime, so byte-for-byte identity of the vendored validator source is not claimed; the required manifest, Skills, branding, exclusions, package limits, archive-root, and deterministic packaging gates are enforced locally.

The public `plugins/chatgpt-companion/` archive excludes Android source copies, APKs, Gradle caches, SDK/NDK, signing material, private/lab token filenames, MCP/app wiring, and other repository-only content. The full source archive remains a repository source package and is not itself a public Plugin Directory artifact.
