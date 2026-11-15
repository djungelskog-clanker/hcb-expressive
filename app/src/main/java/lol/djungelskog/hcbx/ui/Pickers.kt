@file:OptIn(ExperimentalMaterial3Api::class)

package lol.djungelskog.hcbx.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/** Launchers for camera / gallery / file. Call [pick] to show the chooser sheet. */
class FilePicker(val show: () -> Unit)

@Composable
fun rememberFilePicker(allowPdf: Boolean = true, onPicked: (Uri) -> Unit): FilePicker {
    val ctx = LocalContext.current
    var sheet by remember { mutableStateOf(false) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) camUri?.let(onPicked) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(onPicked) }
    val doc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onPicked) }

    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row2("Take a photo", leading = { IconBadge(Icons.Rounded.PhotoCamera) }, onClick = {
                sheet = false
                val f = File(ctx.cacheDir, "captures").apply { mkdirs() }.let { File.createTempFile("cap_", ".jpg", it) }
                val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                camUri = u; cam.launch(u)
            })
            Row2("Choose from gallery", leading = { IconBadge(Icons.Rounded.PhotoLibrary) }, onClick = {
                sheet = false; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            if (allowPdf) Row2("Pick a file or PDF", leading = { IconBadge(Icons.Rounded.AttachFile) }, onClick = {
                sheet = false; doc.launch(arrayOf("image/*", "application/pdf"))
            })
        }
    }
    return remember { FilePicker { sheet = true } }
}

@Composable
fun DateField(value: String, label: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, {}, modifier.fillMaxWidth(), readOnly = true, label = { Text(label) }, shape = MaterialTheme.shapes.medium,
        trailingIcon = { IconButton({ open = true }) { Icon(Icons.Rounded.CalendarMonth, "pick date") } },
    )
    if (open) {
        val st = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { onChange(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } },
        ) { DatePicker(st) }
    }
}
