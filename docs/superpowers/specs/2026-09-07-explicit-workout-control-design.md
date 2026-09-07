# Explicit workout control: add, load, append, and saved workouts

**Date:** 2026-09-07
**Status:** approved design, pending implementation plan

## Goal

Let the user override the generated plan on the plan-preview screen through a
"..." overflow menu, and manage a library of saved workouts from Home.

Menu items on plan preview:

1. **Add an exercise...** — pick one exercise and append it to the plan.
2. **Load a workout...** — replace the plan with a saved workout.
3. **Append a workout...** — add a saved workout's exercises after the current rows.
4. **Save as workout...** — capture the current rows as a new saved workout.

## Decisions (from brainstorming)

- Saved workouts hold an ordered exercise list; each row has an optional rep
  count (per exercise, not per workout).
- Explicit choices win. Added or loaded exercises are included even if their
  muscle is not rested or the exercise is excluded at the current location.
  Such rows get a small visual flag; nothing is dropped.
- The exercise-count slider is a **minimum** the app maintains, not the plan's
  size. Loading or appending leaves the slider target where it was. Swiping an
  exercise away restocks a replacement only if the plan would drop below the
  target.
- **No per-row flags.** Every row in the plan is treated uniformly, however it
  got there. Consequences:
  - The rep-range slider reprices every row, including rows loaded with a saved
    rep count. A saved rep count is only the row's starting `sessionReps`.
  - Lowering the exercise-count slider trims from the end regardless of origin.
  - "Save as workout..." from the preview saves every row with null reps (the
    session decides). Reps can be set afterwards in the editor.
- Duplicates on load/append: if a loaded workout contains an exercise already
  in the plan, the original row is removed and the loaded row is kept at its
  loaded position.
- Editor is minimal: name, drag-to-reorder, swipe-to-delete, add via the shared
  picker, tap a row to set or clear its reps.
- Saved workouts are part of the full-history backup.
- A completed session can be saved as a workout from the summary screen's
  existing "⋮" menu.

## Out of scope

- No link from a completed session back to the saved workout it came from.
- No per-exercise set counts (sets stay `PlannedExercise.DEFAULT_SETS`).
- No changes to progression, replay, prescription, or the backtest gate.

## Data model

Room database version 19 → 20, with a hand-written `Migration` and updates to
every forward list in `MigrationTest`.

```kotlin
@Entity(tableName = "saved_workout")
data class SavedWorkout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "saved_workout_exercise", indices = [Index("workoutId")])
data class SavedWorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    val reps: Int?,        // null = use the session's rep pick
)
```

`SavedWorkoutDao`:

- `observeAll(): Flow<List<SavedWorkoutWithExercises>>` (Room `@Relation`,
  rows ordered by `position`).
- `getById(id): SavedWorkoutWithExercises?`
- `@Transaction suspend fun upsert(workout, rows)`: insert or update the
  workout, delete its existing rows, insert the new rows.
- `delete(id)`

Domain model in `domain/model/`:

```kotlin
data class SavedWorkoutEntry(val exercise: Exercise, val reps: Int?)
data class SavedWorkoutDetail(val id: Long, val name: String, val entries: List<SavedWorkoutEntry>)
```

`WorkoutRepository` wrappers: `observeSavedWorkouts(): Flow<List<SavedWorkoutDetail>>`,
`getSavedWorkout(id)`, `saveWorkout(id: Long?, name, entries): Long`,
`deleteSavedWorkout(id)`. Entries resolve `Exercise` rows through the exercise
DAO so UI never joins by hand. No foreign keys, matching every existing table;
a saved row whose exercise no longer exists is dropped at read time, and
`delete(id)` removes the workout's rows in the same transaction.

## Planner

`WorkoutPlanner` gains one method:

```kotlin
fun planExplicit(exercise: Exercise, reps: Int?, plan: WorkoutPlan): PlannedExercise
```

