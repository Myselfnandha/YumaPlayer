/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.audiodsp

import java.io.File

enum class StemType {
    VOCALS,
    DRUMS,
    BASS,
    OTHER
}

enum class StemSeparationStatus {
    NOT_STARTED,
    QUEUED,
    PROCESSING,
    READY,
    FAILED,
    CANCELLED
}

data class StemMetadata(
    val trackId: String,
    val stemFiles: Map<StemType, String>,
    val durationSeconds: Double,
    val sampleRate: Int = 44100,
    val status: StemSeparationStatus = StemSeparationStatus.NOT_STARTED,
    val totalSizeBytes: Long = 0L,
    val lastAccessedTimestamp: Long = System.currentTimeMillis()
)

interface StemCacheManager {
    fun getStemFile(trackId: String, stemType: StemType): File?
    fun hasAllStems(trackId: String): Boolean
    fun registerStems(trackId: String, stemFiles: Map<StemType, File>, durationSeconds: Double): StemMetadata
    fun getStemMetadata(trackId: String): StemMetadata?
    fun getTotalCacheSizeBytes(): Long
    fun evictLeastRecentlyUsed(maxSizeBytes: Long): Long
    fun clearAll(): Boolean
}

interface StemSeparator {
    suspend fun separateStems(
        trackId: String,
        inputFile: File,
        outputDirectory: File,
        onProgress: (Float) -> Unit
    ): Map<StemType, File>?
}
