package dev.fokus.app.timer

/**
 * Pure pomodoro state machine with no Android dependencies, so it can be unit-tested
 * on the JVM with a fake clock.
 *
 * Behaviour mirrors the KDE "Fokus" plasmoid (without flowmodoro/tasks):
 *  - A cycle is [Durations.sessionsPerCycle] focus sessions; the break after the last
 *    one is the long break, every other break is short.
 *  - When a focus session ends, its break starts automatically.
 *  - When a break ends, the timer either stops and keeps counting into the negative
 *    ("overtime") until the user starts the next focus session, or, when
 *    [autoStartFocusAfterBreak] is enabled, automatically starts the next focus
 *    session.
 *  - [skip] jumps to the next session of the cycle; [postpone] aborts a break and
 *    goes back to focus for [Durations.postponeMinutes], after which the break
 *    returns.
 *  - All countdowns derive from a wall-clock deadline, so locking the screen or the
 *    process being frozen does not change when a session ends.
 */
class TimerEngine(private val clock: () -> Long = System::currentTimeMillis) {

    data class Durations(
        val focusMinutes: Int = 25,
        val shortBreakMinutes: Int = 5,
        val longBreakMinutes: Int = 20,
        val sessionsPerCycle: Int = 4,
        val postponeMinutes: Int = 5,
    )

    enum class Phase { IDLE, FOCUS, SHORT_BREAK, LONG_BREAK }

    enum class Event {
        /** A focus session ended and its break started automatically. */
        BREAK_STARTED,

        /** A break reached zero; the timer is now stopped and counting negative. */
        BREAK_OVERTIME_STARTED,

        /** A break ended and the next focus session started automatically. */
        FOCUS_STARTED,
    }

    data class Snapshot(
        val phase: Phase,
        val running: Boolean,
        val paused: Boolean,
        val overtime: Boolean,
        /** 1-based number of the current focus session within the cycle. */
        val focusIndex: Int,
        val sessionsPerCycle: Int,
        /** Planned length of the current session. */
        val plannedSeconds: Int,
        /** Time left; negative while a break overruns. */
        val remainingMs: Long,
    ) {
        val isBreak: Boolean get() = phase == Phase.SHORT_BREAK || phase == Phase.LONG_BREAK
        val progress: Float
            get() =
                if (plannedSeconds <= 0 || phase == Phase.IDLE) 0f
                else (remainingMs.coerceIn(0L, plannedSeconds * 1000L).toFloat() / (plannedSeconds * 1000L))
    }

    var durations = Durations()

    /**
     * When true, a break reaching zero automatically starts the next focus session.
     *
     * Like [durations], this is a plain mutable property: the engine assumes a single
     * thread (or external synchronization) drives [tick] and mutates configuration.
     */
    var autoStartFocusAfterBreak = false

    // Internal state. stateVal is 0 when idle, otherwise 1..2*sessionsPerCycle:
    // odd values are focus sessions, even values are breaks, and 2*sessionsPerCycle
    // is the long break - the same numbering the plasmoid uses.
    private var stateVal = 0
    private var deadlineMs = 0L
    private var pausedRemainingMs = 0L
    private var running = false
    private var paused = false
    private var overtime = false

    private val sessions: Int get() = durations.sessionsPerCycle.coerceAtLeast(1)

    private fun phaseFor(state: Int): Phase =
        when {
            state == 0 -> Phase.IDLE
            state % 2 == 1 -> Phase.FOCUS
            state == 2 * sessions -> Phase.LONG_BREAK
            else -> Phase.SHORT_BREAK
        }

    private fun plannedSecondsFor(state: Int): Int =
        when (phaseFor(state)) {
            Phase.IDLE -> durations.focusMinutes * 60
            Phase.FOCUS -> durations.focusMinutes * 60
            Phase.SHORT_BREAK -> durations.shortBreakMinutes * 60
            Phase.LONG_BREAK -> durations.longBreakMinutes * 60
        }

    private fun nextState(state: Int): Int = if (state < 2 * sessions) state + 1 else 1

    private fun remainingMs(now: Long): Long =
        when {
            running -> deadlineMs - now
            paused -> pausedRemainingMs
            overtime -> deadlineMs - now // negative and falling
            else -> plannedSecondsFor(stateVal) * 1000L
        }

    fun snapshot(now: Long = clock()): Snapshot =
        Snapshot(
            phase = phaseFor(stateVal),
            running = running,
            paused = paused,
            overtime = overtime,
            focusIndex = if (stateVal == 0) 1 else (stateVal + 1) / 2,
            sessionsPerCycle = sessions,
            plannedSeconds = plannedSecondsFor(stateVal),
            remainingMs = remainingMs(now),
        )

