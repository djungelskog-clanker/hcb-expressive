@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package lol.djungelskog.hcbx.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import lol.djungelskog.hcbx.data.J
import lol.djungelskog.hcbx.data.Settings

// ---------- loading ----------

sealed interface Res<out T> {
    data object Loading : Res<Nothing>
    data class Err(val msg: String) : Res<Nothing>
    data class Ok<T>(val data: T) : Res<T>
}

class Loader<T>(private val scope: CoroutineScope, private val block: suspend () -> T) {
    var res by mutableStateOf<Res<T>>(Res.Loading)
    var refreshing by mutableStateOf(false)
    val data: T? get() = (res as? Res.Ok<T>)?.data

    fun reload(silent: Boolean = true) {
        scope.launch {
            if (!silent || res !is Res.Ok) res = Res.Loading else refreshing = true
            res = try { Res.Ok(block()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Res.Err(e.message ?: "something went wrong") }
            refreshing = false
        }
    }
}

@Composable
fun <T> rememberLoader(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    val scope = rememberCoroutineScope()
    val cur by rememberUpdatedState(block)
    val l = remember(*keys) { Loader(scope) { cur() } }
    LaunchedEffect(l) { l.reload(false) }
    return l
}

/** Cursor-paginated list (`after` = last id). Works for bare arrays too (hasMore = false). */
class Paged(private val scope: CoroutineScope, private val fetch: suspend (after: String?) -> J) {
    val items = mutableStateListOf<J>()
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var hasMore by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var started by mutableStateOf(false); private set

    fun refresh() = scope.launch {
        if (loading) return@launch
        loading = true; error = null; refreshing = started
        try {
            val r = fetch(null)
            val page = r.items
            items.clear(); items.addAll(page)
            hasMore = if (r.isArray) false else r.hasMore && page.isNotEmpty()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
        loading = false; refreshing = false; started = true
    }

    fun loadMore() {
        if (loading || !hasMore || items.isEmpty()) return
        scope.launch {
            loading = true
            try {
                val r = fetch(items.last().id)
                val page = r.items
                items.addAll(page)
                hasMore = !r.isArray && r.hasMore && page.isNotEmpty()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
            loading = false
        }
    }
}

@Composable
fun rememberPaged(vararg keys: Any?, fetch: suspend (String?) -> J): Paged {
    val scope = rememberCoroutineScope()
    val cur by rememberUpdatedState(fetch)
    val p = remember(*keys) { Paged(scope) { cur(it) } }
    LaunchedEffect(p) { p.refresh() }
    return p
}

// ---------- snackbar ----------

val LocalSnack = compositionLocalOf<SnackbarHostState> { error("no snackbar host") }

@Composable
fun rememberToast(): (String) -> Unit {
    val host = LocalSnack.current
    val scope = rememberCoroutineScope()
    return { msg -> scope.launch { host.currentSnackbarData?.dismiss(); host.showSnackbar(msg) } }
}

/** Runs a suspend action with busy state and toast-on-error. */
class Action(private val scope: CoroutineScope, private val toast: (String) -> Unit) {
    var busy by mutableStateOf(false); private set
    fun run(success: String? = null, onDone: (() -> Unit)? = null, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try { block(); success?.let(toast); onDone?.invoke() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { toast(e.message ?: "that did not work") }
            busy = false
        }
    }
}

@Composable
fun rememberAction(): Action {
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    return remember { Action(scope, toast) }
}

// ---------- scaffolds ----------

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(behavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "back") }
                },
                actions = actions,
                scrollBehavior = behavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        floatingActionButton = fab,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.surface,
        content = content,
    )
}

@Composable
fun <T> LoaderBox(loader: Loader<T>, padding: PaddingValues = PaddingValues(0.dp), content: @Composable (T) -> Unit) {
    PullToRefreshBox(
        isRefreshing = loader.refreshing,
        onRefresh = { loader.reload(true) },
        modifier = Modifier.padding(padding).fillMaxSize(),
    ) {
        when (val r = loader.res) {
            Res.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            is Res.Err -> ErrorState(r.msg) { loader.reload(false) }
            is Res.Ok -> content(r.data)
        }
    }
}

@Composable
fun ErrorState(msg: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.CloudOff, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(msg, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(40.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.secondaryContainer), Alignment.Center) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (body != null) {
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

/** Infinite-scroll trigger for LazyColumn paged lists. */
fun LazyListScope.pagedFooter(p: Paged) {
    item {
        if (p.hasMore || p.loading) {
            LaunchedEffect(p.items.size) { p.loadMore() }
            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { if (p.loading) LoadingIndicator(Modifier.size(40.dp)) }
        }
    }
}

// ---------- building blocks ----------

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Surface2(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color = MaterialTheme.colorScheme.surfaceContainer, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    if (onClick != null) Surface(onClick, m, shape = shape, color = color) { Column(content = content) }
    else Surface(m, shape = shape, color = color) { Column(content = content) }
}

@Composable
fun Row2(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    headlineColor: Color = Color.Unspecified,
) {
    Row(
        modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(headline, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, color = headlineColor)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
fun IconBadge(icon: ImageVector, container: Color = MaterialTheme.colorScheme.secondaryContainer, tint: Color = MaterialTheme.colorScheme.onSecondaryContainer, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(MaterialShapes.Clover4Leaf.toShape()).background(container), Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = tint)
    }
}

@Composable
fun Avatar(url: String?, name: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val shape = MaterialShapes.Cookie9Sided.toShape()
    if (!url.isNullOrBlank()) {
        AsyncImage(url, name, modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    } else {
        Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
            Text(initials(name), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun MoneyText(cents: Long, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge, signed: Boolean = false, color: Color = Color.Unspecified, hideable: Boolean = false) {
    val hide = hideable && Settings.hideBalances
    Text(if (hide) "•••••" else money(cents, signed), modifier, style = style, color = color, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    prefix: String? = null,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), prefix = prefix?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } }, shape = MaterialTheme.shapes.medium,
    )
}

@Composable
fun BusyButton(text: String, busy: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled && !busy, shape = CircleShape) {
        if (busy) LoadingIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary) else Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun Confirm(title: String, body: String, confirm: String = "Confirm", danger: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(modifier, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { i ->
            val (key, label) = options[i]
            FilterChip(selected == key, { onSelect(key) }, label = { Text(label) }, shape = CircleShape)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?, modifier: Modifier = Modifier, copy: Boolean = false) {
    if (value.isNullOrBlank()) return
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val toast = rememberToast()
    Row(
        modifier.fillMaxWidth().let { if (copy) it.clickable { clip.setText(androidx.compose.ui.text.AnnotatedString(value)); toast("Copied $label") } else it }.padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyLarge)
        if (copy) Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Standard form screen body: scrolls, padded, with spaced children. */
@Composable
fun FormColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}
