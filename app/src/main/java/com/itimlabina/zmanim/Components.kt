package com.itimlabina.zmanim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** כרטיס נקי ואחיד לכל הממשק. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun SunIcon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier.size(24.dp)) {
        val c = center
        val m = size.minDimension
        drawCircle(color = tint, radius = m * 0.2f, center = c)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            drawLine(
                color = tint,
                start = Offset(c.x + dx * m * 0.32f, c.y + dy * m * 0.32f),
                end = Offset(c.x + dx * m * 0.46f, c.y + dy * m * 0.46f),
                strokeWidth = m * 0.08f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun ClockIcon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier = modifier.size(24.dp)) {
        val c = center
        val m = size.minDimension
        val w = m * 0.08f
        drawCircle(color = tint, radius = m * 0.42f, center = c, style = Stroke(width = w))
        drawLine(tint, c, Offset(c.x, c.y - m * 0.24f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, c, Offset(c.x + m * 0.17f, c.y + m * 0.1f), strokeWidth = w, cap = StrokeCap.Round)
    }
}
