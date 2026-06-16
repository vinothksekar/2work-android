package com.twowork.core.ui

/** Money helpers operate on integer paise, like the backend. */
fun formatMoney(paise: Long, currency: String = "INR"): String {
    val symbol = when (currency.uppercase()) {
        "INR" -> "₹"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> "$currency "
    }
    val whole = paise / 100
    val frac = (paise % 100).toInt().let { if (it < 0) -it else it }
    return "%s%,d.%02d".format(symbol, whole, frac)
}

/** Convert a rupee string (e.g. "1500.50") to integer paise, or null if invalid. */
fun rupeesToPaise(value: String): Long? {
    val trimmed = value.trim()
    if (!Regex("^\\d+(\\.\\d{1,2})?$").matches(trimmed)) return null
    val parts = trimmed.split(".")
    val whole = parts[0].toLongOrNull() ?: return null
    val frac = if (parts.size > 1) parts[1].padEnd(2, '0').toLong() else 0L
    return whole * 100 + frac
}

/** Render an ISO timestamp as a plain calendar date without needing java.time (minSdk 24). */
fun shortDate(iso: String?): String = iso?.take(10).orEmpty()

/** Turn snake/underscored status codes into Title Case labels. */
fun prettyStatus(value: String?): String =
    value.orEmpty().split('_', ' ').filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
