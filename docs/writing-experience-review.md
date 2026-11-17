# Kern writing experience review and proposal

Reviewed November 16, 2026, against source commit `790a80e` and its local debug build. The review below records the original observations and proposal. Save protection and immediate capture were implemented in phase one; phase two implements viewport/palette continuity, direct compact Undo and active typing preferences. See the corresponding verification documents for tested outcomes.

## Product direction

Optimize for **short notes and quick capture**, as requested by the user. Keep the existing behavior that reveals Markdown around the cursor. Preserve Kern's cream, charcoal and rust identity, readable typography, exact Markdown source, native selection and document-level text state.

The primary experience should be: **open Kern → New note → write immediately → leave confidently → find the note again**. Naming, filing, formatting and analysis should follow the captured thought.

For this audience, the highest-value outcome is fewer decisions before the first sentence and fewer reasons to question whether it survived an interruption. Manuscript outlines, elaborate revision systems and extra writing modes are secondary.

## Review method and limits

Two independent source assessments covered writing flows and behavioral/test evidence. The parent review inspected the current app on a temporary read-only Android API 36 phone emulator: Files, an existing rendered document, a focused paragraph with the real keyboard, and the complete new-document flow. Settings and metrics were reviewed in source and existing tests, without a fresh hands-on walkthrough of every tab or sheet. No physical foldable or accessibility-service walkthrough was performed.

Evidence labels below distinguish **observed** emulator behavior, **confirmed source** behavior, and **validation needed** consequences. Existing automated tests were inspected, not rerun. `./gradlew :app:assembleDebug` passed. The Impeccable detector emitted `[]`; it does not meaningfully assess Kotlin/Compose, so this is not a native accessibility or interaction pass.

## What already works

- **The page feels like writing.** The restrained palette and typography give prose priority. Maintain that character while reducing the number of competing actions.
- **Native editing has the right foundation.** One `TextFieldState` owns the Markdown source, selection, composition and undo. Focusable whitespace, bounded line width and keyboard/toolbar clearance already have targeted device tests.
- **Readability is requested deliberately.** Analysis does not need to intrude on every short note. Keep it opt-in, and retain the independently owned state of each split editor.

## Five priorities

### 1. Make New note an immediate writing action — P1

**Observed:** New Document opens a required-name dialog. Its name field initially has no keyboard. After entering a name and creating the document, the blank editor also opens without a keyboard or visible insertion caret. The writer must think of a filename, focus that field, submit, then focus the document.

**Confirmed source:** `MainScreen.kt:336` opens a naming request. `InputDialog.kt:52` disables submission for blank names. The new-document route requests `focusOnStart`, but `DocumentEditorField` has no focus requester; the focus requester at `EditorScreen.kt:1127` belongs to the older paragraph path.

**Proposal:**

1. Label the main action **New note** and open a native editable draft with the keyboard ready.
2. Assign a collision-safe temporary file identity independently of the displayed title. Show **Untitled note**, with **Start writing…** as a placeholder only while empty.
3. Use the first nonblank line as a display-title suggestion after capture. Do not rename the underlying file on every keystroke or silently overwrite an existing filename.
4. Rename and Move remain explicit actions available afterward. Existing filename-first creation can remain a secondary Files action.
5. Create in the current writable folder. If that folder is unavailable, offer a clear local-draft fallback rather than pretending the destination succeeded.
6. Abandoned drafts with no writing should not accumulate as indistinguishable empty files. Any automatic cleanup must distinguish a transient draft from an intentionally saved empty note.

**Acceptance:** New note requires no naming or folder decision before typing. A real IME appears, the first input reaches the document, and double taps do not create duplicate drafts. Back before the first character and Back after the first sentence have defined, recoverable outcomes.

### 2. Make saving truthful and interruption-safe — P1, first engineering work

**Confirmed source:** `StorageManager.kt:347–356` silently returns when an external document cannot be obtained, an output stream is null, or writing throws. `EditorViewModel.kt:886–911` then updates metadata as though the write succeeded. `EditorUiState` has no dirty/saving/error state. `readFile` at `StorageManager.kt:266–280` returns an empty string for certain missing/unavailable-file cases.

