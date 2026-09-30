package com.ivor.openstream.data.extensions.runtime

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/** Conservative network policy for remotely described providers. */
object ProviderRuntimePolicy {
    const val MAX_RESPONSE_BYTES = 8L * 1024L * 1024L
    private const val MAX_HEADER_COUNT = 32
    private const val MAX_HEADER_NAME_LENGTH = 128
    private const val MAX_HEADER_VALUE_LENGTH = 4096

    /** Only public HTTPS/HTTP endpoints are accepted by generic web runtimes. */
    fun isAllowedUrl(raw: String, allowHttp: Boolean = false): Boolean {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && !(allowHttp && scheme == "http")) return false
        if (!uri.userInfo.isNullOrBlank()) return false
        val host = uri.host?.lowercase() ?: return false
        if (isBlockedLiteralHost(host)) return false

        // Reject literal IPs that are private, loopback, link-local or otherwise local-only.
        // DNS names are still allowed here; the HTTP stack remains responsible for normal TLS.
        val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull().orEmpty()
        if (addresses.isNotEmpty() && addresses.all(::isPrivateAddress)) return false
        return true
    }

    fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> {
        if (headers.size > MAX_HEADER_COUNT) return emptyMap()
        return headers.mapNotNull { (rawKey, rawValue) ->
            val key = rawKey.trim()
            val value = rawValue.trim()
            if (
                key.isBlank() ||
                key.length > MAX_HEADER_NAME_LENGTH ||
                value.length > MAX_HEADER_VALUE_LENGTH ||
                key.equals("Host", true) ||
                key.equals("Content-Length", true) ||
                key.equals("Connection", true) ||
                key.equals("Transfer-Encoding", true)
            ) {
                null
            } else {
                key to value
            }
        }.toMap()
    }

    private fun isBlockedLiteralHost(host: String): Boolean =
        host == "localhost" ||
            host.endsWith(".localhost") ||
            host == "0.0.0.0" ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host == "[::1]"

    private fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return true

        val bytes = address.address
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            // RFC 6598 shared address space and RFC 5737 documentation ranges are not useful
            // as public provider endpoints.
            if (first == 100 && second in 64..127) return true
            if (first == 192 && second == 0) return true
            if (first == 198 && second in 18..19) return true
            if (first == 198 && second == 51) return true
            if (first == 203 && second == 0) return true
        } else if (address is Inet6Address) {
            val first = bytes.firstOrNull()?.toInt()?.and(0xff) ?: return false
            // Unique-local (fc00::/7) and link-local (fe80::/10).
            if (first and 0xfe == 0xfc) return true
            if (first == 0xfe) {
                val second = bytes.getOrNull(1)?.toInt()?.and(0xff) ?: 0
                if (second and 0xc0 == 0x80) return true
            }
        }
        return false
    }
}
