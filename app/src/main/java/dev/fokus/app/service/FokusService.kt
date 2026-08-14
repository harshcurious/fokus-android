package dev.fokus.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.fokus.app.MainActivity
import dev.fokus.app.R
import dev.fokus.app.overlay.OverlayController
import dev.fokus.app.settings.SettingsRepository
import dev.fokus.app.timer.TimerEngine
import dev.fokus.app.timer.formatCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs the pomodoro [TimerEngine], owns the persistent countdown notification, the
 * break overlay, and the screen-unlock auto-start.
 *
 * The service is sticky and the session is persisted as a wall-clock deadline, so the
 * countdown survives the process being killed - like the plasmoid surviving a Plasma
 * restart.
 */
class FokusService : Service() {

    companion object {
        const val ACTION_START = "dev.fokus.app.action.START"
        const val ACTION_PAUSE = "dev.fokus.app.action.PAUSE"
        const val ACTION_SKIP = "dev.fokus.app.action.SKIP"
        const val ACTION_POSTPONE = "dev.fokus.app.action.POSTPONE"
        const val ACTION_STOP = "dev.fokus.app.action.STOP"

        private const val ONGOING_NOTIFICATION_ID = 1
        private const val EVENT_NOTIFICATION_ID = 2
        private const val TIMER_CHANNEL = "timer"
        private const val EVENTS_CHANNEL = "events"

        private const val STALE_AFTER_MS = 60L * 60 * 1000
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1000L

        fun intent(context: Context, action: String? = null): Intent =
            Intent(context, FokusService::class.java).setAction(action)
    }

    private val engine = TimerEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var overlay: OverlayController

    @Volatile private var autoStartOnUnlock = true

    private val _state = MutableStateFlow(engine.snapshot())

    private var unlockReceiverRegistered = false
    private var sessionLoaded = false
    private var lastNotificationUpdateMs = 0L

    /** The current timer state, observed by the activity, the notification and the overlay. */
    val state: StateFlow<TimerEngine.Snapshot> = _state

