package app.getarcane.android.ui.screens.jobs

/**
 * Tiny best-effort cron-to-prose translator. Recognizes a handful of common patterns (every N
 * minutes/hours/days, daily at midnight, hourly on the hour) and returns `null` for anything it
 * can't confidently describe — the raw cron expression is shown verbatim in those cases. Direct
 * port of the iOS `CronExpression` enum.
 */
object CronExpression {
    fun readable(expression: String): String? {
        val parts = expression.split(" ").filter { it.isNotEmpty() }.toMutableList()
        if (parts.isEmpty()) return null
        // Strip a leading seconds field so 6-field cron behaves like 5-field.
        if (parts.size == 6) parts.removeAt(0)
        if (parts.size != 5) return null

        val minute = parts[0]
        val hour = parts[1]
        val day = parts[2]
        val month = parts[3]
        val weekday = parts[4]

        // Every N minutes
        stepValue(minute)?.let { n ->
            if (hour == "*" && day == "*" && month == "*" && weekday == "*") {
                return if (n == 1) "Every minute" else "Every $n minutes"
            }
        }
        // Every N hours, on the hour
        if (minute == "0" && day == "*" && month == "*" && weekday == "*") {
            stepValue(hour)?.let { n -> return if (n == 1) "Every hour" else "Every $n hours" }
        }
        // Every N days at midnight
        if (minute == "0" && hour == "0" && month == "*" && weekday == "*") {
            stepValue(day)?.let { n -> return if (n == 1) "Daily at midnight" else "Every $n days at midnight" }
        }
        // Daily at HH:00
        if (minute == "0" && day == "*" && month == "*" && weekday == "*") {
            hour.toIntOrNull()?.let { h -> return "Daily at ${formatHour(h)}:00" }
        }
        return null
    }

    private fun stepValue(field: String): Int? {
        if (!field.startsWith("*/")) return null
        return field.drop(2).toIntOrNull()
    }

    private fun formatHour(hour: Int): String {
        val clamped = hour.coerceIn(0, 23)
        return clamped.toString().padStart(2, '0')
    }
}
