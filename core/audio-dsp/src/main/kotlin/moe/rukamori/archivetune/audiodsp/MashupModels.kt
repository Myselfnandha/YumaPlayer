/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.audiodsp

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

enum class MashupCombination {
    VOCAL_OVER_INSTRUMENTAL, // Combination A
    DRUM_AND_BASS_HANDOFF,   // Combination B
    CREATIVE_MULTITRACK      // Combination C
}

data class StemLayerConfig(
    val trackId: String,
    val stemType: StemType,
    val gain: Float = 1.0f,
    val timeOffsetMs: Long = 0L,
    val mute: Boolean = false
)

data class MashupPlan(
    val combination: MashupCombination,
    val leadTrackId: String,
    val backingTrackId: String,
    val layers: List<StemLayerConfig>,
    val targetBpm: Double,
    val targetKey: String,
    val durationMs: Long,
    val startTransitionMs: Long,
    val enableSoftKneeLimiter: Boolean = true,
    val isPreview: Boolean = false
)

object StemAudioMath {
    private const val LIMITER_THRESHOLD = 0.85

    /**
     * Soft-knee peak limiting to prevent clipping during multi-stem summing.
     */
    fun softKneeLimit(sample: Double): Double {
        val absVal = abs(sample)
        if (absVal <= LIMITER_THRESHOLD) return sample
        val over = absVal - LIMITER_THRESHOLD
        val headroom = 1.0 - LIMITER_THRESHOLD
        val compressed = LIMITER_THRESHOLD + headroom * tanh(over / headroom)
        return compressed * sample.sign
    }

    /**
     * Sums floating point audio samples from multiple stems and applies soft-knee limiting.
     */
    fun sumAndLimit(samples: FloatArray, gains: FloatArray): Float {
        var sum = 0.0
        for (i in samples.indices) {
            sum += samples[i] * gains[i]
        }
        return softKneeLimit(sum).toFloat()
    }
}

object MashupPlanner {

    fun planMashup(
        outgoing: TrackFingerprint,
        incoming: TrackFingerprint,
        preferences: MixPreferences
    ): MashupPlan? {
        val breakdown = HeuristicCompatibilityEngine.evaluate(outgoing, incoming, preferences)
        if (breakdown.totalScore < 60) return null

        val targetBpm = outgoing.primaryBpm.takeIf { it > 0 } ?: incoming.primaryBpm
        val targetKey = if (outgoing.camelotKey.isNotBlank()) outgoing.camelotKey else incoming.camelotKey

        // Combination A: Vocal over Instrumental
        // Outgoing vocals over incoming backing (drums + bass + other), OR incoming vocals over outgoing backing
        val outHasVocals = outgoing.vocalProbability > 0.4
        val inHasVocals = incoming.vocalProbability > 0.4

        val (leadId, backingId, combination) = if (outHasVocals && !inHasVocals) {
            Triple(outgoing.trackId, incoming.trackId, MashupCombination.VOCAL_OVER_INSTRUMENTAL)
        } else if (!outHasVocals && inHasVocals) {
            Triple(incoming.trackId, outgoing.trackId, MashupCombination.VOCAL_OVER_INSTRUMENTAL)
        } else {
            Triple(outgoing.trackId, incoming.trackId, MashupCombination.DRUM_AND_BASS_HANDOFF)
        }

        val layers = when (combination) {
            MashupCombination.VOCAL_OVER_INSTRUMENTAL -> listOf(
                StemLayerConfig(trackId = leadId, stemType = StemType.VOCALS, gain = 1.0f),
                StemLayerConfig(trackId = backingId, stemType = StemType.DRUMS, gain = 0.9f),
                StemLayerConfig(trackId = backingId, stemType = StemType.BASS, gain = 0.9f),
                StemLayerConfig(trackId = backingId, stemType = StemType.OTHER, gain = 0.8f)
            )
            MashupCombination.DRUM_AND_BASS_HANDOFF -> listOf(
                StemLayerConfig(trackId = outgoing.trackId, stemType = StemType.DRUMS, gain = 0.95f),
                StemLayerConfig(trackId = outgoing.trackId, stemType = StemType.BASS, gain = 0.90f),
                StemLayerConfig(trackId = incoming.trackId, stemType = StemType.OTHER, gain = 0.85f),
                StemLayerConfig(trackId = incoming.trackId, stemType = StemType.VOCALS, gain = 1.0f)
            )
            MashupCombination.CREATIVE_MULTITRACK -> listOf(
                StemLayerConfig(trackId = outgoing.trackId, stemType = StemType.VOCALS, gain = 0.9f),
                StemLayerConfig(trackId = incoming.trackId, stemType = StemType.DRUMS, gain = 0.9f),
                StemLayerConfig(trackId = incoming.trackId, stemType = StemType.BASS, gain = 0.85f),
                StemLayerConfig(trackId = outgoing.trackId, stemType = StemType.OTHER, gain = 0.7f)
            )
        }

        val durationMs = (preferences.crossfadeTargetSeconds * 1000L).coerceIn(4000L, 16000L)
        val exitMs = if (outgoing.contentEndTime > 0.0) (outgoing.contentEndTime * 1000.0).toLong() else 180_000L

        return MashupPlan(
            combination = combination,
            leadTrackId = leadId,
            backingTrackId = backingId,
            layers = layers,
            targetBpm = targetBpm,
            targetKey = targetKey,
            durationMs = durationMs,
            startTransitionMs = (exitMs - durationMs).coerceAtLeast(0L),
            enableSoftKneeLimiter = true,
            isPreview = preferences.previewMixMode
        )
    }
}
