package lol.djungelskog.hcbx.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import lol.djungelskog.hcbx.ui.screens.*

/** Secondary routes (everything that is not a tab or core detail screen). */
fun NavGraphBuilder.extraRoutes(go: (String) -> Unit, back: () -> Unit) {
    composable("grants") { GrantsScreen(false, back, go) }
    composable("grants/mine") { GrantsScreen(true, back, go) }
    composable("grant/{id}") { GrantDetailScreen(it.arguments!!.getString("id")!!, back, go) }
    composable("grant-new") { NewGrantScreen(back) }
    composable("transfer") { TransferHubScreen(back, go) }
    composable("transfer/ach") { AchScreen(back) }
    composable("transfer/check") { CheckScreen(back) }
    composable("transfer/wire") { WireScreen(back) }
    composable("transfer/hcb") { HcbTransferScreen(back) }
    composable("donations") { DonationsScreen(back) }
    composable("invoices") { InvoicesScreen(back, go) }
    composable("invoice-new") { NewInvoiceScreen(back) }
    composable("deposits") { DepositsScreen(back, go) }
    composable("deposit-new") { NewDepositScreen(back) }
    composable("team") { TeamScreen(back, go) }
    composable("invite") { InviteScreen(back) }
    composable("tags") { TagsScreen(back) }
    composable("suborgs") { SubOrgsScreen(back) }
    composable("account") { AccountScreen(back) }
    composable("invitations") { InvitationsScreen(back) }
    composable("reimbursements") { ReimbursementsScreen(back, go) }
    composable("reimbursement/{id}") { ReimbursementDetailScreen(it.arguments!!.getString("id")!!, back) }
}
