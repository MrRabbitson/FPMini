package lol.mrrabbitson.fpmini

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import lol.mrrabbitson.fpmini.auto.AutomationService
import lol.mrrabbitson.fpmini.auto.Notifier
import lol.mrrabbitson.fpmini.ui.screens.AutomationScreen
import lol.mrrabbitson.fpmini.ui.screens.ChatScreen
import lol.mrrabbitson.fpmini.ui.screens.ChatsScreen
import lol.mrrabbitson.fpmini.ui.screens.LoginGate
import lol.mrrabbitson.fpmini.ui.screens.LoginScreen
import lol.mrrabbitson.fpmini.ui.screens.MyProfileScreen
import lol.mrrabbitson.fpmini.ui.screens.SalesScreen
import lol.mrrabbitson.fpmini.ui.screens.SettingsScreen
import lol.mrrabbitson.fpmini.ui.screens.SupportScreen
import lol.mrrabbitson.fpmini.ui.screens.TicketScreen
import lol.mrrabbitson.fpmini.ui.theme.Ym
import lol.mrrabbitson.fpmini.ui.theme.YmTheme

class MainActivity : ComponentActivity() {
    private val pendingIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App
        pendingIntent.value = intent
        AutomationService.sync(this)
        setContent {
            val theme by app.prefs.theme.collectAsState()
            YmTheme(theme) {
                val c = Ym.colors
                DisposableEffect(c.isDark, c.bg) {
                    val bar = if (c.isDark) SystemBarStyle.dark(c.bg.toArgb()) else SystemBarStyle.light(c.bg.toArgb(), c.bg.toArgb())
                    enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
                    onDispose {}
                }
                Root(app, pendingIntent.value) { pendingIntent.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    CHATS("chats", "Чаты", Icons.Filled.Email),
    PROFILE("profile", "Профиль", Icons.Filled.Person),
    SALES("sales", "Продажи", Icons.Filled.ShoppingCart),
    SUPPORT("support", "Поддержка", Icons.Filled.Info),
    AUTO("auto", "Авто", Icons.Filled.Build),
}

private fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun Root(app: App, intent: Intent?, consumeIntent: () -> Unit) {
    val account by app.prefs.account.collectAsState()
    val scope = rememberCoroutineScope()
    var webLogin by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    fun tryKey(key: String) {
        busy = true; loginError = null
        app.prefs.setGoldenKey(key)
        app.repo.clear()
        scope.launch {
            try {
                app.repo.me(force = true)
            } catch (e: Exception) {
                app.prefs.setGoldenKey(null)
                loginError = "FunPay не принял вход: ${e.message}"
            } finally { busy = false }
        }
    }

    when {
        webLogin -> LoginScreen(onClose = { webLogin = false }, onGoldenKey = { k -> webLogin = false; tryKey(k) })
        !account.loggedIn || busy -> LoginGate(onWebLogin = { webLogin = true }, onKey = ::tryKey, busy = busy, error = loginError)
        else -> MainUi(app, intent, consumeIntent)
    }
}

@Composable
private fun MainUi(app: App, intent: Intent?, consumeIntent: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val c = Ym.colors

    // переход из уведомления
    LaunchedEffect(intent) {
        val tab = intent?.getStringExtra(Notifier.EXTRA_TAB) ?: return@LaunchedEffect
        val node = intent?.getStringExtra(Notifier.EXTRA_NODE)
        when (tab) {
            "chats" -> { nav.openTab("chats"); if (node != null) nav.navigate("chat/$node?name=") }
            "sales" -> nav.openTab("sales")
            "auto" -> nav.openTab("auto")
        }
        consumeIntent()
    }

    val openChat: (String, String) -> Unit = { node, name -> nav.navigate("chat/$node?name=${Uri.encode(name)}") }

    Scaffold(
        containerColor = c.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (Tab.entries.any { it.route == route }) {
                NavigationBar(containerColor = c.card, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.openTab(t.route) },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = c.accent, selectedTextColor = c.accent,
                                indicatorColor = c.accent.copy(alpha = 0.14f),
                                unselectedIconColor = c.textMuted, unselectedTextColor = c.textMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Tab.CHATS.route, modifier = Modifier.padding(pad)) {
            composable(Tab.CHATS.route) { ChatsScreen(app.repo, openChat) }
            composable(Tab.PROFILE.route) { MyProfileScreen(app.repo, snackbar) { nav.navigate("settings") } }
            composable(Tab.SALES.route) {
                SalesScreen(app.repo) { uid, name ->
                    scopeNavigateToChat(app, nav, uid, name)
                }
            }
            composable(Tab.SUPPORT.route) {
                SupportScreen(app.repo) { id, title -> nav.navigate("ticket/$id?title=${Uri.encode(title)}") }
            }
            composable(Tab.AUTO.route) { AutomationScreen(app.prefs, app.repo, app.log, snackbar) }
            composable(
                "chat/{node}?name={name}",
                arguments = listOf(
                    navArgument("node") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                ChatScreen(
                    app.repo, app.prefs,
                    node = e.arguments?.getString("node").orEmpty(),
                    title = e.arguments?.getString("name").orEmpty().ifBlank { "Чат" },
                    snackbar = snackbar,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "ticket/{id}?title={title}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                TicketScreen(
                    app.repo,
                    id = e.arguments?.getString("id").orEmpty(),
                    title = e.arguments?.getString("title").orEmpty().ifBlank { "Заявка" },
                    snackbar = snackbar,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("settings") { SettingsScreen(app) { nav.popBackStack() } }
        }
    }
}

/** Чат с покупателем из «Моих продаж»: узел users-A-B считается по id. */
private fun scopeNavigateToChat(app: App, nav: NavHostController, userId: Int, name: String) {
    val me = app.prefs.account.value.userId ?: return
    val node = "users-${minOf(me, userId)}-${maxOf(me, userId)}"
    nav.navigate("chat/$node?name=${Uri.encode(name)}")
}
