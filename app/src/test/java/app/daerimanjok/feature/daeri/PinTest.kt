package app.daerimanjok.feature.daeri

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {

    @Test
    fun validatesFormat() {
        assertTrue(Pin.isValid("0420"))
        assertFalse(Pin.isValid("042"))
        assertFalse(Pin.isValid("04a0"))
    }

    @Test
    fun matchesOnlyTheRightPin() {
        val salt = Pin.newSalt()
        val hash = Pin.hash("1234", salt)
        assertTrue(Pin.matches("1234", salt, hash))
        assertFalse(Pin.matches("4321", salt, hash))
    }

    @Test
    fun saltChangesHash() {
        assertNotEquals(Pin.hash("1234", Pin.newSalt()), Pin.hash("1234", Pin.newSalt()))
    }
}
