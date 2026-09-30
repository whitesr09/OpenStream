package com.ivor.openstream.data.streaming

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks short-lived provider health so repeated failures degrade priority without permanent lockout.
 */
class ProviderHealthTracker(
    private val failureThreshold: Int,
    private val cooldownMs: Long,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val failures = ConcurrentHashMap<String, FailureState>()

    fun canAttempt(providerId: String): Boolean {
        val timestamp = now()
        val state = failures[providerId] ?: return true
        if (state.consecutive < failureThreshold) return true
        val stillCoolingDown = (timestamp - state.lastFailureAtMs) < cooldownMs
        if (!stillCoolingDown) failures.remove(providerId)
        return !stillCoolingDown
    }

    fun recordSuccess(providerId: String) {
        failures.remove(providerId)
    }

    fun recordFailure(providerId: String) {
        val timestamp = now()
        failures.compute(providerId) { _, previous ->
            val count = (previous?.consecutive ?: 0) + 1
            FailureState(consecutive = count, lastFailureAtMs = timestamp)
        }
    }

    internal data class FailureState(
        val consecutive: Int,
        val lastFailureAtMs: Long
    )
}
