//
//  Formatting.kt
//  Starshower Run
//
//  Small text helpers shared by the core catalog strings and the UI.
//

package com.starshower.run.core.progression

import java.text.NumberFormat

/** Locale-aware grouping, e.g. 15000 → "15,000". */
fun Int.formatted(): String = NumberFormat.getIntegerInstance().format(this)

/** "1:05" style formatting for seconds. */
val Int.clockString: String
    get() = String.format("%d:%02d", this / 60, this % 60)

/** 1.25 → "1.25", 1.3 → "1.3", 1.0 → "1" */
val Double.compactString: String
    get() = String.format(java.util.Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')

/** "HH:MM:SS" countdown for a number of seconds. */
fun countdownString(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    return String.format("%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}
