@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package lol.djungelskog.hcbx.ui.screens

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.data.J
import lol.djungelskog.hcbx.data.Settings
import lol.djungelskog.hcbx.ui.*
import lol.djungelskog.hcbx.ui.theme.*

@Composable
fun HomeScreen(nav: (String) -> Unit) {
    val orgId = AppState.orgId
    val scope = rememberCoroutineScope()
    var switcher by remember { mutableStateOf(false) }
    val recent = rememberLoader(orgId) { if (orgId == null) J(null) else Api.get("organizations/$orgId/transactions", mapOf("limit" to 6)) }
    val chart = rememberLoader(orgId) { if (orgId == null) J(null) else Api.get("organizations/$orgId/balance_by_date") }
    val toast = rememberToast()

    ScreenScaffold(
        title = AppState.orgName.ifBlank { "Home" },
        onBack = null,
        actions = {
            IconButton({ Settings.setHide(!Settings.hideBalances) }) {
                Icon(if (Settings.hideBalances) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "toggle balance privacy")
            }
            IconButton({ switcher = true }) { Icon(Icons.Rounded.UnfoldMore, "switch organization") }
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = recent.refreshing,
            onRefresh = { recent.reload(); chart.reload(); scope.launch { AppState.refreshOrg(); AppState.refreshBadges() } },
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { BalanceHero(chart.data) }
                item { Spacer(Modifier.height(12.dp)) }
                if (AppState.isPlayground) item {
                    Banner(Icons.Rounded.Science, "Playground mode", "This organization is in playground mode. Some features are limited.", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, null)
                }
                if (AppState.invitations.isNotEmpty()) item {
                    Banner(Icons.Rounded.MarkEmailUnread, "${AppState.invitations.size} pending invitation${if (AppState.invitations.size > 1) "s" else ""}", "Tap to review", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) { nav("invitations") }
                }
                if (AppState.missingReceipts > 0) item {
                    Banner(Icons.Rounded.ReceiptLong, "${AppState.missingReceipts}${if (AppState.missingReceipts >= 50) "+" else ""} receipts missing", "Add them before they pile up", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) { nav("receipts") }
                }
                item { QuickActions(nav) }
                item {
                    SectionTitle("Recent activity", trailing = { TextButton({ nav("activity") }) { Text("See all") } })
                }
                val txns = recent.data?.items.orEmpty()
                if (recent.res is Res.Loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { LoadingIndicator() } }
                else if (recent.res is Res.Err) item { ErrorState((recent.res as Res.Err).msg) { recent.reload(false) } }
                else if (txns.isEmpty()) item { EmptyState(Icons.Rounded.Inbox, "Nothing yet", "Transactions will show up here.") }
                else item {
                    Surface2 { txns.forEach { TxnRow(it) { nav("txn/${it.id}") } } }
                }
            }
        }
    }

    if (switcher) OrgSwitcher(onDismiss = { switcher = false }, onPick = { id -> switcher = false; scope.launch { AppState.select(id) } })
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, container: Color, content: Color, onClick: (() -> Unit)?) {
    Surface2(color = container, onClick = onClick, modifier = Modifier.padding(vertical = 2.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = content)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = content)
                Text(body, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.85f))
            }
            if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = content)
        }
    }
}

@Composable
private fun BalanceHero(series: J?) {
    val org = AppState.org
    val balance = org["balance_cents"].l
    var scrub by remember { mutableStateOf<Pair<String, Long>?>(null) }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box {
            Box(Modifier.size(180.dp).align(Alignment.TopEnd).offset(x = 50.dp, y = (-50).dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)))
            Column(Modifier.padding(24.dp)) {
                Text(scrub?.first?.let { fullDate(it) } ?: "Available balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                Spacer(Modifier.height(4.dp))
                MoneyText(scrub?.second ?: balance, style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer, hideable = true)
                val fee = org["fee_balance_cents"].l
                if (org["balance_cents"].exists && fee != 0L && scrub == null)
                    Text("${if (Settings.hideBalances) "•••" else money(fee)} fee balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                val points = series?.items.orEmpty().map { it["date"].str to it["amount"].l }
                if (points.size >= 2 && !Settings.hideBalances) {
                    Spacer(Modifier.height(16.dp))
                    BalanceChart(points, MaterialTheme.colorScheme.primary) { scrub = it }
                }
            }
        }
    }
}

