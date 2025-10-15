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

// 添加对Windows平台网络拦截器的引用
import mahjongcopilot.platform.MitmNetworkInterceptor

@Composable
fun MahjongCopilotApp() {
    // 创建仓库实例
    val networkInterceptor = remember { MitmNetworkInterceptor() } // 使用Windows平台的网络拦截器
    val gameStateRepository = remember { GameStateRepositoryImpl() }
    
    // 创建服务实例
    val networkManager = remember { 
        NetworkManagerServiceImpl(networkInterceptor) 
    }
    val gameStateManager = remember {
        GameStateManagerServiceImpl(gameStateRepository)
    }
    val gameManager = remember { 
        GameManagerServiceImpl(networkManager, gameStateManager, gameStateRepository) 
    }
    val aiDecisionService = remember { AiDecisionServiceImpl() }
    val aiModelService = remember { AiModelServiceImpl() }
    val automationService = remember { AutomationServiceImpl() }
    
    // 状态管理
    var appState by remember { mutableStateOf(AppState()) }
    var settings by remember { mutableStateOf(getDefaultSettings()) }
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var networkLogs by remember { mutableStateOf<List<String>>(emptyList()) } // 网络日志
    
    val scope = rememberCoroutineScope()
    
    // 初始化 AI 模型
    LaunchedEffect(aiModelService) {
        aiModelService.initializeModels()
    }
    
    // 监听应用状态变化
    LaunchedEffect(gameManager) {
        gameManager.observeAppState().collect { state ->
            appState = state
        }
    }
    
    // 监听游戏状态变化，自动获取 AI 决策
    LaunchedEffect(gameManager) {
        gameManager.observeGameState().collect { gameState ->  
            if (gameState != null && gameState.isGameActive) {
                scope.launch {
                    val result = aiDecisionService.getDecision(gameState)
                    if (result.isSuccess) {
                        val decision = result.getOrNull()
                        if (decision != null) {
                            appState = appState.copy(lastDecision = decision)
                            
                            // 如果启用了自动化，执行自动动作
                            if (settings.automationSettings.autoDiscard) {
                                automationService.executeAutomaticAction(decision)
                            }
                            
                            logs = logs + LogEntry(
                                timestamp = System.currentTimeMillis(),
                                level = LogLevel.INFO,
                                message = "AI 决策: ${decision.reasoning}"
                            )
                        }
                    }
                }
            }
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
                    // 启动游戏管理器
                    val result = gameManager.startGameManager()
                    if (result.isSuccess) {
                        // 加载 AI 模型
                        val models = aiModelService.getAvailableModels()
                        val defaultModel = models.firstOrNull { it.isEnabled }
                        if (defaultModel != null) {
                            aiDecisionService.loadModel(defaultModel)
                        }
                        
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
                    // 停止自动化
                    automationService.disableAutomation()
                    
                    // 卸载 AI 模型
                    aiDecisionService.unloadModel()
                    
                    // 停止游戏管理器
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
            },
            onToggleAutomation = { enabled ->
                scope.launch {
                    if (enabled) {
                        automationService.enableAutomation()
                        settings = settings.copy(
                            automationSettings = settings.automationSettings.copy(autoDiscard = true)
                        )
                        logs = logs + LogEntry(
                            timestamp = System.currentTimeMillis(),
                            level = LogLevel.INFO,
                            message = "Automation enabled"
                        )
                    } else {
                        automationService.disableAutomation()
                        settings = settings.copy(
                            automationSettings = settings.automationSettings.copy(autoDiscard = false)
                        )
                        logs = logs + LogEntry(
                            timestamp = System.currentTimeMillis(),
                            level = LogLevel.INFO,
                            message = "Automation disabled"
                        )
                    }
                }
            }
        )
        
        // 游戏信息面板
        GameInfoPanel(gameState = appState.currentGame)
        
        // 手牌面板
        HandPanel(gameState = appState.currentGame)
        
        // AI 决策面板
        AiDecisionPanel(decision = appState.lastDecision)
        
        // 网络拦截日志面板
        NetworkLogPanel(logs = networkLogs)
        
        // 应用日志面板
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
                // 显示更详细的错误信息
                val displayError = if (appState.errorMessage.contains("mitmdump")) {
                    "网络拦截启动失败：请确保已安装mitmproxy并添加到系统PATH中"
                } else {
                    appState.errorMessage
                }
                
                Text(
                    text = "错误: $displayError",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                
                // 添加帮助信息
                if (appState.errorMessage.contains("mitmdump")) {
                    Text(
                        text = "提示：请参考 README_mitmproxy.md 文件了解安装和配置说明",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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
    onStopGame: () -> Unit,
    onToggleAutomation: (Boolean) -> Unit = {}
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
            
            // 自动化控制
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onToggleAutomation(true) },
                    enabled = appState.isInGame && !settings.automationSettings.autoDiscard,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("启用自动打牌")
                }
                
                Button(
                    onClick = { onToggleAutomation(false) },
                    enabled = appState.isInGame && settings.automationSettings.autoDiscard,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("禁用自动打牌")
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
                text = "应用日志",
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

// 网络拦截日志面板
@Composable
fun NetworkLogPanel(logs: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "网络拦截日志",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            LazyColumn(
                modifier = Modifier.height(150.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs.takeLast(30)) { log -> // 只显示最近30条日志
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