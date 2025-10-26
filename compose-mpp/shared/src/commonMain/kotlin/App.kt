import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
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
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
@Composable
fun App() {
    MaterialTheme {
        // 启动与登录 UI 状态
        var majsoulUrl by remember { mutableStateOf("https://game.maj-soul.com/1/") }
        var openOk by remember { mutableStateOf(false) }
        var isLoggedIn by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("启动与登录", style = MaterialTheme.typography.h6)
            Spacer(Modifier.padding(8.dp))

            TextField(
                value = majsoulUrl,
                onValueChange = { majsoulUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("雀魂网址") },
                singleLine = true
            )

            Spacer(Modifier.padding(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    openOk = openUrl(majsoulUrl)
                }) {
                    Text("打开雀魂")
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { isLoggedIn = true }) {
                    Text("我已登录")
                }
            }

            AnimatedVisibility(openOk) {
                Text("已在默认浏览器打开，请登录后返回此窗口。")
            }
            AnimatedVisibility(isLoggedIn) {
                Text("登录状态：已登录")
            }
        }
    }
}

expect fun getPlatformName(): String

// 在桌面端实现为打开系统浏览器
expect fun openUrl(url: String): Boolean