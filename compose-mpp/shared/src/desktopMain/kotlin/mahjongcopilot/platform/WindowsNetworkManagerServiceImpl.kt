package mahjongcopilot.platform

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mahjongcopilot.data.model.*
import mahjongcopilot.domain.service.NetworkManagerService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Windows平台网络管理器服务实现
 * 使用MITM代理进行网络拦截，并监控游戏客户端进程
 */
class WindowsNetworkManagerServiceImpl() : NetworkManagerService {
    
    private val _networkStatus = MutableStateFlow(NetworkStatus.DISCONNECTED)
    private val networkStatus: Flow<NetworkStatus> = _networkStatus.asStateFlow()
    
    private val _capturedMessages = MutableSharedFlow<LiqiMessage>()
    val capturedMessages: Flow<LiqiMessage> = _capturedMessages.asSharedFlow()
    
    private val mutex = Mutex()
    private var isConnected = false
    private var statistics = NetworkStatistics()
    private var networkInterceptor: MitmNetworkInterceptor? = null
    private var messageProcessorJob: Job? = null
    
    // 进程监控器
    private val processMonitor = WindowsProcessMonitor()
    
    override suspend fun startNetworkInterception(): Result<Unit> {
        return try {
            mutex.withLock {
                if (isConnected) {
                    return Result.success(Unit)
                }
                
                _networkStatus.value = NetworkStatus.CONNECTING
                
                // 启动进程监控
                processMonitor.startMonitoring()
                println("Game process monitoring started")
                
                // 创建MITM网络拦截器
                val interceptor = MitmNetworkInterceptor()
                networkInterceptor = interceptor
                
                // 启动网络拦截
                val networkSettings = NetworkSettings()
                val startResult = interceptor.startInterception(networkSettings)
                if (startResult.isFailure) {
                    _networkStatus.value = NetworkStatus.ERROR
                    processMonitor.stopMonitoring()
                    return Result.failure(startResult.exceptionOrNull() ?: Exception("启动网络拦截失败"))
                }
                
                isConnected = true
                
                // 启动消息处理器
                messageProcessorJob = startMessageProcessor(interceptor)
                
                _networkStatus.value = NetworkStatus.CONNECTED
                statistics = statistics.copy(
                    connectionUptime = System.currentTimeMillis()
                )
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            _networkStatus.value = NetworkStatus.ERROR
            processMonitor.stopMonitoring()
            Result.failure(e)
        }
    }
    
    override suspend fun stopNetworkInterception(): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isConnected) {
                    return Result.success(Unit)
                }
                
                // 停止消息处理器
                messageProcessorJob?.cancel()
                messageProcessorJob = null
                
                // 停止网络拦截
                networkInterceptor?.stopInterception()
                networkInterceptor = null
                
                // 停止进程监控
                processMonitor.stopMonitoring()
                println("Game process monitoring stopped")
                
                isConnected = false
                _networkStatus.value = NetworkStatus.DISCONNECTED
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            _networkStatus.value = NetworkStatus.ERROR
            Result.failure(e)
        }
    }
    
    override suspend fun isNetworkConnected(): Boolean {
        return mutex.withLock { isConnected }
    }
    
    override suspend fun getNetworkStatistics(): NetworkStatistics {
        return mutex.withLock { 
            statistics.copy(
                connectionUptime = if (isConnected) {
                    System.currentTimeMillis() - statistics.connectionUptime
                } else {
                    0L
                }
            )
        }
    }
    
    override fun observeNetworkStatus(): Flow<NetworkStatus> {
        return networkStatus
    }
    
    /**
     * 获取游戏进程状态
     */
    fun observeGameProcessStatus(): StateFlow<Boolean> {
        return processMonitor.isGameRunning
    }
    
    /**
     * 获取游戏进程信息
     */
    fun observeGameProcessInfo(): StateFlow<GameProcessInfo?> {
        return processMonitor.gameProcessInfo
    }
    

    
    /**
     * 启动消息处理器
     */
    private fun startMessageProcessor(interceptor: MitmNetworkInterceptor): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                // 监听拦截器捕获的消息
                interceptor.capturedMessages.collect { message ->
                    processMitmMessage(message)
                }
            } catch (e: Exception) {
                println("消息处理器错误: ${e.message}")
                _networkStatus.value = NetworkStatus.ERROR
            }
        }
    }
    
    /**
     * 检查网络拦截器是否正在运行
     */
    suspend fun isInterceptorRunning(): Boolean {
        return mutex.withLock { 
            networkInterceptor?.isInterceptionActive() ?: false
        }
    }
    
    /**
     * 处理从MITM拦截器接收到的消息
     */
    private suspend fun processMitmMessage(message: LiqiMessage) {
        try {
            // 更新统计信息
            statistics = statistics.copy(
                messagesReceived = statistics.messagesReceived + 1
            )
            
            // 发送消息到流
            _capturedMessages.emit(message)
            
            println("处理MITM消息: ${message.method}, ID: ${message.id}")
        } catch (e: Exception) {
            println("处理MITM消息失败: ${e.message}")
        }
    }
}