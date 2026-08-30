package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.whisper.InferenceBenchmark
import com.example.ui.editor.SpeechRecognitionStatus

@Composable
fun VoiceRecordingHud(
    isRecording: Boolean,
    durationMs: Long,
    amplitude: Float,
    chunkCount: Int,
    speechStatus: SpeechRecognitionStatus = SpeechRecognitionStatus.IDLE_SILENCE,
    lastRecognizedSnippet: String = "",
    latestBenchmark: InferenceBenchmark? = null,
    onStopAndSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minutes = (durationMs / 1000) / 60
    val seconds = (durationMs / 1000) % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    // Dynamic Color Mapping based on recognition state
    val targetPulseColor = when (speechStatus) {
        SpeechRecognitionStatus.WORDS_RECOGNIZED -> Color(0xFF10B981) // Emerald Green (Transcribed words)
        SpeechRecognitionStatus.HEARING_SOUND -> Color(0xFF0284C7)    // Sky/Ocean Blue (Hearing active speech)
        SpeechRecognitionStatus.NO_WORDS_DETECTED -> Color(0xFFEF4444)// Soft Red (No words deciphered)
        SpeechRecognitionStatus.IDLE_SILENCE -> Color(0xFF64748B)     // Cool Slate / Grey (Silence)
    }

    val animatedPulseColor by animateColorAsState(
        targetValue = targetPulseColor,
        animationSpec = tween(durationMillis = 300),
        label = "pulse_color_anim"
    )

    // Breathing pulse for idle recording + dynamic reaction on voice loudness
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    // Voice amplitude directly drives scale multiplier
    val dynamicVoiceScale by animateFloatAsState(
        targetValue = 1.0f + (amplitude * 0.45f),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
        label = "voice_scale"
    )

    val micHaloScale = if (amplitude > 0.08f || speechStatus == SpeechRecognitionStatus.WORDS_RECOGNIZED) {
        dynamicVoiceScale
    } else {
        idlePulse
    }
    val haloAlpha = (0.22f + amplitude * 0.55f).coerceIn(0.18f, 0.85f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A) // Dark slate floating HUD
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("voice_recording_hud")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Pulsing Mic Icon with Adaptive State Halo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier.size(38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer animated reactive halo (Green, Blue, Grey, or Red)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .scale(micHaloScale)
                                .background(animatedPulseColor.copy(alpha = haloAlpha), CircleShape)
                        )
                        // Inner solid mic badge
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(animatedPulseColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Recording",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        )

                        val (statusText, statusColor) = when (speechStatus) {
                            SpeechRecognitionStatus.WORDS_RECOGNIZED -> Pair("✓ Converted to text", Color(0xFF34D399))
                            SpeechRecognitionStatus.HEARING_SOUND -> Pair("● Hearing sound...", Color(0xFF38BDF8))
                            SpeechRecognitionStatus.NO_WORDS_DETECTED -> Pair("✕ No words detected", Color(0xFFF87171))
                            SpeechRecognitionStatus.IDLE_SILENCE -> Pair("○ Silence (Speak to convert)", Color(0xFF94A3B8))
                        }

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Real-time Adaptive Waveform
                AudioWaveformBars(
                    amplitude = amplitude,
                    speechStatus = speechStatus,
                    pulseColor = animatedPulseColor,
                    modifier = Modifier
                        .width(76.dp)
                        .height(28.dp)
                        .padding(horizontal = 4.dp)
                )

                // Stop / Cancel / Done Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_cancel_recording")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(
                        onClick = onStopAndSave,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF00897B), CircleShape)
                            .testTag("btn_done_recording")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = "Finish Dictation",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Live recognized words banner preview (proves conversion to user immediately)
            AnimatedVisibility(
                visible = lastRecognizedSnippet.isNotBlank() && speechStatus == SpeechRecognitionStatus.WORDS_RECOGNIZED,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF064E3B).copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transcribed: \"$lastRecognizedSnippet\"",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA7F3D0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioWaveformBars(
    amplitude: Float,
    speechStatus: SpeechRecognitionStatus,
    pulseColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 7
    // Dynamic multipliers create an organic voice spectrum shape with distinct frequency heights
    val multipliers = floatArrayOf(0.35f, 0.65f, 0.95f, 1.0f, 0.85f, 0.55f, 0.30f)
    val baseNoise = floatArrayOf(0.08f, 0.12f, 0.15f, 0.18f, 0.14f, 0.10f, 0.06f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val dynamicFactor = (amplitude * multipliers[i]) + baseNoise[i]
            val targetHeight = (dynamicFactor * 26.dp.value).coerceIn(3.5f, 26f)

            val animatedHeight by animateFloatAsState(
                targetValue = targetHeight,
                animationSpec = spring(
                    dampingRatio = 0.45f,
                    stiffness = 900f
                ),
                label = "bar_$i"
            )

            val barColor = when (speechStatus) {
                SpeechRecognitionStatus.WORDS_RECOGNIZED -> Brush.verticalGradient(
                    listOf(Color(0xFF34D399), Color(0xFF059669))
                )
                SpeechRecognitionStatus.HEARING_SOUND -> Brush.verticalGradient(
                    listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                )
                SpeechRecognitionStatus.NO_WORDS_DETECTED -> Brush.verticalGradient(
                    listOf(Color(0xFFF87171), Color(0xFFDC2626))
                )
                SpeechRecognitionStatus.IDLE_SILENCE -> Brush.verticalGradient(
                    listOf(Color(0xFF64748B), Color(0xFF334155))
                )
            }

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(animatedHeight.dp)
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }
    }
}
