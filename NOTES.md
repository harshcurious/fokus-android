# Notes

## Learner profile
- Goal: understand and maintain existing Android code (not build from scratch). See MISSION.md.
- Prior knowledge: comfortable in another language; new to Kotlin + Android platform.
- Practice mode: **read + understand first**. Prefer mental models, guided walks through real code, and quizzes. No coding exercises for now.

## Teaching preferences (observed / stated)
- Wants to learn by walking through THIS repo (fokus-android), a Compose + foreground-service Pomodoro app.
- Early lessons should ground every new concept in a concrete `file:line` in this repo.
- Quizzes should be answerable from the lesson + repo; keep answer options uniform in length.

## Repo facts worth remembering
- Pure domain core: `TimerEngine` (no Android deps, injected clock) → unit tests run on JVM.
- Data flow: UI (Compose) → `TimerViewModel` → bound `FokusService` → `TimerEngine`; state flows back as `StateFlow<Snapshot>`.
- Settings: Preferences DataStore via `SettingsRepository`.
- Overlay: `OverlayController` (WindowManager + ComposeView with a hand-rolled LifecycleOwner).
- Navigation 3 (`NavDisplay`, `NavKey`, `entryProvider`) — modern, still-new API.
- Persistence trick: store wall-clock `deadlineMs`, not time-left, so countdown survives process death; discard sessions older than 1h or pre-reboot.

## Open questions / ZPD signals
- (unset) Has the user ever built a small Android project before? Assumed no.
