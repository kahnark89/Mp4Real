package com.capsconc.arcshield.labeler.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Embeds an ExoPlayer [PlayerView] in Compose for offline playback elicitation (W-015).
 *
 * The [exoPlayer] instance is owned by [LabelerViewModel] and survives recomposition.
 * The view is attached/detached via [DisposableEffect] — it does not release the player.
 *
 * If no media is loaded, a "No recording found" placeholder is shown instead.
 */
@Composable
fun VideoPlayerView(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasMedia = remember(exoPlayer) { exoPlayer.mediaItemCount > 0 }

    if (!hasMedia) {
        Box(
            modifier         = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No recording found for this shift log.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player     = exoPlayer
                useController = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
        },
        update = { view ->
            if (view.player !== exoPlayer) view.player = exoPlayer
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
    )

    // Detach player from view when the composable leaves composition, but do NOT
    // release it — the ViewModel owns the player lifecycle.
    DisposableEffect(exoPlayer) {
        onDispose { /* player released in ViewModel.onCleared() */ }
    }
}
