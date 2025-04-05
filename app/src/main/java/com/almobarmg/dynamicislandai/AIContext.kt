package com.almobarmg.dynamicislandai

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import dagger.hilt.android.qualifiers.ActivityContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

class AIContext @Inject constructor(@ActivityContext private val context: Context) {
    private val interpreter: Interpreter by lazy {
        try {
            // Load model file using proper TFLite helper methods
            val modelBuffer = FileUtil.loadMappedFile(context, "event_model.tflite")
            Interpreter(modelBuffer).also {
                Timber.i("TensorFlow Lite interpreter initialized successfully")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TensorFlow Lite interpreter")
            throw IllegalStateException("Failed to initialize AI model", e)
        }
    }

    private val threshold = 0.7f // Configurable prediction threshold
    private val dateFormat = SimpleDateFormat("HH:mm")

    @Composable
    fun ContextualDisplay() {
        val event = predictNextEvent()
        if (event != null) {
            Text(
                text = "Next: $event",
                color = Color.White,
                modifier = Modifier.semantics {
                    contentDescription = "Predicted Event: $event"
                }
            )
        }
    }

    // Predict next event using AI model
    private fun predictNextEvent(): String? {
        return try {
            // Prepare input tensor
            val input = floatArrayOf(
                System.currentTimeMillis().toFloat() / 1000,  // Timestamp
                Random.nextFloat() * 10f                      // Simulated context
            )

            // Prepare output tensor
            val output = Array(1) { FloatArray(2) }

            // Run inference
            interpreter.run(input, output)

            // Interpret results
            if (output[0][1] > threshold) {
                "Meeting at ${dateFormat.format(Date())}"
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "AI prediction failed")
            null
        }
    }

    // Clean up resources when done
    fun close() {
        try {
            interpreter.close()
            Timber.i("TensorFlow Lite interpreter closed")
        } catch (e: Exception) {
            Timber.e(e, "Error closing interpreter")
        }
    }
}