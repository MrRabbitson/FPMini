package lol.mrrabbitson.fpmini.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.ui.Avatar
import lol.mrrabbitson.fpmini.ui.Card
import lol.mrrabbitson.fpmini.ui.LoadBox
import lol.mrrabbitson.fpmini.ui.PrimaryButton
import lol.mrrabbitson.fpmini.ui.SectionLabel
import lol.mrrabbitson.fpmini.ui.TopBar
import lol.mrrabbitson.fpmini.ui.rememberLoad
import lol.mrrabbitson.fpmini.ui.theme.Ym

/** Профиль другого пользователя (покупателя) — внутри приложения. */
@Composable
fun UserScreen(
    repo: Repository,
    id: Int,
    onBack: () -> Unit,
    onChat: (node: String, name: String) -> Unit,
    onLot: (type: String, id: String) -> Unit,
) {
    val c = Ym.colors
    val scope = rememberCoroutineScope()
    val state = rememberLoad("user", id) { repo.profile(id) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(state.data?.user?.name ?: "Профиль", subtitle = "ID $id", onBack = onBack)
        LoadBox(state) { p ->
            LazyColumn(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item {
                    Card(Modifier.padding(12.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.user.avatarUrl, 52.dp, p.user.name)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.user.name, color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                p.user.status?.let {
                                    Text(it.replaceFirstChar { ch -> ch.uppercase() }, color = if (p.user.isOnline) c.online else c.textMuted, fontSize = 13.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    p.rating?.value?.let {
                                        Icon(Icons.Filled.Star, null, tint = c.star, modifier = Modifier.size(14.dp))
                                        Text(" $it  ·  ", color = c.text, fontSize = 13.sp)
                                    }
                                    Text(p.reviewsTotal ?: "Нет отзывов", color = c.textMuted, fontSize = 13.sp)
                                }
                            }
                        }
                        p.registered?.let {
                            HorizontalDivider(color = c.divider)
                            Text("На FunPay с ${it.lines().first()}", color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                        }
                        HorizontalDivider(color = c.divider)
                        PrimaryButton("Написать", Modifier.fillMaxWidth().padding(10.dp)) {
                            scope.launch { onChat(repo.nodeWith(id), p.user.name) }
                        }
                    }
                }
                p.sections.forEach { s ->
                    item(key = "s${s.title}") {
                        SectionLabel(s.title, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                    }
                    items(s.offers, key = { "o${s.title}${it.id}" }) { o ->
                        Row(Modifier.fillMaxWidth().clickable { onLot(o.type, o.id) }.padding(horizontal = 16.dp, vertical = 9.dp)) {
                            Text(o.description.ifBlank { "Без описания" }, color = c.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            Text(o.priceText, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 16.dp))
                    }
                }
                if (p.reviews.isNotEmpty()) {
                    item { SectionLabel("Последние отзывы", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
                    items(p.reviews.take(10)) { r ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(r.stars ?: 0) { Icon(Icons.Filled.Star, null, tint = c.star, modifier = Modifier.size(12.dp)) }
                                Spacer(Modifier.width(6.dp))
                                Text(listOfNotNull(r.date, r.detail).joinToString(" · "), color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (r.text.isNotBlank()) Text(r.text, color = c.text, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 16.dp))
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
