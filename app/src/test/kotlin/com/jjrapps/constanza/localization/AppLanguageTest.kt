package com.jjrapps.constanza.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** app-localization: [AppLanguage]'s tag round-trip and [AppLanguage.SystemDefault]'s null tag —
 *  the tri-state clear (design.md D7) depends on this staying exactly `null`, never a third
 *  persisted string value. */
class AppLanguageTest {

    @Test
    fun `SystemDefault has a null tag`() {
        assertNull(AppLanguage.SystemDefault.tag)
    }

    @Test
    fun `fromTag round-trips every declared language's own tag`() {
        for (language in AppLanguage.entries) {
            assertEquals(language, AppLanguage.fromTag(language.tag))
        }
    }

    @Test
    fun `fromTag with null resolves to SystemDefault`() {
        assertEquals(AppLanguage.SystemDefault, AppLanguage.fromTag(null))
    }

    @Test
    fun `fromTag with an unsupported tag falls back to SystemDefault`() {
        assertEquals(AppLanguage.SystemDefault, AppLanguage.fromTag("fr"))
    }
}
