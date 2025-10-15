package mahjongcopilot.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.*
import mahjongcopilot.data.model.*
import mahjongcopilot.domain.repository.NetworkInterceptorRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 网络拦截器实现
 * 模拟网络拦截功能，实际实现需要平台特定的代码
 */
class NetworkInterceptorRepositoryImpl : NetworkInterceptorRepository {
    
    private val _messages = MutableSharedFlow<LiqiMessage>()
    private val messages: Flow<LiqiMessage> = _messages.asSharedFlow()
    
    private var isActive = false
    private val mutex = Mutex()
    private var interceptJob: Job? = null
    
    override suspend fun startInterception(settings: NetworkSettings): Result<Unit> {
        return try {
            mutex.withLock {
                if (isActive) {
                    return Result.success(Unit)
                }
                
                isActive = true
                interceptJob = CoroutineScope(Dispatchers.IO).launch {
                    startMockInterception()
                }
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun stopInterception(): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isActive) {
                    return Result.success(Unit)
                }
                
                isActive = false
                interceptJob?.cancel()
                interceptJob = null
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun observeNetworkMessages(): Flow<LiqiMessage> {
        return messages
    }
    
    override suspend fun isInterceptionActive(): Boolean {
        return mutex.withLock { isActive }
    }
    
    /**
     * 模拟网络拦截
     * 在实际实现中，这里会使用平台特定的网络拦截技术
     */
    private suspend fun startMockInterception() {
        // 首先发送一个游戏开始消息
        val startGameMessage = LiqiMessage(
            id = 1,
            type = LiqiMessageType.RES,
            method = LiqiMethod.AUTH_GAME,
            data = mapOf(
                "gameId" to "mock_game_${System.currentTimeMillis()}",
                "timestamp" to System.currentTimeMillis()
            )
        )
        _messages.emit(startGameMessage)
        
        // 模拟接收网络消息
        while (isActive) {
            delay(5000) // 每5秒模拟一个消息
            
            if (isActive) {
                val mockMessage = createMockLiqiMessage()
                _messages.emit(mockMessage)
            }
        }
    }
    
    /**
     * 创建模拟的 Liqi 消息
     */
    private fun createMockLiqiMessage(): LiqiMessage {
        val messageTypes = LiqiMessageType.values()
        val methods = LiqiMethod.values()
        
        val randomType = messageTypes.random()
        val randomMethod = methods.random()
        
        return LiqiMessage(
            id = (1..1000).random(),
            type = randomType,
            method = randomMethod,
            data = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "mock" to true,
                "test_data" to "This is a mock message for testing"
            )
        )
    }
}
