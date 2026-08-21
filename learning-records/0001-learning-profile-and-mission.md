# Learning profile and mission established (session 1)

The learner wants to **read and understand existing Android codebases** — not build apps from scratch — using the fokus-android Pomodoro repo as the learning surface. They are comfortable in another programming language but new to Kotlin and the Android platform, and they prefer a read-and-understand mode: mental models, guided walks through real code, and quizzes, with no code-writing exercises yet.

**Evidence:** Direct answers during the intake interview (mission = "Understand existing code"; background = "Know another language"; practice style = "Read + understand first"). No Android-specific prior knowledge was claimed, so lessons must not assume familiarity with Activity/Service/Compose/Flow concepts.

**Implications for future sessions:**
- Every new concept should be introduced bottom-up and grounded in a concrete `file:line` of this repo (the learner is learning to navigate code, not to write it).
- The first lesson (0001) established the "map": manifest components → layered UI/ViewModel/Service/Engine architecture → unidirectional data flow (events down, state up). Subsequent lessons can assume this map and build on it.
- Do not propose coding/run-the-app exercises until the learner signals a readiness to practice differently; poll them when the read-only lessons start feeling too passive.
- Primary source anchored to https://developer.android.com/guide/components/fundamentals (verified current).