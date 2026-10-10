package app.yarmarka.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yarmarka.core.OfferPage
import app.yarmarka.core.Param
import app.yarmarka.core.PaymentMethod
import app.yarmarka.data.Account
import app.yarmarka.data.Repository
import app.yarmarka.ui.Avatar
import app.yarmarka.ui.CapsLabel
import app.yarmarka.ui.CornerCover
import app.yarmarka.ui.LoadBox
import app.yarmarka.ui.RatingBlock
import app.yarmarka.ui.ReviewsBlock
import app.yarmarka.ui.Stars
import app.yarmarka.ui.StatusText
import app.yarmarka.ui.YmButton
import app.yarmarka.ui.openUrl
import app.yarmarka.ui.rememberLoad
import app.yarmarka.ui.theme.Ym
import app.yarmarka.ui.ymBorder
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfferScreen(
    repo: Repository,
    account: Account,
    type: String,
    id: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onCategory: (String, Int) -> Unit,
    onUser: (Int) -> Unit,
    onLogin: () -> Unit,
) {
    val state = rememberLoad(type, id) { force -> repo.offer(type, id, force) }
    val url = "https://funpay.com/$type/offer?id=$id"
    val c = Ym.colors

    LoadBox(state, url) { page ->
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = { state.refresh() }, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).imePadding()) {
                CornerCover(
                    page.coverUrl, page.categoryTitle ?: "Назад", onBack,
                    onTitle = page.categoryRef?.let { r -> { onCategory(r.type, r.id) } },
                )
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Оформление заказа", color = c.text, fontWeight = FontWeight.Black,
                        fontSize = 34.sp, lineHeight = 38.sp,
                        modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
                    )
                    ParamsGrid(page.params)
                    Spacer(Modifier.height(28.dp))
                    page.seller?.let { SellerChat(page, repo, account, snackbar, onUser, onLogin) }
                    Spacer(Modifier.height(28.dp))
                    BuyForm(page, url)
                    page.rating?.let {
                        Spacer(Modifier.height(40.dp))
                        RatingBlock(it)
                    }
                    Spacer(Modifier.height(16.dp))
                    ReviewsBlock(page.reviews)
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

