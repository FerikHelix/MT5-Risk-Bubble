package com.ferik.mt5risk.balance

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Deterministic stand-in for a preferences DataStore. The real one swaps a temp file over the old
 * one on every write, which fails on Windows, so JVM tests of store logic use this instead.
 * Sharing one instance between two stores simulates a restart on the same storage.
 */
class InMemoryPreferencesDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
