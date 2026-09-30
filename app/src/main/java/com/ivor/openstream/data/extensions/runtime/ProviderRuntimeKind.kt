package com.ivor.openstream.data.extensions.runtime

/**
 * Runtime families OpenStream can support without changing the marketplace contract.
 * Protocol-oriented runtimes are declarative and never execute repository-supplied code.
 */
enum class ProviderRuntimeKind(val key: String) {
    BUILT_IN_KOTLIN("builtin-kotlin"),
    WEB_API("web-api"),
    JSON_API("json-api"),
    WEB_HTML("web-html"),
    GRAPHQL("graphql"),
    STREMIO_ADDON("stremio-addon"),
    M3U_PLAYLIST("m3u-playlist"),
    HLS_STREAM("hls-stream"),
    DASH_STREAM("dash-stream"),
    DIRECT_STREAM("direct-stream"),
    EMBED_PLAYER("web-embed"),
    MANIFEST("provider-manifest"),
    REDIRECT_RESOLVER("redirect-resolver"),
    SANDBOXED_PLUGIN("sandboxed-plugin")
}

/** Maps manifest engine keys to app-owned runtime families. Unknown values fail closed. */
object ProviderRuntimeKindResolver {
    fun resolve(engineKey: String?): ProviderRuntimeKind? = when (engineKey?.trim()?.lowercase()) {
        "builtin-kotlin", "builtin" -> ProviderRuntimeKind.BUILT_IN_KOTLIN
        "web-api", "rest-api", "api" -> ProviderRuntimeKind.WEB_API
        "json-api", "json" -> ProviderRuntimeKind.JSON_API
        "web-html", "html" -> ProviderRuntimeKind.WEB_HTML
        "graphql" -> ProviderRuntimeKind.GRAPHQL
        "stremio-addon", "stremio" -> ProviderRuntimeKind.STREMIO_ADDON
        "m3u", "m3u8", "m3u-playlist" -> ProviderRuntimeKind.M3U_PLAYLIST
        "hls", "hls-stream" -> ProviderRuntimeKind.HLS_STREAM
        "dash", "dash-stream", "mpd" -> ProviderRuntimeKind.DASH_STREAM
        "direct", "direct-stream", "mp4", "webm" -> ProviderRuntimeKind.DIRECT_STREAM
        "web-embed", "embed" -> ProviderRuntimeKind.EMBED_PLAYER
        "provider-manifest", "manifest" -> ProviderRuntimeKind.MANIFEST
        "redirect", "redirect-resolver" -> ProviderRuntimeKind.REDIRECT_RESOLVER
        "sandboxed-plugin" -> ProviderRuntimeKind.SANDBOXED_PLUGIN
        else -> null
    }
}
