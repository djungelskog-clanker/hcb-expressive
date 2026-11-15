@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package lol.djungelskog.hcbx.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import lol.djungelskog.hcbx.BuildConfig
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.data.J
import lol.djungelskog.hcbx.ui.*
import lol.djungelskog.hcbx.ui.theme.*

fun openUrl(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun TransactionScreen(id: String, orgHint: String?, onBack: () -> Unit, nav: (String) -> Unit) {
    val ctx = LocalContext.current
    val org = orgHint ?: AppState.orgId
    val tx = rememberLoader(id, org) { Api.get(if (org != null) "organizations/$org/transactions/$id" else "transactions/$id", mapOf("expand" to "organization")) }
    val receipts = rememberLoader(id, org) { Api.get(if (org != null) "organizations/$org/transactions/$id/receipts" else "receipts", mapOf("transaction_id" to id)).items }
    val comments = rememberLoader(id, org) { Api.get("comments", mapOf("transaction_id" to id)).items }
    val action = rememberAction()
    val toast = rememberToast()
    var rename by remember { mutableStateOf(false) }
    var tagSheet by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<J?>(null) }
    var confirmNoReceipt by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<J?>(null) }
    var comment by remember { mutableStateOf("") }
    val picker = rememberFilePicker { uri ->
        action.run("Receipt uploaded") {
            Api.upload("receipts", mapOf("file" to uri), mapOf("transaction_id" to id))
            receipts.reload(); tx.reload()
        }
    }

    ScreenScaffold(
        "Transaction", onBack,
        actions = {
            IconButton({
                val url = "${BuildConfig.HCB_BASE_URL}/hcb/${id.removePrefix("txn_")}"
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), "Share link"))
            }) { Icon(Icons.Rounded.Share, "share") }
        },
    ) { pad ->
        LoaderBox(tx, pad) { t ->
            val code = t["code"].str
            val cents = t["amount_cents"].l
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconBadge(txnIcon(code), size = 48.dp)
                            Text(txnKind(code), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(12.dp))
                        MoneyText(cents, style = MaterialTheme.typography.displayMedium, color = if (cents > 0) Green else Color.Unspecified, hideable = true)
                        Spacer(Modifier.height(4.dp))
                        Text(t["memo"].str, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill(fullDate(t["date"].s), MaterialTheme.colorScheme.primary)
                            if (t["pending"].bool) Pill("Pending", Muted)
                            if (t["declined"].bool) Pill("Declined", Danger)
                            if (t["reversed"].bool) Pill("Reversed", Muted)
                            if (t["missing_receipt"].bool) Pill("Missing receipt", Amber)
                            if (t["lost_receipt"].bool) Pill("No / lost receipt", Muted)
                        }
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton({ rename = true }, Modifier.weight(1f), shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Rename") }
                        FilledTonalButton({ tagSheet = true }, Modifier.weight(1f), shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.Sell, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Tags") }
                    }
                }
                if (t["tags"].list.isNotEmpty()) item {
                    FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { t["tags"].list.forEach { TagChip(it) } }
                }
                item { DetailsCard(t, nav) }

                item { SectionTitle("Receipts", trailing = { TextButton({ picker.show() }) { Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add") } }) }
                val rl = receipts.data.orEmpty()
                if (rl.isEmpty()) item {
                    Surface2 {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (t["missing_receipt"].bool) "This transaction needs a receipt." else "No receipts attached.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!t["lost_receipt"].bool) TextButton({ confirmNoReceipt = true }) { Text("I don't have one") }
                        }
                    }
                } else item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(rl) { r ->
                            Surface(Modifier.size(120.dp), shape = MaterialTheme.shapes.large, onClick = { viewer = r }, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Box {
                                    AsyncImage(r["preview_url"].s ?: r["url"].s, r["filename"].str, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    Text(r["filename"].str, Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(6.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, color = Color.White)
                                }
                            }
                        }
                    }
                }

                item { SectionTitle("Comments") }
                val cl = comments.data.orEmpty()
                if (cl.isEmpty()) item { Text("No comments yet.", Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else items(cl) { c ->
                    Row2(c["content"].str, supporting = "${c["user"]["name"].str} · ${niceDate(c["created_at"].s)}", leading = { Avatar(c["user"]["avatar"].s, c["user"]["name"].str, 36.dp) })
                }
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(comment, { comment = it }, Modifier.weight(1f), placeholder = { Text("Add a comment") }, shape = androidx.compose.foundation.shape.CircleShape, singleLine = true)
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton({
                            val text = comment.trim()
                            if (text.isNotEmpty()) action.run(onDone = { comment = ""; comments.reload() }) { Api.post("comments", mapOf("transaction_id" to id, "content" to text)) }
                        }, enabled = comment.isNotBlank() && !action.busy) { Icon(Icons.Rounded.Send, "send") }
                    }
                }
            }
        }
    }

    if (rename) RenameDialog(t = tx.data, org = org, id = id, onDismiss = { rename = false }, onSaved = { tx.reload() })
    if (tagSheet) TagSheet(tx.data, org, id, onDismiss = { tagSheet = false }, onSaved = { tx.reload() })
    viewer?.let { r -> ReceiptViewer(r, onDismiss = { viewer = null }, onDelete = { confirmDelete = r; viewer = null }) }
    if (confirmNoReceipt) Confirm("No receipt?", "This marks the transaction as having no (or a lost) receipt.", "Mark it", onDismiss = { confirmNoReceipt = false }) {
        action.run("Marked", onDone = { tx.reload() }) { Api.post("transactions/$id/mark_no_receipt") }
    }
    confirmDelete?.let { r -> Confirm("Delete receipt?", r["filename"].str, "Delete", true, onDismiss = { confirmDelete = null }) {
        action.run("Receipt deleted", onDone = { receipts.reload(); tx.reload() }) { Api.delete("receipts/${r.id}") }
    } }
}

@Composable
private fun DetailsCard(t: J, nav: (String) -> Unit) {
    val ctx = LocalContext.current
    Surface2(Modifier.padding(top = 8.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            val cc = t["card_charge"]
            if (cc.exists) {
                InfoRow("Merchant", cc["merchant"]["smart_name"].s ?: cc["merchant"]["name"].s)
                InfoRow("Card", cc["card"]["name"].s ?: cc["card"]["last4"].s?.let { "•••• $it" })
                InfoRow("Cardholder", cc["card"]["user"]["name"].s)
                InfoRow("Spent", niceDate(cc["spent_at"].s))
                InfoRow("Method", cc["charge_method"].s?.let { titleCase(it) })
                InfoRow("Wallet", cc["wallet"].s?.let { titleCase(it) })
                InfoRow("Country", cc["merchant"]["country"].s)
                InfoRow("Decline reason", cc["decline_reason"].s?.let { titleCase(it) })
            }
            val d = t["donation"]
            if (d.exists) {
                InfoRow("Donor", d["donor"]["name"].s); InfoRow("Email", d["donor"]["email"].s, copy = true)
                InfoRow("Message", d["message"].s); InfoRow("Recurring", if (d["recurring"].bool) "Yes" else "One-time")
                InfoRow("Refunded", if (d["refunded"].bool) "Yes" else null)
                InfoRow("Source", d["attribution"]["utm_source"].s ?: d["attribution"]["referrer"].s)
            }
            val ch = t["check"]
            if (ch.exists) {
                InfoRow("Recipient", ch["recipient_name"].s); InfoRow("Status", ch["status"].s?.let { titleCase(it) })
                InfoRow("Check #", ch["check_number"].s); InfoRow("For", ch["payment_for"].s)
                InfoRow("Address", listOf(ch["address_line1"].s, ch["address_line2"].s, ch["address_city"].s, ch["address_state"].s, ch["address_zip"].s).filterNotNull().filter { it.isNotBlank() }.joinToString(", "))
                InfoRow("Sent by", ch["sender"]["name"].s)
            }
            val tr = t["transfer"]
            if (tr.exists) {
                InfoRow("From", tr["from"]["name"].s); InfoRow("To", tr["to"]["name"].s)
                InfoRow("Status", tr["status"].s?.let { titleCase(it) }); InfoRow("Reason", tr["reason"].s)
                InfoRow("Sent by", tr["sender"]["name"].s)
                tr["card_grant_id"].s?.let { g -> TextButton({ nav("grant/$g") }, Modifier.padding(start = 8.dp)) { Text("Open card grant") } }
            }
            val ach = t["ach_transfer"]
            if (ach.exists) {
                InfoRow("Recipient", ach["recipient_name"].s); InfoRow("Bank", ach["bank_name"].s)
                InfoRow("Account", ach["account_number_last4"].s?.let { "•••• $it" }); InfoRow("Routing", ach["routing_number"].s, copy = true)
                InfoRow("For", ach["payment_for"].s); InfoRow("Sent by", ach["sender"]["name"].s)
            }
            val w = t["wire_transfer"]
            if (w.exists) {
                InfoRow("Recipient", w["recipient_name"].s); InfoRow("Country", w["recipient_country"].s)
                InfoRow("State", w["state"].s?.let { titleCase(it) }); InfoRow("Currency", w["currency"].s); InfoRow("For", w["payment_for"].s)
            }
            val wi = t["wise_transfer"]
            if (wi.exists) {
                InfoRow("Recipient", wi["recipient_name"].s); InfoRow("Country", wi["recipient_country"].s)
                InfoRow("State", wi["state"].s?.let { titleCase(it) }); InfoRow("Currency", wi["currency"].s)
                InfoRow("Foreign amount", wi["amount_cents"].long?.let { money(it) }); InfoRow("Return reason", wi["return_reason"].s)
            }
            val inv = t["invoice"]
            if (inv.exists) {
                InfoRow("Sponsor", inv["sponsor"]["name"].s); InfoRow("Email", inv["sponsor"]["email"].s, copy = true)
                InfoRow("Description", inv["description"].s); InfoRow("Due", fullDate(inv["due_date"].s)); InfoRow("Paid", inv["paid_at"].s?.let { fullDate(it) })
            }
            val cd = t["check_deposit"]
            if (cd.exists) {
                InfoRow("Status", cd["status"].s?.let { titleCase(it) }); InfoRow("Submitted by", cd["submitter"]["name"].s)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(cd["front_url"].s, cd["back_url"].s).filterNotNull().forEach { u ->
                        AsyncImage(u, null, Modifier.weight(1f).height(110.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                    }
                }
            }
            val ep = t["expense_payout"]
            if (ep.exists) InfoRow("Report", ep["report_id"].s)
            t["organization"].takeIf { it.exists }?.let { InfoRow("Organization", it["name"].s) }
            InfoRow("Transaction ID", t.id, copy = true)
            TextButton({ openUrl(ctx, "${BuildConfig.HCB_BASE_URL}/hcb/${t.id.removePrefix("txn_")}") }, Modifier.padding(start = 8.dp)) { Text("Open on web") }
        }
    }
}

@Composable
private fun RenameDialog(t: J?, org: String?, id: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var memo by remember { mutableStateOf(t?.get("memo")?.str.orEmpty()) }
    val suggestions = rememberLoader(id) { if (org == null) emptyList() else Api.get("organizations/$org/transactions/$id/memo_suggestions").items.mapNotNull { it.s } }
    val action = rememberAction()
    AlertDialog(
        onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Rename transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(memo, { memo = it }, "Memo")
                val s = suggestions.data.orEmpty().take(5)
                if (s.isNotEmpty()) {
                    Text("Suggestions", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { s.forEach { AssistChip({ memo = it }, { Text(it, maxLines = 1) }) } }
                }
            }
        },
        confirmButton = { TextButton({ action.run("Renamed", onDone = { onSaved(); onDismiss() }) { Api.patch("organizations/$org/transactions/$id", mapOf("memo" to memo.trim())) } }, enabled = !action.busy) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TagSheet(t: J?, org: String?, id: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val all = rememberLoader(org) { Api.get("tags", mapOf("event_id" to org)).items }
    val selected = remember { mutableStateListOf<String>().also { l -> t?.get("tags")?.list?.forEach { l.add(it.id) } } }
    val action = rememberAction()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tags", style = MaterialTheme.typography.titleLarge)
            val list = all.data.orEmpty()
            if (all.res is Res.Loading) LoadingIndicator()
            else if (list.isEmpty()) Text("This organization has no tags yet. Create some from More → Tags.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { tag ->
                    val on = tag.id in selected
                    FilterChip(on, { if (on) selected.remove(tag.id) else selected.add(tag.id) }, { Text((tag["emoji"].s?.let { "$it " } ?: "") + tag["label"].str) }, shape = androidx.compose.foundation.shape.CircleShape)
                }
            }
            BusyButton("Save", action.busy) { action.run(onDone = { onSaved(); onDismiss() }) { Api.patch("organizations/$org/transactions/$id", mapOf("tag_ids" to selected.toList())) } }
        }
    }
}

@Composable
private fun ReceiptViewer(r: J, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(r["preview_url"].s ?: r["url"].s, r["filename"].str, Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Fit)
            Text(r["filename"].str, style = MaterialTheme.typography.titleMedium)
            r["uploader"]["name"].s?.let { Text("Uploaded by $it · ${niceDate(r["created_at"].s)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ openUrl(ctx, r["url"].str) }, Modifier.weight(1f)) { Text("Open original") }
                OutlinedButton(onDelete, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            }
        }
    }
}
