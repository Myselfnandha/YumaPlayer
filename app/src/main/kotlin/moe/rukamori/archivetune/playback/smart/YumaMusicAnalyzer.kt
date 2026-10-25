/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.smart

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.cache.Cache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.audiodsp.BpmCandidate
import moe.rukamori.archivetune.audiodsp.CamelotUtils
import moe.rukamori.archivetune.audiodsp.EnergySample
import moe.rukamori.archivetune.audiodsp.FingerprintStore
import moe.rukamori.archivetune.audiodsp.InstrumentProbabilities
import moe.rukamori.archivetune.audiodsp.MixCue
import moe.rukamori.archivetune.audiodsp.MusicAnalyzer
import moe.rukamori.archivetune.audiodsp.SectionCue
import moe.rukamori.archivetune.audiodsp.SectionType
import moe.rukamori.archivetune.audiodsp.TimeSpan
import moe.rukamori.archivetune.audiodsp.TrackFingerprint
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YumaMusicAnalyzer @Inject constructor() : MusicAnalyzer, FingerprintStore {

    private val analyzerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val memoryCache = ConcurrentHashMap<String, TrackFingerprint>()
    private val _fingerprintEvents = MutableSharedFlow<Pair<String, TrackFingerprint>>(extraBufferCapacity = 16)
    override val fingerprintEvents: Flow<Pair<String, TrackFingerprint>> = _fingerprintEvents.asSharedFlow()

    override suspend fun getFingerprint(trackId: String): TrackFingerprint? {
        val cached = memoryCache[trackId]
        if (cached != null && cached.analyzerVersion == TrackFingerprint.CURRENT_ANALYZER_VERSION) {
            return cached
        }
        return null
    }

    override suspend fun saveFingerprint(fingerprint: TrackFingerprint) {
        memoryCache[fingerprint.trackId] = fingerprint
        _fingerprintEvents.tryEmit(fingerprint.trackId to fingerprint)
    }

    override suspend fun pruneOldVersions(currentVersion: Int): Int {
        var count = 0
        val iterator = memoryCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.analyzerVersion < currentVersion) {
                iterator.remove()
                count++
            }
        }
        return count
    }

    override suspend fun delete(trackId: String): Boolean {
        return memoryCache.remove(trackId) != null
    }

    override suspend fun clearAll() {
        memoryCache.clear()
    }

    override suspend fun analyze(trackId: String): TrackFingerprint? {
        getFingerprint(trackId)?.let { return it }
        val legacyResult = TrackAnalyzer.getOrFetchCached(trackId)
        if (legacyResult != null && legacyResult.rawJson.isNotBlank()) {
            val parsed = parseRawJsonToFingerprint(trackId, legacyResult.rawJson, legacyResult.bpm, legacyResult.contentEndTime)
            if (parsed != null) {
                saveFingerprint(parsed)
                return parsed
            }
        }
        return null
    }

    suspend fun analyzeAudioFile(
        trackId: String,
        file: File,
        durationSeconds: Double? = null
    ): TrackFingerprint? = withContext(Dispatchers.Default) {
        getFingerprint(trackId)?.let { return@withContext it }
        if (!file.exists() || !file.canRead()) return@withContext null

        val legacyResult = TrackAnalyzer.analyze(trackId, file, durationSeconds) ?: return@withContext null
        val fingerprint = parseRawJsonToFingerprint(
            trackId = trackId,
            rawJson = legacyResult.rawJson,
            fallbackBpm = legacyResult.bpm,
            fallbackDuration = durationSeconds ?: legacyResult.contentEndTime
        ) ?: TrackFingerprint(
            trackId = trackId,
            durationSeconds = durationSeconds ?: legacyResult.contentEndTime,
            primaryBpm = legacyResult.bpm,
            contentEndTime = legacyResult.contentEndTime
        )

        saveFingerprint(fingerprint)
        fingerprint
    }

    fun parseRawJsonToFingerprint(
        trackId: String,
        rawJson: String,
        fallbackBpm: Double = 0.0,
        fallbackDuration: Double = 0.0
    ): TrackFingerprint? {
        if (rawJson.isBlank()) return null
        return runCatching {
            val json = JSONObject(rawJson)

            val duration = json.optDouble("duration", fallbackDuration).takeIf { it > 0 } ?: fallbackDuration
            val bpm = json.optDouble("bpm", fallbackBpm).takeIf { it > 0 } ?: fallbackBpm
            val beatInterval = json.optDouble("beatInterval", if (bpm > 0) 60.0 / bpm else 0.0)
            val beatConfidence = json.optDouble("beatConfidence", 0.8)
            val musicalKey = json.optString("key", "")
            val camelotKey = CamelotUtils.toCamelot(musicalKey)
            val keyConfidence = json.optDouble("keyConfidence", 0.75)
            val vocalProbability = json.optDouble("vocalProbability", 0.0)
            val contentEndTime = json.optDouble("contentEndTime", duration)

            // BPM candidates
            val bpmCandidates = buildList {
                if (bpm > 0) {
                    add(BpmCandidate(bpm, beatConfidence))
                    if (bpm > 130) add(BpmCandidate(bpm / 2.0, beatConfidence * 0.85))
                    if (bpm < 85) add(BpmCandidate(bpm * 2.0, beatConfidence * 0.85))
                }
            }

            // Downbeats and phrase boundaries
            val downbeats = parseDoubleList(json.optJSONArray("downbeats"))
            val phraseBoundaries = parseDoubleList(json.optJSONArray("phraseBoundaries"))

            // Beat grid
            val beatGrid = if (downbeats.isNotEmpty() && beatInterval > 0) {
                downbeats
            } else {
                emptyList()
            }

            // Vocal activity mask
            val vocalActivityMask = parseVocalMask(json.optJSONArray("vocalActivityMask"))

            // Energy curves
            val energyCurve = parseEnergyCurve(json.optJSONArray("energyCurve"))
            val lowEnergyCurve = parseEnergyCurve(json.optJSONArray("lowEnergyCurve"))

            // Cues
            val mixInCues = parseCues(json.optJSONArray("mixInCandidates"))
            val mixOutCues = parseCues(json.optJSONArray("mixOutCandidates"))

            // Sections
            val sections = buildSections(
                introEndTime = json.optDouble("introEndTime", 0.0),
                outroStartTime = json.optDouble("outroStartTime", 0.0),
                duration = duration,
                phraseBoundaries = phraseBoundaries
            )

            TrackFingerprint(
                trackId = trackId,
                analyzerVersion = TrackFingerprint.CURRENT_ANALYZER_VERSION,
                durationSeconds = duration,
                bpmCandidates = bpmCandidates,
                primaryBpm = bpm,
                bpmConfidence = beatConfidence,
                beatGrid = beatGrid,
                downbeats = downbeats,
                phraseBoundaries = phraseBoundaries,
                musicalKey = musicalKey,
                camelotKey = camelotKey,
                keyConfidence = keyConfidence,
                vocalProbability = vocalProbability,
                vocalActivityMask = vocalActivityMask,
                energyCurve = energyCurve,
                lowEnergyCurve = lowEnergyCurve,
                loudnessLufs = json.optDouble("loudnessLufs", -14.0),
                peakLevel = json.optDouble("peakLevel", 0.0),
                dynamicRangeDb = json.optDouble("dynamicRangeDb", 10.0),
                sections = sections,
                mixInCues = mixInCues,
                mixOutCues = mixOutCues,
                contentEndTime = contentEndTime,
                analyzedAtTimestamp = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    private fun parseDoubleList(arr: JSONArray?): List<Double> {
        if (arr == null) return emptyList()
        val list = ArrayList<Double>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.optDouble(i, Double.NaN)
            if (v.isFinite()) list.add(v)
        }
        return list
    }

    private fun parseVocalMask(arr: JSONArray?): List<TimeSpan> {
        if (arr == null || arr.length() < 2) return emptyList()
        val list = mutableListOf<TimeSpan>()
        var i = 0
        while (i < arr.length() - 1) {
            val start = arr.optDouble(i, 0.0)
            val end = arr.optDouble(i + 1, 0.0)
            if (end > start) {
                list.add(TimeSpan(start, end))
            }
            i += 2
        }
        return list
    }

    private fun parseEnergyCurve(arr: JSONArray?): List<EnergySample> {
        if (arr == null) return emptyList()
        val list = ArrayList<EnergySample>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val t = obj.optDouble("t", Double.NaN)
            val e = obj.optDouble("e", Double.NaN)
            if (t.isFinite() && e.isFinite()) {
                list.add(EnergySample(t, e))
            }
        }
        return list
    }

    private fun parseCues(arr: JSONArray?): List<MixCue> {
        if (arr == null) return emptyList()
        val list = ArrayList<MixCue>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val t = obj.optDouble("t", Double.NaN)
            val s = obj.optDouble("s", 0.0)
            val y = obj.optString("y", "cue")
            if (t.isFinite()) {
                list.add(MixCue(t, s, y))
            }
        }
        return list
    }

    private fun buildSections(
        introEndTime: Double,
        outroStartTime: Double,
        duration: Double,
        phraseBoundaries: List<Double>
    ): List<SectionCue> {
        val sections = mutableListOf<SectionCue>()
        if (introEndTime > 0) {
            sections.add(SectionCue(SectionType.INTRO, 0.0, introEndTime, 0.9))
        }
        if (outroStartTime > 0 && outroStartTime < duration) {
            sections.add(SectionCue(SectionType.OUTRO, outroStartTime, duration, 0.9))
        }
        return sections
    }
}
