# CLAUDE TODO

Bugs / cleanup ideas noticed out of scope. Triage and address when convenient.

## Open — intended / accepted-by-design (no action needed, kept for visibility)

## Open — needs triage

### From explicit-workout-control (2026-09-07) final review, deferred
- WorkoutSessionController.adjustExerciseCount: lowering the slider trims explicitly added/loaded rows from the tail (spec'd "trim regardless of origin"); consider trim-last for explicit rows or a confirm when dropping >1 row.
- SavedWorkoutsViewModel.createNew: no in-flight guard (FAB double-tap creates two "Untitled workout" rows); backing out of a fresh editor leaves an empty workout.
- SavedWorkoutEditViewModel: a deleted/missing workout id leaves the editor on the spinner (no error state).
- WorkoutSessionControllerTest.locationRefresh_keepsExplicitlyAddedExcludedRow waits with delay(300); convert to a condition poll to avoid a vacuous pass on slow devices.
- WorkoutScreen: `savedWorkouts` starts as emptyList so the Load/Append items can flicker disabled on first frame; `pendingLoadId` never cleared; snackbar replays on rotation.
- SummaryScreen: save-dialog default is "Workout " if opened before the summary loads.
- app/build.gradle.kts: androidTest-only `resolutionStrategy.force` on kotlinx-serialization 1.8.1 (Room 2.8.4 MigrationTestHelper vs Compose BOM). Revisit when bumping either.

