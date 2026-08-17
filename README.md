# Fokus

Fokus is an Android Pomodoro timer inspired by the KDE Fokus plasmoid. It runs a
configurable focus/break cycle in a foreground service, shows a full-screen break
overlay, and can automatically start focus when you unlock your phone.

## What it does

- **Pomodoro cycle** — alternating focus sessions and breaks. The break after the
  last focus session of a cycle is the long break; all other breaks are short.
- **Auto-start breaks** — when a focus session ends, the next break starts
  automatically.
- **Break overtime (default)** — when a break ends, the timer stops and keeps
  counting into the negative until you press **Start focus**. This is the default
  behavior.
- **Auto-start focus after break (opt-in)** — in Settings, enable
  *Auto-start focus after break* to have the next focus session begin
  automatically when a break ends.
- **Postpone** — during a break, abort it and go back to focus for the
  configured postpone length. When that time is up, the same break returns.
- **Skip** — jump to the next session. Skipping a running focus session starts
  its break immediately; skipping a break cues the next focus session without
  starting it.
- **Pause / resume** — pause a running session; resume continues from where it
  left off.
- **Stop** — reset the cycle to idle.
- **Screen-unlock auto-start** — by default, unlocking the screen starts a focus
  session when the timer is idle or in break overtime. This can be disabled in
  Settings.
- **Break overlay** — during breaks, a full-screen dim overlay is shown over any
  app. It requires the *Display over other apps* permission; if denied, the
  overlay is silently skipped and notifications still fire.
- **Notifications** — a persistent timer notification with the remaining time and
  quick actions, plus heads-up notifications when sessions change.
- **Survives process death** — the session deadline is stored as wall-clock
  time, so the countdown keeps running if the app process is killed. Sessions
  saved before a reboot, or older than one hour, are discarded. The service
  restarts automatically after boot so the screen-unlock auto-start keeps
  working.

## Defaults

| Setting | Default |
| --- | --- |
| Focus length | 25 min |
| Short break | 5 min |
| Long break | 20 min |
| Sessions per cycle | 4 |
| Postpone length | 5 min |
| Start focus on screen unlock | **On** |
| Auto-start focus after break | **Off** |

## Requirements

- JDK 17
- Android SDK Platform 36
- An Android device or emulator running Android 7.0 (API 24) or higher

## Build

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Run unit tests

```bash
./gradlew :app:testDebugUnitTest
```

Tests live in `app/src/test/java/dev/fokus/app/` and cover the pure timer state
machine and settings defaults/mapping.

## Install on a physical device

1. Enable **USB debugging** on the device.
2. Build the debug APK.
3. Install it with `adb`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

The following permissions are declared in `app/src/main/AndroidManifest.xml`:

- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` — keeps the countdown
  running reliably.
- `SYSTEM_ALERT_WINDOW` — required for the break overlay.
- `POST_NOTIFICATIONS` — required on Android 13+ for timer and event
  notifications. The app requests this at runtime.
- `RECEIVE_BOOT_COMPLETED` — restarts the service after a reboot.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — lets the user exclude Fokus from
  battery optimizations.

The overlay permission is requested on the main screen; battery-optimization
exemption can be requested from Settings.

## Battery optimization

For reliable countdowns, notifications, and the break overlay, exclude Fokus from
battery optimization. Open **Settings** in the app and tap **Exclude**.

## Project structure

```
app/src/main/java/dev/fokus/app/
├── MainActivity.kt                # Edge-to-edge Compose entry point
├── Navigation.kt                  # Navigation3 routes (timer → settings)
├── NavigationKeys.kt              # Route key objects for Navigation 3
├── service/
│   ├── FokusService.kt            # Foreground service, engine, overlay, notifications
│   └── BootReceiver.kt            # Restarts the service after boot
├── timer/
│   ├── TimerEngine.kt             # Pure Pomodoro state machine (unit-testable)
│   └── TimeFormat.kt              # Countdown formatting
├── overlay/
│   └── OverlayController.kt       # Full-screen break overlay window
├── settings/
│   └── SettingsRepository.kt      # DataStore-backed user settings
├── theme/
│   ├── Color.kt                   # Theme colors
│   ├── Theme.kt                   # Fokus Material3 theme
│   └── Type.kt                    # Typography
└── ui/
    ├── TimerFace.kt               # Shared countdown dial
    ├── timer/
    │   ├── TimerScreen.kt         # Main timer screen
    │   └── TimerViewModel.kt      # Binds the UI to FokusService
    └── settings/
        └── SettingsScreen.kt      # Settings UI
```

Configuration is in `gradle/libs.versions.toml` and `app/build.gradle.kts`.
