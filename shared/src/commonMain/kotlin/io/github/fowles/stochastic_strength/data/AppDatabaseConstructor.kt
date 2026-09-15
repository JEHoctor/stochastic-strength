package io.github.fowles.stochastic_strength.data

import androidx.room.RoomDatabaseConstructor

/** Lets iOS build the database without reflection. Room's KSP generates the actual. */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
