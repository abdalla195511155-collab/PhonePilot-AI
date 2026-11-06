package com.phonepilot.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phonepilot.app.ui.theme.Space

@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    text: String,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(Space.buttonHeight),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            disabledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            disabledContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
    ) {
        Text(text = text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}
