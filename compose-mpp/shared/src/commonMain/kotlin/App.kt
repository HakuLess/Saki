import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.Switch
import androidx.compose.material.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlinx.coroutines.launch
import model.GameState
import model.Tile
import collector.SteamInterceptCollector
import collector.CollectorConfig
import proxinject.ProxinjectStatus

@OptIn(ExperimentalResourceApi::class)
@Composable
fun App() {
    MaterialTheme {
        // 启动与登录 UI 状态
        var isLoggedIn by remember { mutableStateOf(false) }
        
        // Steam 客户端采集器
        var collector by remember { mutableStateOf<SteamInterceptCollector?>(null) }
        var isCollecting by remember { mutableStateOf(false) }
        var stateFlow by remember { mutableStateOf<kotlinx.coroutines.flow.Flow<GameState>?>(null) }
        val gameState by (stateFlow?.collectAsState(initial = null) ?: remember { mutableStateOf<GameState?>(null) })
        
        // Proxinject 配置状态
        var enableProxinject by remember { mutableStateOf(true) }
        
        // Proxinject 状态相关
        var proxinjectStatus by remember { mutableStateOf<ProxinjectStatus?>(null) }
        var proxinjectLogs by remember { mutableStateOf<List<String>>(emptyList()) }

        // 监听 proxinject 状态
        LaunchedEffect(collector) {
            if (collector != null) {
                collector!!.proxinjectStatus?.let { statusFlow ->
                    launch {
                        statusFlow.collect { status ->
                            proxinjectStatus = status
                        }
                    }
                }
                
                // 监听 proxinject 日志
                collector!!.proxinjectLogs?.let { logsFlow ->
                    launch {
                        logsFlow.collect { log ->
                            proxinjectLogs = (proxinjectLogs + log).takeLast(50) // 保留最近50条日志
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Steam 启动状态
            Text("Steam 客户端", style = MaterialTheme.typography.h6)
            Spacer(Modifier.padding(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { isLoggedIn = true }) {
                    Text("我已在 Steam 启动并进入雀魂")
                }
            }

            AnimatedVisibility(isLoggedIn) {
                Text("状态：Steam 已就绪")
            }
            
            // Game State Collection Section
            if (isLoggedIn) {
                Spacer(Modifier.height(24.dp))
                Divider()
                Spacer(Modifier.height(16.dp))
                
                // Proxinject 配置区域
                ProxinjectConfigPanel(
                    enableProxinject = enableProxinject,
                    onEnableProxinjectChange = { enableProxinject = it },
                    collector = collector,
                    isCollecting = isCollecting,
                    proxinjectStatus = proxinjectStatus,
                    proxinjectLogs = proxinjectLogs
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text("Game State Collection", style = MaterialTheme.typography.h6)
                Spacer(Modifier.padding(8.dp))
                
                Button(onClick = {
                    if (!isCollecting) {
                        // 使用 Steam 拦截器启动采集
                        collector = SteamInterceptCollector(
                            CollectorConfig(
                                debugMode = true,
                                steamHosts = listOf(
                                    "game.maj-soul.com",
                                    "gateway-game.maj-soul.com",
                                    "mahjongsoul.game.yo-star.com"
                                ),
                                hostPatterns = listOf("maj-soul", "mahjong", "yo-star"),
                                enableProxinject = enableProxinject  // 使用用户配置的 proxinject 设置
                            )
                        )
                        stateFlow = collector!!.startCollecting()
                        isCollecting = true
                    } else {
                        // 停止采集并释放资源
                        kotlinx.coroutines.GlobalScope.launch {
                            collector?.stopCollecting()
                            collector = null
                            stateFlow = null
                        }
                        isCollecting = false
                    }
                }) {
                    Text(if (isCollecting) "Stop Collection" else "Start Collection")
                }
                
                // Display game state
                gameState?.let { state ->
                    GameStatePanel(state)
                }
            }
        }
    }
}

@Composable
fun ProxinjectConfigPanel(
    enableProxinject: Boolean,
    onEnableProxinjectChange: (Boolean) -> Unit,
    collector: SteamInterceptCollector?,
    isCollecting: Boolean,
    proxinjectStatus: ProxinjectStatus?,
    proxinjectLogs: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Proxinject 自动代理", style = MaterialTheme.typography.h6)
            Spacer(Modifier.height(8.dp))
            
            // 启用/禁用开关
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("启用自动代理")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = enableProxinject,
                    onCheckedChange = onEnableProxinjectChange,
                    enabled = !isCollecting
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // 状态指示器
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProxinjectStatusIndicator(enableProxinject, isCollecting, proxinjectStatus)
                Text(
                    text = when {
                        !enableProxinject -> "已禁用"
                        !isCollecting -> "等待收集器启动"
                        proxinjectStatus == null -> "初始化中..."
                        else -> when (proxinjectStatus) {
                            ProxinjectStatus.STOPPED -> "已停止"
                            ProxinjectStatus.STARTING -> "启动中..."
                            ProxinjectStatus.RUNNING -> "运行中"
                            ProxinjectStatus.STOPPING -> "停止中..."
                            ProxinjectStatus.ERROR -> "错误"
                        }
                    },
                    style = MaterialTheme.typography.body2
                )
            }
            
            // 日志显示区域
            if (proxinjectLogs.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("日志:", style = MaterialTheme.typography.subtitle2)
                Card(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    backgroundColor = MaterialTheme.colors.surface,
                    elevation = 2.dp
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        reverseLayout = true // 最新日志在顶部
                    ) {
                        items(proxinjectLogs.reversed()) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.caption,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProxinjectStatusIndicator(
    isEnabled: Boolean,
    isCollecting: Boolean,
    status: ProxinjectStatus?
) {
    val (color, icon) = when {
        !isEnabled -> MaterialTheme.colors.onSurface.copy(alpha = 0.3f) to "⚫"
        !isCollecting -> MaterialTheme.colors.onSurface.copy(alpha = 0.6f) to "⏸"
        status == null -> MaterialTheme.colors.primary to "⏳"
        else -> when (status) {
            ProxinjectStatus.STOPPED -> MaterialTheme.colors.onSurface.copy(alpha = 0.6f) to "⏹"
            ProxinjectStatus.STARTING -> MaterialTheme.colors.primary to "⏳"
            ProxinjectStatus.RUNNING -> Color(0xFF4CAF50) to "✅"
            ProxinjectStatus.STOPPING -> MaterialTheme.colors.primary to "⏳"
            ProxinjectStatus.ERROR -> MaterialTheme.colors.error to "❌"
        }
    }
    
    Text(
        text = icon,
        color = color,
        style = MaterialTheme.typography.h6
    )
}

@Composable
fun GameStatePanel(gameState: GameState) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Game State", style = MaterialTheme.typography.h6)
            Spacer(Modifier.height(8.dp))
            
            Text("In Game: ${if (gameState.isInGame) "Yes" else "No"}")
            Text("Current Round: ${gameState.round}")
            Text("Current Player: ${gameState.currentPlayer + 1}")
            Text("Remaining Tiles: ${gameState.remainingTiles}")
            
            Spacer(Modifier.height(12.dp))
            
            // Display my hand tiles
            val myPlayer = gameState.players.getOrNull(0)
            myPlayer?.let { player ->
                Text("My Hand:", style = MaterialTheme.typography.subtitle1)
                Spacer(Modifier.height(4.dp))
                LazyRow {
                    items(player.handTiles) { tile ->
                        TileCard(tile)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Display available actions
            Text("Available Actions:", style = MaterialTheme.typography.subtitle1)
            Row {
                if (gameState.canChi) Text("Chi ", color = MaterialTheme.colors.primary)
                if (gameState.canPon) Text("Pon ", color = MaterialTheme.colors.primary)
                if (gameState.canKan) Text("Kan ", color = MaterialTheme.colors.primary)
                if (gameState.canRon) Text("Ron ", color = MaterialTheme.colors.secondary)
                if (gameState.canTsumo) Text("Tsumo ", color = MaterialTheme.colors.secondary)
            }
        }
    }
}

@Composable
fun TileCard(tile: Tile) {
    Card(
        modifier = Modifier.padding(2.dp),
        elevation = 2.dp
    ) {
        Text(
            text = tile.toString(),
            modifier = Modifier.padding(4.dp),
            style = MaterialTheme.typography.caption
        )
    }
}

expect fun getPlatformName(): String

// 在桌面端实现为打开系统浏览器
expect fun openUrl(url: String): Boolean