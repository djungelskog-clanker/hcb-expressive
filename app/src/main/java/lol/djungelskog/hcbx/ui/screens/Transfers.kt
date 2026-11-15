@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package lol.djungelskog.hcbx.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.ui.*

@Composable
fun TransferHubScreen(onBack: () -> Unit, nav: (String) -> Unit) {
    ScreenScaffold("Send money", onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Surface2(Modifier.padding(top = 8.dp)) {
                Row2("To another HCB organization", "Instant, free", leading = { IconBadge(Icons.Rounded.SwapHoriz) }, onClick = { nav("transfer/hcb") })
                Row2("ACH transfer", "US bank account, 1-3 business days", leading = { IconBadge(Icons.Rounded.AccountBalance) }, onClick = { nav("transfer/ach") })
                Row2("Mailed check", "We print and mail it", leading = { IconBadge(Icons.Rounded.Mail) }, onClick = { nav("transfer/check") })
                Row2("International wire", "Send in another currency", leading = { IconBadge(Icons.Rounded.Public) }, onClick = { nav("transfer/wire") })
            }
            Text("Large transfers may need to be approved on the HCB website; the API blocks amounts above the sudo-mode threshold.", Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AttachmentField(uri: Uri?, onPicked: (Uri) -> Unit) {
    val picker = rememberFilePicker(onPicked = onPicked)
    OutlinedButton({ picker.show() }, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = androidx.compose.foundation.shape.CircleShape) {
        Icon(Icons.Rounded.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (uri == null) "Attach invoice / documentation (optional)" else "Attachment added, tap to change")
    }
}

@Composable
fun AchScreen(onBack: () -> Unit) {
    val org = AppState.orgId
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }
    var routing by remember { mutableStateOf("") }; var acct by remember { mutableStateOf("") }; var bank by remember { mutableStateOf("") }
    var forWhat by remember { mutableStateOf("") }; var notify by remember { mutableStateOf(true) }; var file by remember { mutableStateOf<Uri?>(null) }
    val action = rememberAction(); val ok = name.isNotBlank() && routing.length == 9 && acct.isNotBlank() && moneyInput(amt) != null && forWhat.isNotBlank()
    ScreenScaffold("ACH transfer", onBack) { pad ->
        FormColumn(pad) {
            Field(name, { name = it }, "Recipient name"); Field(email, { email = it }, "Recipient email (optional)", keyboard = KeyboardType.Email)
            Field(amt, { amt = it }, "Amount", prefix = "$", keyboard = KeyboardType.Decimal)
            Field(routing, { routing = it.filter(Char::isDigit).take(9) }, "Routing number", keyboard = KeyboardType.Number)
            Field(acct, { acct = it.filter(Char::isDigit) }, "Account number", keyboard = KeyboardType.Number)
            Field(bank, { bank = it }, "Bank name (optional)")
            Field(forWhat, { forWhat = it }, "What is this payment for?")
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Email the recipient", Modifier.weight(1f)); Switch(notify, { notify = it }) }
            AttachmentField(file) { file = it }
            BusyButton("Send ${if (moneyInput(amt) != null) money(moneyInput(amt)!!) else ""}".trim(), action.busy, ok) {
                action.run("Transfer submitted", onDone = onBack) {
                    val fields = mapOf(
                        "ach_transfer[recipient_name]" to name.trim(), "ach_transfer[recipient_email]" to email.ifBlank { null },
                        "ach_transfer[amount_money]" to amt.replace("$", "").replace(",", "").trim(), "ach_transfer[routing_number]" to routing, "ach_transfer[account_number]" to acct,
                        "ach_transfer[bank_name]" to bank.ifBlank { null }, "ach_transfer[payment_for]" to forWhat.trim(), "ach_transfer[send_email_notification]" to notify.toString(),
                    )
                    // multipart so an attachment can travel along; the API treats nested form fields like a JSON body
                    Api.upload("ach_transfers", file?.let { mapOf("ach_transfer[file]" to it) } ?: emptyMap(), fields + ("event_id" to org))
                }
            }
        }
    }
}

@Composable
fun CheckScreen(onBack: () -> Unit) {
    val org = AppState.orgId
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }
    var l1 by remember { mutableStateOf("") }; var l2 by remember { mutableStateOf("") }; var city by remember { mutableStateOf("") }
    var st by remember { mutableStateOf("") }; var zip by remember { mutableStateOf("") }; var memo by remember { mutableStateOf("") }
    var forWhat by remember { mutableStateOf("") }; var notify by remember { mutableStateOf(true) }; var file by remember { mutableStateOf<Uri?>(null) }
    val action = rememberAction(); val ok = name.isNotBlank() && l1.isNotBlank() && city.isNotBlank() && st.isNotBlank() && zip.isNotBlank() && moneyInput(amt) != null && forWhat.isNotBlank()
    ScreenScaffold("Mailed check", onBack) { pad ->
        FormColumn(pad) {
            Field(name, { name = it }, "Payee name"); Field(email, { email = it }, "Payee email (optional)", keyboard = KeyboardType.Email)
            Field(amt, { amt = it }, "Amount", prefix = "$", keyboard = KeyboardType.Decimal)
            Field(l1, { l1 = it }, "Address line 1"); Field(l2, { l2 = it }, "Address line 2 (optional)"); Field(city, { city = it }, "City")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Field(st, { st = it.uppercase().take(2) }, "State", Modifier.weight(1f)); Field(zip, { zip = it }, "ZIP", Modifier.weight(1f), keyboard = KeyboardType.Number) }
            Field(memo, { memo = it }, "Check memo (printed on check)"); Field(forWhat, { forWhat = it }, "What is this payment for?")
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Email the payee", Modifier.weight(1f)); Switch(notify, { notify = it }) }
            AttachmentField(file) { file = it }
            BusyButton("Mail check", action.busy, ok) {
                action.run("Check ordered", onDone = onBack) {
                    val fields = mapOf(
                        "check[recipient_name]" to name.trim(), "check[recipient_email]" to email.ifBlank { null }, "check[amount_cents]" to moneyInput(amt).toString(),
                        "check[address_line1]" to l1, "check[address_line2]" to l2.ifBlank { null }, "check[address_city]" to city, "check[address_state]" to st, "check[address_zip]" to zip,
                        "check[memo]" to memo.ifBlank { null }, "check[payment_for]" to forWhat.trim(), "check[send_email_notification]" to notify.toString(), "event_id" to org,
                    )
                    Api.upload("checks", file?.let { mapOf("check[file]" to it) } ?: emptyMap(), fields)
                }
            }
        }
    }
}

