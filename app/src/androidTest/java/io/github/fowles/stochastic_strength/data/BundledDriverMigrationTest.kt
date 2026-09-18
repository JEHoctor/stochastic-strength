package io.github.fowles.stochastic_strength.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.fowles.stochastic_strength.data.model.SavedWorkout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every other instrumented test builds the database via `Room.inMemoryDatabaseBuilder`/
 * `Room.databaseBuilder` without `setDriver`, i.e. Room's SupportSQLite compat path. This test
 * is the only one that opens the database through [AppDatabase.configure], the production
 * configuration, which runs on the bundled SQLite driver the app ships with.
 */
@RunWith(AndroidJUnit4::class)
class BundledDriverMigrationTest {
    private val dbName = "bundled-driver-migration-test"
    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate19To20_underBundledDriver_thenReadV20Table() {
        helper.createDatabase(dbName, 19).use {
            it.execSQL(
                "INSERT INTO exercises (name, primaryMuscle, secondaryMuscles, equipment, isDisliked, " +
                    "isUnilateral, isAsymmetric, isTimed) VALUES ('Squat', 'QUADS', '', 'BARBELL', 0, 0, 0, 0)"
            )
        }

        val db = AppDatabase.configure(
            Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        ).build()
        runBlocking {
            assertTrue(db.savedWorkoutDao().getAll().isEmpty())
            val exercises = db.exerciseDao().getAll()
            assertEquals(listOf("Squat"), exercises.map { it.name })
        }
        db.close()
    }

    @Test
    fun withTransaction_underBundledDriver_commitsAndRollsBack() {
        val db = AppDatabase.configure(
            Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        ).build()

        runBlocking {
            db.withTransaction {
                db.savedWorkoutDao().insert(SavedWorkout(name = "t", createdAt = 1L))
            }
            assertEquals(1, db.savedWorkoutDao().getAll().size)

            runCatching {
                db.withTransaction {
                    db.savedWorkoutDao().insert(SavedWorkout(name = "u", createdAt = 2L))
                    error("rollback")
                }
            }
            assertEquals(1, db.savedWorkoutDao().getAll().size)
        }
        db.close()
    }
}
