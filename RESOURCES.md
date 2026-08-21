# Android App Development Resources

Resources for learning to read and understand Android codebases. The primary learning surface is the fokus-android repo; these sources are the trusted references for the concepts it uses.

## Knowledge

- [App fundamentals — Android Developers](https://developer.android.com/guide/components/fundamentals)
  The core mental model: the four app components (Activity, Service, BroadcastReceiver, ContentProvider), the manifest, and how intents activate components. Use for: any lesson about app anatomy or the manifest. (Verified current.)
- [Guide to app architecture — Android Developers](https://developer.android.com/topic/architecture)
  The official architecture doctrine: separation of concerns, unidirectional data flow, UI layer (ViewModel) vs data layer, single source of truth. Use for: understanding WHY fokus-android is shaped the way it is. (Verified current.)
- [App manifest overview — Android Developers](https://developer.android.com/guide/topics/manifest/manifest-intro)
  Reference for everything `AndroidManifest.xml` declares: components, intent filters, permissions, device compatibility. Use for: manifest walkthrough lessons.
- [Kotlin documentation — kotlinlang.org](https://kotlinlang.org/docs/home.html)
  The language reference. Start with "Basic syntax" and "Coroutines"; the app is written in idiomatic Kotlin (data classes, sealed-ish enums, lambdas, property syntax).
- [Coroutines guide — Kotlin](https://kotlinlang.org/docs/coroutines-guide.html)
  Coroutines + `Flow`/`StateFlow` underpin the whole app's concurrency. Use for: lessons on the service's tick loop and `StateFlow` propagation.
- [Thinking in Compose — Android Developers](https://developer.android.com/develop/ui/compose/mental-model)
  The fundamental Compose paradigm: recomposition, state hoisting, unidirectional UI data flow. Read before the Compose UI lessons.
- [State and Jetpack Compose — Android Developers](https://developer.android.com/develop/ui/compose/state)
  How state flows through a Compose UI and drives recomposition. Use for: reading `TimerScreen.kt` and `TimerFace.kt`.
- [ViewModel overview — Android Developers](https://developer.android.com/topic/libraries/architecture/viewmodel)
  What a ViewModel is and why it survives configuration changes. Use for: `TimerViewModel.kt`.
- [Foreground services — Android Developers](https://developer.android.com/develop/background-work/services/fgservice)
  The rules and pattern for foreground services, notification types, and background-start limits. Use for: `FokusService.kt`.
- [DataStore — Android Developers](https://developer.android.com/topic/libraries/architecture/datastore)
  The modern key-value persistence API (replaces `SharedPreferences`). Use for: `SettingsRepository.kt`.
- [Local tests / unit tests — Android Developers](https://developer.android.com/training/testing/local-tests)
  JVM-run tests. Use for: understanding why `TimerEngine` has no Android deps and how `TimerEngineTest.kt` works.

## Wisdom (Communities)

- [r/androiddev](https://www.reddit.com/r/androiddev/)
  Large, working-developer subreddit. Good for: seeing how practitioners explain real architecture decisions, and asking "is this pattern idiomatic?" questions.
- [Kotlin Slack](https://kotlinlang.slack.com/)
  Official Kotlin Slack; has #android and #compose channels. Use for: quick clarifications on language/Compose behavior.
- Local: Android Developers Discord / GDG meetups in your area
  Use for: real-time explanations and code-reading practice with other developers. (Opt-in — ask the user before proposing.)
- [Android Developers blog](https://android-developers.googleblog.com/)
  Announcements and deep dives from the platform team. Use for: staying current, especially on new APIs like Navigation 3.

## Gaps

- **Navigation 3** (used by this app) is new enough that official docs are still consolidating; there is no single definitive page yet. Fall back to the repo itself, the `navigation3` sample code, and `r/androiddev` discussion.
- **No resource yet for "reading unfamiliar codebases" as a skill.** The general skill is covered by lessons in this workspace; flag if a good external article turns up.
