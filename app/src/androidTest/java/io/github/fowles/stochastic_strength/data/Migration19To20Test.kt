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