@Composable
fun BalanceChart(points: List<Pair<String, Long>>, color: Color, onScrub: (Pair<String, Long>?) -> Unit) {
    var cursor by remember { mutableStateOf<Int?>(null) }
    val min = points.minOf { it.second }.toFloat()
    val max = points.maxOf { it.second }.toFloat()
    val range = (max - min).takeIf { it > 0 } ?: 1f
    Canvas(
        Modifier.fillMaxWidth().height(110.dp).pointerInput(points) {
            detectDragGestures(
                onDragStart = { o -> cursor = ((o.x / size.width) * (points.size - 1)).toInt().coerceIn(0, points.size - 1); onScrub(points[cursor!!]) },
                onDrag = { ch, _ -> cursor = ((ch.position.x / size.width) * (points.size - 1)).toInt().coerceIn(0, points.size - 1); onScrub(points[cursor!!]) },
                onDragEnd = { cursor = null; onScrub(null) },
                onDragCancel = { cursor = null; onScrub(null) },
            )
        },
    ) {
        val pad = 8.dp.toPx()
        fun x(i: Int) = pad + (size.width - 2 * pad) * i / (points.size - 1)
        fun y(v: Long) = pad + (size.height - 2 * pad) * (1 - (v - min) / range)
        val line = Path().apply {
            moveTo(x(0), y(points[0].second))
            for (i in 1 until points.size) {
                val cx = (x(i - 1) + x(i)) / 2
                cubicTo(cx, y(points[i - 1].second), cx, y(points[i].second), x(i), y(points[i].second))
            }
        }
        val fill = Path().apply { addPath(line); lineTo(x(points.size - 1), size.height); lineTo(x(0), size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        val i = cursor ?: (points.size - 1)
        val c = Offset(x(i), y(points[i].second))
        if (cursor != null) drawLine(color.copy(alpha = 0.4f), Offset(c.x, 0f), Offset(c.x, size.height), 1.5.dp.toPx())
        drawCircle(color, 6.dp.toPx(), c)
        drawCircle(Color.White.copy(alpha = 0.9f), 2.5.dp.toPx(), c)
    }
}

private data class Quick(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val route: String)

@Composable
private fun QuickActions(nav: (String) -> Unit) {
    val items = listOf(
        Quick(Icons.Rounded.Send, "Send money", "transfer"),
        Quick(Icons.Rounded.CreditCard, "Cards", "cards"),
        Quick(Icons.Rounded.CardGiftcard, "Grants", "grants"),
        Quick(Icons.Rounded.Favorite, "Donations", "donations"),
        Quick(Icons.Rounded.Receipt, "Invoices", "invoices"),
        Quick(Icons.Rounded.DocumentScanner, "Deposit check", "deposits"),
        Quick(Icons.Rounded.Group, "Team", "team"),
        Quick(Icons.Rounded.Payments, "Reimbursements", "reimbursements"),
    )
    LazyRow(Modifier.padding(top = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items) { q ->
            Column(Modifier.width(76.dp).clip(MaterialTheme.shapes.large).clickable { nav(q.route) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(q.icon, size = 60.dp, container = MaterialTheme.colorScheme.secondaryContainer)
                Spacer(Modifier.height(6.dp))
                Text(q.label, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
fun OrgSwitcher(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Switch organization", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(AppState.orgs) { o ->
                val sel = o.id == AppState.orgId
                Row2(
                    o["name"].str, supporting = o["slug"].s?.let { "hcb.hackclub.com/$it" },
                    leading = { Avatar(o["icon"].s, o["name"].str) },
                    trailing = { if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { onPick(o.id) },
                )
            }
        }
    }
}
