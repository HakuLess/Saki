package mahjongcopilot.data.model

/**
 * Liqi方法枚举
 * 参考MahjongCopilot的liqi.py实现
 */
enum class LiqiMethod {
    OAUTH2_LOGIN,        // 登录认证
    ACTION_PROTOTYPE,    // 动作原型
    AUTH_GAME,           // 游戏认证
    HEART_BEAT,          // 心跳
    FETCH_ACTIVITY,      // 获取活动
    LOGIN,               // 登录
    LOGIN_SUCCESS,       // 登录成功
    LOGIN_FAILURE,       // 登录失败
    LOBBY_FETCH_MATCHING_ROOM,  // 获取匹配房间
    LOBBY_CREATE_ROOM,   // 创建房间
    LOBBY_JOIN_ROOM,     // 加入房间
    LOBBY_LEAVE_ROOM,    // 离开房间
    GAME_START,          // 游戏开始
    GAME_END,            // 游戏结束
    GAME_ACTION,         // 游戏动作
    GAME_SYNC_GAME,      // 游戏同步
    GAME_SYNC_OTHER,     // 其他玩家同步
    GAME_RESUME,         // 游戏恢复
    GAME_RECONNECT,      // 游戏重连
    GAME_ACCOUNT_INFO,   // 账户信息
    GAME_FINISH,         // 游戏完成
    GAME_ROUND_END,      // 回合结束
    UNKNOWN              // 未知方法
}

/**
 * Liqi方法扩展函数
 */
fun fromString(methodName: String): LiqiMethod {
    return when (methodName) {
        "oauth2Login" -> LiqiMethod.OAUTH2_LOGIN
        "ActionPrototype" -> LiqiMethod.ACTION_PROTOTYPE
        "authGame" -> LiqiMethod.AUTH_GAME
        "heatBeat" -> LiqiMethod.HEART_BEAT
        "fetchActivity" -> LiqiMethod.FETCH_ACTIVITY
        "login" -> LiqiMethod.LOGIN
        "loginSuccess" -> LiqiMethod.LOGIN_SUCCESS
        "loginFailure" -> LiqiMethod.LOGIN_FAILURE
        "lobbyFetchMatchingRoom" -> LiqiMethod.LOBBY_FETCH_MATCHING_ROOM
        "lobbyCreateRoom" -> LiqiMethod.LOBBY_CREATE_ROOM
        "lobbyJoinRoom" -> LiqiMethod.LOBBY_JOIN_ROOM
        "lobbyLeaveRoom" -> LiqiMethod.LOBBY_LEAVE_ROOM
        "gameStart" -> LiqiMethod.GAME_START
        "gameEnd" -> LiqiMethod.GAME_END
        "gameAction" -> LiqiMethod.GAME_ACTION
        "syncGame" -> LiqiMethod.GAME_SYNC_GAME
        "syncOther" -> LiqiMethod.GAME_SYNC_OTHER
        "gameResume" -> LiqiMethod.GAME_RESUME
        "gameReconnect" -> LiqiMethod.GAME_RECONNECT
        "accountInfo" -> LiqiMethod.GAME_ACCOUNT_INFO
        "gameFinish" -> LiqiMethod.GAME_FINISH
        "roundEnd" -> LiqiMethod.GAME_ROUND_END
        else -> LiqiMethod.UNKNOWN
    }
}