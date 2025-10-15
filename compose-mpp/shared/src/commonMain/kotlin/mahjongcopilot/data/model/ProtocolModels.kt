package mahjongcopilot.data.model

/**
 * MJAI 协议消息类型
 */
enum class MjaiMessageType {
    START_GAME,
    START_KYOKU,
    TSUMO,
    DAHAI,
    CHI,
    PON,
    KAN,
    ANKAN,
    KAKAN,
    DAIMINKAN,
    REACH,
    REACH_ACCEPTED,
    HORA,
    RYUKYOKU,
    END_KYOKU,
    END_GAME,
    NONE,
    NUKIDORA
}

/**
 * MJAI 协议消息
 */
data class MjaiMessage(
    val type: MjaiMessageType,
    val id: Int? = null,
    val actor: Int? = null,
    val target: Int? = null,
    val pai: String? = null,
    val consumed: List<String>? = null,
    val tsumogiri: Boolean? = null,
    val bakaze: String? = null,
    val doraMarker: String? = null,
    val kyoku: Int? = null,
    val honba: Int? = null,
    val kyotaku: Int? = null,
    val oya: Int? = null,
    val scores: List<Int>? = null,
    val tehais: List<List<String>>? = null,
    val reachDahai: MjaiMessage? = null,
    val metaOptions: List<Pair<String, Float>>? = null
)

/**
 * MJAI 动作类型
 */
enum class MjaiActionType {
    DAHAI,      // 打牌
    CHI,         // 吃
    PON,         // 碰
    KAN,         // 杠
    ANKAN,       // 暗杠
    KAKAN,       // 加杠
    DAIMINKAN,   // 大明杠
    REACH,       // 立直
    HORA,        // 和牌
    RYUKYOKU,    // 流局
    NONE,        // 无动作
    NUKIDORA     // 拔北
}

/**
 * MJAI 动作
 */
data class MjaiAction(
    val type: MjaiActionType,
    val pai: String? = null,
    val consumed: List<String>? = null,
    val target: Int? = null,
    val confidence: Float = 1.0f,
    val alternatives: List<MjaiAction>? = null
)

/**
 * Liqi 协议消息类型
 */
enum class LiqiMessageType {
    REQ,    // 请求
    RES,    // 响应
    NOTIFY  // 通知
}

/**
 * Liqi 协议方法
 */
enum class LiqiMethod(val value: String) {
    OAUTH2_LOGIN(".lq.Oauth2Login"),
    AUTH_GAME(".lq.FastTest.authGame"),
    INPUT_OPERATION(".lq.FastTest.inputOperation"),
    INPUT_CHI_PENG_GANG(".lq.FastTest.inputChiPengGang"),
    CONFIRM_NEW_ROUND(".lq.FastTest.confirmNewRound"),
    NOTIFY_GAME_BROADCAST(".lq.NotifyGameBroadcast"),
    NOTIFY_PLAYER_LOAD_GAME_READY(".lq.NotifyPlayerLoadGameReady"),
    CHECK_NETWORK_DELAY(".lq.checkNetworkDelay"),
    HEARTBEAT(".lq.heartbeat"),
    LOGIN_BEAT(".lq.loginBeat"),
    FETCH_ACCOUNT_ACTIVITY_DATA(".lq.fetchAccountActivityData"),
    FETCH_SERVER_TIME(".lq.fetchServerTime"),
    TERMINATE_GAME(".lq.terminateGame"),
    NOTIFY_GAME_FINISH_REWARD(".lq.NotifyGameFinishReward"),
    NOTIFY_ACTIVITY_REWARD(".lq.NotifyActivityReward"),
    NOTIFY_LEADERBOARD_POINT(".lq.NotifyLeaderboardPoint"),
    BROADCAST_IN_GAME(".lq.FastTest.broadcastInGame"),
    NOTIFY_PLAYER_CONNECTION_STATE(".lq.NotifyPlayerConnectionState"),
    PLAYER_LEAVING(".lq.PlayerLeaving"),
    CLEAR_LEAVING(".lq.FastTest.clearLeaving");
    
    companion object {
        fun fromString(value: String): LiqiMethod? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Liqi 协议消息
 */
data class LiqiMessage(
    val id: Int,
    val type: LiqiMessageType,
    val method: LiqiMethod,
    val data: Map<String, Any> = emptyMap()
)

/**
 * 游戏操作类型
 */
enum class GameOperationType {
    DISCARD,    // 打牌
    CHI,         // 吃
    PON,         // 碰
    KAN,         // 杠
    REACH,       // 立直
    AGARI,       // 和牌
    PASS         // 过
}

/**
 * 游戏操作
 */
data class GameOperation(
    val type: GameOperationType,
    val tile: String? = null,
    val consumed: List<String>? = null,
    val target: Int? = null,
    val position: Pair<Int, Int>? = null  // 屏幕坐标
)
