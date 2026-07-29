package io.github.yzjdev.expressapp.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yzjdev.expressapp.history.QueryHistoryItem
import io.github.yzjdev.expressapp.tracker.ExpressData
import io.github.yzjdev.expressapp.tracker.TraceItem
import org.json.JSONArray

internal val finalStatuses = setOf("已签收", "本人签收", "代签收", "已签收（代收点）", "已送达")
private val phoneRequiredPrefixes = setOf("JD")

@Composable
fun MainScreen(
    initNu: String = "",
    records: List<QueryHistoryItem> = emptyList(),
    isLoading: Boolean,
    onOpenLog: () -> Unit = {},
    onQuery: (String) -> Unit,
    onQueryWithMobile: (String, String) -> Unit = { _, _ -> },
    onRefresh: (String, () -> Unit) -> Unit = { _, _ -> },
    onRefreshAll: (() -> Unit) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onExport: () -> Unit = {},
    onImport: (List<QueryHistoryItem>) -> Unit = {},
    onImportSfLocal: (String, String) -> Unit = { _, _ -> }
) {
    var nu by rememberSaveable(initNu) { mutableStateOf(initNu) }
    var expandedNus by remember { mutableStateOf(setOf<String>()) }
    var refreshingNus by remember { mutableStateOf(setOf<String>()) }
    var refreshingAll by remember { mutableStateOf(false) }
    var showPhoneDialog by remember { mutableStateOf(false) }
    var phoneInput by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importFromUri(context, it, onImport, onImportSfLocal, nu) }
    }

    fun submitQuery() {
        val value = nu.trim()
        if (value.isEmpty() || isLoading) return
        if (phoneRequiredPrefixes.any { value.uppercase().startsWith(it) }) {
            phoneInput = ""
            showPhoneDialog = true
        } else {
            onQuery(value)
        }
    }

    if (showPhoneDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneDialog = false },
            shape = ExpressivePanelShape,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            },
            title = { Text("验证手机尾号") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("京东快递需要收件手机号后 4 位。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { value ->
                            if (value.length <= 4 && value.all(Char::isDigit)) phoneInput = value
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("手机号后 4 位") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (phoneInput.length == 4) {
                                showPhoneDialog = false
                                onQueryWithMobile(nu.trim(), phoneInput)
                            }
                        }),
                        shape = RoundedCornerShape(18.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = phoneInput.length == 4,
                    onClick = {
                        showPhoneDialog = false
                        onQueryWithMobile(nu.trim(), phoneInput)
                    }
                ) { Text("继续查询") }
            },
            dismissButton = { TextButton(onClick = { showPhoneDialog = false }) { Text("取消") } }
        )
    }

    val sorted = remember(records) {
        records.sortedWith(
            compareBy<QueryHistoryItem> { it.data.delivered }
                .thenBy { it.data.companyName }
                .thenByDescending { it.queryTime }
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                AppHeader(
                    recordCount = records.size,
                    refreshableCount = records.count { it.data.isRefreshable },
                    refreshingAll = refreshingAll,
                    onRefreshAll = {
                        refreshingAll = true
                        onRefreshAll { refreshingAll = false }
                    },
                    onOpenLog = onOpenLog,
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onExport = onExport
                )
            }

            item(key = "search") {
                TrackingSearch(
                    nu = nu,
                    isLoading = isLoading,
                    onNuChange = { nu = it },
                    onClear = { nu = "" },
                    onSubmit = ::submitQuery
                )
            }

            if (records.isNotEmpty()) {
                item(key = "summary") { StatusSummary(records) }
                item(key = "section") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("最近运单", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Text(
                            "${records.size} 件",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(sorted, key = { it.nu }) { record ->
                    val expanded = record.nu in expandedNus
                    val refreshing = record.nu in refreshingNus
                    RecordCard(
                        record = record,
                        accent = statusColor(record.data),
                        expanded = expanded,
                        refreshing = refreshing,
                        onToggle = {
                            expandedNus = if (expanded) expandedNus - record.nu else expandedNus + record.nu
                        },
                        onRefresh = {
                            refreshingNus = refreshingNus + record.nu
                            onRefresh(record.nu) { refreshingNus = refreshingNus - record.nu }
                        },
                        onDelete = { onDelete(record.nu) }
                    )
                }
            } else {
                item(key = "empty") { EmptyState() }
            }
        }
    }
}

