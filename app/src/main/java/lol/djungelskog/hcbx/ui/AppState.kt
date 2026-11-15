package lol.djungelskog.hcbx.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import lol.djungelskog.hcbx.data.Api
import lol.djungelskog.hcbx.data.J
import lol.djungelskog.hcbx.data.Settings

/** Session-wide state: who is signed in, which organizations they have, and which one is selected. */
object AppState {
    var user by mutableStateOf(J(null)); private set
    var orgs by mutableStateOf<List<J>>(emptyList()); private set
    var orgId by mutableStateOf<String?>(null); private set
    var org by mutableStateOf(J(null)); private set
    var invitations by mutableStateOf<List<J>>(emptyList()); private set
    var missingReceipts by mutableStateOf(0); private set
    var loaded by mutableStateOf(false); private set
    var loadError by mutableStateOf<String?>(null); private set

    val orgName get() = org["name"].s ?: orgs.firstOrNull { it.id == orgId }?.get("name")?.s ?: ""
    val userName get() = user["name"].str
    val isPlayground get() = org["playground_mode"].bool

    /** Plan features gate some actions; if the API did not tell us, assume everything is available. */
    fun hasFeature(f: String): Boolean {
        val feats = org["plan"]["features"].list
        return feats.isEmpty() || feats.any { it.s == f }
    }

    suspend fun bootstrap() {
        loadError = null
        try {
            user = Api.get("user", mapOf("expand" to "shipping_address,billing_address"))
            orgs = Api.get("user/organizations").items
            val pick = orgs.firstOrNull { it.id == Settings.lastOrg || it["slug"].s == Settings.lastOrg } ?: orgs.firstOrNull()
            if (pick != null) select(pick.id)
            loaded = true
            refreshBadges()
        } catch (e: Exception) {
            loadError = e.message
            if (user.exists) loaded = true
        }
    }

    suspend fun select(id: String) {
        orgId = id
        Settings.setLastOrg(id)
        // show cached list data right away, then load the expanded org
        org = orgs.firstOrNull { it.id == id } ?: J(null)
        refreshOrg()
    }

    suspend fun refreshOrg() {
        val id = orgId ?: return
        runCatching {
            val full = Api.get("organizations/$id", mapOf("expand" to "balance_cents,users,account_number,plan,reporting"))
            if (orgId == id) org = full
        }
    }

    suspend fun refreshBadges() {
        runCatching { invitations = Api.get("user/invitations").items.filter { !it["accepted"].bool } }
        runCatching {
            val r = Api.get("user/transactions/missing_receipt", mapOf("limit" to 50))
            missingReceipts = r.items.size
        }
    }

    fun reset() {
        user = J(null); orgs = emptyList(); orgId = null; org = J(null); invitations = emptyList(); missingReceipts = 0; loaded = false
    }
}
