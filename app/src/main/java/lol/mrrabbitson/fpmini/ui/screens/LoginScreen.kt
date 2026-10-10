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
 * Вход на настоящем сайте funpay.com во встроенном браузере.
 * Логин и пароль вводятся на странице FunPay, приложение их не видит —
 * после входа берём только куку golden_key.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onClose: () -> Unit, onGoldenKey: (String) -> Unit) {
    val c = Ym.colors
    var done by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Закрыть", tint = c.text) }
            Column {
                Text("Вход на FunPay", color = c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("funpay.com", color = c.textMuted, fontSize = 12.sp)
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    cm.setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            if (done) return
                            val cookies = cm.getCookie("https://funpay.com").orEmpty()
                            val key = Regex("(?:^|;\\s*)golden_key=([^;]+)").find(cookies)?.groupValues?.get(1)
                            if (!key.isNullOrBlank()) {
                                done = true
                                onGoldenKey(key)
                            }
                        }
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
            Text("неофициальный клиент FunPay для продавцов", color = c.textMuted, fontSize = 13.sp)
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
            androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
            Text(
                "Пароль вводится на странице FunPay — приложение его не видит. После входа сохраняется только ключ сессии, и только на этом телефоне.",
                color = c.textMuted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
