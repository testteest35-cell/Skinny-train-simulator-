package com.example.sim

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Procedural Audio Synthesizer (Zero-Stutter, Zero External Audio Files).
 * Uses pre-allocated PCM buffers for Horn, Bell, Wheel-Slip, Brake Squeal, and Coupler Clank.
 */
class TrainAudioSynthesizer {
    private val sampleRate = 22050
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var muted: Boolean = false
    var masterVolume: Float = 0.75f

    // Pre-allocated PCM buffers at boot (Memory Mandate: no allocations during play)
    private val hornBuffer: ShortArray = synthesizeHornChord(durationMs = 650)
    private val bellBuffer: ShortArray = synthesizeBellChime(durationMs = 420)
    private val slipBuffer: ShortArray = synthesizeWheelSlip(durationMs = 350)
    private val clankBuffer: ShortArray = synthesizeCouplerClank(durationMs = 240)
    private val brakeBuffer: ShortArray = synthesizeBrakeSqueal(durationMs = 380)

    fun playHorn() {
        if (muted) return
        playPcmBuffer(hornBuffer, masterVolume)
    }

    fun playBell() {
        if (muted) return
        playPcmBuffer(bellBuffer, masterVolume * 0.8f)
    }

    fun playWheelSlip() {
        if (muted) return
        playPcmBuffer(slipBuffer, masterVolume * 0.7f)
    }

    fun playCouplerClank() {
        if (muted) return
        playPcmBuffer(clankBuffer, masterVolume * 0.85f)
    }

    fun playBrakeSqueal() {
        if (muted) return
        playPcmBuffer(brakeBuffer, masterVolume * 0.6f)
    }

    private fun playPcmBuffer(buffer: ShortArray, volume: Float) {
        audioScope.launch {
            try {
                val byteCount = buffer.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteCount)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.setVolume(volume.coerceIn(0f, 1f))
                track.play()
            } catch (_: Exception) {
                // Resilient audio fallback if hardware channel pool is full
            }
        }
    }

    // Nathan K5LA-inspired 3-chime diesel air horn (D#4, F#4, B4)
    private fun synthesizeHornChord(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        val f1 = 311.13
        val f2 = 369.99
        val f3 = 493.88
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = when {
                i < count * 0.08 -> i / (count * 0.08)
                i > count * 0.82 -> (count - i) / (count * 0.18)
                else -> 1.0
            }
            val wave = (sin(2.0 * PI * f1 * t) +
                0.85 * sin(2.0 * PI * f2 * t) +
                0.75 * sin(2.0 * PI * f3 * t) +
                0.30 * sin(4.0 * PI * f1 * t)) / 2.9
            out[i] = (wave * env * 24000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthesizeBellChime(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        val f1 = 880.0
        val f2 = 1318.5
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = kotlin.math.exp(-6.5 * t)
            val wave = (0.7 * sin(2.0 * PI * f1 * t) + 0.3 * sin(2.0 * PI * f2 * t))
            out[i] = (wave * env * 22000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthesizeWheelSlip(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val freq = 1800.0 + 400.0 * sin(2.0 * PI * 18.0 * t)
            val env = 1.0 - (i.toDouble() / count)
            val wave = sin(2.0 * PI * freq * t)
            out[i] = (wave * env * 18000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthesizeCouplerClank(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        var rng = 0x13579BDF
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            rng = rng xor (rng shl 13)
            rng = rng xor (rng ushr 17)
            rng = rng xor (rng shl 5)
            val noise = ((rng and 0xFFFF) / 32768.0) - 1.0
            val thud = sin(2.0 * PI * 115.0 * t)
            val env = kotlin.math.exp(-16.0 * t)
            out[i] = (((0.6 * thud + 0.4 * noise) * env) * 26000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthesizeBrakeSqueal(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = sin(PI * (i.toDouble() / count))
            val wave = 0.6 * sin(2.0 * PI * 2650.0 * t) + 0.4 * sin(2.0 * PI * 3120.0 * t)
            out[i] = (wave * env * 14000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}
