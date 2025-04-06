package com.almobarmg.dynamicislandai

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

class MediaHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mediaSession = MediaSessionCompat(context, "DynamicIslandMedia").apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = Timber.d("Playing")
            override fun onPause() = Timber.d("Paused")
            override fun onSkipToNext() = Timber.d("Next")
            override fun onSkipToPrevious() = Timber.d("Previous")
        })
        isActive = true
    }

    @Composable
    fun MediaControls() {
        var progress by remember { mutableStateOf(0f) }
        var isPlaying by remember { mutableStateOf(false) }
        var duration by remember { mutableStateOf(0L) }

        LaunchedEffect(Unit) {
            while (true) {
                val playbackState = mediaSession.controller.playbackState
                if (playbackState != null) {
                    val position = playbackState.position
                    duration = playbackState.extras?.getLong("duration") ?: 0L
                    if (duration > 0) {
                        progress = (position.toFloat() / duration).coerceIn(0f, 1f)
                    }
                    isPlaying = playbackState.state == android.media.session.PlaybackState.STATE_PLAYING
                }
                delay(1000L)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(8.dp)
                .semantics { contentDescription = "Media Controls" }
        ) {
            IconButton(onClick = {
                if (isPlaying) {
                    mediaSession.controller.transportControls.pause()
                } else {
                    mediaSession.controller.transportControls.play()
                }
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { mediaSession.controller.transportControls.skipToNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next")
            }
            IconButton(onClick = { mediaSession.controller.transportControls.skipToPrevious() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
            }
        }
    }

    fun release() {
        mediaSession.release()
    }
}