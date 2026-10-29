# Application Visual & Interface Design Specifications

Kern is a quiet, typography-led native Android writing workspace. Refinement preserves its cream, charcoal and rust identity, existing font families, navigation hierarchy, and bookish structural lines.

## Palette and controls

- Cream & Charcoal: background `#F7F3EB`, surface `#EDE8DC`, ink `#1C1C1A`, raw muted ink `#7A7060`, raw rust accent `#C8541A`.
- Inky Charcoal: background `#1C1C1A`, surface `#242420`, ink `#F5F0E8`, raw muted ink `#A89F8C`, raw rust accent `#C8541A`.
- Bind active text and indicators through the accessible presentation roles described below. Raw declarations remain compatible with saved and imported JSON.
- Prefer Material 3 app bars, menus, buttons, tabs, dialogs, selection controls, progress indicators and insets. Native focus, text selection, IME and accessibility behavior remain platform-owned. Document product requirements for custom behavior.
- Active controls retain native minimum touch targets of 48dp. Compound settings expose one row action with passive child indicators. Long labels wrap within the available pane.
- Root theme ownership controls status/navigation bars on every route. Preserve the 2dp header divider and established structural tokens.

## Layout and motion

Use one outer gutter per host: 16dp on compact panes and 24dp on wide hosts. Embedded Settings and metrics inherit the host gutter. Preserve established component spacing and avoid a broad grid or animation rewrite. Responsive decisions use actual pane constraints and system font scaling. Native motion honors the platform animation-duration setting.

## Typography, Density, and Structural Directives

### 1. Typography-First Philosophy
The interface prioritizes reading readability, spatial organization, and intentional density layout choices. It deliberately discards structural mobile framing patterns like standard floating card configurations and visible divider lines, leaning instead on typographic weight and proportional breathing room to separate visual hierarchies.

#### 1.1 Type Scales & Weight Matrix
* **Primary Workspace Font Family:** System Sans-Serif (`Roboto`) for interface elements, metadata headers, and structural configurations.
* **Document Workspace Font Family:** High-Quality Monospace (`JetBrains Mono` or `Roboto Mono`) or premium Serif system options, optimized explicitly for multi-hour writing endurance.
* **Scale Constraints:**
  * `Display Title (H1)`: 22sp / Bold / Tracking: -0.25sp
  * `Section Header (H2)`: 16sp / Medium / Tracking: 0sp
  * `Subsection Header (H3)`: 13sp / Semi-Bold / Tracking: +0.15sp
  * `Body Markdown Workspace`: 16sp Medium / Regular / Line Height: 1.6x (25.6sp before user/system scaling)

### 2. Layout Modalities & Surface Densities

#### 2.1 Surface Layout Specs
* **Page Margin Default Bounds:** 16dp horizontal structural gutters on typical phone form-factors; scale out to 24dp when running on tablet surfaces or fully unfolded foldable screen panels.
* **Component Padding Systems:** Preserve established `4dp`, `8dp`, `12dp`, `16dp`, and `24dp` spacing tokens. Each host owns one outer gutter; embedded content must not duplicate it.

#### 2.2 The Breadcrumb Header (The Navigation Nexus)
The primary system navigation relies on an integrated, high-density breadcrumb pathway anchored inside the Top App Bar.
* **Visual Representation:** `ProjectName › ParentDirectory › active_file.md`
* **Interaction Elements:** The layout elements are rendered inline using a singular, flat string look but respond independently to pointer touches.
* **State Signalling:** Structural directory anchors utilize muted neutral values. The active editing node switches to high-contrast monochrome values.

#### 2.3 Adaptive Workspace Split-States (Dual-Pane Configuration)
* **Trigger Threshold:** Active when the layout detection mechanisms record an available width constraint greater than or equal to `600dp`.
* **The Structural Split:**
  * **Left Component (35% Width Range):** The Project Hierarchy Explorer pane. Features a clean, unbordered file tree displaying document items with precise 8dp vertical spacing boundaries. Background surfaces match the primary system backing color.
  * **Right Component (65% Width Range):** The Core Document Workspace Canvas. Positioned as a linear block container with central alignment bounds. Max text line width values are constrained to a `680dp` bounding box to preserve optimal reading scan lines.

### 3. System Color Palettes & Color Tokens

To allow simple and robust runtime theming (via JSON theme serialization or Jetpack Compose theme states), the interface uses a semantic color token system. UI components must bind to these tokens instead of hardcoded hex values.

| Semantic Token | Description | Default Light Mode | Default Dark Mode |
| :--- | :--- | :--- | :--- |
| `color.background` | Document and workspace main background canvas. | `#F7F3EB` (Warm off-white) | `#1C1C1A` (Pure dark depth) |
| `color.surface` | Interface control backing, sidebar, list container surfaces. | `#EDE8DC` (Muted warm backing) | `#242420` (Raised dark grey) |
| `color.text.primary` | Primary body text and high-contrast title content. | `#1C1C1A` (Charcoal black) | `#F5F0E8` (Soft white) |
| `color.text.muted` | Low-priority metadata labels, file paths, line numbers. | `#7A7060` (Muted warm grey) | `#A89F8C` (Muted cool grey) |
| `color.accent` | Focus indicators, primary buttons, syntax accent highlights. | `#C8541A` (Rust accent) | `#C8541A` (Rust accent) |

