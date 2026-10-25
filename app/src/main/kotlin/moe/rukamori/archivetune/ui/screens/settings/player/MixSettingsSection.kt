/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.ui.screens.settings.player

import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.audiodsp.MixPreferences
import moe.rukamori.archivetune.audiodsp.MixStyle
import moe.rukamori.archivetune.audiodsp.MixingEngine
import moe.rukamori.archivetune.audiodsp.ProcessingQuality
import moe.rukamori.archivetune.audiodsp.StemStrategy
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.settings.SettingsAnimations

@Composable
fun MixSettingsSection(
    preferences: MixPreferences,
    onPreferencesChange: (MixPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    PreferenceGroup(
        title = "Automated Mixing & AI Mashup",
        modifier = modifier.animateContentSize(SettingsAnimations.resizeSpring())
    ) {
        // 1. Mixing Engine
        item {
            EnumListPreference(
                title = { Text("Mixing Engine") },
                description = "Choose between standard DJ AutoMix, stem-based AI Mashup, or Smart Hybrid",
                icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                selectedValue = preferences.engine,
                onValueSelected = { onPreferencesChange(preferences.copy(engine = it)) },
                valueText = { engine ->
                    when (engine) {
                        MixingEngine.SEAMLESS_AUTOMIX -> "Seamless AutoMix"
                        MixingEngine.AI_MASHUP -> "AI Mashup (Stem Blending)"
                        MixingEngine.SMART_HYBRID -> "Smart Hybrid (Contextual Choice)"
                    }
                }
            )
        }

        // 2. Mix Style
        item {
            EnumListPreference(
                title = { Text("Mix Style") },
                description = "Controls transition length, bass sweeps, and filter automation",
                icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                selectedValue = preferences.style,
                onValueSelected = { onPreferencesChange(preferences.copy(style = it)) },
                valueText = { style ->
                    when (style) {
                        MixStyle.GENTLE -> "Gentle (Long, smooth fades, subtle EQ)"
                        MixStyle.BALANCED -> "Balanced (Standard DJ transition with bass swap)"
                        MixStyle.CREATIVE -> "Creative (Aggressive filter drops and stem cuts)"
                    }
                }
            )
        }

        // 3. Harmony & Vocal Collision
        item {
            SwitchPreference(
                title = { Text("Harmonic Matching (Camelot Wheel)") },
                description = "Prefers musically compatible keys and modes to prevent discordant blends",
                icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                checked = preferences.harmonicMatching,
                onCheckedChange = { onPreferencesChange(preferences.copy(harmonicMatching = it)) }
            )
        }

        item {
            SwitchPreference(
                title = { Text("Avoid Vocal Clashes") },
                description = "Prevents overlapping vocals from two songs playing at the same time",
                icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                checked = preferences.avoidVocalClashes,
                onCheckedChange = { onPreferencesChange(preferences.copy(avoidVocalClashes = it)) }
            )
        }

        // 4. Stem Mixing Strategy (when AI Mashup / Hybrid is active)
        item(visible = preferences.engine != MixingEngine.SEAMLESS_AUTOMIX) {
            EnumListPreference(
                title = { Text("Stem Separation Strategy") },
                description = "When to prepare stems for mashup transitions",
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                selectedValue = preferences.stemStrategy,
                onValueSelected = { onPreferencesChange(preferences.copy(stemStrategy = it)) },
                valueText = { strategy ->
                    when (strategy) {
                        StemStrategy.WHEN_BENEFICIAL -> "When Highly Compatible (Score > 75%)"
                        StemStrategy.FREQUENTLY_WHEN_COMPATIBLE -> "Frequently When Compatible (Score > 60%)"
                        StemStrategy.ASK_BEFORE_MASHUP -> "Ask Before Mashup / Preview Only"
                    }
                }
            )
        }

        // 5. Resource Management
        item(visible = preferences.engine != MixingEngine.SEAMLESS_AUTOMIX) {
            SwitchPreference(
                title = { Text("Separate Stems Only On Wi-Fi & Charging") },
                description = "Saves mobile data and battery life during background separation jobs",
                icon = { Icon(Icons.Default.Speed, contentDescription = null) },
                checked = preferences.processOnlyOnWifiAndCharging,
                onCheckedChange = { onPreferencesChange(preferences.copy(processOnlyOnWifiAndCharging = it)) }
            )
        }

        item(visible = preferences.engine != MixingEngine.SEAMLESS_AUTOMIX) {
            EnumListPreference(
                title = { Text("Stem Separation Quality") },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                selectedValue = preferences.processingQualityLevel,
                onValueSelected = { onPreferencesChange(preferences.copy(processingQualityLevel = it)) },
                valueText = { quality ->
                    when (quality) {
                        ProcessingQuality.FAST -> "Fast (Lower compute, quantized)"
                        ProcessingQuality.BALANCED -> "Balanced (Standard mobile model)"
                        ProcessingQuality.HIGH_FIDELITY -> "High Fidelity (Full precision)"
                    }
                }
            )
        }
    }
}
