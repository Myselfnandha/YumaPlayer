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
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object HeuristicCompatibilityEngine : CompatibilityEngine {

    private const val WEIGHT_BPM = 0.25f
    private const val WEIGHT_PHRASE = 0.20f
    private const val WEIGHT_INSTRUMENT = 0.20f
    private const val WEIGHT_HARMONIC = 0.15f
    private const val WEIGHT_ENERGY = 0.10f
    private const val WEIGHT_VOCAL = 0.10f

    private const val CONFIDENCE_THRESHOLD = 0.50f
    private const val MASHUP_SCORE_THRESHOLD = 75

    override fun evaluate(
        outgoing: TrackFingerprint,
        incoming: TrackFingerprint,
        preferences: MixPreferences
    ): CompatibilityBreakdown {
        val bpmScore = calculateBpmScore(outgoing.primaryBpm, incoming.primaryBpm, preferences.tempoTolerancePercent)
        val phraseScore = calculatePhraseScore(outgoing, incoming)
        val instrumentScore = calculateInstrumentScore(outgoing, incoming)
        val harmonicScore = if (preferences.harmonicMatching) {
            calculateHarmonicScore(outgoing.camelotKey, incoming.camelotKey)
        } else {
            0.85f // Neutral high when user disables harmonic filtering
        }
        val energyScore = calculateEnergyScore(outgoing, incoming)
        val vocalScore = if (preferences.avoidVocalClashes) {
            calculateVocalScore(outgoing, incoming, preferences.crossfadeTargetSeconds.toDouble())
        } else {
            0.9f
        }

        val weightedTotal = (
            bpmScore * WEIGHT_BPM +
            phraseScore * WEIGHT_PHRASE +
            instrumentScore * WEIGHT_INSTRUMENT +
            harmonicScore * WEIGHT_HARMONIC +
            energyScore * WEIGHT_ENERGY +
            vocalScore * WEIGHT_VOCAL
        )

        val totalScore = (weightedTotal * 100f).roundToInt().coerceIn(0, 100)
        val overallConfidence = ((outgoing.bpmConfidence + incoming.bpmConfidence + outgoing.keyConfidence + incoming.keyConfidence) / 4.0)
            .toFloat().coerceIn(0.2f, 1.0f)

        return CompatibilityBreakdown(
            bpmScore = bpmScore,
            phraseScore = phraseScore,
            instrumentScore = instrumentScore,
            harmonicScore = harmonicScore,
            energyScore = energyScore,
            vocalScore = vocalScore,
            totalScore = totalScore,
            confidence = overallConfidence
        )
    }

    override fun planTransition(
        outgoing: TrackFingerprint,
        incoming: TrackFingerprint,
        currentDurationMs: Long,
        currentPositionMs: Long,
        preferences: MixPreferences
    ): TransitionPlan {
        val breakdown = evaluate(outgoing, incoming, preferences)
        val tempoRatio = TransitionPlanner.normalizedTempoRatio(outgoing.primaryBpm, incoming.primaryBpm).toFloat()

        val canBeatmatch = breakdown.bpmScore >= 0.65f && breakdown.confidence >= CONFIDENCE_THRESHOLD
        val tier = if (canBeatmatch) TransitionTier.SMART_BEATMATCH else TransitionTier.PLAIN_CROSSFADE

        val fallbackStrategy = when {
            breakdown.totalScore < 30 -> FallbackStrategy.GAPLESS_CUT
            breakdown.confidence < CONFIDENCE_THRESHOLD -> FallbackStrategy.STANDARD_CROSSFADE
            canBeatmatch -> FallbackStrategy.BEATMATCHED_CROSSFADE
            else -> FallbackStrategy.STANDARD_CROSSFADE
        }

        val targetSeconds = preferences.crossfadeTargetSeconds.toDouble()
        val durationMs = TransitionPlanner.calculateAdaptiveDurationMs(
            outgoing.primaryBpm.takeIf { it > 0 },
            incoming.primaryBpm.takeIf { it > 0 },
            (targetSeconds * 1000.0).roundToLong(),
            when (preferences.style) {
                MixStyle.GENTLE -> "soft"
                MixStyle.CREATIVE -> "aggressive"
                MixStyle.BALANCED -> "standard"
            }
        ).coerceAtMost(maxOf(0L, currentDurationMs))

        val exitMs = if (outgoing.contentEndTime > 0.0) {
            (outgoing.contentEndTime * 1000.0).roundToLong()
        } else {
            currentDurationMs
        }

        val bestMixOut = outgoing.mixOutCues.maxByOrNull { it.score }?.let { (it.timeSeconds * 1000.0).roundToLong() }
            ?: (exitMs - durationMs).coerceAtLeast(0L)

        val bestMixIn = incoming.mixInCues.maxByOrNull { it.score }?.let { (it.timeSeconds * 1000.0).roundToLong() }
            ?: 0L

        val recommendedEngine = when (preferences.engine) {
            MixingEngine.SEAMLESS_AUTOMIX -> MixingEngine.SEAMLESS_AUTOMIX
            MixingEngine.AI_MASHUP -> {
                if (breakdown.totalScore >= MASHUP_SCORE_THRESHOLD && breakdown.vocalScore >= 0.8f) {
                    MixingEngine.AI_MASHUP
                } else {
                    MixingEngine.SEAMLESS_AUTOMIX
                }
            }
            MixingEngine.SMART_HYBRID -> {
                if (breakdown.totalScore >= MASHUP_SCORE_THRESHOLD && breakdown.vocalScore >= 0.8f && breakdown.harmonicScore >= 0.75f) {
                    MixingEngine.AI_MASHUP
                } else if (breakdown.totalScore >= 45 && canBeatmatch) {
                    MixingEngine.SEAMLESS_AUTOMIX
                } else {
                    MixingEngine.SEAMLESS_AUTOMIX
                }
            }
        }

        val (anchoredExitMs, anchoredFadeMs) = TransitionPlanner.anchorPlanToPlaybackPosition(
            plannedStartAtMs = bestMixOut,
            plannedFadeMs = durationMs,
            currentPositionMs = currentPositionMs,
            contentEndMs = exitMs
        )

        return TransitionPlan(
            outgoingTrackId = outgoing.trackId,
            incomingTrackId = incoming.trackId,
            outgoingExitTimestampMs = anchoredExitMs,
            incomingEntryTimestampMs = bestMixIn,
            targetTempoRatio = if (canBeatmatch) tempoRatio else 1.0f,
            targetStartBeatTimestampMs = bestMixIn,
            phraseLengthBars = if (breakdown.phraseScore >= 0.7f) 8 else 4,
            crossfadeCurve = when {
                breakdown.totalScore < 30 -> TransitionCurve.HARD_CUT
                preferences.style == MixStyle.CREATIVE -> TransitionCurve.STAGED_BASS_SWAP
                preferences.style == MixStyle.GENTLE -> TransitionCurve.EQUAL_POWER
                else -> TransitionCurve.STAGED_BASS_SWAP
            },
            durationMs = anchoredFadeMs,
            enableBassSwap = preferences.style != MixStyle.GENTLE && breakdown.totalScore >= 40,
            confidenceScore = breakdown.confidence,
            recommendedEngine = recommendedEngine,
            tier = tier,
            fallbackStrategy = fallbackStrategy
        )
    }

    fun calculateBpmScore(bpm1: Double, bpm2: Double, tolerance: Float): Float {
        if (bpm1 <= 0.0 || bpm2 <= 0.0) return 0.5f
        val ratio = TransitionPlanner.normalizedTempoRatio(bpm1, bpm2)
        val dev = abs(1.0 - ratio).toFloat()
        return when {
            dev <= 0.015f -> 1.0f
            dev <= tolerance -> 1.0f - (dev / tolerance) * 0.4f
            dev <= tolerance * 1.5f -> 0.6f - ((dev - tolerance) / (tolerance * 0.5f)) * 0.4f
            else -> 0.1f
        }
    }

    fun calculateHarmonicScore(camelot1: String, camelot2: String): Float {
        if (camelot1.isBlank() || camelot2.isBlank()) return 0.6f
        val parsed1 = parseCamelot(camelot1) ?: return 0.6f
        val parsed2 = parseCamelot(camelot2) ?: return 0.6f

        val (num1, mode1) = parsed1
        val (num2, mode2) = parsed2

        val diff = abs(num1 - num2)
        val circleDist = minOf(diff, 12 - diff)

        return when {
            circleDist == 0 && mode1 == mode2 -> 1.0f         // Exact key match
            circleDist == 0 && mode1 != mode2 -> 0.95f        // Relative major/minor (e.g., 8A <-> 8B)
            circleDist == 1 && mode1 == mode2 -> 0.85f        // Adjacent key (e.g., 8A <-> 9A, 8A <-> 7A)
            circleDist == 1 && mode1 != mode2 -> 0.75f        // Diagonal key
            circleDist == 2 && mode1 == mode2 -> 0.70f        // 2-step energy boost
            else -> 0.20f                                     // Harmonic clash
        }
    }

    private fun parseCamelot(key: String): Pair<Int, Char>? {
        val trimmed = key.trim().uppercase()
        if (trimmed.length < 2) return null
        val mode = trimmed.last()
        if (mode != 'A' && mode != 'B') return null
        val numStr = trimmed.dropLast(1)
        val num = numStr.toIntOrNull() ?: return null
        if (num !in 1..12) return null
        return num to mode
    }

    fun calculatePhraseScore(outgoing: TrackFingerprint, incoming: TrackFingerprint): Float {
        if (outgoing.downbeats.isEmpty() || incoming.downbeats.isEmpty()) return 0.5f
        val outgoingHasPhrases = outgoing.phraseBoundaries.isNotEmpty()
        val incomingHasPhrases = incoming.phraseBoundaries.isNotEmpty()
        return when {
            outgoingHasPhrases && incomingHasPhrases -> 0.95f
            outgoingHasPhrases || incomingHasPhrases -> 0.75f
            else -> 0.60f
        }
    }

    fun calculateInstrumentScore(outgoing: TrackFingerprint, incoming: TrackFingerprint): Float {
        if (outgoing.instrumentTimeline.isEmpty() || incoming.instrumentTimeline.isEmpty()) return 0.7f
        val outTail = outgoing.instrumentTimeline.takeLast(maxOf(1, outgoing.instrumentTimeline.size / 6))
        val inHead = incoming.instrumentTimeline.take(maxOf(1, incoming.instrumentTimeline.size / 6))

        val avgOutDrums = outTail.map { it.drums }.average().toFloat()
        val avgInVocals = inHead.map { it.vocals }.average().toFloat()

        // Complementary: outgoing has drums/groove while incoming starts vocal/melody
        return if (avgOutDrums > 0.6f && avgInVocals > 0.5f) {
            0.95f
        } else if (avgOutDrums > 0.5f && inHead.map { it.drums }.average() < 0.3f) {
            0.90f
        } else {
            0.70f
        }
    }

    fun calculateEnergyScore(outgoing: TrackFingerprint, incoming: TrackFingerprint): Float {
        if (outgoing.energyCurve.isEmpty() || incoming.energyCurve.isEmpty()) return 0.7f
        val outExitEnergy = outgoing.energyCurve.takeLast(10).map { it.energy }.average()
        val inEntryEnergy = incoming.energyCurve.take(10).map { it.energy }.average()
        val diff = abs(outExitEnergy - inEntryEnergy)
        return (1.0 - diff).toFloat().coerceIn(0.2f, 1.0f)
    }

    fun calculateVocalScore(outgoing: TrackFingerprint, incoming: TrackFingerprint, transitionSeconds: Double): Float {
        val outMixOut = outgoing.mixOutCues.maxByOrNull { it.score }?.timeSeconds
            ?: (outgoing.contentEndTime - transitionSeconds).coerceAtLeast(0.0)
        val outTransitionSpan = TimeSpan(maxOf(0.0, outMixOut - transitionSeconds), outMixOut)

        val inMixIn = incoming.mixInCues.maxByOrNull { it.score }?.timeSeconds ?: 0.0
        val inTransitionSpan = TimeSpan(inMixIn, inMixIn + transitionSeconds)

        val outgoingHasVocalsInSpan = outgoing.vocalActivityMask.any { it.overlaps(outTransitionSpan) }
        val incomingHasVocalsInSpan = incoming.vocalActivityMask.any { it.overlaps(inTransitionSpan) }

        return if (outgoingHasVocalsInSpan && incomingHasVocalsInSpan) {
            0.15f // Severe clash: two singers competing at the same time
        } else if (!outgoingHasVocalsInSpan && incomingHasVocalsInSpan) {
            1.0f  // Clean handoff to incoming vocals
        } else if (outgoingHasVocalsInSpan && !incomingHasVocalsInSpan) {
            0.90f // Outgoing singer finishes before incoming track drops
        } else {
            0.85f // Instrumental on both sides
        }
    }
}
