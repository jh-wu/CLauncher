package com.jhwu.clauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jhwu.clauncher.ui.HomeScreen
import com.jhwu.clauncher.ui.theme.CLauncherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CLauncherTheme {
                HomeScreen()
            }
        }
    }
}
