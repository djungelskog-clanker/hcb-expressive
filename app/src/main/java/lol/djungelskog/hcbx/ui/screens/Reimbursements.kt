@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package lol.djungelskog.hcbx.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import lol.djungelskog.hcbx.BuildConfig
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.data.J
import lol.djungelskog.hcbx.ui.*
import lol.djungelskog.hcbx.ui.theme.*

private fun reportText(s: String) = when (s) {
    "draft" -> "Draft"; "submitted" -> "Review requested"; "reimbursement_requested" -> "Processing"; "reimbursement_approved" -> "In transit"
    "reimbursed" -> "Reimbursed"; "rejected" -> "Rejected"; "reversed" -> "Canceled"; else -> titleCase(s)
}
private fun reportColor(s: String): Color = when (s) {
    "draft" -> Muted; "submitted" -> Blue; "reimbursement_requested" -> Purple; "reimbursement_approved", "reimbursed" -> Green; "rejected" -> Danger; else -> Amber
}

private val CATEGORIES = listOf("Advertising / Marketing", "Customs Fees", "Dues & Subscriptions", "Equipment & Furniture", "Food & Entertainment", "Gifts", "Janitorial & Maintenance", "Mileage", "Office Supplies", "Postage & Shipping", "Prizes", "Project Supplies", "Software", "Taxes & Licenses", "Technical Infrastructure", "Training", "Travel")

