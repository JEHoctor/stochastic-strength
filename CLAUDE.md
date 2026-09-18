# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew :app:assembleDebug

# Unit tests (runs on JVM, no device needed). Most live in :shared; the ui/ ones in :app.
./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest

# Run a single unit test class
./gradlew :shared:testAndroidHostTest --tests "io.github.fowles.stochastic_strength.domain.WorkoutPlannerTest"

# Prove shared code is platform-neutral (compiles commonMain against the common stdlib only; fast, Linux-friendly)
./gradlew :shared:compileCommonMainKotlinMetadata

# Instrumented tests (requires connected device/emulator; CI runs them on a GitHub-hosted emulator)
./gradlew :app:connectedAndroidTest

# Lint
./gradlew :app:lint
```

The iOS side cannot be built on Linux; `.github/workflows/ios.yml` links the shared module as an
iOS simulator framework on a macOS runner (`:shared:linkDebugFrameworkIosSimulatorArm64`).

## Architecture

Two Gradle modules. `app/` is the Android application (Jetpack Compose with Material3, the
Android-only services, and the instrumented tests). `shared/` is a Kotlin Multiplatform library
(`android`, `iosArm64`, `iosSimulatorArm64`) holding the platform-neutral `data/` and `domain/`
layers in `commonMain`; it is the seed of the iOS port (see
`docs/superpowers/specs/2026-09-11-kmp-shared-module-design.md`, including its "As built" section).
Under AGP 9 the multiplatform plugin cannot share a module with `com.android.application`, which is
why the split exists. Nothing in `shared` may import `android.*` or `java.*`; the JVM-only calls the
moved code used (`org.json`, `String.format`, `System.currentTimeMillis`, `java.util.Map.merge`,
Room's Android `withTransaction`) are provided by small shims under `shared/src/commonMain/.../{json,text,time,collections,data}`.

- **Package**: `io.github.fowles.stochastic_strength`
- **Min SDK**: 33 (Android 13), **Target SDK**: 36
- **UI**: Jetpack Compose — all UI is written in Kotlin composables, no XML layouts
- **Theme**: `ui/theme/` — Material3 theming
- **Entry point**: `MainActivity` sets content via `setContent { StochasticStrengthTheme { ... } }`

Unit tests run on the JVM: `shared/src/androidHostTest/` for `data/`/`domain/` (JUnit4, may use
`internal` symbols) and `app/src/test/` for `ui/`. Instrumented tests live in `app/src/androidTest/`
and require a device or emulator; they read Room's schema JSON from `shared/schemas/` via `app`'s
androidTest assets. New code under `domain/` or `data/` goes in `shared` — a CI guard fails if any
lands in `app/`.

### Layers

```
shared/src/commonMain
  data/           Room entities, DAOs, AppDatabase, type converters, seed data (ExerciseLibrary)
  domain/         Pure business logic: WorkoutPlanner, ProgressionEngine, WorkoutRepository, coefficient heuristics
shared/src/androidMain
  data/           AppDatabase.getInstance/reset (Context-dependent construction)
app/src/main
  domain/strava/  Strava OAuth + JSON export (Android-only until phase 3)
  domain/history/HistoryRows.kt  (java.time in its API; moves with the UI in phase 2)
  ui/             Composable screens + ViewModels; one sub-package per screen (home/, workout/, history/, debug/, etc.)
  ui/components/  Shared composables (SectionHeader, StrengthGrid, LoadingBox, formatDateTime)
  location/       GPS lookup and KnownLocation resolution
  notification/   Workout foreground notification service
```

Every path keeps the package prefix `io.github.fowles.stochastic_strength`, so the module split
does not change imports.

There is no DI framework. `StochasticStrengthApp` (the `Application` class) owns `AppDatabase`, `workoutRepository`, `stravaExporter`, and `workoutSessionBus` as singletons. ViewModels obtain them via `application as StochasticStrengthApp`.

### Navigation

`AppNavigation.kt` wires the app's screens with string-based routes. The primary flow is `home → workout → summary/{sessionId} → home`; secondary screens (history, locations, exercises, about, debug detail screens) are reachable from home.

### Workout state machine

`WorkoutState` is a sealed interface with five states. State is owned by `WorkoutSessionController` (in `ui/workout/`); `WorkoutViewModel` is a thin delegation layer.

```
Loading → PlanPreview → ActiveSet ⇄ Resting → Done
                                   ↑ (undo)