    /**
     * The primary action: start a session that is cued up, resume a paused one, or
     * finish an overrunning break and begin the next focus session. No-op while a
     * session is running.
     */
    fun start(now: Long = clock()) {
        when {
            running -> Unit
            overtime -> {
                stateVal = nextState(stateVal)
                overtime = false
                running = true
                deadlineMs = now + plannedSecondsFor(stateVal) * 1000L
            }
            paused -> {
                paused = false
                running = true
                deadlineMs = now + pausedRemainingMs
            }
            else -> {
                if (stateVal == 0) stateVal = 1
                running = true
                deadlineMs = now + plannedSecondsFor(stateVal) * 1000L
            }
        }
    }

    /** Freeze a running session; [start] resumes it where it left off. */
    fun pause(now: Long = clock()) {
        if (!running) return
        pausedRemainingMs = deadlineMs - now
        running = false
        paused = true
    }

    /** Advance to the next session of the cycle. Skipping a break leaves the next
     *  focus cued but not running; skipping a focus starts its break immediately. */
    fun skip(now: Long = clock()) {
        if (stateVal == 0) return
        stateVal = nextState(stateVal)
        overtime = false
        paused = false
        if (phaseFor(stateVal) == Phase.FOCUS) {
            running = false
        } else {
            running = true
            deadlineMs = now + plannedSecondsFor(stateVal) * 1000L
        }
    }

    /** Abort the current break and go back to focus for [Durations.postponeMinutes];
     *  the same break returns when that time is up. No-op outside a break. */
    fun postpone(now: Long = clock()) {
        if (stateVal == 0 || phaseFor(stateVal) == Phase.FOCUS) return
        stateVal = if (stateVal > 1) stateVal - 1 else 1
        overtime = false
        paused = false
        running = true
        deadlineMs = now + durations.postponeMinutes * 60 * 1000L
    }

    /** Reset to idle, discarding the current cycle position. */
    fun stop() {
        stateVal = 0
        running = false
        paused = false
        overtime = false
    }

    /**
     * Screen was unlocked. Starts focus only when nothing is in progress: either the
     * timer is fully stopped, or a break is waiting in overtime for the user to come
     * back. Returns true if a focus session was started.
     */
    fun onScreenUnlocked(now: Long = clock()): Boolean =
        when {
            running || paused -> false
            overtime -> {
                start(now)
                true
            }
            stateVal == 0 -> {
                start(now)
                true
            }
            else -> false
        }

    /** Must be called regularly. Fires the state transitions whose deadlines passed. */
    fun tick(now: Long = clock()): List<Event> {
        if (!running || now < deadlineMs) return emptyList()
        return when (phaseFor(stateVal)) {
            Phase.FOCUS -> {
                // Focus ended: the break starts automatically from now.
                stateVal = nextState(stateVal)
                deadlineMs = now + plannedSecondsFor(stateVal) * 1000L
                listOf(Event.BREAK_STARTED)
            }
            Phase.SHORT_BREAK, Phase.LONG_BREAK -> {
                if (autoStartFocusAfterBreak) {
                    // Break ended: start the next focus session automatically from now.
                    stateVal = nextState(stateVal)
                    deadlineMs = now + plannedSecondsFor(stateVal) * 1000L
                    listOf(Event.FOCUS_STARTED)
                } else {
                    // Break ended: stop, and let the overrun count negative until start().
                    running = false
                    overtime = true
                    listOf(Event.BREAK_OVERTIME_STARTED)
                }
            }
            Phase.IDLE -> emptyList()
        }
    }

    // --- persistence -----------------------------------------------------------
    //
    // The deadline is stored rather than the time left, so the seconds the process
    // was dead are still counted - the same property the plasmoid keeps across a
    // Plasma restart.

    data class Persisted(
        val stateVal: Int,
        val deadlineMs: Long,
        val running: Boolean,
        val paused: Boolean,
        val overtime: Boolean,
        val pausedRemainingMs: Long,
    )

    fun serialize(): Persisted =
        Persisted(
            stateVal = stateVal,
            deadlineMs = deadlineMs,
            running = running,
            paused = paused,
            overtime = overtime,
            pausedRemainingMs = pausedRemainingMs,
        )

    fun restore(p: Persisted) {
        stateVal = p.stateVal.coerceIn(0, 2 * sessions)
        deadlineMs = p.deadlineMs
        running = p.running
        paused = p.paused
        overtime = p.overtime
        pausedRemainingMs = p.pausedRemainingMs
    }
}
