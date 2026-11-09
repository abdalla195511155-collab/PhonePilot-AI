package com.phonepilot.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import com.phonepilot.app.ui.theme.Space

@Composable
fun ErrorState(
    message: String = "حدث خطأ",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.xLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.large))
            Text(
                text = message,
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            onRetry?.let {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.xLarge))
                androidx.compose.material3.Button(
                    onClick = it,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(text = "إعادة المحاولة")
                }
            }
        }
    }
}
