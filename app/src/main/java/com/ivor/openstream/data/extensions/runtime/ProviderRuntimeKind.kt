package com.ivor.openstream.data.extensions.runtime

/** Runtime families OpenStream can support without changing the marketplace contract. */
enum class ProviderRuntimeKind(val key: String) {
    BUILT_IN_KOTLIN("builtin-kotlin"),
    WEB_API("web-api"),
    WEB_HTML("web-html"),
    GRAPHQL("graphql"),
    STREMIO_ADDON("stremio-addon"),
    M3U_PLAYLIST("m3u-playlist"),
    EMBED_PLAYER("web-embed"),
    SANDBOXED_PLUGIN("sandboxed-plugin")
}

/**
 * Maps a manifest engine key to a runtime family without executing repository-supplied code.
 * Unknown values intentionally return null and remain unsupported.
 */
object ProviderRuntimeKindResolver {
    fun resolve(engineKey: String?): ProviderRuntimeKind? = when (engineKey?.trim()?.lowercase()) {
        "builtin-kotlin" -> ProviderRuntimeKind.BUILT_IN_KOTLIN
        "web-api", "rest-api" -> ProviderRuntimeKind.WEB_API
        "web-html", "html" -> ProviderRuntimeKind.WEB_HTML
        "graphql" -> ProviderRuntimeKind.GRAPHQL
        "stremio-addon", "stremio" -> ProviderRuntimeKind.STREMIO_ADDON
        "m3u", "m3u-playlist" -> ProviderRuntimeKind.M3U_PLAYLIST
        "web-embed", "embed" -> ProviderRuntimeKind.EMBED_PLAYER
        "sandboxed-plugin" -> ProviderRuntimeKind.SANDBOXED_PLUGIN
        else -> null
    }
}
