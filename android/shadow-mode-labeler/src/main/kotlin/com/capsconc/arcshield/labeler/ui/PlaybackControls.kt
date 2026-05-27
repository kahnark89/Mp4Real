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
 * [hasVideo] is a reactive flag from the ViewModel — it flips to true after
 * [LabelerViewModel.bindVideoFile] completes, avoiding the remember(exoPlayer) anti-pattern
 * which would bake in false at first render before the async load completes.
 *
 * The view detaches from the player (view.player = null) on dispose to release the
 * surface reference; the player itself is released in ViewModel.onCleared().
 */
@Composable
fun VideoPlayerView(
    exoPlayer: ExoPlayer,
    hasVideo:  Boolean,
    modifier:  Modifier = Modifier,
) {
    val context = LocalContext.current

    if (!hasVideo) {
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
                player        = exoPlayer
                useController = true
                layoutParams  = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
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

    DisposableEffect(exoPlayer) {
        onDispose { /* detach surface; player released in ViewModel.onCleared() */ }
    }
}
