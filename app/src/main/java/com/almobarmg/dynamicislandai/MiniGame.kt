package com.almobarmg.dynamicislandai

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import timber.log.Timber

class MiniGame @Inject constructor(@ActivityContext private val context: Context) {
    @Composable
    fun BatteryPong() {
        val ballX = remember { mutableStateOf(50f) }
        val ballY = remember { mutableStateOf(20f) }
        val paddleX = remember { mutableStateOf(50f) }
        val score = remember { mutableStateOf(0) }
        val velocityX = remember { mutableStateOf(2f) }
        val velocityY = remember { mutableStateOf(1f) }

        LaunchedEffect(Unit) {
            while (true) {
                try {
                    ballX.value += velocityX.value
                    ballY.value += velocityY.value
                    if (ballX.value < 0 || ballX.value > 90) velocityX.value = -velocityX.value
                    if (ballY.value < 0) velocityY.value = -velocityY.value
                    if (ballY.value > 80 && ballX.value in paddleX.value..(paddleX.value + 20)) {
                        velocityY.value = -velocityY.value
                        score.value++
                    } else if (ballY.value > 100) {
                        ballY.value = 20f
                        ballX.value = 50f
                        score.value = 0
                    }
                    delay(16) // ~60 FPS
                } catch (e: Exception) {
                    Timber.e(e, "BatteryPong animation failed")
                }
            }
        }

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .size(100.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        paddleX.value = (paddleX.value + dragAmount.x).coerceIn(0f, 70f)
                    }
                }
                .semantics { contentDescription = "Battery Pong Game, Score: ${score.value}" }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(Color.White, 5f, Offset(ballX.value, ballY.value)) // Ball
                drawRect(Color.Green, Offset(paddleX.value, 80f), Size(20f, 10f)) // Paddle
            }
            Text(text = "Score: ${score.value}", color = Color.White)
        }
    }
}
