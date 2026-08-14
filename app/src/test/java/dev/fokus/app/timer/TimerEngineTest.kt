package dev.fokus.app.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    private var now = 1_000_000L

    private fun engine(durations: TimerEngine.Durations = TimerEngine.Durations()): TimerEngine {
        now = 1_000_000L
        return TimerEngine { now }
    }

    @Test
    fun `idle snapshot shows full focus length, not running`() {
        val e = engine()
        val s = e.snapshot()
        assertEquals(TimerEngine.Phase.IDLE, s.phase)
        assertFalse(s.running)
        assertEquals(25 * 60, s.plannedSeconds)
        assertEquals(25 * 60 * 1000L, s.remainingMs)
        assertEquals(1, s.focusIndex)
        assertEquals(4, s.sessionsPerCycle)
    }

    @Test
    fun `start begins first focus session counting down`() {
        val e = engine()
        e.start()
        now += 61_000
        val s = e.snapshot()
        assertEquals(TimerEngine.Phase.FOCUS, s.phase)
        assertTrue(s.running)
        assertEquals((25 * 60 - 61) * 1000L, s.remainingMs)
    }

    @Test
    fun `focus end auto-starts short break`() {
        val e = engine()
        e.start()
        now += 25 * 60 * 1000L
        val events = e.tick()
        assertEquals(listOf(TimerEngine.Event.BREAK_STARTED), events)
        val s = e.snapshot()
        assertEquals(TimerEngine.Phase.SHORT_BREAK, s.phase)
        assertTrue(s.running)
        assertEquals(5 * 60 * 1000L, s.remainingMs)
    }

    @Test
    fun `break end stops and counts negative overtime`() {
        val e = engine()
        e.start()
        now += 25 * 60 * 1000L
        e.tick() // break starts
        now += 5 * 60 * 1000L
        val events = e.tick()
        assertEquals(listOf(TimerEngine.Event.BREAK_OVERTIME_STARTED), events)
        now += 90_000 // sit in the overrun for 90 more seconds
        val s = e.snapshot()
        assertTrue(s.overtime)
        assertFalse(s.running)
        assertEquals(-90_000L, s.remainingMs)
        assertEquals(TimerEngine.Phase.SHORT_BREAK, s.phase)
    }

    @Test
    fun `start during overtime begins next focus`() {
        val e = engine()
        e.start()
        now += 25 * 60 * 1000L
        e.tick()
        now += 5 * 60 * 1000L
        e.tick() // overtime begins
        now += 30_000
        e.start()
        val s = e.snapshot()
        assertEquals(TimerEngine.Phase.FOCUS, s.phase)
        assertTrue(s.running)
        assertFalse(s.overtime)
        assertEquals(2, s.focusIndex)
        assertEquals(25 * 60 * 1000L, s.remainingMs)
    }

    @Test
    fun `fourth focus is followed by the long break, then the cycle wraps`() {
        val e = engine()
        e.start()
        repeat(3) {
            now += 25 * 60 * 1000L; e.tick() // break starts
            now += 5 * 60 * 1000L; e.tick() // break overruns
            e.start() // next focus
        }
        assertEquals(4, e.snapshot().focusIndex)
        now += 25 * 60 * 1000L
        e.tick()
        assertEquals(TimerEngine.Phase.LONG_BREAK, e.snapshot().phase)
        assertEquals(20 * 60 * 1000L, e.snapshot().remainingMs)
        now += 20 * 60 * 1000L
        e.tick() // long break overruns
        e.start() // wraps to focus 1 of the next cycle
        val s = e.snapshot()
        assertEquals(TimerEngine.Phase.FOCUS, s.phase)
        assertEquals(1, s.focusIndex)
    }

    @Test
    fun `skip during focus starts the break immediately`() {
        val e = engine()
        e.start()
        now += 60_000
        e.skip()
        val s = e.snapshot()
        assertEquals(TimerEngine.Phase.SHORT_BREAK, s.phase)
        assertTrue(s.running)
        assertEquals(5 * 60 * 1000L, s.remainingMs)
    }

    @Test
    fun `skip during break cues the next focus without starting it`() {
        val e = engine()
        e.start()
        now += 25 * 60 * 1000L
        e.tick() // break running
        e.skip()
        val s = e.snapshot()
        assertEquals(TimerEngine.Phase.FOCUS, s.phase)
        assertFalse(s.running)
        assertFalse(s.overtime)
        assertEquals(2, s.focusIndex)
        assertEquals(25 * 60 * 1000L, s.remainingMs)
    }

    @Test
    fun `skip during overtime clears the overtime and cues focus`() {
        val e = engine()
        e.start()
        now += 25 * 60 * 1000L; e.tick()
        now += 5 * 60 * 1000L; e.tick() // overtime
        e.skip()
        val s = e.snapshot()
        assertEquals(TimerEngine.Phase.FOCUS, s.phase)
        assertFalse(s.running)
        assertFalse(s.overtime)
    }

    @Test
    fun `postpone returns to focus for the postpone length, then the same break returns`() {
        val e = engine()
        e.start()
        now += 25 * 60 * 1000L
        e.tick() // short break running
        e.postpone()
        var s = e.snapshot()
        assertEquals(TimerEngine.Phase.FOCUS, s.phase)
        assertTrue(s.running)
        assertEquals(1, s.focusIndex)
        assertEquals(5 * 60 * 1000L, s.remainingMs)
        now += 5 * 60 * 1000L
        val events = e.tick() // postpone focus ends -> the break comes back
        assertEquals(listOf(TimerEngine.Event.BREAK_STARTED), events)
        s = e.snapshot()
        assertEquals(TimerEngine.Phase.SHORT_BREAK, s.phase)
        assertEquals(5 * 60 * 1000L, s.remainingMs)
    }

    @Test
    fun `postpone during overtime also returns to focus`() {
        val e = engine()
        e.start()
        now += 25 * 60 * 1000L; e.tick()
        now += 5 * 60 * 1000L; e.tick() // overtime
        e.postpone()
        val s = e.snapshot()
        assertEquals(TimerEngine.Phase.FOCUS, s.phase)
        assertTrue(s.running)
        assertFalse(s.overtime)
    }

    @Test
    fun `postpone is a no-op during focus`() {
        val e = engine()
        e.start()
        val before = e.snapshot()
        e.postpone()
        val after = e.snapshot()
        assertEquals(before.phase, after.phase)
        assertEquals(before.remainingMs, after.remainingMs)
    }

    @Test
    fun `pause freezes the countdown and start resumes it`() {
        val e = engine()
        e.start()
        now += 60_000
        e.pause()
        val frozen = e.snapshot()
        assertTrue(frozen.paused)
        assertFalse(frozen.running)
        assertEquals((25 * 60 - 60) * 1000L, frozen.remainingMs)
        now += 10 * 60 * 1000L // time passes while paused
        assertEquals(frozen.remainingMs, e.snapshot().remainingMs)
        e.start()
        now += 60_000
        assertEquals((25 * 60 - 120) * 1000L, e.snapshot().remainingMs)
    }

    @Test
    fun `stop resets to idle`() {
        val e = engine()
        e.start()
        e.skip()
        e.stop()
        val s = e.snapshot()
        assertEquals(TimerEngine.Phase.IDLE, s.phase)
        assertFalse(s.running)
    }

    @Test
    fun `unlock starts focus only from idle`() {
        val e = engine()
        assertTrue(e.onScreenUnlocked())
        assertEquals(TimerEngine.Phase.FOCUS, e.snapshot().phase)
        // A running session is left alone.
        assertFalse(e.onScreenUnlocked())
    }

    @Test
    fun `unlock finishes an overrunning break but not a paused session`() {
        val e = engine()
        e.start()
        now += 25 * 60 * 1000L; e.tick()
        now += 5 * 60 * 1000L; e.tick() // overtime
        assertTrue(e.onScreenUnlocked())
        assertEquals(TimerEngine.Phase.FOCUS, e.snapshot().phase)
        assertTrue(e.snapshot().running)

        e.pause()
        assertFalse(e.onScreenUnlocked())
        assertTrue(e.snapshot().paused)
    }

    @Test
    fun `tick is a no-op before the deadline and while idle`() {
        val e = engine()
        assertEquals(emptyList<TimerEngine.Event>(), e.tick())
        e.start()
        now += 1000
        assertEquals(emptyList<TimerEngine.Event>(), e.tick())
    }

    @Test
    fun `a process restart restores the deadline, not the time left`() {
        val e = engine()
        e.start()
        now += 60_000
        val saved = e.serialize()

        // The process is dead for the next 2 minutes of wall time.
        now += 2 * 60 * 1000L

        val revived = TimerEngine { now }
        revived.restore(saved)
        assertEquals((25 * 60 - 180) * 1000L, revived.snapshot().remainingMs)
        assertTrue(revived.snapshot().running)
    }

    @Test
    fun `a session whose deadline passed while dead transitions on the first tick`() {
        val e = engine()
        e.start()
        val saved = e.serialize()
        now += 30 * 60 * 1000L // dead past the end of the 25 minute focus

        val revived = TimerEngine { now }
        revived.restore(saved)
        val events = revived.tick()
        assertEquals(listOf(TimerEngine.Event.BREAK_STARTED), events)
        // The break starts from the moment it was noticed, like the plasmoid.
        assertEquals(5 * 60 * 1000L, revived.snapshot().remainingMs)
    }

    @Test
    fun `overtime survives a restart still counting negative`() {
        val e = engine()
        e.start()
        now += 25 * 60 * 1000L; e.tick()
        now += 5 * 60 * 1000L; e.tick() // overtime begins
        now += 60_000
        val saved = e.serialize()

        now += 60_000 // dead for another minute of overrun
        val revived = TimerEngine { now }
        revived.restore(saved)
        val s = revived.snapshot()
        assertTrue(s.overtime)
        assertEquals(-120_000L, s.remainingMs)
    }

    @Test
    fun `break end with autoStartFocusAfterBreak starts next focus`() {
        val e = engine()
        e.autoStartFocusAfterBreak = true
        e.start()
        now += 25 * 60 * 1000L
        e.tick() // break starts
        now += 5 * 60 * 1000L
        val events = e.tick()
        assertEquals(listOf(TimerEngine.Event.FOCUS_STARTED), events)
        val s = e.snapshot()
        assertEquals(TimerEngine.Phase.FOCUS, s.phase)
        assertTrue(s.running)
        assertFalse(s.overtime)
        assertEquals(2, s.focusIndex)
        assertEquals(25 * 60 * 1000L, s.remainingMs)
    }

    @Test
    fun `long break end with autoStartFocusAfterBreak wraps to next cycle`() {
        val e = engine()
        e.autoStartFocusAfterBreak = true
        e.start()
        repeat(3) {
            now += 25 * 60 * 1000L; e.tick() // break starts
            now += 5 * 60 * 1000L; e.tick() // focus starts automatically
        }
        assertEquals(4, e.snapshot().focusIndex)
        now += 25 * 60 * 1000L
        e.tick() // long break starts
        now += 20 * 60 * 1000L
        val events = e.tick()
        assertEquals(listOf(TimerEngine.Event.FOCUS_STARTED), events)
        val s = e.snapshot()
        assertEquals(TimerEngine.Phase.FOCUS, s.phase)
        assertTrue(s.running)
        assertEquals(1, s.focusIndex)
        assertEquals(25 * 60 * 1000L, s.remainingMs)
    }

    @Test
    fun `autoStartFocusAfterBreak disabled keeps break overtime behavior`() {
        val e = engine()
        e.autoStartFocusAfterBreak = false
        e.start()
        now += 25 * 60 * 1000L; e.tick()
        now += 5 * 60 * 1000L
        val events = e.tick()
        assertEquals(listOf(TimerEngine.Event.BREAK_OVERTIME_STARTED), events)
        val s = e.snapshot()
        assertTrue(s.overtime)
        assertFalse(s.running)
        assertEquals(TimerEngine.Phase.SHORT_BREAK, s.phase)
    }

    @Test
    fun `restore preserves high stateVal when sessionsPerCycle is configured`() {
        val saved = TimerEngine.Persisted(
            stateVal = 16,
            deadlineMs = now + 20 * 60 * 1000L,
            running = true,
            paused = false,
            overtime = false,
            pausedRemainingMs = 0L,
        )
        val revived = TimerEngine { now }.apply {
            durations = TimerEngine.Durations(sessionsPerCycle = 8)
            restore(saved)
        }
        assertEquals(TimerEngine.Phase.LONG_BREAK, revived.snapshot().phase)
        assertEquals(8, revived.snapshot().focusIndex)
        assertEquals(16, revived.serialize().stateVal)
    }

    @Test
    fun `formatCounter renders countdown and overtime`() {
        assertEquals("25:00", formatCounter(25 * 60))
        assertEquals("00:05", formatCounter(5))
        assertEquals("65:00", formatCounter(65 * 60))
        assertEquals("-01:30", formatCounter(-90))
    }
}
