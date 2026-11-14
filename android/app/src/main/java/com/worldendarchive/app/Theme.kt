package com.worldendarchive.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Same palette as the Reader / README: near-black, cyan + purple glow, glass cards. */
object C {
    val bg = Color(0xFF050505)
    val glass = Color.White.copy(alpha = 0.04f)
    val border = Color.White.copy(alpha = 0.07f)
    val cyan = Color(0xFF00F2FF)
    val purple = Color(0xFF7000FF)
    val text = Color.White
    val muted = Color(0xFFA0A0A0)
    val warnBg = Color(0x14FFAA00)
    val warnBorder = Color(0x59FFAA00)
    val warn = Color(0xFFFFCF70)
    val green = Color(0xFF4ADE80)
    val red = Color(0xFFFF6B6B)
}

val Mono = FontFamily.Monospace

@Composable
fun WeaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = C.bg, surface = C.bg, primary = C.cyan, secondary = C.purple,
            onBackground = C.text, onSurface = C.text, onPrimary = C.bg,
        ),
        typography = Typography(bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp, color = C.text)),
        content = content,
    )
}

/** The slowly breathing purple/cyan blobs behind everything (README's "liquid background"). */
@Composable
fun LiquidBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val s by t.animateFloat(1f, 1.15f, infiniteRepeatable(tween(10000, easing = EaseInOutSine), RepeatMode.Reverse), label = "s")
    Box(Modifier.fillMaxSize().background(C.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.7f * s
            drawCircle(Brush.radialGradient(listOf(C.purple.copy(alpha = 0.22f), Color.Transparent),
                Offset(size.width * 0.15f, size.height * 0.45f), r), r, Offset(size.width * 0.15f, size.height * 0.45f))
            drawCircle(Brush.radialGradient(listOf(C.cyan.copy(alpha = 0.16f), Color.Transparent),
                Offset(size.width * 0.9f, size.height * 0.18f), r), r, Offset(size.width * 0.9f, size.height * 0.18f))
        }
        content()
    }
}

@Composable
fun Glass(modifier: Modifier = Modifier, padding: Dp = 16.dp, borderColor: Color = C.border, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.background(C.glass, shape).border(1.dp, borderColor, shape).padding(padding), content = content)
}

@Composable
fun Label(text: String, color: Color = C.cyan) =
    Text(text, fontFamily = Mono, fontSize = 12.sp, color = color, letterSpacing = 0.5.sp)

@Composable
fun SectionTitle(text: String) =
    Text(text.uppercase(), fontFamily = Mono, fontSize = 13.sp, color = C.muted, letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))

@Composable
fun GradientTitle(text: String, size: Int = 34) {
    Text(
        text.uppercase(),
        style = TextStyle(
            brush = Brush.linearGradient(listOf(Color.White, C.muted)),
            fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp,
        ),
    )
}
