package mahjongcopilot.platform

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mahjongcopilot.data.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Windows平台进程监控器
 * 用于检测游戏客户端是否运行
 */
class WindowsProcessMonitor {
    
    private val _isGameRunning = MutableStateFlow(false)
    val isGameRunning: StateFlow<Boolean> = _isGameRunning.asStateFlow()
    
    private val _gameProcessInfo = MutableStateFlow<GameProcessInfo?>(null)
    val gameProcessInfo: StateFlow<GameProcessInfo?> = _gameProcessInfo.asStateFlow()
    
    private val mutex = Mutex()
    private val isMonitoring = AtomicBoolean(false)
    private var monitoringJob: Job? = null
    
    // 雀魂客户端可能的进程名
    private val majsoulProcessNames = listOf(
        "Majsoul",          // 官方客户端
        "Majsoul.exe",      // 官方客户端
        "majsoul",          // 可能的小写版本
        "雀魂",              // 中文进程名
        "MahjongSoul",      // 英文版本
        "MahjongSoul.exe"   // 英文版本
    )
    
    /**
     * 开始监控游戏进程
     */
    suspend fun startMonitoring(): Result<Unit> {
        return try {
            mutex.withLock {
                if (isMonitoring.get()) {
                    return Result.success(Unit)
                }
                
                isMonitoring.set(true)
                
                // 启动监控任务
                monitoringJob = CoroutineScope(Dispatchers.IO).launch {
                    try {
                        while (isMonitoring.get()) {
                            checkGameProcess()
                            delay(2000) // 每2秒检查一次
                        }
                    } catch (e: Exception) {
                        println("进程监控错误: ${e.message}")
                    }
                }
                
                // 立即执行一次检查
                checkGameProcess()
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            isMonitoring.set(false)
            Result.failure(e)
        }
    }
    
    /**
     * 停止监控游戏进程
     */
    suspend fun stopMonitoring(): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isMonitoring.get()) {
                    return Result.success(Unit)
                }
                
                isMonitoring.set(false)
                monitoringJob?.cancel()
                monitoringJob = null
                
                _isGameRunning.value = false
                _gameProcessInfo.value = null
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 检查游戏进程是否运行
     */
    private suspend fun checkGameProcess() {
        try {
            val processInfo = findMajsoulProcess()
            val wasRunning = _isGameRunning.value
            val isRunning = processInfo != null
            
            _isGameRunning.value = isRunning
            _gameProcessInfo.value = processInfo
            
            if (isRunning && !wasRunning) {
                println("检测到游戏客户端启动: ${processInfo?.processName} (PID: ${processInfo?.processId})")
            } else if (!isRunning && wasRunning) {
                println("检测到游戏客户端关闭")
            }
        } catch (e: Exception) {
            println("检查游戏进程失败: ${e.message}")
        }
    }
    
    /**
     * 查找雀魂游戏进程
     */
    private fun findMajsoulProcess(): GameProcessInfo? {
        try {
            // 使用tasklist命令查找进程
            val process = ProcessBuilder("tasklist", "/fo", "csv", "/nh").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line?.split(",")
                if (parts != null && parts.size >= 2) {
                    val processName = parts[0].trim('"')
                    val processIdStr = parts[1].trim('"')
                    
                    // 检查是否是雀魂进程
                    if (majsoulProcessNames.any { processName.contains(it, ignoreCase = true) }) {
                        val processId = processIdStr.toIntOrNull()
                        if (processId != null) {
                            return GameProcessInfo(
                                processName = processName,
                                processId = processId,
                                startTime = System.currentTimeMillis()
                            )
                        }
                    }
                }
            }
            
            reader.close()
            process.destroy()
            return null
        } catch (e: Exception) {
            println("查找雀魂进程失败: ${e.message}")
            return null
        }
    }
    
    /**
     * 检查是否正在监控
     */
    fun isMonitoringActive(): Boolean {
        return isMonitoring.get()
    }
}

/**
 * 游戏进程信息
 */
data class GameProcessInfo(
    val processName: String,
    val processId: Int,
    val startTime: Long
)