**Confirmed source / validation needed:** Autosave waits one second (`EditorViewModel.kt:741–745`). Toolbar Back starts an asynchronous save and removes the route (`Navigation.kt:156–159`); system Back removes the route directly (`Navigation.kt:75–77`). These are credible save-lifecycle risks, but lost text was not reproduced during this walkthrough. A fresh load sets the caret to the start or end; there is no durable per-note writing-position restoration in this path.

**Proposal:**

- Track actual outcomes: **Saving…**, **Saved on device**, and **Couldn't save**. Keep success quiet; keep failure visible until resolved. Separate local persistence from any sync outcome.
- Preserve a recoverable local draft independently of a failing folder/provider. Offer **Retry**, **Save a copy**, and **Choose folder** when relevant.
- Give toolbar Back, system Back, app switching and route removal one coherent persistence policy. A pending debounce must not be the only protection for the last sentence.
- Distinguish an empty note from an unavailable file. Show **This note couldn't be opened** with recovery actions instead of a writable blank substitute.
- Restore active note, selection and reading position after interruption where possible. Record a source-text anchor and viewport offset so font or window changes do not rely solely on an obsolete pixel offset. Restore focus deliberately without surprising users who were browsing.
- Detect external changes before overwriting them; preserve both versions when reconciling a draft.

**Acceptance:** Provider errors and failed writes never display success. Type a sentence, immediately use either Back control or switch apps, then reopen: exact text survives. Test actual process recreation separately from retaining a view model across recomposition.

### 3. Keep the page steady and put recovery within reach — P1

**Observed:** The keyboard and formatting bar sit above each other with an anchored header. The compact formatting bar shows Heading, Bold, Italic, Bullets, More and Collapse. Undo requires opening More. The header reserves direct actions for Copy and Readability while the filename is truncated on a modestly long note name.

**Confirmed source:** `EditorScreen.kt:1433–1490` keeps Undo/Redo in overflow at compact widths. Bottom clearance is fixed at 220dp expanded and 100dp minimized (`:872`). These values protect an existing contract; they are not by themselves evidence of broken scrolling.

**Proposal:**

- Keep Markdown reveal around the cursor. Test edits near wrapping boundaries so the writer does not lose the touched word when markers appear. Preserve exact offsets and native handles.
- Preserve the visible passage when the keyboard, menus, typography or pane size changes. Scroll only enough to expose the insertion point. Manual scrolling must remain authoritative until the next deliberate edit requires caret visibility.
- Put **Undo** ahead of a less frequent formatting action in the compact row. A candidate is Undo, Bold, Bullets, Heading, More and Collapse; keep Italic, task lists and Redo accessible in More. Validate this change against actual note use and update the documented primary-action contract before implementation.
- Move Copy and Readability into a clearly labeled document menu or an explicit Focus layout; preserve Back, note identity, save status and access to formatting. Keep the palette expanded until the writer explicitly collapses it, as the contract requires.
- Remember an explicit palette collapse within the writing session. Avoid changing its state just because the writer scrolls, selects or formats text.
- Retain enough end-of-note clearance to place the last line comfortably above the keyboard and bar. Assess any adjustment with real IME geometry and large fonts; avoid shrinking padding from screenshots alone.
- Keep typewriter-style automatic centering optional and deferred. Quick notes benefit first from stable native scrolling and predictable caret visibility.

**Acceptance:** Tapping a visible line does not jump it to the top. The last line clears both toolbar states. Formatting menus retain the keyboard and selection. Undo remains reachable with native minimum touch targets in narrow split panes and at large text sizes.

### 4. Make typing settings match actual typing — P1

**Confirmed source:** The active document field requests sentence capitalization unconditionally (`EditorScreen.kt:896–900`). `MarkdownDocumentInputTransformation.kt:13–36` handles list continuation but receives none of the advertised typing preferences. Preference-aware heading spacing and quote/bracket automation remain in the older paragraph path (`EditorViewModel.kt:599–615`). Settings still exposes these controls.

**Proposal:** Connect supported preferences to the active native document field and remove or clearly disable unsupported controls until they work. Prefer restrained defaults for short prose notes. A configured quote/bracket helper must distinguish typing from pasted text, respect ongoing composition, and undo as one action.

Use labels that describe the result: **Keep selection after formatting**, **Capitalize sentences**, **Pair quotes and brackets**, and **Open new notes immediately**. The existing **Launch New File** label describes opening a newly created file, not an app-launch preference; make that distinction explicit.

