package app.daerimanjok.core.restrictions

import app.daerimanjok.core.car.Gear
import app.daerimanjok.core.Signal
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveModeTest {

    @Test
    fun uxRestrictionWinsOverGear() {
        assertEquals(DriveMode.DRIVING, driveMode(Signal.Value(true), Signal.Value(Gear.PARK)))
        assertEquals(DriveMode.PARKED, driveMode(Signal.Value(false), Signal.Value(Gear.DRIVE)))
    }

    @Test
    fun fallsBackToGearWhenUxUnavailable() {
        val noUx = Signal.Unavailable("no service")
        assertEquals(DriveMode.PARKED, driveMode(noUx, Signal.Value(Gear.PARK)))
        assertEquals(DriveMode.DRIVING, driveMode(noUx, Signal.Value(Gear.REVERSE)))
    }

    @Test
    fun unknownWhenNothingIsKnown() {
        assertEquals(DriveMode.UNKNOWN, driveMode(Signal.Loading, Signal.Unavailable("x")))
    }
}
