package lol.mrrabbitson.fpmini.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lol.mrrabbitson.fpmini.core.OfferRow
import lol.mrrabbitson.fpmini.core.Rating
import lol.mrrabbitson.fpmini.core.Review
import lol.mrrabbitson.fpmini.core.UserShort
import lol.mrrabbitson.fpmini.data.FpException
import lol.mrrabbitson.fpmini.ui.theme.Ym
import coil.compose.AsyncImage

// ---------- ссылки ----------

fun openUrl(context: Context, url: String) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ---------- мелочи ----------

@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Ym.colors.label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
    )
}

@Composable
fun Stars(count: Int = 5, size: Dp = 18.dp, gap: Dp = 2.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(count) {
            Icon(Icons.Filled.Star, null, tint = Ym.colors.star, modifier = Modifier.size(size))
        }
    }
}

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Ym.colors.field)
    ) {
        if (url != null) {
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun StatusText(user: UserShort, fontSize: Int = 16) {
    val s = user.status ?: return
    Text(
        s,
        color = if (user.isOnline) Ym.colors.online else Ym.colors.textMuted,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize.sp,
    )
}

/** Значок сортировки ⬍ (две стрелки), подсвечивается активное направление. */
@Composable
fun SortIcon(dir: Int) {
    val c = Ym.colors
    Canvas(Modifier.size(width = 12.dp, height = 16.dp)) {
        val w = size.width; val h = size.height
        val up = Path().apply { moveTo(w / 2, 0f); lineTo(w, h * 0.42f); lineTo(0f, h * 0.42f); close() }
        val down = Path().apply { moveTo(0f, h * 0.58f); lineTo(w, h * 0.58f); lineTo(w / 2, h); close() }
        drawPath(up, if (dir < 0) c.textMuted else c.text)
        drawPath(down, if (dir > 0) c.textMuted else c.text)
    }
}

// ---------- шапка ----------

@Composable
fun YmTopBar(onMenu: () -> Unit, onSearch: () -> Unit, onLogo: () -> Unit) {
    val c = Ym.colors
    Column(Modifier.background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo(Modifier.clickable(onClick = onLogo))
            Spacer(Modifier.width(16.dp))
            Row(
                Modifier.weight(1f).clickable(onClick = onSearch).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Поиск", color = c.textMuted, fontSize = 17.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Search, null, tint = c.textMuted, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Меню", tint = c.text, modifier = Modifier.size(30.dp)) }
        }
        HorizontalDivider(color = c.divider)
    }
}

@Composable
fun Logo(modifier: Modifier = Modifier) {
    val c = Ym.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
        Spacer(Modifier.width(6.dp))
        Text("FPMini", color = c.text, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 0.5.sp)
    }
}

// ---------- обложки с дугой ----------

/** Обложка раздела/лота: картинка, срезанная дугой снизу-справа. */
private val CornerArcShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.87f, 0f)
            cubicTo(w * 0.72f, h * 0.62f, w * 0.38f, h, 0f, h)
            close()
        }
        return Outline.Generic(p)
    }
}

/** Обложка профиля: нижняя часть большого овала. */
private val OvalArcShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        val p = Path().apply { addOval(Rect(Offset(w * 0.06f, -h), Offset(w * 0.94f, h))) }
        return Outline.Generic(p)
    }
}

@Composable
private fun CoverImage(url: String?, shape: Shape, modifier: Modifier) {
    val c = Ym.colors
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(c.accent, c.accent.copy(alpha = 0.55f), Color(0xFF1B1B22))))) {
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0x88000000)))))
    }
}

@Composable
fun CornerCover(url: String?, title: String?, onBack: () -> Unit, onTitle: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(200.dp)) {
        CoverImage(url, CornerArcShape, Modifier.fillMaxSize())
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 4.dp, end = 80.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White) }
            if (title != null) Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onTitle != null) Modifier.clickable(onClick = onTitle) else Modifier,
            )
        }
    }
}

@Composable
fun OvalCover(url: String?, height: Dp, overlay: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().height(height)) {
        CoverImage(url, OvalArcShape, Modifier.fillMaxWidth().height(height * 0.86f))
        overlay()
    }
}

// ---------- загрузка/ошибка ----------

@Composable
fun <T> LoadBox(state: LoadState<T>, browserUrl: String?, content: @Composable (T) -> Unit) {
    val data = state.data
    when {
        data != null -> content(data)
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Ym.colors.accent)
        }
        else -> ErrorBox(state.error, browserUrl) { state.retry() }
    }
}

@Composable
fun ErrorBox(error: Throwable?, browserUrl: String?, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    val c = Ym.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Не загрузилось", fontWeight = FontWeight.Black, fontSize = 22.sp, color = c.text)
        Spacer(Modifier.height(8.dp))
        Text(error?.message ?: "Неизвестная ошибка", color = c.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        YmButton("Повторить", onClick = onRetry)
        if (browserUrl != null && (error as? FpException)?.openInBrowser != false) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Открыть в браузере",
                color = c.accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { openUrl(ctx, browserUrl) }.padding(8.dp),
            )
        }
    }
}

