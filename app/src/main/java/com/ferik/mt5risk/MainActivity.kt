package com.ferik.mt5risk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ferik.mt5risk.ui.MainScreens
import com.ferik.mt5risk.ui.Palette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Always dark, whatever the phone's theme is set to: one palette, tested for contrast.
            MaterialTheme(colorScheme = Palette.colorScheme) {
                Surface(color = Palette.color(Palette.SCREEN), contentColor = Palette.color(Palette.TEXT)) {
                    MainScreens()
                }
            }
        }
    }
}
