# Foldable companion workspace

The companion keeps a read-only document beside the existing writer. This first version supports reading preview and reference files; diff and editing a second file remain follow-on work.

## Using the companion

- On a sufficiently wide inner display or tablet window, tap **Open split view** in the editor toolbar. The companion initially previews the current draft, including unsaved changes, with inactive Markdown syntax hidden and rich blocks rendered.
- Choose **Open reference** to browse the existing project workspaces and select another Markdown/text file. Choosing the active file returns to its in-memory preview rather than reading a stale disk copy.
- Close with the companion's close control or **Close split view**. The prior Metrics/Settings sidebar is restored. Opening again retains the companion choice and reading position within the editor session.
- Narrow windows and horizontal folds suspend the companion. Minimum width is 720dp overall and 340dp per pane, increasing with both system and editor font scaling. Normal utility sidebars retain their separate 600dp breakpoint.

## Optional physical shortcut

Open the document's **More Options → Fold shortcut** dialog to enable **Fold to toggle split view**. It is off initially. Standard Android hinge-angle sensing and vertical folding geometry are required; the manual toolbar command remains usable without sensors.

Recognition starts from a stable flat baseline (approximately >=175° for 300ms). A deliberate bend (<=150° for 100ms) followed by a stable reopening (>=175° for 100ms within 1.5 seconds) produces one toggle. A 700ms cooldown and fresh baseline separate repetitions. These are starting thresholds for device testing, not a requirement to move forcefully or quickly.

Initial unfolding, sustained book/tabletop posture, deep closure, lifecycle interruption, changed geometry, modal UI, and invalid/discontinuous readings cancel recognition. Commits defer briefly while a pointer is held or the IME owns an active composition. Sensors are registered only for an eligible, opted-in, resumed editor. A timer advances dwell checks even for on-change sensors that emit no new events while stationary.

The gutter cue tracks the bend without measuring or parsing text on each angle update. Material 3 `SupportingPaneScaffold`/`AnimatedPane` supply pane motion and honor the platform animation-duration setting. A recognized command produces one feedback request with the current preferences.

### Paper-fold feedback

The Fold shortcut dialog also contains independent **Fold haptics** (on initially) and **Paper crease sound** (off initially) settings, plus **Try feedback**. Preview is available without a hinge sensor and does not toggle the companion or modify the draft.

Kern reuses the supplied StudyCompanion `paper_crease.wav` unchanged: a 95ms mono sound. `TactileFeedbackHelper` pairs it with Android's native confirmation haptic on API 30+, with a native clock-tick fallback on earlier versions. Native view feedback respects device touch-feedback preferences and requires no added vibration permission.

The optional sound plays once at 0.35 gain through a sonification `SoundPool` with one stream. It preloads only when sound is enabled, checks asynchronous load completion, and skips rather than queues an unready cue. Silent/vibrate ringer modes, DND (including priority/alarms-only modes), disabled system touch sounds, zero/muted system volume, and unavailable policy suppress sound. It never raises volume or requests audio focus. Pausing stops playback; leaving composition releases resources, and stale load callbacks cannot reactivate a released or replacement pool. Audio failure leaves haptics and the layout command functional.

The custom audio helper implements the explicitly requested paper-fold feel; gesture recognition, layout motion, and native text interactions retain their existing contracts. Manual Split view does not request this gesture feedback.

## State and storage boundaries

- `EditorViewModel` retains the primary document's `TextFieldState`, undo history, active file identity, and save path. Loading an already-active file is idempotent; repeated requests during its initial load share the same job.
- `CompanionWorkspace` owns cancellable reference reads, reference identity, parsing, loading, and recoverable errors. Generation checks reject stale completions. Its API cannot mutate or save the writer.
- Preview projects current editor blocks on a background dispatcher. Projection is disposable and never writes back to source.
- References use strict storage reads to distinguish empty files from missing/revoked access. Image resolution uses the reference's own project and path.
- Saveable pane state retains independent preview/reference scroll positions while the companion is hidden or replaced by a utility sidebar. Reference choice belongs to the current ViewModel session; it is not a separate persisted multi-document workspace.
- Separating vertical hinge bounds are passed to the platform pane scaffold. Narrow writer panes use phone-sized gutters. When the full formatting row cannot fit with native 48dp controls, history actions move into the formatting overflow; primary Markdown actions and collapse remain visible in one row.

## Verification

Required release checks:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

Coverage includes recognition timing/cancellation, layout eligibility, reference failure/retry and stale reads, primary-file re-entry, pending-load sharing, native selection/undo/focus/IME preservation, unsaved preview updates, reference isolation, width suspension/restoration, actual hinge exclusion, lifecycle cancellation, and sensor-unavailable fallback. Feedback checks cover native haptic selection on API 28/34, independent saved preferences and preview, one request per committed fold, audio policy suppression, asynchronous loading, stale callbacks, and pause/disposal cleanup.

Verification completed October 26, 2026: all 178 unit tests and all 22 instrumentation tests passed on the API 36 emulator, with zero skipped tests. Lint reported zero errors (64 warnings and one hint), the release APK built successfully, and `git diff --check` passed. The release optimizer emits nonfatal Kotlin-metadata compatibility warnings. Wide-window UI, feedback preferences/preview, and injected hinge geometry/angles were exercised on the emulator; physical hardware was not available.

The original commit reproduced five failing instrumentation checks during implementation. Test maintenance updates the fenced-code fixture to retain its existing closing-line boundary, waits for home-screen initialization before navigation, uses current semantic controls and an explicitly empty database state, and isolates storage paths across installs. UI fixtures dispose their activity before closing Room so direct composable observers cannot outlive the database. The toolbar failure also exposed an existing width defect relevant to split panes; the adaptive history placement fixes it and is recorded in [the editor interaction contracts](editor-interaction-contracts.md).

Emulator tests can inject angle/geometry events through the same platform boundary used by production. They do not establish physical sensor availability, hinge feel, or haptic quality. Validate those on a real book-style foldable before enabling the shortcut by default for a device family. The shortcut remains opt-in.
