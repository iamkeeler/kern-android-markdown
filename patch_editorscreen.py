import re

with open("app/src/main/java/com/attachdesign/kern/ui/editor/EditorScreen.kt", "r") as f:
    content = f.read()

# Add fontSizeScale to EditorCanvas signature
content = content.replace(
    "fun EditorCanvas(\n    state: EditorUiState,\n    textFieldValues: Map<Int, TextFieldValue>,\n    viewModel: EditorViewModel\n)",
    "fun EditorCanvas(\n    state: EditorUiState,\n    textFieldValues: Map<Int, TextFieldValue>,\n    viewModel: EditorViewModel\n)"
) # It's already passing state, so we can access state.editorFontSizeScale inside EditorCanvas and pass it

# Modify EditorCanvas to pass state.editorFontSizeScale to ParagraphField
content = content.replace(
    "            ParagraphField(\n                value = value,\n                onValueChange = { viewModel.updateParagraph(index, it) },\n                isFocused = isFocused,\n                onFocusChanged = { focused ->\n                    if (focused) viewModel.setParagraphFocus(index)\n                },\n                visualTransformation = visualTransformation,\n                theme = state.activeTheme,\n                blockType = paragraph.blockType,\n                viewMode = state.viewMode,\n",
    "            ParagraphField(\n                value = value,\n                onValueChange = { viewModel.updateParagraph(index, it) },\n                isFocused = isFocused,\n                onFocusChanged = { focused ->\n                    if (focused) viewModel.setParagraphFocus(index)\n                },\n                visualTransformation = visualTransformation,\n                theme = state.activeTheme,\n                blockType = paragraph.blockType,\n                viewMode = state.viewMode,\n                fontSizeScale = state.editorFontSizeScale,\n"
)

# Modify ParagraphField signature
content = content.replace(
    "    blockType: MarkdownBlockType,\n    viewMode: ViewMode,\n    onEnterPressed: (Int) -> Unit,\n    onBackspacePressed: () -> Unit\n) {",
    "    blockType: MarkdownBlockType,\n    viewMode: ViewMode,\n    fontSizeScale: Float = 1.0f,\n    onEnterPressed: (Int) -> Unit,\n    onBackspacePressed: () -> Unit\n) {"
)

# Modify ParagraphField textStyle
old_text_style = """    val textStyle = if (viewMode == ViewMode.RAW_PLAIN_TEXT) {
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = theme.textPrimary, lineHeight = 20.sp)
    } else {
        when (blockType) {
            MarkdownBlockType.HEADER_1 -> TextStyle(fontFamily = editorFont, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary, lineHeight = 30.sp)
            MarkdownBlockType.HEADER_2 -> TextStyle(fontFamily = editorFont, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary, lineHeight = 26.sp)
            MarkdownBlockType.HEADER_3 -> TextStyle(fontFamily = editorFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary, lineHeight = 22.sp)
            MarkdownBlockType.HEADER_4 -> TextStyle(fontFamily = editorFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary, lineHeight = 20.sp)
            MarkdownBlockType.HEADER_5 -> TextStyle(fontFamily = editorFont, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = theme.textPrimary, lineHeight = 18.sp)
            MarkdownBlockType.HEADER_6 -> TextStyle(fontFamily = editorFont, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = theme.textPrimary, lineHeight = 17.sp)
            MarkdownBlockType.CODE_BLOCK -> TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = theme.textPrimary, lineHeight = 19.sp)
            MarkdownBlockType.BLOCKQUOTE -> TextStyle(fontFamily = editorFont, fontSize = 15.sp, fontStyle = FontStyle.Italic, color = theme.textMuted, lineHeight = 24.sp)
            else -> TextStyle(fontFamily = editorFont, fontSize = 15.sp, color = theme.textPrimary, lineHeight = 24.sp) // 1.6x Line height (15sp body)
        }
    }"""

new_text_style = """    val textStyle = if (viewMode == ViewMode.RAW_PLAIN_TEXT) {
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = (14 * fontSizeScale).sp, color = theme.textPrimary, lineHeight = (20 * fontSizeScale).sp)
    } else {
        when (blockType) {
            MarkdownBlockType.HEADER_1 -> TextStyle(fontFamily = editorFont, fontSize = (22 * fontSizeScale).sp, fontWeight = FontWeight.Bold, color = theme.textPrimary, lineHeight = (30 * fontSizeScale).sp)
            MarkdownBlockType.HEADER_2 -> TextStyle(fontFamily = editorFont, fontSize = (18 * fontSizeScale).sp, fontWeight = FontWeight.Bold, color = theme.textPrimary, lineHeight = (26 * fontSizeScale).sp)
            MarkdownBlockType.HEADER_3 -> TextStyle(fontFamily = editorFont, fontSize = (15 * fontSizeScale).sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary, lineHeight = (22 * fontSizeScale).sp)
            MarkdownBlockType.HEADER_4 -> TextStyle(fontFamily = editorFont, fontSize = (14 * fontSizeScale).sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary, lineHeight = (20 * fontSizeScale).sp)
            MarkdownBlockType.HEADER_5 -> TextStyle(fontFamily = editorFont, fontSize = (13 * fontSizeScale).sp, fontWeight = FontWeight.Medium, color = theme.textPrimary, lineHeight = (18 * fontSizeScale).sp)
            MarkdownBlockType.HEADER_6 -> TextStyle(fontFamily = editorFont, fontSize = (12 * fontSizeScale).sp, fontWeight = FontWeight.Medium, color = theme.textPrimary, lineHeight = (17 * fontSizeScale).sp)
            MarkdownBlockType.CODE_BLOCK -> TextStyle(fontFamily = FontFamily.Monospace, fontSize = (13 * fontSizeScale).sp, color = theme.textPrimary, lineHeight = (19 * fontSizeScale).sp)
            MarkdownBlockType.BLOCKQUOTE -> TextStyle(fontFamily = editorFont, fontSize = (15 * fontSizeScale).sp, fontStyle = FontStyle.Italic, color = theme.textMuted, lineHeight = (24 * fontSizeScale).sp)
            else -> TextStyle(fontFamily = editorFont, fontSize = (15 * fontSizeScale).sp, color = theme.textPrimary, lineHeight = (24 * fontSizeScale).sp) // 1.6x Line height (15sp body)
        }
    }"""

content = content.replace(old_text_style, new_text_style)

with open("app/src/main/java/com/attachdesign/kern/ui/editor/EditorScreen.kt", "w") as f:
    f.write(content)
