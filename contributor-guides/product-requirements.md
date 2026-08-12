# Product Requirement Document (PRD)
## Project Name: Modern Android Markdown Editor

### 1. Product Overview & Vision
A high-performance, minimal, and typography-first Markdown editor designed for mobile and foldable Android devices. Heavily inspired by Typora, the core value proposition is an **inline-reveal live preview layout engine** built entirely on a custom, paragraph-based differential Markdown parser. The application targets long-form writers, authors, and power users who require dense information layouts, local-first file sovereignty, and zero-latency typing execution on large files.

---

### 2. Core Functional Requirements

#### 2.1 The Editor & Parsing Engine
* **Inline-Reveal WYSIWYG Architecture:** By default, text blocks display fully rendered Markdown styling. When a text block gains cursor focus, the raw Markdown syntax tokens (e.g., `#`, `**`, `*`, `>`) reveal themselves inline for direct editing.
* **Three-State View Configurations:** Accessible via global and per-document settings:
    1. **Rendered (Live Preview):** Standard inline-reveal mode (Default).
    2. **Syntax-Highlighted:** Markdown formatting symbols are visible, but text retains stylistic properties (e.g., headers are enlarged and colored, code blocks are boxed).
    3. **Raw Plain-Text:** Monospace font styling with absolutely zero rendering or structural color decoration.
* **Differential Performance Pipeline:** The text editor manages documents as an indexed sequence of discrete paragraph blocks (delimited by `\n\n`). Typographic modifications only parse and recompose the specific block under modification, ensuring linear O(1) performance scaling on large files.

#### 2.2 Breadcrumb Project Explorer & VFS
* **Target Hierarchy:** Supports infinite nested directory trees (`Root/Sub-Folder/File.md`).
* **Breadcrumb Navigation (Standard Displays):** The Top App Bar explicitly displays the active file path string: `[Project Root] / [Parent Folder] / [Active File]`.
    * Tapping `[Project Root]` opens a full-height bottom-sheet overlay displaying the complete project directory layout.
    * Tapping `[Parent Folder]` filters the modal view to show only files and sub-folders at the immediate directory level of the active file.
* **Adaptive Dual-Pane Navigation (Foldables / Tablets):** When the application detects an unfolded screen state, large-screen layout, or landscape orientation, the UI automatically transitions to a persistent split-screen view. The project directory tree locks to the left margin, and the active editor maps to the right pane.

#### 2.3 Storage Architecture & Cloud Sync Engine
* **Dual Storage Modalities:**
    1. **App-Sandbox Storage (Internal):** High-speed, local-first operational sandbox. This storage tier supports the full suite of automated cloud synchronization pipelines.
    2. **External Scoped Storage (SAF):** Allows users to target arbitrary file paths on device storage or external SD cards. Files are read/written directly via Android's Storage Access Framework. Background cloud synchronization is programmatically disabled on this tier to prevent system file-handle bottlenecks.
* **Sync Execution & Lifecycle:** Cloud provider integrations (Google Drive, OneDrive, Dropbox) utilize an **Auto-Sync on File Save/Close** model. Upload sweeps trigger immediately when a document loses focus, when the app passes to the background, or when an explicit file-close command is called.

#### 2.4 Formatting Toolbar & Text Selection Mechanics
* **Persistent Floating Context Toolbar:** Appears immediately upon active character string selection.
* **Sticky Selection Setting:** Controlled via a dedicated configuration toggle:
    * *State TRUE (Default):* Applying formatting (e.g., wrapping selected text in `**` tokens via the **B** control button) preserves the text selection state, allowing users to apply stacked formatting configurations sequentially.
    * *State FALSE:* Applying formatting drops the selection handle range immediately and returns to standard insertion cursor behavior.

#### 2.5 Metrics & Analytical Panels
* **Asynchronous On-Demand Metrics:** Word count, character count, and structural metrics operate on separate execution threads.
* **Hemingway Readability Analyzer:** Evaluates text grade levels, complex vocabulary structures, and sentence complexity. To optimize typing latency, the analytical suite triggers *only* upon the user explicitly calling or revealing the Metrics Sidebar / Pane. Inline syntax highlights are applied across the active text node on demand.

#### 2.6 Customization, Accessibility, and System Controls
* **Dynamic Theme Import/Export Pipeline:** Theme engine allows users to modify font pairings, background hex color keys, selection handles, and raw code syntax mappings. Themes are serializable as JSON structural files for native export and sharing.
* **State-on-Launch Configurations:** Application settings configure default launch behaviors to choose between opening a new scratchpad document or restoring the last open file/project index pointer.
* **Formatting Reference Legend:** A static, accessible reference menu item mapping standard Markdown commands and keyboard shortcut bindings.

---

### 3. Technical Stack & Implementation Constraints

| Layer | Technology Choice | Architectural Justification |
| :--- | :--- | :--- |
| **Language** | Kotlin 2.x | Standard modern Android language pipeline; strong coroutine primitives for sync threads. |
| **UI Framework** | Jetpack Compose | Declarative UI matching state-driven text nodes; smooth adaptive handling for foldables. |
| **Core Text Component** | `BasicTextField` (Low-Level) | Required for raw canvas drawing and customized text transformations; standard `TextField` is too rigid. |
| **Parser Architecture** | Custom Headless AST Parser | Decoupled pure-Kotlin library layer utilizing paragraph-level indexing to handle inline-reveal rules. |
| **Local Cache Engine** | SQLite via Room DB | Manages internal project structures, file meta-indices, cached metrics, and cloud sync tracking state. |

---

### 4. High-Risk Edge Cases & Mitigations

* **Cursor Index Displacement:** Programmatic insertion of Markdown symbols (`**`) during selection updates shifts string arrays instantly.
    * *Mitigation:* The state management layer must explicitly calculate target offset index adjustments via custom string mapping transformations before re-binding the cursor state inside the `TextFieldValue`.
* **UI Thread Stuttering via Scoped Storage:** Reading massive directory structures over external Android SAF endpoints can freeze frame loops.
    * *Mitigation:* All I/O directory traversals, file reads, and directory mapping pipelines must execute strictly off-thread using `Dispatchers.IO` and stream results reactively via Kotlin asynchronous `Flow` pipelines.