```

- **PlanPreview**: user reviews/edits the generated exercise list before starting
- **ActiveSet**: user performs a set (may show warmup sets first)
- **Resting**: 90-second countdown after each set; auto-advances or can be skipped/undone
- **Done**: triggers `replayDerivedState` then navigates to summary

### Progression system (belief stack + policy layer)

Progression is a **per-exercise running estimate in log-space**. Each loaded exercise carries a `Belief` — its `mu` (the current best guess at ln(fresh 1RM, kg)), `sigma2` (how unsure we are about that guess, in ln-units²), and `updatedAt` — held in `domain/belief/`. These are estimates, not measurements: the whole stack tracks a best guess and its uncertainty and updates both as evidence arrives. The estimate map is the only durable progression state; the per-muscle display levels (`MuscleGroupStrength`), `baseline_history`, and `coefficient_history` are derived projections held in the in-memory `DerivedStateStore` (not Room entities). `WorkoutRepository.finishSession()` (and any override write) calls `replayDerivedState()`, which replays every completed session in order through `ReplayEngine` → `BeliefSessionStep`, rebuilding the derived state from scratch each time (idempotent). All tuning constants live in `BeliefConfig`, each labeled `semantic`/`fitted`/`flat` (constitution rule 2); the fitted values are pinned by the backtest gate (`BeliefScoreTest`, held-out score on real history).

For one session, `BeliefSessionStep.step`:
1. **Pre-fold pooling** (`BeliefPooling.effective`) over the session's muscles at `asOf` — the held-out state for scoring and the cold prior for first-time exercises.
2. **Per-exercise fold** (`BeliefFold.foldSession`): age `sigma2` by idle days (`confidenceDecayEstimate`), then fold each set in id order. Each set implies a model-free ln-1RM interval (`SetIntervals`, from the rep-max formula + feedback bucket), shifted up by a fatigue term `fatiguePerSetEstimate·(rank−1)`. The fold is a censored (boundary-pull) update: when the current best guess already sits inside the set's implied interval it is confirmed (uncertainty shrinks, best guess unmoved); when it falls outside, the best guess takes one correction step toward the violated boundary. `HURT` and feedback-less sets carry no interval (but count toward rank); zero-coefficient (unloadable) exercises are skipped. The fold is **local** — cross-informing happens only at read time.
3. **Post-fold pooling** for the touched muscles: each exercise with an estimate votes `mu_j − ln(coef_j)`, weighted by its confidence `1/(sigma_j² + crossLiftIndependenceEstimate²)` (a tighter, more certain estimate counts for more); the effective estimate blends the exercise's own aged estimate with the leave-one-out prediction from its siblings by that confidence weight. Fresh, confident evidence outweighs siblings; stale/cold exercises lean on them; never mutates stored estimates. The muscle level goes to `MuscleGroupStrength` + `baseline_history` (epsilon-deduped), derived coefficients to `coefficient_history`. The pooling result exposes its per-exercise breakdown (`own`/`sibling`/`siblingShare`/`voterWeight`) — consumers (trace, cross-tuning, charts) must read that, never re-derive the math.

Cold-start seeds are not stored per-exercise: `ExerciseSeedExpansion` synthesizes them live during replay by expanding each per-muscle `BaselineOverride` (manual edits and initial rows; muscles with no override default to `StartingWeights` for the user's sex/level) through the *current* `ExerciseCoefficients` for every loaded exercise in that muscle. There is no `exercise_strength_override` table — shipping a new/refit coefficient table changes seeds automatically on next replay, no migration needed.

**Prescription** is estimator → prescriber → policy, in that order:
- `BeliefPooling.effective` → `BeliefPrescriber.targetE1rm` (backs off from the effective belief's best guess by `cautionMargin` standard deviations — the weight with a `targetSuccessChance` ≈ 70% chance of success) gives the raw target.
- `PrescriptionPolicy.prescribe` clamps it: HURT backoff (15% per event, 14-day half-life, floor 0.6, muscle-level), unconditional overload nudge (+1 grid increment when the last feedback session was all RIR ≥ 2), then the **demonstrated-capacity cap** (a failed weight from the most recent feedback session cannot be re-prescribed for 28 days; the cap binds on the final *rounded* weight and floor-rounds at the grid). Policy rules are plain set-log arithmetic (`PolicyFacts`, built over a **time window** `FACTS_WINDOW_MS` — never a row-count limit) with `semantic` constants only, invisible to the backtest fitness function.
- The load-aware 1RM formula is https://arxiv.org/pdf/2603.17495 (`DefaultProgressionEngine`). Seed coefficients come from `ExerciseCoefficients`; the planner's `coefficientSource` is the effective source (latest derived coefficient if any, else the seed). All exercises use a fixed `PlannedExercise.DEFAULT_SETS` (3) sets.

`ExerciseCoefficients.byName` is a fitted artifact: literal baked coefficients equal to `guess^0.75`. The legible round-number priors (`CoefficientGuesses`) and the structural compression that shaped them (`CoefficientCompression`, `coef = guess^BAKED_LAMBDA`) now live **in the test tree** as a test-only analysis tool — the λ sweep (`CoefExponentFitTest`, `BacktestData.withCoefLambda`) still uses them, and `ExerciseCoefficientsTest` is an exact-equality guard that the shipped literals still equal `compress(CoefficientGuesses.raw, BAKED_LAMBDA)`. There is no runtime compression step and no `LAMBDA` knob. Reference (1.0) and bodyweight (0.0) lifts are anchors, unchanged by compression. Re-fitting the exponent means re-baking the table (a pure code change); nothing coefficient-derived is stored per user.

The debug "why this weight" trace (`PrescriptionTraceBuilder`) and the planner share `WorkoutRepository.prescriptionContext`; the trace reports what `prescribe()` did via the `Prescription` fields — do not re-implement pipeline math in display code.

The backtest tree (`shared/src/androidHostTest/.../backtest/`) replays real history (`shared/src/androidHostTest/resources/backtest/history.json`, gitignored) through the same `BeliefSessionStep`; `BeliefScoreTest` pins the held-out score and `BeliefPolicyBacktestTest` certifies the failed-weight invariant. Changes to fold/pooling/config must keep the gate green (re-baselining is a human decision).

### Location & equipment filtering

On workout start, `LocationService` resolves GPS coordinates to a `KnownLocation`. `WorkoutRepository.buildPlanner` filters out exercises listed in `LocationExcludedExercise` for that location. If location is unknown, no exclusions are applied.

### Saved workouts and explicit control

`saved_workout` / `saved_workout_exercise` (DB v20) hold user-authored workouts: ordered exercises with optional per-row reps. `WorkoutRepository` exposes `observeSavedWorkouts`, `saveWorkout`, `deleteSavedWorkout`, and `saveSessionAsWorkout`; the backup export includes both tables. On plan preview the "⋮" menu can add one exercise, load or append a saved workout, or save the plan. Explicit rows bypass the rested-muscle and location filters (`WorkoutPlanner.planExplicit`) and are flagged in the UI, never dropped. Location-excluded siblings now also vote in per-muscle pooling, so a lift's prescription no longer depends on which location the user is standing at. Plan rows carry no origin flag: the exercise-count slider is a **minimum** the controller maintains (restock on swipe-away only when the plan would fall below `targetCount`), and the rep-range slider reprices every row. A saved workout the user never named is stored with an empty name and shown under one derived from its exercises (`SavedWorkoutDetail.displayName` / `SavedWorkoutNaming`); editors show that derived name as the field placeholder. The "+" button opens the editor with `NEW_WORKOUT_ID`; only the editor's Done button saves (back discards, with a confirm when edits would be lost); the row is inserted on the first save that has anything in it, and an existing row is never deleted by the editor.

### Database

Room database (`AppDatabase`, version 20) on Room's Kotlin Multiplatform APIs: `BundledSQLiteDriver`
on every platform, migrations written against `SQLiteConnection` (`override fun migrate(db: SQLiteConnection)`;
`db.execSQL(...)` is the `androidx.sqlite` extension), construction via `@ConstructedBy` +
`AppDatabase.configure(builder)` (migrations + driver + query dispatcher) with the Android
`Room.databaseBuilder` in `androidMain`. Schema migrations live in `AppDatabase.Companion`; schema
JSON is written to `shared/schemas/`. Transactions use `io.github.fowles.stochastic_strength.data.withTransaction`
(the `useWriterConnection { immediateTransaction { } }` adapter), never Room's Android
`withTransaction`. Instrumented migration tests that call a migration by hand must wrap the support
database: `migrate(SupportSQLiteConnection(db))`. The app has real users — always write a proper
`Migration` when bumping the version; destructive fallback is not configured.
