package com.ivor.openstream.data.streaming

import com.ivor.openstream.domain.model.VideoServer

internal object ServerRanker {
    fun mergeAndRank(
        existing: List<VideoServer>,
        incoming: List<VideoServer>,
        providerPriorities: Map<String, Int>,
        preferredServerId: String?
    ): List<VideoServer> {
        val merged = linkedMapOf<String, VideoServer>()
        (existing + incoming).forEach { candidate ->
            // The same URL can require different request headers. Keep those as distinct
            // stream candidates while collapsing exact URL/header duplicates.
            val key = deduplicationKey(candidate)
            val current = merged[key]
            if (current == null || compare(candidate, current, providerPriorities) < 0) {
                merged[key] = candidate
            }
        }

        return merged.values.sortedWith { left, right ->
            when {
                left.id == preferredServerId && right.id != preferredServerId -> -1
                right.id == preferredServerId && left.id != preferredServerId -> 1
                else -> compare(left, right, providerPriorities)
            }
        }
    }

    private fun deduplicationKey(server: VideoServer): String {
        val normalizedUrl = server.url.substringBefore('#')
        val normalizedHeaders = server.headers
            .entries
            .sortedBy { it.key.lowercase() }
            .joinToString("&") { (name, value) -> "${name.lowercase()}=$value" }
        return "$normalizedUrl\u0000$normalizedHeaders"
    }

    private fun compare(
        left: VideoServer,
        right: VideoServer,
        providerPriorities: Map<String, Int>
    ): Int {
        val quality = right.quality.rank.compareTo(left.quality.rank)
        if (quality != 0) return quality
        val audio = right.audio.rank.compareTo(left.audio.rank)
        if (audio != 0) return audio
        val provider = (providerPriorities[left.providerId] ?: Int.MAX_VALUE)
            .compareTo(providerPriorities[right.providerId] ?: Int.MAX_VALUE)
        if (provider != 0) return provider
        return left.name.compareTo(right.name, ignoreCase = true)
    }
}