private val CURRENCIES = listOf("EUR", "GBP", "CAD", "AUD", "INR", "JPY", "MXN", "BRL", "CHF", "SEK", "NOK", "DKK", "PLN", "PKR", "NGN", "KES", "ZAR", "SGD", "NZD")

@Composable
fun WireScreen(onBack: () -> Unit) {
    val org = AppState.orgId
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }; var cur by remember { mutableStateOf("EUR") }
    var bic by remember { mutableStateOf("") }; var acct by remember { mutableStateOf("") }; var bank by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }; var forWhat by remember { mutableStateOf("") }; var memo by remember { mutableStateOf("") }; var file by remember { mutableStateOf<Uri?>(null) }
    val action = rememberAction(); val ok = name.isNotBlank() && bic.isNotBlank() && acct.isNotBlank() && moneyInput(amt) != null && forWhat.isNotBlank()
    ScreenScaffold("International wire", onBack) { pad ->
        FormColumn(pad) {
            Text("Amount is in the destination currency.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipRow(CURRENCIES.map { it to it }, cur, Modifier.offset(x = (-16).dp)) { cur = it }
            Field(amt, { amt = it }, "Amount ($cur)", keyboard = KeyboardType.Decimal)
            Field(name, { name = it }, "Recipient name"); Field(email, { email = it }, "Recipient email (optional)", keyboard = KeyboardType.Email)
            Field(country, { country = it.uppercase().take(2) }, "Recipient country (2 letters)")
            Field(bank, { bank = it }, "Bank name (optional)"); Field(bic, { bic = it.uppercase() }, "BIC / SWIFT code"); Field(acct, { acct = it }, "IBAN or account number")
            Field(forWhat, { forWhat = it }, "What is this payment for?"); Field(memo, { memo = it }, "Memo (optional)")
            AttachmentField(file) { file = it }
            BusyButton("Send wire", action.busy, ok) {
                action.run("Wire submitted", onDone = onBack) {
                    val fields = mapOf(
                        "wire[recipient_name]" to name.trim(), "wire[recipient_email]" to email.ifBlank { null }, "wire[amount_cents]" to moneyInput(amt).toString(), "wire[currency]" to cur,
                        "wire[bic_code]" to bic, "wire[account_number]" to acct, "wire[bank_name]" to bank.ifBlank { null }, "wire[recipient_country]" to country.ifBlank { null },
                        "wire[payment_for]" to forWhat.trim(), "wire[memo]" to memo.ifBlank { null }, "event_id" to org,
                    )
                    Api.upload("wires", file?.let { mapOf("wire[file]" to it) } ?: emptyMap(), fields)
                }
            }
        }
    }
}

@Composable
fun HcbTransferScreen(onBack: () -> Unit) {
    val org = AppState.orgId
    var dest by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    val action = rememberAction(); val ok = dest.isNotBlank() && moneyInput(amt) != null && name.isNotBlank()
    ScreenScaffold("HCB transfer", onBack) { pad ->
        FormColumn(pad) {
            Text("Move money to another organization on HCB, e.g. a sub-organization or another team.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Pick from your organizations", style = MaterialTheme.typography.labelLarge)
            ChipRow(AppState.orgs.filter { it.id != org }.map { it["slug"].str to it["name"].str }, dest, Modifier.offset(x = (-16).dp)) { dest = it }
            Field(dest, { dest = it }, "or enter a destination slug / ID")
            Field(amt, { amt = it }, "Amount", prefix = "$", keyboard = KeyboardType.Decimal)
            Field(name, { name = it }, "Reason")
            BusyButton("Transfer", action.busy, ok) {
                action.run("Transfer sent", onDone = onBack) { Api.post("organizations/$org/transfers", mapOf("to_organization_id" to dest.trim(), "amount_cents" to moneyInput(amt), "name" to name.trim())) }
            }
        }
    }
}
