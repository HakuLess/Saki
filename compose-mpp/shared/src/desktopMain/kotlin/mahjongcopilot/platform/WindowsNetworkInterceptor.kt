package mahjongcopilot.platform

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mahjongcopilot.data.model.*
import java.net.*
import java.io.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Windows平台网络拦截器
 * 使用WinPcap或Raw Socket实现网络包捕获
 */
class WindowsNetworkInterceptor {
    
    private val _capturedMessages = MutableSharedFlow<ByteArray>()
    val capturedMessages: Flow<ByteArray> = _capturedMessages.asSharedFlow()
    
    private val _interceptionStatus = MutableStateFlow(NetworkStatus.DISCONNECTED)
    val interceptionStatus: Flow<NetworkStatus> = _interceptionStatus.asStateFlow()
    
    private var isIntercepting = false
    private var interceptionJob: Job? = null
    private val mutex = Mutex()
    
    /**
     * 开始网络拦截
     */
    suspend fun startInterception(): Result<Unit> {
        return try {
            mutex.withLock {
                if (isIntercepting) {
                    return Result.success(Unit)
                }
                
                _interceptionStatus.value = NetworkStatus.CONNECTING
                
                // 启动拦截任务
                interceptionJob = startPacketCapture()
                
                isIntercepting = true
                _interceptionStatus.value = NetworkStatus.CONNECTED
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            _interceptionStatus.value = NetworkStatus.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * 停止网络拦截
     */
    suspend fun stopInterception(): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isIntercepting) {
                    return Result.success(Unit)
                }
                
                interceptionJob?.cancel()
                interceptionJob = null
                
                isIntercepting = false
                _interceptionStatus.value = NetworkStatus.DISCONNECTED
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            _interceptionStatus.value = NetworkStatus.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * 检查是否正在拦截
     */
    suspend fun isIntercepting(): Boolean {
        return mutex.withLock { isIntercepting }
    }
    
    /**
     * 启动数据包捕获
     */
    private fun startPacketCapture(): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                // 模拟网络包捕获 - 实际实现需要使用WinPcap或Raw Socket
                simulatePacketCapture()
            } catch (e: Exception) {
                println("数据包捕获失败: ${e.message}")
                _interceptionStatus.value = NetworkStatus.ERROR
            }
        }
    }
    
    /**
     * 模拟数据包捕获（用于演示）
     */
    private suspend fun simulatePacketCapture() {
        var packetCount = 0
        
        while (isIntercepting) {
            try {
                // 模拟捕获网络包
                delay(1000) // 每秒捕获一个包
                
                packetCount++
                
                // 模拟雀魂游戏数据包
                val simulatedPacket = simulateMahjongPacket(packetCount)
                _capturedMessages.emit(simulatedPacket)
                
                println("捕获数据包 #$packetCount")
                
            } catch (e: Exception) {
                println("模拟数据包捕获失败: ${e.message}")
                delay(5000) // 出错后等待5秒
            }
        }
    }
    
    /**
     * 模拟雀魂游戏数据包
     */
    private fun simulateMahjongPacket(packetNumber: Int): ByteArray {
        // 模拟不同的游戏状态数据包
        return when (packetNumber % 5) {
            0 -> simulateAuthGamePacket()
            1 -> simulateNewRoundPacket()
            2 -> simulateDealTilePacket()
            3 -> simulateDiscardTilePacket()
            else -> simulateChiPengGangPacket()
        }
    }
    
    private fun simulateAuthGamePacket(): ByteArray {
        return """
            {
                "method": "AUTH_GAME",
                "data": {
                    "gameId": "game_${System.currentTimeMillis()}",
                    "mode": "四麻",
                    "players": ["玩家1", "玩家2", "玩家3", "玩家4"]
                }
            }
        """.trimIndent().toByteArray()
    }
    
    private fun simulateNewRoundPacket(): ByteArray {
        return """
            {
                "method": "ACTION_PROTOTYPE",
                "data": {
                    "name": "ActionNewRound",
                    "data": {
                        "chang": 0,
                        "ju": 0,
                        "ben": 0,
                        "doras": ["1z"],
                        "scores": [25000, 25000, 25000, 25000],
                        "tiles": ["1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "1p", "2p", "3p", "4p"]
                    }
                }
            }
        """.trimIndent().toByteArray()
    }
    
    private fun simulateDealTilePacket(): ByteArray {
        return """
            {
                "method": "ACTION_PROTOTYPE",
                "data": {
                    "name": "ActionDealTile",
                    "data": {
                        "seat": 0,
                        "tile": "5p"
                    }
                }
            }
        """.trimIndent().toByteArray()
    }
    
    private fun simulateDiscardTilePacket(): ByteArray {
        return """
            {
                "method": "ACTION_PROTOTYPE",
                "data": {
                    "name": "ActionDiscardTile",
                    "data": {
                        "seat": 0,
                        "tile": "5p"
                    }
                }
            }
        """.trimIndent().toByteArray()
    }
    
    private fun simulateChiPengGangPacket(): ByteArray {
        return """
            {
                "method": "ACTION_PROTOTYPE",
                "data": {
                    "name": "ActionChiPengGang",
                    "data": {
                        "seat": 0,
                        "type": 1,
                        "tiles": ["5p", "5p", "5p"]
                    }
                }
            }
        """.trimIndent().toByteArray()
    }
}