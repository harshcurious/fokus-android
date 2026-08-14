package dev.fokus.app.settings

import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `defaults keep unlock auto-start on and break auto-start off`() {
        val settings = AppSettings()
        assertTrue(settings.autoStartOnUnlock)
        assertFalse(settings.autoStartFocusAfterBreak)
    }

    @Test
    fun `preferences mapping honors persisted values and defaults`() {
        val prefs = preferencesOf(
            SettingsRepository.Keys.AUTO_START_FOCUS_AFTER_BREAK to true,
            SettingsRepository.Keys.FOCUS_MINUTES to 30,
            SettingsRepository.Keys.SESSIONS_PER_CYCLE to 2,
        )
        val settings = SettingsRepository.preferencesToAppSettings(prefs)
        assertTrue(settings.autoStartFocusAfterBreak)
        assertEquals(30, settings.focusMinutes)
        assertEquals(2, settings.sessionsPerCycle)
        // Defaults for unspecified keys.
        assertTrue(settings.autoStartOnUnlock)
        assertEquals(5, settings.shortBreakMinutes)
        assertEquals(20, settings.longBreakMinutes)
        assertEquals(5, settings.postponeMinutes)
    }

    @Test
    fun `preferences mapping defaults all values when empty`() {
        val settings = SettingsRepository.preferencesToAppSettings(preferencesOf())
        assertEquals(25, settings.focusMinutes)
        assertEquals(5, settings.shortBreakMinutes)
        assertEquals(20, settings.longBreakMinutes)
        assertEquals(4, settings.sessionsPerCycle)
        assertEquals(5, settings.postponeMinutes)
        assertTrue(settings.autoStartOnUnlock)
        assertFalse(settings.autoStartFocusAfterBreak)
    }

    @Test
    fun `autoStartFocusAfterBreak key name is stable`() {
        assertEquals("auto_start_focus_after_break", SettingsRepository.Keys.AUTO_START_FOCUS_AFTER_BREAK.name)
    }
}
