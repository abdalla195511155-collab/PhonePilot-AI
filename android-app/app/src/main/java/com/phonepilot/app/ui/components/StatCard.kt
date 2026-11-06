package com.phonepilot.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import com.phonepilot.app.ui.theme.Space

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val contentColor = LocalContentColor.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = contentColor,
        ),
        elevation = Space.cardElevation,
        shape = RoundedCornerShape(Space.cardCorner),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Space.medium, vertical = Space.medium)
                .width(1f.min(0.9f)),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineMedium,
                        color = contentColor,
                        maxLines = 1,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.5f),
                            maxLines = 1,
                        )
                    }
                }
                icon?.let {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(Space.iconSize),
                    )
                }
            }
        }
    }
}
