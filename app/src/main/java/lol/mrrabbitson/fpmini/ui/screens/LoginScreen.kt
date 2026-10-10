package lol.mrrabbitson.fpmini.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import lol.mrrabbitson.fpmini.ui.theme.Ym

/**
 * Вход на сайте funpay.com во встроенном браузере (пароль и 2ФА вводятся на странице FunPay).
 * Раз в 3 секунды: если есть golden_key и мы уже не на страницах входа — проверяем, что FunPay
 * действительно открыл аккаунт. Пока 2ФА не пройдена, проверка не проходит, и окно остаётся открытым.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onClose: () -> Unit, verify: suspend (String) -> Boolean, onSuccess: () -> Unit) {
    val c = Ym.colors
    var currentUrl by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }

    fun onAuthPage(url: String): Boolean {
        val u = url.lowercase()
        return u.isBlank() || u.contains("/account/") || u.contains("login") || u.contains("2fa") ||
            u.contains("otp") || u.contains("verif") || u.contains("confirm") || u.contains("auth")
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3_000)
            if (onAuthPage(currentUrl)) continue
            val cm = CookieManager.getInstance()
            cm.flush()
            val key = Regex("(?:^|;\\s*)golden_key=([^;]+)").find(cm.getCookie("https://funpay.com").orEmpty())?.groupValues?.get(1)
            if (key.isNullOrBlank() || key.length < 16) continue
            checking = true
            val ok = verify(key)
            checking = false
            if (ok) { onSuccess(); break }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Закрыть", tint = c.text) }
            Text("Вход на FunPay", color = c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (checking) androidx.compose.material3.CircularProgressIndicator(
                Modifier.padding(end = 12.dp).size(18.dp), color = c.accent, strokeWidth = 2.dp,
            )
        }
        AndroidView(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding(),
            factory = { ctx ->
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    cm.setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) { currentUrl = url }
                    }
                    loadUrl("https://funpay.com/account/login")
                }
            },
        )
    }
}

/** Экран до входа: войти через сайт или вставить golden_key. */
@Composable
fun LoginGate(onWebLogin: () -> Unit, onKey: (String) -> Unit, busy: Boolean, error: String?) {
    val c = Ym.colors
    var showKey by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FPMini", color = c.text, fontWeight = FontWeight.Black, fontSize = 30.sp)
            Text("Клиент FunPay для продавцов", color = c.textMuted, fontSize = 13.sp)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
            lol.mrrabbitson.fpmini.ui.PrimaryButton(if (busy) "Проверяю вход…" else "Войти через funpay.com", Modifier.fillMaxWidth(), enabled = !busy, onClick = onWebLogin)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
            if (!showKey) {
                Text(
                    "Ввести golden_key вручную", color = c.accent, fontSize = 14.sp,
                    modifier = Modifier.clickable { showKey = true }.padding(8.dp),
                )
            } else {
                androidx.compose.material3.OutlinedTextField(
                    key, { key = it.trim() }, singleLine = true,
                    label = { Text("golden_key") }, modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                lol.mrrabbitson.fpmini.ui.SecondaryButton("Войти по ключу", Modifier.fillMaxWidth(), enabled = key.length >= 16 && !busy) { onKey(key) }
            }
            if (error != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                Text(error, color = androidx.compose.ui.graphics.Color(0xFFE5534B), fontSize = 13.sp)
            }
        }
    }
}
