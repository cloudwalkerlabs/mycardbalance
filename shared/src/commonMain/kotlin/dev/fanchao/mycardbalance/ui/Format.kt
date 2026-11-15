package dev.fanchao.mycardbalance.ui

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Today, 3:42 pm", "Yesterday, 9:05 am", "12 Nov, 1:00 pm", or "12 Nov 2025" for older years. */
fun formatTimestamp(instant: Instant, now: Instant = Clock.System.now(), zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val t = instant.toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone).date
    val time = formatTime(t)
    return when {
        t.date == today -> "Today, $time"
        t.date.toEpochDays() == today.toEpochDays() - 1 -> "Yesterday, $time"
        t.date.year == today.year -> "${t.day} ${months[t.month.ordinal]}, $time"
        else -> "${t.day} ${months[t.month.ordinal]} ${t.date.year}"
    }
}

private fun formatTime(t: LocalDateTime): String {
    val h = t.hour % 12
    val hour = if (h == 0) 12 else h
    val min = t.minute.toString().padStart(2, '0')
    return "$hour:$min ${if (t.hour < 12) "am" else "pm"}"
}
