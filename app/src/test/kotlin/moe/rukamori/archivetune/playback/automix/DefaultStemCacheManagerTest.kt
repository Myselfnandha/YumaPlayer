/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.automix

import moe.rukamori.archivetune.audiodsp.DefaultStemCacheManager
import moe.rukamori.archivetune.audiodsp.StemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DefaultStemCacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun stemRegistrationAndRetrieval_worksCorrectly() {
        val rootDir = tempFolder.newFolder("stem_cache")
        val cacheManager = DefaultStemCacheManager(rootDir)

        val stemFiles = mutableMapOf<StemType, File>()
        for (type in StemType.entries) {
            val file = tempFolder.newFile("test_${type.name.lowercase()}.opus")
            file.writeBytes(ByteArray(1024) { 1 })
            stemFiles[type] = file
        }

        assertFalse(cacheManager.hasAllStems("track_100"))
        cacheManager.registerStems("track_100", stemFiles, durationSeconds = 120.0)

        assertTrue(cacheManager.hasAllStems("track_100"))
        val vocalFile = cacheManager.getStemFile("track_100", StemType.VOCALS)
        assertTrue(vocalFile != null && vocalFile.exists() && vocalFile.length() == 1024L)
        assertEquals(4096L, cacheManager.getTotalCacheSizeBytes())
    }

    @Test
    fun lruEviction_removesOldestTrackWhenOverQuota() {
        val rootDir = tempFolder.newFolder("stem_cache_lru")
        val cacheManager = DefaultStemCacheManager(rootDir)

        // Register track 1 (4096 bytes)
        val stems1 = StemType.entries.associateWith { type ->
            tempFolder.newFile("t1_${type.name}.opus").apply { writeBytes(ByteArray(1024) { 1 }) }
        }
        cacheManager.registerStems("track_1", stems1, 100.0)

        Thread.sleep(50) // Ensure timestamp divergence

        // Register track 2 (4096 bytes)
        val stems2 = StemType.entries.associateWith { type ->
            tempFolder.newFile("t2_${type.name}.opus").apply { writeBytes(ByteArray(1024) { 2 }) }
        }
        cacheManager.registerStems("track_2", stems2, 100.0)

        assertEquals(8192L, cacheManager.getTotalCacheSizeBytes())

        // Evict with quota of 4096 bytes (track 1 should be evicted first)
        val freed = cacheManager.evictLeastRecentlyUsed(4096L)
        assertTrue(freed >= 4096L)
        assertFalse(cacheManager.hasAllStems("track_1"))
        assertTrue(cacheManager.hasAllStems("track_2"))
    }
}