@Composable
fun ReimbursementsScreen(onBack: () -> Unit, nav: (String) -> Unit) {
    val org = AppState.orgId
    val reports = rememberLoader(org) { Api.get("reimbursement_reports", mapOf("organization_id" to org, "expand" to "user")).items }
    var create by remember { mutableStateOf(false) }
    val action = rememberAction()
    ScreenScaffold("Reimbursements", onBack, fab = { ExtendedFloatingActionButton({ create = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New report") }) }) { pad ->
        LoaderBox(reports, pad) { list ->
            if (list.isEmpty()) EmptyState(Icons.Rounded.Payments, "No reports yet", "Get paid back for things you bought for the team.")
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                item { Spacer(Modifier.height(4.dp)) }
                items(list, key = { it.id }) { r ->
                    Surface2 {
                        Row2(r["name"].str, supporting = "${r["user"]["name"].s ?: ""} · ${niceDate(r["created_at"].s)}".trimStart(' ', '·'),
                            leading = { IconBadge(Icons.Rounded.Payments, size = 40.dp) },
                            trailing = { Column(horizontalAlignment = Alignment.End) { MoneyText(r["amount_cents"].l, hideable = true); Pill(reportText(r["status"].str), reportColor(r["status"].str)) } },
                            onClick = { nav("reimbursement/${r.id}") })
                    }
                }
            }
        }
    }
    if (create) {
        var name by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { create = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("New reimbursement report", style = MaterialTheme.typography.titleLarge)
                Field(name, { name = it }, "Report name")
                BusyButton("Create", action.busy, name.isNotBlank()) {
                    action.run(onDone = { create = false; reports.reload() }) {
                        val r = Api.post("reimbursement_reports", mapOf("reimbursement_report" to mapOf("organization_id" to org, "name" to name.trim())))
                        if (r.id.isNotEmpty()) nav("reimbursement/${r.id}")
                    }
                }
            }
        }
    }
}

@Composable
fun ReimbursementDetailScreen(id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val report = rememberLoader(id) { Api.get("reimbursement_reports/$id", mapOf("expand" to "user,reviewer")) }
    val expenses = rememberLoader(id) { Api.get("reimbursement_expenses", mapOf("report_id" to id)).items }
    val action = rememberAction()
    var add by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<J?>(null) }
    ScreenScaffold("Report", onBack, actions = {
        IconButton({ openUrl(ctx, "${BuildConfig.HCB_BASE_URL}/${AppState.org["slug"].str}/reimbursement/reports/${id.removePrefix("rmr_")}") }) { Icon(Icons.Rounded.OpenInBrowser, "open on web") }
    }) { pad ->
        LoaderBox(report, pad) { r ->
            val status = r["status"].str
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Column(Modifier.padding(24.dp)) {
                        Text(r["name"].str, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(6.dp)); Pill(reportText(status), reportColor(status))
                        Spacer(Modifier.height(12.dp)); MoneyText(r["amount_cents"].l, style = MaterialTheme.typography.displaySmall, hideable = true)
                        r["maximum_amount_cents"].long?.let { Text("Limit ${money(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status == "draft") {
                            Button({ action.run("Submitted for review", onDone = { report.reload() }) { Api.post("reimbursement_reports/$id/submit") } }, enabled = !action.busy && expenses.data.orEmpty().isNotEmpty(), shape = androidx.compose.foundation.shape.CircleShape) { Text("Submit") }
                            FilledTonalButton({ add = true }, shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add expense") }
                        } else if (status == "submitted") {
                            OutlinedButton({ action.run("Moved back to draft", onDone = { report.reload() }) { Api.post("reimbursement_reports/$id/draft") } }, shape = androidx.compose.foundation.shape.CircleShape) { Text("Back to draft") }
                        }
                    }
                }
                item { Surface2(Modifier.padding(top = 12.dp)) { Column(Modifier.padding(vertical = 8.dp)) { InfoRow("Submitted by", r["user"]["name"].s); InfoRow("Reviewer", r["reviewer"]["name"].s); InfoRow("Created", fullDate(r["created_at"].s)); InfoRow("Reimbursed", r["reimbursed_at"].s?.let { fullDate(it) }) } } }
                item { SectionTitle("Expenses") }
                val ex = expenses.data.orEmpty()
                if (ex.isEmpty()) item { Text("No expenses yet.", Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else item {
                    Surface2 {
                        ex.forEach { e ->
                            Row2(e["memo"].str, supporting = listOfNotNull(e["category"].s, e["description"].s).joinToString(" · "),
                                trailing = { Row(verticalAlignment = Alignment.CenterVertically) { MoneyText(e["amount_cents"].l); if (status == "draft") IconButton({ del = e }) { Icon(Icons.Rounded.DeleteOutline, "delete expense") } } })
                        }
                    }
                }
            }
        }
    }
    if (add) {
        var memo by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }; var cat by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }; var file by remember { mutableStateOf<Uri?>(null) }
        val picker = rememberFilePicker { file = it }
        ModalBottomSheet(onDismissRequest = { add = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).verticalScrollSimple(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add expense", style = MaterialTheme.typography.titleLarge)
                Field(memo, { memo = it }, "What was it?"); Field(amt, { amt = it }, "Amount", prefix = "$", keyboard = KeyboardType.Decimal)
                Text("Category", style = MaterialTheme.typography.labelLarge)
                ChipRow(CATEGORIES.map { it to it }, cat, Modifier.offset(x = (-24).dp)) { cat = it }
                Field(desc, { desc = it }, "Notes (optional)")
                OutlinedButton({ picker.show() }, Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (file == null) "Attach receipt" else "Receipt attached") }
                BusyButton("Add expense", action.busy, memo.isNotBlank() && moneyInput(amt) != null) {
                    action.run("Expense added", onDone = { add = false; expenses.reload(); report.reload() }) {
                        val fields = mapOf("reimbursement_expense[memo]" to memo.trim(), "reimbursement_expense[value]" to amt.replace("$", "").replace(",", "").trim(), "reimbursement_expense[category]" to cat.ifBlank { null }, "reimbursement_expense[description]" to desc.ifBlank { null }, "report_id" to id)
                        Api.upload("reimbursement_expenses", file?.let { mapOf("receipt" to it) } ?: emptyMap(), fields)
                    }
                }
            }
        }
    }
    del?.let { e -> Confirm("Delete expense?", e["memo"].str, "Delete", true, onDismiss = { del = null }) { action.run("Deleted", onDone = { expenses.reload(); report.reload() }) { Api.delete("reimbursement_expenses/${e.id}") } } }
}

@Composable private fun Modifier.verticalScrollSimple(): Modifier = this.verticalScroll(rememberScrollState())
