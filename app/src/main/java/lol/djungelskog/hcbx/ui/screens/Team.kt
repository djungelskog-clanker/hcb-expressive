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
import kotlinx.coroutines.launch
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.data.J
import lol.djungelskog.hcbx.ui.*
import lol.djungelskog.hcbx.ui.theme.*

@Composable
fun TeamScreen(onBack: () -> Unit, nav: (String) -> Unit) {
    val org = AppState.orgId
    val positions = rememberLoader(org) { Api.get("organizer_positions", mapOf("event_id" to org, "expand" to "user", "limit" to 100)).items }
    val invites = rememberLoader(org) { Api.get("organizations/$org/invitations", mapOf("expand" to "invitee,sender")).items.filter { !it["accepted"].bool } }
    val action = rememberAction()
    var removal by remember { mutableStateOf<J?>(null) }
    var reason by remember { mutableStateOf("") }
    var cancelInvite by remember { mutableStateOf<J?>(null) }
    ScreenScaffold("Team", onBack, fab = { ExtendedFloatingActionButton({ nav("invite") }, icon = { Icon(Icons.Rounded.PersonAdd, null) }, text = { Text("Invite") }) }) { pad ->
        LoaderBox(positions, pad) { list ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                val pending = invites.data.orEmpty()
                if (pending.isNotEmpty()) {
                    item { SectionTitle("Pending invitations") }
                    item {
                        Surface2 {
                            pending.forEach { i ->
                                Row2(i["invitee"]["name"].s ?: i["invitee"]["email"].s ?: "Invited member", supporting = titleCase(i["role"].str),
                                    leading = { Avatar(i["invitee"]["avatar"].s, i["invitee"]["name"].str) },
                                    trailing = { IconButton({ cancelInvite = i }) { Icon(Icons.Rounded.Close, "cancel invite") } })
                            }
                        }
                    }
                }
                item { SectionTitle("Members · ${list.size}") }
                item {
                    Surface2 {
                        list.forEach { p ->
                            val me = p["user"].id == AppState.user.id
                            Row2(p["user"]["name"].str + if (me) " (you)" else "", supporting = p["user"]["email"].s,
                                leading = { Avatar(p["user"]["avatar"].s, p["user"]["name"].str) },
                                trailing = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Pill(titleCase(p["role"].str), if (p["role"].str == "manager") MaterialTheme.colorScheme.primary else Muted)
                                        if (p["signee"].bool) Pill("Signee", Blue)
                                    }
                                },
                                onClick = { removal = p })
                        }
                    }
                }
            }
        }
    }
    removal?.let { p ->
        ModalBottomSheet(onDismissRequest = { removal = null }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(p["user"]["name"].str, style = MaterialTheme.typography.titleLarge)
                Text("${titleCase(p["role"].str)} · ${p["user"]["email"].str}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Request removal from this team. Organization managers review removal requests.", style = MaterialTheme.typography.bodySmall)
                Field(reason, { reason = it }, "Reason")
                BusyButton("Request removal", action.busy, reason.isNotBlank()) {
                    action.run("Removal requested", onDone = { removal = null; reason = "" }) { Api.post("organizer_positions/${p.id}/removal_request", mapOf("reason" to reason.trim())) }
                }
            }
        }
    }
    cancelInvite?.let { i -> Confirm("Cancel invitation?", "They will no longer be able to join with this invite.", "Cancel invite", true, onDismiss = { cancelInvite = null }) {
        action.run("Invitation canceled", onDone = { invites.reload() }) { Api.delete("organizations/$org/invitations/${i.id}") }
    } }
}

@Composable
fun InviteScreen(onBack: () -> Unit) {
    val org = AppState.orgId
    var email by remember { mutableStateOf("") }; var role by remember { mutableStateOf("member") }
    val action = rememberAction()
    ScreenScaffold("Invite someone", onBack) { pad ->
        FormColumn(pad) {
            Field(email, { email = it }, "Email address", keyboard = KeyboardType.Email)
            Text("Role", style = MaterialTheme.typography.labelLarge)
            ChipRow(listOf("reader" to "Reader", "member" to "Member", "manager" to "Manager"), role, Modifier.offset(x = (-16).dp)) { role = it }
            Text(when (role) { "reader" -> "Can view the organization but not spend."; "manager" -> "Full access, including managing the team and money."; else -> "Can use cards and upload receipts." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BusyButton("Send invitation", action.busy, email.contains("@")) {
                action.run("Invitation sent", onDone = onBack) { Api.post("organizations/$org/invitations", mapOf("email" to email.trim(), "role" to role)) }
            }
        }
    }
}

@Composable
fun TagsScreen(onBack: () -> Unit) {
    val org = AppState.orgId
    val tags = rememberLoader(org) { Api.get("tags", mapOf("event_id" to org)).items }
    val action = rememberAction()
    var create by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<J?>(null) }
    ScreenScaffold("Tags", onBack, fab = { ExtendedFloatingActionButton({ create = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New tag") }) }) { pad ->
        LoaderBox(tags, pad) { list ->
            if (list.isEmpty()) EmptyState(Icons.Rounded.Sell, "No tags yet", "Tags help you group and filter transactions.")
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                item { Spacer(Modifier.height(4.dp)) }
                item { Surface2 { list.forEach { t -> Row2("", leading = { TagChip(t) }, trailing = { IconButton({ del = t }) { Icon(Icons.Rounded.DeleteOutline, "delete tag") } }) } } }
            }
        }
    }
    if (create) {
        var label by remember { mutableStateOf("") }; var color by remember { mutableStateOf("blue") }; var emoji by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { create = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("New tag", style = MaterialTheme.typography.titleLarge)
                Field(label, { label = it }, "Label"); Field(emoji, { emoji = it.take(2) }, "Emoji (optional)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("muted", "red", "orange", "yellow", "green", "cyan", "blue", "purple").forEach { c ->
                        FilterChip(color == c, { color = c }, { Text(titleCase(c)) }, shape = androidx.compose.foundation.shape.CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = tagColor(c).copy(alpha = 0.2f), selectedLabelColor = tagColor(c)))
                    }
                }
                BusyButton("Create tag", action.busy, label.isNotBlank()) {
                    action.run("Tag created", onDone = { create = false; tags.reload() }) { Api.post("tags", mapOf("event_id" to org, "label" to label.trim(), "color" to color, "emoji" to emoji.ifBlank { null })) }
                }
            }
        }
    }
    del?.let { t -> Confirm("Delete tag?", "\"${t["label"].str}\" will be removed from all transactions.", "Delete", true, onDismiss = { del = null }) {
        action.run("Tag deleted", onDone = { tags.reload() }) { Api.delete("tags/${t.id}") }
    } }
}

