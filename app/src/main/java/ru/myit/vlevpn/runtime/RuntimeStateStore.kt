package ru.myit.vlevpn.runtime

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class RuntimeStateStore @Inject constructor() {
    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Idle)
    val state: StateFlow<RuntimeState> = _state

    fun update(state: RuntimeState) {
        _state.value = state
    }
}
