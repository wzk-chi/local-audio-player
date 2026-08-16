package com.localaudio.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerIconButton(icon: Int, description: String, modifier: Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier) { Icon(painterResource(icon), contentDescription = description) }
}

@Composable
internal fun PlayerAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(42.dp),
            colors = if (active) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            Icon(painterResource(icon), contentDescription = label)
        }
        Text(
            text = label,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