@Composable
fun SubOrgsScreen(onBack: () -> Unit) {
    val org = AppState.orgId
    val subs = rememberLoader(org) { Api.get("organizations/$org/sub_organizations").items }
    val scope = rememberCoroutineScope()
    var create by remember { mutableStateOf(false) }
    val action = rememberAction()
    ScreenScaffold("Sub-organizations", onBack, fab = { ExtendedFloatingActionButton({ create = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New") }) }) { pad ->
        LoaderBox(subs, pad) { list ->
            if (list.isEmpty()) EmptyState(Icons.Rounded.AccountTree, "No sub-organizations", "Spin up child organizations that share your plan.")
            else LazyColumn(Modifier.fillMaxSize()) {
                item { Spacer(Modifier.height(4.dp)) }
                item { Surface2 { list.forEach { o -> Row2(o["name"].str, supporting = o["slug"].s, leading = { Avatar(o["icon"].s, o["name"].str) }, onClick = { scope.launch { AppState.select(o.id) } }) } } }
            }
        }
    }
    if (create) {
        var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var country by remember { mutableStateOf("US") }
        ModalBottomSheet(onDismissRequest = { create = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("New sub-organization", style = MaterialTheme.typography.titleLarge)
                Field(name, { name = it }, "Name"); Field(email, { email = it }, "Manager email", keyboard = KeyboardType.Email); Field(country, { country = it.uppercase().take(2) }, "Country (2 letters)")
                BusyButton("Create", action.busy, name.isNotBlank() && email.contains("@")) {
                    action.run("Created", onDone = { create = false; subs.reload() }) { Api.post("organizations/$org/sub_organizations", mapOf("name" to name.trim(), "email" to email.trim(), "country" to country)) }
                }
            }
        }
    }
}

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val o = AppState.org
    ScreenScaffold("Account numbers", onBack) { pad ->
        Column(Modifier.padding(pad)) {
            if (!o["account_number"].exists) EmptyState(Icons.Rounded.Pin, "Not available", "Account details are only visible to managers of organizations with an account number.")
            else {
                Surface2(Modifier.padding(top = 8.dp)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        InfoRow("Account number", o["account_number"].s, copy = true); InfoRow("Routing number", o["routing_number"].s, copy = true)
                        InfoRow("SWIFT / BIC", o["swift_bic_code"].s, copy = true); InfoRow("Account holder", AppState.orgName)
                    }
                }
                Text("Tap a row to copy it.", Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun InvitationsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val action = rememberAction()
    ScreenScaffold("Invitations", onBack) { pad ->
        val list = AppState.invitations
        if (list.isEmpty()) Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { EmptyState(Icons.Rounded.MarkEmailRead, "No pending invitations") }
        else LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(list, key = { it.id }) { i ->
                Surface2(Modifier.padding(top = 8.dp)) {
                    Row2(i["organization"]["name"].s ?: "Organization", supporting = "Invited by ${i["sender"]["name"].str} as ${titleCase(i["role"].str)}", leading = { Avatar(i["organization"]["icon"].s, i["organization"]["name"].str) })
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ action.run("Joined", onDone = { scope.launch { AppState.bootstrap() } }) { Api.post("user/invitations/${i.id}/accept") } }, enabled = !action.busy, shape = androidx.compose.foundation.shape.CircleShape) { Text("Accept") }
                        OutlinedButton({ action.run("Declined", onDone = { scope.launch { AppState.refreshBadges() } }) { Api.post("user/invitations/${i.id}/reject") } }, enabled = !action.busy, shape = androidx.compose.foundation.shape.CircleShape) { Text("Decline") }
                    }
                }
            }
        }
    }
}
