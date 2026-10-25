/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.audiodsp

data class BpmCandidate(
    val bpm: Double,
    val confidence: Double
)

data class TimeSpan(
    val startSeconds: Double,
    val endSeconds: Double
) {
    fun overlaps(other: TimeSpan): Boolean =
        startSeconds < other.endSeconds && other.startSeconds < endSeconds

    fun durationSeconds(): Double = (endSeconds - startSeconds).coerceAtLeast(0.0)
}

data class EnergySample(
    val timeSeconds: Double,
    val energy: Double
)

enum class SectionType {
    INTRO,
    VERSE,
    CHORUS,
    BREAKDOWN,
    DROP,
    OUTRO,
    UNKNOWN
}

data class SectionCue(
    val type: SectionType,
    val startSeconds: Double,
    val endSeconds: Double,
    val confidence: Double = 1.0
)

data class MixCue(
    val timeSeconds: Double,
    val score: Double,
    val label: String
)

data class InstrumentProbabilities(
    val timeSeconds: Double,
    val drums: Float,
    val bass: Float,
    val vocals: Float,
    val other: Float
)

data class TrackFingerprint(
    val trackId: String,
    val analyzerVersion: Int = CURRENT_ANALYZER_VERSION,
    val durationSeconds: Double = 0.0,
    val bpmCandidates: List<BpmCandidate> = emptyList(),
    val primaryBpm: Double = 0.0,
    val bpmConfidence: Double = 0.0,
    val beatGrid: List<Double> = emptyList(),
    val downbeats: List<Double> = emptyList(),
    val phraseBoundaries: List<Double> = emptyList(),
    val musicalKey: String = "",
    val camelotKey: String = "",
    val keyConfidence: Double = 0.0,
    val vocalProbability: Double = 0.0,
    val vocalActivityMask: List<TimeSpan> = emptyList(),
    val instrumentTimeline: List<InstrumentProbabilities> = emptyList(),
    val energyCurve: List<EnergySample> = emptyList(),
    val lowEnergyCurve: List<EnergySample> = emptyList(),
    val loudnessLufs: Double = 0.0,
    val peakLevel: Double = 0.0,
    val dynamicRangeDb: Double = 0.0,
    val sections: List<SectionCue> = emptyList(),
    val mixInCues: List<MixCue> = emptyList(),
    val mixOutCues: List<MixCue> = emptyList(),
    val contentEndTime: Double = 0.0,
    val analyzedAtTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val CURRENT_ANALYZER_VERSION = 2
    }
}

interface MusicAnalyzer {
    suspend fun analyze(trackId: String): TrackFingerprint?
}