    private val unlockReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_PRESENT && autoStartOnUnlock) {
                    if (engine.onScreenUnlocked()) onStateChanged()
                }
            }
        }

    inner class LocalBinder : Binder() {
        val service: FokusService get() = this@FokusService
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(this)
        prefs = getSharedPreferences("session", MODE_PRIVATE)
        overlay =
            OverlayController(this).apply {
                stateFlow = this@FokusService.state
                actions =
                    object : OverlayController.Actions {
                        override fun onStart() = startFocus()

                        override fun onSkip() = skip()

                        override fun onPostpone() = postpone()
                    }
            }

        createChannels()
        startForegroundWithNotification()

        scope.launch {
            // Load settings first: restoreSession clamps stateVal against sessionsPerCycle,
            // so durations must be applied before restore. Keep this non-blocking; the
            // service is already foreground.
            val initial = settingsRepo.settings.first()
            engine.durations = initial.toDurations()
            engine.autoStartFocusAfterBreak = initial.autoStartFocusAfterBreak
            autoStartOnUnlock = initial.autoStartOnUnlock

            restoreSession()
            sessionLoaded = true
            publish()
            updateOngoingNotification()

            ContextCompat.registerReceiver(
                this@FokusService,
                unlockReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            unlockReceiverRegistered = true

            // Tick loop runs only after settings, restore, and receiver are ready.
            launch {
                while (isActive) {
                    val events = engine.tick()
                    if (events.isNotEmpty()) {
                        events.forEach { event ->
                            when (event) {
                                TimerEngine.Event.BREAK_STARTED ->
                                    postEvent(getString(R.string.event_break_started_title), getString(R.string.event_break_started_text))
                                TimerEngine.Event.BREAK_OVERTIME_STARTED ->
                                    postEvent(getString(R.string.event_break_over_title), getString(R.string.event_break_over_text))
                                TimerEngine.Event.FOCUS_STARTED ->
                                    postEvent(getString(R.string.event_focus_started_title), getString(R.string.event_focus_started_text))
                            }
                        }
                        onStateChanged()
                    } else {
                        publish()
                        val nowElapsed = SystemClock.elapsedRealtime()
                        if (nowElapsed - lastNotificationUpdateMs >= NOTIFICATION_UPDATE_INTERVAL_MS) {
                            updateOngoingNotification()
                            lastNotificationUpdateMs = nowElapsed
                        }
                    }
                    delay(250)
                }
            }

            // Keep reacting to settings changes.
            settingsRepo.settings.collect { settings ->
                engine.durations = settings.toDurations()
                engine.autoStartFocusAfterBreak = settings.autoStartFocusAfterBreak
                autoStartOnUnlock = settings.autoStartOnUnlock
                publish()
                updateOngoingNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> engine.start()
            ACTION_PAUSE -> engine.pause()
            ACTION_SKIP -> engine.skip()
            ACTION_POSTPONE -> engine.postpone()
            ACTION_STOP -> engine.stop()
            null -> {
                // Restarted after being killed: just keep ticking. Do not touch state
                // or persistence until restoreSession has finished.
                return START_STICKY
            }
            else -> return START_STICKY
        }
        onStateChanged()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = LocalBinder()

    override fun onDestroy() {
        if (unlockReceiverRegistered) {
            unregisterReceiver(unlockReceiver)
        }
        overlay.hide()
        scope.cancel()
        saveSession()
        super.onDestroy()
    }

    // --- commands called through the binder (activity, overlay) -------------------

    fun startFocus() = dispatch { engine.start() }

    fun pause() = dispatch { engine.pause() }

    fun skip() = dispatch { engine.skip() }

    fun postpone() = dispatch { engine.postpone() }

    fun stop() = dispatch { engine.stop() }

    private fun dispatch(block: () -> Unit) {
        block()
        onStateChanged()
    }

    private fun onStateChanged() {
        publish()
        updateOngoingNotification()
        lastNotificationUpdateMs = SystemClock.elapsedRealtime()
        saveSession()
    }

    private fun publish() {
        _state.value = engine.snapshot()
        overlay.setVisible(shouldShowOverlay(_state.value))
    }

    private fun shouldShowOverlay(s: TimerEngine.Snapshot): Boolean =
        s.isBreak && (s.running || s.overtime)

    // --- persistence ---------------------------------------------------------------

    private fun saveSession() {
        if (!sessionLoaded) return
        val p = engine.serialize()
        prefs.edit()
            .putInt("stateVal", p.stateVal)
            .putLong("deadlineMs", p.deadlineMs)
            .putBoolean("running", p.running)
            .putBoolean("paused", p.paused)
            .putBoolean("overtime", p.overtime)
            .putLong("pausedRemainingMs", p.pausedRemainingMs)
            .putLong("savedAtElapsed", SystemClock.elapsedRealtime())
            .putLong("savedAtWall", System.currentTimeMillis())
            .apply()
    }

    private fun restoreSession() {
        if (!prefs.contains("stateVal")) return
        val rebooted = SystemClock.elapsedRealtime() < prefs.getLong("savedAtElapsed", 0L)
        val stale = System.currentTimeMillis() - prefs.getLong("savedAtWall", 0L) > STALE_AFTER_MS
        if (rebooted || stale) {
            prefs.edit().clear().apply()
            return
        }
        engine.restore(
            TimerEngine.Persisted(
                stateVal = prefs.getInt("stateVal", 0),
                deadlineMs = prefs.getLong("deadlineMs", 0L),
                running = prefs.getBoolean("running", false),
                paused = prefs.getBoolean("paused", false),
                overtime = prefs.getBoolean("overtime", false),
                pausedRemainingMs = prefs.getLong("pausedRemainingMs", 0L),
            )
        )
    }

    // --- notifications ---------------------------------------------------------------

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(TIMER_CHANNEL, getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(EVENTS_CHANNEL, getString(R.string.channel_events), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun startForegroundWithNotification() {
        val notification = buildOngoingNotification(_state.value)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                ONGOING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    private fun statusText(s: TimerEngine.Snapshot): String {
        val time = formatCounter(s.remainingMs / 1000)
        return when {
            s.overtime -> getString(R.string.status_overtime, time)
            s.paused -> getString(R.string.status_paused, time)
            s.phase == TimerEngine.Phase.IDLE -> getString(R.string.status_idle)
            s.phase == TimerEngine.Phase.FOCUS ->
                getString(R.string.status_focus, s.focusIndex, s.sessionsPerCycle, time)
            s.phase == TimerEngine.Phase.LONG_BREAK -> getString(R.string.status_long_break, time)
            else -> getString(R.string.status_short_break, time)
        }
    }

    private fun buildOngoingNotification(s: TimerEngine.Snapshot): Notification {
        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            NotificationCompat.Builder(this, TIMER_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(statusText(s))
                .setContentIntent(openIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setSilent(true)

        fun action(titleRes: Int, actionName: String, requestCode: Int) {
            val pi =
                PendingIntent.getService(
                    this,
                    requestCode,
                    intent(this, actionName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            builder.addAction(0, getString(titleRes), pi)
        }

        when {
            s.overtime -> action(R.string.action_start_focus, ACTION_START, 1)
            s.paused -> action(R.string.action_resume, ACTION_START, 2)
            s.running && s.isBreak -> {
                action(R.string.action_postpone, ACTION_POSTPONE, 3)
                action(R.string.action_skip, ACTION_SKIP, 4)
            }
            s.running -> {
                action(R.string.action_pause, ACTION_PAUSE, 5)
                action(R.string.action_skip, ACTION_SKIP, 4)
            }
            else -> action(R.string.action_start, ACTION_START, 6)
        }
        return builder.build()
    }

    private fun updateOngoingNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(_state.value))
    }

    private fun postEvent(title: String, text: String) {
        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(this, EVENTS_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(EVENT_NOTIFICATION_ID, notification)
    }
}
