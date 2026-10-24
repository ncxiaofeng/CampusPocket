package com.ecut.campuspocket.ui.screens

import android.text.Html
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.remote.jw.JwNotice
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.theme.CourseTeal
import com.ecut.campuspocket.ui.theme.ErrorRed

@Composable
fun NoticesScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenOfficial: () -> Unit,
    onOpenNoticeOfficial: (JwNotice) -> Unit,
    viewModel: NoticesViewModel = viewModel(factory = NoticesViewModel.Factory)
) {
    val selected = viewModel.selectedNotice

    BackHandler(enabled = selected != null) { viewModel.closeNotice() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable {
                        if (selected != null) viewModel.closeNotice() else onBack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (selected != null) "公告详情" else "通知公告",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    if (selected != null) {
                        onOpenNoticeOfficial(selected)
                    } else {
                        onOpenOfficial()
                    }
                }
            ) {
                Text("官方页", style = MaterialTheme.typography.labelMedium)
            }
            if (selected == null) {
                TextButton(onClick = { viewModel.refresh() }, enabled = !viewModel.isLoading) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("加载中")
                    } else {
                        Text("刷新")
                    }
                }
            }
        }

        if (selected != null) {
            NoticeDetail(
                notice = selected,
                onOpenOfficial = { onOpenNoticeOfficial(selected) },
                modifier = Modifier.weight(1f)
            )
        } else {
            NoticeList(
                viewModel = viewModel,
                onOpenLogin = onOpenLogin,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NoticeList(
    viewModel: NoticesViewModel,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = viewModel.searchText,
            onValueChange = viewModel::updateSearchText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            placeholder = { Text("搜索公告标题") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    viewModel.search()
                }
            ),
            trailingIcon = {
                TextButton(onClick = {
                    focusManager.clearFocus()
                    viewModel.search()
                }) { Text("搜索") }
            }
        )

        if (viewModel.keyword.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "筛选：${viewModel.keyword}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.clearSearch() }) { Text("清除") }
            }
        }

        if (viewModel.message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = viewModel.message.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (viewModel.needsLogin) {
                        ErrorRed
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
                if (viewModel.needsLogin) {
                    TextButton(
                        onClick = {
                            viewModel.clearMessage()
                            onOpenLogin()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) { Text("去登录") }
                } else {
                    TextButton(
                        onClick = { viewModel.clearMessage() },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) { Text("知道了") }
                }
            }
        }

        when {
            viewModel.isLoading && viewModel.notices.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "正在读取通知公告…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            viewModel.notices.isEmpty() -> {
                EmptyNotices(
                    filtered = viewModel.keyword.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 6.dp,
                        bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(viewModel.notices, key = { it.id.ifBlank { it.title } }) { notice ->
                        NoticeRow(
                            notice = notice,
                            onClick = { viewModel.openNotice(notice) }
                        )
                    }

                    if (viewModel.hasMore) {
                        item(key = "more") {
                            Button(
                                onClick = { viewModel.loadMore() },
                                enabled = !viewModel.isLoadingMore,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (viewModel.isLoadingMore) {
                                    CircularProgressIndicator(
                                        Modifier.size(15.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("加载中")
                                } else {
                                    Text("加载更多")
                                }
                            }
                        }
                    }

                    item(key = "note") {
                        Text(
                            text = "公告来自教务系统通知公告，只读；正文中的图片与表格请打开官方页查看。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(notice: JwNotice, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        if (notice.publisher.isNotBlank()) append(notice.publisher)
                        if (notice.publishedAt.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(notice.publishedAt)
                        }
                    }.ifBlank { "教务通知" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "›",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoticeDetail(
    notice: JwNotice,
    onOpenOfficial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plainContent = remember(notice.id, notice.contentHtml) {
        if (notice.contentHtml.isBlank()) {
            ""
        } else {
            val lines = Html.fromHtml(notice.contentHtml, Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace('\u00A0', ' ')
                .replace("\uFFFC", "")
                .replace(Regex("""[\t ]+"""), " ")
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            // 正文 HTML 自带标题，详情页已经展示过，去掉重复的第一行。
            val body = if (lines.firstOrNull() == notice.title.trim()) lines.drop(1) else lines
            body.joinToString("\n\n")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            Column {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildString {
                        if (notice.publisher.isNotBlank()) append("发布人：").append(notice.publisher)
                        if (notice.publishedAt.isNotBlank()) {
                            if (isNotEmpty()) append("　")
                            append("发布时间：").append(notice.publishedAt)
                        }
                    }.ifBlank { "教务通知" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "divider") { ThinDivider() }

        item(key = "content") {
            if (plainContent.isBlank()) {
                Text(
                    text = "这篇公告没有正文，请打开官方页面查看。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SelectionContainer {
                    Text(
                        text = plainContent,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 24.sp
                    )
                }
            }
        }

        item(key = "official") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onOpenOfficial,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("打开官方页面查看完整排版") }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "正文来自教务系统，只读；如与官方页面不一致，以官方页面为准。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmptyNotices(filtered: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AppCard(modifier = Modifier.padding(horizontal = 24.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                IconBadge(text = "告", color = CourseTeal, size = 54.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (filtered) "没有匹配的公告" else "暂无通知公告",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (filtered) {
                        "换个关键词再试试。"
                    } else {
                        "教务发布通知后会显示在这里，也可以直接打开官方页面。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
