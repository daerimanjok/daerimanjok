package app.daerimanjok.feature.daeri

import java.security.MessageDigest
import java.security.SecureRandom

/** Owner PIN. Only a salted SHA-256 hash is stored; the PIN itself never is. */
object Pin {
    const val LENGTH = 4

    fun isValid(pin: String) = pin.length == LENGTH && pin.all(Char::isDigit)

    fun newSalt(): String = ByteArray(16).also(SecureRandom()::nextBytes).toHex()

    fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).toHex()

    fun matches(pin: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expectedHash.toByteArray())

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
