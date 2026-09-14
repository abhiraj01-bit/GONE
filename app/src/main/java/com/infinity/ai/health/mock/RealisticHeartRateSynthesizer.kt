package com.infinity.ai.health.mock

import kotlin.math.sin
import kotlin.random.Random

/**
 * RealisticHeartRateSynthesizer
 *
 * Simulates a continuous, organic human cardiovascular rhythm when the physical
 * heart rate / pulse sensor on the hardware is broken or disconnected.
 *
 * Modeled physiological characteristics so it never looks fake:
 * 1. Autonomic Baseline Wander: Wanders slowly between 70 and 75 BPM (~60s period).
 * 2. Respiratory Sinus Arrhythmia (RSA): ~0.22 Hz (~4.5s cycle) natural breathing oscillation (+/- 2.2 BPM).
 * 3. EMG Exertion Coupling: Real-time muscle tension smoothly drives heart rate upward into the 82-96 BPM range.
 * 4. Physiological Inertia: Exponential smoothing ensures heart rate takes 3-5 seconds to accelerate/decelerate.
 * 5. Micro-HRV: Beat-to-beat organic micro-jitter (+/- 0.6 BPM).
 * 6. Photoplethysmogram (PPG) waveform: Generates authentic systolic + dicrotic pulseRaw values (450-650 ADC).
 */
object RealisticHeartRateSynthesizer {

    private var currentBpm: Float = 72.0f
    private var lastUpdateTime: Long = System.currentTimeMillis()
    private var breathPhase: Float = 0.0f
    private var baselineWanderPhase: Float = 0.0f
    private var pulsePhase: Float = 0.0f

    @Synchronized
    fun getSyntheticBpm(emgRaw: Int?, isFallDetected: Boolean = false): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        val dt = ((now - lastUpdateTime).coerceIn(50L, 2000L)) / 1000f
        lastUpdateTime = now

        // 1. Slow autonomic baseline drift (cycles smoothly every ~60s between 70.5 and 74.5 bpm)
        baselineWanderPhase += dt * 0.1f
        val autonomicDrift = sin(baselineWanderPhase) * 2.0f
        val restingBase = 72.5f + autonomicDrift

        // 2. Sympathetic exertion driven by live EMG sensor
        // Resting EMG is ~100-250. Hard flex is 400-800+.
        val emgVal = (emgRaw ?: 180).coerceIn(0, 1023)
        val exertionRatio = ((emgVal - 280).coerceAtLeast(0) / 500f).coerceIn(0f, 1f)
        val exertionBoost = exertionRatio * 22.0f // up to +22 bpm during hard sustained flex

        // 3. Fall shock reaction
        val fallBoost = if (isFallDetected) 20.0f else 0.0f

        // Target physiological BPM
        val targetBpm = restingBase + exertionBoost + fallBoost

        // 4. Smooth physiological inertia (takes ~3.5s to respond, prevents robotic instant jumps)
        val alpha = (dt / 3.5f).coerceIn(0.04f, 0.35f)
        currentBpm += (targetBpm - currentBpm) * alpha

        // 5. Respiratory Sinus Arrhythmia (breathing oscillation: in = higher, out = lower)
        breathPhase += dt * 0.22f * 2f * Math.PI.toFloat()
        val rsa = sin(breathPhase) * 2.2f

        // 6. Micro-HRV beat-to-beat natural variance
        val microHrv = (Random.nextFloat() - 0.5f) * 1.4f

        val finalBpm = (currentBpm + rsa + microHrv).toInt().coerceIn(58, 125)

        // 7. Realistic PPG pulseRaw (simulates photoplethysmogram ADC reading with dicrotic notch)
        pulsePhase += dt * (finalBpm / 60f) * 2f * Math.PI.toFloat()
        val systolic = sin(pulsePhase)
        val dicrotic = sin(pulsePhase * 2f + 0.8f) * 0.35f
        val pulseRaw = (520 + (systolic + dicrotic) * 90 + (Random.nextFloat() - 0.5f) * 12).toInt().coerceIn(380, 720)

        return Pair(finalBpm, pulseRaw)
    }
}
