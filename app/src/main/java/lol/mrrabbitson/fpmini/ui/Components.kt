package lol.mrrabbitson.fpmini.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import lol.mrrabbitson.fpmini.ui.theme.Ym

// ---------- шапка ----------

@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Ym.colors
    Column(Modifier.background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = if (onBack == null) 16.dp else 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = c.text)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions()
        }
        HorizontalDivider(color = c.divider)
    }
}

// ---------- мелочи ----------

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Ym.colors.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
}

const val DEFAULT_AVATAR = "https://funpay.com/img/layout/avatar.png"

@Composable
fun Avatar(url: String?, size: Dp, name: String? = null, modifier: Modifier = Modifier) {
    val c = Ym.colors
    Box(modifier.size(size).clip(CircleShape).background(c.field), contentAlignment = Alignment.Center) {
        // нет своей аватарки — стандартная аватарка FunPay (буква видна только пока картинка грузится)
        val letter = name?.firstOrNull()?.uppercaseChar()
        if (letter != null) Text(letter.toString(), color = c.textMuted, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        AsyncImage(
            url?.ifBlank { null } ?: DEFAULT_AVATAR, null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Ym.colors
    Column(
        modifier.fillMaxWidth().clip(Ym.shape).background(c.card).border(1.dp, c.divider, Ym.shape),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Ym.colors
    Box(
        modifier.clip(Ym.shape)
            .background(if (enabled) c.accent else c.accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c.onAccent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Ym.colors
    Box(
        modifier.clip(Ym.shape).background(c.field)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) c.text else c.textMuted, fontWeight = FontWeight.Medium, fontSize = 14.sp) }
}

@Composable
fun EmptyState(title: String, hint: String? = null) {
    val c = Ym.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(hint, color = c.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

// ---------- загрузка ----------

@Composable
fun <T> LoadBox(state: LoadState<T>, content: @Composable (T) -> Unit) {
    val data = state.data
    when {
        data != null -> content(data)
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Ym.colors.accent, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
        }
        else -> ErrorBox(state.error?.message) { state.retry() }
    }
}

@Composable
fun ErrorBox(message: String?, onRetry: () -> Unit) {
    val c = Ym.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Не загрузилось", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(message ?: "Неизвестная ошибка", color = c.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Повторить", onClick = onRetry)
    }
}

/** Строка списка с аватаром слева. */
@Composable
fun ListRow(
    onClick: (() -> Unit)?,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f), content = content)
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Переключатель: в выключенном состоянии — серый ползунок на светлой дорожке с рамкой. */
@Composable
fun YmSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Ym.colors
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = modifier,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor = c.onAccent,
            checkedTrackColor = c.accent,
            checkedBorderColor = c.accent,
            uncheckedThumbColor = c.textMuted,
            uncheckedTrackColor = c.field,
            uncheckedBorderColor = c.textMuted.copy(alpha = 0.6f),
        ),
    )
}

/** Картинка на весь экран (из чата), без перехода в браузер. */
@Composable
fun ImageViewer(url: String, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(url, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Пара «подпись — значение» для страниц заказа и лота. */
@Composable
fun ParamRow(label: String, value: String) {
    val c = Ym.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
        Text(label, color = c.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = c.text, fontSize = 14.sp, lineHeight = 19.sp)
    }
}
