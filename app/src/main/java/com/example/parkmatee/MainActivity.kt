package com.example.parkmatee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.parkmatee.ui.MainScreen
import com.example.parkmatee.ui.theme.ParkMateeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ParkMateeTheme {
                MainScreen()
            }
        }
    }
}