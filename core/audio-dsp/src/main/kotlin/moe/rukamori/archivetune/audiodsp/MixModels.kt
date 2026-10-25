/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.audiodsp

enum class MixingEngine {
    SEAMLESS_AUTOMIX,
    AI_MASHUP,
    SMART_HYBRID
}

enum class MixStyle {
    GENTLE,
    BALANCED,
    CREATIVE
}

enum class StemStrategy {
    WHEN_BENEFICIAL,
    FREQUENTLY_WHEN_COMPATIBLE,
    ASK_BEFORE_MASHUP
}

enum class ProcessingQuality {
    FAST,
    BALANCED,
    HIGH_FIDELITY
}

enum class FallbackStrategy {
    BEATMATCHED_CROSSFADE,
    STANDARD_CROSSFADE,
    GAPLESS_CUT
}

data class MixPreferences(
    val engine: MixingEngine = MixingEngine.SMART_HYBRID,
    val style: MixStyle = MixStyle.BALANCED,
    val crossfadeTargetSeconds: Int = 8,
    val harmonicMatching: Boolean = true,
    val avoidVocalClashes: Boolean = true,
    val stemStrategy: StemStrategy = StemStrategy.WHEN_BENEFICIAL,
    val tempoTolerancePercent: Float = 0.06f,
    val effectIntensity: Float = 1.0f,
    val processingQualityLevel: ProcessingQuality = ProcessingQuality.BALANCED,
    val processOnlyOnWifiAndCharging: Boolean = true,
    val maxStemCacheMb: Int = 1024,
    val previewMixMode: Boolean = false,
    val fallbackStrategy: FallbackStrategy = FallbackStrategy.STANDARD_CROSSFADE
)
