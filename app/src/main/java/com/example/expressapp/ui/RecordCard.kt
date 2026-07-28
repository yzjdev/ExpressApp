package com.example.expressapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expressapp.history.QueryHistoryItem
import com.example.expressapp.tracker.TraceItem
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordCard(
    record: QueryHistoryItem,
    accent: Color,
    expanded: Boolean,
    refreshing: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit
) {
    val data = record.data
    val context = LocalContext.current
    var copied by remember(record.nu) { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val status = data.statusTag.ifBlank { if (data.delivered) "已签收" else "运输中" }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "recordArrow"
    )

    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveCardShape)
            .combinedClickable(onClick = onToggle, onLongClick = { showDeleteDialog = true }),
        shape = ExpressiveCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 3.dp else 1.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(7.dp).background(accent))
            Column(
                modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(accent.copy(alpha = 0.18f), RoundedCornerShape(17.dp, 17.dp, 5.dp, 17.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = data.companyName.firstOrNull()?.toString() ?: "快",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = data.companyName.ifBlank { "未知快递" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "更新于 ${record.timeFormatted}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(shape = ExpressivePillShape, color = accent.copy(alpha = 0.16f)) {
                        Text(
                            text = status,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val latest = data.latest.ifBlank { data.traces.firstOrNull()?.desc.orEmpty() }
                if (latest.isNotBlank()) {
                    Text(
                        text = latest,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 3 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("运单号", record.nu))
                            copied = true
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (copied) "已复制" else record.nu,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${data.traces.size} 条轨迹",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起详情" else "展开详情",
                        modifier = Modifier.size(24.dp).rotate(arrowRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("物流详情", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (data.isRefreshable) {
                                IconButton(onClick = onRefresh, enabled = !refreshing) {
                                    if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Default.Refresh, contentDescription = "刷新此运单")
                                }
                            }
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除此运单", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        if (data.from.isNotBlank() && data.to.isNotBlank()) {
                            RouteLine(
                                from = data.from,
                                to = if (data.delivered) data.to else maskLocation(data.to),
                                accent = accent
                            )
                        }

                        if (data.traces.isEmpty()) {
                            Text("暂无物流轨迹", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column {
                                data.traces.forEachIndexed { index, trace ->
                                    TimelineStep(
                                        trace = trace,
                                        isCurrent = index == 0,
                                        isLast = index == data.traces.lastIndex,
                                        accent = accent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = ExpressivePanelShape,
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除这条运单？") },
            text = { Text("将从本机历史记录中移除 ${record.nu}。") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun RouteLine(from: String, to: String, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("寄出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(from, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier.padding(horizontal = 12.dp).size(38.dp).background(accent.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("送达", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                to,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun TimelineStep(trace: TraceItem, isCurrent: Boolean, isLast: Boolean, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 22.dp else 10.dp)
                    .background(if (isCurrent) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent) Box(Modifier.size(10.dp).background(accent, CircleShape))
            }
            if (!isLast) {
                Box(
                    Modifier
                        .weight(1f)
                        .width(2.dp)
                        .background(if (isCurrent) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 20.dp)) {
            Text(
                trace.timeFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(Modifier.height(3.dp))
            Text(
                trace.desc,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

private val provinces = listOf(
    "北京", "天津", "上海", "重庆", "河北", "山西", "辽宁", "吉林", "黑龙江",
    "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南", "湖北", "湖南",
    "广东", "海南", "四川", "贵州", "云南", "陕西", "甘肃", "青海", "台湾",
    "内蒙古", "广西", "西藏", "宁夏", "新疆", "香港", "澳门"
)

private fun maskLocation(location: String): String {
    if (location.isBlank()) return location
    for (province in provinces) {
        if (location.startsWith(province)) {
            val suffix = location.removePrefix(province).takeWhile { it == '省' || it == '市' }
            return "***" + location.removePrefix(province).removePrefix(suffix)
        }
    }
    return location
}
