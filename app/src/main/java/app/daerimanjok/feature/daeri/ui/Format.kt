package app.daerimanjok.feature.daeri.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.daerimanjok.R
import app.daerimanjok.feature.daeri.Place
import app.daerimanjok.feature.daeri.PlaceKind
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "집" / "회사" for those bookmarks, else the bookmark's own name. */
@Composable
@ReadOnlyComposable
fun Place.label(): String = when (kind) {
    PlaceKind.HOME -> stringResource(R.string.place_home)
    PlaceKind.OFFICE -> stringResource(R.string.place_office)
    PlaceKind.OTHER -> name.ifBlank { address }
}

@Composable
@ReadOnlyComposable
private fun isKorean() = LocalConfiguration.current.locales[0].language == "ko"

/** Korean subject particle: "집이", "회사가". Other languages get the word unchanged. */
@Composable
@ReadOnlyComposable
fun withSubjectParticle(word: String): String {
    if (!isKorean() || word.isEmpty()) return word
    val last = word.last()
    if (last !in '가'..'힣') return word
    val hasFinal = (last - '가') % 28 != 0
    return word + if (hasFinal) "이" else "가"
}

@Composable
@ReadOnlyComposable
fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60_000).toInt().coerceAtLeast(0)
    return if (totalMin < 60) stringResource(R.string.fmt_minutes, totalMin)
    else stringResource(R.string.fmt_hours_minutes, totalMin / 60, totalMin % 60)
}

@Composable
@ReadOnlyComposable
fun formatDistanceM(meters: Int): String =
    if (meters < 1000) stringResource(R.string.fmt_m, meters)
    else stringResource(R.string.fmt_km, oneDecimal(meters / 1000.0))

fun oneDecimal(v: Double): String = String.format(Locale.US, "%.1f", v)

fun clockTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

/** "오늘" / "어젯밤" / a date, for the report headline. */
@Composable
@ReadOnlyComposable
fun dayWord(tripStartMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val trip = Calendar.getInstance().apply { timeInMillis = tripStartMs }
    val now = Calendar.getInstance().apply { timeInMillis = nowMs }
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(trip, now) -> stringResource(R.string.day_today)
        sameDay(trip, yesterday) -> stringResource(R.string.day_last_night)
        else -> stringResource(R.string.day_date, reportDate(tripStartMs, LocalConfiguration.current.locales[0]))
    }
}

/** "11월 10일 (월)" in Korean, "Mon, 10 Nov" otherwise. */
fun reportDate(ms: Long, locale: Locale): String {
    val pattern = if (locale.language == "ko") "M월 d일 (E)" else "EEE, d MMM"
    return SimpleDateFormat(pattern, locale).format(Date(ms))
}
