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
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

class DefaultStemCacheManager(
    private val rootCacheDir: File
) : StemCacheManager {

    private val metadataRegistry = ConcurrentHashMap<String, StemMetadata>()

    init {
        scanExistingCache()
    }

    @Synchronized
    private fun scanExistingCache() {
        if (!rootCacheDir.exists()) {
            rootCacheDir.mkdirs()
            return
        }

        val trackDirs = rootCacheDir.listFiles { f -> f.isDirectory } ?: return
        for (dir in trackDirs) {
            val trackId = dir.name
            val stemMap = mutableMapOf<StemType, String>()
            var totalSize = 0L

            for (stemType in StemType.entries) {
                val stemFile = File(dir, "${stemType.name.lowercase()}.opus")
                val wavFile = File(dir, "${stemType.name.lowercase()}.wav")
                val activeFile = if (stemFile.exists() && stemFile.length() > 0) {
                    stemFile
                } else if (wavFile.exists() && wavFile.length() > 0) {
                    wavFile
                } else null

                if (activeFile != null) {
                    stemMap[stemType] = activeFile.absolutePath
                    totalSize += activeFile.length()
                }
            }

            if (stemMap.size == StemType.entries.size) {
                metadataRegistry[trackId] = StemMetadata(
                    trackId = trackId,
                    stemFiles = stemMap,
                    durationSeconds = 0.0,
                    status = StemSeparationStatus.READY,
                    totalSizeBytes = totalSize,
                    lastAccessedTimestamp = dir.lastModified()
                )
            }
        }
    }

    override fun getStemFile(trackId: String, stemType: StemType): File? {
        val metadata = metadataRegistry[trackId] ?: return null
        val path = metadata.stemFiles[stemType] ?: return null
        val file = File(path)
        if (file.exists() && file.canRead()) {
            // Update last accessed timestamp
            metadataRegistry[trackId] = metadata.copy(lastAccessedTimestamp = System.currentTimeMillis())
            file.parentFile?.setLastModified(System.currentTimeMillis())
            return file
        }
        return null
    }

    override fun hasAllStems(trackId: String): Boolean {
        val metadata = metadataRegistry[trackId] ?: return false
        if (metadata.status != StemSeparationStatus.READY) return false
        return StemType.entries.all { type ->
            val path = metadata.stemFiles[type] ?: return@all false
            File(path).let { it.exists() && it.length() > 0 }
        }
    }

    override fun registerStems(
        trackId: String,
        stemFiles: Map<StemType, File>,
        durationSeconds: Double
    ): StemMetadata {
        val paths = mutableMapOf<StemType, String>()
        var totalSize = 0L

        val targetDir = File(rootCacheDir, trackId)
        if (!targetDir.exists()) targetDir.mkdirs()

        for ((type, file) in stemFiles) {
            val destFile = if (file.parentFile == targetDir) {
                file
            } else {
                val ext = file.extension.takeIf { it.isNotBlank() } ?: "opus"
                val dest = File(targetDir, "${type.name.lowercase()}.$ext")
                file.copyTo(dest, overwrite = true)
                dest
            }
            paths[type] = destFile.absolutePath
            totalSize += destFile.length()
        }

        val metadata = StemMetadata(
            trackId = trackId,
            stemFiles = paths,
            durationSeconds = durationSeconds,
            status = StemSeparationStatus.READY,
            totalSizeBytes = totalSize,
            lastAccessedTimestamp = System.currentTimeMillis()
        )
        metadataRegistry[trackId] = metadata
        Timber.tag("StemCacheManager").d("Registered 4 stems for trackId=$trackId totalSize=${totalSize / 1024} KB")
        return metadata
    }

    override fun getStemMetadata(trackId: String): StemMetadata? = metadataRegistry[trackId]

    override fun getTotalCacheSizeBytes(): Long {
        return metadataRegistry.values.sumOf { it.totalSizeBytes }
    }

    @Synchronized
    override fun evictLeastRecentlyUsed(maxSizeBytes: Long): Long {
        var currentTotal = getTotalCacheSizeBytes()
        if (currentTotal <= maxSizeBytes) return 0L

        var bytesFreed = 0L
        val sortedByAccess = metadataRegistry.values.sortedBy { it.lastAccessedTimestamp }

        for (meta in sortedByAccess) {
            if (currentTotal <= maxSizeBytes) break
            val trackDir = File(rootCacheDir, meta.trackId)
            val freed = trackDir.walkBottomUp().fold(0L) { acc, file ->
                val len = file.length()
                if (file.delete()) acc + len else acc
            }
            bytesFreed += freed
            currentTotal -= freed
            metadataRegistry.remove(meta.trackId)
            Timber.tag("StemCacheManager").d("Evicted stem cache for trackId=${meta.trackId} freed=${freed / 1024} KB")
        }
        return bytesFreed
    }

    @Synchronized
    override fun clearAll(): Boolean {
        metadataRegistry.clear()
        return rootCacheDir.deleteRecursively().also { rootCacheDir.mkdirs() }
    }
}