@Composable
fun YmButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Ym.colors
    Box(
        modifier
            .clip(Ym.shape)
            .background(if (enabled) c.accent else c.accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = c.onAccent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

// ---------- таблица предложений ----------

@Composable
fun OfferTable(
    rows: List<OfferRow>,
    showSeller: Boolean,
    onRow: (OfferRow) -> Unit,
    onSeller: (Int) -> Unit = {},
) {
    val c = Ym.colors
    var sort by remember { mutableIntStateOf(0) } // 0 — как на сайте, 1 — дешевле, -1 — дороже
    val sorted = remember(rows, sort) {
        when (sort) {
            1 -> rows.sortedBy { it.priceValue ?: Double.MAX_VALUE }
            -1 -> rows.sortedByDescending { it.priceValue ?: -1.0 }
            else -> rows
        }
    }
    Column {
        HorizontalDivider(color = c.divider)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Описание", color = c.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Row(
                Modifier.clickable { sort = when (sort) { 0 -> 1; 1 -> -1; else -> 0 } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Цена", color = c.text, fontSize = 17.sp)
                Spacer(Modifier.width(6.dp))
                SortIcon(sort)
            }
        }
        sorted.forEach { row ->
            HorizontalDivider(color = c.divider)
            OfferRowItem(row, showSeller, onRow, onSeller)
        }
    }
}

@Composable
private fun OfferRowItem(row: OfferRow, showSeller: Boolean, onRow: (OfferRow) -> Unit, onSeller: (Int) -> Unit) {
    val c = Ym.colors
    Row(
        Modifier.fillMaxWidth().clickable { onRow(row) }.padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            val extra = listOfNotNull(row.server, row.amount).joinToString(" · ")
            Text(row.description.ifBlank { "Без описания" }, color = c.text, fontSize = 16.sp, lineHeight = 22.sp)
            if (extra.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(extra, color = c.textMuted, fontSize = 14.sp)
            }
            val s = row.seller
            if (showSeller && s != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = s.id != null) { s.id?.let(onSeller) },
                ) {
                    Avatar(s.avatarUrl, 22.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(s.name, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (s.isOnline) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(c.online))
                    }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(row.priceText, color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

// ---------- рейтинг ----------

@Composable
fun RatingBlock(rating: Rating, countAsLink: Boolean = false, onCount: () -> Unit = {}) {
    val c = Ym.colors
    Column {
        CapsLabel("Рейтинг продавца")
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(rating.value ?: "—", color = c.text, fontWeight = FontWeight.Black, fontSize = 84.sp, letterSpacing = (-3).sp)
                Spacer(Modifier.width(8.dp))
                Text("из 5", color = c.text, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(bottom = 18.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until 5) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(66.dp), contentAlignment = Alignment.CenterEnd) { Stars(5 - i, size = 12.dp, gap = 1.dp) }
                        Spacer(Modifier.width(6.dp))
                        val frac = rating.distribution.getOrNull(i) ?: 0f
                        Box(Modifier.width(70.dp).height(2.dp).background(c.divider)) {
                            Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.02f)).height(2.dp).background(c.bar))
                        }
                    }
                }
            }
        }
        rating.countText?.let {
            Spacer(Modifier.height(6.dp))
            if (countAsLink) Text(it, color = c.accent, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onCount))
            else Text(it, color = c.textMuted, fontSize = 16.sp)
        }
    }
}

// ---------- отзывы ----------

@Composable
fun ReviewsBlock(reviews: List<Review>) {
    if (reviews.isEmpty()) return
    val c = Ym.colors
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) reviews else reviews.take(3)
    Column {
        OutlinedButton(
            onClick = { expanded = !expanded },
            shape = Ym.shape,
            border = BorderStroke(1.dp, c.border),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (expanded) "Свернуть" else "Все отзывы", color = c.text, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Icon(Icons.Filled.ArrowDropDown, null, tint = c.text)
        }
        Spacer(Modifier.height(20.dp))
        shown.forEach { r ->
            HorizontalDivider(color = c.divider)
            ReviewItem(r)
        }
    }
}

@Composable
fun ReviewItem(r: Review) {
    val c = Ym.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
        Row {
            Avatar(r.authorAvatar, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                r.date?.let { Text(it, color = c.text, fontSize = 17.sp) }
                r.detail?.let { Text(it, color = c.textMuted, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                r.stars?.let { n ->
                    Spacer(Modifier.height(4.dp))
                    Stars(n, size = 22.dp)
                }
            }
        }
        if (r.text.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(r.text, color = c.text, fontSize = 17.sp)
        }
        r.answer?.let {
            Spacer(Modifier.height(14.dp))
            CapsLabel("Ответ продавца")
            Spacer(Modifier.height(4.dp))
            Text(it, color = c.text, fontSize = 15.sp)
        }
    }
}

/** Карточка с рамкой (чат с продавцом, поля формы). */
fun Modifier.ymBorder(shape: Shape, color: Color) = this.border(1.dp, color, shape)