/** Параметры: короткие — по два в ряд, описания — на всю ширину. */
@Composable
private fun ParamsGrid(params: List<Param>) {
    val c = Ym.colors
    val blocks = remember(params) {
        val out = mutableListOf<List<Param>>()
        var pending: Param? = null
        params.forEach { p ->
            if (p.isWide) {
                pending?.let { out.add(listOf(it)); pending = null }
                out.add(listOf(p))
            } else if (pending == null) {
                pending = p
            } else {
                out.add(listOf(pending!!, p)); pending = null
            }
        }
        pending?.let { out.add(listOf(it)) }
        out
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        blocks.forEach { row ->
            if (row.size == 1 && row[0].isWide) {
                Column {
                    CapsLabel(row[0].label)
                    Spacer(Modifier.height(10.dp))
                    Text(row[0].value, color = c.text, fontSize = 17.sp, lineHeight = 25.sp)
                }
            } else {
                Row {
                    row.forEach { p ->
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            CapsLabel(p.label)
                            Spacer(Modifier.height(10.dp))
                            Text(p.value, color = c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SellerChat(
    page: OfferPage,
    repo: Repository,
    account: Account,
    snackbar: SnackbarHostState,
    onUser: (Int) -> Unit,
    onLogin: () -> Unit,
) {
    val seller = page.seller ?: return
    val c = Ym.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val sent = remember { mutableStateListOf<String>() }

    Column(Modifier.fillMaxWidth().clip(Ym.shape).ymBorder(Ym.shape, c.border).background(c.card)) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = seller.id != null) { seller.id?.let(onUser) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(seller.avatarUrl, 60.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(seller.name, color = c.text, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                StatusText(seller, 17)
            }
            Box(
                Modifier.size(52.dp).clip(Ym.shape).background(c.field)
                    .clickable { openUrl(ctx, "https://funpay.com/${page.categoryRef?.type ?: "lots"}/offer?id=${page.id}") },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Warning, "Пожаловаться на сайте", tint = c.accent) }
        }
        HorizontalDivider(color = c.divider)

        Column(
            Modifier.fillMaxWidth().heightIn(min = 280.dp).padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (sent.isEmpty()) {
                Stars(5, size = 36.dp, gap = 10.dp)
                page.rating?.countText?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = c.accent, fontSize = 17.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text("Напишите продавцу\nперед оплатой", color = c.textMuted, fontSize = 17.sp, textAlign = TextAlign.Center)
            } else {
                sent.forEach { m ->
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            m, color = c.onAccent, fontSize = 15.sp,
                            modifier = Modifier.clip(Ym.shape).background(c.accent).padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Ответ продавца придёт в чат FunPay", color = c.textMuted, fontSize = 14.sp)
            }
        }
        HorizontalDivider(color = c.divider)

        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("Написать...", color = c.textMuted, fontSize = 17.sp)
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    textStyle = TextStyle(color = c.text, fontSize = 17.sp),
                    cursorBrush = SolidColor(c.accent),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(c.field).clickable(enabled = !sending && text.isNotBlank()) {
                    if (!account.loggedIn) {
                        scope.launch {
                            val r = snackbar.showSnackbar("Чтобы писать продавцу, войдите в аккаунт", actionLabel = "Войти")
                            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) onLogin()
                        }
                        return@clickable
                    }
                    val sid = seller.id ?: return@clickable
                    val msg = text.trim()
                    sending = true
                    scope.launch {
                        try {
                            repo.sendMessage(sid, msg)
                            sent.add(msg)
                            text = ""
                        } catch (e: Exception) {
                            snackbar.showSnackbar(e.message ?: "Не отправилось")
                        } finally {
                            sending = false
                        }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.ArrowForward, "Отправить", tint = c.accent, modifier = Modifier.size(30.dp))
            }
        }
    }
}

private fun fmt(v: Double): String {
    val s = if (v >= 100) String.format(Locale.US, "%.2f", v) else String.format(Locale.US, "%.3f", v)
    return s.trimEnd('0').trimEnd('.')
}

@Composable
private fun BuyForm(page: OfferPage, url: String) {
    val c = Ym.colors
    val ctx = LocalContext.current
    var selected by remember(page.id) { mutableStateOf<PaymentMethod?>(null) }
    var menu by remember { mutableStateOf(false) }
    val minPrice = page.methods.mapNotNull { it.unitPrice }.minOrNull()
    val unitPrice = selected?.unitPrice ?: minPrice
    val unit = page.unitLabel
    var qty by rememberSaveable(page.id) { mutableStateOf("") }
    var pay by rememberSaveable(page.id) { mutableStateOf("") }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CapsLabel("Способ оплаты", Modifier.weight(1f))
            if (unit != null) CapsLabel("за 1 $unit")
        }
        Spacer(Modifier.height(12.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(Ym.shape).ymBorder(Ym.shape, c.border).background(c.card)
                    .clickable(enabled = page.methods.isNotEmpty()) { menu = true }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected?.title ?: "Не выбран", color = c.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                val priceLabel = selected?.priceText ?: minPrice?.let { "от ${fmt(it)} ₽" }
                priceLabel?.let { Text(it, color = c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                Icon(Icons.Filled.ArrowDropDown, null, tint = c.text)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                page.methods.forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Row(Modifier.fillMaxWidth()) {
                                Text(m.title, modifier = Modifier.weight(1f))
                                m.priceText?.let { Text(it, fontWeight = FontWeight.Bold) }
                            }
                        },
                        onClick = {
                            selected = m; menu = false
                            val p = m.unitPrice; val q = qty.toDoubleOrNull()
                            if (p != null && q != null) pay = fmt(p * q)
                        },
                    )
                }
            }
        }

        if (unit != null) {
            Spacer(Modifier.height(20.dp))
            Row {
                CapsLabel("Получу", Modifier.weight(1f))
                Spacer(Modifier.width(36.dp))
                CapsLabel("Заплачу", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmountField(qty, suffix = unit, filled = false, modifier = Modifier.weight(1f)) { v ->
                    qty = v
                    val q = v.replace(',', '.').toDoubleOrNull()
                    pay = if (q != null && unitPrice != null) fmt(q * unitPrice) else ""
                }
                Text("⇄", color = c.text, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                AmountField(pay, suffix = "₽", filled = true, modifier = Modifier.weight(1f)) { v ->
                    pay = v
                    val p = v.replace(',', '.').toDoubleOrNull()
                    qty = if (p != null && unitPrice != null && unitPrice > 0) (p / unitPrice).toLong().toString() else ""
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        YmButton("Купить", Modifier.fillMaxWidth()) { openUrl(ctx, url) }
        Spacer(Modifier.height(10.dp))
        Text(
            "Оплата проходит на сайте FunPay. Продавец получит деньги только после того, как вы подтвердите выполнение заказа.",
            color = c.textMuted, fontSize = 16.sp, lineHeight = 23.sp,
        )
    }
}

@Composable
private fun AmountField(value: String, suffix: String, filled: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    val c = Ym.colors
    Row(
        modifier.clip(Ym.shape).ymBorder(Ym.shape, c.border).background(if (filled) c.field else c.card)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(suffix, color = c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
