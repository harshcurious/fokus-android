# Fokus — Agent Repository Guide

A compact guide for future coding agents working on the Fokus Android app.

## Architecture

The app is a single-module Android project (`:app`) using Kotlin, Jetpack
Compose, and Navigation 3.

| Layer | Responsibility | Key file |
| --- | --- | --- |
| **State machine** | Pure Pomodoro logic with no Android deps | `app/src/main/java/dev/fokus/app/timer/TimerEngine.kt` |
| **Service** | Foreground service that owns the engine, notification, overlay, unlock receiver, and persistence | `app/src/main/java/dev/fokus/app/service/FokusService.kt` |
| **Overlay** | Full-screen break window drawn via `WindowManager` | `app/src/main/java/dev/fokus/app/overlay/OverlayController.kt` |
| **Settings** | DataStore-backed user preferences | `app/src/main/java/dev/fokus/app/settings/SettingsRepository.kt` |
| **UI** | Compose screens, shared dial, ViewModel binding | `app/src/main/java/dev/fokus/app/ui/` |
| **Boot** | Restarts the foreground service after reboot | `app/src/main/java/dev/fokus/app/service/BootReceiver.kt` |

`MainActivity.kt` is edge-to-edge and hosts `MainNavigation()`, which uses
Navigation 3 (`androidx.navigation3`) to switch between `TimerScreen` and
`SettingsScreen`.

## Timer invariants and state semantics

See `TimerEngine.kt` for the source of truth.

- **Phases:** `IDLE`, `FOCUS`, `SHORT_BREAK`, `LONG_BREAK`.
- **Internal `stateVal`:** `0` = idle; `1..2*sessions` alternates focus (odd) and
  break (even). `2*sessions` is the long break.
- **Running / paused / overtime:**
  - `running == true` — countdown is active.
  - `paused == true` — countdown was frozen by `pause()`; `start()` resumes.
  - `overtime == true` — a break ended without auto-start; `remainingMs` is
    negative and still falling.
- **Deadlines:** all durations are derived from a wall-clock deadline. Screen
  locks or process death do not change when a session ends.
- **Transitions:**
  - Focus end → break starts automatically (`Event.BREAK_STARTED`).
  - Break end, `autoStartFocusAfterBreak == false` → stops and enters overtime
    (`Event.BREAK_OVERTIME_STARTED`).
  - Break end, `autoStartFocusAfterBreak == true` → starts next focus
    (`Event.FOCUS_STARTED`).
- **Commands:**
  - `start()` — start idle, resume paused, or finish overtime and begin next focus.
  - `pause()` — freeze running session.
  - `skip()` — advance to next session. Skipping a running focus session starts
    its break immediately; skipping a break starts the next focus when
    `autoStartFocusAfterBreak` is enabled, otherwise it cues the next focus
    session without starting it.
  - `postpone()` — only during a break/overtime; returns to focus for
    `postponeMinutes`, then the same break returns.
  - `stop()` — resets to idle.
  - `onScreenUnlocked()` — starts focus only if idle or in overtime; no-op while
    running or paused.

## Persistence, service, overlay, and settings constraints

### Persistence

- `FokusService` stores the engine's serialized state in a private
  `SharedPreferences` named `"session"`.
- It stores the wall-clock deadline, not the remaining time, so elapsed time
  while dead is still counted.
- `STALE_AFTER_MS` is one hour; sessions older than that are discarded on
  restore.
- Reboot is detected by comparing elapsed realtime; a session saved before a
  reboot is discarded.
- `restoreSession()` must run **after** settings are loaded because it clamps
  `stateVal` against `sessionsPerCycle`.

### Service

- `FokusService` calls `startForegroundWithNotification()` immediately in
  `onCreate`, before launching coroutines.
- `onStartCommand` handles `START`, `PAUSE`, `SKIP`, `POSTPONE`, `STOP`, and
  sticky restart (`null` action).
- `START_STICKY` makes the service restartable after the process is killed.

### Overlay

- `OverlayController` requires `Settings.canDrawOverlays(context)`.
- Without the permission the overlay is **silently skipped**; notifications and
  the app UI still work.
- It creates its own `LifecycleOwner`, `ViewModelStoreOwner`, and
  `SavedStateRegistryOwner` so a `ComposeView` can live outside an activity.

### Settings

- Backed by DataStore (`settings` preferences file).
- `SettingsRepository.settings` emits a flow; `FokusService` collects it and
  applies changes to the engine live.
- Defaults: `autoStartOnUnlock = true`, `autoStartFocusAfterBreak = false`.
- Valid ranges for UI sliders are defined in `SettingsScreen.kt` (e.g., focus
  `5..120` min, sessions per cycle `1..8`).

## Verification commands

Run from the repository root:

```bash
# Unit tests
./gradlew :app:testDebugUnitTest

# Debug build
./gradlew :app:assembleDebug

# Both
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Unit tests are in `app/src/test/java/dev/fokus/app/timer/TimerEngineTest.kt`
and `app/src/test/java/dev/fokus/app/settings/AppSettingsTest.kt`.

## Code conventions

- Kotlin 2.3.20, Java 17 toolchain, `compileSdk = 36`, `minSdk = 24`.
- Compose-only UI; no `res/layout/*.xml` files. XML resources include the
  manifest, themes/strings, launcher and notification icons, and backup/data
  extraction rules under `res/xml/`.
- Jetpack Navigation 3 for routing (`Navigation.kt`).
- Use `StateFlow` for state observable from Compose; `collectAsState()` in
  screens.
- Keep `TimerEngine` free of Android dependencies so it stays JVM-testable.
- Prefer string resources in `app/src/main/res/values/strings.xml`.

## Safety and scope rules

- Do not change Pomodoro semantics without updating or adding tests in
  `TimerEngineTest.kt`.
- Do not add dependencies unless explicitly approved.
- Keep `TimerEngine` pure; do not leak `Context`, Android framework classes, or
  coroutines into it.
- Any change to `FokusService` foreground behavior must keep the service
  foreground-compliant for API 34+ (`FOREGROUND_SERVICE_TYPE_SPECIAL_USE`).
- New permissions must be declared in `app/src/main/AndroidManifest.xml` and,
  if runtime, requested in the UI.
- Avoid absolute paths, device-specific instructions, or commit/push guidance in
  documentation.
