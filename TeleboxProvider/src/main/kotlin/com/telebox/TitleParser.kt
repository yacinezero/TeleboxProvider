package com.telebox

object TitleParser {
    data class ParsedTitle(
        val title: String,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val label: String = title
    )

    fun clean(raw: String): ParsedTitle {
        var value = raw.substringBeforeLast('.', raw).trim()
        value = value.replace(Regex("[._]+"), " ")
        val year = Regex("\\b((?:19|20)\\d{2})\\b").find(value)?.groupValues?.get(1)?.toIntOrNull()
        val seasonEpisode = Regex("(?i)\\bS(\\d{1,2})\\s*E(\\d{1,3})\\b").find(value)
        val epOnly = Regex("(?i)\\bEP(?:ISODE)?[ ._-]?(\\d{1,3})\\b").find(value)
        val season = seasonEpisode?.groupValues?.get(1)?.toIntOrNull()
        val episode = seasonEpisode?.groupValues?.get(2)?.toIntOrNull() ?: epOnly?.groupValues?.get(1)?.toIntOrNull()
        value = value.replace(Regex("(?i)\\b(\\d{3,4}p|4k|8k|web[- ]?dl|webrip|bluray|brrip|hdr|x26[45]|hevc|aac|proper|repack)\\b"), " ")
        value = value.replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '_')
        if (value.isBlank()) value = raw.substringBeforeLast('.').trim().ifBlank { raw.trim() }
        val label = when {
            season != null && episode != null -> "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
            episode != null -> "Episode ${episode.toString().padStart(2, '0')}"
            else -> raw.trim()
        }
        return ParsedTitle(value, year, season, episode, label)
    }
}
