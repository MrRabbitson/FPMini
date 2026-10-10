package lol.mrrabbitson.fpmini

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import lol.mrrabbitson.fpmini.core.OfferRow
import lol.mrrabbitson.fpmini.ui.Logo
import lol.mrrabbitson.fpmini.ui.YmTopBar
import lol.mrrabbitson.fpmini.ui.screens.CategoryScreen
import lol.mrrabbitson.fpmini.ui.screens.HomeScreen
import lol.mrrabbitson.fpmini.ui.screens.LoginScreen
import lol.mrrabbitson.fpmini.ui.screens.OfferScreen
import lol.mrrabbitson.fpmini.ui.screens.ProfileScreen
import lol.mrrabbitson.fpmini.ui.screens.SettingsScreen
import lol.mrrabbitson.fpmini.ui.theme.Ym
import lol.mrrabbitson.fpmini.ui.theme.YmTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App
        setContent {
            val theme by app.prefs.theme.collectAsState()
            YmTheme(theme) {
                val c = Ym.colors
                DisposableEffect(c.isDark, c.bg) {
                    val bar = if (c.isDark) SystemBarStyle.dark(c.bg.toArgb())
                    else SystemBarStyle.light(c.bg.toArgb(), c.bg.toArgb())
                    enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
                    onDispose {}
                }
                AppRoot(app)
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val CATEGORY = "cat/{type}/{id}"
    const val OFFER = "offer/{type}/{id}"
    const val USER = "user/{id}"
    const val SETTINGS = "settings"
    const val LOGIN = "login"
    fun category(type: String, id: Int) = "cat/$type/$id"
    fun offer(type: String, id: String) = "offer/$type/$id"
    fun user(id: Int) = "user/$id"
}

private fun NavHostController.goHome() {
    popBackStack(Routes.HOME, inclusive = false)
}

@Composable
private fun AppRoot(app: App) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val account by app.prefs.account.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var focusSearch by remember { mutableStateOf(false) }
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route
    val c = Ym.colors

    val openOffer: (OfferRow) -> Unit = { nav.navigate(Routes.offer(it.type, it.id)) }
    val openUser: (Int) -> Unit = { nav.navigate(Routes.user(it)) }
    val openCategory: (String, Int) -> Unit = { t, id -> nav.navigate(Routes.category(t, id)) }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = c.bg) {
                Column(Modifier.statusBarsPadding().padding(20.dp)) {
                    Logo()
                    Text("неофициальный клиент FunPay", color = c.textMuted, fontSize = 13.sp)
                }
                val itemColors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = c.field, unselectedContainerColor = c.bg,
                    selectedTextColor = c.text, unselectedTextColor = c.text,
                    selectedIconColor = c.accent, unselectedIconColor = c.textMuted,
                )
                NavigationDrawerItem(
                    label = { Text("Главная") }, icon = { Icon(Icons.Filled.Home, null) },
                    selected = route == Routes.HOME, colors = itemColors,
                    onClick = { scope.launch { drawer.close() }; nav.goHome() },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(if (account.loggedIn) (account.name ?: "Мой профиль") else "Войти") },
                    icon = { Icon(Icons.Filled.Person, null) },
                    selected = false, colors = itemColors,
                    onClick = {
                        scope.launch { drawer.close() }
                        val me = account.userId
                        when {
                            account.loggedIn && me != null -> nav.navigate(Routes.user(me))
                            account.loggedIn -> nav.navigate(Routes.SETTINGS)
                            else -> nav.navigate(Routes.LOGIN)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Настройки и темы") }, icon = { Icon(Icons.Filled.Settings, null) },
                    selected = route == Routes.SETTINGS, colors = itemColors,
                    onClick = { scope.launch { drawer.close() }; nav.navigate(Routes.SETTINGS) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        val showBar = route != Routes.SETTINGS && route != Routes.LOGIN
        Scaffold(
            containerColor = c.bg,
            contentWindowInsets = WindowInsets.navigationBars,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (showBar) YmTopBar(
                    onMenu = { scope.launch { drawer.open() } },
                    onSearch = { nav.goHome(); focusSearch = true },
                    onLogo = { nav.goHome() },
                )
            },
        ) { pad ->
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(pad).background(c.bg),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        repo = app.repo, prefs = app.prefs,
                        query = query, onQuery = { query = it },
                        searchFocus = focusSearch, onSearchFocused = { focusSearch = false },
                        onCategory = { openCategory(it.type, it.id) },
                    )
                }
                composable(
                    Routes.CATEGORY,
                    arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.IntType }),
                ) { e ->
                    CategoryScreen(
                        repo = app.repo,
                        type = e.arguments?.getString("type") ?: "lots",
                        id = e.arguments?.getInt("id") ?: 0,
                        onBack = { nav.popBackStack() },
                        onCategory = { t, id -> nav.navigate(Routes.category(t, id)) { popUpTo(Routes.CATEGORY) { inclusive = true } } },
                        onOffer = openOffer,
                        onUser = openUser,
                    )
                }
                composable(
                    Routes.OFFER,
                    arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType }),
                ) { e ->
                    OfferScreen(
                        repo = app.repo, account = account,
                        type = e.arguments?.getString("type") ?: "lots",
                        id = e.arguments?.getString("id").orEmpty(),
                        snackbar = snackbar,
                        onBack = { nav.popBackStack() },
                        onCategory = openCategory,
                        onUser = openUser,
                        onLogin = { nav.navigate(Routes.LOGIN) },
                    )
                }
                composable(Routes.USER, arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                    ProfileScreen(
                        repo = app.repo,
                        id = e.arguments?.getInt("id") ?: 0,
                        onBack = { nav.popBackStack() },
                        onCategory = { openCategory(it.type, it.id) },
                        onOffer = openOffer,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        prefs = app.prefs, repo = app.repo, snackbar = snackbar,
                        onBack = { nav.popBackStack() },
                        onLoginWeb = { nav.navigate(Routes.LOGIN) },
                    )
                }
                composable(Routes.LOGIN) {
                    LoginScreen(
                        onClose = { nav.popBackStack() },
                        onGoldenKey = { key ->
                            app.prefs.setGoldenKey(key)
                            app.repo.clearCache()
                            nav.popBackStack()
                            scope.launch {
                                val ok = runCatching { app.repo.verifyLogin() }.getOrDefault(false)
                                snackbar.showSnackbar(if (ok) "Вход выполнен" else "Вход сохранён, но FunPay пока не видит аккаунт")
                            }
                        },
                    )
                }
            }
        }
    }
}
