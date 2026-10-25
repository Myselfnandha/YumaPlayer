/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.audiodsp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

enum class AutoMixState {
    PLAYING,
    PREPARING,
    PLANNING,
    ARMED,
    MIXING,
    COMPLETE
}

data class AutoMixStateContext(
    val state: AutoMixState = AutoMixState.PLAYING,
    val currentTrackId: String = "",
    val nextTrackId: String? = null,
    val activePlan: TransitionPlan? = null,
    val progress: Float = 0f,
    val armedTriggerMs: Long? = null,
    val lastError: String? = null
)

class AutoMixStateMachine(
    private val controller: DeckController? = null,
    private val compatibilityEngine: CompatibilityEngine = HeuristicCompatibilityEngine
) {
    private val _stateContext = MutableStateFlow(AutoMixStateContext())
    val stateContext: StateFlow<AutoMixStateContext> = _stateContext.asStateFlow()

    val currentState: AutoMixState
        get() = _stateContext.value.state

    fun onTrackStarted(trackId: String) {
        Timber.tag("AutoMixStateMachine").d("Transitioning to PLAYING for track=$trackId")
        _stateContext.value = AutoMixStateContext(
            state = AutoMixState.PLAYING,
            currentTrackId = trackId
        )
    }

    fun onQueueNextTrack(nextTrackId: String) {
        val current = _stateContext.value
        if (current.state != AutoMixState.PLAYING) return
        Timber.tag("AutoMixStateMachine").d("Transitioning to PREPARING for nextTrack=$nextTrackId")
        _stateContext.value = current.copy(
            state = AutoMixState.PREPARING,
            nextTrackId = nextTrackId
        )
    }

    fun onNextTrackPreloaded(
        outgoingFingerprint: TrackFingerprint,
        incomingFingerprint: TrackFingerprint,
        currentDurationMs: Long,
        currentPositionMs: Long,
        preferences: MixPreferences
    ) {
        val current = _stateContext.value
        if (current.state != AutoMixState.PREPARING) return

        Timber.tag("AutoMixStateMachine").d("Transitioning to PLANNING")
        val plan = compatibilityEngine.planTransition(
            outgoing = outgoingFingerprint,
            incoming = incomingFingerprint,
            currentDurationMs = currentDurationMs,
            currentPositionMs = currentPositionMs,
            preferences = preferences
        )

        _stateContext.value = current.copy(
            state = AutoMixState.PLANNING,
            activePlan = plan
        )

        armTransition(plan)
    }

    private fun armTransition(plan: TransitionPlan) {
        Timber.tag("AutoMixStateMachine").d("Transitioning to ARMED for triggerMs=${plan.outgoingExitTimestampMs}")
        _stateContext.value = _stateContext.value.copy(
            state = AutoMixState.ARMED,
            armedTriggerMs = plan.outgoingExitTimestampMs
        )
    }

    fun checkPositionAndTrigger(currentPositionMs: Long): Boolean {
        val current = _stateContext.value
        if (current.state != AutoMixState.ARMED) return false
        val triggerAt = current.armedTriggerMs ?: return false

        if (currentPositionMs >= triggerAt) {
            startMixing()
            return true
        }
        return false
    }

    fun startMixing() {
        val current = _stateContext.value
        if (current.state != AutoMixState.ARMED && current.state != AutoMixState.PLANNING) return

        Timber.tag("AutoMixStateMachine").d("Transitioning to MIXING")
        val plan = current.activePlan
        if (plan != null && controller != null) {
            val automixPlan = AutomixPlan(
                triggerOffsetMs = plan.durationMs,
                durationMs = plan.durationMs,
                incomingStartMs = plan.incomingEntryTimestampMs,
                enableBassSwap = plan.enableBassSwap,
                tier = plan.tier,
                incomingTempoRatio = plan.targetTempoRatio
            )
            controller.startCrossfade(automixPlan)
        }

        _stateContext.value = current.copy(
            state = AutoMixState.MIXING,
            progress = 0f
        )
    }

    fun onMixingProgress(progress: Float) {
        val current = _stateContext.value
        if (current.state != AutoMixState.MIXING) return
        _stateContext.value = current.copy(progress = progress.coerceIn(0f, 1f))
    }

    fun completeTransition() {
        val current = _stateContext.value
        if (current.state != AutoMixState.MIXING) return

        Timber.tag("AutoMixStateMachine").d("Transitioning to COMPLETE")
        controller?.completeHandoff()

        _stateContext.value = current.copy(
            state = AutoMixState.COMPLETE,
            progress = 1.0f
        )

        // Reset to PLAYING for the newly active track
        val nextId = current.nextTrackId ?: ""
        onTrackStarted(nextId)
    }

    fun cancel(reason: String = "User or error cancel") {
        Timber.tag("AutoMixStateMachine").w("AutoMix cancelled: $reason")
        controller?.cancel()
        val current = _stateContext.value
        _stateContext.value = current.copy(
            state = AutoMixState.PLAYING,
            activePlan = null,
            armedTriggerMs = null,
            progress = 0f,
            lastError = reason
        )
    }
}
