# Explicit Workout Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user add exercises, load or append saved workouts, and save workouts from the plan preview and summary screens, backed by a new saved-workouts library with its own screens, migration, and backup support.

**Architecture:** Two new Room tables (`saved_workout`, `saved_workout_exercise`) behind `SavedWorkoutDao` and thin `WorkoutRepository` wrappers. `WorkoutPlanner.planExplicit` prices an explicitly chosen exercise, bypassing rest and location filters. `WorkoutSessionController` gains add/load/append/save operations and a "slider is a minimum" restock rule. UI adds a `MoreVert` menu on plan preview, a shared bottom-sheet exercise picker, two saved-workout screens, and a summary-menu item. Backup export/import carries the new tables.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Room 2.8 (KSP, exported schemas in `app/schemas/`), `sh.calvin.reorderable`, JUnit4 (JVM unit tests in `src/test`, instrumented in `src/androidTest`), jj for version control.

**Spec:** `docs/superpowers/specs/2026-09-07-explicit-workout-control-design.md`

## Global Constraints

- Package root: `io.github.fowles.stochastic_strength`. Source root `app/src/main/java/io/github/fowles/stochastic_strength/` is written as `$S` below; `app/src/test/java/...` as `$U`; `app/src/androidTest/java/...` as `$T`.
- Room database version 19 → 20 with a hand-written `Migration`; the app has real users, destructive fallback is not configured. Every existing forward list in `MigrationTest.kt` must append `AppDatabase.MIGRATION_19_20`.
- No foreign keys on the new tables (no existing table uses them).
- No per-row flags on `PlannedExercise`; every plan row is treated uniformly.
- No changes to progression, replay, prescription math, or the backtest gate (`BeliefScoreTest`, `BeliefPolicyBacktestTest` stay green and untouched).
- `WorkoutBackup.DB_VERSION` must move to 20 with the Room bump; `FORMAT_VERSION` stays 1.
- User copy, verbatim: menu items "Add an exercise...", "Load a workout...", "Append a workout...", "Save as workout..."; row flags "Not at this location" and "Trained recently"; empty-library hint "No saved workouts"; Home button "Workouts"; default names "Workout <date>" and "Untitled workout".
- Commits use jj: `jj commit -m "<type>: <subject>"` with types `feat`, `fix`, `test`, `chore`, `refactor`, `docs`, ending with a `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` trailer. The user owns push and reshaping.
- Tests: run the narrowest target after each change; the full JVM suite (`./gradlew :app:testDebugUnitTest`) and instrumented suite (`./gradlew :app:connectedAndroidTest`, emulator is usually running) at the end of the plan.
- Instrumented single-class run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fqcn>`.

---

## File Structure

**Create**
- `$S/data/model/SavedWorkout.kt` — `SavedWorkout`, `SavedWorkoutExercise` entities.
- `$S/data/dao/SavedWorkoutDao.kt` — CRUD for both tables.
- `$S/domain/model/SavedWorkoutDetail.kt` — `SavedWorkoutEntry`, `SavedWorkoutDetail` (exercise rows resolved).
- `$S/ui/components/ExerciseFilterChips.kt` — muscle + equipment chip rows (extracted).
- `$S/ui/components/ExercisePickerSheet.kt` — bottom-sheet single-exercise picker.
- `$S/ui/components/NameDialog.kt` — one-field text dialog.
- `$S/ui/components/SavedWorkoutPickerDialog.kt` — list-of-saved-workouts dialog.
- `$S/ui/workout/PlanPreviewMenu.kt` — the `MoreVert` dropdown for plan preview.
- `$S/ui/savedworkouts/SavedWorkoutsScreen.kt`, `SavedWorkoutsViewModel.kt` — list screen.
- `$S/ui/savedworkouts/SavedWorkoutEditScreen.kt`, `SavedWorkoutEditViewModel.kt` — editor.
- `$T/data/Migration19To20Test.kt`, `$T/data/SavedWorkoutDaoTest.kt`, `$T/domain/SavedWorkoutRepositoryTest.kt`.

**Modify**
- `$S/data/AppDatabase.kt` — entities, DAO accessor, version 20, `MIGRATION_19_20`.
- `$T/data/MigrationTest.kt` — forward lists.
- `$S/domain/WorkoutRepository.kt` — saved-workout wrappers, `saveSessionAsWorkout`, `buildPlanner` prescribes over all active exercises.
- `$S/domain/WorkoutPlanner.kt` — `planExplicit`, public `isMuscleRested`.
- `$U/domain/WorkoutPlannerTest.kt` — new tests.
- `$S/ui/workout/WorkoutState.kt` — `PlanPreview.targetCount`, `rowFlags`, `edited`; `RowFlag`.
- `$S/ui/workout/WorkoutSessionController.kt` — restock rule, add/load/append/save, flags.
- `$T/ui/workout/WorkoutSessionControllerTest.kt` — new tests.
- `$S/ui/workout/WorkoutViewModel.kt`, `WorkoutScreen.kt`, `PlanPreviewContent.kt` — menu, dialogs, flags, slider.
- `$S/ui/exercises/ExercisesScreen.kt` — use `ExerciseFilterChips`.
- `$S/domain/backup/WorkoutBackup.kt`, `BackupJson.kt`, `BackupManager.kt` — new tables.
- `$U/domain/backup/BackupJsonTest.kt`, `$T/domain/backup/BackupManagerTest.kt` — new tests.
- `$S/ui/history/HistoryViewModel.kt` — import message mentions saved workouts.
- `$S/ui/summary/SummaryScreen.kt`, `SummaryViewModel.kt` — "Save as workout...".
- `$S/ui/home/HomeScreen.kt`, `$S/ui/AppNavigation.kt` — Workouts button and routes.
- `CLAUDE.md` — database version note and saved-workouts paragraph.

---

### Task 1: Saved-workout entities, DAO, migration 19→20

**Files:**
- Create: `$S/data/model/SavedWorkout.kt`
- Create: `$S/data/dao/SavedWorkoutDao.kt`
- Modify: `$S/data/AppDatabase.kt` (entities list L29-38, version L39, DAO accessors L44-51, migrations L344-361, `addMigrations` L381-387)
- Modify: `$T/data/MigrationTest.kt` forward lists at L142-150, L255, L510, L614, L658, L715
- Create: `$T/data/Migration19To20Test.kt`
- Create: `$T/data/SavedWorkoutDaoTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class SavedWorkout(val id: Long = 0, val name: String, val createdAt: Long)
  data class SavedWorkoutExercise(val id: Long = 0, val workoutId: Long, val exerciseId: Long, val position: Int, val reps: Int?)
  interface SavedWorkoutDao {
      fun observeAll(): Flow<List<SavedWorkout>>
      fun observeAllExerciseRows(): Flow<List<SavedWorkoutExercise>>
      suspend fun getAll(): List<SavedWorkout>
      suspend fun getAllExerciseRows(): List<SavedWorkoutExercise>
      suspend fun getById(id: Long): SavedWorkout?
      suspend fun getExerciseRows(workoutId: Long): List<SavedWorkoutExercise>   // ordered by position
      suspend fun insert(workout: SavedWorkout): Long
      suspend fun update(workout: SavedWorkout)
      suspend fun insertExerciseRows(rows: List<SavedWorkoutExercise>)
      suspend fun deleteExerciseRows(workoutId: Long)
      suspend fun deleteById(id: Long)
      suspend fun deleteAll()
      suspend fun deleteAllExerciseRows()
  }
  AppDatabase.savedWorkoutDao(); AppDatabase.MIGRATION_19_20
  ```

- [ ] **Step 1: Write the DAO test (fails: DAO does not exist)**

`$T/data/SavedWorkoutDaoTest.kt`:

```kotlin
package io.github.fowles.stochastic_strength.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.fowles.stochastic_strength.data.model.SavedWorkout
import io.github.fowles.stochastic_strength.data.model.SavedWorkoutExercise
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedWorkoutDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun exerciseRows_comeBackInPositionOrder_withNullRepsPreserved() = runBlocking {
        val dao = db.savedWorkoutDao()
        val id = dao.insert(SavedWorkout(name = "Push", createdAt = 1L))
        dao.insertExerciseRows(listOf(
            SavedWorkoutExercise(workoutId = id, exerciseId = 30L, position = 2, reps = null),
            SavedWorkoutExercise(workoutId = id, exerciseId = 10L, position = 0, reps = 5),
            SavedWorkoutExercise(workoutId = id, exerciseId = 20L, position = 1, reps = 12),
        ))
        val rows = dao.getExerciseRows(id)
        assertEquals(listOf(10L, 20L, 30L), rows.map { it.exerciseId })
        assertEquals(listOf(5, 12, null), rows.map { it.reps })
    }

    @Test
    fun deleteById_removesWorkoutAndItsRows() = runBlocking {
        val dao = db.savedWorkoutDao()
        val keep = dao.insert(SavedWorkout(name = "Keep", createdAt = 1L))
        val drop = dao.insert(SavedWorkout(name = "Drop", createdAt = 2L))
        dao.insertExerciseRows(listOf(
            SavedWorkoutExercise(workoutId = keep, exerciseId = 1L, position = 0, reps = null),
            SavedWorkoutExercise(workoutId = drop, exerciseId = 2L, position = 0, reps = null),
        ))
        dao.deleteExerciseRows(drop)
        dao.deleteById(drop)
        assertNull(dao.getById(drop))
        assertTrue(dao.getExerciseRows(drop).isEmpty())
        assertEquals(1, dao.getExerciseRows(keep).size)
        assertEquals(listOf("Keep"), dao.getAll().map { it.name })
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.fowles.stochastic_strength.data.SavedWorkoutDaoTest`
Expected: compilation error, `Unresolved reference: SavedWorkout` / `savedWorkoutDao`.

- [ ] **Step 3: Add the entities**

`$S/data/model/SavedWorkout.kt`:

```kotlin
package io.github.fowles.stochastic_strength.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user-authored workout: an ordered list of exercises with optional per-row reps. Pure input. */
@Entity(tableName = "saved_workout")
data class SavedWorkout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** One row of a [SavedWorkout]. `reps == null` means "use the session's rep pick". */
@Entity(tableName = "saved_workout_exercise", indices = [Index("workoutId")])
data class SavedWorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    val reps: Int?,
)
```

- [ ] **Step 4: Add the DAO**

`$S/data/dao/SavedWorkoutDao.kt`:

```kotlin
package io.github.fowles.stochastic_strength.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.fowles.stochastic_strength.data.model.SavedWorkout
import io.github.fowles.stochastic_strength.data.model.SavedWorkoutExercise
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedWorkoutDao {
    @Query("SELECT * FROM saved_workout ORDER BY name")
    fun observeAll(): Flow<List<SavedWorkout>>

    @Query("SELECT * FROM saved_workout_exercise ORDER BY workoutId, position")
    fun observeAllExerciseRows(): Flow<List<SavedWorkoutExercise>>

    @Query("SELECT * FROM saved_workout ORDER BY name")
    suspend fun getAll(): List<SavedWorkout>

    @Query("SELECT * FROM saved_workout_exercise ORDER BY workoutId, position")
    suspend fun getAllExerciseRows(): List<SavedWorkoutExercise>

    @Query("SELECT * FROM saved_workout WHERE id = :id")
    suspend fun getById(id: Long): SavedWorkout?

    @Query("SELECT * FROM saved_workout_exercise WHERE workoutId = :workoutId ORDER BY position")
    suspend fun getExerciseRows(workoutId: Long): List<SavedWorkoutExercise>

    @Insert
    suspend fun insert(workout: SavedWorkout): Long

    @Update
    suspend fun update(workout: SavedWorkout)

    @Insert
    suspend fun insertExerciseRows(rows: List<SavedWorkoutExercise>)

    @Query("DELETE FROM saved_workout_exercise WHERE workoutId = :workoutId")
    suspend fun deleteExerciseRows(workoutId: Long)

    @Query("DELETE FROM saved_workout WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_workout")
    suspend fun deleteAll()

    @Query("DELETE FROM saved_workout_exercise")
    suspend fun deleteAllExerciseRows()
}
```

- [ ] **Step 5: Register in `AppDatabase` and write the migration**

In `$S/data/AppDatabase.kt`:
- Add imports for `SavedWorkoutDao`, `SavedWorkout`, `SavedWorkoutExercise`.
- Append `SavedWorkout::class, SavedWorkoutExercise::class,` to the `entities` array.
- Change `version = 19` to `version = 20`.
- Add `abstract fun savedWorkoutDao(): SavedWorkoutDao` after `exerciseHurtStateDao()`.
- After `MIGRATION_18_19` add:

```kotlin
        internal val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_workout` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_workout_exercise` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`workoutId` INTEGER NOT NULL, `exerciseId` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, `reps` INTEGER)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_saved_workout_exercise_workoutId` " +
                        "ON `saved_workout_exercise` (`workoutId`)"
                )
            }
        }
```
- Append `MIGRATION_19_20,` to the `addMigrations(...)` list.

