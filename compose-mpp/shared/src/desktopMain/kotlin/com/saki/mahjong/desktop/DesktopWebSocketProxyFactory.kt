package com.saki.mahjong.desktop

import com.saki.mahjong.network.BaseWebSocketProxy
import com.saki.mahjong.network.WebSocketProxy
import com.saki.mahjong.network.WebSocketProxyFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
// java.io.IOException already imported
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import javax.net.ssl.*
import kotlin.concurrent.thread

/**
 * 桌面平台的WebSocket代理实现
 */
class DesktopWebSocketProxy : BaseWebSocketProxy() {
    private var proxyServer: Thread? = null
    private val COUNT_DOWN_LATCH = CountDownLatch(1)
    private var savedProxyEnable: String? = null
    private var savedProxyServer: String? = null
    private var savedProxyOverride: String? = null
    
    // 代理功能支持

    // 确保成员变量正确定义
    
    override fun start(port: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                if (running) {
                    return@withLock
                }
                running = true

                try {
                    // 保存并设置系统代理
                    saveOriginalProxySettings()
                    setSystemProxy(port)

                    // 启动代理服务器
                    proxyServer = thread(name = "MITM-Proxy-Server") {
                        try {
                            startProxyServer(port)
                        } catch (e: Exception) {
                            java.lang.System.out.println("代理服务器启动失败: ${e.message}")
                            e.printStackTrace()
                            // 恢复系统代理设置
                            try { restoreOriginalProxySettings() } catch (_: Exception) {}
                        }
                    }

                    java.lang.System.out.println("代理服务器已启动在端口 $port")
                } catch (e: Exception) {
                    java.lang.System.out.println("启动代理失败: ${e.message}")
                    e.printStackTrace()
                    try { restoreOriginalProxySettings() } catch (_: Exception) {}
                }
            }
        }
    }

    override fun stop() {
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                if (!running) {
                    return@withLock
                }
                running = false

                try {
                    if (proxyServer?.isAlive == true) {
                        proxyServer?.interrupt()
                        proxyServer = null
                    }

                    // 恢复系统代理
                    restoreOriginalProxySettings()

                    java.lang.System.out.println("代理服务器已停止")
                } catch (e: Exception) {
                    java.lang.System.out.println("停止代理失败: ${e.message}")
                    e.printStackTrace()
                    try { restoreOriginalProxySettings() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun startProxyServer(port: Int) {
        val serverSocket = ServerSocket(port)
        try {
            while (running) {
                try {
                    val clientSocket = serverSocket.accept()
                    thread { handleClientConnection(clientSocket) }
                } catch (e: IOException) {
                    // 如果是因为关闭而中断，则退出循环
                    if (!Thread.currentThread().isInterrupted()) {
                        java.lang.System.out.println("接受连接失败: ${e.message}")
                    }
                }
            }
        } finally {
            serverSocket.close()
        }
    }

    private fun handleClientConnection(clientSocket: Socket) {
        try {
            val request = readHttpRequest(clientSocket.inputStream)
            val (requestLine, headers) = parseHttpRequest(request)
            
            if (requestLine.startsWith("CONNECT")) {
                // HTTPS连接
                handleHttpsConnect(clientSocket, requestLine, headers)
            } else if (isWebSocketUpgradeRequest(headers)) {
                // WebSocket升级请求
                handleWebSocketUpgrade(clientSocket, request, requestLine, headers)
            } else {
                // 普通HTTP请求
                handleHttpGet(clientSocket, requestLine, headers)
            }
        } catch (e: Exception) {
            System.out.println("处理客户端连接失败: ${e.message}")
            try {
                clientSocket.close()
            } catch (ignored: IOException) {
            }
        }
    }

    private fun handleHttpsConnect(clientSocket: Socket, requestLine: String, headers: Map<String, String>) {
        try {
            // 解析主机和端口
            val host = extractHostFromConnectRequest(requestLine)
            val hostAndPort = if (host.contains(":")) {
                host.split(":")
            } else {
                listOf(host, "443")
            }
            val hostname = hostAndPort[0]
            val port = hostAndPort[1].toInt()
            
            // 发送200连接建立响应
            clientSocket.outputStream.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            clientSocket.outputStream.flush()
            
            // 建立与目标服务器的连接
            val serverSocket = Socket(hostname, port)
            
            // 记录目标主机，便于后续扩展（SNI检测/分类）
            java.lang.System.out.println("HTTPS CONNECT: $hostname:$port")
            
            // 双向转发数据（TLS隧道，无法直接解密）
            forwardData(clientSocket.inputStream, serverSocket.outputStream, hostname.contains("tenhou.net"), true)
            forwardData(serverSocket.inputStream, clientSocket.outputStream, hostname.contains("tenhou.net"), false)
        } catch (e: Exception) {
            System.out.println("处理HTTPS连接失败: ${e.message}")
            try {
                clientSocket.close()
            } catch (ignored: IOException) {
            }
        }
    }

    private fun handleWebSocketUpgrade(clientSocket: Socket, request: String, requestLine: String, headers: Map<String, String>) {
        try {
            // 解析主机信息
            val host = headers["Host"] ?: extractHostFromRequestLine(requestLine)
            val isSecure = false // HTTP连接
            
            // 连接到目标服务器
            val serverSocket = Socket(host, 80)
            
            // 转发原始请求
            serverSocket.outputStream.write(request.toByteArray())
            serverSocket.outputStream.flush()
            
            // 读取服务器响应并转发
            val response = readHttpResponse(serverSocket.inputStream)
            clientSocket.outputStream.write(response.toByteArray())
            clientSocket.outputStream.flush()
            
            // 双向转发WebSocket数据
            forwardWebSocketData(clientSocket.inputStream, serverSocket.outputStream, host.contains("tenhou.net"), true)
            forwardWebSocketData(serverSocket.inputStream, clientSocket.outputStream, host.contains("tenhou.net"), false)
        } catch (e: Exception) {
            System.out.println("处理WebSocket升级失败: ${e.message}")
            try {
                clientSocket.close()
            } catch (ignored: IOException) {
            }
        }
    }

    private fun handleHttpGet(clientSocket: Socket, requestLine: String, headers: Map<String, String>) {
        try {
            // 解析URL
            val host = headers["Host"] ?: extractHostFromRequestLine(requestLine)
            val path = requestLine.split(" ")[1]
            val url = "http://$host$path"
            
            // 连接到目标服务器
            val serverSocket = Socket(host, 80)
            
            // 转发请求
            serverSocket.outputStream.write("$requestLine\r\n".toByteArray())
            for ((key, value) in headers) {
                serverSocket.outputStream.write("$key: $value\r\n".toByteArray())
            }
            serverSocket.outputStream.write("\r\n".toByteArray())
            serverSocket.outputStream.flush()
            
            // 读取响应并转发
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (serverSocket.inputStream.read(buffer).also { bytesRead = it } != -1) {
                clientSocket.outputStream.write(buffer, 0, bytesRead)
                clientSocket.outputStream.flush()
            }
        } catch (e: Exception) {
            System.out.println("处理HTTP GET请求失败: ${e.message}")
            try {
                clientSocket.close()
            } catch (ignored: IOException) {
            }
        }
    }

    fun forwardWebSocketData(input: InputStream, output: OutputStream, isTenhou: Boolean, isClientToServer: Boolean) {
        try {
            val buffer = ByteArray(4096)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                
                // 解析WebSocket帧（简化）
                val (isText, payload) = parseWebSocketFrame(buffer, bytesRead)
                
                // 如果是文本帧，调用基础代理的文本消息处理逻辑
                if (isText && payload.isNotEmpty()) {
                    try {
                        val message = String(payload)
                        // 调用基础实现，解析并更新GameState
                        processTextMessage(message)
                    } catch (e: Exception) {
                        println("文本帧解析失败: ${e.message}")
                    }
                } else if (!isText && payload.isNotEmpty()) {
                    // 二进制帧，尝试调用通用消息处理（包含protobuf解码）
                    try {
                        processMessage(payload)
                    } catch (e: Exception) {
                        println("二进制帧处理失败: ${e.message}")
                    }
                }

                // 继续原始数据转发，避免影响正常连接
                output.write(buffer, 0, bytesRead)
                output.flush()
            }
        } catch (e: IOException) {
            // 连接关闭异常，正常处理
        } catch (e: Exception) {
            println("转发WebSocket数据失败: ${e.message}")
        }
    }

    fun parseWebSocketFrame(buffer: ByteArray, length: Int): Pair<Boolean, ByteArray> {
        if (length < 2) return Pair(false, ByteArray(0))
        
        val opcode = buffer[0].toInt() and 0x0F
        val isText = opcode == 0x01
        
        // 跳过基本头部，这里简化处理（未处理mask等）
        return Pair(isText, buffer.copyOfRange(2, length))
    }

    fun forwardData(input: InputStream, output: OutputStream, isTenhou: Boolean, isClientToServer: Boolean) {
        try {
            val buffer = ByteArray(4096)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                output.write(buffer, 0, bytesRead)
                output.flush()
            }
        } catch (e: IOException) {
            // 连接关闭异常，正常处理
        } catch (e: Exception) {
            System.out.println("转发数据失败: ${e.message}")
        }
    }

    fun readHttpRequest(input: InputStream): String {
        val reader = BufferedReader(InputStreamReader(input))
        val requestBuilder = StringBuilder()
        var line: String
        
        while (reader.readLine().also { line = it ?: "" } != "") {
            requestBuilder.append(line).append("\r\n")
        }
        requestBuilder.append("\r\n")
        
        return requestBuilder.toString()
    }

    fun readHttpResponse(input: InputStream): String {
        val buffer = ByteArray(4096)
        val responseBuilder = StringBuilder()
        var bytesRead: Int
        
        while (input.available() > 0) {
            bytesRead = input.read(buffer)
            if (bytesRead == -1) break
            responseBuilder.append(String(buffer, 0, bytesRead))
        }
        
        return responseBuilder.toString()
    }

    fun parseHttpRequest(request: String): Pair<String, Map<String, String>> {
        val lines = request.split("\r\n")
        val requestLine = lines[0]
        val headers = mutableMapOf<String, String>()
        
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) break
            val colonIndex = line.indexOf(":")
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }
        
        return Pair(requestLine, headers)
    }

    fun parseHttpHeaders(response: String): Pair<String, Map<String, String>> {
        return parseHttpRequest(response) // 复用HTTP请求的解析逻辑
    }

    fun isWebSocketUpgradeRequest(headers: Map<String, String>): Boolean {
        return headers.containsKey("Upgrade") && 
               headers["Upgrade"]?.contains("websocket", ignoreCase = true) == true
    }

    fun extractHostFromRequestLine(requestLine: String): String {
        val parts = requestLine.split(" ")
        if (parts.size < 2) return ""
        
        val path = parts[1]
        if (path.startsWith("http://")) {
            val url = path.substring(7)
            return url.split("/")[0]
        }
        
        return ""
    }

    fun extractHostFromConnectRequest(requestLine: String): String {
        val parts = requestLine.split(" ")
        if (parts.size < 2) return ""
        return parts[1]
    }

    fun parseContentLength(headers: String): Int {
        val lines = headers.split("\r\n")
        for (line in lines) {
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                return line.substring(15).trim().toInt()
            }
        }
        return 0
    }

    fun isAdmin(): Boolean {
        return true // 简化实现
    }

    private fun runAsAdmin() {
        try {
            val pb = ProcessBuilder(
                "powershell",
                "Start-Process",
                "java",
                "-jar",
                System.getProperty("java.class.path"),
                "-verb",
                "runAs"
            )
            pb.start()
        } catch (ex: Exception) {
            System.out.println("以管理员权限重新启动失败:")
        }
    }

    fun saveOriginalProxySettings() {
        try {
            // 读取Windows系统代理(HKCU)原始值
            savedProxyEnable = queryRegistryValue(
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "ProxyEnable"
            )
            savedProxyServer = queryRegistryValue(
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "ProxyServer"
            )
            savedProxyOverride = queryRegistryValue(
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "ProxyOverride"
            )
            java.lang.System.out.println("已保存原始系统代理设置: enable=$savedProxyEnable, server=$savedProxyServer, override=$savedProxyOverride")
        } catch (e: Exception) {
            java.lang.System.out.println("保存原始代理设置失败: ${e.message}")
        }
    }

    fun setSystemProxy(port: Int) {
        try {
            // 设置Windows系统代理(HKCU)
            setWindowsSystemProxy(port)
            java.lang.System.out.println("已设置Windows系统代理到 127.0.0.1:${port}")
        } catch (e: Exception) {
            java.lang.System.out.println("设置系统代理失败: ${e.message}")
        }
    }

    fun restoreOriginalProxySettings() {
        try {
            // 恢复Windows系统代理(HKCU)
            restoreWindowsSystemProxy()
            java.lang.System.out.println("已恢复原始Windows系统代理设置")
        } catch (e: Exception) {
            java.lang.System.out.println("恢复原始代理设置失败: ${e.message}")
        }
    }

    fun getJarPath(): String {
        return System.getProperty("java.class.path")
    }

    private fun runRegCommand(args: List<String>): String {
        val pb = ProcessBuilder("reg", *args.toTypedArray())
        pb.redirectErrorStream(true)
        val process = pb.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.appendLine(line)
        }
        process.waitFor()
        return output.toString()
    }

    private fun parseRegQueryValue(output: String, valueName: String): String? {
        val lines = output.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith(valueName)) {
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val type = parts[1]
                    val valuePart = trimmed.substring(trimmed.indexOf(type) + type.length).trim()
                    return if (type.equals("REG_DWORD", ignoreCase = true) && valuePart.startsWith("0x")) {
                        Integer.decode(valuePart).toString()
                    } else {
                        valuePart
                    }
                }
            }
        }
        return null
    }

    private fun queryRegistryValue(path: String, valueName: String): String? {
        val output = runRegCommand(listOf("query", path, "/v", valueName))
        return parseRegQueryValue(output, valueName)
    }

    private fun setWindowsSystemProxy(port: Int) {
        val baseKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
        val server = "127.0.0.1:$port"
        // Enable proxy
        runRegCommand(listOf("add", baseKey, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "1", "/f"))
        // Set proxy server
        runRegCommand(listOf("add", baseKey, "/v", "ProxyServer", "/t", "REG_SZ", "/d", server, "/f"))
        // Preserve or set override
        val overrideValue = savedProxyOverride ?: "<local>"
        runRegCommand(listOf("add", baseKey, "/v", "ProxyOverride", "/t", "REG_SZ", "/d", overrideValue, "/f"))
    }

    private fun restoreWindowsSystemProxy() {
        val baseKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
        // Restore ProxyEnable
        if (savedProxyEnable != null) {
            runRegCommand(listOf("add", baseKey, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", savedProxyEnable!!, "/f"))
        } else {
            runRegCommand(listOf("add", baseKey, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "0", "/f"))
        }
        // Restore ProxyServer
        if (savedProxyServer != null) {
            runRegCommand(listOf("add", baseKey, "/v", "ProxyServer", "/t", "REG_SZ", "/d", savedProxyServer!!, "/f"))
        } else {
            runRegCommand(listOf("delete", baseKey, "/v", "ProxyServer", "/f"))
        }
        // Restore ProxyOverride
        if (savedProxyOverride != null) {
            runRegCommand(listOf("add", baseKey, "/v", "ProxyOverride", "/t", "REG_SZ", "/d", savedProxyOverride!!, "/f"))
        } else {
            runRegCommand(listOf("delete", baseKey, "/v", "ProxyOverride", "/f"))
        }
    }

    fun isTenhou(host: String): Boolean {
        return host.contains("tenhou.net")
    }

    fun buildRequest(requestLine: String, headers: Map<String, String>): String {
        val requestBuilder = StringBuilder()
        requestBuilder.append(requestLine).append("\r\n")
        
        for ((key, value) in headers) {
            requestBuilder.append("$key: $value\r\n")
        }
        requestBuilder.append("\r\n")
        
        return requestBuilder.toString()
    }
}

/**
 * 桌面平台的WebSocket代理工厂
 */
class DesktopWebSocketProxyFactory : WebSocketProxyFactory {
    override fun createWebSocketProxy(): WebSocketProxy {
        return DesktopWebSocketProxy()
    }
}
// 移除重复的顶层注册表工具方法（已在类内部实现）
// 顶层重复注册表工具方法已移除；类内部已提供成员方法。