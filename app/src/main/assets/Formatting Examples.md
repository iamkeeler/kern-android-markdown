# Formatting Examples

This document showcases all the markdown formatting styles supported by Kern. Tap any line in the editor to view the raw syntax.

---

## 1. Headers

# Heading 1
## Heading 2
### Heading 3

---

## 2. Text Emphasis

This text is **bold** (or __bold__).
This text is *italic* (or _italic_).
This text is ~~strikethrough~~.

---

## 3. Lists

### Unordered List
* Item A
* Item B
  * Sub-item B1 (indented)

### Ordered List
1. First item
2. Second item
3. Third item

### Task List
- [ ] Uncompleted task item
- [x] Completed task item (strikes through in Rendered view)

---

## 4. Blocks

### Blockquote
> This is a blockquote. It stands out with a left accent line when rendered.

### Code Block
```kotlin
fun main() {
    println("Hello, Kern!")
}
```

### Inline Code
Use `val x = 42` inline inside paragraphs.

---

## 5. Tables

| Element | Format | Status |
| --- | --- | --- |
| Task List | `- [ ]` | Supported |
| Tables | `\|` | Supported |
| Images | `![alt](url)` | Supported |

---

## 6. Links and Media

### Link
[Visit attach.design](https://kern.attach.design)

### Image
![Kern Logo](https://kern.attach.design/logo.png)

---

## 7. Escape Characters

Normally, \*asterisks\* make text italic. But with backslash escaping, you can show literal \*asterisks\* or \_underscores\_ without any formatting!