- [ ] **Step 6: Build so KSP exports `app/schemas/.../20.json`**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL and a new file `app/schemas/io.github.fowles.stochastic_strength.data.AppDatabase/20.json`. Open it and confirm the `createSql` for both tables and the index name `index_saved_workout_exercise_workoutId` match the migration SQL exactly (column order, NOT NULL, nullable `reps`). If they differ, fix the migration SQL, not the entity.

- [ ] **Step 7: Extend every forward list in `MigrationTest.kt`**

Six places (L142-150, L255, L510, L614, L658, L715) currently end with `AppDatabase.MIGRATION_18_19`. Append `, AppDatabase.MIGRATION_19_20` to each `addMigrations(...)` call. Then grep to prove none were missed:

Run: `grep -c "MIGRATION_19_20" app/src/androidTest/java/io/github/fowles/stochastic_strength/data/MigrationTest.kt`
Expected: `6`.

- [ ] **Step 8: Write the 19→20 migration test**

`$T/data/Migration19To20Test.kt` (uses Room's `MigrationTestHelper`; `androidTest` assets already include `app/schemas`):

```kotlin
package io.github.fowles.stochastic_strength.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration19To20Test {
    private val dbName = "migration-19-20-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate19To20_createsSavedWorkoutTables_andKeepsExistingRows() {
        helper.createDatabase(dbName, 19).use { v19 ->
            v19.execSQL(
                "INSERT INTO exercises (name, primaryMuscle, secondaryMuscles, equipment, isDisliked, " +
                    "isUnilateral, isAsymmetric, isTimed) VALUES ('Squat', 'QUADS', '', 'BARBELL', 0, 0, 0, 0)"
            )
        }
        val v20 = helper.runMigrationsAndValidate(dbName, 20, true, AppDatabase.MIGRATION_19_20)
        v20.query("SELECT COUNT(*) FROM exercises").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        v20.execSQL("INSERT INTO saved_workout (name, createdAt) VALUES ('Push', 5)")
        v20.execSQL(
            "INSERT INTO saved_workout_exercise (workoutId, exerciseId, position, reps) VALUES (1, 1, 0, NULL)"
        )
        v20.query("SELECT reps FROM saved_workout_exercise").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
        v20.close()
    }
}
```

If the `MigrationTestHelper` constructor rejects `AppDatabase::class.java`, use `AppDatabase::class` (Room 2.8 offers both). If `secondaryMuscles` is stored under a different converter format, check `19.json` for the `exercises` columns and adjust the insert; the point is one pre-existing row surviving.

- [ ] **Step 9: Run the three instrumented classes**

Run:
```bash
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.fowles.stochastic_strength.data.SavedWorkoutDaoTest,io.github.fowles.stochastic_strength.data.Migration19To20Test,io.github.fowles.stochastic_strength.data.MigrationTest
```
Expected: all PASS. `runMigrationsAndValidate` failing with a schema mismatch means the migration SQL and `20.json` disagree; fix the SQL.

- [ ] **Step 10: Commit**

```bash
jj commit -m "feat: saved_workout tables, DAO, and migration 19->20

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Domain model and repository wrappers

**Files:**
- Create: `$S/domain/model/SavedWorkoutDetail.kt`
- Modify: `$S/domain/WorkoutRepository.kt` (add a "Saved workouts" section next to the "Exercise library" section around L275)
- Create: `$T/domain/SavedWorkoutRepositoryTest.kt`

**Interfaces:**
- Consumes: `SavedWorkoutDao` (Task 1), `WorkoutSetDao.getSetsForSession(sessionId)`, `ExerciseDao.getByIds`.
- Produces:
  ```kotlin
  data class SavedWorkoutEntry(val exercise: Exercise, val reps: Int?)
  data class SavedWorkoutDetail(val id: Long, val name: String, val entries: List<SavedWorkoutEntry>)
  // WorkoutRepository
  fun observeSavedWorkouts(): Flow<List<SavedWorkoutDetail>>
  suspend fun getSavedWorkout(id: Long): SavedWorkoutDetail?
  suspend fun saveWorkout(id: Long?, name: String, entries: List<SavedWorkoutEntry>): Long
  suspend fun deleteSavedWorkout(id: Long)
  suspend fun saveSessionAsWorkout(sessionId: Long, name: String): Long
  ```

- [ ] **Step 1: Write the failing repository test**

`$T/domain/SavedWorkoutRepositoryTest.kt`:

```kotlin
package io.github.fowles.stochastic_strength.domain

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.fowles.stochastic_strength.data.AppDatabase
import io.github.fowles.stochastic_strength.data.model.Equipment
import io.github.fowles.stochastic_strength.data.model.Exercise
import io.github.fowles.stochastic_strength.data.model.MuscleGroup
import io.github.fowles.stochastic_strength.data.model.SetFeedback
import io.github.fowles.stochastic_strength.data.model.WorkoutSession
import io.github.fowles.stochastic_strength.data.model.WorkoutSet
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedWorkoutRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: WorkoutRepository
    private lateinit var bench: Exercise
    private lateinit var squat: Exercise

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = WorkoutRepository(db)
        val benchId = db.exerciseDao().insert(Exercise(name = "Bench", primaryMuscle = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val squatId = db.exerciseDao().insert(Exercise(name = "Squat", primaryMuscle = MuscleGroup.QUADS, equipment = Equipment.BARBELL))
        bench = db.exerciseDao().getById(benchId)!!
        squat = db.exerciseDao().getById(squatId)!!
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun saveWorkout_roundTrips_orderAndReps_andUpdateReplacesRows() = runBlocking {
        val id = repo.saveWorkout(null, "Legs first", listOf(SavedWorkoutEntry(squat, 5), SavedWorkoutEntry(bench, null)))
        val detail = repo.getSavedWorkout(id)!!
        assertEquals("Legs first", detail.name)
        assertEquals(listOf(squat.id, bench.id), detail.entries.map { it.exercise.id })
        assertEquals(listOf(5, null), detail.entries.map { it.reps })

        val sameId = repo.saveWorkout(id, "Bench only", listOf(SavedWorkoutEntry(bench, 8)))
        assertEquals(id, sameId)
        val updated = repo.getSavedWorkout(id)!!
        assertEquals("Bench only", updated.name)
        assertEquals(listOf(bench.id), updated.entries.map { it.exercise.id })
        assertEquals(1, db.savedWorkoutDao().getAllExerciseRows().size)
    }

    @Test
    fun observeSavedWorkouts_dropsRowsWhoseExerciseIsGone() = runBlocking {
        val id = repo.saveWorkout(null, "W", listOf(SavedWorkoutEntry(bench, null), SavedWorkoutEntry(squat, null)))
        db.exerciseDao().deleteAll()
        db.exerciseDao().insert(bench)   // only bench survives, with its original id
        val list = repo.observeSavedWorkouts().first()
        assertEquals(1, list.size)
        assertEquals(id, list[0].id)
        assertEquals(listOf(bench.id), list[0].entries.map { it.exercise.id })
    }

    @Test
    fun deleteSavedWorkout_removesDetailAndRows() = runBlocking {
        val id = repo.saveWorkout(null, "W", listOf(SavedWorkoutEntry(bench, null)))
        repo.deleteSavedWorkout(id)
        assertNull(repo.getSavedWorkout(id))
        assertEquals(0, db.savedWorkoutDao().getAllExerciseRows().size)
    }

    @Test
    fun saveSessionAsWorkout_ordersByFirstSet_andRecordsFirstSetTargetReps() = runBlocking {
        val sid = db.workoutSessionDao().insert(WorkoutSession(startTime = 1000, endTime = 5000))
        db.workoutSetDao().insert(WorkoutSet(sessionId = sid, exerciseId = squat.id, setNumber = 1, targetWeight = 100f, targetReps = 5, feedback = SetFeedback.RIR_2_4, completedAt = 1100))
        db.workoutSetDao().insert(WorkoutSet(sessionId = sid, exerciseId = squat.id, setNumber = 2, targetWeight = 100f, targetReps = 5, feedback = SetFeedback.RIR_2_4, completedAt = 1200))
        db.workoutSetDao().insert(WorkoutSet(sessionId = sid, exerciseId = bench.id, setNumber = 1, targetWeight = 60f, targetReps = 8, feedback = SetFeedback.RIR_2_4, completedAt = 1300))
        val id = repo.saveSessionAsWorkout(sid, "Replay")
        val detail = repo.getSavedWorkout(id)!!
        assertEquals(listOf(squat.id, bench.id), detail.entries.map { it.exercise.id })
        assertEquals(listOf(5, 8), detail.entries.map { it.reps })
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.fowles.stochastic_strength.domain.SavedWorkoutRepositoryTest`
Expected: `Unresolved reference: SavedWorkoutEntry` / `saveWorkout`.

- [ ] **Step 3: Add the domain model**

`$S/domain/model/SavedWorkoutDetail.kt`:

```kotlin
package io.github.fowles.stochastic_strength.domain.model

import io.github.fowles.stochastic_strength.data.model.Exercise

/** One row of a saved workout with its exercise resolved. `reps == null` = session decides. */
data class SavedWorkoutEntry(val exercise: Exercise, val reps: Int?)

data class SavedWorkoutDetail(
    val id: Long,
    val name: String,
    val entries: List<SavedWorkoutEntry>,
)
```

- [ ] **Step 4: Add the repository wrappers**

In `$S/domain/WorkoutRepository.kt`, add imports for `SavedWorkout`, `SavedWorkoutExercise`, `SavedWorkoutEntry`, `SavedWorkoutDetail`, and `kotlinx.coroutines.flow.combine`. Add after the "Exercise library" block:

```kotlin
    // Saved workouts

    /** Resolves every saved workout against the live exercise table; rows whose exercise is gone are dropped. */
    fun observeSavedWorkouts(): Flow<List<SavedWorkoutDetail>> = combine(
        db.savedWorkoutDao().observeAll(),
        db.savedWorkoutDao().observeAllExerciseRows(),
        db.exerciseDao().observeAll(),
    ) { workouts, rows, exercises ->
        val byId = exercises.associateBy { it.id }
        val rowsByWorkout = rows.groupBy { it.workoutId }
        workouts.map { w -> w.toDetail(rowsByWorkout[w.id].orEmpty(), byId) }
    }

    suspend fun getSavedWorkout(id: Long): SavedWorkoutDetail? {
        val workout = db.savedWorkoutDao().getById(id) ?: return null
        val rows = db.savedWorkoutDao().getExerciseRows(id)
        val byId = db.exerciseDao().getByIds(rows.map { it.exerciseId }).associateBy { it.id }
        return workout.toDetail(rows, byId)
    }

    private fun SavedWorkout.toDetail(rows: List<SavedWorkoutExercise>, byId: Map<Long, Exercise>) =
        SavedWorkoutDetail(
            id = id,
            name = name,
            entries = rows.sortedBy { it.position }
                .mapNotNull { r -> byId[r.exerciseId]?.let { SavedWorkoutEntry(it, r.reps) } },
        )

    /** Inserts (id == null) or fully replaces (id != null) a saved workout in one transaction. */
    suspend fun saveWorkout(id: Long?, name: String, entries: List<SavedWorkoutEntry>): Long = db.withTransaction {
        val dao = db.savedWorkoutDao()
        val workoutId = if (id == null) {
            dao.insert(SavedWorkout(name = name, createdAt = System.currentTimeMillis()))
        } else {
            val existing = dao.getById(id) ?: error("Saved workout $id not found")
            dao.update(existing.copy(name = name))
            dao.deleteExerciseRows(id)
            id
        }
        dao.insertExerciseRows(entries.mapIndexed { i, e ->
            SavedWorkoutExercise(workoutId = workoutId, exerciseId = e.exercise.id, position = i, reps = e.reps)
        })
        workoutId
    }

    suspend fun deleteSavedWorkout(id: Long) = db.withTransaction {
        db.savedWorkoutDao().deleteExerciseRows(id)
        db.savedWorkoutDao().deleteById(id)
    }

    /**
     * Captures a completed session as a saved workout: distinct exercises in order of first set,
     * each with the first set's target reps (the reps the session was prescribed at).
     */
    suspend fun saveSessionAsWorkout(sessionId: Long, name: String): Long {
        val sets = db.workoutSetDao().getSetsForSession(sessionId)
        val firstSetByExercise = sets.sortedWith(compareBy({ it.completedAt ?: Long.MAX_VALUE }, { it.id }))
            .distinctBy { it.exerciseId }
        val byId = db.exerciseDao().getByIds(firstSetByExercise.map { it.exerciseId }).associateBy { it.id }
        val entries = firstSetByExercise.mapNotNull { s -> byId[s.exerciseId]?.let { SavedWorkoutEntry(it, s.targetReps) } }
        return saveWorkout(null, name, entries)
    }
```

- [ ] **Step 5: Run the repository test**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.fowles.stochastic_strength.domain.SavedWorkoutRepositoryTest`
Expected: 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
jj commit -m "feat: saved workout domain model and repository wrappers

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Planner `planExplicit` and repository prescribes for all active exercises

**Files:**
- Modify: `$S/domain/WorkoutPlanner.kt` (add `planExplicit`, make `muscleGroupRested` public as `isMuscleRested`)
- Modify: `$S/domain/WorkoutRepository.kt` `prescriptionContext` (L78-93) and `buildPlanner` (L95-136)
- Modify: `$U/domain/WorkoutPlannerTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  // WorkoutPlanner
  fun planExplicit(exercise: Exercise, reps: Int?, plan: WorkoutPlan): PlannedExercise
  fun isMuscleRested(exercise: Exercise): Boolean
  ```
  `buildPlanner`'s `prescribedE1rm` and `policyFacts` now cover every active exercise, not only location-available ones.

- [ ] **Step 1: Write the failing planner tests**

Append inside `class WorkoutPlannerTest` in `$U/domain/WorkoutPlannerTest.kt` (helpers `exercise`, `strengthsFor`, `planner`, `nearFailureSet` already exist there):

```kotlin
    @Test
    fun planExplicit_pricesExerciseWhoseMuscleIsNotRested() {
        val chest = exercise(1, "Bench", MuscleGroup.CHEST)
        val now = System.currentTimeMillis()
        val p = planner(
            exercises = listOf(chest),
            strengths = strengthsFor(MuscleGroup.CHEST to 100f),
            recentHistory = mapOf(1L to listOf(nearFailureSet(1L, now - 1000), nearFailureSet(1L, now - 2000))),
            nowMs = now,
        )
        assertTrue(!p.isMuscleRested(chest))
        assertTrue(p.generateWorkout(sessionReps = 8).exercises.isEmpty())

        val planned = p.planExplicit(chest, reps = null, plan = WorkoutPlan(emptyList(), null, sessionReps = 8))
        assertEquals(chest, planned.exercise)
        assertEquals(8, planned.sessionReps)
        assertTrue(planned.sessionWeight > 0f)
    }

    @Test
    fun planExplicit_usesPinnedReps_elsePlanSessionReps() {
        val chest = exercise(1, "Bench", MuscleGroup.CHEST)
        val p = planner(exercises = listOf(chest), strengths = strengthsFor(MuscleGroup.CHEST to 100f))
        val plan = WorkoutPlan(emptyList(), null, sessionReps = 10)
        assertEquals(5, p.planExplicit(chest, reps = 5, plan = plan).sessionReps)
        assertEquals(10, p.planExplicit(chest, reps = null, plan = plan).sessionReps)
        // Fewer reps at the same e1rm means a heavier set.
        assertTrue(p.planExplicit(chest, 5, plan).sessionWeight > p.planExplicit(chest, 10, plan).sessionWeight)
    }

    @Test
    fun planExplicit_pricesExerciseOutsideAvailableList_whenItHasAnEstimate() {
        val chest = exercise(1, "Bench", MuscleGroup.CHEST)
        val excludedHere = exercise(2, "Incline Bench", MuscleGroup.CHEST)
        val p = WorkoutPlanner(
            availableExercises = listOf(chest),
            prescribedE1rm = strengthsToPrescribedE1rm(listOf(chest, excludedHere), strengthsFor(MuscleGroup.CHEST to 100f), ExerciseCoefficients),
            recentHistory = emptyMap(),
            weightUnit = WeightUnit.KG,
            locationId = null,
            random = Random(0),
        )
        val planned = p.planExplicit(excludedHere, null, WorkoutPlan(emptyList(), null, sessionReps = 8))
        assertTrue(planned.sessionWeight > 0f)
        assertTrue(planned.warmupSets.isNotEmpty())
    }
```

- [ ] **Step 2: Run to confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests "io.github.fowles.stochastic_strength.domain.WorkoutPlannerTest"`
Expected: compilation error `Unresolved reference: planExplicit` / `isMuscleRested`.

- [ ] **Step 3: Implement in `WorkoutPlanner`**

In `$S/domain/WorkoutPlanner.kt`:
- Rename `private fun muscleGroupRested(exercise: Exercise)` to `fun isMuscleRested(exercise: Exercise): Boolean` (public) and update its three call sites (`generateWorkout`, `candidatesFor`).
- Add after `pickAdditional`:

```kotlin
    /**
     * Price one explicitly chosen exercise. Skips the rested-muscle and in-plan/rejected filters —
     * the user asked for it — but prescribes through the same policy as any generated row.
     * The exercise need not be in [availableExercises]; it only needs a `prescribedE1rm` entry
     * (or a coefficient of zero, in which case it is unloaded like any bodyweight row).
     */
    fun planExplicit(exercise: Exercise, reps: Int?, plan: WorkoutPlan): PlannedExercise =
        withWeight(PlannedExercise(exercise = exercise), reps ?: plan.sessionReps)
```

- [ ] **Step 4: Run planner tests**

Run: `./gradlew :app:testDebugUnitTest --tests "io.github.fowles.stochastic_strength.domain.WorkoutPlannerTest"`
Expected: PASS (all, including the three new).

- [ ] **Step 5: Make the repository prescribe over all active exercises**

In `$S/domain/WorkoutRepository.kt` replace the body of `prescriptionContext` so that `seedCoef`, `muscleIds`, `factsSets`, and `policyFacts` are built over `allActive` while `available` stays location-filtered:

```kotlin
    private suspend fun prescriptionContext(locationId: Long?, now: Long): PrescriptionContext {
        val excluded = excludedExerciseIds(locationId)
        val allActive = db.exerciseDao().getActive()
        val available = allActive.filter { it.id !in excluded }
        // Estimates and policy facts cover every active exercise so an explicit pick of a
        // location-excluded lift still gets a real weight; only generation is location-filtered.
        val seedCoef = allActive.associate { it.id to (ExerciseCoefficients.get(it) ?: 0f) }
        val muscleIds = allActive.filter { (seedCoef[it.id] ?: 0f) > 0f }
            .groupBy { it.primaryMuscle }.mapValues { e -> e.value.map { it.id } }
        val factsSets = if (allActive.isNotEmpty())
            db.workoutSetDao().getCompletedSetsForExercisesSince(
                allActive.map { it.id }, now - PrescriptionPolicy.FACTS_WINDOW_MS)
        else emptyList()
        val policyFacts = PolicyFacts.build(
            sets = factsSets,
            exerciseMuscle = allActive.associate { it.id to it.primaryMuscle },
        )
        return PrescriptionContext(available, seedCoef, muscleIds, policyFacts)
    }
```

`buildPlanner` needs no change: `prescribedE1rm` is derived from `ctx.muscleExerciseIds`, which now spans all active exercises; `availableExercises = available` stays location-filtered.

- [ ] **Step 6: Run the JVM suite (prescription-trace and backtest tests read `prescriptionContext`)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS. `BeliefScoreTest` and `BeliefPolicyBacktestTest` untouched and green. If `ProdBssPrescriptionTest` changes value, stop: that means the muscle grouping change altered pooling; report before proceeding.

- [ ] **Step 7: Commit**

```bash
jj commit -m "feat: WorkoutPlanner.planExplicit; prescribe over all active exercises

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Controller: target count, restock rule, add/load/append/save, row flags

**Files:**
- Modify: `$S/ui/workout/WorkoutState.kt` (`PlanPreview` L24-30)
- Modify: `$S/ui/workout/WorkoutSessionController.kt` (fields L56-61; `initializeSession` L78-101; `replaceExercise` L138-169; `adjustExerciseCount` L171-191; `adjustExerciseWeight` L203-227; `moveExercise` L229-234)
- Modify: `$T/ui/workout/WorkoutSessionControllerTest.kt`

**Interfaces:**
- Consumes: `WorkoutPlanner.planExplicit`, `isMuscleRested` (Task 3); `WorkoutRepository.getSavedWorkout`, `saveWorkout`, `getExcludedExerciseIds`, `getExerciseById` (Task 2 / existing).
- Produces:
  ```kotlin
  enum class RowFlag { NOT_AT_LOCATION, TRAINED_RECENTLY }
  data class PlanPreview(
      val plan: WorkoutPlan, val locationName: String? = null, val repMin: Int = 5, val repMax: Int = 10,
      val detraining: DetrainingNotice? = null,
      val targetCount: Int = WorkoutGenerator.DEFAULT_EXERCISE_COUNT,
      val rowFlags: Map<Long, RowFlag> = emptyMap(),
      val edited: Boolean = false,
  )
  // WorkoutSessionController
  fun addExercise(exerciseId: Long)
  fun loadSavedWorkout(id: Long)
  fun appendSavedWorkout(id: Long)
  suspend fun saveCurrentPlan(name: String): Long?
  ```

- [ ] **Step 1: Write the failing controller tests**

Add to `$T/ui/workout/WorkoutSessionControllerTest.kt`. First a shared helper (place near `seedDerivedStrength`):

```kotlin
    /** Fresh DB with three loaded exercises and a controller parked on PlanPreview at [count]. */
    private data class PreviewFixture(
        val db: AppDatabase, val repo: WorkoutRepository, val controller: WorkoutSessionController,
    )

    private suspend fun previewFixture(count: Int): PreviewFixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val freshDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        freshDb.userProfileDao().insert(
            UserProfile(sex = Sex.MALE, strengthLevel = StrengthLevel.MEDIUM, weightUnit = WeightUnit.KG)
        )
        freshDb.exerciseDao().insertAll(listOf(
            Exercise(name = "Barbell Bench Press", primaryMuscle = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
            Exercise(name = "Barbell Squat", primaryMuscle = MuscleGroup.QUADS, equipment = Equipment.BARBELL),
            Exercise(name = "Barbell Row", primaryMuscle = MuscleGroup.BACK, equipment = Equipment.BARBELL),
        ))
        val freshRepo = WorkoutRepository(freshDb)
        val active = freshDb.exerciseDao().getActive()
        val now = System.currentTimeMillis()
        freshRepo.derivedState.rebuild { mut ->
            for (m in listOf(MuscleGroup.CHEST, MuscleGroup.QUADS, MuscleGroup.BACK)) {
                mut.upsertMuscleGroupStrength(MuscleGroupStrength(m, 100f))
            }
            mut.putExerciseBeliefs(
                active.associate { it.id to Belief(bestGuessLn = kotlin.math.ln(100f), uncertainty = 4e-4f, updatedAt = now) }
            )
        }
        val c = WorkoutSessionController(freshDb, freshRepo, WorkoutSessionBus(), scope)
        c.initializeSession(
            locationId = null, locationName = null,
            preferredExerciseCount = count, preferredRepMin = 5, preferredRepMax = 10,
            weightUnit = WeightUnit.KG,
        )
        awaitPreviewSize(c, count)
        return PreviewFixture(freshDb, freshRepo, c)
    }

    private suspend fun awaitPreviewSize(c: WorkoutSessionController, size: Int, timeoutMs: Long = 2000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val s = c.state.value
            if (s is WorkoutState.PlanPreview && s.plan.exercises.size == size) return
            delay(20)
        }
        error("Preview did not reach $size exercises; was ${c.state.value}")
    }

    private fun preview(c: WorkoutSessionController) = c.state.value as WorkoutState.PlanPreview
```

Then the tests:

```kotlin
    @Test
    fun replace_atTarget_restocks() = runBlocking {
        val f = previewFixture(count = 2)
        val removedId = preview(f.controller).plan.exercises[0].exercise.id
        f.controller.replaceExercise(removedId, ExerciseRemovalReason.SKIP_TODAY)
        awaitPreviewSize(f.controller, 2)
        val ids = preview(f.controller).plan.exercises.map { it.exercise.id }
        assertTrue(removedId !in ids)
        assertEquals(2, ids.size)
        f.db.close()
    }

    @Test
    fun replace_aboveTarget_removesWithoutRestock() = runBlocking {
        val f = previewFixture(count = 2)
        val third = f.db.exerciseDao().getActive().first { ex ->
            preview(f.controller).plan.exercises.none { it.exercise.id == ex.id }
        }
        f.controller.addExercise(third.id)
        awaitPreviewSize(f.controller, 3)
        assertEquals(2, preview(f.controller).targetCount)

        f.controller.replaceExercise(third.id, ExerciseRemovalReason.SKIP_TODAY)
        awaitPreviewSize(f.controller, 2)
        delay(100)
        assertEquals(2, preview(f.controller).plan.exercises.size)
        f.db.close()
    }

    @Test
    fun addExercise_appends_marksEdited_andIgnoresDuplicates() = runBlocking {
        val f = previewFixture(count = 1)
        assertTrue(!preview(f.controller).edited)
        val existing = preview(f.controller).plan.exercises[0].exercise.id
        val other = f.db.exerciseDao().getActive().first { it.id != existing }
        f.controller.addExercise(other.id)
        awaitPreviewSize(f.controller, 2)
        val p = preview(f.controller)
        assertEquals(other.id, p.plan.exercises[1].exercise.id)
        assertTrue(p.plan.exercises[1].sessionWeight > 0f)
        assertTrue(p.edited)

        f.controller.addExercise(other.id)
        delay(150)
        assertEquals(2, preview(f.controller).plan.exercises.size)
        f.db.close()
    }

    @Test
    fun loadSavedWorkout_replacesRows_clearsOverrides_keepsTarget() = runBlocking {
        val f = previewFixture(count = 2)
        val first = preview(f.controller).plan.exercises[0]
        f.controller.adjustExerciseWeight(first.exercise.id, +2.5f)
        assertTrue(preview(f.controller).plan.exerciseOverrides.isNotEmpty())

        val all = f.db.exerciseDao().getActive()
        val savedId = f.repo.saveWorkout(null, "Trio", all.map { SavedWorkoutEntry(it, 6) })
        f.controller.loadSavedWorkout(savedId)
        awaitPreviewSize(f.controller, 3)
        val p = preview(f.controller)
        assertEquals(all.map { it.id }, p.plan.exercises.map { it.exercise.id })
        assertEquals(listOf(6, 6, 6), p.plan.exercises.map { it.sessionReps })
        assertTrue(p.plan.exerciseOverrides.isEmpty())
        assertEquals(2, p.targetCount)
        assertTrue(p.edited)
        f.db.close()
    }

    @Test
    fun appendSavedWorkout_keepsExisting_andReplacesDuplicateWithLoadedRow() = runBlocking {
        val f = previewFixture(count = 2)
        val before = preview(f.controller).plan.exercises
        val dup = before[0].exercise
        val other = f.db.exerciseDao().getActive().first { ex -> before.none { it.exercise.id == ex.id } }
        val savedId = f.repo.saveWorkout(null, "Two", listOf(SavedWorkoutEntry(other, 12), SavedWorkoutEntry(dup, 3)))
        f.controller.appendSavedWorkout(savedId)
        awaitPreviewSize(f.controller, 3)
        val ids = preview(f.controller).plan.exercises.map { it.exercise.id }
        assertEquals(listOf(before[1].exercise.id, other.id, dup.id), ids)
        assertEquals(3, preview(f.controller).plan.exercises.last().sessionReps)
        f.db.close()
    }

    @Test
    fun saveCurrentPlan_writesOrderWithNullReps() = runBlocking {
        val f = previewFixture(count = 2)
        val ids = preview(f.controller).plan.exercises.map { it.exercise.id }
        val savedId = f.controller.saveCurrentPlan("Snapshot")!!
        val detail = f.repo.getSavedWorkout(savedId)!!
        assertEquals("Snapshot", detail.name)
        assertEquals(ids, detail.entries.map { it.exercise.id })
        assertTrue(detail.entries.all { it.reps == null })
        f.db.close()
    }

    @Test
    fun adjustExerciseCount_updatesTargetCountOnPreview() = runBlocking {
        val f = previewFixture(count = 1)
        f.controller.adjustExerciseCount(3)
        awaitPreviewSize(f.controller, 3)
        assertEquals(3, preview(f.controller).targetCount)
        f.controller.adjustExerciseCount(1)
        awaitPreviewSize(f.controller, 1)
        assertEquals(1, preview(f.controller).targetCount)
        f.db.close()
    }
```

Add the import `io.github.fowles.stochastic_strength.domain.model.SavedWorkoutEntry` at the top of the test file.

- [ ] **Step 2: Run to confirm failure**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.fowles.stochastic_strength.ui.workout.WorkoutSessionControllerTest`
Expected: compilation error on `addExercise` / `targetCount` / `edited`.

- [ ] **Step 3: Extend `WorkoutState.PlanPreview`**

In `$S/ui/workout/WorkoutState.kt`, add `import io.github.fowles.stochastic_strength.domain.WorkoutGenerator` and change `PlanPreview` to:

```kotlin
    data class PlanPreview(
        val plan: WorkoutPlan,
        val locationName: String? = null,
        val repMin: Int = 5,
        val repMax: Int = 10,
        val detraining: DetrainingNotice? = null,
        /** The exercise-count slider's value: the minimum plan size the app maintains. */
        val targetCount: Int = WorkoutGenerator.DEFAULT_EXERCISE_COUNT,
        /** Rows the user chose that the generator would have filtered out. */
        val rowFlags: Map<Long, RowFlag> = emptyMap(),
        /** True once the user has changed the plan (weight, order, add, load, append). */
        val edited: Boolean = false,
    ) : WorkoutState
```

and add at file bottom:

```kotlin
enum class RowFlag { NOT_AT_LOCATION, TRAINED_RECENTLY }
```

- [ ] **Step 4: Implement in `WorkoutSessionController`**

Add imports: `io.github.fowles.stochastic_strength.domain.model.SavedWorkoutEntry`, `io.github.fowles.stochastic_strength.domain.WorkoutGenerator`.

Add a field after `preferredRepMax`:

```kotlin
    private var targetCount: Int = WorkoutGenerator.DEFAULT_EXERCISE_COUNT
```

In `initializeSession`, set `targetCount = preferredExerciseCount` before `setState(...)` and pass `targetCount = preferredExerciseCount` into the `PlanPreview` constructor.

In `replaceExercise`, replace the line `val replacement = p.pickReplacement(updatedPlan, currentIndex)` with:

```kotlin
            // The slider is a floor, not the plan's size: only restock when removing would drop below it.
            val replacement = if (updatedPlan.exercises.size - 1 < targetCount)
                p.pickReplacement(updatedPlan, currentIndex) else null
```

In `adjustExerciseCount`, after `addExerciseJob?.cancel()` add `targetCount = targetCount.coerceAtLeast(1)` — careful with the shadowing: rename the parameter to `newTarget` and write:

```kotlin
    fun adjustExerciseCount(newTarget: Int) {
        addExerciseJob?.cancel()
        targetCount = newTarget.coerceAtLeast(1)
        val preview = _state.value as? WorkoutState.PlanPreview ?: return
        val current = preview.plan.exercises
        when {
            targetCount < current.size -> {
                val trimmed = current.take(targetCount)
                setState(preview.copy(plan = preview.plan.copy(exercises = trimmed), targetCount = targetCount))
            }
            targetCount > current.size -> {
                val needed = targetCount - current.size
                setState(preview.copy(targetCount = targetCount))
                addExerciseJob = scope.launch {
                    repeat(needed) {
                        val p = _state.value as? WorkoutState.PlanPreview ?: return@launch
                        val extra = planner?.pickAdditional(p.plan) ?: return@launch
                        setState(p.copy(plan = p.plan.copy(exercises = p.plan.exercises + extra)))
                    }
                }
            }
            else -> setState(preview.copy(targetCount = targetCount))
        }
    }
```

In `adjustExerciseWeight`, change the `setState(...)` call to include `edited = true`:
```kotlin
        setState(state.copy(plan = state.plan.copy(exercises = exercises, exerciseOverrides = updatedOverrides), edited = true))
```
In `moveExercise`, likewise: `setState(preview.copy(plan = preview.plan.copy(exercises = exercises), edited = true))`.

Add the new operations after `moveExercise`:

```kotlin
    fun addExercise(exerciseId: Long) {
        val preview = _state.value as? WorkoutState.PlanPreview ?: return
        if (preview.plan.exercises.any { it.exercise.id == exerciseId }) return
        scope.launch {
            val p = planner ?: return@launch
            val exercise = repository.getExerciseById(exerciseId) ?: return@launch
            val current = _state.value as? WorkoutState.PlanPreview ?: return@launch
            if (current.plan.exercises.any { it.exercise.id == exerciseId }) return@launch
            val planned = p.planExplicit(exercise, reps = null, plan = current.plan)
            val newPlan = current.plan.copy(
                exercises = current.plan.exercises + planned,
                sessionRejectedIds = current.plan.sessionRejectedIds - exerciseId,
            )
            setState(withRowFlags(current.copy(plan = newPlan, edited = true)))
        }
    }

    fun loadSavedWorkout(id: Long) = applySavedWorkout(id, append = false)

    fun appendSavedWorkout(id: Long) = applySavedWorkout(id, append = true)

    private fun applySavedWorkout(id: Long, append: Boolean) {
        addExerciseJob?.cancel()
        scope.launch {
            val saved = repository.getSavedWorkout(id) ?: return@launch
            val entries = saved.entries.distinctBy { it.exercise.id }
            val loadedIds = entries.map { it.exercise.id }.toSet()
            val current = _state.value as? WorkoutState.PlanPreview ?: return@launch
            // Load discards manual weight edits, so price from a planner that has none.
            val p = if (append) planner ?: return@launch
            else repository.buildPlanner(sessionLocationId, weightUnit).also { planner = it }
            val basePlan = if (append) current.plan else current.plan.copy(exerciseOverrides = emptyMap())
            // A loaded row wins over an existing row for the same exercise.
            val kept = if (append) basePlan.exercises.filter { it.exercise.id !in loadedIds } else emptyList()
            val loaded = entries.map { p.planExplicit(it.exercise, it.reps, basePlan) }
            val newPlan = basePlan.copy(
                exercises = kept + loaded,
                sessionRejectedIds = basePlan.sessionRejectedIds - loadedIds,
            )
            setState(withRowFlags(current.copy(plan = newPlan, edited = true)))
        }
    }

    /** Saves the current preview rows, in order, with no pinned reps. Null if not on the preview. */
    suspend fun saveCurrentPlan(name: String): Long? {
        val preview = _state.value as? WorkoutState.PlanPreview ?: return null
        return repository.saveWorkout(
            id = null,
            name = name,
            entries = preview.plan.exercises.map { SavedWorkoutEntry(it.exercise, reps = null) },
        )
    }

    /** Flags rows the generator would have filtered: location-excluded first, then unrested muscle. */
    private suspend fun withRowFlags(preview: WorkoutState.PlanPreview): WorkoutState.PlanPreview {
        val excluded = sessionLocationId?.let { repository.getExcludedExerciseIds(it) } ?: emptySet()
        val p = planner
        val flags = preview.plan.exercises.mapNotNull { pe ->
            val ex = pe.exercise
            when {
                ex.id in excluded -> ex.id to RowFlag.NOT_AT_LOCATION
                p != null && !p.isMuscleRested(ex) -> ex.id to RowFlag.TRAINED_RECENTLY
                else -> null
            }
        }.toMap()
        return preview.copy(rowFlags = flags)
    }
```

- [ ] **Step 5: Run the controller tests**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.fowles.stochastic_strength.ui.workout.WorkoutSessionControllerTest`
Expected: all PASS, including the 7 new tests and every pre-existing one.

- [ ] **Step 6: Build the app (the ViewModel/UI still compile: new fields have defaults)**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
jj commit -m "feat: controller add/load/append/save; slider is a floor for restock

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Backup carries saved workouts

**Files:**
- Modify: `$S/domain/backup/WorkoutBackup.kt`, `BackupJson.kt`, `BackupManager.kt`
- Modify: `$S/ui/history/HistoryViewModel.kt` (import message, L152)
- Modify: `$U/domain/backup/BackupJsonTest.kt`, `$T/domain/backup/BackupManagerTest.kt`

**Interfaces:**
- Consumes: `SavedWorkoutDao` (Task 1).
- Produces: `WorkoutBackup.savedWorkouts: List<SavedWorkout> = emptyList()`, `WorkoutBackup.savedWorkoutExercises: List<SavedWorkoutExercise> = emptyList()`, `WorkoutBackup.DB_VERSION = 20`, `AdditiveResult.savedWorkoutsAdded: Int`.

- [ ] **Step 1: Write the failing JSON round-trip test**

Append to `$U/domain/backup/BackupJsonTest.kt` (add imports for `SavedWorkout`, `SavedWorkoutExercise`, `assertEquals`, `assertTrue`):

```kotlin
    @Test
    fun `saved workouts survive round trip and default to empty when absent`() {
        val backup = WorkoutBackup(
            formatVersion = WorkoutBackup.FORMAT_VERSION,
            dbVersion = WorkoutBackup.DB_VERSION,
            exportedAt = 0L,
            exercises = emptyList(), knownLocations = emptyList(), locationExcludedExercises = emptyList(),
            workoutSessions = emptyList(), workoutSets = emptyList(), userProfile = emptyList(),
            baselineOverrides = emptyList(), exerciseHurtState = emptyList(),
            savedWorkouts = listOf(SavedWorkout(id = 3, name = "Push", createdAt = 9L)),
            savedWorkoutExercises = listOf(
                SavedWorkoutExercise(id = 1, workoutId = 3, exerciseId = 7, position = 0, reps = 5),
                SavedWorkoutExercise(id = 2, workoutId = 3, exerciseId = 8, position = 1, reps = null),
            ),
        )
        val restored = BackupJsonParser.parse(BackupJsonBuilder.build(backup))
        assertEquals(backup.savedWorkouts, restored.savedWorkouts)
        assertEquals(backup.savedWorkoutExercises, restored.savedWorkoutExercises)

        // A pre-saved-workouts file (no arrays) still parses.
        val legacy = BackupJsonBuilder.build(backup.copy(savedWorkouts = emptyList(), savedWorkoutExercises = emptyList()))
            .replace("\"savedWorkouts\"", "\"_gone\"").replace("\"savedWorkoutExercises\"", "\"_gone2\"")
        val parsed = BackupJsonParser.parse(legacy)
        assertTrue(parsed.savedWorkouts.isEmpty())
        assertTrue(parsed.savedWorkoutExercises.isEmpty())
    }
```

- [ ] **Step 2: Run to confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests "io.github.fowles.stochastic_strength.domain.backup.BackupJsonTest"`
Expected: compilation error, no `savedWorkouts` parameter.

- [ ] **Step 3: Extend `WorkoutBackup`**

In `$S/domain/backup/WorkoutBackup.kt` add imports for `SavedWorkout`, `SavedWorkoutExercise`, two trailing constructor fields with defaults (so existing construction sites keep compiling), and bump the version:

```kotlin
    val exerciseHurtState: List<ExerciseHurtState>,
    val savedWorkouts: List<SavedWorkout> = emptyList(),
    val savedWorkoutExercises: List<SavedWorkoutExercise> = emptyList(),
) {
    companion object {
        const val FORMAT = "stochastic-strength-backup"
        const val FORMAT_VERSION = 1
        const val DB_VERSION = 20
    }
}
```

- [ ] **Step 4: Extend `BackupJson`**

In `BackupJsonBuilder.build`, add two `.put` lines to `tables`:

```kotlin
            .put("savedWorkouts", JSONArray().apply { backup.savedWorkouts.forEach { put(savedWorkoutObj(it)) } })
            .put("savedWorkoutExercises", JSONArray().apply { backup.savedWorkoutExercises.forEach { put(savedWorkoutExerciseObj(it)) } })
```
and the two builders:

```kotlin
    private fun savedWorkoutObj(w: SavedWorkout) = obj("id" to w.id, "name" to w.name, "createdAt" to w.createdAt)

    private fun savedWorkoutExerciseObj(r: SavedWorkoutExercise) = obj(
        "id" to r.id, "workoutId" to r.workoutId, "exerciseId" to r.exerciseId,
        "position" to r.position, "reps" to r.reps,
    )
```

In `BackupJsonParser.parse`, inside the `WorkoutBackup(...)` construction add (use `optJSONArray` so old files parse):

```kotlin
                savedWorkouts = tables.optJSONArray("savedWorkouts")?.map { savedWorkout(it) } ?: emptyList(),
                savedWorkoutExercises = tables.optJSONArray("savedWorkoutExercises")?.map { savedWorkoutExercise(it) } ?: emptyList(),
```
and the parsers:

```kotlin
    private fun savedWorkout(o: JSONObject) = SavedWorkout(
        id = o.getLong("id"), name = o.getString("name"), createdAt = o.getLong("createdAt"),
    )

    private fun savedWorkoutExercise(o: JSONObject) = SavedWorkoutExercise(
        id = o.getLong("id"), workoutId = o.getLong("workoutId"), exerciseId = o.getLong("exerciseId"),
        position = o.getInt("position"), reps = o.intOrNull("reps"),
    )
```
Add the two model imports.

- [ ] **Step 5: Run the JSON test**

Run: `./gradlew :app:testDebugUnitTest --tests "io.github.fowles.stochastic_strength.domain.backup.BackupJsonTest"`
Expected: PASS.

- [ ] **Step 6: Write the failing manager test**

Append to `$T/domain/backup/BackupManagerTest.kt` (add imports for `SavedWorkout`, `SavedWorkoutExercise`):

```kotlin
    @Test
    fun export_includesSavedWorkouts_andDestructiveImportRestoresThem() = runBlocking {
        seed()
        val wid = db.savedWorkoutDao().insert(SavedWorkout(name = "Push", createdAt = 1))
        db.savedWorkoutDao().insertExerciseRows(listOf(
            SavedWorkoutExercise(workoutId = wid, exerciseId = 1, position = 0, reps = 5),
        ))
        val backup = manager.export()
        assertEquals(1, backup.savedWorkouts.size)
        assertEquals(1, backup.savedWorkoutExercises.size)

        db.savedWorkoutDao().deleteAllExerciseRows()
        db.savedWorkoutDao().deleteAll()
        manager.importDestructive(backup)
        assertEquals(backup.savedWorkouts, db.savedWorkoutDao().getAll())
        assertEquals(backup.savedWorkoutExercises, db.savedWorkoutDao().getAllExerciseRows())
    }

    @Test
    fun additiveImport_remapsSavedWorkoutExercisesByName_andSkipsSameName() = runBlocking {
        val localBench = db.exerciseDao().insert(Exercise(id = 0, name = "Bench Press",
            primaryMuscle = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        db.savedWorkoutDao().insert(SavedWorkout(name = "Already here", createdAt = 1))

        val backup = WorkoutBackup(
            formatVersion = WorkoutBackup.FORMAT_VERSION, dbVersion = WorkoutBackup.DB_VERSION,
            exportedAt = 0,
            exercises = listOf(
                Exercise(id = 5, name = "Bench Press", primaryMuscle = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
            ),
            knownLocations = emptyList(), locationExcludedExercises = emptyList(),
            workoutSessions = emptyList(), workoutSets = emptyList(), userProfile = emptyList(),
            baselineOverrides = emptyList(), exerciseHurtState = emptyList(),
            savedWorkouts = listOf(
                SavedWorkout(id = 9, name = "Already here", createdAt = 2),
                SavedWorkout(id = 10, name = "New one", createdAt = 3),
            ),
            savedWorkoutExercises = listOf(
                SavedWorkoutExercise(workoutId = 10, exerciseId = 5, position = 0, reps = 8),
                SavedWorkoutExercise(workoutId = 10, exerciseId = 999, position = 1, reps = null), // unresolvable
            ),
        )
        val result = manager.importAdditive(backup)
        assertEquals(1, result.savedWorkoutsAdded)
        val all = db.savedWorkoutDao().getAll()
        assertEquals(listOf("Already here", "New one"), all.map { it.name })
        val newId = all.first { it.name == "New one" }.id
        val rows = db.savedWorkoutDao().getExerciseRows(newId)
        assertEquals(1, rows.size)
        assertEquals(localBench, rows[0].exerciseId)
        assertEquals(8, rows[0].reps)
    }
```

- [ ] **Step 7: Extend `BackupManager`**

`AdditiveResult` gains a trailing `val savedWorkoutsAdded: Int = 0`.

In `export()` add:
```kotlin
            savedWorkouts = db.savedWorkoutDao().getAll(),
            savedWorkoutExercises = db.savedWorkoutDao().getAllExerciseRows(),
```

In `importDestructive`, add `db.savedWorkoutDao().deleteAllExerciseRows(); db.savedWorkoutDao().deleteAll()` to the delete block (before `deleteAll` on exercises) and, after the hurt-state inserts:
```kotlin
            backup.savedWorkouts.forEach { db.savedWorkoutDao().insert(it) }
            db.savedWorkoutDao().insertExerciseRows(backup.savedWorkoutExercises)
```

In `importAdditive`, add `var savedWorkoutsAdded = 0` and, inside the transaction after the session loop:

```kotlin
            val localWorkoutNames = db.savedWorkoutDao().getAll().map { it.name }.toMutableSet()
            val rowsByWorkout = backup.savedWorkoutExercises.groupBy { it.workoutId }
            for (workout in backup.savedWorkouts) {
                if (workout.name in localWorkoutNames) continue
                val newId = db.savedWorkoutDao().insert(workout.copy(id = 0))
                localWorkoutNames += workout.name
                savedWorkoutsAdded++
                val rows = rowsByWorkout[workout.id].orEmpty().sortedBy { it.position }
                    .mapNotNull { r ->
                        val exerciseId = resolveExerciseId(r.exerciseId) ?: return@mapNotNull null
                        r.copy(id = 0, workoutId = newId, exerciseId = exerciseId)
                    }
                    .mapIndexed { i, r -> r.copy(position = i) }
                db.savedWorkoutDao().insertExerciseRows(rows)
            }
```
and return `AdditiveResult(sessionsAdded, exercisesCreated, locationsCreated, setsSkipped, savedWorkoutsAdded)`.

In `$S/ui/history/HistoryViewModel.kt` L152, extend the message: after the existing `"Imported ${r.sessionsAdded} sessions (" ...` string, append `, ${r.savedWorkoutsAdded} saved workouts` inside the parenthetical (read the surrounding lines and keep its format).

- [ ] **Step 8: Run both backup test classes**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "io.github.fowles.stochastic_strength.domain.backup.*"
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.fowles.stochastic_strength.domain.backup.BackupManagerTest,io.github.fowles.stochastic_strength.domain.backup.BackupJsonTest
```
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
jj commit -m "feat: saved workouts in history backup export/import

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Shared UI: filter chips extraction, exercise picker sheet, name and saved-workout dialogs

**Files:**
- Create: `$S/ui/components/ExerciseFilterChips.kt`
- Create: `$S/ui/components/ExercisePickerSheet.kt`
- Create: `$S/ui/components/NameDialog.kt`
- Create: `$S/ui/components/SavedWorkoutPickerDialog.kt`
- Modify: `$S/ui/exercises/ExercisesScreen.kt` L295-334 (replace the two `LazyRow`s)

**Interfaces:**
- Produces:
  ```kotlin
  @Composable fun ExerciseFilterChips(selectedMuscle: MuscleGroup?, selectedEquipment: Equipment?, onMuscle: (MuscleGroup?) -> Unit, onEquipment: (Equipment?) -> Unit)
  @Composable fun ExercisePickerSheet(exercises: List<Exercise>, excludeIds: Set<Long>, onPick: (Long) -> Unit, onDismiss: () -> Unit)
  @Composable fun NameDialog(title: String, initial: String, confirmLabel: String = "Save", onConfirm: (String) -> Unit, onDismiss: () -> Unit)
  @Composable fun SavedWorkoutPickerDialog(title: String, workouts: List<SavedWorkoutDetail>, onPick: (Long) -> Unit, onDismiss: () -> Unit)
  ```
  No behavior tests (pure composables); verified by build and on-device.

- [ ] **Step 1: Extract the chips**

`$S/ui/components/ExerciseFilterChips.kt`:

```kotlin
package io.github.fowles.stochastic_strength.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import io.github.fowles.stochastic_strength.data.model.Equipment
import io.github.fowles.stochastic_strength.data.model.MuscleGroup

/** Two chip rows (muscle, equipment) shared by the exercise library and the exercise picker. */
@Composable
fun ExerciseFilterChips(
    selectedMuscle: MuscleGroup?,
    selectedEquipment: Equipment?,
    onMuscle: (MuscleGroup?) -> Unit,
    onEquipment: (Equipment?) -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Column {
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = selectedMuscle == null, onClick = { onMuscle(null) }, label = { Text("All") }) }
                items(MuscleGroup.entries) { muscle ->
                    FilterChip(selected = selectedMuscle == muscle, onClick = { onMuscle(muscle) }, label = { Text(muscle.displayName()) })
                }
            }
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = selectedEquipment == null, onClick = { onEquipment(null) }, label = { Text("All") }) }
                items(Equipment.entries) { equipment ->
                    FilterChip(selected = selectedEquipment == equipment, onClick = { onEquipment(equipment) }, label = { Text(equipment.displayName()) })
                }
            }
        }
    }
}

/** Pure filter used by both callers so the picker and the library agree. */
fun filterExercises(exercises: List<Exercise>, muscle: MuscleGroup?, equipment: Equipment?, query: String = ""): List<Exercise> =
    exercises
        .filter { muscle == null || it.primaryMuscle == muscle }
        .filter { equipment == null || it.equipment == equipment }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
```
Add `import io.github.fowles.stochastic_strength.data.model.Exercise`.

In `ExercisesScreen.kt`, replace the `CompositionLocalProvider { LazyRow ... LazyRow ... }` block (L295-334) with:

```kotlin
            ExerciseFilterChips(
                selectedMuscle = state.selectedFilter,
                selectedEquipment = state.selectedEquipmentFilter,
                onMuscle = viewModel::setFilter,
                onEquipment = viewModel::setEquipmentFilter,
            )
```
and replace the `filtered` computation (L264-274) with `filterExercises(state.exercises, state.selectedFilter, state.selectedEquipmentFilter)` inside the same `remember`. Remove now-unused imports (`LazyRow`, `FilterChip`, `LocalMinimumInteractiveComponentSize`, `CompositionLocalProvider`, `PaddingValues`, `Arrangement` if unused).

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, no unused-import warnings from `ExercisesScreen.kt`.

- [ ] **Step 3: Picker sheet**

`$S/ui/components/ExercisePickerSheet.kt`:

```kotlin
package io.github.fowles.stochastic_strength.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.fowles.stochastic_strength.data.model.Equipment
import io.github.fowles.stochastic_strength.data.model.Exercise
import io.github.fowles.stochastic_strength.data.model.MuscleGroup

/** Single-exercise picker. Exercises in [excludeIds] are hidden; disliked ones sort last but stay pickable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    exercises: List<Exercise>,
    excludeIds: Set<Long>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<MuscleGroup?>(null) }
    var equipment by remember { mutableStateOf<Equipment?>(null) }

    val visible = remember(exercises, excludeIds, query, muscle, equipment) {
        filterExercises(exercises.filter { it.id !in excludeIds }, muscle, equipment, query)
            .sortedWith(compareBy({ it.isDisliked }, { it.primaryMuscle.ordinal }, { it.name }))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxHeight(0.9f)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            ExerciseFilterChips(muscle, equipment, onMuscle = { muscle = it }, onEquipment = { equipment = it })
            HorizontalDivider()
            LazyColumn {
                items(visible, key = { it.id }) { exercise ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(exercise.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${exercise.primaryMuscle.displayName()} · ${exercise.equipment.displayName()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (exercise.isDisliked) {
                            Text("Disliked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}
```

- [ ] **Step 4: Name dialog and saved-workout picker dialog**

`$S/ui/components/NameDialog.kt`:

```kotlin
package io.github.fowles.stochastic_strength.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String = "Save",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim().ifEmpty { "Untitled workout" }) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

`$S/ui/components/SavedWorkoutPickerDialog.kt`:

```kotlin
package io.github.fowles.stochastic_strength.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutDetail

@Composable
fun SavedWorkoutPickerDialog(
    title: String,
    workouts: List<SavedWorkoutDetail>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (workouts.isEmpty()) {
                Text("No saved workouts yet. Create one from Home → Workouts.")
            } else {
                LazyColumn {
                    items(workouts, key = { it.id }) { w ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(w.id) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(w.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${w.entries.size} exercises",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
jj commit -m "refactor: shared exercise filter chips, picker sheet, name and saved-workout dialogs

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: Plan preview menu, dialogs, row flags, slider from target

**Files:**
- Create: `$S/ui/workout/PlanPreviewMenu.kt`
- Modify: `$S/ui/workout/WorkoutViewModel.kt` (delegations, saved-workout flow, message)
- Modify: `$S/ui/workout/WorkoutScreen.kt` (L406-441 PlanPreview branch, Scaffold snackbar)
- Modify: `$S/ui/workout/PlanPreviewContent.kt` (signature L62-74, slider L79/L118-127, header L89-95, row L210-318)

**Interfaces:**
- Consumes: controller API (Task 4), `ExercisePickerSheet`, `NameDialog`, `SavedWorkoutPickerDialog` (Task 6), `WorkoutRepository.observeSavedWorkouts`, `observeAllExercises`.
- Produces on `WorkoutViewModel`:
  ```kotlin
  val savedWorkouts: StateFlow<List<SavedWorkoutDetail>>
  val allExercises: StateFlow<List<Exercise>>
  val message: StateFlow<String?>
  fun clearMessage()
  fun addExercise(exerciseId: Long); fun loadSavedWorkout(id: Long); fun appendSavedWorkout(id: Long)
  fun saveCurrentPlan(name: String)
  ```
  `PlanPreviewContent` gains parameters `onAddExercise: () -> Unit, onLoadWorkout: () -> Unit, onAppendWorkout: () -> Unit, onSaveWorkout: () -> Unit, hasSavedWorkouts: Boolean`.

- [ ] **Step 1: ViewModel**

In `$S/ui/workout/WorkoutViewModel.kt` add imports (`Exercise`, `SavedWorkoutDetail`, `SharingStarted`, `stateIn`) and:

```kotlin
    val savedWorkouts: StateFlow<List<SavedWorkoutDetail>> = app.workoutRepository.observeSavedWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allExercises: StateFlow<List<Exercise>> = app.workoutRepository.observeAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    fun addExercise(exerciseId: Long) = controller.addExercise(exerciseId)
    fun loadSavedWorkout(id: Long) = controller.loadSavedWorkout(id)
    fun appendSavedWorkout(id: Long) = controller.appendSavedWorkout(id)

    fun saveCurrentPlan(name: String) {
        viewModelScope.launch {
            val id = controller.saveCurrentPlan(name)
            _message.value = if (id != null) "Saved \"$name\"" else "Nothing to save"
        }
    }
```

- [ ] **Step 2: Menu composable**

`$S/ui/workout/PlanPreviewMenu.kt`:

```kotlin
package io.github.fowles.stochastic_strength.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun PlanPreviewMenu(
    hasSavedWorkouts: Boolean,
    onAddExercise: () -> Unit,
    onLoadWorkout: () -> Unit,
    onAppendWorkout: () -> Unit,
    onSaveWorkout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Workout options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Add an exercise...") }, onClick = { expanded = false; onAddExercise() })
            DropdownMenuItem(
                text = { Text("Load a workout...") },
                enabled = hasSavedWorkouts,
                onClick = { expanded = false; onLoadWorkout() },
            )
            DropdownMenuItem(
                text = { Text("Append a workout...") },
                enabled = hasSavedWorkouts,
                onClick = { expanded = false; onAppendWorkout() },
            )
            if (!hasSavedWorkouts) {
                DropdownMenuItem(
                    text = { Text("No saved workouts", style = MaterialTheme.typography.labelSmall) },
                    enabled = false,
                    onClick = {},
                )
            }
            DropdownMenuItem(text = { Text("Save as workout...") }, onClick = { expanded = false; onSaveWorkout() })
        }
    }
}
```

- [ ] **Step 3: `PlanPreviewContent` changes**

In `$S/ui/workout/PlanPreviewContent.kt`:

1. Add parameters to `PlanPreviewContent`: `hasSavedWorkouts: Boolean, onAddExercise: () -> Unit, onLoadWorkout: () -> Unit, onAppendWorkout: () -> Unit, onSaveWorkout: () -> Unit,` (after `onExerciseTap`).
2. Replace the unkeyed slider state with one keyed on the target:
   ```kotlin
   var sliderValue by remember(state.targetCount) { mutableFloatStateOf(state.targetCount.toFloat()) }
   ```
3. Replace the title `Text("Today's Workout", ...)` with a row carrying the menu:
   ```kotlin
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Today's Workout", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            PlanPreviewMenu(
                hasSavedWorkouts = hasSavedWorkouts,
                onAddExercise = onAddExercise,
                onLoadWorkout = onLoadWorkout,
                onAppendWorkout = onAppendWorkout,
                onSaveWorkout = onSaveWorkout,
            )
        }
   ```
4. Pass `flag = state.rowFlags[planned.exercise.id]` into `ExercisePreviewRow`; add parameter `flag: RowFlag?` to it and, inside the name `Column` just under the detail `Text`, render:
   ```kotlin
                    flag?.let {
                        Text(
                            when (it) {
                                RowFlag.NOT_AT_LOCATION -> "Not at this location"
                                RowFlag.TRAINED_RECENTLY -> "Trained recently"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
   ```

- [ ] **Step 4: `WorkoutScreen` wiring**

In the `is WorkoutState.PlanPreview ->` branch of `$S/ui/workout/WorkoutScreen.kt`, before `PlanPreviewContent(...)` add dialog state and collect flows:

```kotlin
                    val savedWorkouts by viewModel.savedWorkouts.collectAsState()
                    val allExercises by viewModel.allExercises.collectAsState()
                    var dialog by rememberSaveable { mutableStateOf<PreviewDialog?>(null) }
```

and pass the new parameters:

```kotlin
                        hasSavedWorkouts = savedWorkouts.isNotEmpty(),
                        onAddExercise = { dialog = PreviewDialog.ADD },
                        onLoadWorkout = { dialog = PreviewDialog.LOAD },
                        onAppendWorkout = { dialog = PreviewDialog.APPEND },
                        onSaveWorkout = { dialog = PreviewDialog.SAVE },
```

then, after `PlanPreviewContent(...)`, the dialogs:

```kotlin
                    when (dialog) {
                        PreviewDialog.ADD -> ExercisePickerSheet(
                            exercises = allExercises,
                            excludeIds = s.plan.exercises.map { it.exercise.id }.toSet(),
                            onPick = { id -> dialog = null; viewModel.addExercise(id) },
                            onDismiss = { dialog = null },
                        )
                        PreviewDialog.LOAD -> SavedWorkoutPickerDialog(
                            title = "Load a workout",
                            workouts = savedWorkouts,
                            onPick = { id ->
                                pendingLoadId = id
                                if (s.edited) dialog = PreviewDialog.CONFIRM_LOAD
                                else { dialog = null; viewModel.loadSavedWorkout(id) }
                            },
                            onDismiss = { dialog = null },
                        )
                        PreviewDialog.CONFIRM_LOAD -> AlertDialog(
                            onDismissRequest = { dialog = null },
                            title = { Text("Replace the current plan?") },
                            text = { Text("Your edits to this plan will be lost.") },
                            confirmButton = { TextButton(onClick = { dialog = null; pendingLoadId?.let(viewModel::loadSavedWorkout) }) { Text("Replace") } },
                            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
                        )
                        PreviewDialog.APPEND -> SavedWorkoutPickerDialog(
                            title = "Append a workout",
                            workouts = savedWorkouts,
                            onPick = { id -> dialog = null; viewModel.appendSavedWorkout(id) },
                            onDismiss = { dialog = null },
                        )
                        PreviewDialog.SAVE -> NameDialog(
                            title = "Save as workout",
                            initial = "Workout " + SimpleDateFormat("MMM d", Locale.getDefault()).format(Date()),
                            onConfirm = { name -> dialog = null; viewModel.saveCurrentPlan(name) },
                            onDismiss = { dialog = null },
                        )
                        null -> Unit
                    }
```

with `var pendingLoadId by rememberSaveable { mutableStateOf<Long?>(null) }` declared next to `dialog`, and this enum at file bottom:

```kotlin
private enum class PreviewDialog { ADD, LOAD, CONFIRM_LOAD, APPEND, SAVE }
```

Snackbar: add `val snackbarHostState = remember { SnackbarHostState() }`, `val message by viewModel.message.collectAsState()`, a `LaunchedEffect(message) { message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() } }`, and `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) })`. Add the needed imports (`AlertDialog`, `TextButton`, `SnackbarHost`, `SnackbarHostState`, `ExercisePickerSheet`, `NameDialog`, `SavedWorkoutPickerDialog`, `SimpleDateFormat`, `Date`, `Locale`, `rememberSaveable`, `mutableStateOf`).

- [ ] **Step 5: Build and run the controller test class again (it exercises `PlanPreview` fields the UI now reads)**

Run: `./gradlew :app:assembleDebug` then `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.fowles.stochastic_strength.ui.workout.WorkoutSessionControllerTest`
Expected: BUILD SUCCESSFUL; tests PASS.

- [ ] **Step 6: On-device smoke (emulator)**

Launch the app, start a workout, and verify: the "⋮" opens the four items; "Add an exercise..." shows the sheet and appends a row with a weight; the exercise-count slider stays where it was; swiping the added row away removes it without a restock; "Save as workout..." shows a snackbar. Report anything that doesn't match; do not fix silently.

- [ ] **Step 7: Commit**

```bash
jj commit -m "feat: plan preview menu: add exercise, load/append/save workout, row flags

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: Saved workouts screens, navigation, Home button

**Files:**
- Create: `$S/ui/savedworkouts/SavedWorkoutsViewModel.kt`, `SavedWorkoutsScreen.kt`, `SavedWorkoutEditViewModel.kt`, `SavedWorkoutEditScreen.kt`
- Modify: `$S/ui/AppNavigation.kt` (Home lambdas L34-40; add two routes)
- Modify: `$S/ui/home/HomeScreen.kt` (signature L40-47, `ReadyContent` L120-134 and its call L82-88)

**Interfaces:**
- Consumes: `WorkoutRepository.observeSavedWorkouts/getSavedWorkout/saveWorkout/deleteSavedWorkout/observeAllExercises`, `ExercisePickerSheet`, `NameDialog`.
- Produces routes `workouts` and `workout-edit/{workoutId}`; `HomeScreen(onWorkouts: () -> Unit)`.

- [ ] **Step 1: List ViewModel and screen**

`$S/ui/savedworkouts/SavedWorkoutsViewModel.kt`:

```kotlin
package io.github.fowles.stochastic_strength.ui.savedworkouts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fowles.stochastic_strength.StochasticStrengthApp
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedWorkoutsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StochasticStrengthApp).workoutRepository

    val workouts: StateFlow<List<SavedWorkoutDetail>?> = repository.observeSavedWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _createdId = MutableStateFlow<Long?>(null)
    val createdId: StateFlow<Long?> = _createdId.asStateFlow()

    fun createNew() {
        viewModelScope.launch { _createdId.value = repository.saveWorkout(null, "Untitled workout", emptyList()) }
    }

    fun consumeCreated() { _createdId.value = null }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteSavedWorkout(id) }
    }
}
```

`$S/ui/savedworkouts/SavedWorkoutsScreen.kt`:

```kotlin
package io.github.fowles.stochastic_strength.ui.savedworkouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fowles.stochastic_strength.ui.components.BackTopAppBar
import io.github.fowles.stochastic_strength.ui.components.LoadingBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedWorkoutsScreen(
    onWorkoutTap: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SavedWorkoutsViewModel = viewModel(),
) {
    val workouts by viewModel.workouts.collectAsState()
    val createdId by viewModel.createdId.collectAsState()

    LaunchedEffect(createdId) {
        createdId?.let { viewModel.consumeCreated(); onWorkoutTap(it) }
    }

    Scaffold(
        topBar = { BackTopAppBar(title = "Workouts", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::createNew) {
                Icon(Icons.Default.Add, contentDescription = "New workout")
            }
        },
    ) { paddingValues ->
        val list = workouts
        when {
            list == null -> LoadingBox(contentPadding = paddingValues)
            list.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No saved workouts yet.\nTap + to build one, or use \"Save as workout...\" on a plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { w ->
                    Card(onClick = { onWorkoutTap(w.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(w.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${w.entries.size} exercises",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.delete(w.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${w.name}")
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Editor ViewModel**

`$S/ui/savedworkouts/SavedWorkoutEditViewModel.kt`:

```kotlin
package io.github.fowles.stochastic_strength.ui.savedworkouts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.fowles.stochastic_strength.StochasticStrengthApp
import io.github.fowles.stochastic_strength.data.model.Exercise
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SavedWorkoutEditState(
    val loaded: Boolean = false,
    val name: String = "",
    val entries: List<SavedWorkoutEntry> = emptyList(),
)

class SavedWorkoutEditViewModel(
    application: Application,
    private val workoutId: Long,
) : AndroidViewModel(application) {
    private val repository = (application as StochasticStrengthApp).workoutRepository

    private val _state = MutableStateFlow(SavedWorkoutEditState())
    val state: StateFlow<SavedWorkoutEditState> = _state.asStateFlow()

    val allExercises: StateFlow<List<Exercise>> = repository.observeAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val detail = repository.getSavedWorkout(workoutId) ?: return@launch
            _state.value = SavedWorkoutEditState(loaded = true, name = detail.name, entries = detail.entries)
        }
    }

    fun setName(name: String) { _state.value = _state.value.copy(name = name) }

    fun addExercise(exerciseId: Long) {
        val exercise = allExercises.value.firstOrNull { it.id == exerciseId } ?: return
        if (_state.value.entries.any { it.exercise.id == exerciseId }) return
        _state.value = _state.value.copy(entries = _state.value.entries + SavedWorkoutEntry(exercise, null))
    }

    fun removeExercise(exerciseId: Long) {
        _state.value = _state.value.copy(entries = _state.value.entries.filterNot { it.exercise.id == exerciseId })
    }

    fun setReps(exerciseId: Long, reps: Int?) {
        _state.value = _state.value.copy(entries = _state.value.entries.map {
            if (it.exercise.id == exerciseId) it.copy(reps = reps) else it
        })
    }

    fun move(from: Int, to: Int) {
        val list = _state.value.entries.toMutableList()
        list.add(to, list.removeAt(from))
        _state.value = _state.value.copy(entries = list)
    }

    /** Persist on leaving the screen. Safe to call more than once. */
    fun save() {
        val s = _state.value
        if (!s.loaded) return
        viewModelScope.launch {
            repository.saveWorkout(workoutId, s.name.trim().ifEmpty { "Untitled workout" }, s.entries)
        }
    }

    companion object {
        fun factory(workoutId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] ?: error("No application")
                return SavedWorkoutEditViewModel(app, workoutId) as T
            }
        }
    }
}
```

- [ ] **Step 3: Editor screen**

`$S/ui/savedworkouts/SavedWorkoutEditScreen.kt`:

```kotlin
package io.github.fowles.stochastic_strength.ui.savedworkouts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fowles.stochastic_strength.domain.model.SavedWorkoutEntry
import io.github.fowles.stochastic_strength.ui.components.BackTopAppBar
import io.github.fowles.stochastic_strength.ui.components.ExercisePickerSheet
import io.github.fowles.stochastic_strength.ui.components.LoadingBox
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedWorkoutEditScreen(
    workoutId: Long,
    onBack: () -> Unit,
    viewModel: SavedWorkoutEditViewModel = viewModel(factory = SavedWorkoutEditViewModel.factory(workoutId)),
) {
    val state by viewModel.state.collectAsState()
    val allExercises by viewModel.allExercises.collectAsState()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var repsFor by rememberSaveable { mutableStateOf<Long?>(null) }

    val saveAndBack = { viewModel.save(); onBack() }
    BackHandler(onBack = saveAndBack)

    Scaffold(topBar = { BackTopAppBar(title = "Edit workout", onBack = saveAndBack) }) { paddingValues ->
        if (!state.loaded) {
            LoadingBox(contentPadding = paddingValues)
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Drag to reorder · swipe left to remove · tap to set reps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            HorizontalDivider()
            val lazyListState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                viewModel.move(from.index, to.index)
            }
            LazyColumn(state = lazyListState, modifier = Modifier.weight(1f)) {
                items(state.entries, key = { it.exercise.id }) { entry ->
                    ReorderableItem(reorderState, key = entry.exercise.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp, label = "dragElevation")
                        Column(modifier = Modifier.animateItem().graphicsLayer { shadowElevation = elevation.toPx() }) {
                            EntryRow(
                                entry = entry,
                                dragHandleModifier = Modifier.draggableHandle(),
                                onRemove = { viewModel.removeExercise(entry.exercise.id) },
                                onTap = { repsFor = entry.exercise.id },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
            Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Add exercise")
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            exercises = allExercises,
            excludeIds = state.entries.map { it.exercise.id }.toSet(),
            onPick = { id -> showPicker = false; viewModel.addExercise(id) },
            onDismiss = { showPicker = false },
        )
    }
    repsFor?.let { id ->
        val entry = state.entries.firstOrNull { it.exercise.id == id }
        if (entry != null) {
            RepsDialog(
                exerciseName = entry.exercise.name,
                initial = entry.reps,
                onConfirm = { reps -> viewModel.setReps(id, reps); repsFor = null },
                onDismiss = { repsFor = null },
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: SavedWorkoutEntry,
    dragHandleModifier: Modifier,
    onRemove: () -> Unit,
    onTap: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) { onRemove(); true } else false },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.padding(end = 24.dp))
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onTap)
                .padding(vertical = 12.dp),
        ) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragHandleModifier.padding(start = 4.dp, end = 8.dp).size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.exercise.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    entry.reps?.let { "$it reps" } ?: "Session default reps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RepsDialog(
    exerciseName: String,
    initial: Int?,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exerciseName) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(3) },
                label = { Text("Reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toIntOrNull()?.takeIf { it > 0 }) }) { Text("Set") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onConfirm(null) }) { Text("Use session default") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
```

- [ ] **Step 4: Navigation and Home**

In `$S/ui/AppNavigation.kt`: import `SavedWorkoutsScreen`, `SavedWorkoutEditScreen`; add `onWorkouts = { navController.navigate("workouts") },` to the `HomeScreen(...)` call; add routes:

```kotlin
        composable("workouts") {
            SavedWorkoutsScreen(
                onWorkoutTap = { id -> navController.navigate("workout-edit/$id") },
                onBack = { navController.popBackStackIfResumed() },
            )
        }
        composable(
            route = "workout-edit/{workoutId}",
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
        ) { backStackEntry ->
            SavedWorkoutEditScreen(
                workoutId = backStackEntry.arguments!!.getLong("workoutId"),
                onBack = { navController.popBackStackIfResumed() },
            )
        }
```

In `$S/ui/home/HomeScreen.kt`: add `onWorkouts: () -> Unit` to `HomeScreen` (after `onExercises`) and to `ReadyContent`; pass it through; in `ReadyContent` add between the Exercises and Locations buttons:

```kotlin
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onWorkouts, modifier = Modifier.fillMaxWidth()) {
            Text("Workouts")
        }
```

- [ ] **Step 5: Build and smoke on device**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. On the emulator: Home → Workouts → + creates "Untitled workout" and opens the editor; add two exercises, set reps on one, reorder, back; the list shows "2 exercises"; delete removes it. Then start a workout and "Load a workout..." shows the saved one and loads it in order with the pinned reps.

- [ ] **Step 6: Commit**

```bash
jj commit -m "feat: saved workouts list and editor screens; Home entry point

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: "Save as workout..." on the summary screen

**Files:**
- Modify: `$S/ui/summary/SummaryViewModel.kt`
- Modify: `$S/ui/summary/SummaryScreen.kt` (menu L94-105; Scaffold snackbar)

**Interfaces:**
- Consumes: `WorkoutRepository.saveSessionAsWorkout` (Task 2), `NameDialog` (Task 6).
- Produces: `SummaryViewModel.saveAsWorkout(name: String)`, `SummaryViewModel.message: StateFlow<String?>`, `clearMessage()`.

- [ ] **Step 1: ViewModel**

In `SummaryViewModel` add:

```kotlin
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    fun saveAsWorkout(name: String) {
        viewModelScope.launch {
            app.workoutRepository.saveSessionAsWorkout(sessionId, name)
            _message.value = "Saved \"$name\""
        }
    }
```
with imports `MutableStateFlow`, `asStateFlow`.

- [ ] **Step 2: Screen**

In `SummaryScreen`:
- add `var showSaveDialog by remember { mutableStateOf(false) }`, `val snackbarHostState = remember { SnackbarHostState() }`, `val message by viewModel.message.collectAsState()`, and `LaunchedEffect(message) { message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() } }`;
- add a second `DropdownMenuItem` after "Re-export to Strava":
  ```kotlin
                            DropdownMenuItem(
                                text = { Text("Save as workout...") },
                                onClick = { menuExpanded = false; showSaveDialog = true },
                            )
  ```
- `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = ...)`;
- after the `Scaffold`:
  ```kotlin
    if (showSaveDialog) {
        NameDialog(
            title = "Save as workout",
            initial = "Workout " + (dateLabel?.substringBefore(" ·") ?: ""),
            onConfirm = { name -> showSaveDialog = false; viewModel.saveAsWorkout(name) },
            onDismiss = { showSaveDialog = false },
        )
    }
  ```
- imports: `SnackbarHost`, `SnackbarHostState`, `NameDialog`.

- [ ] **Step 3: Build and smoke**

Run: `./gradlew :app:assembleDebug`. On device: History → a session → "⋮" → "Save as workout..." → the Workouts list shows it with the session's exercises and reps.

- [ ] **Step 4: Commit**

```bash
jj commit -m "feat: save a historical session as a workout from the summary menu

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10: Full verification and docs

**Files:**
- Modify: `CLAUDE.md` (Database paragraph: version 19 → 20; add a short "Saved workouts" note under Architecture)

- [ ] **Step 1: JVM suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS; `BeliefScoreTest` and `BeliefPolicyBacktestTest` green with unchanged pinned values.

- [ ] **Step 2: Instrumented suite**

Run: `./gradlew :app:connectedAndroidTest`
Expected: PASS. If the emulator is not running, say so and report which classes were run individually earlier.

- [ ] **Step 3: Lint**

Run: `./gradlew :app:lint`
Expected: no new errors; fix any unused-import warnings introduced by this plan.

- [ ] **Step 4: Update `CLAUDE.md`**

- Change `Room database (\`AppDatabase\`, version 19)` to `version 20`.
- After the "Location & equipment filtering" section add:

```markdown
### Saved workouts and explicit control

`saved_workout` / `saved_workout_exercise` (DB v20) hold user-authored workouts: ordered exercises with optional per-row reps. `WorkoutRepository` exposes `observeSavedWorkouts`, `saveWorkout`, `deleteSavedWorkout`, and `saveSessionAsWorkout`; the backup export includes both tables. On plan preview the "⋮" menu can add one exercise, load or append a saved workout, or save the plan. Explicit rows bypass the rested-muscle and location filters (`WorkoutPlanner.planExplicit`) and are flagged in the UI, never dropped. Plan rows carry no origin flag: the exercise-count slider is a **minimum** the controller maintains (restock on swipe-away only when the plan would fall below `targetCount`), and the rep-range slider reprices every row.
```

- [ ] **Step 5: Commit**

```bash
jj commit -m "docs: CLAUDE.md for saved workouts and DB v20

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```