It prices a single explicitly chosen exercise with the normal prescription
(`withWeight`), using `reps ?: plan.sessionReps`. It does **not** apply the
rested-muscle filter or the candidate exclusion set. The exercise need not be in
`availableExercises`; the caller passes the `Exercise` it fetched from the
repository, and the planner prescribes from `prescribedE1rm` (falling back to
the same cold path `withWeight` already uses for exercises without an
estimate).

Location-excluded exercises are absent from `prescriptionContext.available`
and today have no `prescribedE1rm` entry. `WorkoutRepository.buildPlanner`
changes to compute `prescribedE1rm` and `policyFacts` over **all active**
exercises while `availableExercises` stays location-filtered, so an explicit
pick of an excluded exercise still gets a real weight. The controller fetches
the `Exercise` by id from the repository for `planExplicit`.

`WorkoutPlanner.isMuscleRested(exercise)` becomes public so the controller can
flag unrested rows.

## Controller

`WorkoutSessionController` changes:

- Holds `targetCount: Int`, set from `initializeSession`'s
  `preferredExerciseCount` and updated by `adjustExerciseCount`.
- `WorkoutState.PlanPreview` gains `targetCount: Int` so the slider syncs to
  the real target instead of the list size (fixes the unkeyed `remember`).
- `replaceExercise(id, reason)`: persistence of dislike/exclusion is unchanged.
  A replacement is picked only if `exercises.size - 1 < targetCount`;
  otherwise the row is simply removed.
- `adjustExerciseCount(n)`: unchanged (`take(n)` when shrinking; add until
  `size >= n` when growing).
- `addExercise(exerciseId)`: fetch the exercise, `planExplicit`, append. Ignored
  if the exercise is already in the plan.
- `loadSavedWorkout(id)`: replace `plan.exercises` with `planExplicit` rows in
  saved order; clear `exerciseOverrides`; keep `sessionRejectedIds`.
- `appendSavedWorkout(id)`: for each saved entry, remove any existing row with
  the same exercise id, then append the new row. Existing overrides are kept.
- `saveCurrentPlan(name): Long`: writes the current rows in order with null reps.
- `planWasEdited: Boolean` (derived): true if any override, reorder, add, load,
  or append has happened since `initializeSession`. Tracked by a simple
  `edited` field set by the mutating methods. Used by the Load confirmation.

Every mutation goes through the existing "copy plan, set state" path, so
`onLocationRefreshed` and the mid-workout `swapCurrentExercise` need no changes.

`WorkoutViewModel` exposes these as thin delegations plus
`observeSavedWorkouts()` for the load/append dialog.

## UI

### Plan preview (`PlanPreviewContent`)

- A `MoreVert` `IconButton` in the existing header row opens a `DropdownMenu`
  with the four items. "Load a workout..." and "Append a workout..." are
  disabled with a "No saved workouts" hint when the library is empty.
- **Add an exercise...** opens `ExercisePickerSheet` (below) with
  `excludeIds = plan exercise ids`.
- **Load / Append a workout...** open `SavedWorkoutPickerDialog`: an
  `AlertDialog` listing saved workouts (name, exercise count). Load asks
  "Replace the current plan?" first if `planWasEdited`; append never confirms.
- **Save as workout...** opens a name dialog defaulting to "Workout <date>".
  Snackbar on success.
- Row flags: rows whose exercise is excluded at the session's location show a
  small label "Not at this location"; rows whose primary muscle is not rested
  show "Trained recently". Both are computed in the controller and carried on
  `PlanPreview` as `flaggedExerciseIds: Map<Long, RowFlag>` so the composable
  stays dumb. (`muscleGroupRested` and `excludedExerciseIds` are already
  available to the planner and repository respectively.)
- The exercise-count slider reads `state.targetCount` rather than
  `plan.exercises.size`. Its label stays "Exercises".

### Shared exercise picker (`ui/components/ExercisePickerSheet.kt`)

A `ModalBottomSheet` with a search text field, the muscle and equipment
`FilterChip` rows extracted from `ExercisesScreen` into a shared
`ExerciseFilterChips` composable, and a plain list of exercise rows grouped by
primary muscle. Parameters: `exercises: List<Exercise>`, `excludeIds: Set<Long>`,
`onPick: (Long) -> Unit`, `onDismiss`. Disliked exercises are listed last with
a "disliked" label but remain pickable. `ExercisesScreen` switches to the
extracted chip composable so the two filters stay identical.

