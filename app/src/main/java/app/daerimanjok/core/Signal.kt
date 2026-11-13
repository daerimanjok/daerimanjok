package app.daerimanjok.core

/**
 * A live value read from the car.
 *
 * Support varies by car model and not every property exists on the emulator, so
 * every signal can be [Unavailable]. UI must handle that case instead of crashing.
 */
sealed interface Signal<out T> {
    data object Loading : Signal<Nothing>
    data class Value<T>(val value: T) : Signal<T>
    data class Unavailable(val reason: String) : Signal<Nothing>
}

fun <T> Signal<T>.valueOrNull(): T? = (this as? Signal.Value)?.value
