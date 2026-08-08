package com.giraffe.mizanapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.giraffe.mizanapp.ui.theme.MizanAppTheme
import com.giraffe.presentation.common.MizanNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MizanAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MizanNavHost()
                }
            }
        }
    }
}
