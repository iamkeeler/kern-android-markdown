# Foldable companion workspace

The split workspace places a second editable document beside the existing writer. Diff remains follow-on work.

## Using the companion

- On a sufficiently wide inner display or tablet window, tap **Open split view** in the editor toolbar. Choose a second file from the project workspaces; the current file is disabled to avoid competing writes.
- The second pane uses the same native editor, with independent source, selection, undo, and autosave. Choose another file using its folder button; the current second document is saved before switching.
- Close with the second pane's back control or **Close split view**. The prior Metrics/Settings sidebar is restored. Opening again retains the second document within the editor session.
- Narrow windows and horizontal folds suspend the companion. Minimum width is 720dp overall and 340dp per pane, increasing with both system and editor font scaling. Normal utility sidebars retain their separate 600dp breakpoint.

## Optional physical shortcut

Open **Settings → Behavior → Fold shortcut** (from the workspace or the document's Settings menu) to enable the shortcut and choose **Reader metrics (Hemingway)** or **Second editor**. The existing second-editor choice is preserved when no preference has been saved. Reader metrics opens the existing analysis sidebar or compact-screen sheet and does not require split-editor width. The second editor retains its readable-pane sizing rules; if the window is too narrow, an explanatory message replaces the previous silent refusal. It is off initially. Standard Android hinge-angle sensing is required; the manual toolbar command remains usable without sensors.

Recognition starts from a stable flat baseline (approximately >=170° for 300ms). A deliberate bend (<=160° for 100ms) followed by a stable reopening (>=170° for 100ms within 2.5 seconds) produces one toggle. A 700ms cooldown and fresh baseline separate repetitions. These are starting thresholds for device testing, not a requirement to move forcefully or quickly.

Initial unfolding, sustained book/tabletop posture, deep closure, lifecycle interruption, horizontal folds, modal UI, and invalid/discontinuous readings cancel recognition. Transient vertical geometry changes do not restart recognition. Commits defer briefly while a pointer is held or either IME owns an active composition. A second-editor command still checks whether both panes can fit. Sensors are registered only for an eligible, opted-in, resumed editor. A timer advances dwell checks even for on-change sensors that emit no new events while stationary.

The gutter cue tracks the bend without measuring or parsing text on each angle update. Material 3 `SupportingPaneScaffold`/`AnimatedPane` supply pane motion and honor the platform animation-duration setting. A recognized command produces one feedback request with the current preferences.

### Paper-fold feedback

The Fold shortcut settings dialog shows live hinge-angle readings while it is open so device sensing can be checked locally. Gestures cannot commit while Settings or another modal window is visible; close Settings before using the shortcut. It also contains independent **Fold haptics** (on initially) and **Paper crease sound** (off initially) settings, plus **Try feedback**. Preview is available without a hinge sensor and does not toggle the companion or modify the draft.

Kern reuses the supplied StudyCompanion `paper_crease.wav` unchanged: a 95ms mono sound. `TactileFeedbackHelper` pairs it with Android's native confirmation haptic on API 30+, with a native clock-tick fallback on earlier versions. Native view feedback respects device touch-feedback preferences and requires no added vibration permission.

The optional sound plays once at 0.35 gain through a sonification `SoundPool` with one stream. It preloads only when sound is enabled, checks asynchronous load completion, and skips rather than queues an unready cue. Silent/vibrate ringer modes, DND (including priority/alarms-only modes), disabled system touch sounds, zero/muted system volume, and unavailable policy suppress sound. It never raises volume or requests audio focus. Pausing stops playback; leaving composition releases resources, and stale load callbacks cannot reactivate a released or replacement pool. Audio failure leaves haptics and the layout command functional.

The custom audio helper implements the explicitly requested paper-fold feel; gesture recognition, layout motion, and native text interactions retain their existing contracts. Manual Split view does not request this gesture feedback.

## State and storage boundaries

- `EditorViewModel` retains the primary document's `TextFieldState`, undo history, active file identity, and save path. Loading an already-active file is idempotent; repeated requests during its initial load share the same job.
- A separate `EditorViewModel` owns the second document's editing and save state. A switch waits for its current save before loading the new document. The second session is retained while the split is hidden.
- Separating vertical hinge bounds are passed to the platform pane scaffold only while a supporting pane is visible; bending a single-pane window does not force the writer onto one half. Narrow writer panes use phone-sized gutters. When the full formatting row cannot fit with native 48dp controls, history actions move into the formatting overflow.

## Verification

Required release checks:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

Coverage includes recognition timing/cancellation, layout eligibility, reference failure/retry and stale reads, primary-file re-entry, pending-load sharing, native selection/undo/focus/IME preservation, unsaved preview updates, reference isolation, width suspension/restoration, actual hinge exclusion, lifecycle cancellation, and sensor-unavailable fallback. Feedback checks cover native haptic selection on API 28/34, independent saved preferences and preview, one request per committed fold, audio policy suppression, asynchronous loading, stale callbacks, and pause/disposal cleanup.

Verification completed October 26, 2026: all 178 unit tests and all 22 instrumentation tests passed on the API 36 emulator, with zero skipped tests. Lint reported zero errors (64 warnings and one hint), the release APK built successfully, and `git diff --check` passed. The release optimizer emits nonfatal Kotlin-metadata compatibility warnings. Wide-window UI, feedback preferences/preview, and injected hinge geometry/angles were exercised on the emulator; physical hardware was not available.

Review fixes verified October 27, 2026: 180 unit tests and all 23 instrumentation tests passed on the API 36 emulator, with no failures or skips; lint reported zero errors and the release APK built. Coverage now includes switching inline-image document context, reusing parsed Markdown projections, and cancelling a different pending load when returning to the active draft. Physical foldable feedback remains unverified.

The original commit reproduced five failing instrumentation checks during implementation. Test maintenance updates the fenced-code fixture to retain its existing closing-line boundary, waits for home-screen initialization before navigation, uses current semantic controls and an explicitly empty database state, and isolates storage paths across installs. UI fixtures dispose their activity before closing Room so direct composable observers cannot outlive the database. The toolbar failure also exposed an existing width defect relevant to split panes; the adaptive history placement fixes it and is recorded in [the editor interaction contracts](editor-interaction-contracts.md).

Emulator tests can inject angle/geometry events through the same platform boundary used by production. They do not establish physical sensor availability, hinge feel, or haptic quality. Validate those on a real book-style foldable before enabling the shortcut by default for a device family. The shortcut remains opt-in.

Fold shortcut refinement verified October 29, 2026: 299 unit tests and 58 instrumentation tests passed, with zero failures/errors/skips; lint reported zero errors and the release APK built. Settings now owns the selected action and live sensor check. See [the refinement verification record](fold-shortcut-verification.md) for regression evidence, the emulator recovery, and the physical-device limit. Compact metrics uses the existing modal sheet and its native dismissal controls; gestures remain blocked behind modal windows.
