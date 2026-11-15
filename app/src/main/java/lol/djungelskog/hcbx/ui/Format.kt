package lol.djungelskog.hcbx.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

private val usd: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

fun money(cents: Long, signed: Boolean = false): String {
    val s = usd.format(abs(cents) / 100.0)
    return when {
        cents < 0 -> "-$s"
        signed && cents > 0 -> "+$s"
        else -> s
    }
}

fun moneyInput(text: String): Long? {
    val t = text.replace("$", "").replace(",", "").trim()
    val d = t.toBigDecimalOrNull() ?: return null
    return d.movePointRight(2).toBigInteger().toLong().takeIf { it > 0 }
}

fun parseDate(s: String?): LocalDate? = runCatching {
    if (s == null) null
    else if (s.length <= 10) LocalDate.parse(s)
    else OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
}.getOrNull()

fun niceDate(s: String?): String {
    val d = parseDate(s) ?: return s.orEmpty()
    val today = LocalDate.now()
    return when {
        d == today -> "Today"
        d == today.minusDays(1) -> "Yesterday"
        d.year == today.year -> d.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
        else -> d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

fun fullDate(s: String?): String {
    val d = parseDate(s) ?: return s.orEmpty()
    return d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
}

fun titleCase(s: String) = s.replace('_', ' ').split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

fun initials(name: String) = name.trim().split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
