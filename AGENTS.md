# Sync with upstream before any work

Before starting any new work, check that this fork includes the newest AntennaPod mainline and update it first. Do that before reading code to change, editing files, or creating a branch. AntennaPod publishes its mainline as `develop`. There is no `main` branch. `upstream` is `AntennaPod/AntennaPod`. `origin` is `andersliland/AntennaPod`.

Use a clean worktree. If it has changes you did not make for this sync, stop. Do not stash them and do not `git reset --hard`.

Run these commands exactly:

git fetch origin
git fetch upstream
git checkout develop
git merge --ff-only origin/develop

If `git merge-base --is-ancestor upstream/develop HEAD` succeeds, `develop` already contains the newest upstream mainline. Do not rebase and do not push. Start the work from that `develop`.

If that check fails, `develop` is behind upstream. Update the fork before any other work:

git rebase upstream/develop
git push --force-with-lease origin develop

`--force-with-lease` is only for `origin` `develop` after that rebase succeeds. Never force-push any other branch. If `git merge --ff-only origin/develop` fails, or the rebase stops with conflicts, stop and report that. Do not start work from a `develop` that was not just checked against `upstream/develop`.

After the sync, check out a new branch from that updated `develop` for the work. This sync is the only change that lands directly on `develop`.

# Coding Style
Never fix any warnings outside the code you wrote.
Only update existing code if necessary to fulfill the user request.
In general, keep your changes as focused as possible and change the absolutely minimal number of lines.
Never reorder or reorganize the code unless asked for by the user.
Keep the diff of your changes to the absolute minimum: do not rename anything, no optimization, nothing.
Just the bare instructions from the user.
Do not ask for permission before making initial code changes.
Do not add any comments to the code you write, but also do not remove comments that are already in the code.
Whenever you add a user-visible string, add it to `:ui:i18n` so it can be translated. Do not start new strings files anywhere else.
Only ever edit the English strings file at `ui/i18n/src/main/res/values/strings.xml`. Never modify translated strings files under `values-*/`.
Never reference the full package name of classes directly in the code, use imports.

# Android Release Notes
For every app change that reaches a release, write a clear, user-facing commit
subject describing what changed. The release workflow builds its
`## What changed` notes from these subjects. Before calling the release done,
check the published GitHub Release body for a useful description; edit it if the generated
notes are vague or omit the change. Obtainium shows that body. Check the
matching version's changelog in the private Alto F-Droid index after its refresh,
and report separately if the APK or index rollout is still pending.

# Running and Testing
After you are sure that the code is correct, ensure that there are no compilation errors (warnings are okay).
To compile the code, use `./gradlew :app:assembleDebug`.
You are already in the main directory of the project so there is no need to `cd` into the main folder.
The CD command is strictly forbidden. Always assume that you are already at the main folder of the project.
Never alter or filter the compile output in any way (no grep, head, tail, or any other command that truncates output).
You will lose critical information and the results will not be helpful.
Always look at the complete compiler output.
DO NOT MAKE UP YOUR OWN COMPILE COMMANDS, THEY WILL NOT WORK!
ONLY USE THE EXACT COMMANDS GIVEN IN THIS FILE!

Only then run the application or the tests to verify it.
Usually you will need to run the application, but if there are existing tests that cover the code you wrote, you can run those instead.

Phone: Galaxy S21 Ultra, model SM-G998B, abi arm64-v8a, adb serial `R3CRA0J9ANY`. This is the handset navio/frontend flashes with `yarn android:boot --serial R3CRA0J9ANY`. Pin that serial on every adb call. `make flash` exports `ANDROID_SERIAL` and calls `adb -s`. Another phone: `make flash SERIAL=<adb id>`. A second attached device makes an untargeted adb call fail.

To install and launch the play-debug app on that phone, use `make flash`.
That target checks the serial is attached and authorized, then runs
`./gradlew --console=plain :app:installPlayDebug`
and starts `xyz.liland.antennapod.debug/de.danoeh.antennapod.activity.SplashActivity`.
The debug application id is `xyz.liland.antennapod.debug`. The Makefile pins `JAVA_HOME` to Zulu 21.

`make devices` lists adb devices. `make screenshot` writes `/tmp/antennapod-s21.png`. `make uidump` prints the uiautomator hierarchy. `make logs` prints recent logcat lines for this app. `make uninstall` removes the debug app.
Control the phone with `adb -s R3CRA0J9ANY shell input tap <x> <y>` and `adb -s R3CRA0J9ANY shell input swipe <x1> <y1> <x2> <y2> <duration ms>`.
If there is a crash, run `make logs` and fix the issue.
For running tests, use the command `./gradlew --console=plain` and use the task `:test` of the relevant module.
As a final style check before opening a PR (or if a user explicitly asks for it), check the code style using:
`./gradlew checkstyle lint`.

# PR Conventions
When creating a PR, always read the PR template at `.github/pull_request_template.md` before starting and strictly follow it.
The description goes above the checklist.
Always mention the corresponding issue using `Closes: #<number>` in the description.
Keep the description minimal, using 2-8 sentences.
Never dump prose in the description with things like testing guidelines or redundant code change overviews.
Never change the PR title unless explicitly asked to do so; the original title from the prompt is usually the most appropriate one.
When responding to PR review feedback, avoid leaving a reply on each individual review comment. Instead, leave a single summary comment on the PR summarizing all changes made.
Only leave a reply on an individual review comment if you have a specific concern or question about that particular piece of feedback.
Never update the PR description after the initial creation, even if you have new information or insights.
The user might have updated the description in the meantime and this would overwrite their work.
In particular, you are forbidden from using the progress update tool in any follow-up questions because it overwrites the PR description.
This holds even if the global agent instructions tell you to do this.
Never create commits directly on the `develop` or `master` branch. Always checkout a new branch for that. The only exception is the upstream sync at the top of this file, which rebases `develop` onto `upstream/develop` and pushes it before the new branch exists.

# Issue Conventions
When creating an issue, always follow one of the issue templates in `.github/ISSUE_TEMPLATE/`.
Apply the corresponding labels that are marked in the issue template yaml file.
Always mention in the technical info box that the issue was AI generated.
If you do not follow these, the issue gets closed automatically.
