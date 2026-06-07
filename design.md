# Application Visual & Interface Design Specifications
## Typography, Density, and Structural Directives

### 1. Typography-First Philosophy
The interface prioritizes reading readability, spatial organization, and intentional density layout choices. It deliberately discards structural mobile framing patterns like standard floating card configurations and visible divider lines, leaning instead on typographic weight and proportional breathing room to separate visual hierarchies.

#### 1.1 Type Scales & Weight Matrix
* **Primary Workspace Font Family:** System Sans-Serif (`Roboto` / `Inter` fallback) for interface elements, metadata headers, and structural configurations.
* **Document Workspace Font Family:** High-Quality Monospace (`JetBrains Mono` or `Roboto Mono`) or premium Serif system options, optimized explicitly for multi-hour writing endurance.
* **Scale Constraints:**
  * `Display Title (H1)`: 22sp / Bold / Tracking: -0.25sp
  * `Section Header (H2)`: 16sp / Medium / Tracking: 0sp
  * `Subsection Header (H3)`: 13sp / Semi-Bold / Tracking: +0.15sp
  * `Body Markdown Workspace`: 15sp / Regular / Line Height: 1.6x (24sp equivalent line bounding)

---

### 2. Layout Modalities & Surface Densities

#### 2.1 Surface Layout Specs
* **Page Margin Default Bounds:** 16dp horizontal structural gutters on typical phone form-factors; scale out to 24dp when running on tablet surfaces or fully unfolded foldable screen panels.
* **Component Padding Systems:** Rely cleanly on an 8dp grid spacing system. Inter-component boundaries map directly to `8dp`, `16dp`, or `24dp` step increments.

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

---

### 3. System Color Palettes & Color Tokens

To allow simple and robust runtime theming (via JSON theme serialization or Jetpack Compose theme states), the interface uses a semantic color token system. UI components must bind to these tokens instead of hardcoded hex values.

| Semantic Token | Description | Default Light Mode | Default Dark Mode |
| :--- | :--- | :--- | :--- |
| `color.background` | Document and workspace main background canvas. | `#FCFBFA` (Warm off-white) | `#121212` (Pure dark depth) |
| `color.surface` | Interface control backing, sidebar, list container surfaces. | `#F4F3F0` (Muted warm backing) | `#1E1E1E` (Raised dark grey) |
| `color.text.primary` | Primary body text and high-contrast title content. | `#1A1A18` (Charcoal black) | `#E3E3E3` (Soft white) |
| `color.text.muted` | Low-priority metadata labels, file paths, line numbers. | `#7C7A75` (Muted warm grey) | `#8C8C8C` (Muted cool grey) |
| `color.accent` | Focus indicators, primary buttons, syntax accent highlights. | `#2E5BFF` (Utility blue) | `#5E81FF` (Bright utility blue) |

---

### 4. Interactive Components & Micro-Interactions

#### 4.1 Selection Floating Toolbar
* **Visual Container:** Borderless, deep rounded geometry (12dp corner rounding values) wrapped with a low-opacity casting ambient drop shadow.
* **Component Interaction:** Animates into view vertically exactly 8dp above the highest point of the text selection bracket boundaries. Button interactions utilize flat monochrome vector glyph representations without internal backgrounds.

#### 4.2 Inline Syntax Transition Reveal
* When a paragraph shifts focus state to active editing, the animation layer must transition raw markdown syntax anchors inline using a direct, instantaneous alpha fade pipeline (0ms to 50ms curve range). Do not inject sliding spatial displacement dampening curves—layout elements must pop or fade directly to avoid tracking errors under active key typing loops.
