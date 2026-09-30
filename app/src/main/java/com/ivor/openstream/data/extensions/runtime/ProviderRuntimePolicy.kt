package com.ivor.openstream.data.extensions.runtime

import java.net.URI

/** Conservative network policy for remotely described providers. */
object ProviderRuntimePolicy {
    const val MAX_RESPONSE_BYTES = 8L * 1024L * 1024L
    const val MAX_REDIRECTS = 5

    /** Only public HTTPS/HTTP endpoints are accepted by generic web runtimes. */
    fun isAllowedUrl(raw: String, allowHttp: Boolean = false): Boolean {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && !(allowHttp && scheme == "http")) return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "localhost" || host.endsWith(".localhost")) return false
        if (host == "127.0.0.1" || host == "::1" || host == "0.0.0.0") return false
        if (host.startsWith("10.") || host.startsWith("192.168.")) return false
        if (host.startsWith("169.254.")) return false
        if (host.startsWith("172.")) {
            val second = host.split('.').getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return false
        }
        return true
    }

    fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> =
        headers.filterKeys { key ->
            key.isNotBlank() && !key.equals("Host", true) &&
                !key.equals("Content-Length", true) &&
                !key.equals("Connection", true)
        }.mapKeys { it.key.trim() }
}
