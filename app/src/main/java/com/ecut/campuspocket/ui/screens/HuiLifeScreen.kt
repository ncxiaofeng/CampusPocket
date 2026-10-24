package com.ecut.campuspocket.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.theme.ChartPrimary

private const val HUI_ALIPAY_APP_ID = "2019061465519660"

/**
 * 慧生活798：官方入口是支付宝小程序。
 *
 * 它的原生 App 有 InnoSecure 加固 + 证书固定，H5 只是下载 / 说明页，
 * 因此 App 第一版只做“打开支付宝小程序”的入口，不读取设备状态。
 */
@Composable
fun HuiLifeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "慧生活798",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "支付宝小程序 · 扫码启动",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge("水", ChartPrimary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "打开慧生活798",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "饮水机 / 吹风机 / 淋浴",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "官方入口是支付宝小程序。点下面按钮打开小程序，再用支付宝扫描机器上的二维码，" +
                                "选择设备与方案后完成支付和启动。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { openHuiLifeAlipay(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("在支付宝打开慧生活798")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "首次打开 ColorOS 会询问“冻小梨想要打开支付宝小程序”，勾选“始终允许”即可。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionHeader("使用顺序")
                AppCard {
                    Column {
                        StepRow("1", "用支付宝扫饮水机 / 吹风机上的二维码")
                        StepRow("2", "在小程序里选择设备与用量 / 方案")
                        StepRow("3", "支付后启动；筒自洁、启动等操作也在小程序内")
                    }
                }
            }

            item {
                AppCard {
                    Column {
                        Text(
                            "为什么 App 不直接读取设备状态？",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "慧生活798 的原生 App 使用 InnoSecure 加固与证书固定，H5 只是下载页 / 使用说明，" +
                                "没有独立的登录与设备接口。第一版只提供官方小程序入口，不伪造设备数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "注意：官方每年有一段时间会关闭小程序设备页、引导用户下载 App；" +
                                "这期间请直接用慧生活798 官方 App 扫码使用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(index: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index,
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun openHuiLifeAlipay(context: Context) {
    val uri = Uri.parse(
        "alipays://platformapi/startapp?appId=$HUI_ALIPAY_APP_ID&page=pages/index/index"
    )
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "未安装支付宝，无法打开慧生活798", Toast.LENGTH_SHORT).show()
        }
}
