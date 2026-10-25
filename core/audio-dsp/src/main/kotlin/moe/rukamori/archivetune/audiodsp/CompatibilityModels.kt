/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.audiodsp

enum class TransitionCurve {
    EQUAL_POWER,
    LINEAR,
    STAGED_BASS_SWAP,
    HARD_CUT
}

data class CompatibilityBreakdown(
    val bpmScore: Float,
    val phraseScore: Float,
    val instrumentScore: Float,
    val harmonicScore: Float,
    val energyScore: Float,
    val vocalScore: Float,
    val totalScore: Int,
    val confidence: Float
)

data class TransitionPlan(
    val outgoingTrackId: String,
    val incomingTrackId: String,
    val outgoingExitTimestampMs: Long,
    val incomingEntryTimestampMs: Long,
    val targetTempoRatio: Float,
    val targetStartBeatTimestampMs: Long,
    val phraseLengthBars: Int,
    val crossfadeCurve: TransitionCurve,
    val durationMs: Long,
    val enableBassSwap: Boolean,
    val crossoverFrequencyHz: Double = DjFilterAudioProcessor.BASS_CROSSOVER_HZ,
    val confidenceScore: Float,
    val recommendedEngine: MixingEngine,
    val tier: TransitionTier,
    val fallbackStrategy: FallbackStrategy
)

interface CompatibilityEngine {
    fun evaluate(
        outgoing: TrackFingerprint,
        incoming: TrackFingerprint,
        preferences: MixPreferences
    ): CompatibilityBreakdown

    fun planTransition(
        outgoing: TrackFingerprint,
        incoming: TrackFingerprint,
        currentDurationMs: Long,
        currentPositionMs: Long,
        preferences: MixPreferences
    ): TransitionPlan
}
