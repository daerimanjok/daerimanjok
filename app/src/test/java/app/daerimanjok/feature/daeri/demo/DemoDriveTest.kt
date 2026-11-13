package app.daerimanjok.feature.daeri.demo

import app.daerimanjok.core.car.Gear
import app.daerimanjok.feature.daeri.record.EventDetector
import app.daerimanjok.feature.daeri.record.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDriveTest {

    private val frames = DemoDrive.frames

    @Test fun eventsAreExactlyWhatTheDemoShows() {
        val d = EventDetector()
        val events = frames.flatMap { d.onSample(it.tMs, it.kmh, it.limitKmh, brakePressed = null) } + d.finish()
        assertEquals(listOf(EventType.HARD_ACCEL), events.map { it.type })
        // Within every limit, so the report shows 100 % 제한속도 준수.
        assertTrue(frames.all { f -> f.limitKmh == null || f.kmh <= f.limitKmh })
    }

    @Test fun startsAndEndsParkedAndReachesTheEnd() {
        assertEquals(Gear.PARK, frames.first().gear)
        assertEquals(Gear.PARK, frames.last().gear)
        assertEquals(0, frames.last().remainingM)
        assertEquals(0f, frames.last().kmh)
    }

    @Test fun realisticLengthAndSpeeds() {
        val minutes = frames.last().tMs / 60_000.0
        assertTrue("drive takes $minutes min", minutes in 9.0..11.0)
        assertTrue(frames.maxOf { it.kmh } < 80f + 10f)
        assertTrue("route ${frames.first().remainingM} m", frames.first().remainingM in 7_000..11_000)
        // Stops at one red light on the way.
        val stopsMidway = frames.zipWithNext().count { (a, b) -> a.kmh > 0f && b.kmh == 0f && b.remainingM > 1_000 }
        assertEquals(1, stopsMidway)
    }
}
