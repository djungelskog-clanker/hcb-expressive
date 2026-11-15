@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package lol.djungelskog.hcbx.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import lol.djungelskog.hcbx.BuildConfig
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.data.J
import lol.djungelskog.hcbx.ui.*
import lol.djungelskog.hcbx.ui.theme.*

private fun grantColor(s: String) = when (s) { "active" -> Green; "pending" -> Amber; "canceled", "expired" -> Muted; else -> Blue }

@Composable
fun GrantsScreen(mine: Boolean, onBack: () -> Unit, nav: (String) -> Unit) {
    val orgId = AppState.orgId
    val grants = rememberLoader(orgId, mine) {
        if (mine) Api.get("user/card_grants", mapOf("expand" to "organization")).items
        else Api.get("organizations/$orgId/card_grants", mapOf("expand" to "user,organization")).items
    }
    ScreenScaffold(
        if (mine) "My grants" else "Card grants", onBack,
        fab = { if (!mine) ExtendedFloatingActionButton(onClick = { nav("grant-new") }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New grant") }) },
    ) { pad ->
        LoaderBox(grants, pad) { list ->
            if (list.isEmpty()) EmptyState(Icons.Rounded.CardGiftcard, "No card grants", if (mine) "Grants you receive will show up here." else "Give a team member a card with a spending limit.")
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                item { Spacer(Modifier.height(4.dp)) }
                items(list, key = { it.id }) { g ->
                    Surface2(onClick = { nav("grant/${g.id}") }) {
                        Row2(
                            g["user"]["name"].s ?: g["email"].s ?: g["organization"]["name"].str,
                            supporting = g["purpose"].s?.takeIf { it.isNotBlank() } ?: g["organization"]["name"].s,
                            leading = { Avatar(g["user"]["avatar"].s, g["user"]["name"].s ?: g["email"].str) },
                            trailing = {
                                Column(horizontalAlignment = Alignment.End) {
                                    MoneyText(g["amount_cents"].l, hideable = true)
                                    Pill(titleCase(g["status"].str), grantColor(g["status"].str))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GrantDetailScreen(id: String, onBack: () -> Unit, nav: (String) -> Unit) {
    val grant = rememberLoader(id) { Api.get("card_grants/$id", mapOf("expand" to "user,organization,balance_cents,disbursements")) }
    val txns = rememberPaged(id) { after -> Api.get("card_grants/$id/transactions", mapOf("limit" to 20, "after" to after)) }
    val action = rememberAction()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var dlg by remember { mutableStateOf("") } // topup | withdraw | cancel | edit
    var amt by remember { mutableStateOf("") }
    ScreenScaffold("Card grant", onBack, actions = {
        IconButton({ openUrl(ctx, "${BuildConfig.HCB_BASE_URL}/grants/${id.removePrefix("cdg_")}") }) { Icon(Icons.Rounded.OpenInBrowser, "open on web") }
    }) { pad ->
        LoaderBox(grant, pad) { g ->
            val status = g["status"].str
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Column(Modifier.padding(24.dp)) {
                        Text(g["user"]["name"].s ?: g["email"].str, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(4.dp))
                        Pill(titleCase(status), grantColor(status))
                        Spacer(Modifier.height(16.dp))
                        Text("Balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(g["balance_cents"].long ?: g["amount_cents"].l, style = MaterialTheme.typography.displayMedium, hideable = true)
                        Text("of ${money(g["amount_cents"].l)} granted", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status == "active" || status == "pending") {
                            Button({ amt = ""; dlg = "topup" }, shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add funds") }
                            FilledTonalButton({ amt = ""; dlg = "withdraw" }, shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.Remove, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Withdraw") }
                        }
                        if (status != "canceled" && status != "expired") OutlinedButton({ dlg = "cancel" }, shape = androidx.compose.foundation.shape.CircleShape, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancel grant") }
                    }
                }
                item {
                    Surface2(Modifier.padding(top = 12.dp)) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            InfoRow("Recipient", g["email"].s, copy = true); InfoRow("Purpose", g["purpose"].s)
                            InfoRow("Organization", g["organization"]["name"].s); InfoRow("Expires", g["expires_on"].s?.let { fullDate(it) })
                            InfoRow("One-time use", if (g["one_time_use"].bool) "Yes" else null)
                            InfoRow("Pre-authorization", if (g["pre_authorization_required"].bool) "Required" else null)
                            InfoRow("Merchants", g["allowed_merchants"].list.joinToString(", ") { it.str }.ifBlank { null })
                            InfoRow("Categories", g["allowed_categories"].list.joinToString(", ") { titleCase(it.str) }.ifBlank { null })
                            InfoRow("Keywords", g["keyword_lock"].s)
                            g["card_id"].s?.let { c -> TextButton({ nav("card/$c") }, Modifier.padding(start = 8.dp)) { Text("Open card") } }
                        }
                    }
                }
                item { SectionTitle("Card activity") }
                if (!txns.started) item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { LoadingIndicator() } }
                else if (txns.items.isEmpty()) item { EmptyState(Icons.Rounded.Inbox, "No spending yet") }
                else { item { Surface2 { txns.items.forEach { t -> TxnRow(t) { nav("txn/${t.id}") } } } }; pagedFooter(txns) }
                val dis = g["disbursements"].list
                if (dis.isNotEmpty()) {
                    item { SectionTitle("Funding history") }
                    item { Surface2 { dis.forEach { d -> Row2(d["memo"].s ?: "Transfer", supporting = d["status"].s?.let { titleCase(it) }, trailing = { MoneyText(d["amount_cents"].l) }) } } }
                }
            }
        }
    }
    if (dlg == "topup" || dlg == "withdraw") AlertDialog(
        onDismissRequest = { dlg = "" }, shape = MaterialTheme.shapes.extraLarge,
        title = { Text(if (dlg == "topup") "Add funds" else "Withdraw funds") },
        text = { Field(amt, { amt = it }, "Amount", prefix = "$", keyboard = KeyboardType.Decimal) },
        confirmButton = {
            TextButton({
                val c = moneyInput(amt) ?: return@TextButton
                val kind = dlg; dlg = ""
                action.run(if (kind == "topup") "Funds added" else "Funds withdrawn", onDone = { grant.reload(); txns.refresh() }) { Api.post("card_grants/$id/$kind", mapOf("amount_cents" to c)) }
            }, enabled = moneyInput(amt) != null) { Text("Confirm") }
        },
        dismissButton = { TextButton({ dlg = "" }) { Text("Cancel") } },
    )
    if (dlg == "cancel") Confirm("Cancel this grant?", "Remaining funds return to the organization and the card is canceled.", "Cancel grant", true, onDismiss = { dlg = "" }) {
        action.run("Grant canceled", onDone = { grant.reload() }) { Api.post("card_grants/$id/cancel") }
    }
}

@Composable
fun NewGrantScreen(onBack: () -> Unit) {
    val orgId = AppState.orgId
    var email by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }; var purpose by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }; var merchants by remember { mutableStateOf("") }; var categories by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }; var oneTime by remember { mutableStateOf(false) }; var preAuth by remember { mutableStateOf(false) }
    var expires by remember { mutableStateOf("") }
    val action = rememberAction()
    ScreenScaffold("New card grant", onBack) { pad ->
        FormColumn(pad) {
            Field(email, { email = it }, "Recipient email", keyboard = KeyboardType.Email)
            Field(amt, { amt = it }, "Amount", prefix = "$", keyboard = KeyboardType.Decimal)
            Field(purpose, { purpose = it }, "Purpose (optional)")
            Field(message, { message = it }, "Message to recipient (optional)", singleLine = false)
            Text("Restrictions", style = MaterialTheme.typography.labelLarge)
            Field(merchants, { merchants = it }, "Merchant IDs (comma separated)")
            Field(categories, { categories = it }, "Categories (comma separated)")
            Field(keywords, { keywords = it }, "Keyword lock (regex)")
            DateField(expires, "Expires on (optional)", { expires = it })
            Row(verticalAlignment = Alignment.CenterVertically) { Text("One-time use", Modifier.weight(1f)); Switch(oneTime, { oneTime = it }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Require pre-authorization", Modifier.weight(1f)); Switch(preAuth, { preAuth = it }) }
            BusyButton("Send grant", action.busy, email.contains("@") && moneyInput(amt) != null) {
                action.run("Grant sent", onDone = onBack) {
                    Api.post("organizations/$orgId/card_grants", mapOf(
                        "email" to email.trim(), "amount_cents" to moneyInput(amt), "purpose" to purpose.ifBlank { null }, "invite_message" to message.ifBlank { null },
                        "merchant_lock" to merchants.ifBlank { null }, "category_lock" to categories.ifBlank { null }, "keyword_lock" to keywords.ifBlank { null },
                        "one_time_use" to oneTime, "pre_authorization_required" to preAuth, "expiration_at" to expires.ifBlank { null },
                    ))
                }
            }
        }
    }
}
