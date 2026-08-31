package com.jjrapps.constanza.reminding

/**
 * reminder-response: Snooze Configuration and Re-arm (task 5.6) — exactly these seven values,
 * default 20 minutes.
 *
 * "Unlimited" in that requirement is the snooze COUNT (ratified decision 11): how many times a
 * single occurrence may be re-snoozed, which has no setting and no cap anywhere in this codebase.
 * It is not a duration — there is deliberately no eighth "unlimited" entry here.
 */
enum class SnoozeDuration(val minutes: Int) {
    TEN_MINUTES(minutes = 10),
    TWENTY_MINUTES(minutes = 20),
    THIRTY_MINUTES(minutes = 30),
    ONE_HOUR(minutes = 60),
    TWO_HOURS(minutes = 120),
    THREE_HOURS(minutes = 180),
    FOUR_HOURS(minutes = 240),
    ;

    companion object {
        val DEFAULT = TWENTY_MINUTES

        /** Falls back to [DEFAULT] for a persisted value outside this exact set — e.g. a future
         *  downgrade reading a value a newer build once wrote. */
        fun fromMinutes(minutes: Int): SnoozeDuration = entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}
