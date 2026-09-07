package com.infinity.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinity.ai.ui.theme.Blue500
import com.infinity.ai.ui.theme.SuccessGreen
import com.infinity.ai.ui.theme.TextPrimary
import com.infinity.ai.ui.theme.TextPrimaryLight

/**
 * G-ONE Dual-Loop Infinity Emblem
 *
 * Inspired by the continuous O-G infinity mark:
 * - Left loop forms an 'O' (symbolizing 1 / unit / continuity)
 * - Right loop forms a geometric 'G' (symbolizing G-ONE)
 * - Gradient transitions from Infinity Blue (#4F8CFF) to Emerald Green (#10B981)
 */
@Composable
fun GoneEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    startColor: Color = Blue500,
    endColor: Color = SuccessGreen,
    tint: Color? = null
) {
    val height = size
    val width = size * 1.65f
    val effectiveStart = tint ?: startColor
    val effectiveEnd = tint ?: endColor

    Canvas(modifier = modifier.size(width = width, height = height)) {
        val w = size.toPx() * 1.65f
        val h = size.toPx()
        val r = h * 0.40f
        val strokeW = h * 0.18f

        val leftCenter = Offset(h * 0.42f, h / 2f)
        val rightCenter = Offset(w - (h * 0.42f), h / 2f)

        val gradient = Brush.horizontalGradient(
            colors = listOf(effectiveStart, effectiveEnd),
            startX = 0f,
            endX = w
        )

        // ── 1. Left 'O' loop ───────────────────────────────────────────────────
        drawCircle(
            brush = gradient,
            radius = r,
            center = leftCenter,
            style = Stroke(width = strokeW)
        )

        // ── 2. Right 'G' outer arc ──────────────────────────────────────────────
        val gRect = Rect(
            rightCenter.x - r,
            rightCenter.y - r,
            rightCenter.x + r,
            rightCenter.y + r
        )

        val gPath = Path().apply {
            addArc(
                oval = gRect,
                startAngleDegrees = -35f,
                sweepAngleDegrees = 305f
            )
        }

        drawPath(
            path = gPath,
            brush = gradient,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // ── 3. Right 'G' horizontal bar ─────────────────────────────────────────
        val barPath = Path().apply {
            moveTo(rightCenter.x, rightCenter.y)
            lineTo(rightCenter.x + (r * 0.85f), rightCenter.y)
        }

        drawPath(
            path = barPath,
            brush = gradient,
            style = Stroke(width = strokeW, cap = StrokeCap.Square)
        )
    }
}

/**
 * G-ONE Clean Geometric Wordmark
 */
@Composable
fun GoneWordmark(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 3.sp
) {
    Text(
        text = "G-ONE",
        style = MaterialTheme.typography.titleMedium,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = letterSpacing,
        color = if (isDarkTheme) TextPrimary else TextPrimaryLight,
        modifier = modifier
    )
}

/**
 * Combined G-ONE Logo + Wordmark Header
 */
@Composable
fun GoneLogo(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    symbolSize: Dp = 28.dp,
    showWordmark: Boolean = true,
    startColor: Color = Blue500,
    endColor: Color = SuccessGreen
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GoneEmblem(size = symbolSize, startColor = startColor, endColor = endColor)
        if (showWordmark) {
            GoneWordmark(isDarkTheme = isDarkTheme, fontSize = (symbolSize.value * 0.65f).sp)
        }
    }
}
