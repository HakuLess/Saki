import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp

import com.saki.mahjong.data.GameState
import com.saki.mahjong.data.GameStatus
import com.saki.mahjong.data.PlayerInfo
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

        // 测试方法：创建模拟游戏状态
        fun createTestGameState(): GameState {
            // 创建简单的模拟游戏状态
            return GameState(
                status = GameStatus.IN_PROGRESS,
                round = 1,
                honba = 0,
                kyotaku = 1,
                currentPlayerIndex = 0,
                players = listOf(
                    PlayerInfo("玩家1", 25000, 0, true, false),
                    PlayerInfo("玩家2", 25000, 1, false, false),
                    PlayerInfo("玩家3", 25000, 2, false, false),
                    PlayerInfo("玩家4", 25000, 3, false, false)
                ),
                handTiles = emptyList(),
                drawnTile = null,
                river = emptyList(),
                doraIndicators = emptyList(),
                uradoraIndicators = emptyList()
            )
        }
        
        Column(modifier = Modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            // 控制按钮
            Button(
                onClick = {
                    if (isIntercepting) {
                        mahjongService.stopNetworkInterceptor()
                    } else {
                        mahjongService.startNetworkInterceptor()
                    }
                    isIntercepting = !isIntercepting
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text(if (isIntercepting) "停止拦截网络请求" else "开始拦截网络请求")
            }
            
            // 测试按钮：使用模拟数据更新UI
            Button(
                onClick = {
                    // 直接使用模拟数据更新游戏状态
                    gameState = createTestGameState()
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("使用模拟数据测试UI")
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