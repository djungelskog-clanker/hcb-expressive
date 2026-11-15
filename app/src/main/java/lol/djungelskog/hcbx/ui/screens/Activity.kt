@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package lol.djungelskog.hcbx.ui.screens

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.data.J
import lol.djungelskog.hcbx.ui.*

private val TYPES = listOf(
    "" to "All", "card_charge" to "Cards", "donation" to "Donations", "hcb_transfer" to "HCB transfers",
    "ach_transfer" to "ACH", "wire" to "Wire", "wise_transfer" to "Wise", "mailed_check" to "Checks",
    "check_deposit" to "Deposits", "invoice" to "Invoices", "reimbursement" to "Reimbursements", "fiscal_sponsorship_fee" to "Fees", "refund" to "Refunds",
)

class TxnFilters(
    type: String = "", missing: Boolean = false, flow: String = "", tag: String = "", start: String = "", end: String = "",
) {
    var type by mutableStateOf(type)
    var missing by mutableStateOf(missing)
    var flow by mutableStateOf(flow) // "", "expenses", "revenue"
    var tag by mutableStateOf(tag)
    var start by mutableStateOf(start)
    var end by mutableStateOf(end)
    val activeCount get() = listOf(missing, flow.isNotEmpty(), tag.isNotEmpty(), start.isNotEmpty(), end.isNotEmpty()).count { it }
    fun reset() { missing = false; flow = ""; tag = ""; start = ""; end = "" }
}

@Composable
fun ActivityScreen(nav: (String) -> Unit) {
    val orgId = AppState.orgId
    val f = remember { TxnFilters() }
    var q by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }
    LaunchedEffect(q) { delay(350); applied = q }

    val paged = rememberPaged(orgId, applied, f.type, f.missing, f.flow, f.tag, f.start, f.end) { after ->
        if (orgId == null) J(null) else Api.get(
            "organizations/$orgId/transactions",
            mapOf(
                "limit" to 35, "after" to after, "type" to f.type,
                "filters[search]" to applied,
                "filters[missing_receipts]" to if (f.missing) "true" else null,
                "filters[expenses]" to if (f.flow == "expenses") "true" else null,
                "filters[revenue]" to if (f.flow == "revenue") "true" else null,
                "filters[tag_id]" to f.tag, "filters[start_date]" to f.start, "filters[end_date]" to f.end,
            ),
        )
    }

    ScreenScaffold(
        "Activity", null,
        actions = {
            BadgedBox({ if (f.activeCount > 0) Badge { Text("${f.activeCount}") } }) {
                IconButton({ sheet = true }) { Icon(Icons.Rounded.FilterList, "filters") }
            }
        },
    ) { pad ->
        PullToRefreshBox(isRefreshing = paged.refreshing, onRefresh = { paged.refresh() }, modifier = Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    OutlinedTextField(
                        q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = androidx.compose.foundation.shape.CircleShape,
                        placeholder = { Text("Search transactions") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Rounded.Close, "clear") } },
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipRow(TYPES, f.type) { f.type = it }
                    Spacer(Modifier.height(8.dp))
                }
                if (!paged.started) item { Box(Modifier.fillParentMaxWidth().height(240.dp), Alignment.Center) { LoadingIndicator() } }
                else if (paged.error != null && paged.items.isEmpty()) item { ErrorState(paged.error!!) { paged.refresh() } }
                else if (paged.items.isEmpty()) item { EmptyState(Icons.Rounded.SearchOff, "No transactions", "Try changing your filters.") }
                else {
                    val groups = paged.items.groupBy { it["date"].str }
                    groups.forEach { (date, list) ->
                        item(key = "h$date") { SectionTitle(niceDate(date)) }
                        item(key = "g$date${list.first().id}") { Surface2 { list.forEach { t -> TxnRow(t) { nav("txn/${t.id}") } } } }
                    }
                    pagedFooter(paged)
                }
            }
        }
    }

    if (sheet) FilterSheet(f, orgId, onDismiss = { sheet = false })
}

@Composable
private fun FilterSheet(f: TxnFilters, orgId: String?, onDismiss: () -> Unit) {
    val tags = rememberLoader(orgId) { Api.get("tags", mapOf("event_id" to orgId)).items }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton({ f.reset() }) { Text("Reset") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Missing receipts only", Modifier.weight(1f)); Switch(f.missing, { f.missing = it })
            }
            Text("Direction", style = MaterialTheme.typography.labelLarge)
            ChipRow(listOf("" to "Both", "expenses" to "Money out", "revenue" to "Money in"), f.flow, Modifier.offset(x = (-16).dp)) { f.flow = it }
            val tagList = tags.data.orEmpty()
            if (tagList.isNotEmpty()) {
                Text("Tag", style = MaterialTheme.typography.labelLarge)
                ChipRow(listOf("" to "Any") + tagList.map { it.id to (it["emoji"].s?.let { e -> "$e " }.orEmpty() + it["label"].str) }, f.tag, Modifier.offset(x = (-16).dp)) { f.tag = it }
            }
            DateField(f.start, "From", { f.start = it })
            DateField(f.end, "To", { f.end = it })
            Button(onDismiss, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = androidx.compose.foundation.shape.CircleShape) { Text("Show results") }
        }
    }
}
