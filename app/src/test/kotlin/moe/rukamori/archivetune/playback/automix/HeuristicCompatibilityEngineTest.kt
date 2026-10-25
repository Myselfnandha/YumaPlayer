/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.automix

import moe.rukamori.archivetune.audiodsp.FallbackStrategy
import moe.rukamori.archivetune.audiodsp.HeuristicCompatibilityEngine
import moe.rukamori.archivetune.audiodsp.MixPreferences
import moe.rukamori.archivetune.audiodsp.MixingEngine
import moe.rukamori.archivetune.audiodsp.TimeSpan
import moe.rukamori.archivetune.audiodsp.TrackFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicCompatibilityEngineTest {

    @Test
    fun camelotHarmonicMatching_calculatesCorrectWeights() {
        // Exact match (8A <-> 8A)
        val scoreExact = HeuristicCompatibilityEngine.calculateHarmonicScore("8A", "8A")
        assertEquals(1.0f, scoreExact, 1e-4f)

        // Relative major/minor (8A <-> 8B)
        val scoreRelative = HeuristicCompatibilityEngine.calculateHarmonicScore("8A", "8B")
        assertEquals(0.95f, scoreRelative, 1e-4f)

        // Adjacent key (8A <-> 9A, 8A <-> 7A)
        val scoreAdjacentUp = HeuristicCompatibilityEngine.calculateHarmonicScore("8A", "9A")
        val scoreAdjacentDown = HeuristicCompatibilityEngine.calculateHarmonicScore("8A", "7A")
        assertEquals(0.85f, scoreAdjacentUp, 1e-4f)
        assertEquals(0.85f, scoreAdjacentDown, 1e-4f)

        // Across circle boundary (12A <-> 1A)
        val scoreCircleWrap = HeuristicCompatibilityEngine.calculateHarmonicScore("12A", "1A")
        assertEquals(0.85f, scoreCircleWrap, 1e-4f)

        // Clash (8A <-> 2A)
        val scoreClash = HeuristicCompatibilityEngine.calculateHarmonicScore("8A", "2A")
        assertEquals(0.20f, scoreClash, 1e-4f)
    }

    @Test
    fun bpmMatching_octaveFoldsCorrectly() {
        // Same BPM
        val scoreIdentical = HeuristicCompatibilityEngine.calculateBpmScore(120.0, 120.0, 0.06f)
        assertEquals(1.0f, scoreIdentical, 1e-4f)

        // Half-time / Double-time fold (60 <-> 120, 140 <-> 70)
        val scoreHalfTime = HeuristicCompatibilityEngine.calculateBpmScore(60.0, 120.0, 0.06f)
        assertEquals(1.0f, scoreHalfTime, 1e-4f)

        val scoreDoubleTime = HeuristicCompatibilityEngine.calculateBpmScore(140.0, 70.0, 0.06f)
        assertEquals(1.0f, scoreDoubleTime, 1e-4f)

        // Out of tolerance (120 <-> 145 = 20% deviation)
        val scoreDistant = HeuristicCompatibilityEngine.calculateBpmScore(120.0, 145.0, 0.06f)
        assertEquals(0.1f, scoreDistant, 1e-4f)
    }

    @Test
    fun vocalConflict_penalizesOverlappingVocals() {
        val outTrack = TrackFingerprint(
            trackId = "track_a",
            contentEndTime = 180.0,
            vocalActivityMask = listOf(TimeSpan(168.0, 180.0))
        )

        val inTrackWithVocal = TrackFingerprint(
            trackId = "track_b",
            contentEndTime = 180.0,
            vocalActivityMask = listOf(TimeSpan(0.0, 15.0))
        )

        val inTrackWithoutVocal = TrackFingerprint(
            trackId = "track_c",
            contentEndTime = 180.0,
            vocalActivityMask = listOf(TimeSpan(30.0, 60.0))
        )

        // Overlap: Track A singing up to 180, Track B singing from 0 to 15 during an 8s transition
        val clashScore = HeuristicCompatibilityEngine.calculateVocalScore(outTrack, inTrackWithVocal, 8.0)
        assertTrue(clashScore < 0.25f)

        // Clean: Track C has no vocals in intro
        val cleanScore = HeuristicCompatibilityEngine.calculateVocalScore(outTrack, inTrackWithoutVocal, 8.0)
        assertTrue(cleanScore >= 0.85f)
    }

    @Test
    fun transitionPlan_selectsExpectedEngineAndFallback() {
        val perfectTrackA = TrackFingerprint(
            trackId = "A",
            primaryBpm = 124.0,
            bpmConfidence = 0.95,
            camelotKey = "8A",
            keyConfidence = 0.9,
            contentEndTime = 200.0,
            vocalProbability = 0.7,
            downbeats = listOf(0.0, 1.93, 3.87, 5.8)
        )

        val perfectTrackB = TrackFingerprint(
            trackId = "B",
            primaryBpm = 124.0,
            bpmConfidence = 0.95,
            camelotKey = "8A",
            keyConfidence = 0.9,
            contentEndTime = 190.0,
            vocalProbability = 0.1,
            downbeats = listOf(0.0, 1.93, 3.87, 5.8)
        )

        val hybridPrefs = MixPreferences(engine = MixingEngine.SMART_HYBRID)
        val plan = HeuristicCompatibilityEngine.planTransition(
            outgoing = perfectTrackA,
            incoming = perfectTrackB,
            currentDurationMs = 200_000L,
            currentPositionMs = 150_000L,
            preferences = hybridPrefs
        )

        assertTrue(plan.confidenceScore >= 0.8f)
        assertEquals(FallbackStrategy.BEATMATCHED_CROSSFADE, plan.fallbackStrategy)
        assertTrue(plan.durationMs > 0)
    }
}
