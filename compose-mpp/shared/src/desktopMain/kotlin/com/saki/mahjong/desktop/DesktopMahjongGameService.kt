package com.saki.mahjong.desktop

import com.saki.mahjong.data.GameState
import com.saki.mahjong.data.GameStatus
import com.saki.mahjong.data.Tile
import com.saki.mahjong.data.TileValue
import com.saki.mahjong.service.MahjongGameService
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

import java.util.concurrent.TimeUnit

/**
 * 桌面平台麻将游戏服务实现
 * 实现网络请求拦截和游戏状态处理
 */
class DesktopMahjongGameService : MahjongGameService {
    
    private var onGameStateUpdate: ((GameState) -> Unit)? = null
    private val _gameState = MutableStateFlow(GameState())
    val gameState = _gameState.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isIntercepting = false
    
    // Ktor HttpClient
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }
    
    // 简单的模拟请求检测函数
    private fun checkAndUpdateGameState(url: String) {
        if (url.contains("game_data") || 
            url.contains("ms/1/player") || 
            url.contains("ms/1/game") || 
            url.contains("ms/1/match") || 
            url.contains("ms/1/game/player") || 
            url.contains("ms/1/table") || 
            url.contains("ms/1/lobby") || 
            url.contains("ms/1/character")) {
            scope.launch {
                updateMockGameState()
            }
        }
    }
    
    override fun initialize(onGameStateUpdate: (GameState) -> Unit) {
        this.onGameStateUpdate = onGameStateUpdate
        // 设置初始模拟数据
        updateMockGameState()
    }
    
    private var networkCheckJob: Job? = null
    
    override fun startNetworkInterceptor() {
        isIntercepting = true
        println("开始拦截雀魂网络请求...")
        
        // 使用定时器模拟定期检测雀魂游戏网络请求
        networkCheckJob = scope.launch {
            while (isIntercepting) {
                // 模拟检测雀魂API请求
                checkAndUpdateGameState("https://game.mahjongsoul.com/api/v1/ms/1/game/player")
                delay(3000) // 每3秒检查一次
            }
        }
    }
    
    override fun stopNetworkInterceptor() {
        isIntercepting = false
        networkCheckJob?.cancel()
        networkCheckJob = null
        println("停止拦截雀魂网络请求...")
    }
    
    override fun getCurrentGameState(): GameState {
        return _gameState.value
    }
    
    override fun dispose() {
        isIntercepting = false
        client.close()
        println("释放游戏服务资源")
    }
    
    // 模拟网络更新
    private suspend fun simulateNetworkUpdates() {
        while (isIntercepting) {
            // 每5秒更新一次模拟数据
            kotlinx.coroutines.delay(5000)
            updateMockGameState()
        }
    }
    
    // 更新模拟游戏状态
    private fun updateMockGameState() {
        val mockState = createMockGameState()
        _gameState.update { mockState }
        onGameStateUpdate?.invoke(mockState)
    }
    
    // 创建模拟游戏状态数据
    private fun createMockGameState(): GameState {
        // 模拟手牌
        val handTiles = listOf(
            Tile(TileValue.MAN_1),
            Tile(TileValue.MAN_2),
            Tile(TileValue.MAN_3),
            Tile(TileValue.MAN_4),
            Tile(TileValue.MAN_5),
            Tile(TileValue.PIN_1),
            Tile(TileValue.PIN_2),
            Tile(TileValue.PIN_3),
            Tile(TileValue.SOU_4),
            Tile(TileValue.SOU_5),
            Tile(TileValue.SOU_6),
            Tile(TileValue.EAST),
            Tile(TileValue.EAST)
        )
        
        // 模拟玩家
        val players = listOf(
            com.saki.mahjong.data.PlayerInfo("玩家1", 25000, 0, true),
            com.saki.mahjong.data.PlayerInfo("玩家2", 25000, 1),
            com.saki.mahjong.data.PlayerInfo("玩家3", 25000, 2),
            com.saki.mahjong.data.PlayerInfo("玩家4", 25000, 3)
        )
        
        // 模拟打出的牌
        val river = listOf(
            listOf(Tile(TileValue.MAN_9), Tile(TileValue.PIN_9)),
            listOf(Tile(TileValue.SOU_9), Tile(TileValue.WEST)),
            listOf(Tile(TileValue.NORTH), Tile(TileValue.WHITE)),
            listOf(Tile(TileValue.GREEN), Tile(TileValue.RED))
        )
        
        // 模拟宝牌指示牌
        val doraIndicators = listOf(Tile(TileValue.PIN_5))
        
        return GameState(
            status = GameStatus.IN_PROGRESS,
            round = 1,
            honba = 0,
            kyotaku = 1,
            currentPlayerIndex = 0,
            players = players,
            handTiles = handTiles,
            drawnTile = Tile(TileValue.MAN_6),
            river = river,
            doraIndicators = doraIndicators,
            uradoraIndicators = emptyList()
        )
    }
}