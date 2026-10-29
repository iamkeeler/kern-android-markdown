# Application polish execution record

Status: implementation and required core checks passed. Extended device-matrix checks and a fresh review of the complete final diff remain outstanding as listed below. No release or publication is implied.

## P00 baseline

- Branch: `codex/foldable-companion-feedback`.
- HEAD before this work: `ba1bfbae2a7485fc3f4ab974d578fd102bb65489`.
- Existing tracked edits preserved: `EditorScreen.kt`, `EditorViewModel.kt`, `CompanionPane.kt`, `EditorScreenTest.kt`, `EditorViewModelTest.kt`, `contributor-guides/design-guide.md`, `contributor-guides/product-requirements.md`, `docs/editor-interaction-contracts.md`, `docs/foldable-companion.md`.
- Existing untracked design/execution plans are part of the accepted context.
- Preexisting tracked diff saved before implementation to `/private/tmp/kern-polish-baseline/preexisting.patch`, SHA-256 `28c27c246c9cafd57fa39232cdb1f44494bace25a706f6fe3d23d47ef929f802`; original status is beside it. This is a temporary local audit snapshot, not a published artifact.
- System Java: Oracle 24. Gradle daemon declaration selects JetBrains 21; Kotlin compilation toolchain selects Java 17 (Homebrew 17.0.19 installed). AGP 9.0.1, Kotlin 2.3.20, Compose BOM 2026.03.01; no dependency upgrade authorized by polish.
- Started existing `Medium_Phone_API_36` AVD, `emulator-5554`: 1080×2400 pixels, density 420, boot completed. Initial `show_ime_with_hard_keyboard=0`; restore if changed for tests. No separate API 26/tablet AVD was initially listed.
- Baseline command passed: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` (2m 1s). Unit results: 181 tests, zero failures/errors/skips. Fresh debug lint XML: zero Error/Fatal entries. Logs and copied unit/lint reports are under `/private/tmp/kern-polish-baseline/`. R8/Kotlin metadata compatibility warnings predate polish; dependencies remain unchanged.
- Resolved Foundation/UI/runtime 1.11.2, Material3 1.5.0-alpha01, adaptive 1.2.0, Lifecycle 2.10.0, Navigation 3 1.0.1. Installed APIs support `TextFieldDecorator`, paragraph styles in output transformation, and `LineHeightStyle.Mode.Minimum`. Merged debug test manifest already declares AndroidJUnitRunner; no build edit needed.
- Baseline full connected instrumentation could not install: `INSTALL_FAILED_INSUFFICIENT_STORAGE`, zero tests executed (not a test pass). Original APKs/results are saved in the baseline directory; app APK SHA-256 `7831c7c88e4a3029a84edf91ce15321be97474f7196739bec0d2843f77cd1dd5`, test APK `485c34a2082f26f738512da2622cb66219e40849388202383c9576d75115be01`. Preserve existing emulator data and provision a temporary AVD for subsequent checks. Software keyboard setting temporarily changed to 1 (initial 0); restore before closing the original emulator.
- ViewModel inventory: Navigation3 default decorator supplies saveable entry state but no entry ViewModelStore decorator, so browser/primary editor currently use activity-owned ViewModels; secondary editors have explicit keys. Manual factories do not consume SavedStateHandle extras. Preserve this ownership rather than assuming route-scoped restored handles.
- Created fresh test AVD `Kern_Polish_API_36` entirely under `/private/tmp/kern-polish-avd`, using the existing installed system image and an 8GB data partition (6.9GB available). Restored original AVD keyboard setting and closed it without wiping data. Saved baseline APK installation succeeded on the temporary device; baseline is running directly through `adb shell am instrument -w -r com.attachdesign.kern.test/androidx.test.runner.AndroidJUnitRunner` so ongoing source edits cannot change the tested baseline APKs. Log: `/private/tmp/kern-polish-baseline/temporary-instrumentation.log`.
- Fresh-device first boot caused a startup ANR before tests under 2GB RAM. Restarted only the temporary device with 4GB RAM and completed setup. Saved baseline APK suite then passed **23 tests**, zero failures, in 111.523s. Evidence: `/private/tmp/kern-polish-baseline/temporary-instrumentation-retry.log` (`OK (23 tests)`, instrumentation code -1). The earlier installation/ANR attempts are not counted as passing runs.

## Packet status

Only completed, reviewed work will be marked complete. Focused checks do not replace the required integrated suite.

| Packet | Status | Evidence |
| --- | --- | --- |
| P00 | Complete | Unit181/lint0/release passed; API inventory complete; original APK instrumentation23 passed on temporary API36 device after environment repair |
| P01–P13 | Implemented; core checks passed | Editor metrics/canvas, theme roles/export, Settings/search/browser/supporting layout, empty-state and result-aware copy |
| S01–S09 | Implemented; core checks passed | Named mutation outcomes, retained dialogs/editor identity, complete scan gate, recovery/removal, truthful trash/delete and activity Undo owner |
| V01–V03 | Partial | Independent packet reviews and integration fixes recorded; required final checks, device limitations and documentation remain explicit below |

## Implementation checkpoints

- Checkpoint 01 caught a test-only unsupported Compose import; corrected without changing production behavior.
- Checkpoint 02 compiled production and ran 228 unit tests; 15 new failures identified a provider query-overload boundary plus alpha-quantization expectations. The naming helper now uses the API 26 Bundle query overload supported by the real DocumentsProvider fixture; it does not replace provider tests with success mocks.
- Checkpoint 03 compiled production and Android tests and ran 228 unit tests: 227 passed; the remaining new contrast assertion assumed exact half-alpha instead of Compose's 8-bit alpha. That expectation is corrected and awaits rerun. The original 181 tests remain passing.
- P01/P02 dedicated metrics and production typography, P05 serialization fidelity, P06A derived roles/Material mapping, and S01 naming outcomes are implemented and integration-pending. P09 search changes and tests are also implemented, pending device checks.
- Five new P03 canvas coordinate/width tests are being executed against the old production geometry before its fix; this run provides regression evidence, not a release pass.
- Old geometry failed all five initial coordinate/width assertions; measured wide text was 1050.7dp. The top-padding test was subsequently tightened to measure the actual app-bar bounds rather than infer them from its Back icon. Native decoration now owns all canvas whitespace and caps/centers inner text at 680dp.
- Checkpoint 04 reached ten of thirteen device tests, but the Android system process ANRed; real IME requests failed at server visibility. This interrupted run is not a passing result. Restarted only the temporary AVD offline/headless with software rendering. A three-test probe against the rebuilt app then passed width, below-text focus/real IME/typing, and complete heading line bounds (9.268s). Heading paragraphs now reserve proportional line height so large runs do not clip.
- Checkpoint 05 ran 240 units: 238 passed, while two new settings-observation tests exposed an unconfined test-Main race with real IO emissions. They now serialize Main resumptions while waiting on real IO; production scheduling was not changed to accommodate the tests.
- Checkpoint 06 passed **254 unit tests**, zero failures/errors/skips, and assembled debug app + test APKs (30s). Includes S01 provider metadata recovery, S04A complete directory outcomes, S07A native move capabilities, theme/serialization/metrics, and original regressions. P07/P08 Settings instrumentation compiles. Expanded P04 viewport/clearance/legacy tests compile.
- Checkpoint 07 ran 26 focused device tests: 21 passed and five failed test assumptions. Two editor assertions used a unique Compose root despite native selection popups; search empty text included placeholder semantics; Settings counted the tab group as a radio group and assumed Behavior always overflowed at 1x. Assertions/fixtures were corrected without app changes; rerun is pending. Required final integrated lint/release/full instrumentation remain pending.

- Checkpoint 08 compiled production and Android tests successfully. Checkpoint 09 ran **31 device tests: 30 passed**, with one new browser assertion expecting a rename dialog title before S02 integration; the five prior failures passed after fixture corrections. This is not the final integrated suite.
- Checkpoint 10 exercised the first combined storage/scan/session snapshot. S07B provider/disk/Room tests passed; six legacy creation tests still expected the replaced low-level API, one linked-removal fixture and one virtual-time/real-IO timeout required correction. New integrated editor naming/deletion changes were added after this checkpoint and require a fresh run.

## Implemented scope and conservative boundaries

- P01–P04: dedicated editor metrics, native full-canvas decoration, centered measure, safe heading metrics, real IME/coordinate/scroll regression coverage. No new pointer interception or source replacement.
- P05–P08: complete theme export, compatible derived accessible roles, Material mapping, native selection rows, host-local Settings drafts/tabs/scroll and transactional idempotent imports.
- P09–P12: explicit search focus/Back policy, constrained browser headers/rows, actual control names in empty states, adaptive metrics and accessible About links with one outer gutter.
- S01–S03: validated leaf names, actual provider identities and metadata-only receipts, retained operation state plus saved presentation drafts, success-only navigation, editor rename without reloading its live text.
- S04–S06: explicit complete directory results, project mutation/scan commit gate (including importer and editor saves), failure/retry presentation, stale-result protection and linked-workspace association removal that preserves files/grants/history validity.
- S07–S09/P13: capability-checked native trash/restore, truthful final deletion, one activity session with revocable Undo presentation leases, actual-location checks before ordinary success, and copy supported by operation outcomes.
- Internal enumeration can still select/create/fall back/migrate roots through legacy `getProjectRootFile`. Internal scans therefore **do not prune absent index rows**; they add observed entries while preserving old metadata. An authoritative root/migration policy is deferred rather than guessing which empty directory is authoritative. Complete external SAF scans may prune only within the generation/project-existence commit guard.
- Generic cross-project moves, persistent deletion receipts, restart trash cleanup, provider-wide atomicity across external actors, and a global editor-session registry remain outside these bounded packets. No dependency upgrade, publication, signing-policy change or release was performed.
- Session Undo does not survive process death. Unfinalized trash stays discoverable on disk instead of being purged by a lost timer.

## Verification limitations to retain at handoff

- Only API36 phone and Wear images are installed. API26 emulator smoke is unavailable without provisioning another compatible image; it must not be reported as passed.
- Injected pane/hinge tests do not verify physical hinge sensing, haptics or sound. A physical foldable remains a separate check.
- Fresh independent planning/editor/operations reviews preceded execution; packet reviews covered theme and scan/storage contracts, and an independent session-state review found the cancellation spinner fixed here. A fresh review of the entire final cross-cutting diff is still outstanding.
- Temporary test logs live under `/private/tmp/kern-polish-checks/`; they are local evidence and may be cleared by the OS. Final report counts and artifact paths below are the durable summary.

## Final required build checks

- Passed `./gradlew test lint bundleRelease :app:assembleRelease -x :app:uploadCrashlyticsMappingFileRelease` on the final source snapshot (1m 48s). This runs the configured unit-test variant; release unit tests are not configured and are not counted as a zero-test pass.
- Debug units: **296 tests, zero failures/errors/skips**. The added scan fixture now uses direct Room query/transaction executors because its coroutine races are explicitly controlled; this avoids treating real Room-flow delivery as virtual-time completion. Command-only legacy paragraph tests stop unrelated settings observers; real persistence remains covered on-device.
- Fresh `app/build/reports/lint-results-debug.xml`: **zero Error/Fatal**, 69 warnings and one hint. Added suggestions include `Uri.toUri` usage and a cursor warning; the cursor is enclosed by `use`, with close/cancellation behavior covered by directory tests. Existing R8/Kotlin metadata warnings remain; no dependency upgrade or warning suppression was introduced.
- Release APK: `app/build/outputs/apk/release/app-release.apk`, SHA-256 `802c561d5eba1df1ace0e74e373e8b8cf402d12431353d60c529943cbacf64ba`.
- Release bundle: `app/build/outputs/bundle/release/app-release.aab`, SHA-256 `8e95747fa00b6be944ce2d301bf5353f7cad3a9f93193f3aa21449917aaa06c9`.
- Crashlytics mapping upload was excluded; no publication or release command ran.
- Changed Kotlin source/test manifest: 55 files (including preserved preexisting edits), local `/private/tmp/kern-polish-checks/final-source-manifest.json`, SHA-256 `afa972bd539e29a113189c14b1de772b2af7c025afbc751b60c46734b968a3a4`. HEAD remains `ba1bfbae2a7485fc3f4ab974d578fd102bb65489`; this is a dirty working-tree verification, not a committed revision.
- Prior full integrated device run: **55 tests passed**, zero skips/failures, plus 296 units passed (4m 19s). A final complete instrumentation rerun after the last copy refinements is recorded below when it finishes.
- Manually launched the production app on the temporary API36 phone, created a disposable sample through the normal dialog, tapped the blank editor canvas, typed two paragraphs with a real IME, and dismissed the keyboard. Inspected browser/editor screenshots: cream/rust identity, readable 16sp text, gutters, fixed header, toolbar/IME clearance and root system bars were intact. Local captures: `visual-launch.png`, `visual-editor-ime.png`, `visual-editor.png` under the check directory. These are current-state inspection images, not matched before/after goldens.

## Final instrumentation confirmation

- Passed `./gradlew :app:connectedDebugAndroidTest` on the final integrated snapshot: **55 tests, zero failures/errors/skips**, 4m 27s, `Kern_Polish_API_36` / emulator-5554 / Android16 API36, offline, software rendering, real software IME enabled. Log: `/private/tmp/kern-polish-checks/final-device-checks.log`.
- Scope: AdaptiveMetricPair1, EditorCanvasFocus8, original EditorScreen19, EditorTypography5 (including rename and route-independent Undo/restoration), MarkdownRendering1, MainScreen10, Settings11. No zero-test or disconnected-device attempt is counted as passing.
- `git diff --check` passed after the final documentation update. The original AVD was preserved; the temporary AVD may be restarted from `/private/tmp/kern-polish-avd` for further checks.
- No source changes followed this run. Remaining work is the explicitly unverified extended matrix (API26, dedicated landscape/RTL and broader theme/large-font visual walkthroughs, physical fold feedback), matched before/after visual goldens, and fresh independent review of the final diff. The existing automated large-font/Unicode/wide-pane/companion tests are passing; they do not substitute for every directed device condition.
