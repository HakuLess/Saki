import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import com.saki.mahjong.data.GameState
import com.saki.mahjong.ui.GameStatusPanel
import com.saki.mahjong.service.MahjongGameService

@Composable
fun App() {
    MaterialTheme {

        val mahjongService = remember { createMahjongService() }
        var gameState by remember { mutableStateOf(GameState()) }
        var isIntercepting by remember { mutableStateOf(false) }

        // 初始化服务
        remember {
            object {
                init {
                    // 初始化麻将服务
                    mahjongService.initialize { newState ->
                        gameState = newState
                    }
                }
            }
        }

        Column(modifier = Modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            // 控制按钮
            Button(onClick = {
                if (isIntercepting) {
                    mahjongService.stopNetworkInterceptor()
                } else {
                    mahjongService.startNetworkInterceptor()
                }
                isIntercepting = !isIntercepting
            }) {
                Text(if (isIntercepting) "停止拦截网络请求" else "开始拦截网络请求")
            }

            // 游戏状态面板
            GameStatusPanel(gameState)
        }
    }
}

// 平台特定的服务创建函数
expect fun createMahjongService(): MahjongGameService

// 平台名称函数保留，供某些UI显示使用
expect fun getPlatformName(): String