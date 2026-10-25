/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_fingerprints")
data class TrackFingerprintEntity(
    @PrimaryKey val trackId: String,
    val analyzerVersion: Int = 1,
    val durationSeconds: Double = 0.0,
    val primaryBpm: Double = 0.0,
    val bpmConfidence: Double = 0.0,
    val musicalKey: String = "",
    val camelotKey: String = "",
    val keyConfidence: Double = 0.0,
    val vocalProbability: Double = 0.0,
    val loudnessLufs: Double = 0.0,
    val peakLevel: Double = 0.0,
    val dynamicRangeDb: Double = 0.0,
    val contentEndTime: Double = 0.0,
    val beatGridJson: String = "",
    val downbeatsJson: String = "",
    val phraseBoundariesJson: String = "",
    val vocalActivityJson: String = "",
    val sectionsJson: String = "",
    val mixInCuesJson: String = "",
    val mixOutCuesJson: String = "",
    val energyCurveJson: String = "",
    val analyzedAtTimestamp: Long = System.currentTimeMillis()
)
