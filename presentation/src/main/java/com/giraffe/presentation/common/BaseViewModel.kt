package com.giraffe.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel<STATE : Any, EFFECT : Any>(
    initialState: STATE,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<STATE> = _state.asStateFlow()

    private val _effect = Channel<EFFECT>(Channel.BUFFERED)
    val effect: Flow<EFFECT> = _effect.receiveAsFlow()

    protected fun updateState(reduce: STATE.() -> STATE) {
        _state.value = _state.value.reduce()
    }

    protected fun sendEffect(effect: EFFECT) {
        viewModelScope.launch {
            _effect.send(effect)
        }
    }

    protected fun tryToExecute(
        action: suspend () -> Unit,
        onError: (Exception) -> EFFECT,
    ) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: Exception) {
                sendEffect(onError(e))
            }
        }
    }

    protected fun tryToExecuteAll(
        actions: List<suspend () -> Unit>,
        onError: (Exception) -> EFFECT,
    ) {
        viewModelScope.launch {
            try {
                actions.forEach { it() }
            } catch (e: Exception) {
                sendEffect(onError(e))
            }
        }
    }

    protected fun <T> tryToCollect(
        flow: Flow<T>,
        onEach: (T) -> Unit,
        onError: (Exception) -> EFFECT,
    ) {
        viewModelScope.launch {
            try {
                flow.collect { onEach(it) }
            } catch (e: Exception) {
                sendEffect(onError(e))
            }
        }
    }
}
