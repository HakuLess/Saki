package collector

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
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
            println("[SteamIntercept] 正在启动MITM代理，端口: ${config.mitmPort}")
            mitm = MitmProxyServer(
                port = config.mitmPort,
                hostPatterns = config.hostPatterns,
                onCapture = { bytes, inbound ->
                    // 入站/出站方向，统一喂入原始消息并记录方向
                    feedRawMessage(bytes, inbound)
                },
                enableSocks5 = config.enableProxinject  // 当启用proxinject时使用SOCKS5模式
            )
            try {
                mitm?.start()
                println("[SteamIntercept] MITM代理启动成功")
            } catch (e: Exception) {
                println("[SteamIntercept] MITM代理启动失败: ${e.message}")
                e.printStackTrace()
            }
        } else if (!config.enableMitm) {
            println("[SteamIntercept] MITM代理已禁用")
        } else {
            println("[SteamIntercept] MITM代理已存在，跳过启动")
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
        val preview = data.take(32).joinToString(" ") { "%02X".format(it) }
        
        // 增加详细的网络拦截日志
        println("[MITM] ===== 网络数据捕获 =====")
        println("[MITM] 方向: $dir")
        println("[MITM] 数据大小: ${data.size} bytes")
        println("[MITM] 数据预览: $preview")
        
        // 检查是否是WebSocket数据
        val isWebSocket = checkIfWebSocketData(data)
        println("[MITM] 是否WebSocket数据: $isWebSocket")
        
        // 检查是否包含雀魂相关标识
        val containsMajsoul = checkMajsoulSignature(data)
        println("[MITM] 包含雀魂标识: $containsMajsoul")
        
        // 尝试解析为文本
        val textPreview = tryParseAsText(data)
        if (textPreview.isNotEmpty()) {
            println("[MITM] 文本内容预览: $textPreview")
        }
        
        println("[MITM] ========================")
        
        _events.tryEmit(
            RawMessageCaptured(
                direction = dir,
                size = data.size,
                previewHex = preview,
                timestamp = System.currentTimeMillis()
            )
        )
        
        // 解码并应用事件
        val decoded = MajsoulDecoder.decodeFrame(data)
        if (decoded.isNotEmpty()) {
            println("[MITM] 解码成功，获得 ${decoded.size} 个事件")
            applyEvents(decoded)
        } else {
            println("[MITM] 解码失败或无有效事件")
        }
    }
    
    /**
     * 检查是否是WebSocket数据
     */
    private fun checkIfWebSocketData(data: ByteArray): Boolean {
        if (data.isEmpty()) return false
        
        // WebSocket帧格式检查
        val firstByte = data[0].toInt() and 0xFF
        val fin = (firstByte and 0x80) != 0
        val opcode = firstByte and 0x0F
        
        // 检查是否是有效的WebSocket opcode
        return when (opcode) {
            0x0, 0x1, 0x2, 0x8, 0x9, 0xA -> true  // 继续帧、文本帧、二进制帧、关闭帧、ping、pong
            else -> false
        }
    }
    
    /**
     * 检查是否包含雀魂相关标识
     */
    private fun checkMajsoulSignature(data: ByteArray): Boolean {
        val text = String(data, Charsets.UTF_8)
        val signatures = listOf(
            "maj-soul", "majsoul", "mahjong", "liqi", 
            "NotifyGameStart", "NotifyDealTile", "NotifyDiscardTile",
            "ActionPrototype", "GameMgr", "FastTest"
        )
        return signatures.any { text.contains(it, ignoreCase = true) }
    }
    
    /**
     * 尝试解析为文本内容
     */
    private fun tryParseAsText(data: ByteArray): String {
        return try {
            val text = String(data, Charsets.UTF_8)
            if (text.all { it.isLetterOrDigit() || it.isWhitespace() || "{}[]\":,.-_".contains(it) }) {
                text.take(200)  // 只显示前200个字符
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun applyEvents(events: List<GameEvent>) {
        val current = _state.value ?: createInitialState()
        var newState = current
        
        println("[SteamIntercept] 开始应用 ${events.size} 个事件")
        
        events.forEach { event ->
            println("[SteamIntercept] 处理事件: $event")
            when (event) {
                is HandTilesUpdated -> {
                    // 更新手牌数据
                    val updatedPlayers = current.players.toMutableList()
                    println("[SteamIntercept] 当前玩家数量: ${updatedPlayers.size}")
                    println("[SteamIntercept] 尝试更新玩家 ${event.playerId} 的手牌")
                    
                    if (event.playerId < updatedPlayers.size) {
                        val player = updatedPlayers[event.playerId]
                        println("[SteamIntercept] 原始手牌数据: ${event.tiles}")
                        
                        val tiles = event.tiles.mapNotNull { tileStr -> 
                            val parsed = parseTileString(tileStr)
                            if (parsed == null) {
                                println("[SteamIntercept] 无法解析牌: $tileStr")
                            } else {
                                println("[SteamIntercept] 成功解析牌: $tileStr -> $parsed")
                            }
                            parsed
                        }
                        
                        println("[SteamIntercept] 解析后的手牌: $tiles")
                        updatedPlayers[event.playerId] = player.copy(handTiles = tiles)
                        newState = current.copy(players = updatedPlayers)
                        
                        println("[SteamIntercept] 更新玩家 ${event.playerId} 手牌 (${event.fieldName}): ${event.tiles.joinToString(",")}")
                        println("[SteamIntercept] 新状态中玩家手牌数量: ${newState.players[event.playerId].handTiles.size}")
                    } else {
                        println("[SteamIntercept] 警告: 玩家ID ${event.playerId} 超出范围 (最大: ${updatedPlayers.size - 1})")
                    }
                }
                is TileDrawn -> {
                    println("[SteamIntercept] 玩家 ${event.playerId} 摸牌: ${event.tile}")
                }
                is TileDiscarded -> {
                    println("[SteamIntercept] 玩家 ${event.playerId} 打牌: ${event.tile}")
                }
                is MeldMade -> {
                    println("[SteamIntercept] 玩家 ${event.playerId} 副露 ${event.type}: ${event.tiles.joinToString(",")}")
                }
                is RoundStarted -> {
                    println("[SteamIntercept] 游戏开始")
                    newState = current.copy(isInGame = true)
                }
                is HandWon -> {
                    println("[SteamIntercept] 玩家 ${event.playerId} 和牌: ${event.kind}")
                }
                is RiichiDeclared -> {
                    println("[SteamIntercept] 玩家 ${event.playerId} 立直")
                    // 处理立直逻辑
                }
            }
        }
        
        if (newState != current) {
            _state.value = newState
        }
    }

    /**
     * 解析牌的字符串表示为Tile对象 - 增强版
     */
    private fun parseTileString(tileStr: String): Tile? {
        return try {
            // 雀魂的牌通常用数字表示，需要转换为我们的Tile格式
            when {
                // 标准数字格式：11-19(万), 21-29(筒), 31-39(索), 41-47(字)
                tileStr.matches(Regex("\\d+")) -> {
                    val num = tileStr.toInt()
                    when {
                        num in 11..19 -> Tile(TileType.MAN, num - 10)
                        num in 21..29 -> Tile(TileType.PIN, num - 20)
                        num in 31..39 -> Tile(TileType.SOU, num - 30)
                        num in 41..47 -> Tile(TileType.HONOR, num - 40)
                        // 扩展：支持更多格式
                        num in 1..9 -> Tile(TileType.MAN, num) // 默认万子
                        else -> null
                    }
                }
                // 字符格式：如 "1m", "5p", "9s", "1z"
                tileStr.matches(Regex("\\d+[mpsz]", RegexOption.IGNORE_CASE)) -> {
                    val number = tileStr.dropLast(1).toInt()
                    val suit = tileStr.last().lowercaseChar()
                    when (suit) {
                        'm' -> Tile(TileType.MAN, number)
                        'p' -> Tile(TileType.PIN, number)
                        's' -> Tile(TileType.SOU, number)
                        'z' -> Tile(TileType.HONOR, number)
                        else -> null
                    }
                }
                // 对象格式字符串：如 "type:1,value:5"
                tileStr.contains("type") && tileStr.contains("value") -> {
                    val typeMatch = Regex("type:(\\d+)").find(tileStr)
                    val valueMatch = Regex("value:(\\d+)").find(tileStr)
                    if (typeMatch != null && valueMatch != null) {
                        val type = typeMatch.groupValues[1].toInt()
                        val value = valueMatch.groupValues[1].toInt()
                        when (type) {
                            1 -> Tile(TileType.MAN, value)
                            2 -> Tile(TileType.PIN, value)
                            3 -> Tile(TileType.SOU, value)
                            4 -> Tile(TileType.HONOR, value)
                            else -> null
                        }
                    } else null
                }
                // JSON格式：如 "{\"type\":1,\"number\":5}"
                tileStr.startsWith("{") && tileStr.endsWith("}") -> {
                    try {
                        val json = Json.parseToJsonElement(tileStr).jsonObject
                        val type = json["type"]?.jsonPrimitive?.intOrNull
                        val number = json["number"]?.jsonPrimitive?.intOrNull ?: 
                                   json["value"]?.jsonPrimitive?.intOrNull
                        if (type != null && number != null) {
                            when (type) {
                                1 -> Tile(TileType.MAN, number)
                                2 -> Tile(TileType.PIN, number)
                                3 -> Tile(TileType.SOU, number)
                                4 -> Tile(TileType.HONOR, number)
                                else -> null
                            }
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
                else -> {
                    println("[SteamIntercept] 未知牌格式: $tileStr")
                    null
                }
            }
        } catch (e: Exception) {
            println("[SteamIntercept] 解析牌失败: $tileStr, 错误: ${e.message}")
            null
        }
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
        println("[SteamIntercept] 创建初始游戏状态")
        
        return GameState(
            isInGame = false, // 初始状态为未在游戏中，等待真实连接
            round = GameRound.EAST_1,
            currentPlayer = 0,
            players = listOf(
                Player(
                    id = 0,
                    name = "我",
                    score = 25000,
                    wind = Wind.EAST,
                    handTiles = emptyList(), // 初始手牌为空，等待真实数据
                    discardedTiles = emptyList(),
                    melds = emptyList()
                ),
                Player(
                    id = 1,
                    name = "对手A",
                    score = 25000,
                    wind = Wind.SOUTH,
                    handTiles = emptyList(),
                    discardedTiles = emptyList(),
                    melds = emptyList()
                ),
                Player(
                    id = 2,
                    name = "对手B",
                    score = 25000,
                    wind = Wind.WEST,
                    handTiles = emptyList(),
                    discardedTiles = emptyList(),
                    melds = emptyList()
                ),
                Player(
                    id = 3,
                    name = "对手C",
                    score = 25000,
                    wind = Wind.NORTH,
                    handTiles = emptyList(),
                    discardedTiles = emptyList(),
                    melds = emptyList()
                )
            ),
            remainingTiles = 70,
            doraIndicators = emptyList(),
            lastDiscardedTile = null,
            canChi = false,
            canPon = false,
            canKan = false,
            canRon = false,
            canTsumo = false,
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