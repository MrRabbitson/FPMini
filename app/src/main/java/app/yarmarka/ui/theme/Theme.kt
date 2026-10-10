package app.yarmarka.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.yarmarka.data.ThemeMode
import app.yarmarka.data.ThemeSettings

/** Цвета приложения. Названия по смыслу, а не по Material, — так проще настраивать. */
@Immutable
data class YmColors(
    val isDark: Boolean,
    val bg: Color,          // фон страницы
    val section: Color,     // серый блок («Ваши игры», «Предложения»)
    val card: Color,        // карточки
    val field: Color,       // поле «Заплачу», кнопки-иконки
    val text: Color,
    val textMuted: Color,   // серый текст
    val label: Color,       // ПОДПИСИ КАПСОМ
    val divider: Color,
    val border: Color,
    val accent: Color,      // ссылки, кнопка «Купить»
    val onAccent: Color,
    val online: Color,
    val star: Color,
    val bar: Color,         // заполненная полоска рейтинга
)

fun buildColors(s: ThemeSettings, systemDark: Boolean): YmColors {
    val dark = when (s.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = Color(s.accent)
    val onAccent = if (accent.luminance() > 0.6f) Color(0xFF111111) else Color.White
    return if (!dark) YmColors(
        isDark = false,
        bg = Color.White,
        section = Color(0xFFF7F7F7),
        card = Color.White,
        field = Color(0xFFF2F2F2),
        text = Color(0xFF222222),
        textMuted = Color(0xFF9A9A9A),
        label = Color(0xFF555555),
        divider = Color(0xFFE6E6E6),
        border = Color(0xFFDDDDDD),
        accent = accent,
        onAccent = onAccent,
        online = Color(0xFF6CC417),
        star = Color(0xFFF7E21B),
        bar = Color(0xFF222222),
    ) else {
        val bg = if (s.amoled) Color.Black else Color(0xFF121316)
        YmColors(
            isDark = true,
            bg = bg,
            section = if (s.amoled) Color(0xFF0B0B0C) else Color(0xFF17181B),
            card = if (s.amoled) Color(0xFF111113) else Color(0xFF1C1D21),
            field = Color(0xFF26282D),
            text = Color(0xFFECECEC),
            textMuted = Color(0xFF8C8F96),
            label = Color(0xFFA9ACB3),
            divider = Color(0xFF2A2C31),
            border = Color(0xFF34363C),
            accent = accent,
            onAccent = onAccent,
            online = Color(0xFF7FD83A),
            star = Color(0xFFF7E21B),
            bar = Color(0xFFDDDDDD),
        )
    }
}

val LocalYm = staticCompositionLocalOf { buildColors(ThemeSettings(), false) }
val LocalRadius = staticCompositionLocalOf { 8.dp }

object Ym {
    val colors: YmColors @Composable get() = LocalYm.current
    val shape: Shape @Composable get() = RoundedCornerShape(LocalRadius.current)
}

@Composable
fun YmTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val c = buildColors(settings, isSystemInDarkTheme())
    val scheme = if (c.isDark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent,
        background = c.bg, onBackground = c.text,
        surface = c.bg, onSurface = c.text,
        surfaceVariant = c.field, onSurfaceVariant = c.textMuted,
        surfaceContainer = c.card, surfaceContainerHigh = c.card, surfaceContainerLow = c.bg,
        outline = c.border, outlineVariant = c.divider,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent,
        background = c.bg, onBackground = c.text,
        surface = c.bg, onSurface = c.text,
        surfaceVariant = c.field, onSurfaceVariant = c.textMuted,
        surfaceContainer = c.card, surfaceContainerHigh = c.card, surfaceContainerLow = c.bg,
        outline = c.border, outlineVariant = c.divider,
    )
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalYm provides c,
        LocalRadius provides settings.radius.dp,
        LocalDensity provides Density(density.density, density.fontScale * settings.fontScale),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