### Accessible presentation roles

Raw palette values remain import/export compatible. Use derived `textSecondary`, `textAccent`, and `textDanger` for active text; `controlAccent`, `onControlAccent`, and `indicator` for controls. Built-in active text requires 4.5:1 contrast and indicators 3:1 on their actual composited surfaces. Do not force the raw accent to serve both text and filled-control roles. Material role mapping and root system bars follow the active theme.

The editor uses its dedicated 16sp token; the shared interface body remains 13sp. Explanatory text uses 13sp/18sp helper tokens. Saved editor multipliers remain 0.8/1.0/1.2/1.4; preserve heading/code ratios, source, selection, and undo during typography changes. Native document decoration owns focusable whitespace and centers text at a maximum 680dp measure.

## 7. Interface Density & Aesthetic Constraints
When writing UI bindings, you must adhere strictly to the parameters defined in this document:
* **No Cards/Dividers:** Discard standard mobile framing patterns like floating cards. Visible divider lines are generally avoided, but structural dividers/lines that resemble formatting elements found in printed books (such as thin bookish separator lines) are permitted.
* **Layout Margins:** Use the established spacing tokens. Body editor line height is 1.6x; heading paragraph metrics grow to accommodate their larger runs.
* **Adaptive Split-States:** Implement the adaptive split-screen layout for width constraints >= 600dp (35% left file tree rail, 65% central document workspace canvas capped at a max text line width of 680dp).
* **Animations:** For inline-reveal animations, use an instantaneous alpha fade pipeline (0ms to 50ms). Do not use sliding or spatial displacement dampening curves.

### Foldable companion extension

- While explicitly open, the companion extends the legacy 35/65 navigation layout into a balanced writer/supporting pair through Material 3 `SupportingPaneScaffold` and `AnimatedPane`, respecting separating hinge bounds. Do not add a third column to a book-style device.
- Its 720dp/340dp eligibility and large-font adjustment are separate from the 600dp sidebar breakpoint. Suspend the companion when it cannot remain readable; preserve its mode/session for re-expansion.
- Keep a stable primary editor composition and `TextFieldState`. Native pane bounds animation must not recreate or scale the editable text buffer. Standard Compose motion observes the platform animation-duration setting.
- An armed fold may draw a restrained hinge cue; raw angles must not drive text measurement or parsing. Commit only after reopening. Use one platform haptic at recognition, honoring system preferences.
- Fold feedback uses the native confirmation haptic and an optional short paper-crease sound. Haptics and sound have independent persistent controls; sound is off initially. A Try feedback action previews them without changing layout. Audio must respect silent/vibrate mode, DND, system touch sounds and volume, and must stop when backgrounded. Never queue a cue until an asynchronous sound load completes.
- Use current theme tokens and root-owned system bars. The second editor's file selection, open/close labels, and a visible manual command must remain accessible.

## 8. Performance & Concurrency Invariants
The text insertion loop operates at a targeted threshold under **11 milliseconds** per character stroke. To guarantee frame drops are prevented:
* All parsed paragraph block representations must pass through Jetpack Compose explicitly wrapped or annotated using `@Immutable` or `@Stable`.
* Do not pass naked `List<T>` properties down the layout tree. Wrap nested structures in custom immutable domain wrappers or leverage `kotlinx.collections.immutable.ImmutableList`.
* Implement differential update loops inside the Text Processor. The view tier must read state slices wrapped in a standard `derivedStateOf {}` scope block to prevent non-impacted screen elements from parsing text changes.

## 9. State Invariant & Cursor Offset Mechanics
The custom parser alters text representations on the fly when adding markdown delimiters. To prevent cursor snap dislocations and layout indexing fractures:
* Implement an explicit **Index Transformation Matrix** layer. This structural contract translates an absolute presentation index (where the cursor sits inside the displayed text string) to an exact domain indexing pointer (the string coordinate inside the raw file cache).
* When programmatic text wrapping operations change string layout definitions via the selection tray toolbar, the transition sequence must mutate text contents and selection vectors within a singular, atomic state swap instruction.

### 4. Interactive Components & Micro-Interactions

#### 4.1 Selection Floating Toolbar
* **Visual Container:** Borderless, deep rounded geometry (12dp corner rounding values) wrapped with a low-opacity casting ambient drop shadow.
* **Component Interaction:** Animates into view vertically exactly 8dp above the highest point of the text selection bracket boundaries. Button interactions utilize flat monochrome vector glyph representations without internal backgrounds.

#### 4.2 Inline Syntax Transition Reveal
* When a paragraph shifts focus state to active editing, the animation layer must transition raw markdown syntax anchors inline using a direct, instantaneous alpha fade pipeline (0ms to 50ms curve range). Do not inject sliding spatial displacement dampening curves—layout elements must pop or fade directly to avoid tracking errors under active key typing loops.
