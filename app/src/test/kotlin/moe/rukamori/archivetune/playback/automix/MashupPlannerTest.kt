/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.automix

import moe.rukamori.archivetune.audiodsp.MashupCombination
import moe.rukamori.archivetune.audiodsp.MashupPlanner
import moe.rukamori.archivetune.audiodsp.MixPreferences
import moe.rukamori.archivetune.audiodsp.StemAudioMath
import moe.rukamori.archivetune.audiodsp.StemType
import moe.rukamori.archivetune.audiodsp.TrackFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MashupPlannerTest {

    @Test
    fun softKneeLimiting_boundsSumToUnity() {
        // Linear region (< 0.85)
        assertEquals(0.5, StemAudioMath.softKneeLimit(0.5), 1e-4)
        assertEquals(-0.5, StemAudioMath.softKneeLimit(-0.5), 1e-4)

        // Saturated region (> 0.85)
        val limited1 = StemAudioMath.softKneeLimit(1.2)
        assertTrue(limited1 < 1.0)
        assertTrue(limited1 > 0.85)

        val limitedExtr = StemAudioMath.softKneeLimit(4.0)
        assertTrue(limitedExtr < 1.0)
        assertTrue(limitedExtr > 0.98)

        // Multi-stem summing
        val stemSamples = floatArrayOf(0.8f, 0.7f, 0.6f, 0.5f)
        val gains = floatArrayOf(1f, 1f, 1f, 1f)
        val sum = StemAudioMath.sumAndLimit(stemSamples, gains)
        assertTrue(abs(sum) < 1.0f)
    }

    @Test
    fun mashupPlanner_selectsVocalOverInstrumental() {
        val vocalTrack = TrackFingerprint(
            trackId = "vocal_track",
            primaryBpm = 120.0,
            camelotKey = "8A",
            vocalProbability = 0.9,
            contentEndTime = 180.0
        )

        val instrumentalTrack = TrackFingerprint(
            trackId = "inst_track",
            primaryBpm = 120.0,
            camelotKey = "8A",
            vocalProbability = 0.05,
            contentEndTime = 180.0
        )

        val plan = MashupPlanner.planMashup(
            outgoing = vocalTrack,
            incoming = instrumentalTrack,
            preferences = MixPreferences()
        )

        assertTrue(plan != null)
        assertEquals(MashupCombination.VOCAL_OVER_INSTRUMENTAL, plan!!.combination)
        assertEquals("vocal_track", plan.leadTrackId)
        assertEquals("inst_track", plan.backingTrackId)

        val vocalLayer = plan.layers.find { it.stemType == StemType.VOCALS }
        assertTrue(vocalLayer != null && vocalLayer.trackId == "vocal_track")
    }
}
