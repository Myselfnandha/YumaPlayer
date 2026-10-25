/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import moe.rukamori.archivetune.db.entities.TrackFingerprintEntity

@Dao
interface TrackFingerprintDao {
    @Query("SELECT * FROM track_fingerprints WHERE trackId = :trackId AND analyzerVersion = :currentVersion")
    suspend fun getValid(trackId: String, currentVersion: Int): TrackFingerprintEntity?

    @Query("SELECT * FROM track_fingerprints WHERE trackId = :trackId")
    suspend fun get(trackId: String): TrackFingerprintEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackFingerprintEntity)

    @Query("DELETE FROM track_fingerprints WHERE analyzerVersion < :currentVersion")
    suspend fun pruneOutdatedVersions(currentVersion: Int): Int

    @Query("DELETE FROM track_fingerprints WHERE trackId = :trackId")
    suspend fun delete(trackId: String): Int

    @Query("SELECT COUNT(*) FROM track_fingerprints WHERE analyzerVersion = :currentVersion")
    suspend fun countValid(currentVersion: Int): Int
}