@Composable
private fun AppHeader(
    recordCount: Int,
    refreshableCount: Int,
    refreshingAll: Boolean,
    onRefreshAll: () -> Unit,
    onOpenLog: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("快", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("快递查询", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (recordCount == 0) "查单号，跟进每一次移动" else "$recordCount 件包裹正在列表中",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (refreshableCount > 0) {
            FilledIconButton(
                onClick = onRefreshAll,
                enabled = !refreshingAll,
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                if (refreshingAll) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = "刷新全部")
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(46.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("查看日志") },
                    leadingIcon = {
                        Text(
                            "!", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { menuExpanded = false; onOpenLog() }
                )
                DropdownMenuItem(
                    text = { Text("导入记录") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = { menuExpanded = false; onImport() }
                )
                DropdownMenuItem(
                    text = { Text("导出记录") },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    enabled = recordCount > 0,
                    onClick = { menuExpanded = false; onExport() }
                )
            }
        }
    }
}

@Composable
private fun TrackingSearch(
    nu: String,
    isLoading: Boolean,
    onNuChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressivePanelShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("追踪新包裹", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nu,
                    onValueChange = onNuChange,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    singleLine = true,
                    label = { Text("运单号") },
                    placeholder = { Text("SF、JD 或其他单号") },
                    trailingIcon = {
                        if (nu.isNotEmpty() && !isLoading) {
                            IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "清空") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    shape = RoundedCornerShape(18.dp)
                )
                FilledIconButton(
                    onClick = onSubmit,
                    enabled = nu.isNotBlank() && !isLoading,
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primaryContainer)
                    else Icon(Icons.Default.Search, contentDescription = "查询")
                }
            }
        }
    }
}

@Composable
private fun StatusSummary(records: List<QueryHistoryItem>) {
    val stats = listOf(
        Triple("全部", records.size, MaterialTheme.colorScheme.primary),
        Triple("运输", records.count { !it.data.delivered && it.data.statusTag != "到达代收点" }, MaterialTheme.colorScheme.secondary),
        Triple("待取", records.count { it.data.statusTag == "到达代收点" }, MaterialTheme.colorScheme.tertiary),
        Triple("签收", records.count { it.data.delivered }, MaterialTheme.colorScheme.onSurfaceVariant)
    )
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp)) {
            stats.forEach { (label, value, color) ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Spacer(Modifier.height(6.dp))
                    Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun statusColor(data: ExpressData): Color = when {
    data.delivered || data.statusTag in finalStatuses -> MaterialTheme.colorScheme.primary
    data.statusTag == "到达代收点" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

internal fun importFromUri(
    context: Context,
    uri: Uri,
    onImport: (List<QueryHistoryItem>) -> Unit,
    onImportSfLocal: (String, String) -> Unit,
    defaultNu: String
) {
    try {
        val input = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
        val fileName = uri.lastPathSegment ?: ""
        if (fileName.endsWith(".txt", ignoreCase = true) || fileName.endsWith(".sf.txt", ignoreCase = true)) {
            onImportSfLocal(defaultNu.ifEmpty { fileName }, input)
        } else {
            importFromJson(input, onImport)
        }
    } catch (_: Exception) {
    }
}

private fun importFromJson(text: String, onResult: (List<QueryHistoryItem>) -> Unit) {
    try {
        val arr = JSONArray(text)
        val items = mutableListOf<QueryHistoryItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val traces = obj.optJSONArray("traces") ?: JSONArray()
            val traceList = mutableListOf<TraceItem>()
            for (j in 0 until traces.length()) {
                val trace = traces.getJSONObject(j)
                traceList.add(TraceItem(trace.optLong("timestamp", 0), trace.optString("desc", "")))
            }
            val statusTag = obj.optString("statusTag", "")
            items.add(
                QueryHistoryItem(
                    nu = obj.optString("nu", ""),
                    queryTime = obj.optLong("queryTime", System.currentTimeMillis()),
                    data = ExpressData(
                        companyName = obj.optString("companyName", ""),
                        companyCode = obj.optString("companyCode", ""),
                        tel = obj.optString("tel", ""),
                        status = obj.optString("status", ""),
                        statusCode = obj.optString("statusCode", ""),
                        from = obj.optString("from", ""),
                        to = obj.optString("to", ""),
                        latest = "",
                        traces = traceList,
                        delivered = if (obj.has("delivered")) obj.optBoolean("delivered", false)
                        else statusTag in setOf("已签收", "本人签收", "代签收", "已签收（代收点）"),
                        statusTag = statusTag
                    )
                )
            )
        }
        if (items.isNotEmpty()) onResult(items)
    } catch (_: Exception) {
    }
}
