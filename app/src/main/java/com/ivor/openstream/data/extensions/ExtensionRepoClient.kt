package com.ivor.openstream.data.extensions

import com.ivor.openstream.data.extensions.runtime.ProviderRuntimePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Fetches a repository index and any extension lists it links to. */
@Singleton
class ExtensionRepoClient @Inject constructor(
    @Named("StreamingClient") private val client: OkHttpClient,
    private val parser: ExtensionIndexParser
) {

    suspend fun fetch(url: String): CachedRepoSnapshot = withContext(Dispatchers.IO) {
        val raw = get(url)
        val repo = parser.parseRepo(raw)
        val linked = repo.extensionLists
            .mapNotNull { RepoUrlNormalizer.normalize(it) }
            .flatMap { listUrl ->
                runCatching { parser.parseExtensionList(get(listUrl)) }.getOrElse { emptyList() }
            }

        val direct = repo.extensions.ifEmpty { parser.parseExtensionList(raw) }
        val entries = (direct + linked).distinctBy { it.id }
        if (entries.isEmpty() && repo.extensionLists.isNotEmpty()) {
            throw IOException("Repository lists could not be read")
        }
        if (entries.isEmpty()) {
            throw IOException("Repository published no extensions")
        }

        CachedRepoSnapshot(
            url = url,
            name = repo.name?.trim().orEmpty().ifEmpty { defaultName(url) },
            description = repo.description?.trim().orEmpty(),
            iconUrl = repo.iconUrl?.trim()?.takeIf { it.isNotEmpty() },
            website = repo.website?.trim()?.takeIf { it.isNotEmpty() },
            fetchedAt = System.currentTimeMillis(),
            extensions = entries
        )
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from ${response.request.url.host}")
            }
            val body = response.readBodyLimited()
            if (body.isBlank()) throw IOException("Empty response from ${response.request.url.host}")
            return body
        }
    }

    private fun Response.readBodyLimited(): String {
        val responseBody = body ?: return ""
        val declaredLength = responseBody.contentLength()
        if (declaredLength > ProviderRuntimePolicy.MAX_RESPONSE_BYTES) {
            throw IOException("Response is too large from ${request.url.host}")
        }
        val maxBytes = ProviderRuntimePolicy.MAX_RESPONSE_BYTES
        responseBody.byteStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val output = Buffer()
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw IOException("Response is too large from ${request.url.host}")
                }
                output.write(buffer, 0, read)
            }
            return output.readUtf8()
        }
    }

    private fun defaultName(url: String): String =
        url.substringAfter("://").substringBefore('/').ifEmpty { "Repository" }
}
