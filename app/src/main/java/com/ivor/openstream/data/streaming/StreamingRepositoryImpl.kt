package com.ivor.openstream.data.streaming

import android.content.SharedPreferences
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.ServerResolution
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.domain.repository.StreamingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class StreamingRepositoryImpl @Inject constructor(
    private val providerRegistry: ExtensionProviderRegistry,
    private val idMappingService: IdMappingService,
    @Named("StreamingClient") private val client: OkHttpClient,
    private val preferences: SharedPreferences
) : StreamingRepository {
    private val providerFailures = ConcurrentHashMap<String, FailureState>()

    override fun resolveServers(
        identity: MediaIdentity,
        includeFallbacks: Boolean
    ): Flow<ServerResolution> = channelFlow {
        val enrichedIdentity = idMappingService.enrich(identity)
        val installedProviders = withContext(Dispatchers.IO) { providerRegistry.activeProviders() }
        val providerPriorities = installedProviders.associate { it.id to it.priority }
        val preferredServerId = preferences.getString(preferenceKey(identity), null)
        val preferredServerProviderId = preferredServerId?.substringBefore(":")
        val now = System.currentTimeMillis()
        val enabledProviders = installedProviders.sortedWith(
            compareBy<ExtensionStreamProvider> { if (it.id == preferredServerProviderId) 0 else 1 }
                .thenBy { it.priority }
        ).filter {
            it.isEnabled && !isCircuitOpen(it.id, now)
        }
        val directProviders = if (includeFallbacks) {
            enabledProviders
        } else {
            enabledProviders.filterNot(ExtensionStreamProvider::isFallback)
        }
        val fallbackProviders = if (includeFallbacks) {
            emptyList()
        } else {
            enabledProviders.filter(ExtensionStreamProvider::isFallback)
        }
        val firstStageProviders = directProviders.ifEmpty { fallbackProviders }
        val deferredFallbackProviders = fallbackProviders.takeIf { directProviders.isNotEmpty() }.orEmpty()
        send(ServerResolution(totalProviders = firstStageProviders.size))
        if (firstStageProviders.isEmpty()) {
            send(ServerResolution(isComplete = true))
            return@channelFlow
        }

        val outcomes = Channel<ProviderOutcome>(enabledProviders.size.coerceAtLeast(1))
        firstStageProviders.forEach { provider ->
            launch(Dispatchers.IO) {
                val result = runCatching {
                    withTimeout(PROVIDER_TIMEOUT_MS) {
                        provider.resolve(enrichedIdentity).getOrThrow()
                    }
                }
                outcomes.send(ProviderOutcome(provider, result))
            }
        }

        var servers = emptyList<VideoServer>()
        val failedProviders = mutableListOf<String>()
        repeat(firstStageProviders.size) { completedIndex ->
            val outcome = outcomes.receive()
            outcome.result.fold(
                onSuccess = { incoming ->
                    providerFailures.remove(outcome.provider.id)
                    providerRegistry.recordOutcome(outcome.provider, incoming.isNotEmpty())
                    if (incoming.isEmpty()) {
                        failedProviders += outcome.provider.displayName
                    } else {
                        servers = ServerRanker.mergeAndRank(
                            existing = servers,
                            incoming = incoming,
                            providerPriorities = providerPriorities,
                            preferredServerId = preferredServerId
                        )
                    }
                },
                onFailure = {
                    recordFailure(outcome.provider.id)
                    providerRegistry.recordOutcome(outcome.provider, false)
                    failedProviders += outcome.provider.displayName
                }
            )
            val completed = completedIndex + 1
            val firstStageComplete = completed == firstStageProviders.size
            val shouldTryFallback = firstStageComplete && deferredFallbackProviders.isNotEmpty()
            send(
                ServerResolution(
                    servers = servers,
                    completedProviders = completed,
                    totalProviders = firstStageProviders.size +
                        if (shouldTryFallback) deferredFallbackProviders.size else 0,
                    failedProviders = failedProviders.toList(),
                    isComplete = firstStageComplete && !shouldTryFallback
                )
            )
        }

        if (deferredFallbackProviders.isNotEmpty()) {
            deferredFallbackProviders.forEach { provider ->
                launch(Dispatchers.IO) {
                    val result = runCatching {
                        withTimeout(PROVIDER_TIMEOUT_MS) {
                            provider.resolve(enrichedIdentity).getOrThrow()
                        }
                    }
                    outcomes.send(ProviderOutcome(provider, result))
                }
            }

            repeat(deferredFallbackProviders.size) { completedIndex ->
                val outcome = outcomes.receive()
                outcome.result.fold(
                    onSuccess = { incoming ->
                        providerFailures.remove(outcome.provider.id)
                        providerRegistry.recordOutcome(outcome.provider, incoming.isNotEmpty())
                        if (incoming.isEmpty()) {
                            failedProviders += outcome.provider.displayName
                        } else {
                            servers = ServerRanker.mergeAndRank(
                                existing = servers,
                                incoming = incoming,
                                providerPriorities = providerPriorities,
                                preferredServerId = preferredServerId
                            )
                        }
                    },
                    onFailure = {
                        recordFailure(outcome.provider.id)
                        providerRegistry.recordOutcome(outcome.provider, false)
                        failedProviders += outcome.provider.displayName
                    }
                )
                val completed = firstStageProviders.size + completedIndex + 1
                send(
                    ServerResolution(
                        servers = servers,
                        completedProviders = completed,
                        totalProviders = firstStageProviders.size + deferredFallbackProviders.size,
                        failedProviders = failedProviders.toList(),
                        isComplete = completedIndex == deferredFallbackProviders.lastIndex
                    )
                )
            }
        }
        outcomes.close()
    }

    override suspend fun getServers(identity: MediaIdentity): List<VideoServer> =
        resolveServers(identity).first { it.servers.isNotEmpty() || it.isComplete }.servers

    override suspend fun refreshServer(server: VideoServer): Result<VideoServer> =
        runCatching {
            val request = Request.Builder()
                .url(server.url)
                .header("Range", "bytes=0-1")
                .apply { server.headers.forEach { (name, value) -> header(name, value) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200 && response.code != 206) {
                    throw IOException("${server.name} returned HTTP ${response.code}")
                }
            }
            server.copy(resolvedAt = System.currentTimeMillis())
        }

    override fun rememberServer(identity: MediaIdentity, server: VideoServer) {
        preferences.edit().putString(preferenceKey(identity), server.id).apply()
    }

    private fun preferenceKey(identity: MediaIdentity): String =
        "last_stream_server:${identity.cacheKey}"

    private fun isCircuitOpen(providerId: String, now: Long): Boolean {
        val state = providerFailures[providerId] ?: return false
        if (now - state.lastFailureAt >= CIRCUIT_BREAKER_COOLDOWN_MS) {
            providerFailures.remove(providerId, state)
            return false
        }
        return state.count >= CIRCUIT_BREAKER_THRESHOLD
    }

    private fun recordFailure(providerId: String) {
        providerFailures.compute(providerId) { _, previous ->
            val now = System.currentTimeMillis()
            val previousState = previous ?: FailureState()
            FailureState(
                count = (previousState.count + 1).coerceAtMost(CIRCUIT_BREAKER_THRESHOLD),
                lastFailureAt = now
            )
        }
    }

    private data class ProviderOutcome(
        val provider: ExtensionStreamProvider,
        val result: Result<List<VideoServer>>
    )

    private data class FailureState(
        val count: Int = 0,
        val lastFailureAt: Long = 0L
    )

    private companion object {
        const val PROVIDER_TIMEOUT_MS = 12_000L
        const val CIRCUIT_BREAKER_THRESHOLD = 5
        const val CIRCUIT_BREAKER_COOLDOWN_MS = 5 * 60_000L
    }
}
