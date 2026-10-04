package com.sadrazam.lusifer

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sadrazam.lusifer.ui.AppRoot
import com.sadrazam.lusifer.ui.Neon
import com.sadrazam.lusifer.ui.PanoptikSplash

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Neon, onPrimary = Color.Black,
                    background = Color.Black, surface = Color.Black, onSurface = Neon
                )
            ) {
                var splash by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    AppRoot()
                    // Her açılışta ~2 sn görünür, yavaşça silinir, altındaki sistem aktif olur
                    if (splash) PanoptikSplash { splash = false }
                }
            }
        }
    }
}
