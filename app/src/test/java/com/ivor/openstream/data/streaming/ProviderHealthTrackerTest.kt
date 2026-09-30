package com.ivor.openstream.data.streaming

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHealthTrackerTest {
    private var nowMs = 0L

    private val tracker = ProviderHealthTracker(
        failureThreshold = 3,
        cooldownMs = 1_000L,
        now = { nowMs }
    )

    @Test
    fun blocksOnlyAfterThresholdAndRecoversAfterCooldown() {
        repeat(2) {
            tracker.recordFailure("a")
            assertTrue(tracker.canAttempt("a"))
        }

        tracker.recordFailure("a")
        assertFalse(tracker.canAttempt("a"))

        nowMs += 1_001L
        assertTrue(tracker.canAttempt("a"))
    }

    @Test
    fun successClearsFailurePenaltyImmediately() {
        repeat(3) { tracker.recordFailure("a") }
        assertFalse(tracker.canAttempt("a"))

        tracker.recordSuccess("a")
        assertTrue(tracker.canAttempt("a"))
    }
}
