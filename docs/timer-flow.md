# Timer Flow

This document describes the `TimerEngine` state machine and the
`FokusService` paths that drive it. `BREAK` means either a short break or the
long break after the last focus session in a cycle.

## Engine State Machine

```mermaid
flowchart TD
    IDLE["IDLE\nstateVal = 0\nrunning = false"]
    FOCUS_RUNNING["FOCUS running\ndeadline is active"]
    FOCUS_PAUSED["FOCUS paused\npausedRemainingMs is stored"]
    FOCUS_QUEUED["FOCUS queued\nrunning = false\ncreated by skip with auto-start off"]
    BREAK_RUNNING["BREAK running\nshort or long break\ndeadline is active"]
    BREAK_PAUSED["BREAK paused\npausedRemainingMs is stored"]
    BREAK_OVERTIME["BREAK overtime\nrunning = false\nremaining time is negative"]
    POSTPONED_FOCUS["FOCUS after postpone\npostpone deadline is active"]

    IDLE -->|start| FOCUS_RUNNING
    IDLE -->|unlock + autoStartOnUnlock| FOCUS_RUNNING
    IDLE -->|pause, skip, postpone, stop, tick| IDLE

    FOCUS_RUNNING -->|tick before deadline| FOCUS_RUNNING
    FOCUS_RUNNING -->|deadline tick / BREAK_STARTED| BREAK_RUNNING
    FOCUS_RUNNING -->|pause| FOCUS_PAUSED
    FOCUS_RUNNING -->|skip| BREAK_RUNNING
    FOCUS_RUNNING -->|stop| IDLE
    FOCUS_RUNNING -->|start| FOCUS_RUNNING

    FOCUS_PAUSED -->|start / resume remaining time| FOCUS_RUNNING
    FOCUS_PAUSED -->|skip| BREAK_RUNNING
    FOCUS_PAUSED -->|stop| IDLE
    FOCUS_PAUSED -->|pause, postpone, unlock, tick| FOCUS_PAUSED

    FOCUS_QUEUED -->|start| FOCUS_RUNNING
    FOCUS_QUEUED -->|skip| BREAK_RUNNING
    FOCUS_QUEUED -->|stop| IDLE
    FOCUS_QUEUED -->|pause, postpone, unlock, tick| FOCUS_QUEUED

    BREAK_RUNNING -->|tick before deadline| BREAK_RUNNING
    BREAK_RUNNING -->|deadline tick + auto-start on / FOCUS_STARTED| FOCUS_RUNNING
    BREAK_RUNNING -->|deadline tick + auto-start off / BREAK_OVERTIME_STARTED| BREAK_OVERTIME
    BREAK_RUNNING -->|pause| BREAK_PAUSED
    BREAK_RUNNING -->|skip + auto-start on| FOCUS_RUNNING
    BREAK_RUNNING -->|skip + auto-start off| FOCUS_QUEUED
    BREAK_RUNNING -->|postpone| POSTPONED_FOCUS
    BREAK_RUNNING -->|stop| IDLE
    BREAK_RUNNING -->|start| BREAK_RUNNING

    BREAK_PAUSED -->|start / resume remaining time| BREAK_RUNNING
    BREAK_PAUSED -->|skip + auto-start on| FOCUS_RUNNING
    BREAK_PAUSED -->|skip + auto-start off| FOCUS_QUEUED
    BREAK_PAUSED -->|postpone| POSTPONED_FOCUS
    BREAK_PAUSED -->|stop| IDLE
    BREAK_PAUSED -->|pause, unlock, tick| BREAK_PAUSED

    BREAK_OVERTIME -->|start| FOCUS_RUNNING
    BREAK_OVERTIME -->|unlock + autoStartOnUnlock| FOCUS_RUNNING
    BREAK_OVERTIME -->|skip + auto-start on| FOCUS_RUNNING
    BREAK_OVERTIME -->|skip + auto-start off| FOCUS_QUEUED
    BREAK_OVERTIME -->|postpone| POSTPONED_FOCUS
    BREAK_OVERTIME -->|stop| IDLE
    BREAK_OVERTIME -->|pause, tick| BREAK_OVERTIME

    POSTPONED_FOCUS -->|tick at postpone deadline / BREAK_STARTED| BREAK_RUNNING
    POSTPONED_FOCUS -->|pause| FOCUS_PAUSED
    POSTPONED_FOCUS -->|skip| BREAK_RUNNING
    POSTPONED_FOCUS -->|stop| IDLE
    POSTPONED_FOCUS -->|start| POSTPONED_FOCUS
```

