package com.attachdesign.kern.parser

enum class MarkdownElementType {
    HEADER_1, HEADER_2, HEADER_3, HEADER_4, HEADER_5, HEADER_6,
    BOLD, ITALIC, STRIKETHROUGH, INLINE_CODE, LINK, BLOCKQUOTE, LIST_BULLET,
    // Syntax tokens (characters to be stripped or styled differently)
    TOKEN_HEADER,
    TOKEN_BOLD,
    TOKEN_ITALIC,
    TOKEN_STRIKETHROUGH,
    TOKEN_INLINE_CODE,
    TOKEN_LINK_TEXT,
    TOKEN_LINK_URL,
    TOKEN_BLOCKQUOTE,
    TOKEN_LIST_BULLET
}

enum class MarkdownBlockType {
    PARAGRAPH,
    HEADER_1, HEADER_2, HEADER_3, HEADER_4, HEADER_5, HEADER_6,
    BLOCKQUOTE,
    UNORDERED_LIST,
    ORDERED_LIST,
    CODE_BLOCK
}

data class MarkdownElement(
    val type: MarkdownElementType,
    val start: Int,
    val end: Int,
    val extra: String? = null
)

data class ParagraphBlock(
    val id: String,
    val rawText: String,
    val blockType: MarkdownBlockType,
    val elements: List<MarkdownElement>
)
