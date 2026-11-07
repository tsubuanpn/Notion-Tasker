package com.notiontasks.app.utils

import android.content.SharedPreferences
import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityUtilsTest {

    @Test
    fun openSecurePreferences_returnsAvailable_whenOpenerSucceeds() {
        val preferences = mockk<SharedPreferences>()

        val result = SecurityUtils.openSecurePreferences { preferences }

        assertTrue(result is SecurityUtils.SecurePreferencesAccess.Available)
        assertSame(preferences, (result as SecurityUtils.SecurePreferencesAccess.Available).preferences)
    }

    @Test
    fun openSecurePreferences_returnsUnavailable_whenOpenerThrows() {
        val result = SecurityUtils.openSecurePreferences {
            throw IllegalStateException("keystore unavailable")
        }

        assertTrue(result is SecurityUtils.SecurePreferencesAccess.Unavailable)
    }
}
