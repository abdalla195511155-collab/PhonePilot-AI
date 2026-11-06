package com.phonepilot.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phonepilot.app.ui.theme.Space

@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    onNavigateUp: (() -> Unit)? = null,
    floatingActionButton: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = {
            if (title != null) {
                PhonePilotTopAppBar(title = title, onNavigateUp = onNavigateUp)
            }
        },
        floatingActionButton = floatingActionButton,
    ) { paddingValues ->
        content(
            paddingValues + WindowInsets.safeDrawing.padding(WindowInsetsSides.all)
        )
    }
}

@Composable
private fun PhonePilotTopAppBar(
    title: String,
    onNavigateUp: (() -> Unit)?,
) {
    TopAppBar(
        modifier = Modifier.fillMaxWidth(),
        title = { Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall) },
        colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        ),
        elevation = 0.dp,
        navigationIcon = {
            onNavigateUp?.let {
                androidx.compose.material3.IconButton(onClick = it) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                        contentDescription = null,
                    )
                }
            }
        },
    )
}
