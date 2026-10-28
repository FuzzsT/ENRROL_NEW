# Branch protection / ruleset requirement

The repository CI now has two independent required-quality jobs in `.github/workflows/pr-android-ci.yml`:

- `host-tests` — runs the complete host verification suite via `./tools/run_host_tests.sh`
- `verify` — runs Android source contracts, builds `:app-dpc:assembleEnterpriseDebug`, and verifies the APK exists

## GitHub repository setting required

GitHub repository rules cannot be enforced by workflow YAML alone. Configure a repository ruleset (or classic branch protection) for `main` with at least:

1. Require a pull request before merging.
2. Require status checks to pass before merging.
3. Require these checks:
   - `host-tests`
   - `verify`
4. Require the branch to be up to date before merging, if compatible with the team's merge workflow.
5. Block force pushes to `main`.
6. Block branch deletion for `main`.

The workflow also runs on pushes to `main` as post-merge verification, but that does not replace pre-merge repository enforcement.

## Verification

After configuring the ruleset, verify through the GitHub repository Rules/Branches UI or the repository rulesets API that `main` is targeted and both checks are required.
