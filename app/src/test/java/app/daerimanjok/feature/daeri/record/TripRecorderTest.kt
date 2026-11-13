package app.daerimanjok.feature.daeri.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecorderTest {

    @Test fun pointEveryTenSecondsWhenNotMoving() {
        val r = TripRecorder(tripId = 1)
        (0L..30_000L step 1_000).forEach { r.onSpeed(it, 0f) }
        // t = 0, 10 s, 20 s, 30 s
        assertEquals(listOf(0L, 10_000L, 20_000L, 30_000L), r.points.value.map { it.atMs })
    }

    @Test fun pointEveryFiftyMetres() {
        val r = TripRecorder(tripId = 1)
        r.onLocation(37.5, 127.0)
        r.onSpeed(0, 50f)
        // ~0.0005° latitude ≈ 55 m
        r.onLocation(37.5005, 127.0)
        r.onSpeed(2_000, 50f)
        r.onLocation(37.5006, 127.0) // ~11 m further: no point
        r.onSpeed(3_000, 50f)
        assertEquals(2, r.points.value.size)
    }

    @Test fun eventForcesAPointAndIsSaved() {
        val r = TripRecorder(tripId = 7)
        r.onSpeed(0, 30f)
        r.onSpeed(1_000, 45f) // 급가속
        assertEquals(2, r.points.value.size)
        val (points, events) = r.drain()
        assertEquals(2, points.size)
        assertEquals(EventType.HARD_ACCEL.name, events.single().type)
        assertEquals(7L, events.single().tripId)
        assertTrue(r.drain().first.isEmpty())
    }

    @Test fun pointsCarryLimitAndRoad() {
        val r = TripRecorder(tripId = 1)
        r.onLimit(80)
        r.onRoad("강변북로")
        r.onSpeed(0, 70f)
        val p = r.points.value.single()
        assertEquals(80, p.limitKmh)
        assertEquals("강변북로", p.road)
    }

    @Test fun haversineIsAboutRight() {
        // 1° of latitude ≈ 111 km
        assertEquals(111_195.0, TripRecorder.metersBetween(37.0, 127.0, 38.0, 127.0), 100.0)
    }
}
