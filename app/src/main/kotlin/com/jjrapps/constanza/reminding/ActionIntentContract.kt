package com.jjrapps.constanza.reminding

/**
 * design.md §8.2/§9.1: the intent contract every notification action targets, frozen here in
 * work unit 5-i so 5-ii's `ActionReceiver` implements against an already-fixed contract instead
 * of the two slices co-designing it across a PR boundary.
 *
 * [ACTION_RECEIVER_CLASS] is referenced by fully-qualified string, not by class literal, because
 * that receiver does not exist yet in this slice — [android.content.Intent.setClassName] lets
 * this contract compile and run correctly today (tapping an action currently does nothing, since
 * nothing is registered to receive it) without a forward reference to unwritten code.
 *
 * [EXTRA_OCCURRENCE_ID]'s value is always `occurrence.id` (design.md §8.2) — the same integer
 * already doing triple duty as the notification id and the alarm's own `PendingIntent` request
 * code. The three actions below share that same request code too; they stay distinct system
 * `PendingIntent`s because `PendingIntent` identity compares `Intent.filterEquals` (action, data,
 * component, categories — never extras), and [ACTION_YES]/[ACTION_NO]/[ACTION_SNOOZE] are three
 * different action strings.
 */
object ActionIntentContract {
    const val ACTION_RECEIVER_CLASS = "com.jjrapps.constanza.reminding.ActionReceiver"

    const val ACTION_YES = "com.jjrapps.constanza.action.YES"
    const val ACTION_NO = "com.jjrapps.constanza.action.NO"
    const val ACTION_SNOOZE = "com.jjrapps.constanza.action.SNOOZE"

    const val EXTRA_OCCURRENCE_ID = "occurrenceId"
}
