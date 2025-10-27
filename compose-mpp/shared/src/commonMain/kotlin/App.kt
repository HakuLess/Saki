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
import collector.GameStateCollector
import model.*
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
        
        // 添加调试日志
        LaunchedEffect(stateFlow, gameState) {
            println("[App] stateFlow: $stateFlow")
            println("[App] gameState: $gameState")
            println("[App] isCollecting: $isCollecting")
            println("[App] collector: $collector")
        }
        
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
                
                Button(onClick = {
                    if (!isCollecting) {
                        println("[App] 开始启动数据收集")
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
                        println("[App] 数据收集已启动，stateFlow: $stateFlow")
                    } else {
                        println("[App] 停止数据收集")
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
                
                // 添加状态显示
                Text("数据收集状态: ${if (isCollecting) "运行中" else "已停止"}")
                Text("GameState: ${if (gameState != null) "已获取" else "未获取"}")
                
                // Display game state
                println("[App] 准备显示游戏状态，gameState: $gameState")
                gameState?.let { state ->
                    println("[App] gameState不为null，显示手牌面板")
                    // 手牌面板
                    HandTilesPanel(state)
                    
                    // 游戏状态面板
                    GameStatePanel(state)
                } ?: run {
                    println("[App] gameState为null，显示等待提示")
                    Text(
                        "等待游戏数据...",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(16.dp)
                    )
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
fun HandTilesPanel(gameState: GameState) {
    // 添加调试日志
    println("[HandTilesPanel] 开始渲染手牌面板")
    println("[HandTilesPanel] gameState.isInGame: ${gameState.isInGame}")
    println("[HandTilesPanel] gameState.players.size: ${gameState.players.size}")
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "手牌分析", 
                style = MaterialTheme.typography.h5,
                color = MaterialTheme.colors.primary
            )
            Spacer(Modifier.height(12.dp))
            
            val myPlayer = gameState.players.getOrNull(0)
            println("[HandTilesPanel] myPlayer: $myPlayer")
            
            myPlayer?.let { player ->
                println("[HandTilesPanel] player.handTiles.size: ${player.handTiles.size}")
                println("[HandTilesPanel] player.handTiles: ${player.handTiles}")
                
                // 手牌展示区域
                Text(
                    "当前手牌 (${player.handTiles.size}张):",
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (player.handTiles.isEmpty()) {
                    Text(
                        "暂无手牌数据",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        items(player.handTiles.sortedWith(compareBy({ it.type }, { it.number }))) { tile ->
                            println("[HandTilesPanel] 渲染牌: $tile")
                            TileCard(
                                tile = tile,
                                modifier = Modifier.width(60.dp).height(80.dp)
                            )
                        }
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // 牌型分析
                val handAnalysis = analyzeHand(player.handTiles)
                println("[HandTilesPanel] handAnalysis: $handAnalysis")
                
                Text(
                    "牌型分析:",
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (handAnalysis.isEmpty()) {
                    Text(
                        "暂无分析数据",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        handAnalysis.forEach { analysis ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    "• ${analysis.type}:",
                                    style = MaterialTheme.typography.body2,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                                    modifier = Modifier.width(80.dp)
                                )
                                Text(
                                    analysis.description,
                                    style = MaterialTheme.typography.body2,
                                    color = when (analysis.priority) {
                                        "高" -> Color(0xFF4CAF50)
                                        "中" -> Color(0xFFFF9800)
                                        else -> MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                    }
                                )
                            }
                        }
                    }
                }
            } ?: run {
                println("[HandTilesPanel] myPlayer 为 null，显示无数据提示")
                Text(
                    "等待游戏数据...",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

data class HandAnalysis(
    val type: String,
    val description: String,
    val priority: String
)

fun analyzeHand(tiles: List<Tile>): List<HandAnalysis> {
    val analysis = mutableListOf<HandAnalysis>()
    
    if (tiles.isEmpty()) return analysis
    
    // 按类型分组
    val tilesByType = tiles.groupBy { it.type }
    val tileCounts = tiles.groupingBy { "${it.type}-${it.number}" }.eachCount()
    
    // 检查对子
    val pairs = tileCounts.filter { it.value >= 2 }
    if (pairs.isNotEmpty()) {
        analysis.add(HandAnalysis(
            "对子",
            "${pairs.size}个对子 (${pairs.keys.joinToString(", ") { it.split("-")[1] }})",
            if (pairs.size >= 2) "中" else "低"
        ))
    }
    
    // 检查刻子
    val triplets = tileCounts.filter { it.value >= 3 }
    if (triplets.isNotEmpty()) {
        analysis.add(HandAnalysis(
            "刻子",
            "${triplets.size}个刻子",
            "高"
        ))
    }
    
    // 检查顺子可能性
    tilesByType.forEach { (type, tilesOfType) ->
        if (type != TileType.HONOR) {
            val sequences = findSequences(tilesOfType)
            if (sequences.isNotEmpty()) {
                analysis.add(HandAnalysis(
                    "顺子",
                    "${sequences.size}个${getTileSymbol(Tile(type, 1))}顺子",
                    "中"
                ))
            }
        }
    }
    
    // 检查字牌
    val honorTiles = tilesByType[TileType.HONOR]?.size ?: 0
    if (honorTiles > 0) {
        analysis.add(HandAnalysis(
            "字牌",
            "${honorTiles}张字牌",
            if (honorTiles >= 3) "中" else "低"
        ))
    }
    
    return analysis
}

fun findSequences(tiles: List<Tile>): List<List<Tile>> {
    val sequences = mutableListOf<List<Tile>>()
    val sortedTiles = tiles.sortedBy { it.number }
    
    var i = 0
    while (i < sortedTiles.size - 2) {
        val current = sortedTiles[i]
        val next1 = sortedTiles.getOrNull(i + 1)
        val next2 = sortedTiles.getOrNull(i + 2)
        
        if (next1 != null && next2 != null &&
            next1.number == current.number + 1 &&
            next2.number == current.number + 2) {
            sequences.add(listOf(current, next1, next2))
            i += 3
        } else {
            i++
        }
    }
    
    return sequences
}

@Composable
fun GameStatePanel(gameState: GameState) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("游戏状态", style = MaterialTheme.typography.h6)
            Spacer(Modifier.height(8.dp))
            
            Text("游戏中: ${if (gameState.isInGame) "是" else "否"}")
            Text("当前局数: ${gameState.round}")
            Text("当前玩家: ${gameState.currentPlayer + 1}")
            Text("剩余牌数: ${gameState.remainingTiles}")
            
            Spacer(Modifier.height(12.dp))
            
            // Display available actions
            Text("可用操作:", style = MaterialTheme.typography.subtitle1)
            Row {
                if (gameState.canChi) Text("吃 ", color = MaterialTheme.colors.primary)
                if (gameState.canPon) Text("碰 ", color = MaterialTheme.colors.primary)
                if (gameState.canKan) Text("杠 ", color = MaterialTheme.colors.primary)
                if (gameState.canTsumo) Text("自摸 ", color = MaterialTheme.colors.secondary)
                if (gameState.canRon) Text("荣和 ", color = MaterialTheme.colors.secondary)
            }
        }
    }
}

@Composable
fun TileCard(tile: Tile, modifier: Modifier = Modifier) {
    val tileColor = when (tile.type) {
        TileType.MAN -> Color(0xFF4CAF50) // 绿色 - 万子
        TileType.PIN -> Color(0xFF2196F3) // 蓝色 - 筒子
        TileType.SOU -> Color(0xFFFF9800) // 橙色 - 索子
        TileType.HONOR -> Color(0xFFE91E63) // 粉色 - 字牌
    }
    
    Card(
        modifier = modifier.padding(2.dp),
        elevation = 4.dp,
        backgroundColor = tileColor.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = getTileSymbol(tile),
                style = MaterialTheme.typography.h6,
                color = tileColor
            )
            Text(
                text = tile.number.toString(),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

fun getTileSymbol(tile: Tile): String {
    return when (tile.type) {
        TileType.MAN -> "万"
        TileType.PIN -> "筒"
        TileType.SOU -> "索"
        TileType.HONOR -> when (tile.number) {
            1 -> "东"
            2 -> "南"
            3 -> "西"
            4 -> "北"
            5 -> "白"
            6 -> "发"
            7 -> "中"
            else -> "?"
        }
    }
}

expect fun getPlatformName(): String

// 在桌面端实现为打开系统浏览器
expect fun openUrl(url: String): Boolean