### Transition Rules

| Case | Result |
| --- | --- |
| Start from idle | Starts focus session 1. |
| Focus deadline expires | Starts the next break automatically. |
| Break deadline expires with auto-start enabled | Starts the next focus automatically and emits `FOCUS_STARTED`. |
| Break deadline expires with auto-start disabled | Stops the timer in overtime and emits `BREAK_OVERTIME_STARTED`. |
| Skip during focus | Starts the current focus session's break immediately. |
| Skip during a break with auto-start enabled | Starts the next focus immediately. |
| Skip during a break with auto-start disabled | Cues the next focus without starting it. `start()` is required. |
| Start during overtime | Advances to and starts the next focus. |
| Postpone during a break or overtime | Runs the preceding focus for the postpone duration, then returns to the same break. |
| Pause while running | Stores the remaining time and stops countdown; `start()` resumes it. |
| Stop from any active, paused, queued, or overtime state | Resets to idle and discards the cycle position. |
| Screen unlock with auto-start enabled | Starts focus only from idle or overtime. Running, paused, and queued states are unchanged. |

`nextState()` advances focus to its break and break to its next focus. After
the long break, the cycle wraps to focus session 1. A focus has an odd
`stateVal`; a break has an even `stateVal`.

## Service, UI, and Persistence Flow

```mermaid
flowchart LR
    CREATE["FokusService.onCreate"] --> SETTINGS["Load settings\napply durations and auto-start flags"]
    SETTINGS --> RESTORE["Restore saved session"]
    RESTORE -->|no state, reboot, or older than 1 hour| CLEAR["Clear saved state"]
    RESTORE -->|valid state| READY["Session ready"]
    CLEAR --> READY
    READY --> TICK["250 ms tick loop"]
    READY --> SETTINGS_FLOW["Observe settings changes"]

    UI["Timer screen"] --> ACTION["start / pause / skip / postpone / stop"]
    OVERLAY["Break overlay"] --> ACTION
    NOTIFICATION["Notification action"] --> ACTION
    ACTION --> SERVICE_ACTION["FokusService dispatches engine command"]
    SERVICE_ACTION --> STATE_CHANGED["onStateChanged"]

    TICK --> ENGINE_TICK["engine.tick()"]
    ENGINE_TICK -->|no event| PUBLISH["publish snapshot\nupdate ongoing notification"]
    ENGINE_TICK -->|BREAK_STARTED, BREAK_OVERTIME_STARTED, or FOCUS_STARTED| EVENT["Post event notification"]
    EVENT --> STATE_CHANGED

    UNLOCK["ACTION_USER_PRESENT"] --> UNLOCK_SETTING{"autoStartOnUnlock?"}
    UNLOCK_SETTING -->|no| NO_UNLOCK["No action"]
    UNLOCK_SETTING -->|yes| ENGINE_UNLOCK["engine.onScreenUnlocked()"]
    ENGINE_UNLOCK -->|false: running, paused, or queued| NO_UNLOCK
    ENGINE_UNLOCK -->|true: idle or overtime| STATE_CHANGED

    STATE_CHANGED --> PUBLISH
    STATE_CHANGED --> OVERLAY_RULE{"Break and running or overtime?"}
    OVERLAY_RULE -->|yes| SHOW_OVERLAY["Show break overlay"]
    OVERLAY_RULE -->|no| HIDE_OVERLAY["Hide break overlay"]
    STATE_CHANGED --> SAVE["Persist state, wall-clock deadline, and flags"]

    SETTINGS_FLOW --> APPLY["Apply durations and auto-start flags"]
    APPLY --> PUBLISH
```

The service persists the deadline rather than only the remaining time, so
elapsed wall-clock time continues to count while the process is stopped. The
tick loop starts only after settings and session restoration are complete.
