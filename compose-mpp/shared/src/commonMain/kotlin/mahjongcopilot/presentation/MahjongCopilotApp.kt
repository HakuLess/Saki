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
import mahjongcopilot.data.model.*
import mahjongcopilot.data.repository.*
import mahjongcopilot.domain.service.impl.*
import kotlinx.coroutines.launch

@Composable
fun MahjongCopilotApp() {
    // 创建仓库实例
    val networkInterceptor = remember { NetworkInterceptorRepositoryImpl() }
    val protocolParser = remember { ProtocolParserRepositoryImpl() }
    val gameStateRepository = remember { GameStateRepositoryImpl() }
    
    // 创建服务实例
    val gameManager = remember { 
        GameManagerServiceImpl(networkInterceptor, protocolParser, gameStateRepository) 
    }
    val networkManager = remember { 
        NetworkManagerServiceImpl(networkInterceptor) 
    }
    
    // 状态管理
    var appState by remember { mutableStateOf(AppState()) }
    var settings by remember { mutableStateOf(getDefaultSettings()) }
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    
    val scope = rememberCoroutineScope()
    
    // 监听应用状态变化
    LaunchedEffect(gameManager) {
        gameManager.observeAppState().collect { state ->
            appState = state
        }
    }
    
    // 监听网络状态变化
    LaunchedEffect(networkManager) {
        networkManager.observeNetworkStatus().collect { status ->
            logs = logs + LogEntry(
                timestamp = System.currentTimeMillis(),
                level = LogLevel.INFO,
                message = "Network status changed to: $status"
            )
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
            text = "🀄 Mahjong Copilot",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        // 状态面板
        StatusPanel(appState = appState)
        
        // 控制面板
        ControlPanel(
            appState = appState,
            settings = settings,
            onSettingsChange = { settings = it },
            onStartGame = { 
                scope.launch {
                    val result = gameManager.startGameManager()
                    if (result.isSuccess) {
                        logs = logs + LogEntry(
                            timestamp = System.currentTimeMillis(),
                            level = LogLevel.INFO,
                            message = "Game manager started successfully"
                        )
                    } else {
                        logs = logs + LogEntry(
                            timestamp = System.currentTimeMillis(),
                            level = LogLevel.ERROR,
                            message = "Failed to start game manager: ${result.exceptionOrNull()?.message}"
                        )
                    }
                }
            },
            onStopGame = { 
                scope.launch {
                    val result = gameManager.stopGameManager()
                    if (result.isSuccess) {
                        logs = logs + LogEntry(
                            timestamp = System.currentTimeMillis(),
                            level = LogLevel.INFO,
                            message = "Game manager stopped successfully"
                        )
                    } else {
                        logs = logs + LogEntry(
                            timestamp = System.currentTimeMillis(),
                            level = LogLevel.ERROR,
                            message = "Failed to stop game manager: ${result.exceptionOrNull()?.message}"
                        )
                    }
                }
            }
        )
        
        // 游戏信息面板
        GameInfoPanel(gameState = appState.currentGame)
        
        // AI 决策面板
        AiDecisionPanel(decision = appState.lastDecision)
        
        // 日志面板
        LogPanel(logs = logs)
    }
}

@Composable
fun StatusPanel(appState: AppState) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "状态信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusItem(
                    label = "连接状态",
                    value = if (appState.isConnected) "已连接" else "未连接",
                    isActive = appState.isConnected
                )
                
                StatusItem(
                    label = "游戏状态",
                    value = if (appState.isInGame) "游戏中" else "等待中",
                    isActive = appState.isInGame
                )
            }
            
            if (appState.errorMessage != null) {
                Text(
                    text = "错误: ${appState.errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun StatusItem(
    label: String,
    value: String,
    isActive: Boolean
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ControlPanel(
    appState: AppState,
    settings: GameSettings,
    onSettingsChange: (GameSettings) -> Unit,
    onStartGame: () -> Unit,
    onStopGame: () -> Unit
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
                    onClick = onStartGame,
                    enabled = !appState.isInGame,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("启动游戏")
                }
                
                Button(
                    onClick = onStopGame,
                    enabled = appState.isInGame,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("停止游戏")
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = settings.autoPlay,
                    onCheckedChange = { 
                        onSettingsChange(settings.copy(autoPlay = it))
                    },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "自动打牌",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = settings.showOverlay,
                    onCheckedChange = { 
                        onSettingsChange(settings.copy(showOverlay = it))
                    },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "显示覆盖",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun GameInfoPanel(gameState: GameState?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "游戏信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (gameState != null) {
                Text("游戏ID: ${gameState.gameId}")
                Text("模式: ${if (gameState.mode == GameMode.FOUR_PLAYER) "四人麻将" else "三人麻将"}")
                Text("当前局: ${gameState.currentKyoku}局 ${gameState.currentHonba}本场")
                Text("当前玩家: ${gameState.currentPlayer.name}")
                Text("分数: ${gameState.scores.joinToString(", ")}")
            } else {
                Text(
                    text = "暂无游戏信息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AiDecisionPanel(decision: AiDecision?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "AI 决策",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (decision != null) {
                Text("动作: ${decision.action.type.name}")
                Text("置信度: ${(decision.confidence * 100).toInt()}%")
                Text("处理时间: ${decision.processingTime}ms")
                if (decision.reasoning != null) {
                    Text("推理: ${decision.reasoning}")
                }
            } else {
                Text(
                    text = "暂无决策",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LogPanel(logs: List<LogEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "日志",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            LazyColumn(
                modifier = Modifier.height(200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs.takeLast(50)) { log -> // 只显示最近50条日志
                    Text(
                        text = "[${log.level.name}] ${log.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when (log.level) {
                            LogLevel.ERROR -> MaterialTheme.colorScheme.error
                            LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

private fun getDefaultSettings(): GameSettings {
    return GameSettings(
        aiModel = AiModelConfig(
            type = AiModelType.LOCAL,
            name = "默认模型",
            modelPath = "models/mortal.pth"
        ),
        networkSettings = NetworkSettings(),
        automationSettings = AutomationSettings()
    )
}
