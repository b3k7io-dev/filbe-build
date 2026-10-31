package dev.filebe

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val Night = darkColorScheme(
    primary = Color(0xFF7DF4D1), secondary = Color(0xFFB5ABFF),
    background = Color(0xFF090D16), surface = Color(0xFF151D2C),
    onBackground = Color(0xFFE8EEF8), onSurface = Color(0xFFE8EEF8)
)
private val Day = lightColorScheme(
    primary = Color(0xFF006C58), secondary = Color(0xFF62519B),
    background = Color(0xFFF2F6FB), surface = Color(0xFFFDFEFF),
    onBackground = Color(0xFF142335), onSurface = Color(0xFF142335)
)
@Composable fun FilebeTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Night else Day, content = content)
}
fun Modifier.glass(): Modifier = this
    .clip(RoundedCornerShape(24.dp))
    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .13f), Color.White.copy(alpha = .035f))))
    .border(1.dp, Color.White.copy(alpha = .20f), RoundedCornerShape(24.dp))

@Composable fun LiquidBackground(content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.background)) {
        // Blur ONLY the background layer, never the foreground text/buttons.
        Box(Modifier.fillMaxSize().graphicsLayer {
            if (Build.VERSION.SDK_INT >= 31) renderEffect = BlurEffect(80f, 80f)
        }.background(Brush.radialGradient(listOf(colors.primary.copy(alpha = .24f), Color.Transparent), radius = 850f)))
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.Transparent, colors.secondary.copy(alpha = .12f)))))
        content()
    }
}
