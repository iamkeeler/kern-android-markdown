package com.attachdesign.kern.ui.editor

import androidx.compose.runtime.Immutable
import com.attachdesign.kern.parser.ParagraphBlock
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ImmutableParagraphBlock(
    val block: ParagraphBlock
)

@Immutable
data class ImmutableParagraphList(
    val items: ImmutableList<ImmutableParagraphBlock>
)