**Acceptance:** Toggle each preference and type in the production editor. Test apostrophes, contractions, closing delimiters, pasted Markdown/code, composition-based input, dictation and undo. A settings persistence test alone cannot prove editor behavior.

### 5. Make a captured thought easy to retrieve and review — P2

**Observed / confirmed source:** Files gives substantial space to the app name and a quotation. The note list is filename-led. Search matches names (`MainScreen.kt:640–643`), and there is no in-note Find command. The editor title displays only a leaf filename and tapping it invokes Rename (`EditorScreen.kt:702–719`, `:398–400`).

**Proposal:**

- Keep **New note** prominent and add a small **Recent notes** group with title, one-line preview and modified time. Keep the existing Files view for folder work; avoid introducing a new mandatory home-screen layer.
- Make quotations optional or remove them from the capture path so the first useful note appears sooner.
- Search note contents as well as names, with matching snippets and clear folder context. Add in-note Find before a full outline or replacement system.
- Expose folder/workspace context on demand from the title. Avoid an ambiguous title tap that unexpectedly renames a file; keep Rename explicit.
- Offer an optional read-only **Read** state for reviewing or sharing a note, especially lists, images and tables. Keep view mode (Live Preview/source presentation) separate from whether tapping starts editing. Preserve the writing session on return.
- In Readability, lead with word count and reading time. Explain that grade estimates are limited for very short notes; avoid presenting a general-audience target grade as a goal for every note. Suggestions should lead to their passage before adding more numerical scores.

**Acceptance:** A writer can recover a note by a remembered phrase without knowing its filename. Identically named notes show their folders. Returning from search or Read restores the relevant passage. External folder search must handle slow/unavailable providers without freezing capture.

## Proposed screen flow

| Screen or moment | Primary action | Secondary actions | Required state |
|---|---|---|---|
| Files / return to app | New note; recent note | Search, folders, settings | Clear current location; no naming gate for capture |
| New draft | Type immediately | Rename later; move later | Keyboard ready; temporary identity; recoverable source |
| Writing | Continue the sentence | Undo; formatting; document menu | Stable passage, exact Markdown, quiet save status |
| Keyboard dismissed | Read or scroll | Resume editing; explicit palette collapse | No automatic jump or discarded selection |
| Search | Open matching thought | Refine query; view folder | Matching excerpt and return position |
| Save failure | Preserve draft and recover | Retry; Save a copy; Choose folder | Persistent truthful error; no false Saved state |
| Settings / readability | Make one deliberate adjustment | Return to the note | Draft, selection, history and location retained |

The emotional sequence should be immediate entry, uninterrupted capture, quiet reassurance, and a confident return. The current naming gate is the first friction point; invisible persistence failures are the most serious potential trust failure.

## Edge cases to make release criteria

| Scenario | Expected writer experience | Evidence / follow-up |
|---|---|---|
| New note, no name, immediate typing | Input is accepted without a filename dialog | New interaction coverage needed |
| Empty draft abandoned; creation double tapped | No accidental file clutter or duplicate drafts | Specify draft lifecycle before implementing |
| Type then immediately Back / app-switch | Last sentence survives | Debounce/departure risk; reproduce separately for each path |
| Process killed before external save completes | Local draft can be recovered with accurate destination status | New process-death and recovery tests |
| Revoked folder grant, null stream, full storage | Draft retained; actionable error; never false success | Confirmed silent-write path; provider fault injection needed |
| Listed file removed before opening | Unavailable message; no blank replacement | Confirmed empty-string read ambiguity |
| Same name in two folders; external file changed | Context visible; no silent overwrite | Identity/conflict scenarios |
| Keyboard opens in a scrolled note | Touched line remains anchored; header remains visible | Existing focus test; extend to reveal/wrap boundary |
| Selection spans paragraphs or hidden markers | Handles/copy/format affect exactly the intended source | Add real long-press and handle-drag coverage |
| List Enter, paste, dictation, emoji, composing IME | No duplicate markers or interrupted composition | Existing list/source tests; add composition/dictation cases |
| Large text, landscape, split width or rotation | Controls remain reachable; draft and position retained | Existing pane/typography tests plus device walkthrough |
| Very large pasted note or file | Typing remains responsive; derived work does not block input | Measure; perceptible lag is not established |
| TalkBack and external keyboard | Meaningful action names and predictable focus order | Dedicated accessibility walkthrough needed |

