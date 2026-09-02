package com.jjrapps.constanza.onboarding

import com.jjrapps.constanza.reminding.NotificationPermissionDecision
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * first-run-onboarding design.md §7 — the API 31-32 label trap's regression guard. [isLastPage]
 * must read `pages.lastIndex`, never a literal `1`: on API 31-32 there is only one page, so index
 * `0` IS the last page. [showsProgress] must be false whenever there is exactly one page — a
 * one-of-one indicator would tell the user there is somewhere else to go when there is not.
 */
class OnboardingUiStateTest {

    private fun state(pages: List<OnboardingPage>, index: Int) =
        OnboardingUiState(pages, index, NotificationPermissionDecision.GRANTED)

    @Test
    fun `with two pages, only the second index is the last page and progress shows`() {
        val twoPages = listOf(OnboardingPage.Intro, OnboardingPage.Notifications)

        val onFirstPage = state(twoPages, index = 0)
        assertFalse(onFirstPage.isLastPage)
        assertTrue(onFirstPage.showsProgress)

        val onSecondPage = state(twoPages, index = 1)
        assertTrue(onSecondPage.isLastPage)
        assertTrue(onSecondPage.showsProgress)
    }

    @Test
    fun `with one page, index 0 is already the last page and progress never shows`() {
        val onePage = listOf(OnboardingPage.Intro)

        val onOnlyPage = state(onePage, index = 0)

        assertTrue(onOnlyPage.isLastPage)
        assertFalse(onOnlyPage.showsProgress)
    }
}
