package com.attachdesign.kern.ui.main.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.attachdesign.kern.data.storage.VfsNode
import com.attachdesign.kern.ui.theme.AppColorTheme

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NodeActionsDialog(
    node: VfsNode,
    theme: AppColorTheme,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = theme.surface) {
        Text(
            text = node.name,
            color = theme.textPrimary,
            fontSize = theme.typography.subtitle,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = theme.dimensions.spacingExtraLarge, vertical = theme.dimensions.spacingMedium)
        )
        listOf("duplicate" to "Duplicate", "rename" to "Rename", "share" to "Share", "delete" to "Delete").forEach { (action, label) ->
            ListItem(
                headlineContent = { Text(label, color = if (action == "delete") theme.danger else theme.textPrimary) },
                modifier = Modifier.fillMaxWidth().clickable { onAction(action) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
    }
}
