import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saki.mahjong.service.AnalysisResult
import com.saki.mahjong.service.MahjongGameService
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

/**
 * 创建平台特定的麻将游戏服务实例
 */
expect fun createMahjongGameService() : MahjongGameService

@OptIn(ExperimentalResourceApi::class)
@Composable
fun App() {
    MaterialTheme {
        var isServiceRunning by remember { mutableStateOf(false) }
        var logMessages by remember { mutableStateOf(listOf("准备就绪")) }
        
        // 分析服务相关状态
        val analysisResult = remember { mutableStateOf<AnalysisResult?>(null) }
        val pythonServiceStatus = remember { mutableStateOf("未连接") }
        
        // 获取平台特定的实例
        val gameService = remember { createMahjongGameService() }
        val scope = rememberCoroutineScope()
        
        // 初始化游戏服务
        LaunchedEffect(Unit) {
            gameService.onGameStateUpdate = { _, result ->
                // 只在收到新的分析结果时更新UI
                if (result != null) {
                    analysisResult.value = result
                    pythonServiceStatus.value = "已连接"
                }
            }
            
            // 自动尝试连接Python分析服务
            scope.launch {
                try {
                    logMessages = logMessages + "尝试连接Python分析服务..."
                    gameService.start()
                    isServiceRunning = true
                    logMessages = logMessages + "Python分析服务启动请求已发送"
                } catch (e: Exception) {
                    logMessages = logMessages + "Python服务连接失败: ${e.message}"
                    logMessages = logMessages + "请确保已启动Python分析器服务"
                }
            }
        }
        
        // 清理资源
        DisposableEffect(Unit) {
            onDispose {
                gameService.stop()
                isServiceRunning = false
            }
        }
        
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            // 标题和控制区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "雀魂助手", style = MaterialTheme.typography.h5)
                Button(
                    onClick = {
                        scope.launch {
                            if (isServiceRunning) {
                                // 停止服务
                                gameService.stop()
                                isServiceRunning = false
                                pythonServiceStatus.value = "已停止"
                                logMessages = logMessages + "分析服务已停止"
                            } else {
                                // 启动服务
                                try {
                                    gameService.start()
                                    isServiceRunning = true
                                    pythonServiceStatus.value = "启动中"
                                    logMessages = logMessages + "分析服务已启动，正在从Python获取分析"
                                } catch (e: Exception) {
                                    logMessages = logMessages + "启动失败: ${e.message}"
                                }
                            }
                        }
                    }
                ) {
                    Text(if (isServiceRunning) "停止分析服务" else "启动Python分析")
                }
            }
            
            // 状态信息
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Python分析器状态
                Text(text = "Python分析器: ${pythonServiceStatus.value}")
            }
            
            // 游戏信息和分析结果区域
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                // 游戏信息区域（从分析结果提取）
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    // 手牌信息
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(text = "手牌:", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            val handTiles = (analysisResult.value?.tiles?.get("hand") as? List<*>)?.map { it.toString() } ?: emptyList()
                            if (handTiles.isEmpty()) {
                                Text(text = "等待游戏开始或分析数据...")
                            } else {
                                Text(text = handTiles.joinToString(" "), style = MaterialTheme.typography.h6)
                            }
                        }
                    }
                    
                    // 日志区域
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "日志:", fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, MaterialTheme.colors.onBackground)) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                logMessages.takeLast(20).forEach { message ->
                                    Text(text = message)
                                }
                            }
                        }
                    }
                }
                
                // 分析结果区域
                Card(modifier = Modifier.weight(1f).padding(start = 8.dp), elevation = 4.dp) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(text = "分析结果", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (analysisResult.value != null) {
                            AnalysisResultDisplay(analysisResult = analysisResult.value!!)
                        } else {
                            Text(text = "等待Python分析...")
                        }
                    }
                }
            }
            
            // 使用说明
            Text(text = "说明: Kotlin仅用于UI展示，拦截逻辑完全在Python中运行。")
        }
    }
}

@Composable
fun AnalysisResultDisplay(analysisResult: AnalysisResult) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 游戏阶段
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(text = "游戏阶段: ", fontWeight = FontWeight.Bold)
            Text(text = when (analysisResult.gameStage) {
                "early" -> "初期"
                "middle" -> "中期"
                "late" -> "后期"
                else -> "未知"
            })
        }
        
        // 建议列表
        if (analysisResult.recommendations.isNotEmpty()) {
            Text(text = "建议: ", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Column(modifier = Modifier.padding(8.dp)) {
                analysisResult.recommendations.forEachIndexed { index, recommendation ->
                    Text(text = "${index + 1}. $recommendation")
                }
            }
        }
        
        // 警告列表
        if (analysisResult.warning.isNotEmpty()) {
            Text(text = "警告: ", color = MaterialTheme.colors.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Column(modifier = Modifier.padding(8.dp)) {
                analysisResult.warning.forEach { warning ->
                    Text(text = "⚠️ $warning", color = MaterialTheme.colors.error)
                }
            }
        }
        
        // 危险牌提示
        if (analysisResult.tiles.containsKey("danger_tiles") && (analysisResult.tiles["danger_tiles"] as? List<*>)?.isNotEmpty() == true) {
            Text(text = "危险牌: ", color = MaterialTheme.colors.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(text = (analysisResult.tiles["danger_tiles"] as List<*>).joinToString(" "), color = MaterialTheme.colors.error)
        }
    }
}

expect fun getPlatformName(): String