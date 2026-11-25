# GitHub Actions — workflow map

The main branch intentionally has four workflow definitions. Keep their filenames, workflow names, job IDs and required checks stable.

| Workflow | When | Purpose |
| --- | --- | --- |
| Android CI (`pr-android-ci.yml`) | Pull requests and pushes to main; manual | Source contracts, debug APK and aggregate verify check |
| Enrollment build + release (`build-aio-enrollment.yml`) | Relevant pushes, version tags; manual | Build, runtime validation, release and manifest repair |
| Migration signing validation (`migration-signing.yml`) | Manual only | Isolated migration validation |
| Migration Update Compatibility Matrix (`migration-update-matrix.yml`) | Manual only | Aggregate device compatibility reports |

Run titles identify the category, event and ref. They do not interpolate manual inputs or credentials. The debug and release paths are intentionally separate; do not remove the main-push CI trigger or merge migration into the normal release path.

## Historical clutter

The Actions page can retain entries and runs from older workflow definitions. Compare the path against the four files above before treating an entry as obsolete. Use the Actions UI to inspect an obsolete workflow and disable it if available. Keep active workflows enabled. Do not delete historical runs indiscriminately: logs and artifacts can be needed for diagnostics or audit. GitHub provides a Delete workflow run action for individual runs if cleanup is explicitly desired.

Use the workflow-specific view to filter runs, and keep failed release runs until their failure has been investigated. See the repository's existing workflow organization contract at `tools/tests/test_workflow_organization_contract.py`.
