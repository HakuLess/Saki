package model

// 网络拦截产生的原始事件（连接层、端点解析、原始消息）
sealed interface NetEvent { val timestamp: Long }

enum class Direction { CLIENT_TO_SERVER, SERVER_TO_CLIENT }

data class ConnectionActive(
    val remoteIPs: List<String>,
    val establishedCount: Int,
    override val timestamp: Long,
) : NetEvent

data class ConnectionInactive(
    override val timestamp: Long,
) : NetEvent

data class EndpointResolved(
    val hosts: List<String>,
    val ips: List<String>,
    override val timestamp: Long,
) : NetEvent

data class RawMessageCaptured(
    val direction: Direction,
    val size: Int,
    val previewHex: String,
    override val timestamp: Long,
) : NetEvent

// 逻辑层的麻将事件占位（后续由解码器解析填充）
sealed interface GameEvent { val timestamp: Long }

data class RoundStarted(override val timestamp: Long) : GameEvent

data class TileDrawn(
    val playerId: Int,
    val tile: String,
    override val timestamp: Long,
) : GameEvent

data class TileDiscarded(
    val playerId: Int,
    val tile: String,
    override val timestamp: Long,
) : GameEvent

data class MeldMade(
    val playerId: Int,
    val type: String, // chi / pon / kan
    val tiles: List<String>,
    override val timestamp: Long,
) : GameEvent

data class RiichiDeclared(
    val playerId: Int,
    override val timestamp: Long,
) : GameEvent

data class HandWon(
    val playerId: Int,
    val kind: String, // ron / tsumo
    override val timestamp: Long,
) : GameEvent