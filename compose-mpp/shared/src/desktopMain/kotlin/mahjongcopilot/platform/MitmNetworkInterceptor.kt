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
    private val mitmPort = 11001  // 更改端口以避免冲突
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
                
                println("Starting network interception...")
                
                // 创建配置目录
                createConfigDirectory()
                
                // 安装MITM证书
                if (!installMitmCertificate()) {
                    println("Warning: Failed to install MITM certificate")
                }
                
                // 清理旧的消息文件
                cleanupMessageFile()
                
                // 启动mitmproxy进程
                if (!startMitmProxy()) {
                    return Result.failure(RuntimeException("Failed to start mitmproxy. Please ensure mitmproxy is installed and available in PATH."))
                }
                
                // 开始读取消息
                startMessageReading()
                
                isActive = true
                println("Network interception started successfully")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to start network interception: ${e.message}"
            println(errorMsg)
            e.printStackTrace()
            Result.failure(RuntimeException(errorMsg, e))
        }
    }
    
    override suspend fun stopInterception(): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isActive) {
                    return Result.success(Unit)
                }
                
                println("Stopping network interception...")
                
                // 停止消息读取
                messageReaderJob?.cancel()
                messageReaderJob = null
                
                // 停止mitmproxy进程
                stopMitmProxy()
                
                isActive = false
                println("Network interception stopped successfully")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to stop network interception: ${e.message}"
            println(errorMsg)
            e.printStackTrace()
            Result.failure(RuntimeException(errorMsg, e))
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
    private fun startMitmProxy(): Boolean {
        try {
            // 检查mitmdump是否可用
            val mitmdumpPath = findMitmdumpPath()
            if (mitmdumpPath == null) {
                println("Error: mitmdump not found. Please install mitmproxy and ensure it's in your PATH.")
                println("You can install mitmproxy by running: pip install mitmproxy")
                return false
            }
            
            // 构建mitmproxy命令
            // 使用绝对路径确保脚本可以被找到
            val scriptFile = File("mitm_script.py")
            val scriptPath = if (scriptFile.exists()) {
                scriptFile.absolutePath
            } else {
                // 如果在当前目录找不到，尝试在构建目录中查找
                val buildScriptFile = File("build/libs/mitm_script.py")
                if (buildScriptFile.exists()) {
                    buildScriptFile.absolutePath
                } else {
                    "mitm_script.py" // 回退到相对路径
                }
            }
            
            val command = listOf(
                mitmdumpPath,
                "-p", mitmPort.toString(),
                "-s", scriptPath,
                "--set", "confdir=$mitmConfigDir"
            )
            
            println("Starting mitmproxy with command: ${command.joinToString(" ")}")
            
            // 启动进程
            val processBuilder = ProcessBuilder(command)
            // 重定向输出以便调试
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
            mitmProcess = processBuilder.start()
            
            // 等待进程启动
            Thread.sleep(3000)
            
            // 检查进程是否仍在运行
            if (mitmProcess?.isAlive == true) {
                println("mitmproxy started successfully")
                return true
            } else {
                println("mitmproxy failed to start or exited immediately")
                return false
            }
            
        } catch (e: Exception) {
            println("Failed to start mitmproxy: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * 检查mitmproxy是否可用并返回路径
     */
    private fun findMitmdumpPath(): String? {
        println("Attempting to find mitmdump...")
        
        // 首先尝试在PATH中查找
        try {
            println("Trying to find mitmdump in PATH...")
            val process = ProcessBuilder("mitmdump", "--version").start()
            val exitCode = process.waitFor(5, TimeUnit.SECONDS)
            if (exitCode && process.exitValue() == 0) {
                println("Found mitmdump in PATH")
                return "mitmdump"
            } else {
                println("mitmdump not found in PATH or failed to execute")
            }
        } catch (e: Exception) {
            println("Exception when trying to find mitmdump in PATH: ${e.message}")
            // 忽略异常，继续尝试其他方法
        }
        
        // 尝试常见的安装路径
        val commonPaths = listOf(
            "C:\\Users\\HaKu\\AppData\\Roaming\\Python\\Python313\\Scripts\\mitmdump.exe",
            "C:\\Python313\\Scripts\\mitmdump.exe",
            "C:\\Users\\HaKu\\AppData\\Local\\Programs\\Python\\Python313\\Scripts\\mitmdump.exe"
        )
        
        for (path in commonPaths) {
            println("Checking path: $path")
            val file = File(path)
            if (file.exists()) {
                println("Found mitmdump at: $path")
                return path
            } else {
                println("mitmdump not found at: $path")
            }
        }
        
        println("mitmdump not found in any expected location")
        return null
    }
    
    /**
     * 检查mitmproxy是否可用
     */
    private fun isMitmProxyAvailable(): Boolean {
        return findMitmdumpPath() != null
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
     * 现在使用简单的解析方式，后续可以改进
     */
    private fun processRealMessage(messageData: String) {
        try {
            // 简单解析消息，提取关键信息
            // 这里我们先使用一个更简单的方法来解析消息
            val method = extractMethod(messageData)
            val type = extractType(messageData)
            val id = extractId(messageData)
            
            // 创建真实的LiqiMessage对象
            val liqiMessage = LiqiMessage(
                id = id,
                type = type,
                method = method,
                data = mapOf("raw_data" to messageData)
            )
            
            println("Intercepted Liqi message: method=${method.value}, type=${type.name}, id=$id")
            // println("Raw message data: $messageData")
            
            // 发射消息到Flow
            CoroutineScope(Dispatchers.IO).launch {
                _messages.emit(liqiMessage)
            }
        } catch (e: Exception) {
            println("Error processing message: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 从消息数据中提取方法
     */
    private fun extractMethod(messageData: String): LiqiMethod {
        // 简单的字符串匹配来提取方法
        return when {
            messageData.contains("authGame") -> LiqiMethod.AUTH_GAME
            messageData.contains("ActionPrototype") -> LiqiMethod.ACTION_PROTOTYPE
            messageData.contains("oauth2Login") -> LiqiMethod.OAUTH2_LOGIN
            else -> LiqiMethod.OAUTH2_LOGIN
        }
    }
    
    /**
     * 从消息数据中提取类型
     */
    private fun extractType(messageData: String): LiqiMessageType {
        // 简单的字符串匹配来提取类型
        return when {
            messageData.contains("\"type\":\"REQ\"") -> LiqiMessageType.REQ
            messageData.contains("\"type\":\"RES\"") -> LiqiMessageType.RES
            else -> LiqiMessageType.NOTIFY
        }
    }
    
    /**
     * 从消息数据中提取ID
     */
    private fun extractId(messageData: String): Int {
        // 简单的正则表达式来提取ID
        val idRegex = "\"id\":(\\d+)".toRegex()
        val match = idRegex.find(messageData)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
    
    /**
     * 安装MITM证书
     */
    private fun installMitmCertificate(): Boolean {
        try {
            val configDir = File(mitmConfigDir)
            val certFile = File(configDir, certFileName)
            
            // 如果证书文件不存在，尝试生成它
            if (!certFile.exists()) {
                println("MITM certificate not found, trying to generate it...")
                if (!generateMitmCertificate()) {
                    println("Failed to generate MITM certificate")
                    return false
                }
            }
            
            if (!certFile.exists()) {
                println("MITM certificate still not found: ${certFile.absolutePath}")
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
    
    /**
     * 生成MITM证书
     */
    private fun generateMitmCertificate(): Boolean {
        return try {
            // 尝试运行mitmdump来生成证书
            val mitmdumpPath = findMitmdumpPath() ?: "mitmdump"
            val command = listOf(mitmdumpPath, "-q", "--set", "confdir=$mitmConfigDir")
            val process = ProcessBuilder(command).start()
            
            // 等待几秒钟让证书生成
            Thread.sleep(2000)
            
            // 强制停止进程
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}