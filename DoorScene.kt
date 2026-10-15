package ir.haditak.portknock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos

enum class Phase { Idle, Running, Success, Fail }

@Composable
fun DoorScene(phase: Phase, step: Int, modifier: Modifier = Modifier) {
    val open by animateFloatAsState(
        targetValue = if (phase == Phase.Success) 1f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "open"
    )
    val doorColor by animateColorAsState(
        targetValue = when (phase) {
            Phase.Success -> Color(0xFF2E7D32)
            Phase.Fail -> Color(0xFF8E2A2A)
            else -> Color(0xFF6D4C41)
        },
        animationSpec = tween(500),
        label = "doorColor"
    )

    // Door vibrates each time a knock is sent
    val shake = remember { Animatable(0f) }
    LaunchedEffect(step) {
        if (step >= 0) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(70))
            shake.animateTo(0f, spring(dampingRatio = 0.35f))
        }
    }

    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
        label = "blinkValue"
    )
    val eyesVisible = phase == Phase.Running || phase == Phase.Fail
    val eyesClosed = blink > 0.93f

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val doorH = h * 0.92f
        val doorW = minOf(w * 0.55f, doorH * 0.55f)
        val left = (w - doorW) / 2f
        val top = (h - doorH) / 2f

        // frame
        drawRoundRect(
            color = Color(0xFF3E2F28),
            topLeft = Offset(left - 12f, top - 12f),
            size = Size(doorW + 24f, doorH + 12f),
            cornerRadius = CornerRadius(10f)
        )

        // room behind the door (turns green when open)
        val dark = Color(0xFF0E0E12)
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    lerp(dark, Color(0xFF43A047), open),
                    lerp(dark, Color(0xFFA5D6A7), open)
                ),
                startY = top,
                endY = top + doorH
            ),
            topLeft = Offset(left, top),
            size = Size(doorW, doorH)
        )

        if (open > 0.02f) {
            drawThief(left, top, doorW, doorH, open.coerceIn(0f, 1f))
        }

        // door panel, hinged on the left, swings open
        val wf = cos(open * 1.35f)
        val xr = left + doorW * wf
        val inset = doorH * 0.07f * open
        val dx = shake.value * 7f
        val panel = Path().apply {
            moveTo(left + dx, top)
            lineTo(xr + dx, top + inset)
            lineTo(xr + dx, top + doorH - inset)
            lineTo(left + dx, top + doorH)
            close()
        }
        drawPath(panel, doorColor)

        // peephole with the thief's eyes
        if (open < 0.15f) {
            val pr = doorW * 0.08f
            val px = left + doorW * wf * 0.5f + dx
            val py = top + doorH * 0.28f
            drawCircle(Color(0xFF111111), pr, Offset(px, py))
            if (eyesVisible && !eyesClosed) {
                drawCircle(Color.White, pr * 0.3f, Offset(px - pr * 0.4f, py))
                drawCircle(Color.White, pr * 0.3f, Offset(px + pr * 0.4f, py))
                drawCircle(Color.Black, pr * 0.13f, Offset(px - pr * 0.4f, py))
                drawCircle(Color.Black, pr * 0.13f, Offset(px + pr * 0.4f, py))
            }
        }

        // handle
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = doorW * 0.045f * wf.coerceAtLeast(0.3f),
            center = Offset(left + doorW * wf * 0.85f + dx, top + doorH * 0.55f)
        )
    }
}

private fun DrawScope.drawThief(left: Float, top: Float, dw: Float, dh: Float, a: Float) {
    val cx = left + dw / 2f
    val r = dw * 0.13f
    val headY = top + dh * 0.36f
    val body = Color(0xFF15151A).copy(alpha = a)

    // body with yellow stripes
    val stripe = Color(0xFFFFC107).copy(alpha = a)
    val bodyLeft = cx - r * 1.3f
    val bodyTop = headY + r * 1.1f
    val bodyW = r * 2.6f
    val bodyH = dh * 0.4f
    drawRoundRect(
        color = body,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(r * 0.8f)
    )
    val sh = r * 0.2f
    val bodyShape = Path().apply {
        addRoundRect(
            RoundRect(bodyLeft, bodyTop, bodyLeft + bodyW, bodyTop + bodyH, CornerRadius(r * 0.8f))
        )
    }
    clipPath(bodyShape) {
        var y = bodyTop + sh
        while (y < bodyTop + bodyH) {
            drawRect(stripe, Offset(bodyLeft, y), Size(bodyW, sh))
            y += sh * 2f
        }
    }
    // arms up, striped the same way
    val dash = PathEffect.dashPathEffect(floatArrayOf(sh, sh), 0f)
    listOf(-1f, 1f).forEach { side ->
        val from = Offset(cx + side * r * 1.2f, headY + r * 1.8f)
        val to = Offset(cx + side * r * 2.4f, headY - r * 0.9f)
        drawLine(body, from, to, strokeWidth = r * 0.55f, cap = StrokeCap.Round)
        drawLine(stripe, from, to, strokeWidth = r * 0.55f, cap = StrokeCap.Butt, pathEffect = dash)
    }
    // head
    drawCircle(Color(0xFFE0B48F).copy(alpha = a), r, Offset(cx, headY))
    // beanie
    drawArc(
        color = Color.Black.copy(alpha = a),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(cx - r, headY - r),
        size = Size(r * 2f, r * 2f)
    )
    // mask
    drawRect(
        Color.Black.copy(alpha = a),
        Offset(cx - r, headY - r * 0.35f),
        Size(r * 2f, r * 0.6f)
    )
    // eyes
    drawCircle(Color.White.copy(alpha = a), r * 0.14f, Offset(cx - r * 0.4f, headY - r * 0.05f))
    drawCircle(Color.White.copy(alpha = a), r * 0.14f, Offset(cx + r * 0.4f, headY - r * 0.05f))
}
