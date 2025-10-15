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
    
    // 提供公共访问属性
    val capturedMessages: Flow<LiqiMessage> = messages
    
    private var isActive = false
    private val mutex = ReentrantLock()
    private var mitmProcess: Process? = null
    private var messageReaderJob: Job? = null
    
    // MITM配置
    private val mitmPort = 11003  // 更改端口以避免冲突
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
                if (!startMitmProxy(settings)) {
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
    
    /**
     * 启动网络拦截（不带参数的版本，用于向后兼容）
     */
    suspend fun startInterception(): Result<Unit> {
        val defaultSettings = NetworkSettings()
        return startInterception(defaultSettings)
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
    private fun startMitmProxy(settings: NetworkSettings): Boolean {
        try {
            // 检查mitmdump是否可用
            val mitmdumpPath = findMitmdumpPath()
            if (mitmdumpPath == null) {
                println("Error: mitmdump not found. Please install mitmproxy and ensure it's in your PATH.")
                println("You can install mitmproxy by running: pip install mitmproxy")
                return false
            }
            
            // 使用传入的设置或默认设置
            val port = settings.mitmPort
            val upstreamProxy = settings.upstreamProxy
            val enableProxyInject = settings.enableProxyInject
            
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
            
            // 构建基本命令
            val command = mutableListOf<String>()
            
            // 检查是否需要通过Python运行
            if (mitmdumpPath.startsWith("python")) {
                // 通过Python运行mitmdump
                command.addAll(listOf(
                    "python", "-c", "import mitmproxy.tools.main; mitmproxy.tools.main.mitmdump()",
                    "-p", port.toString(),
                    "-s", scriptPath,
                    "--set", "confdir=$mitmConfigDir"
                ))
            } else {
                // 直接运行mitmdump
                command.addAll(listOf(
                    mitmdumpPath,
                    "-p", port.toString(),
                    "-s", scriptPath,
                    "--set", "confdir=$mitmConfigDir"
                ))
            }
            
            // 添加上游代理配置（如果存在）
            upstreamProxy?.let {
                command.addAll(listOf("--mode", "upstream:$it"))
            }
            
            // 如果启用代理注入，添加相关参数
            if (enableProxyInject) {
                command.addAll(listOf(
                    "--set", "upstream_cert=false",
                    "--set", "ssl_insecure=true"
                ))
            }
            
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
        
        // 尝试通过Python运行mitmdump
        try {
            val process = ProcessBuilder("python", "-c", "import mitmproxy.tools.main; mitmproxy.tools.main.mitmdump()", "--version").start()
            if (process.waitFor(5, TimeUnit.SECONDS)) {
                println("Found mitmdump through Python module")
                return "python -c \"import mitmproxy.tools.main; mitmproxy.tools.main.mitmdump()\""
            }
        } catch (e: Exception) {
            println("Exception when trying to find mitmdump through Python: ${e.message}")
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
     * 处理从MITM脚本接收到的消息
     * 增强版，支持解析二进制协议
     */
    private fun processRealMessage(message: String) {
        try {
            // 使用简单的正则表达式提取JSON字段
            val method = extractFieldFromJson(message, "method")
            val type = extractFieldFromJson(message, "type")
            val content = extractFieldFromJson(message, "content")
            val direction = extractFieldFromJson(message, "direction")
            
            // 检查是否是解析后的二进制消息
            if (method != null && type != null) {
                // 这是解析后的二进制消息，转换为LiqiMessage
                val id = extractFieldFromJson(message, "id")?.toIntOrNull() ?: -1
                val data = extractFieldFromJson(message, "data") ?: ""
                
                val liqiMessage = LiqiMessage(
                    id = id,
                    type = parseMessageType(type),
                    method = parseLiqiMethod(method),
                    data = mapOf("raw_data" to data)
                )
                _messages.tryEmit(liqiMessage)
                println("MITM: Processed binary message - method: ${liqiMessage.method}, type: ${liqiMessage.type}")
            } else if (content != null) {
                // 这是WebSocket消息，需要进一步解析
                parseWebSocketMessage(content)
            } else {
                // 这是HTTP请求/响应消息
                println("HTTP message: ${direction ?: "unknown"}")
            }
        } catch (e: Exception) {
            // 如果JSON解析失败，尝试作为原始消息处理
            parseWebSocketMessage(message)
        }
    }
    
    /**
     * 解析WebSocket消息
     * 参考MahjongCopilot的liqi.py实现
     */
    private fun parseWebSocketMessage(content: String) {
        try {
            // 如果是Base64编码的数据，先解码
            val decodedContent = if (isBase64(content)) {
                String(base64Decode(content))
            } else {
                content
            }
            
            // 尝试解析为二进制数据
            if (decodedContent.startsWith("{")) {
                // JSON格式的消息
                parseJsonMessage(decodedContent)
            } else {
                // 二进制格式的消息，需要进一步处理
                parseBinaryMessage(decodedContent)
            }
        } catch (e: Exception) {
            println("Error parsing WebSocket message: ${e.message}")
        }
    }
    
    /**
     * 解析JSON格式的消息
     */
    private fun parseJsonMessage(jsonStr: String) {
        try {
            // 使用简单的正则表达式提取JSON字段
            val method = extractFieldFromJson(jsonStr, "method") ?: ""
            val type = extractFieldFromJson(jsonStr, "type") ?: "NOTIFY"
            val id = extractFieldFromJson(jsonStr, "id")?.toIntOrNull() ?: 0
            
            // 转换为LiqiMessage
            val liqiMessage = LiqiMessage(
                id = id,
                type = parseMessageType(type),
                method = parseLiqiMethod(method),
                data = mapOf("raw_data" to jsonStr)
            )
            
            println("Intercepted Liqi message: method=${method}, type=${type}, id=$id")
            
            // 发射消息到Flow
            CoroutineScope(Dispatchers.IO).launch {
                _messages.emit(liqiMessage)
            }
        } catch (e: Exception) {
            println("Error parsing JSON message: ${e.message}")
        }
    }
    
    /**
     * 从JSON字符串中提取字段值
     */
    private fun extractFieldFromJson(jsonStr: String, fieldName: String): String? {
        try {
            // 使用正则表达式提取字段值
            val regex = "\"$fieldName\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val matchResult = regex.find(jsonStr)
            return matchResult?.groupValues?.get(1)
        } catch (e: Exception) {
            // 如果正则表达式失败，尝试其他方法
            return null
        }
    }
    
    /**
     * 解析二进制格式的消息
     * 参考MahjongCopilot的liqi.py实现
     */
    private fun parseBinaryMessage(data: String) {
        try {
            // 将字符串转换为字节数组
            val bytes = data.toByteArray()
            
            if (bytes.isEmpty()) return
            
            // 解析消息类型
            val msgType = bytes[0].toInt()
            val liqiMessageType = when (msgType) {
                1 -> LiqiMessageType.NOTIFY
                2 -> LiqiMessageType.REQ
                3 -> LiqiMessageType.RES
                else -> LiqiMessageType.UNKNOWN
            }
            
            // 提取消息ID（对于REQ和RES类型）
            val msgId = if (msgType == 2 || msgType == 3) {
                // 小端序读取2字节ID
                ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
            } else {
                -1
            }
            
            // 解析Protobuf数据
            val protobufData = if (msgType == 1) {
                bytes.sliceArray(1 until bytes.size)
            } else {
                bytes.sliceArray(3 until bytes.size)
            }
            
            // 解析Protobuf结构
            val protobufBlocks = parseProtobuf(protobufData)
            
            // 提取方法名
            val method = if (protobufBlocks.isNotEmpty()) {
                val methodName = String(protobufBlocks[0].data)
                parseLiqiMethod(methodName)
            } else {
                LiqiMethod.UNKNOWN
            }
            
            // 提取数据
            val dataContent = if (protobufBlocks.size > 1) {
                base64Encode(protobufBlocks[1].data)
            } else {
                ""
            }
            
            // 特殊处理ActionPrototype消息
            var actionName: String? = null
            var actionData: String? = null
            if (method == LiqiMethod.ACTION_PROTOTYPE && protobufBlocks.size > 1) {
                try {
                    // 解密ActionPrototype数据
                    val decodedData = decodeXor(protobufBlocks[1].data)
                    val actionBlocks = parseProtobuf(decodedData)
                    
                    if (actionBlocks.isNotEmpty()) {
                        actionName = String(actionBlocks[0].data)
                    }
                    if (actionBlocks.size > 1) {
                        actionData = base64Encode(actionBlocks[1].data)
                    }
                } catch (e: Exception) {
                    println("Error parsing ActionPrototype: ${e.message}")
                }
            }
            
            // 创建LiqiMessage
            val liqiMessage = LiqiMessage(
                id = msgId,
                type = liqiMessageType,
                method = method,
                data = mapOf(
                    "raw_data" to data,
                    "protobuf_data" to base64Encode(protobufData),
                    "method_name" to (if (method != LiqiMethod.UNKNOWN) method.name else ""),
                    "action_name" to (actionName ?: ""),
                    "action_data" to (actionData ?: "")
                )
            )
            
            println("Intercepted binary Liqi message: method=${method.name}, type=${liqiMessageType.name}, id=$msgId")
            if (actionName != null) {
                println("  Action: $actionName")
            }
            
            // 发射消息到Flow
            CoroutineScope(Dispatchers.IO).launch {
                _messages.emit(liqiMessage)
            }
        } catch (e: Exception) {
            println("Error parsing binary message: ${e.message}")
        }
    }
    
    /**
     * 解析Protobuf结构
     * 参考MahjongCopilot的from_protobuf实现
     */
    private fun parseProtobuf(data: ByteArray): List<ProtobufBlock> {
        val blocks = mutableListOf<ProtobufBlock>()
        var offset = 0
        
        while (offset < data.size) {
            try {
                // 读取varint类型和字段号
                val (fieldNumber, wireType, varintSize) = parseVarint(data, offset)
                offset += varintSize
                
                when (wireType) {
                    0 -> { // Varint
                        val (value, size) = parseVarintValue(data, offset)
                        offset += size
                        blocks.add(ProtobufBlock(fieldNumber, wireType, byteArrayOf(value.toByte())))
                    }
                    1 -> { // 64-bit
                        if (offset + 8 <= data.size) {
                            val value = data.sliceArray(offset until offset + 8)
                            offset += 8
                            blocks.add(ProtobufBlock(fieldNumber, wireType, value))
                        } else {
                            break
                        }
                    }
                    2 -> { // Length-delimited
                        val (length, size) = parseVarint(data, offset)
                        offset += size
                        
                        if (offset + length.toInt() <= data.size) {
                            val value = data.sliceArray(offset until offset + length.toInt())
                            offset += length.toInt()
                            blocks.add(ProtobufBlock(fieldNumber, wireType, value))
                        } else {
                            break
                        }
                    }
                    5 -> { // 32-bit
                        if (offset + 4 <= data.size) {
                            val value = data.sliceArray(offset until offset + 4)
                            offset += 4
                            blocks.add(ProtobufBlock(fieldNumber, wireType, value))
                        } else {
                            break
                        }
                    }
                    else -> {
                        // 不支持的wire类型，跳过
                        break
                    }
                }
            } catch (e: Exception) {
                // 解析出错，停止解析
                break
            }
        }
        
        return blocks
    }
    
    /**
     * 解析varint
     * 参考MahjongCopilot的parse_varint实现
     */
    private fun parseVarint(data: ByteArray, offset: Int): Triple<Int, Int, Int> {
        var value = 0L
        var shift = 0
        var bytesConsumed = 0
        var currentByte: Int
        
        do {
            if (offset + bytesConsumed >= data.size) {
                throw IllegalArgumentException("Invalid varint: reached end of data")
            }
            
            currentByte = data[offset + bytesConsumed].toInt() and 0xFF
            bytesConsumed++
            
            value = value or ((currentByte and 0x7F).toLong() shl shift)
            shift += 7
        } while ((currentByte and 0x80) != 0)
        
        // 解析字段号和wire类型
        val fieldNumber = (value shr 3).toInt()
        val wireType = (value and 0x7).toInt()
        
        return Triple(fieldNumber, wireType, bytesConsumed)
    }
    
    /**
     * 解析varint值
     * 用于解析wireType 0类型的值
     */
    private fun parseVarintValue(data: ByteArray, offset: Int): Pair<Long, Int> {
        var value = 0L
        var shift = 0
        var bytesConsumed = 0
        var currentByte: Int
        
        do {
            if (offset + bytesConsumed >= data.size) {
                throw IllegalArgumentException("Invalid varint: reached end of data")
            }
            
            currentByte = data[offset + bytesConsumed].toInt() and 0xFF
            bytesConsumed++
            
            value = value or ((currentByte and 0x7F).toLong() shl shift)
            shift += 7
        } while ((currentByte and 0x80) != 0)
        
        return Pair(value, bytesConsumed)
    }
    
    /**
     * XOR解密
     * 参考MahjongCopilot的decode实现
     */
    private fun decodeXor(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        
        val result = ByteArray(data.size)
        val key = 0x171.toByte()
        
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor key.toInt()).toByte()
        }
        
        return result
    }
    
    /**
     * Protobuf块数据类
     */
    private data class ProtobufBlock(
        val fieldNumber: Int,
        val wireType: Int,
        val data: ByteArray
    )
    
    /**
     * 解析消息类型
     */
    private fun parseMessageType(typeStr: String): LiqiMessageType {
        return when (typeStr.uppercase()) {
            "NOTIFY" -> LiqiMessageType.NOTIFY
            "REQ" -> LiqiMessageType.REQ
            "RES" -> LiqiMessageType.RES
            else -> LiqiMessageType.UNKNOWN
        }
    }
    
    /**
     * 解析Liqi方法
     * 参考MahjongCopilot的liqi.py实现
     */
    private fun parseLiqiMethod(methodStr: String): LiqiMethod {
        return mahjongcopilot.data.model.fromString(methodStr)
    }
    
    /**
     * 检查字符串是否是Base64编码
     */
    private fun isBase64(str: String): Boolean {
        return try {
            base64Decode(str)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Base64解码
     */
    private fun base64Decode(str: String): ByteArray {
        return java.util.Base64.getDecoder().decode(str)
    }
    
    /**
     * Base64编码
     */
    private fun base64Encode(data: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(data)
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