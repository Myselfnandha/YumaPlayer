/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val MixingEngineKey = stringPreferencesKey("mixing_engine")
val MixStyleKey = stringPreferencesKey("mix_style")
val CrossfadeTargetSecondsKey = intPreferencesKey("crossfade_target_seconds")
val HarmonicMatchingKey = booleanPreferencesKey("harmonic_matching")
val AvoidVocalClashesKey = booleanPreferencesKey("avoid_vocal_clashes")
val StemStrategyKey = stringPreferencesKey("stem_strategy")
val TempoTolerancePercentKey = floatPreferencesKey("tempo_tolerance_percent")
val EffectIntensityKey = floatPreferencesKey("effect_intensity")
val ProcessingQualityKey = stringPreferencesKey("processing_quality")
val ProcessOnlyOnWifiAndChargingKey = booleanPreferencesKey("process_only_on_wifi_and_charging")
val MaxStemCacheMbKey = intPreferencesKey("max_stem_cache_mb")
val PreviewMixModeKey = booleanPreferencesKey("preview_mix_mode")
val FallbackStrategyKey = stringPreferencesKey("fallback_strategy")
