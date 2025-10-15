package mahjongcopilot.domain.service.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mahjongcopilot.data.model.*
import mahjongcopilot.domain.repository.ProtocolParserRepository
import mahjongcopilot.domain.service.NetworkManagerService
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MITM网络管理服务实现 - 专门处理mitmproxy拦截的真实雀魂游戏数据
 */
class MitmNetworkManagerServiceImpl(
    private val protocolParser: ProtocolParserRepository
) : NetworkManagerService {
    
    private val _networkStatus = MutableStateFlow(NetworkStatus.DISCONNECTED)
    private val networkStatus: Flow<NetworkStatus> = _networkStatus.asStateFlow()
    
    private val _capturedMessages = MutableSharedFlow<LiqiMessage>()
    val capturedMessages: Flow<LiqiMessage> = _capturedMessages.asSharedFlow()
    
    private val mutex = Mutex()
    private var isConnected = false
    private var statistics = NetworkStatistics()
    private var mitmProcess: Process? = null
    private var messageReaderJob: Job? = null
    
    override suspend fun startNetworkInterception(): Result<Unit> {
        return try {
            mutex.withLock {
                if (isConnected) {
                    return Result.success(Unit)
                }
                
                _networkStatus.value = NetworkStatus.CONNECTING
                
                // 启动mitmproxy进程
                val process = startMitmProxy()
                if (process == null) {
                    _networkStatus.value = NetworkStatus.ERROR
                    return Result.failure(Exception("无法启动mitmproxy"))
                }
                
                mitmProcess = process
                isConnected = true
                
                // 启动消息读取器
                messageReaderJob = startMessageReader()
                
                _networkStatus.value = NetworkStatus.CONNECTED
                statistics = statistics.copy(connectionUptime = System.currentTimeMillis())
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            _networkStatus.value = NetworkStatus.ERROR
            Result.failure(e)
        }
    }
    
    override suspend fun stopNetworkInterception(): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isConnected) {
                    return Result.success(Unit)
                }
                
                // 停止消息读取器
                messageReaderJob?.cancel()
                messageReaderJob = null
                
                // 停止mitmproxy进程
                mitmProcess?.destroy()
                mitmProcess = null
                
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
    
    private fun startMitmProxy(): Process? {
        return try {
            // 检查mitmproxy是否可用
            val mitmProcess = ProcessBuilder("mitmdump", "--version").start()
            val exitCode = mitmProcess.waitFor()
            
            if (exitCode != 0) {
                println("mitmproxy未安装或不可用")
                return null
            }
            
            // 启动mitmproxy
            val process = ProcessBuilder(
                "mitmdump",
                "-s", "mitm_script.py",
                "--listen-port", "8080",
                "--set", "confdir=.",
                "--no-http2",
                "--quiet"
            ).directory(File(".")).start()
            
            // 等待进程启动
            Thread.sleep(2000)
            
            if (process.isAlive) {
                println("mitmproxy启动成功，监听端口8080")
                process
            } else {
                println("mitmproxy启动失败")
                null
            }
        } catch (e: Exception) {
            println("启动mitmproxy失败: ${e.message}")
            null
        }
    }
    
    private fun startMessageReader(): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isConnected) {
                try {
                    readCapturedMessages()
                    delay(1000) // 每秒读取一次
                } catch (e: Exception) {
                    println("读取消息失败: ${e.message}")
                    delay(5000) // 出错后等待5秒
                }
            }
        }
    }
    
    private suspend fun readCapturedMessages() {
        val messageFile = File("mitm_messages.json")
        if (!messageFile.exists()) {
            return
        }
        
        try {
            val lines = messageFile.readLines()
            if (lines.isNotEmpty()) {
                // 读取最后几条消息（避免处理过多历史消息）
                val recentLines = lines.takeLast(10)
                
                for (line in recentLines) {
                    if (line.isNotBlank()) {
                        val messageResult = protocolParser.parseLiqiMessage(line.toByteArray())
                        if (messageResult.isSuccess) {
                            _capturedMessages.emit(messageResult.getOrThrow())
                        }
                    }
                }
                
                // 清空文件内容（避免重复处理）
                messageFile.writeText("")
            }
        } catch (e: Exception) {
            println("处理消息文件失败: ${e.message}")
        }
    }
    
    /**
     * 检查mitmproxy是否正在运行
     */
    suspend fun isMitmProxyRunning(): Boolean {
        return mutex.withLock { 
            mitmProcess?.isAlive ?: false
        }
    }
}