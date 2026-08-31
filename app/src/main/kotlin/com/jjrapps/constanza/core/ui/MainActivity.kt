package com.jjrapps.constanza.core.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjrapps.constanza.core.ui.theme.ConstanzaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single launcher Activity. Renders an empty Compose screen — the Today screen (habit list,
 * per-slot rows) is implemented in work unit 6b (design.md §14).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ConstanzaTheme {
                EmptyScreen()
            }
        }
    }
}

@Composable
private fun EmptyScreen() {
    Surface {
        Box(modifier = Modifier.fillMaxSize())
    }
}
