package com.ivor.openstream.data.extensions.runtime

import com.ivor.openstream.domain.model.ExtensionManifest

/**
 * Safe boundary between the extension marketplace and stream resolution.
 *
 * Runtime implementations are app-owned. A repository manifest selects a runtime but never
 * supplies executable Kotlin/DEX code. This keeps a bad repository entry from becoming code
 * execution inside the OpenStream process.
 */
interface ExtensionProviderRuntime {
    val id: String
    suspend fun resolve(request: ProviderResolveRequest): ProviderResolveResult
}

data class ProviderResolveRequest(
    val extension: ExtensionManifest,
    val mediaUrl: String,
    val season: Int? = null,
    val episode: Int? = null,
    val preferredQuality: String? = null
)

data class ProviderResolveResult(
    val streams: List<ProviderStream> = emptyList(),
    val diagnostics: String? = null
)

data class ProviderStream(
    val url: String,
    val quality: String? = null,
    val title: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val referer: String? = null
)

/** Runtime selection is deliberately fail-closed. */
class ExtensionProviderRuntimeRegistry(
    runtimes: List<ExtensionProviderRuntime> = emptyList()
) {
    private val byId = runtimes.associateBy { it.id.trim().lowercase() }
    fun find(id: String): ExtensionProviderRuntime? = byId[id.trim().lowercase()]
    fun isAvailable(id: String): Boolean = find(id) != null
}

/** Provider compiled into the APK by OpenStream. */
interface BuiltInProviderRuntime : ExtensionProviderRuntime

/** Provider using a controlled HTTP/Web adapter owned by OpenStream. */
interface WebProviderRuntime : ExtensionProviderRuntime

/**
 * Isolated plugin boundary. Implementations must run outside the main application process and
 * expose only the ProviderResolveRequest/ProviderResolveResult contract.
 */
interface SandboxedProviderRuntime : ExtensionProviderRuntime
