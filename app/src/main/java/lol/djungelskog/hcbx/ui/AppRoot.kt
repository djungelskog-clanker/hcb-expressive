@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package lol.djungelskog.hcbx.ui

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.data.Auth
import lol.djungelskog.hcbx.ui.screens.*

private class Tab(val route: String, val label: String, val icon: ImageVector, val selected: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab("activity", "Activity", Icons.Outlined.SwapVert, Icons.Rounded.SwapVert),
    Tab("cards", "Cards", Icons.Outlined.CreditCard, Icons.Rounded.CreditCard),
    Tab("receipts", "Receipts", Icons.Outlined.Receipt, Icons.Rounded.Receipt),
    Tab("more", "More", Icons.Outlined.Menu, Icons.Rounded.Menu),
)
private val TAB_ROUTES = TABS.map { it.route }.toSet()

@Composable
fun AppRoot(
    locked: Boolean, onUnlock: () -> Unit,
    sharedUri: Uri?, onShareConsumed: () -> Unit,
    deepLink: Uri?, onDeepLinkConsumed: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (!Auth.signedIn) {
            LoginScreen()
        } else {
            val scope = rememberCoroutineScope()
            LaunchedEffect(Auth.signedIn) { if (!AppState.loaded) AppState.bootstrap() }
            if (!AppState.loaded) {
                Box(Modifier.fillMaxSize().systemBarsPadding(), Alignment.Center) {
                    if (AppState.loadError != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ErrorState(AppState.loadError!!) { scope.launch { AppState.bootstrap() } }
                        }
                    } else LoadingIndicator(Modifier.size(72.dp))
                }
            } else {
                Shell(sharedUri, onShareConsumed, deepLink, onDeepLinkConsumed)
            }
        }
        if (locked) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("HCB Expressive is locked", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onUnlock) { Text("Unlock") }
                }
            }
        }
    }
}

@Composable
private fun Shell(sharedUri: Uri?, onShareConsumed: () -> Unit, deepLink: Uri?, onDeepLinkConsumed: () -> Unit) {
    val nav = rememberNavController()
    val snack = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    fun go(r: String) {
        if (r in TAB_ROUTES) nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true; restoreState = true
        } else nav.navigate(r) { launchSingleTop = true }
    }

    LaunchedEffect(sharedUri) { if (sharedUri != null) go("receipts") }
    LaunchedEffect(deepLink) {
        val seg = deepLink?.pathSegments
        if (seg != null && seg.size >= 2 && seg[0] == "hcb") { go("txn/txn_${seg[1]}"); onDeepLinkConsumed() } else if (deepLink != null) onDeepLinkConsumed()
    }

    CompositionLocalProvider(LocalSnack provides snack) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                AnimatedVisibility(route in TAB_ROUTES, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        TABS.forEach { t ->
                            val sel = route == t.route
                            NavigationBarItem(
                                selected = sel, onClick = { go(t.route) },
                                icon = {
                                    val badge = when (t.route) { "receipts" -> AppState.missingReceipts; "more" -> AppState.invitations.size; else -> 0 }
                                    BadgedBox({ if (badge > 0) Badge { Text(if (badge > 49) "50+" else "$badge") } }) { Icon(if (sel) t.selected else t.icon, t.label) }
                                },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
        ) { pad ->
            NavHost(
                nav, "home", Modifier.padding(pad).consumeWindowInsets(pad),
                enterTransition = { fadeIn(tween(220)) + scaleIn(tween(260), 0.96f) + slideInHorizontally(tween(260)) { it / 10 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + scaleOut(tween(200), 0.96f) },
            ) {
                val back: () -> Unit = { nav.popBackStack() }
                composable("home") { HomeScreen(::go) }
                composable("activity") { ActivityScreen(::go) }
                composable("cards") { CardsScreen(::go) }
                composable("receipts") { ReceiptsScreen(::go, sharedUri, onShareConsumed) }
                composable("more") { MoreScreen(::go) }
                composable("settings") { SettingsScreen(back) }
                composable("about") { AboutScreen(back) }
                composable("txn/{id}?org={org}", listOf(navArgument("id") { type = NavType.StringType }, navArgument("org") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                    TransactionScreen(it.arguments!!.getString("id")!!, it.arguments?.getString("org"), back, ::go)
                }
                composable("card/{id}") { CardDetailScreen(it.arguments!!.getString("id")!!, back, ::go) }
                composable("order-card") { OrderCardScreen(back) }
                extraRoutes(::go, back)
            }
        }
    }
}
