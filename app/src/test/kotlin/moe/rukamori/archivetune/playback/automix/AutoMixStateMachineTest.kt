/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.automix

import moe.rukamori.archivetune.audiodsp.AutoMixState
import moe.rukamori.archivetune.audiodsp.AutoMixStateMachine
import moe.rukamori.archivetune.audiodsp.MixPreferences
import moe.rukamori.archivetune.audiodsp.TrackFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMixStateMachineTest {

    @Test
    fun stateFlow_advancesThroughAllSixStates() {
        val sm = AutoMixStateMachine()
        assertEquals(AutoMixState.PLAYING, sm.currentState)

        // 1. Track Started
        sm.onTrackStarted("song_1")
        assertEquals(AutoMixState.PLAYING, sm.currentState)
        assertEquals("song_1", sm.stateContext.value.currentTrackId)

        // 2. Queue next track -> PREPARING
        sm.onQueueNextTrack("song_2")
        assertEquals(AutoMixState.PREPARING, sm.currentState)
        assertEquals("song_2", sm.stateContext.value.nextTrackId)

        // 3. Preloaded -> PLANNING and auto-advance to ARMED
        val fp1 = TrackFingerprint(trackId = "song_1", primaryBpm = 120.0, contentEndTime = 180.0)
        val fp2 = TrackFingerprint(trackId = "song_2", primaryBpm = 120.0, contentEndTime = 180.0)

        sm.onNextTrackPreloaded(
            outgoingFingerprint = fp1,
            incomingFingerprint = fp2,
            currentDurationMs = 180_000L,
            currentPositionMs = 100_000L,
            preferences = MixPreferences()
        )
        assertEquals(AutoMixState.ARMED, sm.currentState)
        assertTrue(sm.stateContext.value.armedTriggerMs != null)

        val triggerMs = sm.stateContext.value.armedTriggerMs!!

        // Before trigger time -> does not start
        val triggeredEarly = sm.checkPositionAndTrigger(triggerMs - 1000L)
        assertFalse(triggeredEarly)
        assertEquals(AutoMixState.ARMED, sm.currentState)

        // At trigger time -> MIXING
        val triggeredOnTime = sm.checkPositionAndTrigger(triggerMs)
        assertTrue(triggeredOnTime)
        assertEquals(AutoMixState.MIXING, sm.currentState)

        // 5. Progress during mixing
        sm.onMixingProgress(0.5f)
        assertEquals(0.5f, sm.stateContext.value.progress, 1e-4f)

        // 6. Complete -> transitions to COMPLETE then auto-resets to PLAYING for song_2
        sm.completeTransition()
        assertEquals(AutoMixState.PLAYING, sm.currentState)
        assertEquals("song_2", sm.stateContext.value.currentTrackId)
    }

    @Test
    fun cancellation_resetsToPlayingWithoutError() {
        val sm = AutoMixStateMachine()
        sm.onTrackStarted("song_1")
        sm.onQueueNextTrack("song_2")
        assertEquals(AutoMixState.PREPARING, sm.currentState)

        sm.cancel("Playback paused by user")
        assertEquals(AutoMixState.PLAYING, sm.currentState)
        assertEquals("Playback paused by user", sm.stateContext.value.lastError)
    }
}
