package com.ivor.openstream.data.streaming.providers

import com.ivor.openstream.data.extensions.runtime.ProviderRuntimePolicy
import com.ivor.openstream.data.streaming.StreamProvider
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Minimal Stremio addon stream runtime. Only the public JSON stream contract is consumed. */
class StremioAddonProvider(
    override val id: String,
    private val name: String,
    private val endpoint: String,
    override val priority: Int,
    private val client: OkHttpClient,
    private val json: Json
) : StreamProvider {
    override val displayName: String = name
    override val isEnabled: Boolean = true
    override val isFallback: Boolean = false

    override suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>> = runCatching {
        val type = if (identity.tmdbType.equals("tv", true) || identity.tmdbType.equals("series", true)) "series" else "movie"
        val baseId = identity.imdbId ?: identity.tmdbId.toString()
        val addonId = if (type == "series") "$baseId:${identity.season}:${identity.episode}" else baseId
        val url = endpoint.trimEnd('/') + "/stream/$type/$addonId.json"
        require(ProviderRuntimePolicy.isAllowedUrl(url)) { "Addon endpoint is not allowed" }
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Addon returned HTTP ${response.code}" }
            val body = response.body?.string().orEmpty()
            require(body.toByteArray().size.toLong() <= ProviderRuntimePolicy.MAX_RESPONSE_BYTES) { "Addon response is too large" }
            val root = json.parseToJsonElement(body).jsonObject
            root["streams"]?.jsonArray.orEmpty().mapNotNull { element ->
                val stream = element.jsonObject
                val streamUrl = stream["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (!ProviderRuntimePolicy.isAllowedUrl(streamUrl)) return@mapNotNull null
                VideoServer(
                    id = "$id-${streamUrl.hashCode()}",
                    providerId = id,
                    providerName = name,
                    name = stream["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: name,
                    url = streamUrl,
                    quality = StreamQuality.parse(
                        stream["name"]?.jsonPrimitive?.contentOrNull
                            ?: stream["title"]?.jsonPrimitive?.contentOrNull
                    )
                )
            }
        }
    }
}
