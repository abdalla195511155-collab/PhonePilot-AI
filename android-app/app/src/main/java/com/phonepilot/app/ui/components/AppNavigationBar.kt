package com.phonepilot.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import com.phonepilot.app.ui.theme.Space

data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun AppNavigationBar(
    items: List<NavItem>,
    selectedRoute: String,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(bottom = Space.medium),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        elevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val isSelected = selectedRoute == item.route
                Column(
                    modifier = Modifier
                        .clickable { onItemClick(item.route) }
                        .padding(vertical = Space.xSmall, horizontal = Space.xSmall),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.label,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
