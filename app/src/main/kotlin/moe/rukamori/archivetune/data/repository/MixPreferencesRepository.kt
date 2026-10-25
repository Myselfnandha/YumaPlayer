/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.audiodsp.FallbackStrategy
import moe.rukamori.archivetune.audiodsp.MixPreferences
import moe.rukamori.archivetune.audiodsp.MixStyle
import moe.rukamori.archivetune.audiodsp.MixingEngine
import moe.rukamori.archivetune.audiodsp.ProcessingQuality
import moe.rukamori.archivetune.audiodsp.StemStrategy
import moe.rukamori.archivetune.constants.AvoidVocalClashesKey
import moe.rukamori.archivetune.constants.CrossfadeTargetSecondsKey
import moe.rukamori.archivetune.constants.EffectIntensityKey
import moe.rukamori.archivetune.constants.FallbackStrategyKey
import moe.rukamori.archivetune.constants.HarmonicMatchingKey
import moe.rukamori.archivetune.constants.MaxStemCacheMbKey
import moe.rukamori.archivetune.constants.MixStyleKey
import moe.rukamori.archivetune.constants.MixingEngineKey
import moe.rukamori.archivetune.constants.PreviewMixModeKey
import moe.rukamori.archivetune.constants.ProcessOnlyOnWifiAndChargingKey
import moe.rukamori.archivetune.constants.ProcessingQualityKey
import moe.rukamori.archivetune.constants.StemStrategyKey
import moe.rukamori.archivetune.constants.TempoTolerancePercentKey
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

interface MixPreferencesRepository {
    val mixPreferences: Flow<MixPreferences>
    suspend fun getPreferences(): MixPreferences
    suspend fun updatePreferences(transform: (MixPreferences) -> MixPreferences)
    suspend fun setEngine(engine: MixingEngine)
    suspend fun setStyle(style: MixStyle)
    suspend fun setCrossfadeDuration(seconds: Int)
    suspend fun setHarmonicMatching(enabled: Boolean)
    suspend fun setAvoidVocalClashes(enabled: Boolean)
    suspend fun setStemStrategy(strategy: StemStrategy)
    suspend fun setTempoTolerance(tolerance: Float)
    suspend fun setEffectIntensity(intensity: Float)
    suspend fun setQuality(quality: ProcessingQuality)
    suspend fun setProcessOnlyOnWifiAndCharging(enabled: Boolean)
    suspend fun setMaxStemCacheMb(maxMb: Int)
    suspend fun setPreviewMixMode(enabled: Boolean)
}

@Singleton
class MixPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MixPreferencesRepository {

    override val mixPreferences: Flow<MixPreferences> = context.dataStore.data.map { prefs ->
        val engineStr = prefs[MixingEngineKey] ?: MixingEngine.SMART_HYBRID.name
        val styleStr = prefs[MixStyleKey] ?: MixStyle.BALANCED.name
        val stemStrategyStr = prefs[StemStrategyKey] ?: StemStrategy.WHEN_BENEFICIAL.name
        val qualityStr = prefs[ProcessingQualityKey] ?: ProcessingQuality.BALANCED.name
        val fallbackStr = prefs[FallbackStrategyKey] ?: FallbackStrategy.STANDARD_CROSSFADE.name

        MixPreferences(
            engine = runCatching { MixingEngine.valueOf(engineStr) }.getOrDefault(MixingEngine.SMART_HYBRID),
            style = runCatching { MixStyle.valueOf(styleStr) }.getOrDefault(MixStyle.BALANCED),
            crossfadeTargetSeconds = prefs[CrossfadeTargetSecondsKey] ?: 8,
            harmonicMatching = prefs[HarmonicMatchingKey] ?: true,
            avoidVocalClashes = prefs[AvoidVocalClashesKey] ?: true,
            stemStrategy = runCatching { StemStrategy.valueOf(stemStrategyStr) }.getOrDefault(StemStrategy.WHEN_BENEFICIAL),
            tempoTolerancePercent = prefs[TempoTolerancePercentKey] ?: 0.06f,
            effectIntensity = prefs[EffectIntensityKey] ?: 1.0f,
            processingQualityLevel = runCatching { ProcessingQuality.valueOf(qualityStr) }.getOrDefault(ProcessingQuality.BALANCED),
            processOnlyOnWifiAndCharging = prefs[ProcessOnlyOnWifiAndChargingKey] ?: true,
            maxStemCacheMb = prefs[MaxStemCacheMbKey] ?: 1024,
            previewMixMode = prefs[PreviewMixModeKey] ?: false,
            fallbackStrategy = runCatching { FallbackStrategy.valueOf(fallbackStr) }.getOrDefault(FallbackStrategy.STANDARD_CROSSFADE)
        )
    }.distinctUntilChanged()

    override suspend fun getPreferences(): MixPreferences = mixPreferences.first()

    override suspend fun updatePreferences(transform: (MixPreferences) -> MixPreferences) {
        val current = getPreferences()
        val updated = transform(current)
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[MixingEngineKey] = updated.engine.name
                prefs[MixStyleKey] = updated.style.name
                prefs[CrossfadeTargetSecondsKey] = updated.crossfadeTargetSeconds
                prefs[HarmonicMatchingKey] = updated.harmonicMatching
                prefs[AvoidVocalClashesKey] = updated.avoidVocalClashes
                prefs[StemStrategyKey] = updated.stemStrategy.name
                prefs[TempoTolerancePercentKey] = updated.tempoTolerancePercent
                prefs[EffectIntensityKey] = updated.effectIntensity
                prefs[ProcessingQualityKey] = updated.processingQualityLevel.name
                prefs[ProcessOnlyOnWifiAndChargingKey] = updated.processOnlyOnWifiAndCharging
                prefs[MaxStemCacheMbKey] = updated.maxStemCacheMb
                prefs[PreviewMixModeKey] = updated.previewMixMode
                prefs[FallbackStrategyKey] = updated.fallbackStrategy.name
            }
        }
    }

    override suspend fun setEngine(engine: MixingEngine) = updatePreferences { it.copy(engine = engine) }
    override suspend fun setStyle(style: MixStyle) = updatePreferences { it.copy(style = style) }
    override suspend fun setCrossfadeDuration(seconds: Int) = updatePreferences { it.copy(crossfadeTargetSeconds = seconds) }
    override suspend fun setHarmonicMatching(enabled: Boolean) = updatePreferences { it.copy(harmonicMatching = enabled) }
    override suspend fun setAvoidVocalClashes(enabled: Boolean) = updatePreferences { it.copy(avoidVocalClashes = enabled) }
    override suspend fun setStemStrategy(strategy: StemStrategy) = updatePreferences { it.copy(stemStrategy = strategy) }
    override suspend fun setTempoTolerance(tolerance: Float) = updatePreferences { it.copy(tempoTolerancePercent = tolerance) }
    override suspend fun setEffectIntensity(intensity: Float) = updatePreferences { it.copy(effectIntensity = intensity) }
    override suspend fun setQuality(quality: ProcessingQuality) = updatePreferences { it.copy(processingQualityLevel = quality) }
    override suspend fun setProcessOnlyOnWifiAndCharging(enabled: Boolean) = updatePreferences { it.copy(processOnlyOnWifiAndCharging = enabled) }
    override suspend fun setMaxStemCacheMb(maxMb: Int) = updatePreferences { it.copy(maxStemCacheMb = maxMb) }
    override suspend fun setPreviewMixMode(enabled: Boolean) = updatePreferences { it.copy(previewMixMode = enabled) }
}
