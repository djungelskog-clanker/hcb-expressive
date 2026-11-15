@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package lol.djungelskog.hcbx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lol.djungelskog.hcbx.data.Auth
import lol.djungelskog.hcbx.ui.theme.isDark

@Composable
fun LoginScreen() {
    val ctx = LocalContext.current
    val dark = isDark()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // decorative expressive shapes
        Box(Modifier.size(320.dp).offset(x = (-90).dp, y = (-70).dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer))
        Box(Modifier.size(200.dp).align(Alignment.TopEnd).offset(x = 60.dp, y = 150.dp).clip(MaterialShapes.Clover4Leaf.toShape()).background(MaterialTheme.colorScheme.tertiaryContainer))
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp), verticalArrangement = Arrangement.Bottom) {
            Box(Modifier.size(72.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primary), Alignment.Center) {
                androidx.compose.material3.Icon(Icons.Rounded.AccountBalance, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.height(20.dp))
            Text("HCB,\nbut expressive.", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "Cards, transfers, receipts and your whole organization in your pocket. An alternative client for Hack Club's HCB.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Auth.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { Auth.error = null; Auth.start(ctx, dark) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = CircleShape,
            ) { Text("Sign in with HCB", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(10.dp))
            Text(
                "You will be sent to hcb.hackclub.com to approve access. Your tokens stay in the Android Keystore on this device.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
