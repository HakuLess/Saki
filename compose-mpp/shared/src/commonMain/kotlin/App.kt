import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mahjongcopilot.domain.service.AutomationState

@Composable
fun App() {
    MaterialTheme {
        val automationState = remember { mutableStateOf<AutomationState>(AutomationState.Disabled) }
        val clientConnected = remember { mutableStateOf(false) }
        val clientInfo = remember { mutableStateOf("未连接") }
        val logText = remember { mutableStateOf("应用已启动\n") }
        
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // 标题
            Text("雀魂助手 (Saki)", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(16.dp))
            
            // 状态卡片
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("当前状态", style = MaterialTheme.typography.h6)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text("自动化: ")
                        Text(
                            when (automationState.value) {
                                AutomationState.Enabled -> "已启用"
                                AutomationState.Disabled -> "已禁用"
                                AutomationState.Paused -> "已暂停"
                            },
                            color = when (automationState.value) {
                                AutomationState.Enabled -> androidx.compose.ui.graphics.Color.Green
                                AutomationState.Disabled -> androidx.compose.ui.graphics.Color.Gray
                                AutomationState.Paused -> androidx.compose.ui.graphics.Color.Yellow
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        Text("客户端: ")
                        Text(
                            clientInfo.value,
                            color = if (clientConnected.value) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                        )
                    }
                }
            }
            
            // 控制按钮
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Button(onClick = {
                    clientConnected.value = !clientConnected.value
                    clientInfo.value = if (clientConnected.value) "雀魂客户端 (已连接)" else "未连接"
                    logText.value += "${if (clientConnected.value) "已连接到雀魂客户端" else "已断开连接"}\n"
                }) {
                    Text(if (clientConnected.value) "断开连接" else "连接客户端")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    when (automationState.value) {
                        AutomationState.Disabled -> {
                            automationState.value = AutomationState.Enabled
                            logText.value += "自动化已启用\n"
                        }
                        AutomationState.Enabled -> {
                            automationState.value = AutomationState.Disabled
                            logText.value += "自动化已禁用\n"
                        }
                        AutomationState.Paused -> {
                            automationState.value = AutomationState.Enabled
                            logText.value += "自动化已恢复\n"
                        }
                    }
                }) {
                    Text(when (automationState.value) {
                        AutomationState.Disabled -> "启用自动化"
                        AutomationState.Enabled -> "禁用自动化"
                        AutomationState.Paused -> "恢复自动化"
                    })
                }
                if (automationState.value == AutomationState.Enabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        automationState.value = AutomationState.Paused
                        logText.value += "自动化已暂停\n"
                    }) {
                        Text("暂停")
                    }
                }
            }
            
            // 日志区域
            Card(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("日志", style = MaterialTheme.typography.h6)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = logText.value,
                        onValueChange = {},
                        modifier = Modifier.fillMaxSize(),
                        readOnly = true,
                        maxLines = Int.MAX_VALUE
                    )
                }
            }
        }
    }
}

expect fun getPlatformName(): String