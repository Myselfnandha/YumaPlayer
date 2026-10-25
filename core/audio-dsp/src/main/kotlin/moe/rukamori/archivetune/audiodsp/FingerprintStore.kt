/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.audiodsp

import kotlinx.coroutines.flow.Flow

interface FingerprintStore {
    suspend fun getFingerprint(trackId: String): TrackFingerprint?
    suspend fun saveFingerprint(fingerprint: TrackFingerprint)
    suspend fun pruneOldVersions(currentVersion: Int): Int
    suspend fun delete(trackId: String): Boolean
    suspend fun clearAll()
    val fingerprintEvents: Flow<Pair<String, TrackFingerprint>>
}
