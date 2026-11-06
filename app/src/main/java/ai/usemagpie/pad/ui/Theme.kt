package ai.usemagpie.pad.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import kotlin.math.abs

val Green = Color(0xFF2EB67D)
val Amber = Color(0xFFE8A33D)
val Red = Color(0xFFE5484D)

@Composable
fun MagpieTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF8AB4F8))
        else -> lightColorScheme(primary = Color(0xFF1F5FBF))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun fmtTokens(n: Long): String = when {
    abs(n) >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", n / 1e9)
    abs(n) >= 1_000_000 -> String.format(Locale.US, "%.2fM", n / 1e6)
    abs(n) >= 1_000 -> String.format(Locale.US, "%.1fk", n / 1e3)
    else -> n.toString()
}

fun fmtCost(c: Double): String = if (c >= 100) String.format(Locale.US, "$%.0f", c) else String.format(Locale.US, "$%.2f", c)

/** "2026-11-05T10:00:00Z" → "还有 3 小时" */
fun fmtReset(iso: String?): String? {
    iso ?: return null
    val t = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return null
    val s = java.time.Duration.between(java.time.Instant.now(), t).seconds
    if (s <= 0) return "已重置"
    return when {
        s < 3600 -> "${s / 60} 分钟后重置"
        s < 86400 -> "${s / 3600} 小时 ${(s % 3600) / 60} 分后重置"
        else -> "${s / 86400} 天 ${(s % 86400) / 3600} 小时后重置"
    }
}
