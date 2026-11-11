package crow.evilo.architecture

import kotlinx.coroutines.flow.StateFlow

/** Shared UI state contract; dispatch intents on the store's owning UI dispatcher. */
interface MviStore<State, Intent> {
    val state: StateFlow<State>
    fun dispatch(intent: Intent)
    suspend fun close()
}
