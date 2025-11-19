package com.abbas.smartsight.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

@Composable
fun UserAvatar(
    email: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    textStyle: TextStyle = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
) {
    val backgroundColor = remember(email) {
        generateColorFromString(email)
    }

    val initial = email.firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(SolidColor(backgroundColor))
        }

        Text(text = initial, style = textStyle, color = Color.White)
    }
}

private fun generateColorFromString(text: String): Color {
    val hash = text.fold(0) { acc, char -> char.code + acc * 37 } % 360
    return Color.hsl(
        hue = hash.absoluteValue.toFloat(),
        saturation = 0.6f,
        lightness = 0.5f
    )
}
