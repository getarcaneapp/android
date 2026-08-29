# Android parity delivery workflow

This document defines how Android parity backlog work moves from investigation through a
review-ready pull request. The [iOS parity task list](ios-parity-task-list.md) remains the canonical
source for task scope, status, acceptance criteria, dependencies, and completion.

## 1. Revalidate before implementation

Before starting a backlog item:

1. Confirm the working tree, current branch, `origin/main`, and relevant sibling repository state.
2. Review recent commits and pull requests that may have changed the reported gap.
3. Re-read the task in the canonical parity list.
4. Classify the remaining work as **implementation**, **hardening**, **testing**, or **evidence**.

Current source outranks older backlog wording. If a prior change fixed the visible defect, do not
claim to implement it again; describe follow-up work accurately as hardening, regression coverage,
or validation.

Verify the real Arcane and SDK contract before selecting an implementation. In particular, bounded
offset pagination is not inherently safer when a changing collection lacks a uniquely ordered
traversal key. Choose behavior from the endpoint's documented and tested ordering, limit, and
termination semantics.

Honor the canonical task's scope, dependencies, holds, and adjacent-task boundaries. Discoveries
become scoped follow-up tasks unless they are necessary to complete the active item.

## 2. Use the canonical task record

Review the task list before coding and reconcile it again before publication. Confirm:

- workflow **Status** and the task-title checkbox;
- every acceptance-criterion checkbox;
- exact Android, Kotlin SDK, and Arcane revision pins where relevant;
- commands and results supporting validation evidence;
- pending emulator, device, or live-server testing; and
- narrowly scoped follow-up discoveries.

Do not mark an item or criterion complete without evidence. State unavailable manual validation
plainly rather than treating automated checks as a substitute.

Create `docs/tasks/<task-id>-<slug>.md` only while substantial active work needs design,
investigation, or validation notes. Link it from the canonical task. After completion, move durable
decisions to permanent documentation and remove the temporary packet and link when they no longer
add value.

## 3. Complete and review locally

Before requesting publication:

1. Add and run focused regression tests.
2. Run the full Android baseline:

   ```text
   ./gradlew :app:testDebugUnitTest :app:assembleDebug
   ```

3. Run the sibling SDK baseline first when that repository changed.
4. Perform an independent final review of the complete diff.
5. Reconcile the canonical task record and other affected documentation.
6. Confirm that behavioral claims are proven by current code and tests.

Manual checks should be focused on the changed workflow and Michael's normal use, not expanded into
an unrelated whole-app regression checklist. Clearly separate automated, emulator/device, and
live-server evidence.

If a command succeeds interactively but fails in an agent process, investigate environment
differences such as `PATH`, shell initialization, credentials, and working directory before
declaring the tool unavailable.

## 4. Treat Git and GitHub actions as separate gates

Each action requires its own explicit authorization:

1. commit;
2. push the feature branch;
3. open a pull request; and
4. merge the pull request.

Authorization for one action does not imply authorization for the next. Never push directly to
`main`, merge, delete a remote branch, revert shared history, tag, release, or publish artifacts
without separate explicit permission. If wording could mean either updating a PR branch or merging
the PR, stop and clarify.

Use Conventional Commit prefixes consistently for commit messages, PR titles, and planned squash
commit subjects:

| Prefix | Use |
| --- | --- |
| `fix:` | Correctness changes |
| `feat:` | New capabilities |
| `test:` | Test-only work |
| `docs:` | Documentation |
| `refactor:` | Behavior-preserving restructuring |
| `chore:` | General maintenance |
| `build:` | Build-system or dependency maintenance |
| `ci:` | Continuous-integration changes |

## 5. Publish a review-ready change

Do not use draft pull requests for this workflow. Once publication is explicitly authorized, the
branch must already be tested, documented, and reviewable.

The publication sequence is:

1. finish implementation and validation locally;
2. reconcile the canonical task and supporting documentation;
3. review the final diff and proposed PR description;
4. commit when authorized;
5. push the feature branch when authorized;
6. open a **ready for review** PR when authorized;
7. monitor GitHub CI and Greptile;
8. address higher-risk findings;
9. wait for the follow-up checks; and
10. notify Michael when the PR is safe for his review and merge decision.

The PR title and description must accurately distinguish a new fix from hardening, test coverage,
or validation of an earlier fix.

## 6. Close the automated-review loop

GitHub's ready state starts automated review; it does not establish merge readiness. Retrieve and
evaluate the full Greptile review, including inline comments, as well as required CI results.

For each higher-risk Greptile finding:

1. determine and document the root cause;
2. implement and validate the correction;
3. push the authorized fixing commit;
4. reply directly in the original review thread with the root cause, exact change, commit SHA, and
   validation results;
5. verify the fix; and
6. resolve the original thread.

A general PR comment is not a substitute for the contextual reply. After the finding is fixed,
pushed, validated, replied to, and resolved, a fresh green GitHub build is the terminal signal to
notify Michael that the PR is ready for review. Do not delay that notification for an unnecessary
second bot review unless a new higher-risk finding appears first.

Only Michael's separate, explicit merge authorization permits merging the PR.
