package dev.fokus.app.ui.timer

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.fokus.app.service.FokusService
import dev.fokus.app.settings.AppSettings
import dev.fokus.app.settings.SettingsRepository
import dev.fokus.app.timer.TimerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Binds to [FokusService] and exposes its timer state to the Compose screens. All
 * button presses are forwarded to the service, which owns the engine.
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)

    private val _snapshot = MutableStateFlow<TimerEngine.Snapshot?>(null)
    val snapshot: StateFlow<TimerEngine.Snapshot?> = _snapshot.asStateFlow()

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var service: FokusService? = null
    private var collectJob: kotlinx.coroutines.Job? = null

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as FokusService.LocalBinder).service
                collectJob =
                    viewModelScope.launch { service?.state?.collect { _snapshot.value = it } }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                collectJob?.cancel()
                service = null
            }
        }

    init {
        val context = getApplication<Application>()
        ContextCompat.startForegroundService(context, FokusService.intent(context))
        context.bindService(FokusService.intent(context), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onCleared() {
        collectJob?.cancel()
        getApplication<Application>().unbindService(connection)
        super.onCleared()
    }

    fun start() = service?.startFocus()

    fun pause() = service?.pause()

    fun skip() = service?.skip()

    fun postpone() = service?.postpone()

    fun stop() = service?.stop()

    fun setFocusMinutes(value: Int) = viewModelScope.launch { settingsRepo.setFocusMinutes(value) }

    fun setShortBreakMinutes(value: Int) = viewModelScope.launch { settingsRepo.setShortBreakMinutes(value) }

    fun setLongBreakMinutes(value: Int) = viewModelScope.launch { settingsRepo.setLongBreakMinutes(value) }

    fun setSessionsPerCycle(value: Int) = viewModelScope.launch { settingsRepo.setSessionsPerCycle(value) }

    fun setPostponeMinutes(value: Int) = viewModelScope.launch { settingsRepo.setPostponeMinutes(value) }

    fun setAutoStartOnUnlock(value: Boolean) = viewModelScope.launch { settingsRepo.setAutoStartOnUnlock(value) }

    fun setAutoStartFocusAfterBreak(value: Boolean) = viewModelScope.launch { settingsRepo.setAutoStartFocusAfterBreak(value) }
}
