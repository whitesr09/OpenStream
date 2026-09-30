package com.ivor.openstream.data.streaming

import com.ivor.openstream.domain.model.VideoServer

internal object ServerRanker {
    fun mergeAndRank(
        existing: List<VideoServer>,
        incoming: List<VideoServer>,
        providerPriorities: Map<String, Int>,
        preferredServerId: String?
    ): List<VideoServer> {
        // A URL is not a sufficient identity for a streaming source. Two providers can expose
        // the same URL with different authentication, referer, cookies, or provider semantics.
        // Collapse only true duplicates from the same provider with the same request headers.
        val merged = linkedMapOf<String, VideoServer>()
        (existing + incoming).forEach { candidate ->
            val key = sourceKey(candidate)
            val current = merged[key]
            if (current == null || isBetterSource(candidate, current, providerPriorities)) {
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

    private fun sourceKey(server: VideoServer): String {
        val normalizedUrl = server.url.trim().substringBefore('#')
        val normalizedHeaders = server.headers
            .entries
            .sortedBy { it.key.lowercase() }
            .joinToString("&") { "${it.key.lowercase()}=${it.value}" }
        return "${server.providerId}|$normalizedUrl|$normalizedHeaders"
    }

    /** Replaces a duplicate only when its meaningful ranking signals are actually better. */
    private fun isBetterSource(
        candidate: VideoServer,
        current: VideoServer,
        providerPriorities: Map<String, Int>
    ): Boolean {
        val quality = candidate.quality.rank.compareTo(current.quality.rank)
        if (quality != 0) return quality > 0
        val audio = candidate.audio.rank.compareTo(current.audio.rank)
        if (audio != 0) return audio > 0
        val candidatePriority = providerPriorities[candidate.providerId] ?: Int.MAX_VALUE
        val currentPriority = providerPriorities[current.providerId] ?: Int.MAX_VALUE
        return candidatePriority < currentPriority
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
