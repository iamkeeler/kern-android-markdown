package com.example.modernandroidmarkdowneditor.ui.editor

import androidx.compose.runtime.Immutable
import com.example.modernandroidmarkdowneditor.parser.ParagraphBlock
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ImmutableParagraphBlock(
    val block: ParagraphBlock
)

@Immutable
data class ImmutableParagraphList(
    val items: ImmutableList<ImmutableParagraphBlock>
)
