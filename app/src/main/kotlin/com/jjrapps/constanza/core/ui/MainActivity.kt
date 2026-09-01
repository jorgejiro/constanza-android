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
import com.jjrapps.constanza.scheduling.ReplanOnResumeObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single launcher Activity. Renders an empty Compose screen — the Today screen (habit list,
 * per-slot rows) is implemented in work unit 6b (design.md §14).
 *
 * The one non-placeholder thing it does is register design.md §5.5/§13.1's `onResume()` re-check
 * (task G.5). The decision logic lives in [ReplanOnResumeObserver], not here: an Activity that
 * renders nothing yet should not be the place scheduling rules are written.
 *
 * Still deferred to work unit 6b, and deliberately NOT built here: §13.1's non-blocking banner
 * explaining that reminders may arrive late, with one tap to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
 * That row of §13.1's table is UI, and this screen has none yet.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var replanOnResumeObserver: ReplanOnResumeObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(replanOnResumeObserver)
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
