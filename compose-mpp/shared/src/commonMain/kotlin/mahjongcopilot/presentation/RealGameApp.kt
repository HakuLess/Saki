package mahjongcopilot.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import mahjongcopilot.data.model.*
import mahjongcopilot.data.model.enums.*
import mahjongcopilot.domain.service.impl.MitmNetworkManagerServiceImpl
import mahjongcopilot.domain.service.impl.GameStateManagerServiceImpl
import mahjongcopilot.domain.repository.GameStateRepository
import mahjongcopilot.data.repository.ProtocolParserRepositoryImpl
import mahjongcopilot.data.repository.GameStateRepositoryImpl
import java.io.File

/**
 * 真实游戏应用 - 专门用于显示拦截到的真实雀魂游戏数据
 */
@Composable
fun RealGameApp() {
    // 创建服务实例
    val protocolParser = remember { ProtocolParserRepositoryImpl() }
    val gameStateRepository = remember { GameStateRepositoryImpl() }
    val networkManager = remember { MitmNetworkManagerServiceImpl(protocolParser) }
    val gameStateManager = remember { GameStateManagerServiceImpl(gameStateRepository) }
    
    // 状态管理
    var networkStatus by remember { mutableStateOf(NetworkStatus.DISCONNECTED) }
    var gameState by remember { mutableStateOf<GameState?>(null) }
    var capturedMessages by remember { mutableStateOf(0) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    
    val scope = rememberCoroutineScope()
    
    // 监听网络状态
    LaunchedEffect(networkManager) {
        networkManager.observeNetworkStatus().collect { status ->
            networkStatus = status
            logs = logs + "网络状态: $status"
        }
    }
    
    // 监听游戏状态
    LaunchedEffect(gameStateRepository) {
        gameStateRepository.observeGameState().collect { state ->
            gameState = state
            if (state != null) {
                logs = logs + "游戏状态更新: ${state.gameId}"
            }
        }
    }
    
    // 监听捕获的消息
    LaunchedEffect(networkManager) {
        networkManager.capturedMessages.collect { message ->
            capturedMessages++
            logs = logs + "捕获消息 #$capturedMessages: ${message.method}"
            
            // 更新游戏状态
            gameStateManager.updateGameState(message)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "🀄 雀魂游戏拦截器",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        // 状态面板
        RealStatusPanel(
            networkStatus = networkStatus,
            capturedMessages = capturedMessages,
            gameState = gameState
        )
        
        // 控制面板
        RealControlPanel(
            networkStatus = networkStatus,
            onStartCapture = {
                scope.launch {
                    networkManager.startNetworkInterception()
                }
            },
            onStopCapture = {
                scope.launch {
                    networkManager.stopNetworkInterception()
                }
            }
        )
        
        // 游戏信息面板
        RealGameInfoPanel(gameState = gameState)
        
        // 手牌面板
        RealHandPanel(gameState = gameState)
        
        // 日志面板
        RealLogPanel(logs = logs)
    }
}

@Composable
fun RealStatusPanel(
    networkStatus: NetworkStatus,
    capturedMessages: Int,
    gameState: GameState?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "拦截状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusItem(
                    label = "网络拦截",
                    value = when (networkStatus) {
                        NetworkStatus.CONNECTED -> "已连接"
                        NetworkStatus.CONNECTING -> "连接中"
                        NetworkStatus.DISCONNECTED -> "未连接"
                        NetworkStatus.ERROR -> "错误"
                    },
                    isActive = networkStatus == NetworkStatus.CONNECTED
                )
                
                StatusItem(
                    label = "捕获消息",
                    value = capturedMessages.toString(),
                    isActive = capturedMessages > 0
                )
                
                StatusItem(
                    label = "游戏状态",
                    value = if (gameState != null) "进行中" else "等待中",
                    isActive = gameState != null
                )
            }
        }
    }
}

@Composable
fun RealControlPanel(
    networkStatus: NetworkStatus,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "控制面板",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartCapture,
                    enabled = networkStatus != NetworkStatus.CONNECTED,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("开始拦截")
                }
                
                Button(
                    onClick = onStopCapture,
                    enabled = networkStatus == NetworkStatus.CONNECTED,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("停止拦截")
                }
            }
            
            Text(
                text = "使用说明:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "1. 点击'开始拦截'启动mitmproxy网络拦截\n" +
                       "2. 启动雀魂游戏并开始对局\n" +
                       "3. 系统将自动捕获游戏数据并显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RealGameInfoPanel(gameState: GameState?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "真实游戏信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (gameState != null) {
                Text("游戏ID: ${gameState.gameId}")
                Text("模式: ${gameState.mode}")
                Text("当前局: ${gameState.currentKyoku}局 ${gameState.currentHonba}本场")
                Text("当前回合: ${gameState.currentPlayer.index + 1}号位")
                Text("分数: ${gameState.scores.joinToString(", ")}")
                Text("宝牌指示牌: ${gameState.doraMarkers.joinToString(", ")}")
            } else {
                Text(
                    text = "等待游戏数据...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RealHandPanel(gameState: GameState?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "手牌信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (gameState != null && gameState.players.isNotEmpty()) {
                gameState.players.forEachIndexed { index, player ->
                    Text(
                        text = "玩家${index + 1} (${player.name}): ${player.hand.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // 显示当前玩家的手牌详细信息
                val currentPlayer = gameState.players.getOrNull(gameState.currentPlayer.index)
                if (currentPlayer != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "当前玩家手牌:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("手牌: ${currentPlayer.hand.joinToString(", ") { it.type.value }}")
                    Text("面子: ${currentPlayer.melds.size}个")
                }
            } else {
                Text(
                    text = "等待手牌数据...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RealLogPanel(logs: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "拦截日志",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            LazyColumn(
                modifier = Modifier.height(200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs.takeLast(50)) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

