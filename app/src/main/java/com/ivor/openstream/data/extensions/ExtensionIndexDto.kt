package com.ivor.openstream.data.extensions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Wire format for an OpenStream extension repository.
 *
 * The primary format is OpenStream's declarative manifest. A few common extension-index fields
 * are also accepted so repositories originating from Mihon/Aninyomi-style APK indexes can be
 * imported and displayed instead of being rejected because they do not contain an OpenStream
 * engine object. APK binaries remain metadata-only until an explicit, isolated provider contract
 * exists; OpenStream never executes repository-supplied APK code in its own process.
 */
@Serializable
data class ExtensionRepoDto(
    val manifestVersion: Int = 1,
    val name: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val website: String? = null,
    /** Indirection to one or more extension lists, as in CloudStream's `pluginLists`. */
    @SerialName("extensionLists")
    val extensionLists: List<String> = emptyList(),
    /** Inline catalog, so a single file can be a complete repository. */
    val extensions: List<ExtensionEntryDto> = emptyList()
)

@Serializable
data class ExtensionEntryDto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val version: String = "1.0.0",
    val versionCode: Int = 1,
    val apiVersion: Int = 1,
    val authors: List<String> = emptyList(),
    val language: String = "Multi",
    val iconUrl: String? = null,
    val tags: List<String> = emptyList(),
    /** 0 down, 1 ok, 2 slow, 3 beta. */
    val status: Int = 1,
    val nsfw: Boolean = false,
    val installs: Long = 0L,
    val installsLast7Days: Long = 0L,
    val rating: Float = 0f,
    val ratingCount: Int = 0,
    /** ISO-8601 date (`2026-08-01`) or epoch millis. */
    val updatedAt: JsonElement? = null,
    val homepage: String? = null,
    val fallback: Boolean = false,
    val installedByDefault: Boolean = false,
    /** Mihon/Aninyomi-compatible package identifier. */
    val pkg: String? = null,
    /** Mihon/Aninyomi-compatible APK download URL. */
    val apk: String? = null,
    /** Mihon/Aninyomi-compatible numeric source code. */
    val code: Int? = null,
    val engine: ExtensionEngineDto = ExtensionEngineDto(type = "unsupported")
)

@Serializable
data class ExtensionEngineDto(
    val type: String,
    val endpoint: String = "",
    val priority: Int = 50,
    val language: String? = null,
    val qualityFilter: String? = null,
    val movieUrl: String? = null,
    val tvUrl: String? = null
)

/** Cached snapshot of one repository, persisted verbatim so the catalog survives being offline. */
@Serializable
data class CachedRepoSnapshot(
    val url: String,
    val name: String,
    val description: String = "",
    val iconUrl: String? = null,
    val website: String? = null,
    val fetchedAt: Long = 0L,
    val extensions: List<ExtensionEntryDto> = emptyList()
)

internal fun JsonElement?.asRawString(): String? = (this as? JsonPrimitive)?.content
