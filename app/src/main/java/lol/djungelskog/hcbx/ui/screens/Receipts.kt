@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package lol.djungelskog.hcbx.ui.screens

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.data.J
import lol.djungelskog.hcbx.ui.*
import lol.djungelskog.hcbx.ui.theme.*

/** Receipt bin + transactions that still need a receipt. Tap "Match" on a bin receipt, or "Add" on a transaction. */
@Composable
fun ReceiptsScreen(nav: (String) -> Unit, sharedUri: Uri?, onShareConsumed: () -> Unit) {
    val missing = rememberPaged { after -> Api.get("user/transactions/missing_receipt", mapOf("limit" to 30, "after" to after)) }
    val bin = rememberLoader { Api.get("receipts").items }
    val action = rememberAction()
    var matching by remember { mutableStateOf<J?>(null) }
    val scope = rememberCoroutineScope()
    var targetTx by remember { mutableStateOf<J?>(null) }

    // an image shared into the app goes to the receipt bin right away
    LaunchedEffect(sharedUri) {
        if (sharedUri != null) {
            action.run("Added to your receipt bin", onDone = { bin.reload() }) { Api.upload("receipts", mapOf("file" to sharedUri)) }
            onShareConsumed()
        }
    }
    val binPicker = rememberFilePicker { uri -> action.run("Added to your receipt bin", onDone = { bin.reload() }) { Api.upload("receipts", mapOf("file" to uri)) } }
    val txPicker = rememberFilePicker { uri ->
        val t = targetTx ?: return@rememberFilePicker
        action.run("Receipt uploaded", onDone = { missing.refresh(); scope.launch { AppState.refreshBadges() } }) { Api.upload("receipts", mapOf("file" to uri), mapOf("transaction_id" to t.id)) }
    }

    ScreenScaffold(
        "Receipts", null,
        fab = { ExtendedFloatingActionButton({ binPicker.show() }, icon = { Icon(Icons.Rounded.AddAPhoto, null) }, text = { Text("Add receipt") }) },
    ) { pad ->
        PullToRefreshBox(isRefreshing = missing.refreshing, onRefresh = { missing.refresh(); bin.reload() }, modifier = Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                val binItems = bin.data.orEmpty()
                if (binItems.isNotEmpty()) {
                    item { SectionTitle("Receipt bin") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(binItems) { r ->
                                Surface(modifier = Modifier.size(110.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = { matching = r }) {
                                    AsyncImage(r["preview_url"].s ?: r["url"].s, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            }
                        }
                    }
                }
                item { SectionTitle("Needs a receipt") }
                if (!missing.started) item { Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) { LoadingIndicator() } }
                else if (missing.error != null && missing.items.isEmpty()) item { ErrorState(missing.error!!) { missing.refresh() } }
                else if (missing.items.isEmpty()) item { EmptyState(Icons.Rounded.TaskAlt, "All caught up", "Every card transaction has a receipt.") }
                else {
                    items(missing.items, key = { it.id }) { t ->
                        Surface2 {
                            TxnRow(t) { nav("txn/${t.id}") }
                            Row(Modifier.padding(start = 76.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton({ targetTx = t; txPicker.show() }, shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add") }
                            }
                        }
                    }
                    pagedFooter(missing)
                }
            }
        }
    }

    matching?.let { r ->
        ModalBottomSheet(onDismissRequest = { matching = null }) {
            Column(Modifier.padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(r["preview_url"].s ?: r["url"].s, null, Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Fit)
                Text("This receipt is waiting in your bin. Attach it to a transaction from the HCB web app, or delete it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ action.run("Deleted", onDone = { bin.reload(); matching = null }) { Api.delete("receipts/${r.id}") } }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete receipt") }
            }
        }
    }
}
