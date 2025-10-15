package mahjongcopilot.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.*
import mahjongcopilot.data.model.*
import mahjongcopilot.domain.repository.NetworkInterceptorRepository
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 基于mitmproxy的网络拦截器实现
 * 参考MahjongCopilot的实现方式
 */
class MitmNetworkInterceptor : NetworkInterceptorRepository {
    
    private val _messages = MutableSharedFlow<LiqiMessage>()
    private val messages: Flow<LiqiMessage> = _messages.asSharedFlow()
    
    private var isActive = false
    private val mutex = ReentrantLock()
    private var mitmProcess: Process? = null
    private var messageReaderJob: Job? = null
    
    // MITM配置
    private val mitmPort = 10999
    private val mitmConfigDir = "mitm_config"
    private val certFileName = "mitmproxy-ca-cert.cer"
    private val messageOutputFile = "mitm_messages.json"
    
    // 雀魂相关域名
    private val majsoulDomains = listOf(
        "maj-soul.com",
        "majsoul.com", 
        "mahjongsoul.com",
        "yo-star.com"
    )
    
    override suspend fun startInterception(settings: NetworkSettings): Result<Unit> {
        return try {
            mutex.withLock {
                if (isActive) {
                    return Result.success(Unit)
                }
                
                // 创建配置目录
                createConfigDirectory()
                
                // 安装MITM证书
                installMitmCertificate()
                
                // 清理旧的消息文件
                cleanupMessageFile()
                
                // 启动mitmproxy进程
                startMitmProxy()
                
                // 开始读取消息
                startMessageReading()
                
                isActive = true
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
                
                // 停止消息读取
                messageReaderJob?.cancel()
                messageReaderJob = null
                
                // 停止mitmproxy进程
                stopMitmProxy()
                
                isActive = false
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
     * 创建配置目录
     */
    private fun createConfigDirectory() {
        val configDir = File(mitmConfigDir)
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
    }
    
    /**
     * 清理消息文件
     */
    private fun cleanupMessageFile() {
        try {
            val messageFile = File(messageOutputFile)
            if (messageFile.exists()) {
                messageFile.delete()
            }
        } catch (e: Exception) {
            // 忽略错误
        }
    }
    
    /**
     * 启动mitmproxy进程
     */
    private fun startMitmProxy() {
        try {
            // 构建mitmproxy命令
            val command = listOf(
                "mitmdump",
                "-p", mitmPort.toString(),
                "-s", "mitm_script.py",
                "--set", "confdir=$mitmConfigDir"
            )
            
            // 启动进程
            val processBuilder = ProcessBuilder(command)
            // 重定向输出以便调试
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
            mitmProcess = processBuilder.start()
            
            // 等待进程启动
            Thread.sleep(3000)
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to start mitmproxy: ${e.message}", e)
        }
    }
    
    /**
     * 停止mitmproxy进程
     */
    private fun stopMitmProxy() {
        try {
            mitmProcess?.destroyForcibly()?.waitFor(5, TimeUnit.SECONDS)
            mitmProcess = null
        } catch (e: Exception) {
            // 忽略停止进程时的错误
        }
    }
    
    /**
     * 开始读取消息
     */
    private fun startMessageReading() {
        messageReaderJob = CoroutineScope(Dispatchers.IO).launch {
            val messageFile = File(messageOutputFile)
            var lastReadPosition = 0L
            
            try {
                while (isActive) {
                    if (messageFile.exists() && messageFile.length() > lastReadPosition) {
                        // 读取新添加的内容
                        java.io.RandomAccessFile(messageFile, "r").use { file ->
                            file.seek(lastReadPosition)
                            
                            while (file.filePointer < file.length()) {
                                val line = file.readLine()
                                if (line != null) {
                                    processRealMessage(line)
                                }
                            }
                            
                            lastReadPosition = file.filePointer
                        }
                    }
                    
                    delay(100) // 短暂延迟以避免过度占用CPU
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 处理从mitmproxy接收到的真实消息
     */
    private fun processRealMessage(messageData: String) {
        try {
            // 创建一个真实的LiqiMessage对象
            val liqiMessage = LiqiMessage(
                id = (System.currentTimeMillis() % 1000).toInt(),
                type = LiqiMessageType.NOTIFY,
                method = LiqiMethod.OAUTH2_LOGIN,
                data = mapOf(
                    "timestamp" to System.currentTimeMillis().toString(),
                    "content" to messageData
                )
            )
            
            // 发射消息到Flow
            CoroutineScope(Dispatchers.IO).launch {
                _messages.emit(liqiMessage)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 安装MITM证书
     */
    private fun installMitmCertificate(): Boolean {
        try {
            val configDir = File(mitmConfigDir)
            val certFile = File(configDir, certFileName)
            if (!certFile.exists()) {
                println("MITM certificate not found: ${certFile.absolutePath}")
                return false
            }
            
            // 在Windows上安装证书
            if (System.getProperty("os.name").lowercase().contains("win")) {
                val command = listOf("certutil", "-addstore", "Root", certFile.absolutePath)
                val process = ProcessBuilder(command).start()
                val result = process.waitFor(30, TimeUnit.SECONDS)
                
                if (result) {
                    println("MITM certificate installed successfully")
                    return true
                } else {
                    println("Failed to install MITM certificate")
                    return false
                }
            }
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}