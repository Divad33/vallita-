package com.vallita.timer

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { VallitaProApp() }
    }
}

private enum class Phase { IDLE, FIRST_HALF, BREAK, SECOND_HALF, OVERTIME, DONE }

@Composable
fun VallitaProApp() {
    MaterialTheme {
        val context = LocalContext.current
        val haptics = LocalHapticFeedback.current
        val vibrator = remember { context.getSystemService(Vibrator::class.java) }

        val soundPool = remember {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(attrs)
                .build()
        }

        // FIFA-style: aviso 1:00 = silbato corto; 0:00 = silbato largo; final = largo + corto ("piii... pi")
        val whistleLongId = remember { soundPool.load(context, R.raw.whistle_long, 1) }
        val whistleShortId = remember { soundPool.load(context, R.raw.whistle_short, 1) }

        fun vibrateShort() {
            val effect = VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        }

        fun vibrateLong() {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 220, 120, 220, 120, 320), -1)
            vibrator.vibrate(effect)
        }

        fun playShortWhistle() {
            soundPool.play(whistleShortId, 1f, 1f, 1, 0, 1.0f)
            vibrateShort()
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        fun playLongWhistle() {
            soundPool.play(whistleLongId, 1f, 1f, 1, 0, 1.0f)
            vibrateLong()
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        suspend fun finalDoubleWhistle() {
            playLongWhistle()   // piii
            delay(650)
            playShortWhistle()  // pi
        }

        // Duraciones (minutos)
        val halfPresets = listOf(7, 10, 12, 15, 20, 25, 30, 40, 45, 60, 90)
        val breakPresets = listOf(1, 2, 3, 5, 10, 15)
        val otPresets = listOf(1, 2, 3, 5, 10, 15)

        var halfMinutes by remember { mutableStateOf(10) }
        var breakMinutes by remember { mutableStateOf(5) }

        var useCustomHalf by remember { mutableStateOf(false) }
        var customHalfMinutes by remember { mutableStateOf(10) }

        var useCustomBreak by remember { mutableStateOf(false) }
        var customBreakMinutes by remember { mutableStateOf(5) }

        // Overtime opcional
        var overtimeEnabled by remember { mutableStateOf(false) }
        var overtimeMinutes by remember { mutableStateOf(5) }
        var useCustomOt by remember { mutableStateOf(false) }
        var customOtMinutes by remember { mutableStateOf(5) }

        val halfTotalMs = remember(halfMinutes, customHalfMinutes, useCustomHalf) {
            val mins = if (useCustomHalf) customHalfMinutes else halfMinutes
            (mins * 60_000L).coerceAtLeast(10_000L)
        }
        val breakTotalMs = remember(breakMinutes, customBreakMinutes, useCustomBreak) {
            val mins = if (useCustomBreak) customBreakMinutes else breakMinutes
            (mins * 60_000L).coerceAtLeast(10_000L)
        }
        val otTotalMs = remember(overtimeMinutes, customOtMinutes, useCustomOt) {
            val mins = if (useCustomOt) customOtMinutes else overtimeMinutes
            (mins * 60_000L).coerceAtLeast(10_000L)
        }

        var phase by remember { mutableStateOf(Phase.IDLE) }
        var running by remember { mutableStateOf(false) }
        var remainingMs by remember { mutableLongStateOf(halfTotalMs) }
        var lastTickMs by remember { mutableLongStateOf(0L) }
        var warnedThisPhase by remember { mutableStateOf(false) }

        fun phaseLabel(p: Phase) = when (p) {
            Phase.IDLE -> "LISTO"
            Phase.FIRST_HALF -> "1ER TIEMPO"
            Phase.BREAK -> "DESCANSO"
            Phase.SECOND_HALF -> "2DO TIEMPO"
            Phase.OVERTIME -> "OVERTIME"
            Phase.DONE -> "FINAL"
        }

        fun durationForPhase(p: Phase): Long = when (p) {
            Phase.BREAK -> breakTotalMs
            Phase.OVERTIME -> otTotalMs
            Phase.IDLE, Phase.FIRST_HALF, Phase.SECOND_HALF -> halfTotalMs
            Phase.DONE -> 0L
        }

        fun setPhase(newPhase: Phase) {
            phase = newPhase
            warnedThisPhase = false
            remainingMs = durationForPhase(newPhase)
        }

        LaunchedEffect(halfTotalMs, breakTotalMs, otTotalMs) {
            if (!running) remainingMs = durationForPhase(phase)
        }

        fun startSequenceIfIdle() {
            if (phase == Phase.IDLE || phase == Phase.DONE) setPhase(Phase.FIRST_HALF)
            running = true
            lastTickMs = System.currentTimeMillis()
        }

        fun canWarnOneMinute(p: Phase) = (p == Phase.FIRST_HALF || p == Phase.SECOND_HALF)

        suspend fun advancePhaseAutomatically() {
            when (phase) {
                Phase.FIRST_HALF -> {
                    playLongWhistle()
                    setPhase(Phase.BREAK)
                    running = true
                    lastTickMs = System.currentTimeMillis()
                }
                Phase.BREAK -> {
                    playShortWhistle()
                    setPhase(Phase.SECOND_HALF)
                    running = true
                    lastTickMs = System.currentTimeMillis()
                }
                Phase.SECOND_HALF -> {
                    if (overtimeEnabled) {
                        playLongWhistle()
                        setPhase(Phase.OVERTIME)
                        running = true
                        lastTickMs = System.currentTimeMillis()
                    } else {
                        finalDoubleWhistle()
                        phase = Phase.DONE
                        remainingMs = 0L
                        running = false
                    }
                }
                Phase.OVERTIME -> {
                    finalDoubleWhistle()
                    phase = Phase.DONE
                    remainingMs = 0L
                    running = false
                }
                else -> running = false
            }
        }

        LaunchedEffect(running) {
            if (running) lastTickMs = System.currentTimeMillis()

            while (running) {
                val now = System.currentTimeMillis()
                val delta = now - lastTickMs
                lastTickMs = now

                remainingMs = max(0L, remainingMs - delta)

                // Aviso FIFA a 1:00 (silbato corto) solo en 1er/2do tiempo
                if (!warnedThisPhase && canWarnOneMinute(phase) && remainingMs in 1..60_000L) {
                    warnedThisPhase = true
                    playShortWhistle()
                }

                if (remainingMs <= 0L) {
                    running = false
                    advancePhaseAutomatically()
                }

                delay(50)
            }
        }

        val timeText = remember(remainingMs) { formatMMSS(remainingMs) }
        val canEditDurations = !running && (phase == Phase.IDLE || phase == Phase.DONE)

        Scaffold(timeText = { TimeText() }) {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = phaseLabel(phase),
                    style = MaterialTheme.typography.caption1,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = timeText,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.display1,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Text("Tiempo (min)", style = MaterialTheme.typography.caption1, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                PresetRow(halfPresets.take(4), canEditDurations) { mins ->
                    useCustomHalf = false; halfMinutes = mins
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                Spacer(Modifier.height(6.dp))
                PresetRow(halfPresets.drop(4).take(4), canEditDurations) { mins ->
                    useCustomHalf = false; halfMinutes = mins
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                Spacer(Modifier.height(6.dp))
                PresetRow(halfPresets.drop(8), canEditDurations) { mins ->
                    useCustomHalf = false; halfMinutes = mins
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }

                Spacer(Modifier.height(8.dp))
                StepperRow(
                    title = "Custom: ${customHalfMinutes}m",
                    enabled = canEditDurations,
                    onMinus = { useCustomHalf = true; customHalfMinutes = (customHalfMinutes - 1).coerceAtLeast(1) },
                    onPlus = { useCustomHalf = true; customHalfMinutes = (customHalfMinutes + 1).coerceAtMost(180) },
                    onCenter = { useCustomHalf = true }
                )

                Spacer(Modifier.height(10.dp))

                Text("Descanso (min)", style = MaterialTheme.typography.caption1, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                PresetRow(breakPresets, canEditDurations) { mins ->
                    useCustomBreak = false; breakMinutes = mins
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }

                Spacer(Modifier.height(8.dp))
                StepperRow(
                    title = "Custom: ${customBreakMinutes}m",
                    enabled = canEditDurations,
                    onMinus = { useCustomBreak = true; customBreakMinutes = (customBreakMinutes - 1).coerceAtLeast(1) },
                    onPlus = { useCustomBreak = true; customBreakMinutes = (customBreakMinutes + 1).coerceAtMost(60) },
                    onCenter = { useCustomBreak = true }
                )

                Spacer(Modifier.height(10.dp))

                Text("Overtime", style = MaterialTheme.typography.caption1, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))

                Chip(
                    onClick = {
                        if (canEditDurations) {
                            overtimeEnabled = !overtimeEnabled
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    enabled = canEditDurations,
                    label = { Text(if (overtimeEnabled) "ON" else "OFF") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(6.dp))

                PresetRow(otPresets, canEditDurations && overtimeEnabled) { mins ->
                    useCustomOt = false; overtimeMinutes = mins
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }

                Spacer(Modifier.height(8.dp))
                StepperRow(
                    title = "Custom: ${customOtMinutes}m",
                    enabled = canEditDurations && overtimeEnabled,
                    onMinus = { useCustomOt = true; customOtMinutes = (customOtMinutes - 1).coerceAtLeast(1) },
                    onPlus = { useCustomOt = true; customOtMinutes = (customOtMinutes + 1).coerceAtMost(60) },
                    onCenter = { useCustomOt = true }
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (running) running = false else startSequenceIfIdle()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (running) "PAUSE" else "START") }

                    Button(
                        onClick = {
                            remainingMs += 10_000L
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        enabled = phase != Phase.IDLE && phase != Phase.DONE,
                        modifier = Modifier.weight(1f)
                    ) { Text("+10s") }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            running = false
                            phase = Phase.IDLE
                            warnedThisPhase = false
                            remainingMs = halfTotalMs
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("RESET") }

                    OutlinedButton(
                        onClick = { playLongWhistle() },
                        modifier = Modifier.weight(1f)
                    ) { Text("WHISTLE") }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose { soundPool.release() }
        }
    }
}

@Composable
private fun PresetRow(items: List<Int>, enabled: Boolean, onPick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { mins ->
            Chip(
                onClick = { onPick(mins) },
                label = { Text("${mins}m") },
                modifier = Modifier.weight(1f),
                enabled = enabled
            )
        }
    }
}

@Composable
private fun StepperRow(
    title: String,
    enabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onCenter: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { onMinus(); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            enabled = enabled
        ) { Text("-") }

        Chip(
            onClick = { onCenter(); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            label = { Text(title) },
            modifier = Modifier.weight(1f),
            enabled = enabled
        )

        Button(
            onClick = { onPlus(); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            enabled = enabled
        ) { Text("+") }
    }
}

private fun formatMMSS(ms: Long): String {
    val totalSec = ms / 1000
    val sec = totalSec % 60
    val min = totalSec / 60
    return "%02d:%02d".format(min, sec)
}
