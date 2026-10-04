package com.sadrazam.lusifer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Siyah ekranda tek yeşil göz. Dinlerken açılır, konuşurken içinde ses çubukları oynar.
 */
@Composable
fun GreenEye(state: AssistantState, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val open by animateFloatAsState(
        when (state) {
            AssistantState.IDLE -> 0.04f
            AssistantState.WAKE -> 0.65f
            else -> 1f
        }, tween(650), label = "open"
    )
    val t = rememberInfiniteTransition(label = "eye")
    val phase by t.animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "bars"
    )

    Box(
        modifier.background(Color.Black).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth(0.84f).aspectRatio(2f)) {
            val w = size.width
            val h = size.height
            val cy = h / 2f
            val half = h * 0.48f * open
            val eye = Path().apply {
                moveTo(0f, cy)
                quadraticTo(w / 2f, cy - half * 2f, w, cy)
                quadraticTo(w / 2f, cy + half * 2f, 0f, cy)
                close()
            }
            listOf(20f to 0.05f, 12f to 0.09f, 6f to 0.16f).forEach { (wd, al) ->
                drawPath(eye, Neon.copy(alpha = al), style = Stroke(wd.dp.toPx(), join = StrokeJoin.Round))
            }
            drawPath(eye, Neon, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            val irisR = h * 0.40f
            val cx = w / 2f
            clipPath(eye) {
                drawCircle(NeonDim, irisR, Offset(cx, cy))
                drawCircle(Neon, irisR, Offset(cx, cy), style = Stroke(2.5.dp.toPx()))
                if (state == AssistantState.SPEAKING) {
                    val count = 9
                    val step = irisR * 2f / count
                    for (i in 0 until count) {
                        val bh = irisR * 0.85f * (0.25f + 0.75f * abs(sin(phase + i * 0.8f)))
                        val x = cx - irisR + (i + 0.5f) * step
                        drawLine(Neon, Offset(x, cy - bh), Offset(x, cy + bh),
                            strokeWidth = step * 0.5f, cap = StrokeCap.Round)
                    }
                } else {
                    drawCircle(Color.Black, irisR * 0.38f, Offset(cx, cy))
                    drawCircle(Neon.copy(alpha = 0.8f), irisR * 0.10f, Offset(cx - irisR * 0.12f, cy - irisR * 0.12f))
                }
            }
        }
    }
}
