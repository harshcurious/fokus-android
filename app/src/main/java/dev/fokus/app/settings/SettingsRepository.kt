package dev.fokus.app.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.fokus.app.timer.TimerEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 20,
    val sessionsPerCycle: Int = 4,
    val postponeMinutes: Int = 5,
    val autoStartOnUnlock: Boolean = true,
    val autoStartFocusAfterBreak: Boolean = false,
) {
    fun toDurations(): TimerEngine.Durations =
        TimerEngine.Durations(
            focusMinutes = focusMinutes,
            shortBreakMinutes = shortBreakMinutes,
            longBreakMinutes = longBreakMinutes,
            sessionsPerCycle = sessionsPerCycle,
            postponeMinutes = postponeMinutes,
        )
}

class SettingsRepository(private val context: Context) {

    internal object Keys {
        val FOCUS_MINUTES = intPreferencesKey("focus_minutes")
        val SHORT_BREAK_MINUTES = intPreferencesKey("short_break_minutes")
        val LONG_BREAK_MINUTES = intPreferencesKey("long_break_minutes")
        val SESSIONS_PER_CYCLE = intPreferencesKey("sessions_per_cycle")
        val POSTPONE_MINUTES = intPreferencesKey("postpone_minutes")
        val AUTO_START_ON_UNLOCK = booleanPreferencesKey("auto_start_on_unlock")
        val AUTO_START_FOCUS_AFTER_BREAK = booleanPreferencesKey("auto_start_focus_after_break")
    }

    companion object {
        internal fun preferencesToAppSettings(prefs: Preferences): AppSettings =
            AppSettings(
                focusMinutes = prefs[Keys.FOCUS_MINUTES] ?: 25,
                shortBreakMinutes = prefs[Keys.SHORT_BREAK_MINUTES] ?: 5,
                longBreakMinutes = prefs[Keys.LONG_BREAK_MINUTES] ?: 20,
                sessionsPerCycle = prefs[Keys.SESSIONS_PER_CYCLE] ?: 4,
                postponeMinutes = prefs[Keys.POSTPONE_MINUTES] ?: 5,
                autoStartOnUnlock = prefs[Keys.AUTO_START_ON_UNLOCK] ?: true,
                autoStartFocusAfterBreak = prefs[Keys.AUTO_START_FOCUS_AFTER_BREAK] ?: false,
            )
    }

    val settings: Flow<AppSettings> =
        context.dataStore.data.map { preferencesToAppSettings(it) }

    suspend fun setFocusMinutes(value: Int) = edit { it[Keys.FOCUS_MINUTES] = value }

    suspend fun setShortBreakMinutes(value: Int) = edit { it[Keys.SHORT_BREAK_MINUTES] = value }

    suspend fun setLongBreakMinutes(value: Int) = edit { it[Keys.LONG_BREAK_MINUTES] = value }

    suspend fun setSessionsPerCycle(value: Int) = edit { it[Keys.SESSIONS_PER_CYCLE] = value }

    suspend fun setPostponeMinutes(value: Int) = edit { it[Keys.POSTPONE_MINUTES] = value }

    suspend fun setAutoStartOnUnlock(value: Boolean) = edit { it[Keys.AUTO_START_ON_UNLOCK] = value }

    suspend fun setAutoStartFocusAfterBreak(value: Boolean) = edit { it[Keys.AUTO_START_FOCUS_AFTER_BREAK] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
