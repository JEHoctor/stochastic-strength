package io.github.fowles.stochastic_strength.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope

// The Context-dependent construction path. Extension functions on the companion keep the
// call sites (`AppDatabase.getInstance(...)`, `AppDatabase.reset(...)`) unchanged.

@Volatile private var INSTANCE: AppDatabase? = null
private val LOCK = Any()

fun AppDatabase.Companion.getInstance(context: Context, scope: CoroutineScope): AppDatabase =
    INSTANCE ?: synchronized(LOCK) {
        INSTANCE ?: buildDatabase(context, scope).also { INSTANCE = it }
    }

fun AppDatabase.Companion.reset(context: Context, scope: CoroutineScope): AppDatabase {
    synchronized(LOCK) {
        INSTANCE?.close()
        INSTANCE = null
    }
    context.deleteDatabase("stochastic_strength.db")
    return getInstance(context, scope)
}

private fun buildDatabase(context: Context, scope: CoroutineScope): AppDatabase =
    AppDatabase.configure(
        Room.databaseBuilder(context, AppDatabase::class.java, "stochastic_strength.db"),
    ).build()
