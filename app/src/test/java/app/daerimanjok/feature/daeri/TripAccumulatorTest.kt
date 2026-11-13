package app.daerimanjok.feature.daeri

import app.daerimanjok.core.car.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripAccumulatorTest {

    private fun acc() = TripAccumulator(TripStats(startedAtMs = 0))

    @Test
    fun integratesDistanceAtConstantSpeed() {
        val a = acc()
        a.onSpeed(60f, 0)
        a.onTick(60_000) // one minute at 60 km/h
        assertEquals(1.0, a.stats.estimatedDistanceKm, 0.001)
        assertEquals(60_000, a.stats.movingMs)
    }

    @Test
    fun tracksTopSpeed() {
        val a = acc()
        a.onSpeed(40f, 0)
        a.onSpeed(95f, 1_000)
        a.onSpeed(70f, 10_000)
        assertEquals(95f, a.stats.topSpeedKmh)
    }

    @Test
    fun countsDoorOpeningsNotInitialState() {
        val a = acc()
        a.onDriverDoor(true) // already open when recording starts: not counted
        a.onDriverDoor(false)
        a.onDriverDoor(true)
        a.onDriverDoor(false)
        a.onDriverDoor(true)
        assertEquals(2, a.stats.driverDoorOpens)
    }

    @Test
    fun prefersOdometerWhenItMoves() {
        val a = acc()
        a.onSpeed(60f, 0)
        a.onTick(60_000)
        a.onOdometer(100f)
        a.onOdometer(101.5f)
        assertTrue(a.stats.distanceFromOdometer)
        assertEquals(1.5, a.stats.distanceKm, 0.001)
    }

    @Test
    fun fallsBackToEstimateWhenOdometerStill() {
        val a = acc()
        a.onOdometer(100f)
        a.onSpeed(60f, 0)
        a.onTick(30_000)
        assertFalse(a.stats.distanceFromOdometer)
        assertEquals(0.5, a.stats.distanceKm, 0.001)
    }

    @Test
    fun recordsWhenCarWasParked() {
        val a = acc()
        a.onGear(Gear.PARK, 0) // initial P: not a "parked" event
        a.onGear(Gear.DRIVE, 1_000)
        a.onGear(Gear.PARK, 5_000)
        assertEquals(5_000L, a.stats.lastParkedAtMs)
    }

    @Test
    fun finishStampsEndAndFlushesDistance() {
        val a = acc()
        a.onSpeed(36f, 0)
        val s = a.finish(10_000) // 10 s at 36 km/h = 100 m
        assertEquals(10_000L, s.endedAtMs)
        assertEquals(0.1, s.estimatedDistanceKm, 0.0001)
        assertEquals(10_000, s.durationMs(nowMs = 99_999))
    }
}
