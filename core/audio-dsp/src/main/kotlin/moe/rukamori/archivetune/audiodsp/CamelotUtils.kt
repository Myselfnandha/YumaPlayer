/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.audiodsp

object CamelotUtils {

    private val KEY_TO_CAMELOT = mapOf(
        // Major keys (B)
        "b major" to "1B", "b" to "1B",
        "f# major" to "2B", "f#" to "2B", "gb major" to "2B", "gb" to "2B",
        "db major" to "3B", "db" to "3B", "c# major" to "3B", "c#" to "3B",
        "ab major" to "4B", "ab" to "4B", "g# major" to "4B", "g#" to "4B",
        "eb major" to "5B", "eb" to "5B", "d# major" to "5B", "d#" to "5B",
        "bb major" to "6B", "bb" to "6B", "a# major" to "6B", "a#" to "6B",
        "f major" to "7B", "f" to "7B",
        "c major" to "8B", "c" to "8B",
        "g major" to "9B", "g" to "9B",
        "d major" to "10B", "d" to "10B",
        "a major" to "11B", "a" to "11B",
        "e major" to "12B", "e" to "12B",

        // Minor keys (A)
        "ab minor" to "1A", "abm" to "1A", "g# minor" to "1A", "g#m" to "1A",
        "eb minor" to "2A", "ebm" to "2A", "d# minor" to "2A", "d#m" to "2A",
        "bb minor" to "3A", "bbm" to "3A", "a# minor" to "3A", "a#m" to "3A",
        "f minor" to "4A", "fm" to "4A",
        "c minor" to "5A", "cm" to "5A",
        "g minor" to "6A", "gm" to "6A",
        "d minor" to "7A", "dm" to "7A",
        "a minor" to "8A", "am" to "8A",
        "e minor" to "9A", "em" to "9A",
        "b minor" to "10A", "bm" to "10A",
        "f# minor" to "11A", "f#m" to "11A", "gb minor" to "11A", "gbm" to "11A",
        "c# minor" to "12A", "c#m" to "12A", "db minor" to "12A", "dbm" to "12A"
    )

    fun toCamelot(musicalKey: String): String {
        val normalized = musicalKey.trim().lowercase()
        return KEY_TO_CAMELOT[normalized] ?: ""
    }
}