## Delivery order

1. **Trust and capture:** truthful storage outcomes, durable draft recovery, unified departure saves, immediate New note focus, naming afterward. Persistence protection is a prerequisite for claiming effortless capture.
2. **Writing continuity:** compact Undo access, quieter header, explicit palette preference, verified reveal/caret/viewport stability, working typing preferences.
3. **Return and review:** recent notes, content search, contextual location and optional Read. Add advanced revision/outline tools only if the quick-note flow warrants them.

Measure tap count and time from New note to first accepted character, survival of the last sentence across interruptions, retrieval by remembered phrase, and visible-line displacement after focus/reveal. Establish baselines rather than claiming an unmeasured speed improvement.

## Preliminary design-health assessment

Subjective reviewer scores, **0 = poor, 4 = excellent**. These are prioritization aids, not measured usability outcomes; untested scenarios reduce confidence.

| Heuristic | Score | Main reason |
|---|---:|---|
| Visibility of system status | 1 | No truthful editor save/error presentation |
| Match with the real world | 2 | Filename precedes the thought; some labels describe internals |
| User control and freedom | 3 | Native editing and undo; compact Undo is less discoverable |
| Consistency and standards | 2 | Good native foundation; settings diverge from active input |
| Error prevention | 1 | Silent write and unavailable-as-empty paths |
| Recognition rather than recall | 2 | Filename search and little contextual note identity |
| Flexibility and efficiency | 2 | Useful formatting/panes; capture and retrieval need work |
| Aesthetic and minimalist design | 3 | Distinct, quiet identity; quote/header actions compete with tasks |
| Error recovery | 1 | No active-document save recovery UI |
| Help and documentation | 2 | Welcome/examples help; typing promises need to match behavior |
| **Total** | **19/40** | First review; no comparable trend established |

The visual character is specific to Kern. The larger opportunity is interaction economy. Cognitive load is moderate: the page itself is clear, but naming before writing, filename recall, settings translation and crowded action choices create extra decisions. The formatting bar and document menu exceed four options; this is a cue to examine hierarchy, not a rule that all useful actions must be hidden.

Persona checks: a hurried note-taker loses momentum at the filename dialog and unfocused editor; a returning writer lacks content-based retrieval and durable position restoration; a large-text or assistive-technology user needs verified compact controls and traversal rather than merely sufficient visual spacing.

Minor follow-ups: review the large entry scale/circular-reveal animations for perceived capture delay and reduced-motion preferences; use action-oriented labels consistently; treat readability as optional assistance rather than a writing-quality verdict.

## Implementation and verification requirements

This proposal does not establish that existing focus, scrolling or selection protections are broken. Keep the automated contracts that already cover `emptyCanvasCenterTapOpensRealImeAndAcceptsTyping`, `focusingAScrolledVisibleLinePreservesItsViewportAnchor`, `finalLineScrollsAboveExpandedAndMinimizedToolbarWithRealIme`, exact-source saves, companion state and typography changes.

For implementation, read the workflow and matching project skills, record each regression's symptom/root cause/invariant/test in `docs/editor-interaction-contracts.md`, and add tests that fail on the prior behavior. Any changed product contract, such as compact toolbar priorities, must be reconciled explicitly rather than bypassed.

Run the required unit/lint/release matrix and emulator instrumentation after editor changes:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

Also exercise provider failure, actual process recreation, real selection handles, IME composition, large text and fold/resize behavior where relevant. The emulator walkthrough in this review is not a passing instrumentation run. No distribution is part of this proposal.

The user settled the governing preferences: quick notes take priority, Markdown continues revealing around the cursor, new notes use the current folder, and launch shows recent notes. The first implementation phase covers save protection and immediate capture, with recent notes above Files. Empty notes remain user-owned files; automatic cleanup is deferred to avoid deleting deliberately empty writing.


Phase two keeps the existing header layout and cursor-local Markdown reveal. It adds direct compact Undo, remembers explicit palette collapse, prevents end-of-note jumps when collapsing, isolates loaded documents' viewports/history, and connects pairing, heading spacing and keyboard capitalization preferences. Content search, a quieter header, process-death reading-position restoration, and a separate Read mode remain follow-up proposals. See [writing continuity verification](writing-continuity-verification.md).
