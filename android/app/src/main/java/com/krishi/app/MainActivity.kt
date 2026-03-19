package com.krishi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.krishi.app.ui.KrishiMitraApp
import com.krishi.app.ui.theme.KrishiColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set status bar to dark green
        window.statusBarColor = android.graphics.Color.parseColor("#1B5E20")

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = KrishiColors.Background,
                ) {
                    KrishiMitraApp()
                }
            }
        }
    }
}
