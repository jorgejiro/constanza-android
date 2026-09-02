package com.jjrapps.constanza.reminding

import com.jjrapps.constanza.scheduling.AlarmScheduler
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * reminder-delivery: Exact-Alarm Permission States — "Declining onboarding's offer costs nothing
 * later" — and Exact-Alarm Banner, Standing Fallback — "Declining onboarding's ask does not
 * suppress the banner" (spec.md). Both scenarios are proven by an *absence*: nothing in this app
 * persists "the user was asked about exact alarms" or "the user declined", so nothing exists for a
 * later decline to flip. `sdd-verify`'s hard rule — a scenario is compliant only when a covering
 * test passed at runtime — has no carve-out for negative/structural-absence claims, however strong
 * the grep evidence. This test turns "we grepped and found nothing" into a runtime assertion that
 * enumerates the actual public surface of the two types that could plausibly hold such a flag, and
 * finds none.
 *
 * **Why this, not a positive test.** A positive test proving "declining costs nothing" would have
 * to fabricate the very persisted "declined" state the requirement says must never exist — proving
 * the test's own fixture, not the production code. Enumerating the surface instead proves the real
 * constraint: there is no member here for a "declined" precondition to vary.
 *
 * **What would make this fail, and why it matters.** Any new public member on [AlarmScheduler] or
 * [ReminderSettingsStore] that reads as recording, checking, or gating on "having asked" about
 * exact alarms — `record*`, `has*Asked*`, `has*Requested*`, a boolean flag getter, and so on. If
 * this test ever fails on a real addition, the fix is never to rename that member: it is to delete
 * it. A persisted "we asked about exact alarms" flag is exactly what would let declining during
 * onboarding suppress the standing
 * [com.jjrapps.constanza.tracking.ExactAlarmBanner] later, which the spec forbids —
 * [AlarmScheduler.canScheduleExactAlarms] must stay the one and only source of truth, re-checked
 * live on every call, never cached behind a stored decision.
 *
 * **How the pattern was chosen, not hand-listed.** A fixed roster of forbidden method names (`"do
 * not add recordExactAlarmAsked, hasAskedExactAlarm, ..."`) would rot the moment a future author
 * picked a synonym it didn't list. Reflection plus a name-shape pattern catches the whole family
 * instead of enumerating it. [ReminderSettingsStore] already legitimately exposes
 * `hasRequestedNotificationPermission`/`recordRequestedNotificationPermission` for
 * `POST_NOTIFICATIONS` — precisely the shape this test forbids for exact alarms — so its check does
 * not ban the shape outright; it bans that shape combined with any exact-alarm naming, which is the
 * one combination this feature must never introduce. [AlarmScheduler] has no legitimate
 * persistence-shaped member at all today, so its check is unconditional.
 *
 * **What it cannot see.** It reads declared members by name, so a flag stored under a
 * deliberately-obscure name, or state added to a third type this test does not know about, slips
 * past. It also cannot see a flag smuggled through an existing parameter's semantics rather than a
 * new member. It is a floor, not a proof of the entire codebase's innocence — the "obvious other
 * candidate" named by `sdd-verify`'s own re-verify pass, made executable.
 */
class NoExactAlarmAskPersistenceTest {

    @Test
    fun `AlarmScheduler exposes no persistence-shaped member for exact alarms`() {
        val offenders = publicMembersOf(AlarmScheduler::class.java).filter(::isPersistenceShaped)
        assertTrue(
            actual = offenders.isEmpty(),
            message = "AlarmScheduler gained ${offenders.joinToString { it.name }}, which reads as " +
                "recording or checking an \"already asked\" flag for exact alarms. AlarmScheduler " +
                "must only ever re-check the live system permission via canScheduleExactAlarms() — " +
                "a persisted \"we asked\" flag here would let declining during onboarding suppress " +
                "the standing exact-alarm banner later, which reminder-delivery's spec forbids. " +
                "Delete the member rather than renaming it.",
        )
    }

    @Test
    fun `ReminderSettingsStore gained no exact-alarm equivalent of its notification-ask latch`() {
        val offenders = publicMembersOf(ReminderSettingsStore::class.java)
            .filter(::isPersistenceShaped)
            .filter { member -> member.name.namesExactAlarm() }
        assertTrue(
            actual = offenders.isEmpty(),
            message = "ReminderSettingsStore gained ${offenders.joinToString { it.name }} — an " +
                "exact-alarm equivalent of hasRequestedNotificationPermission / " +
                "recordRequestedNotificationPermission. Onboarding's exact-alarm ask must never " +
                "gain that kind of latch: a persisted \"we asked\" flag would let declining during " +
                "onboarding suppress the standing exact-alarm banner later, which the spec " +
                "forbids. Delete the member rather than renaming it.",
        )
    }

    /**
     * A scan that quietly reaches nothing would pass both tests above for the wrong reason — the
     * same failure mode [ControlStrokeCallSiteTest][com.jjrapps.constanza.core.ui.theme.ControlStrokeCallSiteTest]
     * and [ViewModelTeardownCallSiteTest][com.jjrapps.constanza.habit.ViewModelTeardownCallSiteTest]
     * both guard against one level up. Pinned against the real classes so a refactor that renames or
     * hides them is caught here, rather than by this guard silently checking an empty list forever.
     */
    @Test
    fun `the scan actually reaches both types' declared public members`() {
        assertTrue(
            actual = publicMembersOf(AlarmScheduler::class.java).isNotEmpty(),
            message = "Found no public members on AlarmScheduler at all; the reflection scan is " +
                "broken and every assertion above is vacuously true.",
        )
        assertTrue(
            actual = publicMembersOf(ReminderSettingsStore::class.java).isNotEmpty(),
            message = "Found no public members on ReminderSettingsStore at all; the reflection " +
                "scan is broken and every assertion above is vacuously true.",
        )
    }

    private companion object {

        /**
         * Declared, public, non-synthetic instance members of [type] — its own surface, not
         * whatever it inherits from `Any`/`Object`. Fields are scanned alongside methods so a
         * future `val hasAskedExactAlarm: Boolean` (a Kotlin property, compiled as a field plus a
         * getter) is still caught by name.
         */
        fun publicMembersOf(type: Class<*>): List<Member> =
            (type.declaredMethods.toList() + type.declaredFields.toList())
                .filterNot { member -> member.isSynthetic }
                .filter { member -> Modifier.isPublic(member.modifiers) }

        /**
         * A name that *records* or *checks* a completed action, regardless of exactly which verb or
         * noun a future author reaches for. `has`/`is`/`get` alone would also match legitimate live
         * checks such as `canScheduleExactAlarms` or `getSnoozeDuration`, so a name only counts when
         * it additionally names a completion — asked, requested, declined, prompted, a stored flag,
         * or a "done" marker (the shape `setOnboardingDone`/`onboardingDone` already uses for a
         * different, legitimate one-shot flag).
         */
        val PERSISTENCE_VERB = Regex("(?i)^(record|has|is|get|set)[A-Za-z0-9]*")
        val COMPLETION_NOUN = Regex("(?i)(asked|requested|declined|prompted|flag|consumed|done)")

        fun isPersistenceShaped(member: Member): Boolean =
            PERSISTENCE_VERB.containsMatchIn(member.name) && COMPLETION_NOUN.containsMatchIn(member.name)

        fun String.namesExactAlarm(): Boolean = Regex("(?i)exact.?alarm").containsMatchIn(this)
    }
}
