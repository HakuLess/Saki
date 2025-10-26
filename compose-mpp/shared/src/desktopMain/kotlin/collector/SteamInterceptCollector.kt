package collector

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import model.*
import java.net.InetAddress
import java.nio.charset.Charset
import decoder.MajsoulDecoder
import net.MitmProxyServer
import proxinject.ProxinjectManager
import proxinject.ProxinjectStatus

/**
 * 基于系统网络连接的简单拦截（Steam 客户端）
 * - DNS 解析 + DNS 缓存匹配，动态获取目标 IP 集合
 * - 周期性扫描本机 TCP 连接（netstat），命中目标 IP 计数
 *
 * 注意：此实现不解密 TLS/WSS 负载，仅用于无扩展情况下的最小可用拦截。
 */
class SteamInterceptCollector(
    private val config: CollectorConfig = CollectorConfig()
) : GameStateCollector {

    private var collectingJob: Job? = null
    private val _state = MutableStateFlow<GameState?>(null)
    // 事件管道（连接层与原始消息）
    private val _events = MutableSharedFlow<NetEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<NetEvent> get() = _events.asSharedFlow()
    private var lastConnected = false
    private var lastTargets: Set<String> = emptySet()
    private var mitm: MitmProxyServer? = null
    private var proxinjectManager: ProxinjectManager? = null

    override val isCollecting: Boolean
        get() = collectingJob?.isActive == true

    /**
     * 获取 Proxinject 状态
     */
    val proxinjectStatus: StateFlow<ProxinjectStatus>?
        get() = proxinjectManager?.status

    /**
     * 获取 Proxinject 日志流
     */
    val proxinjectLogs: SharedFlow<String>?
        get() = proxinjectManager?.logs

    /**
     * 检查 Proxinject 是否正在运行
     */
    val isProxinjectRunning: Boolean
        get() = proxinjectManager?.isRunning == true

    override fun startCollecting(): Flow<GameState> {
        if (isCollecting) return _state.filterNotNull()
        _state.value = createInitialState()

        if (config.enableMitm && mitm == null) {
            mitm = MitmProxyServer(
                port = config.mitmPort,
                hostPatterns = config.hostPatterns,
                onCapture = { bytes, inbound ->
                    // 入站/出站方向，统一喂入原始消息并记录方向
                    feedRawMessage(bytes, inbound)
                },
                enableSocks5 = config.enableProxinject  // 当启用proxinject时使用SOCKS5模式
            )
            mitm?.start()
        }

        // 启用 proxinject 自动代理
        if (config.enableProxinject && proxinjectManager == null) {
            proxinjectManager = ProxinjectManager()
            val proxyAddress = "127.0.0.1:${config.mitmPort}"
            
            CoroutineScope(Dispatchers.IO).launch {
                val success = proxinjectManager?.start("steam", proxyAddress) ?: false
                if (success) {
                    println("[SteamIntercept] Proxinject 自动代理已启用")
                } else {
                    println("[SteamIntercept] Proxinject 启动失败")
                }
            }
        }

        collectingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val targetIPs = collectTargetIPs()
                    val details = checkEstablishedDetails(targetIPs)
                    val established = details.count

                    // 事件：端点解析变化（去重）
                    if (targetIPs != lastTargets) {
                        _events.tryEmit(
                            EndpointResolved(
                                hosts = config.steamHosts,
                                ips = targetIPs.toList(),
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        lastTargets = targetIPs
                    }

                    // 事件：连接状态变化
                    if (!lastConnected && established > 0) {
                        _events.tryEmit(
                            ConnectionActive(
                                remoteIPs = details.matchedIPs,
                                establishedCount = established,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        lastConnected = true
                    } else if (lastConnected && established == 0) {
                        _events.tryEmit(ConnectionInactive(System.currentTimeMillis()))
                        lastConnected = false
                    }

                    val current = _state.value ?: createInitialState()
                    val newState = current.copy(
                        isInGame = established > 0,
                        timestamp = System.currentTimeMillis()
                    )
                    _state.value = newState

                    if (config.debugMode) {
                        println("[SteamIntercept] targets=${targetIPs.size}, established=${established}")
                    }
                } catch (e: Exception) {
                    if (config.debugMode) println("[SteamIntercept] Poll error: ${e.message}")
                }
                delay(config.pollIntervalMs)
            }
        }

        return _state.filterNotNull()
    }

    // 原始消息输入（后续由MITM/Hook等来源调用）
    fun feedRawMessage(data: ByteArray, inbound: Boolean) {
        val dir = if (inbound) Direction.CLIENT_TO_SERVER else Direction.SERVER_TO_CLIENT
        val preview = data.take(16).joinToString("") { "%02X".format(it) }
        println("[MITM] Raw captured dir=$dir size=${data.size} preview=$preview")
        _events.tryEmit(
            RawMessageCaptured(
                direction = dir,
                size = data.size,
                previewHex = preview,
                timestamp = System.currentTimeMillis()
            )
        )
        val decoded = MajsoulDecoder.decodeFrame(data)
        if (decoded.isNotEmpty()) applyEvents(decoded)
    }

    private fun applyEvents(events: List<GameEvent>) {
        // TODO: 将解析事件映射为 GameState 变更（占位）
        // 目前不改变状态，仅保留事件管道。
    }

    // ---------------- private helpers -----------------

    // 新增：返回命中详情
    private data class EstablishedDetails(val count: Int, val matchedIPs: List<String>)

    private fun checkEstablishedDetails(targetIPs: Set<String>): EstablishedDetails {
        if (targetIPs.isEmpty()) return EstablishedDetails(0, emptyList())
        val process = ProcessBuilder("powershell", "-NoProfile", "-Command", "netstat -na -p TCP").start()
        val text = process.inputStream.bufferedReader(Charset.defaultCharset()).readText()
        val lines = text.lines()
        var count = 0
        val matched = mutableSetOf<String>()
        lines.forEach { line ->
            if (line.contains("ESTABLISHED", ignoreCase = true)) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val remote = parts[2]
                    val ip = remote.substringBeforeLast(":")
                    if (ip in targetIPs) {
                        count++
                        matched.add(ip)
                    }
                }
            }
        }
        return EstablishedDetails(count, matched.toList())
    }

    private fun collectTargetIPs(): Set<String> {
        val ips = mutableSetOf<String>()
        // 1) 解析明确的主机列表
        config.steamHosts.forEach { host ->
            ips.addAll(resolveHostIPs(host))
        }
        // 2) 从 DNS 缓存中匹配可能的域名，提取 IP
        ips.addAll(queryDnsCacheIPs(config.hostPatterns))
        return ips
    }

    private fun resolveHostIPs(host: String): Set<String> = try {
        InetAddress.getAllByName(host).map { it.hostAddress }.toSet()
    } catch (e: Exception) {
        emptySet()
    }

    private fun queryDnsCacheIPs(patterns: List<String>): Set<String> {
        if (patterns.isEmpty()) return emptySet()
        val entryConditions = patterns.joinToString(" -or ") { "\$_.Entry -like '*$it*'" }
        val nameConditions = patterns.joinToString(" -or ") { "\$_.Name -like '*$it*'" }
        // 同时匹配 Entry 与 Name 字段
        val ps = "Get-DnsClientCache | Where-Object { ($entryConditions) -or ($nameConditions) } | Select-Object -ExpandProperty Data"
        return try {
            val process = ProcessBuilder("powershell", "-NoProfile", "-Command", ps).start()
            val text = process.inputStream.bufferedReader(Charset.defaultCharset()).readText()
            val lines = text.lines()
            lines.mapNotNull { line ->
                val t = line.trim()
                if (t.matches(Regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}$"))) t
                else if (t.matches(Regex("^[0-9a-fA-F:]+$"))) t // IPv6
                else null
            }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun checkEstablishedConnections(targetIPs: Set<String>): Int {
        if (targetIPs.isEmpty()) return 0
        val process = ProcessBuilder("powershell", "-NoProfile", "-Command", "netstat -na -p TCP").start()
        val text = process.inputStream.bufferedReader(Charset.defaultCharset()).readText()
        val lines = text.lines()
        var count = 0
        lines.forEach { line ->
            if (line.contains("ESTABLISHED", ignoreCase = true)) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val remote = parts[2]
                    // 兼容 IPv6，取最后一个 ':' 之前的部分作为 IP
                    val ip = remote.substringBeforeLast(":")
                    if (ip in targetIPs) {
                        count++
                    }
                }
            }
        }
        return count
    }

    private fun createInitialState(): GameState {
        val players = listOf(
            Player(id = 0, name = "我", score = 25000, wind = Wind.EAST),
            Player(id = 1, name = "对手A", score = 25000, wind = Wind.SOUTH),
            Player(id = 2, name = "对手B", score = 25000, wind = Wind.WEST),
            Player(id = 3, name = "对手C", score = 25000, wind = Wind.NORTH),
        )
        return GameState(
            isInGame = false,
            round = GameRound.EAST_1,
            currentPlayer = 0,
            players = players,
            remainingTiles = 70,
            doraIndicators = emptyList(),
            lastDiscardedTile = null,
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun stopCollecting() {
        collectingJob?.cancel()
        collectingJob = null
        mitm?.stop()
        mitm = null
        
        // 停止 proxinject
        proxinjectManager?.stop()
        proxinjectManager = null
        
        println("[SteamIntercept] Stopped collecting")
    }

    override suspend fun getCurrentState(): GameState? = _state.value
}