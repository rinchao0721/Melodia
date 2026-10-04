package com.lin0721.linmusic.core.player

private const val MAX_SLEEP_TIMER_HOURS = 23
private const val MINUTES_PER_HOUR = 60
private const val MAX_SLEEP_TIMER_MINUTES = MAX_SLEEP_TIMER_HOURS * MINUTES_PER_HOUR + 59

data class SleepTimerSelection(
    val hours: Int,
    val minutes: Int
) {
    val totalMinutes: Int get() = hours * MINUTES_PER_HOUR + minutes

    fun canAdjustMinutes(delta: Int): Boolean =
        totalMinutes + delta in 0..MAX_SLEEP_TIMER_MINUTES

    fun adjustMinutes(delta: Int): SleepTimerSelection {
        if (!canAdjustMinutes(delta)) return this
        return fromTotalMinutes(totalMinutes + delta)
    }

    fun canAdjustHours(delta: Int): Boolean = hours + delta in 0..MAX_SLEEP_TIMER_HOURS

    fun adjustHours(delta: Int): SleepTimerSelection {
        if (!canAdjustHours(delta)) return this
        return copy(hours = hours + delta)
    }

    companion object {
        fun fromTotalMinutes(totalMinutes: Int): SleepTimerSelection {
            val safeTotal = totalMinutes.coerceIn(0, MAX_SLEEP_TIMER_MINUTES)
            return SleepTimerSelection(
                hours = safeTotal / MINUTES_PER_HOUR,
                minutes = safeTotal % MINUTES_PER_HOUR
            )
        }
    }
}
