package com.ecut.campuspocket.capture.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecut.campuspocket.capture.CaptureStore

@Composable
fun CaptureRoot() {
    var browserUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var showLogs by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        when {
            browserUrl != null -> {
                CaptureBrowserScreen(
                    url = browserUrl.orEmpty(),
                    onBack = { browserUrl = null }
                )
            }
            showLogs -> {
                LogsScreen(onBack = { showLogs = false })
            }
            else -> {
                HomeScreen(
                    onOpenUrl = { browserUrl = it },
                    onOpenLogs = { showLogs = true }
                )
            }
        }
    }
}

@Composable
private fun LogsScreen(onBack: () -> Unit) {
    val entries by CaptureStore.entries.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(4.dp))
            Text("抓包日志", style = MaterialTheme.typography.titleLarge)
        }
        LogPanel(
            entries = entries,
            modifier = Modifier.weight(1f)
        )
    }
}
