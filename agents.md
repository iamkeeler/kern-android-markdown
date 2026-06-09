# AI Agent Engineering Rules, Guardrails & Guidelines
## System Context, Architectural Alignment, and Visual Enforcement

### 1. The Decoupled Engineering Rule (Architectural Boundary)
When implementing features defined in the PRD, the implementation must be cleanly severed into two isolation layers:
1. **Headless Domain Processing Module:** Pure Kotlin execution. Zero references to `androidx.compose`, `Context`, `MutableState`, or Android runtime libraries.
2. **Platform Binding & Layout Component Module:** Handles Jetpack Compose recomposition, hardware event bridges, and OS filesystem endpoints.

> **Fatal System Exception:** Do not inject Compose `TextFieldValue` or UI hooks into the text processing engines. The text processing loop must consume standard Kotlin primitives (`String`, `Int`, `List`) and output static Immutable Data Trees or Data Classes. Any attempt to cross-pollinate these layers will result in a fatal script abortion.

---

### 2. Performance & Concurrency Invariants

#### 2.1 Recomposition Optimization Protocols
The text insertion loop operates at a targeted threshold under **11 milliseconds** per character stroke. To guarantee frame drops are prevented:
* All parsed paragraph block representations must pass through Jetpack Compose explicitly wrapped or annotated using `@Immutable` or `@Stable`.
* Do not pass naked `List<T>` properties down the layout tree. Wrap nested structures in custom immutable domain wrappers or leverage `kotlinx.collections.immutable.ImmutableList`.
* Implement differential update loops inside the Text Processor. The view tier must read state slices wrapped in a standard `derivedStateOf {}` scope block to prevent non-impacted screen elements from parsing text changes.

#### 2.2 Storage Stream Concurrency
* The main execution loop is completely restricted from interacting with the file storage system.
* All Android Storage Access Framework (SAF) folder evaluations, recursive subfolder walks, and Room cache metadata scans must execute on bounded thread blocks explicitly bound under `Dispatchers.IO`.
* Expose asynchronous file data structures out to the user interface solely through cold `Flow` pipelines converted to architecture-aware state components using `.stateIn(scope, SharingStarted.WhileSubscribed(5000), initial)`.

---

### 3. State Invariant & Cursor Offset Mechanics
The custom parser alters text representations on the fly when adding markdown delimiters. To prevent cursor snap dislocations and layout indexing fractures:
* Implement an explicit **Index Transformation Matrix** layer. This structural contract translates an absolute presentation index (where the cursor sits inside the displayed text string) to an exact domain indexing pointer (the string coordinate inside the raw file cache).
* When programmatic text wrapping operations change string layout definitions via the selection tray toolbar, the transition sequence must mutate text contents and selection vectors within a singular, atomic state swap instruction.

---

### 4. Interface Density & Aesthetic Constraints
When writing UI bindings, you must adhere strictly to the parameters defined in `design.md`:
* **No Cards/Dividers:** Discard standard mobile framing patterns like floating cards. Visible divider lines are generally avoided, but structural dividers/lines that resemble formatting elements found in printed books (such as thin bookish separator lines) are permitted.
* **Layout Margins:** Enforce an 8dp grid spacing system strictly. Line height for the core editing workspace must be locked at exactly 1.6x.
* **Adaptive Split-States:** Implement the adaptive split-screen layout for width constraints >= 600dp (35% left file tree rail, 65% central document workspace canvas capped at a max text line width of 680dp).
* **Animations:** For inline-reveal animations, use an instantaneous alpha fade pipeline (0ms to 50ms). Do not use sliding or spatial displacement dampening curves.

---

### 5. Runtime Execution Protocol (Self-Review Loop)
* **Testing Priority:** You must author pure-Kotlin JVM unit tests for the core parser and Index Transformation Matrix before writing any Compose layout components.
* **Failure Loop Cap:** If a compilation check, verification test, or asset generation routine fails, you have a maximum of 3 automated modification attempts. On the 4th failure, you must immediately halt all execution loops, emit the complete diagnostic stack trace, and hand control back to the human operator. Do not hallucinate fixes or repeat failing steps.

---

### 6. Git Hygiene & Version Control Protocol
* **Commit After Each Turn:** Commit all working modifications to Git at the end of each turn or completion of a logical sub-task. Commits must be atomic and accompanied by concise, descriptive commit messages.
* **Feature Branching:** Core features must be developed on dedicated feature branches and subsequently merged into the main development branch once verified.
* **Standard Git Hygiene:** Adhere to basic Git hygiene standards as a standard in Antigravity, ensuring clean staging, logical commit history, and prevention of untracked build artifacts or temporary files in the repository.