### Saved workouts flow

- Home gets a "Workouts" `OutlinedButton` alongside History / Exercises /
  Locations.
- Route `workouts`: `SavedWorkoutsScreen` with `BackTopAppBar`, a list of saved
  workouts (name, exercise count), tap to edit, a delete icon per row, and
  a FAB that creates a new empty workout and opens the editor.
- Route `workout-edit/{id}`: `SavedWorkoutEditScreen` with a name field, a
  reorderable list (same `sh.calvin.reorderable` pattern as the preview),
  swipe-to-delete rows, tap a row for a reps dialog (number field plus "Use
  session default" which clears it), and an "Add exercise" button opening
  `ExercisePickerSheet` with `excludeIds` = current rows. Back writes the
  workout in one transaction. An editor left with an empty name saves as
  "Untitled workout".
- `SavedWorkoutsViewModel` and `SavedWorkoutEditViewModel` follow the existing
  no-DI pattern (`application as StochasticStrengthApp`).

## Error handling

- Loading a saved workout whose exercises were all deleted results in an empty
  plan; the Start button is already disabled for an empty plan. The dialog
  shows "0 exercises" for such a workout.
- `planExplicit` for an exercise with no estimate falls back to the seed path
  exactly as generated plans do; no new error path.
- Repository writes run on `Dispatchers.IO` inside the ViewModel scope, as
  existing writes do.

## Backup

`WorkoutBackup` gains `savedWorkouts: List<SavedWorkout>` and
`savedWorkoutExercises: List<SavedWorkoutExercise>`; `BackupJson` writes and
reads them as two more table arrays. `WorkoutBackup.DB_VERSION` moves to 20 with
the Room bump. `FORMAT_VERSION` stays 1: a backup without the new arrays reads
as empty lists, so older files still import.

Additive import (`BackupManager`) resolves each saved row's exercise by name
through the same `resolveExerciseId` used for sets. A saved workout whose name
already exists locally is skipped (name is the identity, matching how
exercises and locations dedupe). Rows whose exercise cannot be resolved are
dropped. `AdditiveResult` gains `savedWorkoutsAdded`.

## Save from a historical session

`SummaryScreen`'s existing "⋮" menu gains "Save as workout...". It opens the
same name dialog as the preview, defaulting to "Workout <session date>". The
entries are the session's distinct exercises in order of first set, each with
`reps = targetReps` of that exercise's first working set (the reps the session
was actually prescribed at). This is the one save path that records reps,
because a completed session has a definite number to record. Implemented as
`WorkoutRepository.saveSessionAsWorkout(sessionId, name): Long`.

## Testing

JVM unit tests (`src/test/`):

- `WorkoutPlannerTest`: `planExplicit` prices a rested and an unrested exercise
  identically, honours `reps`, and defaults to `plan.sessionReps`.
- `WorkoutSessionControllerTest` (existing pattern):
  - replace restocks only when the plan would fall below `targetCount`;
  - add appends and ignores duplicates;
  - load replaces rows and clears overrides;
  - append keeps existing rows, drops duplicates in favour of loaded rows;
  - save captures order and writes null reps;
  - `PlanPreview.targetCount` follows `adjustExerciseCount`.

Instrumented tests (`src/androidTest/`):

- `MigrationTest`: 19→20 creates both tables with the expected columns and
  indices; all forward lists extended.
- `SavedWorkoutDaoTest`: upsert round trip preserves order and null reps;
  deleting a workout cascades its rows; deleting an exercise removes its rows.
- `BackupManagerTest` (existing): export/import round trip carries saved
  workouts, remaps exercise ids by name, skips a same-named workout.
- `WorkoutRepositoryTest` or DAO-level: `saveSessionAsWorkout` orders by first
  set and records the first working set's `targetReps`.

Gate: `BeliefScoreTest` and `BeliefPolicyBacktestTest` must remain green and
untouched; run the full suite at the end.
