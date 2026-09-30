package com.ivor.openstream.data.streaming.providers

import com.ivor.openstream.data.extensions.runtime.ProviderRuntimePolicy
import com.ivor.openstream.data.streaming.StreamProvider
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Generic, declarative HTTPS JSON provider. No remote code is executed. */
class WebJsonProvider(
    private val id: String,
    private val name: String,
    private val endpointTemplate: String,
    private val priority: Int,
    private val client: OkHttpClient,
    private val json: Json
) : StreamProvider {
    override val displayName: String = name
    override val priority: Int = priority
    override val isEnabled: Boolean = true
    override val isFallback: Boolean = false

    override suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>> = runCatching {
        val url = expand(endpointTemplate, identity)
        require(ProviderRuntimePolicy.isAllowedUrl(url)) { "Provider endpoint is not allowed" }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Provider returned HTTP ${response.code}" }
            val body = response.body?.string().orEmpty()
            require(body.toByteArray().size <= ProviderRuntimePolicy.MAX_RESPONSE_BYTES) {
                "Provider response is too large"
            }
            parse(body)
        }
    }

    private fun parse(body: String): List<VideoServer> {
        val root = json.parseToJsonElement(body)
        val entries: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> root["streams"]?.jsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return entries.mapNotNull { element ->
            val obj = element.jsonObject
            val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (!ProviderRuntimePolicy.isAllowedUrl(url)) return@mapNotNull null
            val headers = obj["headers"]?.jsonObject
                ?.mapNotNull { (key, value) -> value.jsonPrimitive.contentOrNull?.let { key to it } }
                ?.toMap()
                ?.let(ProviderRuntimePolicy::sanitizeHeaders)
                .orEmpty()
            VideoServer(
                id = "$id-${url.hashCode()}",
                providerId = id,
                providerName = name,
                name = obj["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: name,
                url = url,
                quality = StreamQuality.parse(obj["quality"]?.jsonPrimitive?.contentOrNull),
                headers = headers,
                mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    private fun expand(template: String, identity: MediaIdentity): String = template
        .replace("{title}", java.net.URLEncoder.encode(identity.title, "UTF-8"))
        .replace("{imdbId}", identity.imdbId.orEmpty())
        .replace("{tmdbId}", identity.tmdbId.toString())
        .replace("{season}", identity.season.toString())
        .replace("{episode}", identity.episode.toString())
        .replace("{year}", identity.year?.toString().orEmpty())
}
