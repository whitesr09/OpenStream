package com.ivor.openstream.domain.model

/**
 * Version of the extension manifest contract this build understands. Extensions declaring a
 * higher [ExtensionManifest.apiVersion] are listed but cannot be installed, mirroring the
 * `apiVersion` gate CloudStream uses in its plugin lists.
 */
const val EXTENSION_API_VERSION: Int = 1

/** Availability reported by the repository. Status codes match CloudStream's convention. */
enum class ExtensionStatus(val code: Int, val label: String) {
    DOWN(0, "Down"),
    OK(1, "Online"),
    SLOW(2, "Slow"),
    BETA(3, "Beta");

    companion object {
        fun fromCode(code: Int): ExtensionStatus = entries.firstOrNull { it.code == code } ?: OK
    }
}

/**
 * The runtime an extension binds to. Manifests are declarative — like a Stremio add-on they
 * describe *where* to resolve streams, and the app supplies the engine that talks the protocol.
 */
enum class ExtensionEngineType(val key: String) {
    VIDKING_DIRECT("vidking-direct"),
    VIDKING_WEBVIEW("vidking-webview"),
    /** Any embeddable web player, described by URL templates. */
    WEB_EMBED("web-embed"),
    /** Declarative HTTP resolver: lookup a provider id, then resolve playback resources. */
    RESOLVER_API("resolver-api"),
    /** Anime sites; `endpoint` is the site's origin. Titles are matched through AniList. */
    ANIKOTO("anikoto"),
    REANIME("reanime"),
    ANIMEPAHE("animepahe"),
    FOURANIMO("fouranimo"),
    ANIMEGG("animegg"),
    UNSUPPORTED("unsupported");

    companion object {
        fun fromKey(key: String?): ExtensionEngineType =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: UNSUPPORTED
    }
}

data class ExtensionEngine(
    val type: ExtensionEngineType,
    val endpoint: String = "",
    val priority: Int = DEFAULT_PRIORITY,
    val language: String? = null,
    val qualityFilter: String? = null,
    /** `web-embed` only: player URL templates, see `WebEmbedSpec`. */
    val movieUrl: String? = null,
    val tvUrl: String? = null,
    /** Optional multi-stage HTTP resolver configuration. */
    val resolver: ResolverApiSpec? = null
) {
    val isRunnable: Boolean
        get() = when (type) {
            ExtensionEngineType.VIDKING_DIRECT -> endpoint.isNotBlank()
            ExtensionEngineType.VIDKING_WEBVIEW -> true
            ExtensionEngineType.WEB_EMBED ->
                listOfNotNull(movieUrl, tvUrl).any { it.startsWith("https://") }
            ExtensionEngineType.RESOLVER_API ->
                endpoint.startsWith("https://") && resolver?.isRunnable == true
            ExtensionEngineType.ANIKOTO,
            ExtensionEngineType.REANIME,
            ExtensionEngineType.ANIMEPAHE,
            ExtensionEngineType.FOURANIMO,
            ExtensionEngineType.ANIMEGG -> endpoint.startsWith("https://")
            ExtensionEngineType.UNSUPPORTED -> false
        }

    companion object {
        const val DEFAULT_PRIORITY = 50
    }
}

/**
 * Declarative HTTP resolver configuration.
 *
 * The app owns the HTTP/runtime behavior; an extension only describes public/authorized
 * endpoints and JSON fields. URLs and bodies support {tmdbId}, {imdbId}, {title}, {year},
 * {season}, {episode} and {providerId}.
 */
data class ResolverApiRequest(
    val method: String = "GET",
    val url: String = "",
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

data class ResolverApiResponse(
    val searchItemsPath: String = "",
    val providerIdPath: String = "id",
    val titlePath: String? = "title",
    val yearPath: String? = "year",
    val streamsPath: String = "",
    val streamUrlPath: String = "url",
    val qualityPath: String? = "quality",
    val audioPath: String? = "audio",
    val languagePath: String? = "language",
    val mimeTypePath: String? = "mimeType",
    val subtitlesPath: String? = "subtitles",
    val subtitleUrlPath: String = "url",
    val subtitleLabelPath: String? = "label"
)

data class ResolverApiSpec(
    val search: ResolverApiRequest? = null,
    val details: ResolverApiRequest? = null,
    val playback: ResolverApiRequest,
    val response: ResolverApiResponse = ResolverApiResponse()
) {
    val isRunnable: Boolean
        get() = playback.url.isNotBlank() &&
            (search == null || search.url.isNotBlank()) &&
            (details == null || details.url.isNotBlank())
}

/** A single catalog entry as published by a repository. */
data class ExtensionManifest(
    val id: String,
    val repoId: String,
    val name: String,
    val description: String,
    val authors: List<String> = emptyList(),
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val apiVersion: Int = EXTENSION_API_VERSION,
    val language: String = "Multi",
    val iconUrl: String? = null,
    val tags: List<String> = emptyList(),
    val status: ExtensionStatus = ExtensionStatus.OK,
    val isNsfw: Boolean = false,
    val installs: Long = 0L,
    val installsLast7Days: Long = 0L,
    val rating: Float = 0f,
    val ratingCount: Int = 0,
    val updatedAt: Long = 0L,
    val homepage: String? = null,
    val engine: ExtensionEngine,
    val isFallback: Boolean = false,
    val installedByDefault: Boolean = false
) {
    /** Globally unique across repositories — two repos may publish the same extension id. */
    val key: String get() = "$repoId/$id"

    val author: String get() = authors.firstOrNull().orEmpty().ifBlank { "Community" }

    val isSupported: Boolean get() = engine.isRunnable && apiVersion <= EXTENSION_API_VERSION
}

/** How often this extension actually produced a playable stream on this device. */
data class ExtensionUsage(
    val successes: Int = 0,
    val failures: Int = 0
) {
    val total: Int get() = successes + failures

    /** Null until there is enough local history to be meaningful. */
    val successRate: Float?
        get() = if (total < MIN_SAMPLES) null else successes.toFloat() / total

    companion object {
        const val MIN_SAMPLES = 4
    }
}

data class MarketplaceExtension(
    val manifest: ExtensionManifest,
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = false,
    val installedVersionCode: Int = 0,
    val installedAt: Long = 0L,
    val usage: ExtensionUsage = ExtensionUsage()
) {
    val key: String get() = manifest.key
    val hasUpdate: Boolean get() = isInstalled && manifest.versionCode > installedVersionCode
    val isActive: Boolean get() = isInstalled && isEnabled && manifest.isSupported
}

data class ExtensionRepo(
    val id: String,
    val url: String,
    val name: String,
    val description: String = "",
    val iconUrl: String? = null,
    val website: String? = null,
    val isBuiltIn: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val extensionCount: Int = 0,
    val error: String? = null
)

enum class MarketplaceSort(val label: String) {
    POPULAR("Popular"),
    TRENDING("Trending"),
    TOP_RATED("Top rated"),
    RECENT("Recently updated"),
    NAME("A–Z")
}

data class ExtensionCatalog(
    val repos: List<ExtensionRepo> = emptyList(),
    val extensions: List<MarketplaceExtension> = emptyList(),
    val isSyncing: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val syncError: String? = null
) {
    val installed: List<MarketplaceExtension> get() = extensions.filter { it.isInstalled }
    val enabled: List<MarketplaceExtension> get() = extensions.filter { it.isInstalled && it.isEnabled }
    val updatable: List<MarketplaceExtension> get() = extensions.filter { it.hasUpdate }
}
