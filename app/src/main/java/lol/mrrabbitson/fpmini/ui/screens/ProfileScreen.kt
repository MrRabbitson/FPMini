package lol.mrrabbitson.fpmini.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lol.mrrabbitson.fpmini.core.CategoryRef
import lol.mrrabbitson.fpmini.core.OfferRow
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.ui.Avatar
import lol.mrrabbitson.fpmini.ui.CapsLabel
import lol.mrrabbitson.fpmini.ui.LoadBox
import lol.mrrabbitson.fpmini.ui.OfferTable
import lol.mrrabbitson.fpmini.ui.OvalCover
import lol.mrrabbitson.fpmini.ui.RatingBlock
import lol.mrrabbitson.fpmini.ui.ReviewItem
import lol.mrrabbitson.fpmini.ui.StatusText
import lol.mrrabbitson.fpmini.ui.rememberLoad
import lol.mrrabbitson.fpmini.ui.theme.Ym
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repo: Repository,
    id: Int,
    onBack: (() -> Unit)?,
    onCategory: (CategoryRef) -> Unit,
    onOffer: (OfferRow) -> Unit,
) {
    val state = rememberLoad("user", id) { force -> repo.profile(id, force) }
    val c = Ym.colors

    LoadBox(state, "https://funpay.com/users/$id/") { p ->
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        var reviewsY by remember { mutableFloatStateOf(0f) }

        PullToRefreshBox(isRefreshing = state.loading, onRefresh = { state.refresh() }, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(scroll)) {
                OvalCover(p.coverUrl, 260.dp) {
                    if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
                    }
                    Avatar(
                        p.user.avatarUrl, 120.dp,
                        Modifier.align(Alignment.BottomStart).padding(start = 16.dp)
                            .border(4.dp, c.bg, CircleShape).clip(CircleShape),
                    )
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(12.dp))
                    Text(p.user.name, color = c.text, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 44.sp)
                    StatusText(p.user, 18)

                    p.registered?.let {
                        Spacer(Modifier.height(28.dp))
                        CapsLabel("Дата регистрации")
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = c.text, fontSize = 17.sp, lineHeight = 24.sp)
                    }
                    p.rating?.let { r ->
                        Spacer(Modifier.height(24.dp))
                        RatingBlock(r.copy(countText = p.reviewsTotal ?: r.countText), countAsLink = true) {
                            scope.launch { scroll.animateScrollTo(reviewsY.toInt()) }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }

                if (p.sections.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().background(c.section).padding(16.dp)) {
                        CapsLabel("Предложения", Modifier.padding(top = 12.dp, bottom = 16.dp))
                        p.sections.forEach { s ->
                            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(Ym.shape).background(c.card).padding(horizontal = 12.dp)) {
                                Text(
                                    s.title, color = c.accent, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 28.sp,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 18.dp, start = 2.dp)
                                        .clickable(enabled = s.ref != null) { s.ref?.let(onCategory) },
                                )
                                OfferTable(s.offers, showSeller = false, onRow = onOffer)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                if (p.reviews.isNotEmpty()) {
                    Column(
                        Modifier.padding(16.dp).onGloballyPositioned { reviewsY = it.positionInParent().y },
                    ) {
                        CapsLabel("Отзывы", Modifier.padding(top = 12.dp, bottom = 8.dp))
                        p.reviews.forEach { r ->
                            HorizontalDivider(color = c.divider)
                            ReviewItem(r)
                        }
                    }
                }
                if (p.sections.isEmpty() && p.reviews.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("У пользователя пока нет предложений", color = c.textMuted)
                    }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
