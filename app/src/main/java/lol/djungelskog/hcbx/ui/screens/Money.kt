@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package lol.djungelskog.hcbx.ui.screens

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import lol.djungelskog.hcbx.BuildConfig
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.data.J
import lol.djungelskog.hcbx.ui.*
import lol.djungelskog.hcbx.ui.theme.*

private fun qr(text: String, px: Int = 640): androidx.compose.ui.graphics.ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until px) for (y in 0 until px) bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    return bmp.asImageBitmap()
}

private fun donationColor(d: J) = when {
    d["refunded"].bool -> Amber; d["deposited"].bool -> Green; d["in_transit"].bool -> Blue; else -> Danger
}
private fun donationStatus(d: J) = when {
    d["refunded"].bool -> "Refunded"; d["deposited"].bool -> "Deposited"; d["in_transit"].bool -> "In transit"; else -> "Failed"
}

@Composable
fun DonationsScreen(onBack: () -> Unit) {
    val org = AppState.orgId
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val toast = rememberToast()
    var status by remember { mutableStateOf("") }
    var qrOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<J?>(null) }
    val total = rememberLoader(org) { Api.get("donations", mapOf("event_id" to org, "expand" to "stats", "limit" to 1))["total_cents"].l }
    val paged = rememberPaged(org, status) { after -> Api.get("donations", mapOf("event_id" to org, "status" to status, "limit" to 30, "after" to after)) }
    val link = "${BuildConfig.HCB_BASE_URL}/donations/start/${AppState.org["slug"].str}"

    ScreenScaffold("Donations", onBack) { pad ->
        PullToRefreshBox(isRefreshing = paged.refreshing, onRefresh = { paged.refresh(); total.reload() }, Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Surface(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Column(Modifier.padding(24.dp)) {
                            Text("Raised through donations", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                            MoneyText(total.data ?: 0, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onTertiaryContainer, hideable = true)
                            Spacer(Modifier.height(12.dp))
                            if (AppState.org["donation_page_available"].bool) {
                                Text(link, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(Modifier.height(10.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton({ clip.setText(AnnotatedString(link)); toast("Link copied") }, shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copy") }
                                    FilledTonalButton({ ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), "Share donation page")) }, shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share") }
                                    FilledTonalButton({ qrOpen = true }, shape = androidx.compose.foundation.shape.CircleShape) { Icon(Icons.Rounded.QrCode2, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("QR code") }
                                }
                            } else Text("The public donation page is not enabled for this organization.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
                item { ChipRow(listOf("" to "All", "deposited" to "Deposited", "in_transit" to "In transit", "refunded" to "Refunded", "failed" to "Failed"), status) { status = it } }
                if (!paged.started) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { LoadingIndicator() } }
                else if (paged.error != null && paged.items.isEmpty()) item { ErrorState(paged.error!!) { paged.refresh() } }
                else if (paged.items.isEmpty()) item { EmptyState(Icons.Rounded.Favorite, "No donations yet", "Share your donation link to get started.") }
                else {
                    item {
                        Surface2(Modifier.padding(top = 8.dp)) {
                            paged.items.forEach { d ->
                                Row2(d["donor"]["name"].s ?: "Anonymous", supporting = niceDate(d["donated_at"].s) + if (d["recurring"].bool) " · recurring" else "",
                                    leading = { IconBadge(Icons.Rounded.Favorite, size = 40.dp) },
                                    trailing = { Column(horizontalAlignment = Alignment.End) { MoneyText(d["amount_cents"].l, hideable = true); Pill(donationStatus(d), donationColor(d)) } },
                                    onClick = { selected = d })
                            }
                        }
                    }
                    pagedFooter(paged)
                }
            }
        }
    }
    if (qrOpen) ModalBottomSheet(onDismissRequest = { qrOpen = false }) {
        Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(AppState.orgName, style = MaterialTheme.typography.titleLarge)
            Image(remember(link) { qr(link) }, "QR code for the donation page", Modifier.size(260.dp).clip(MaterialTheme.shapes.large).background(Color.White).padding(8.dp))
            Text("Scan to donate", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    selected?.let { d ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text(money(d["amount_cents"].l), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.displaySmall)
                InfoRow("Donor", d["donor"]["name"].s); InfoRow("Email", d["donor"]["email"].s, copy = true); InfoRow("Message", d["message"].s)
                InfoRow("Date", fullDate(d["donated_at"].s)); InfoRow("Status", donationStatus(d)); InfoRow("Type", if (d["recurring"].bool) "Recurring" else "One-time")
                InfoRow("Payment", d["payment_method"].let { p -> listOfNotNull(p["brand"].s?.let { titleCase(it) }, p["last4"].s?.let { "•••• $it" }).joinToString(" ").ifBlank { p["type"].s?.let { titleCase(it) } } })
                InfoRow("Source", d["attribution"]["utm_source"].s ?: d["attribution"]["referrer"].s)
                InfoRow("ID", d.id, copy = true)
            }
        }
    }
}

// ---------------- invoices ----------------

private fun invoiceColor(s: String) = when (s) {
    "Deposited", "Paid" -> Green; "In Transit", "Sent" -> Blue; "Due soon", "Refunded" -> Amber; "Overdue" -> Danger; else -> Muted
}

@Composable
fun InvoicesScreen(onBack: () -> Unit, nav: (String) -> Unit) {
    val org = AppState.orgId
    val ctx = LocalContext.current
    val inv = rememberLoader(org) { Api.get("invoices", mapOf("event_id" to org)).items }
    ScreenScaffold("Invoices", onBack, fab = { ExtendedFloatingActionButton({ nav("invoice-new") }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New invoice") }) }) { pad ->
        LoaderBox(inv, pad) { list ->
            if (list.isEmpty()) EmptyState(Icons.Rounded.Receipt, "No invoices", "Bill sponsors and get paid by card or ACH.")
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                item { Spacer(Modifier.height(4.dp)) }
                items(list, key = { it.id }) { i ->
                    Surface2 {
                        Row2(i["to"].str, supporting = listOfNotNull(i["item_description"].s, i["due_date"].s?.let { "due ${niceDate(it)}" }).joinToString(" · "),
                            leading = { IconBadge(Icons.Rounded.Receipt, size = 40.dp) },
                            trailing = { Column(horizontalAlignment = Alignment.End) { MoneyText(i["amount_due"].l, hideable = true); Pill(i["status"].str, invoiceColor(i["status"].str)) } },
                            onClick = { openUrl(ctx, "${BuildConfig.HCB_BASE_URL}/invoices/${i.id.removePrefix("inv_")}") })
                    }
                }
            }
        }
    }
}

@Composable
fun NewInvoiceScreen(onBack: () -> Unit) {
    val org = AppState.orgId
    val sponsors = rememberLoader(org) { Api.get("sponsors", mapOf("event_id" to org)).items }
    var sponsor by remember { mutableStateOf("") }
    var newSponsor by remember { mutableStateOf(false) }
    var sName by remember { mutableStateOf("") }; var sEmail by remember { mutableStateOf("") }
    var l1 by remember { mutableStateOf("") }; var city by remember { mutableStateOf("") }; var st by remember { mutableStateOf("") }; var zip by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }; var due by remember { mutableStateOf("") }
    val action = rememberAction()
    ScreenScaffold("New invoice", onBack) { pad ->
        FormColumn(pad) {
            Text("Sponsor", style = MaterialTheme.typography.labelLarge)
            ChipRow(sponsors.data.orEmpty().map { it.id to it["name"].str } + ("new" to "+ New sponsor"), if (newSponsor) "new" else sponsor, Modifier.offset(x = (-16).dp)) { if (it == "new") { newSponsor = true; sponsor = "" } else { newSponsor = false; sponsor = it } }
            if (newSponsor) {
                Field(sName, { sName = it }, "Sponsor name"); Field(sEmail, { sEmail = it }, "Contact email", keyboard = KeyboardType.Email)
                Field(l1, { l1 = it }, "Address line 1"); Field(city, { city = it }, "City")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Field(st, { st = it }, "State", Modifier.weight(1f)); Field(zip, { zip = it }, "ZIP", Modifier.weight(1f)) }
            }
            Field(desc, { desc = it }, "Item description")
            Field(amt, { amt = it }, "Amount", prefix = "$", keyboard = KeyboardType.Decimal)
            DateField(due, "Due date", { due = it })
            BusyButton("Send invoice", action.busy, (sponsor.isNotEmpty() || (newSponsor && sName.isNotBlank() && sEmail.contains("@") && l1.isNotBlank())) && desc.isNotBlank() && moneyInput(amt) != null && due.isNotEmpty()) {
                action.run("Invoice sent", onDone = onBack) {
                    var sid = sponsor
                    if (newSponsor) sid = Api.post("sponsors", mapOf("event_id" to org, "sponsor" to mapOf("name" to sName.trim(), "contact_email" to sEmail.trim(), "address_line1" to l1, "address_city" to city, "address_state" to st, "address_postal_code" to zip, "address_country" to "US"))).id
                    Api.post("invoices", mapOf("event_id" to org, "sponsor_id" to sid, "invoice" to mapOf("due_date" to due, "item_description" to desc.trim(), "item_amount" to moneyInput(amt))))
                }
            }
        }
    }
}

// ---------------- check deposits ----------------

@Composable
fun DepositsScreen(onBack: () -> Unit, nav: (String) -> Unit) {
    val org = AppState.orgId
    val list = rememberLoader(org) { Api.get("check_deposits", mapOf("event_id" to org)).items }
    var open by remember { mutableStateOf<J?>(null) }
    ScreenScaffold("Check deposits", onBack, fab = { ExtendedFloatingActionButton({ nav("deposit-new") }, icon = { Icon(Icons.Rounded.DocumentScanner, null) }, text = { Text("Deposit a check") }) }) { pad ->
        LoaderBox(list, pad) { l ->
            if (l.isEmpty()) EmptyState(Icons.Rounded.DocumentScanner, "No check deposits", "Snap a photo of the front and back of a check to deposit it.")
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                item { Spacer(Modifier.height(4.dp)) }
                items(l, key = { it.id }) { d ->
                    Surface2 {
                        Row2(money(d["amount_cents"].l), supporting = "${niceDate(d["created_at"].s)} · ${d["submitter"]["name"].str}",
                            leading = { IconBadge(Icons.Rounded.DocumentScanner, size = 40.dp) },
                            trailing = { Pill(titleCase(d["status"].str), when (d["status"].str) { "deposited" -> Green; "rejected", "returned" -> Danger; else -> Blue }) }, onClick = { open = d })
                    }
                }
            }
        }
    }
    open?.let { d ->
        ModalBottomSheet(onDismissRequest = { open = null }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text(money(d["amount_cents"].l), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.displaySmall)
                InfoRow("Status", titleCase(d["status"].str)); InfoRow("Submitted by", d["submitter"]["name"].s)
                InfoRow("Expected", d["estimated_arrival_date"].s?.let { fullDate(it) }); InfoRow("Rejected", d["rejection"]["reason"].s?.let { titleCase(it) })
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(d["front_url"].s, d["back_url"].s).filterNotNull().forEach { AsyncImage(it, null, Modifier.weight(1f).height(130.dp).clip(MaterialTheme.shapes.medium), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                }
            }
        }
    }
}

@Composable
fun NewDepositScreen(onBack: () -> Unit) {
    val org = AppState.orgId
    var front by remember { mutableStateOf<Uri?>(null) }; var back by remember { mutableStateOf<Uri?>(null) }; var amt by remember { mutableStateOf("") }
    val fp = rememberFilePicker(allowPdf = false) { front = it }; val bp = rememberFilePicker(allowPdf = false) { back = it }
    val action = rememberAction()
    ScreenScaffold("Deposit a check", onBack) { pad ->
        FormColumn(pad) {
            Text("Endorse the back with \"For deposit only\" and your organization's name, then photograph both sides on a dark, flat surface.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Field(amt, { amt = it }, "Check amount", prefix = "$", keyboard = KeyboardType.Decimal)
            for ((label, uri, picker) in listOf(Triple("Front of check", front, fp), Triple("Back of check", back, bp))) {
                Surface(modifier = Modifier.fillMaxWidth().height(150.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = { picker.show() }) {
                    if (uri != null) AsyncImage(uri, label, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Icon(Icons.Rounded.AddAPhoto, null); Text(label) }
                }
            }
            BusyButton("Deposit", action.busy, front != null && back != null && moneyInput(amt) != null) {
                action.run("Check submitted", onDone = onBack) { Api.upload("check_deposits", mapOf("front" to front!!, "back" to back!!), mapOf("event_id" to org, "amount_cents" to moneyInput(amt).toString())) }
            }
        }
    }
}
