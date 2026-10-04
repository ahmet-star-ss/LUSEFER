package com.sadrazam.lusifer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sadrazam.lusifer.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ses halkası: ortada profil fotoğrafı (olduğu gibi, filtresiz), etrafında neon yeşil dalga halkası.
 * Konuşurken büyüyüp küçülür, wake word (WAKE) anında parlar.
 */
@Composable
fun VoiceRing(state: AssistantState, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val t = rememberInfiniteTransition(label = "ring")
    val phase by t.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "phase"
    )
    val pulse by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "pulse"
    )
    val amp by animateFloatAsState(
        when (state) {
            AssistantState.IDLE -> 0.03f
            AssistantState.WAKE -> 0.10f
            AssistantState.LISTENING -> 0.17f
            AssistantState.THINKING -> 0.12f
            AssistantState.SPEAKING -> 0.34f
        }, tween(400), label = "amp"
    )
    val glow by animateFloatAsState(
        if (state == AssistantState.WAKE) 1f else 0.3f, tween(300), label = "glow"
    )

    Box(
        modifier.aspectRatio(1f).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        // Profil fotoğrafı: dokunulmadan, tam boyutuyla
        Image(
            painterResource(R.drawable.profile_photo), null,
            Modifier.fillMaxWidth(0.40f).aspectRatio(1f), contentScale = ContentScale.Fit
        )
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val base = size.minDimension * 0.34f
            val n = 160
            val wob = if (state == AssistantState.SPEAKING) 0.55f + 0.45f * pulse else 1f
            val path = Path()
            for (i in 0..n) {
                val a = (2 * PI * i / n).toFloat()
                val w = sin(3 * a + phase) * 0.5f + sin(5 * a - phase * 1.3f) * 0.3f + sin(8 * a + phase * 0.7f) * 0.2f
                val r = base * (1f + amp * wob * w)
                val x = c.x + r * cos(a)
                val y = c.y + r * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            listOf(22f to 0.06f, 14f to 0.10f, 8f to 0.16f).forEach { (wd, al) ->
                drawPath(path, Neon.copy(alpha = al * (0.4f + glow)),
                    style = Stroke(wd.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawPath(path, Neon, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
