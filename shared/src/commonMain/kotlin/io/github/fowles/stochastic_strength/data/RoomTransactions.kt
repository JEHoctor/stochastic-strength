package io.github.fowles.stochastic_strength.data

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Same shape as Android's `androidx.room.withTransaction`, which is not available in common
 * code. DAO calls inside [block] join the transaction through the coroutine context, as they
 * joined the executor-backed transaction before. Import this instead of the Android one.
 */
suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
