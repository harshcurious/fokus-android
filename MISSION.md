# Mission: Read and understand Android app code

## Why
The user wants to be able to read, understand, and maintain existing Android codebases. The concrete goal: pick up any unfamiliar Android app — starting with this fokus-android Pomodoro repo — and build an accurate mental model of how it works: what each layer does, how data flows, and where to look when tracing a behavior. They are not building apps from scratch; they are becoming fluent in reading them.

## Success looks like
- Can trace a single user action end-to-end (e.g. a button tap) through the UI, ViewModel, service, engine, and back into the rendered screen, naming the files involved.
- Can explain what `AndroidManifest.xml` declares (components, permissions, intent filters) and why each declaration exists in this app.
- Can read a Jetpack Compose screen and explain state, events, and recomposition.
- Can identify the architecture of an unfamiliar repo (layers, state holders, single source of truth) within minutes of opening it.
- Knows the platform vocabulary — Activity, Service, BroadcastReceiver, Intent, PendingIntent, permission, lifecycle, foreground service — well enough to navigate official docs without being lost.

## Constraints
- Background: comfortable in another programming language, but new to Kotlin and the Android platform.
- Practice mode: read-and-understand first. Lessons walk through the fokus-android repo, build mental models, and reinforce with quizzes. No code-writing exercises in early lessons.
- One repo as the learning surface: fokus-android.
- Environment: Linux desktop; may or may not have an emulator/device available — lessons should not depend on running the app.

## Out of scope
- Building and publishing apps from scratch.
- UI design, custom animation, and pixel-perfect styling work.
- Performance tuning, R8/ProGuard optimization, and release signing details (except as they appear in passing).
- Non-Android topics (server backends, other mobile platforms).
