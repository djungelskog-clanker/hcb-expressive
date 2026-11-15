@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package lol.djungelskog.hcbx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import lol.djungelskog.hcbx.BuildConfig
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.data.J
import lol.djungelskog.hcbx.ui.*
import lol.djungelskog.hcbx.ui.theme.*

fun cardTitle(c: J): String = c["name"].s?.takeIf { it.isNotBlank() } ?: c["user"]["name"].s?.let { n ->
    val p = n.split(" ").filter { it.isNotEmpty() }
    if (p.size > 1) "${p[0]} ${p[1].take(1)}'s card" else "${p[0]}'s card"
} ?: "Card"

fun cardStatusColor(s: String) = when (s) { "active" -> Green; "frozen" -> Blue; "inactive" -> Amber; else -> Muted }

@Composable
fun CardVisual(card: J, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val status = card["status"].str
    val white = card["personalization"]["color"].s == "white"
    val virtual = card["type"].str == "virtual"
    val brush = when {
        status == "canceled" || status == "expired" -> Brush.linearGradient(listOf(Color(0xFF6B6B6B), Color(0xFF3A3A3A)))
        white -> Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE3E3E8)))
        virtual -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
        else -> Brush.linearGradient(listOf(Color(0xFF2A2A33), Color(0xFF0E0E13)))
    }
    val fg = if (white && status != "canceled") Color(0xFF1B1B1F) else Color.White
    val shape = RoundedCornerShape(28.dp)
    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().aspectRatio(1.586f).background(brush)) {
            Box(Modifier.size(220.dp).align(Alignment.TopEnd).offset(60.dp, (-80).dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(fg.copy(alpha = 0.07f)))
            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (virtual) "VIRTUAL" else "PHYSICAL", color = fg.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    card["personalization"]["logo_url"].s?.let { AsyncImage(it, null, Modifier.height(28.dp).widthIn(max = 90.dp)) }
                    if (status != "active") Pill(titleCase(status), if (white) cardStatusColor(status) else Color.White, Modifier.padding(start = 8.dp))
                }
                Column {
                    Text("••••  ••••  ••••  ${card["last4"].s ?: "••••"}", color = fg, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(cardTitle(card), color = fg, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            card["organization"]["name"].s?.let { Text(it, color = fg.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                        }
                        if (card["exp_month"].exists) Text("%02d/%02d".format(card["exp_month"].int, card["exp_year"].int % 100), color = fg, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
    if (onClick != null) Surface(onClick, modifier, shape = shape) { body() } else Surface(modifier, shape = shape) { body() }
}

@Composable
fun CardsScreen(nav: (String) -> Unit) {
    val orgId = AppState.orgId
    var tab by remember { mutableStateOf("mine") }
    val mine = rememberLoader { Api.get("user/cards", mapOf("expand" to "organization,user")).items }
    val org = rememberLoader(orgId) { if (orgId == null) emptyList() else Api.get("organizations/$orgId/cards", mapOf("expand" to "user,organization")).items }
    val grants = rememberLoader { Api.get("user/card_grants", mapOf("expand" to "organization")).items }
    val loader = if (tab == "mine") mine else org
    ScreenScaffold(
        "Cards", null,
        fab = { ExtendedFloatingActionButton({ nav("order-card") }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New card") }) },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            ChipRow(listOf("mine" to "My cards", "org" to (AppState.orgName.ifBlank { "Organization" })), tab) { tab = it }
            Spacer(Modifier.height(8.dp))
            LoaderBox(loader) { cards ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (tab == "mine" && grants.data.orEmpty().isNotEmpty()) item {
                        Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, onClick = { nav("grants/mine") }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.CardGiftcard, null)
                                Text("${grants.data.orEmpty().size} card grant${if (grants.data.orEmpty().size > 1) "s" else ""} for you", Modifier.weight(1f))
                                Icon(Icons.Rounded.ChevronRight, null)
                            }
                        }
                    }
                    if (cards.isEmpty()) item { EmptyState(Icons.Rounded.CreditCard, "No cards yet", "Order a virtual or physical card for your team.") }
                    items(cards, key = { it.id }) { c -> CardVisual(c) { nav("card/${c.id}") } }
                }
            }
        }
    }
}

@Composable
fun CardDetailScreen(id: String, onBack: () -> Unit, nav: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val card = rememberLoader(id) { Api.get("cards/$id", mapOf("expand" to "organization,user,last_frozen_by,total_spent_cents")) }
    val txns = rememberPaged(id) { after -> Api.get("cards/$id/transactions", mapOf("limit" to 25, "after" to after)) }
    val action = rememberAction()
    var activate by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    ScreenScaffold("Card", onBack, actions = {
        IconButton({ openUrl(ctx, "${BuildConfig.HCB_BASE_URL}/stripe_cards/${id.removePrefix("card_")}") }) { Icon(Icons.Rounded.OpenInBrowser, "open on web") }
    }) { pad ->
        LoaderBox(card, pad) { c ->
            val status = c["status"].str
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item { Box(Modifier.padding(16.dp)) { CardVisual(c) } }
                item {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (status) {
                            "active" -> Button({ action.run("Card frozen", onDone = { card.reload() }) { Api.patch("cards/$id", mapOf("status" to "frozen")) } }, shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.AcUnit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Freeze") }
                            "frozen" -> Button({ action.run("Card unfrozen", onDone = { card.reload() }) { Api.patch("cards/$id", mapOf("status" to "active")) } }, shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.LockOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Unfreeze") }
                            "inactive" -> Button({ activate = true }, shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Activate") }
                        }
                        if (status != "canceled" && status != "expired") OutlinedButton({ cancel = true }, shape = androidx.compose.foundation.shape.CircleShape, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancel card") }
                    }
                }
                item {
                    Surface2(Modifier.padding(top = 12.dp)) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            InfoRow("Status", titleCase(status)); InfoRow("Cardholder", c["user"]["name"].s)
                            InfoRow("Organization", c["organization"]["name"].s)
                            InfoRow("Total spent", c["total_spent_cents"].long?.let { money(it) })
                            c["last_frozen_by"]["name"].s?.let { InfoRow("Frozen by", it) }
                            if (c["shipping"].exists) { InfoRow("Shipping", titleCase(c["shipping"]["status"].str)); InfoRow("ETA", c["shipping"]["eta"].s?.let { fullDate(it) }) }
                            InfoRow("Card ID", c.id, copy = true)
                        }
                    }
                }
                item { Text("Card numbers can only be revealed in the official HCB app (Stripe requires a trusted client), but you can still freeze, activate and manage cards here.", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { SectionTitle("Transactions") }
                if (!txns.started) item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { LoadingIndicator() } }
                else if (txns.items.isEmpty()) item { EmptyState(Icons.Rounded.Inbox, "No charges yet") }
                else {
                    item { Surface2 { txns.items.forEach { t -> TxnRow(t) { nav("txn/${t.id}") } } } }
                    pagedFooter(txns)
                }
            }
        }
    }
    if (activate) {
        var last4 by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { activate = false }, shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Activate card") },
            text = { Field(last4, { last4 = it.filter(Char::isDigit).take(4) }, "Last 4 digits on the card", keyboard = KeyboardType.Number) },
            confirmButton = { TextButton({ activate = false; action.run("Card activated", onDone = { card.reload() }) { Api.patch("cards/$id", mapOf("status" to "active", "last4" to last4)) } }, enabled = last4.length == 4) { Text("Activate") } },
            dismissButton = { TextButton({ activate = false }) { Text("Cancel") } },
        )
    }
    if (cancel) Confirm("Cancel this card?", "This permanently cancels the card. It cannot be undone.", "Cancel card", true, onDismiss = { cancel = false }) {
        action.run("Card canceled", onDone = { card.reload() }) { Api.post("cards/$id/cancel") }
    }
}

@Composable
fun OrderCardScreen(onBack: () -> Unit) {
    val orgs = AppState.orgs
    var orgId by remember { mutableStateOf(AppState.orgId.orEmpty()) }
    var type by remember { mutableStateOf("virtual") }
    var name by remember { mutableStateOf(AppState.userName) }
    var line1 by remember { mutableStateOf("") }; var line2 by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }; var state by remember { mutableStateOf("") }; var zip by remember { mutableStateOf("") }
    var design by remember { mutableStateOf("") }
    val designs = rememberLoader(orgId) { Api.get("cards/designs", mapOf("event_id" to orgId)).items }
    val action = rememberAction()
    val toast = rememberToast()
    val scope = rememberCoroutineScope()
    ScreenScaffold("New card", onBack) { pad ->
        FormColumn(pad) {
            Text("Organization", style = MaterialTheme.typography.labelLarge)
            ChipRow(orgs.map { it.id to it["name"].str }, orgId, Modifier.offset(x = (-16).dp)) { orgId = it }
            Text("Type", style = MaterialTheme.typography.labelLarge)
            ChipRow(listOf("virtual" to "Virtual", "physical" to "Physical"), type, Modifier.offset(x = (-16).dp)) { type = it }
            if (type == "physical") {
                Text("Shipping is US only.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Field(name, { name = it }, "Name on card")
                Field(line1, { line1 = it }, "Address line 1"); Field(line2, { line2 = it }, "Address line 2 (optional)")
                Field(city, { city = it }, "City")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Field(state, { state = it.uppercase().take(2) }, "State", Modifier.weight(1f)); Field(zip, { zip = it }, "ZIP", Modifier.weight(1f), keyboard = KeyboardType.Number) }
                val dl = designs.data.orEmpty()
                if (dl.isNotEmpty()) {
                    Text("Design", style = MaterialTheme.typography.labelLarge)
                    ChipRow(listOf("" to "Default") + dl.map { it.id to it["name"].str }, design, Modifier.offset(x = (-16).dp)) { design = it }
                }
            }
            BusyButton(if (type == "virtual") "Create virtual card" else "Order physical card", action.busy, orgId.isNotEmpty()) {
                if (type == "physical" && listOf(name, line1, city, state, zip).any { it.isBlank() }) { toast("Fill in the shipping details"); return@BusyButton }
                action.run("Card created", onDone = onBack) {
                    val card = mutableMapOf<String, Any?>("organization_id" to orgId, "card_type" to type)
                    if (type == "physical") card += mapOf(
                        "shipping_name" to name, "shipping_address_line1" to line1, "shipping_address_line2" to line2, "shipping_address_city" to city,
                        "shipping_address_state" to state, "shipping_address_postal_code" to zip, "shipping_address_country" to "US",
                        "card_personalization_design_id" to design.ifEmpty { null },
                    )
                    Api.post("cards", mapOf("card" to card))
                }
            }
        }
    }
}
