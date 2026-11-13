package app.daerimanjok.feature.daeri.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDetectorTest {

    /** Feeds (time ms, km/h) pairs and returns every event found, including on finish. */
    private fun run(
        samples: List<Pair<Long, Float>>,
        limit: Int? = null,
        brake: Boolean? = null,
    ): List<TripEvent> {
        val d = EventDetector()
        val out = samples.flatMap { (t, v) -> d.onSample(t, v, limit, brake) }
        return out + d.finish()
    }

    private fun overspeeds(e: List<TripEvent>) = e.filter { it.type == EventType.OVERSPEED }

    private fun steady(from: Long, to: Long, kmh: Float, step: Long = 200) = (from..to step step).map { it to kmh }

    // --- 급제동 ---

    @Test fun hardBrakeCountedOncePerStop() {
        val e = run(listOf(0L to 80f, 1_000L to 60f, 2_000L to 40f, 3_000L to 40f, 10_000L to 80f, 11_000L to 60f))
        assertEquals(2, e.count { it.type == EventType.HARD_BRAKE })
    }

    @Test fun gentleBrakingIsNotHard() {
        assertTrue(run(listOf(0L to 60f, 1_000L to 55f)).isEmpty())
    }

    @Test fun burstOfQueuedSamplesIsNotHard() {
        // Samples delivered all at once (e.g. after the app was paused) have ~0 ms gaps.
        assertTrue(run(listOf(0L to 140f, 1L to 100f, 2L to 50f, 3L to 0f)).isEmpty())
    }

    @Test fun hardBrakeFromFrequentSmallSteps() {
        var v = 100f
        val s = (0L..2_000L step 100).map { t -> (t to v).also { v -= 2f } }
        assertEquals(1, run(s).count { it.type == EventType.HARD_BRAKE })
    }

    @Test fun ignoresBrakingFromLowSpeed() {
        assertTrue(run(listOf(0L to 12f, 1_000L to 0f)).isEmpty())
    }

    @Test fun hardBrakeNeedsPedalWhenTheCarReportsIt() {
        val s = listOf(0L to 80f, 1_000L to 60f)
        assertTrue(run(s, brake = false).isEmpty())
        assertEquals(1, run(s, brake = true).size)
    }

    // --- 급가속 ---

    @Test fun hardAcceleration() {
        val e = run(listOf(0L to 30f, 1_000L to 45f))
        assertEquals(EventType.HARD_ACCEL, e.single().type)
    }

    @Test fun oneLongHardAccelerationCountsOnce() {
        // Flat out from 0 to 160 km/h at ~16 km/h per second, like the Virtual Console.
        val s = (0L..10_000L step 250).map { it to (it / 1000f * 16f) }
        assertEquals(1, run(s).count { it.type == EventType.HARD_ACCEL })
    }

    @Test fun twoSeparateHardAccelerationsCountTwice() {
        val s = listOf(0L to 30f, 1_000L to 45f) + steady(1_200, 6_000, 45f) + listOf(7_000L to 46f, 8_000L to 60f)
        assertEquals(2, run(s).count { it.type == EventType.HARD_ACCEL })
    }

    @Test fun normalAccelerationIsNotHard() {
        val s = (0L..5_000L step 500).map { it to (20f + it / 1000f * 6f) } // +6 km/h per second
        assertTrue(run(s).isEmpty())
    }

    // --- 과속 ---

    @Test fun overspeedNeedsThreeSecondsOverLimitPlusMargin() {
        // Limit 80: 91 km/h for 2 s is not enough, 91 for 4 s is.
        assertTrue(overspeeds(run(steady(0, 2_000, 91f) + (2_200L to 85f), limit = 80)).isEmpty())
        val e = overspeeds(run(steady(0, 4_000, 91f) + (4_200L to 85f), limit = 80)).single()
        assertEquals(EventType.OVERSPEED, e.type)
        assertEquals(0L, e.atMs)
        assertEquals(4_200L, e.endMs)
        assertEquals(80, e.limitKmh)
    }

    @Test fun withinMarginIsNotOverspeed() {
        assertTrue(run(steady(0, 10_000, 89f), limit = 80).isEmpty())
    }

    @Test fun overspeedEpisodeIsOneEventWithItsTopSpeed() {
        val s = steady(0, 2_000, 95f) + steady(2_200, 4_000, 102f) + steady(4_200, 6_000, 96f) + (6_200L to 85f)
        val e = overspeeds(run(s, limit = 80))
        assertEquals(1, e.size)
        assertEquals(102f, e.single().maxKmh)
    }

    @Test fun unknownLimitUsesFixedThreshold() {
        assertTrue(run(steady(0, 5_000, 105f)).isEmpty())
        assertEquals(1, run(steady(0, 5_000, 115f)).count { it.type == EventType.OVERSPEED })
    }

    @Test fun overspeedStillRunningAtEndIsClosedByFinish() {
        val e = run(steady(0, 5_000, 100f), limit = 60)
        assertEquals(5_000L, e.single().endMs)
    }
}
