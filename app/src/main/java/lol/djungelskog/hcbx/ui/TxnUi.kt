@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package lol.djungelskog.hcbx.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import lol.djungelskog.hcbx.data.J
import lol.djungelskog.hcbx.ui.theme.*

fun txnIcon(code: String): ImageVector = when (code) {
    "100" -> Icons.Rounded.Receipt
    "200", "201" -> Icons.Rounded.Favorite
    "300", "800" -> Icons.Rounded.AccountBalance
    "310", "350", "360" -> Icons.Rounded.Public
    "400", "401" -> Icons.Rounded.Mail
    "402" -> Icons.Rounded.DocumentScanner
    "500" -> Icons.Rounded.SwapHoriz
    "600", "601" -> Icons.Rounded.CreditCard
    "700", "701", "702" -> Icons.Rounded.Percent
    "710" -> Icons.Rounded.ReceiptLong
    else -> Icons.Rounded.Paid
}

fun txnKind(code: String): String = when (code) {
    "100" -> "Invoice"
    "200", "201" -> "Donation"
    "300" -> "ACH transfer"
    "310" -> "Wire transfer"
    "350" -> "PayPal transfer"
    "360" -> "Wise transfer"
    "400", "401" -> "Check"
    "402" -> "Check deposit"
    "500" -> "HCB transfer"
    "600", "601" -> "Card charge"
    "700", "701", "702" -> "Fee"
    "710" -> "Reimbursement"
    "800" -> "ACH payment"
    else -> "Transaction"
}

fun tagColor(name: String): Color = when (name) {
    "red" -> Danger
    "orange" -> Color(0xFFFF8C37)
    "yellow" -> Amber
    "green" -> Green
    "cyan" -> Color(0xFF5BC0DE)
    "blue" -> Blue
    "purple" -> Purple
    else -> Muted
}

@Composable
fun TagChip(tag: J, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = tagColor(tag["color"].str)
    val label = (tag["emoji"].s?.let { "$it " } ?: "") + tag["label"].str
    if (onClick != null) {
        FilterChip(true, onClick, { Text(label) }, modifier, shape = androidx.compose.foundation.shape.CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.18f), selectedLabelColor = c))
    } else {
        Pill(label, c, modifier)
    }
}

@Composable
fun TxnRow(tx: J, onClick: () -> Unit) {
    val code = tx["code"].str
    val cents = tx["amount_cents"].l
    val pending = tx["pending"].bool
    val declined = tx["declined"].bool
    val title = tx["card_charge"]["merchant"]["smart_name"].s ?: tx["memo"].str.ifBlank { txnKind(code) }
    Row2(
        headline = tx["memo"].str.ifBlank { title },
        supporting = niceDate(tx["date"].s),
        leading = {
            IconBadge(
                txnIcon(code),
                container = if (declined) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                tint = if (declined) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MoneyText(cents, signed = false, color = if (cents > 0) Green else Color.Unspecified, hideable = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (tx["missing_receipt"].bool) Pill("Receipt", Amber)
                    if (declined) Pill("Declined", Danger) else if (pending) Pill("Pending", Muted)
                    if (tx["reversed"].bool) Pill("Reversed", Muted)
                }
            }
        },
        onClick = onClick,
    )
}
