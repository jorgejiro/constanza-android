@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jjrapps.constanza.core.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Graphite redesign: the top bar every secondary screen shares (editor, progress, settings) — a thin
 * stroke glyph in the leading navigation slot ([com.jjrapps.constanza.core.ui.icons.ConstanzaIcons]'s
 * `ChevronStart` to go back, `Close` to dismiss the editor) and a left-aligned `titleLarge`
 * (22sp/600) title, all on the screen background rather than a raised bar.
 *
 * [navigationLabel] is the glyph's content description; callers pass the existing `action_back`
 * string so tests and TalkBack keep finding the control by the word they always did.
 */
@Composable
fun ScreenTopBar(
    title: @Composable () -> Unit,
    navigationIcon: ImageVector,
    navigationLabel: String,
    onNavigate: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        navigationIcon = {
            IconButton(onClick = onNavigate) { Icon(navigationIcon, contentDescription = navigationLabel) }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}
