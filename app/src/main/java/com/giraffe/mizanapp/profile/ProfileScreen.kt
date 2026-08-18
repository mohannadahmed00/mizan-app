package com.giraffe.mizanapp.profile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Stub pending T127 — renders nothing but a placeholder so the module compiles. */
@Composable
fun ProfileScreen(state: ProfileUiState, onEvent: (ProfileEvent) -> Unit, modifier: Modifier = Modifier) {
    Text("TODO(T127)", modifier = modifier.fillMaxSize())
}
