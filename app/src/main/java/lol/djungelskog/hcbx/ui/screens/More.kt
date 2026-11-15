@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package lol.djungelskog.hcbx.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lol.djungelskog.hcbx.BuildConfig
import lol.djungelskog.hcbx.data.Auth
import lol.djungelskog.hcbx.data.Settings
import lol.djungelskog.hcbx.data.ThemeMode
import lol.djungelskog.hcbx.ui.*

private class Entry(val icon: ImageVector, val title: String, val sub: String, val route: String, val badge: Int = 0)

@Composable
fun MoreScreen(nav: (String) -> Unit) {
    val u = AppState.user
    val org = listOf(
        Entry(Icons.Rounded.Send, "Send money", "ACH, check, wire, HCB transfer", "transfer"),
        Entry(Icons.Rounded.CardGiftcard, "Card grants", "Give team members spending money", "grants"),
        Entry(Icons.Rounded.Favorite, "Donations", "Donation page, link and QR code", "donations"),
        Entry(Icons.Rounded.Receipt, "Invoices", "Bill sponsors", "invoices"),
        Entry(Icons.Rounded.DocumentScanner, "Check deposits", "Deposit paper checks", "deposits"),
        Entry(Icons.Rounded.Payments, "Reimbursements", "Expense reports", "reimbursements"),
        Entry(Icons.Rounded.Group, "Team", "Members, invites and roles", "team"),
        Entry(Icons.Rounded.Sell, "Tags", "Organize transactions", "tags"),
        Entry(Icons.Rounded.AccountTree, "Sub-organizations", "Child organizations", "suborgs"),
        Entry(Icons.Rounded.Pin, "Account numbers", "Routing and account details", "account"),
    )
    ScreenScaffold("More", null) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Surface2(onClick = { nav("settings") }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Avatar(u["avatar"].s, u["name"].str, 56.dp)
                        Column(Modifier.weight(1f)) {
                            Text(u["name"].str, style = MaterialTheme.typography.titleLarge)
                            Text(u["email"].str, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.Settings, null)
                    }
                }
            }
            if (AppState.invitations.isNotEmpty()) item {
                Surface2(color = MaterialTheme.colorScheme.primaryContainer, onClick = { nav("invitations") }, modifier = Modifier.padding(top = 4.dp)) {
                    Row2("${AppState.invitations.size} pending invitation${if (AppState.invitations.size > 1) "s" else ""}", leading = { Icon(Icons.Rounded.MarkEmailUnread, null) }, trailing = { Icon(Icons.Rounded.ChevronRight, null) })
                }
            }
            item { SectionTitle(AppState.orgName.ifBlank { "Organization" }) }
            item {
                Surface2 {
                    org.forEach { e -> Row2(e.title, supporting = e.sub, leading = { IconBadge(e.icon, size = 40.dp) }, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { nav(e.route) }) }
                }
            }
            item { SectionTitle("App") }
            item {
                Surface2 {
                    Row2("Settings", supporting = "Theme, privacy, security", leading = { IconBadge(Icons.Rounded.Tune, size = 40.dp) }, onClick = { nav("settings") })
                    Row2("About", supporting = "Version ${BuildConfig.VERSION_NAME}", leading = { IconBadge(Icons.Rounded.Info, size = 40.dp) }, onClick = { nav("about") })
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmOut by remember { mutableStateOf(false) }
    ScreenScaffold("Settings", onBack) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionTitle("Appearance") }
            item {
                Surface2 {
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Theme", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
                        ChipRow(ThemeMode.entries.map { it.name to it.name }, Settings.themeMode.name) { Settings.setTheme(ThemeMode.valueOf(it)) }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 31) SwitchRow("Dynamic color", "Use colors from your wallpaper", Settings.dynamicColor) { Settings.setDynamic(it) }
                }
            }
            item { SectionTitle("Privacy & security") }
            item {
                Surface2 {
                    SwitchRow("Hide balances", "Mask amounts on the home screen and lists", Settings.hideBalances) { Settings.setHide(it) }
                    SwitchRow("Biometric lock", "Ask for fingerprint, face or PIN when opening the app", Settings.biometricLock) { Settings.setBio(it) }
                    SwitchRow("Block screenshots", "Also hides the app in the recents screen", Settings.secureScreen) { Settings.setSecure(it) }
                }
            }
            item { SectionTitle("Account") }
            item {
                Surface2 {
                    Row2("Signed in as ${AppState.userName}", supporting = AppState.user["email"].s)
                    Row2("Sign out", leading = { Icon(Icons.Rounded.Logout, null, tint = MaterialTheme.colorScheme.error) }, headlineColor = MaterialTheme.colorScheme.error, onClick = { confirmOut = true })
                }
            }
        }
    }
    if (confirmOut) Confirm("Sign out?", "Your access token will be revoked.", "Sign out", true, onDismiss = { confirmOut = false }) {
        scope.launch { Auth.signOut(); AppState.reset() }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row2(title, supporting = sub, trailing = { Switch(checked, onChange) }, onClick = { onChange(!checked) })
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    ScreenScaffold("About", onBack) { pad ->
        Column(Modifier.padding(pad).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("HCB Expressive ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
            Text("An unofficial, community-made Material 3 Expressive client for HCB, the fiscal sponsorship platform by Hack Club. It talks to the public HCB v4 API and is not affiliated with or endorsed by Hack Club.", style = MaterialTheme.typography.bodyLarge)
            FilledTonalButton({ openUrl(ctx, "https://github.com/djungelskog-clanker/hcb-expressive") }) { Text("Source code on GitHub") }
            FilledTonalButton({ openUrl(ctx, "https://hcb.hackclub.com") }) { Text("hcb.hackclub.com") }
            FilledTonalButton({ openUrl(ctx, "https://github.com/hackclub/hcb-mobile") }) { Text("Official HCB mobile app") }
        }
    }
}
