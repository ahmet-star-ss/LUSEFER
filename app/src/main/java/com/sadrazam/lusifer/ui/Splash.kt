package com.sadrazam.lusifer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.sadrazam.lusifer.R
import kotlinx.coroutines.delay

/** PANOPTİK posteri: ~1.2 sn sabit + 1 sn yavaş silinme. Bitince onDone çağrılır. */
@Composable
fun PanoptikSplash(onDone: () -> Unit) {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(1200)
        alpha.animateTo(0f, tween(1000))
        onDone()
    }
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.background(Color.Black)) {
        Image(
            painterResource(R.drawable.splash_panoptik), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Fit
        )
    }
}
