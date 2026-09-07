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
