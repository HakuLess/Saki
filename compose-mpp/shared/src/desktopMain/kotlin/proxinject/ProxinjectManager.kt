package proxinject

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Proxinject 管理器
 * 封装 proxinject 工具的启动、停止和状态管理
 */
class ProxinjectManager(
    private val config: ProxinjectConfig = ProxinjectConfig()
) {
    private var process: Process? = null
    private var monitorJob: Job? = null
    private val _status = MutableStateFlow(ProxinjectStatus.STOPPED)
    val status: StateFlow<ProxinjectStatus> = _status.asStateFlow()
    
    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    /**
     * 启动 proxinject 进程
     * @param targetProcess 目标进程名称（如 "steam"）
     * @param proxyAddress SOCKS5 代理地址（如 "127.0.0.1:1080"）
     */
    suspend fun start(targetProcess: String, proxyAddress: String): Boolean {
        if (_status.value == ProxinjectStatus.RUNNING) {
            log("Proxinject 已在运行中")
            return true
        }

        try {
            _status.value = ProxinjectStatus.STARTING
            log("正在启动 proxinject...")

            // 确保 proxinject 可执行文件存在
            val executablePath = ensureProxinjectExecutable()
            if (executablePath == null) {
                _status.value = ProxinjectStatus.ERROR
                log("错误: 无法找到 proxinject 可执行文件")
                return false
            }

            // 构建命令行参数
            val command = buildList {
                add(executablePath)
                add("-n")
                add(targetProcess)
                add("-p")
                add(proxyAddress)
                if (config.enableLogging) {
                    add("-l")
                }
                if (config.injectSubprocesses) {
                    add("-s")
                }
            }

            log("执行命令: ${command.joinToString(" ")}")

            // 启动进程
            val processBuilder = ProcessBuilder(command)
                .directory(File(executablePath).parentFile)
                .redirectErrorStream(true)

            process = processBuilder.start()
            
            // 启动监控协程
            monitorJob = CoroutineScope(Dispatchers.IO).launch {
                monitorProcess()
            }

            _status.value = ProxinjectStatus.RUNNING
            log("Proxinject 启动成功")
            return true

        } catch (e: Exception) {
            _status.value = ProxinjectStatus.ERROR
            log("启动失败: ${e.message}")
            return false
        }
    }

    /**
     * 停止 proxinject 进程
     */
    suspend fun stop() {
        if (_status.value == ProxinjectStatus.STOPPED) {
            return
        }

        try {
            _status.value = ProxinjectStatus.STOPPING
            log("正在停止 proxinject...")

            monitorJob?.cancel()
            monitorJob = null

            process?.let { proc ->
                if (proc.isAlive) {
                    proc.destroyForcibly()
                    proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            process = null

            _status.value = ProxinjectStatus.STOPPED
            log("Proxinject 已停止")

        } catch (e: Exception) {
            _status.value = ProxinjectStatus.ERROR
            log("停止失败: ${e.message}")
        }
    }

    /**
     * 检查是否正在运行
     */
    val isRunning: Boolean
        get() = _status.value == ProxinjectStatus.RUNNING

    /**
     * 确保 proxinject 可执行文件存在并返回路径
     */
    private fun ensureProxinjectExecutable(): String? {
        val currentDir = System.getProperty("user.dir")
        log("当前工作目录: $currentDir")
        
        // 检查当前目录的 proxinject 文件夹
        var proxinjectDir = File(currentDir, "proxinject/release")
        var cliExecutable = File(proxinjectDir, "proxinjector-cli.exe")
        
        log("检查路径: ${cliExecutable.absolutePath}")
        if (cliExecutable.exists()) {
            log("找到可执行文件: ${cliExecutable.absolutePath}")
            return cliExecutable.absolutePath
        }
        
        // 检查上级目录的 proxinject 文件夹（适用于从 compose-mpp 子目录运行的情况）
        val parentDir = File(currentDir).parent
        if (parentDir != null) {
            proxinjectDir = File(parentDir, "proxinject/release")
            cliExecutable = File(proxinjectDir, "proxinjector-cli.exe")
            
            log("检查父目录路径: ${cliExecutable.absolutePath}")
            if (cliExecutable.exists()) {
                log("在父目录找到可执行文件: ${cliExecutable.absolutePath}")
                return cliExecutable.absolutePath
            }
        } else {
            log("无法获取父目录")
        }

        // 检查上上级目录的 proxinject 文件夹（适用于从 desktopApp 子目录运行的情况）
        if (parentDir != null) {
            val grandParentDir = File(parentDir).parent
            if (grandParentDir != null) {
                proxinjectDir = File(grandParentDir, "proxinject/release")
                cliExecutable = File(proxinjectDir, "proxinjector-cli.exe")
                
                log("检查祖父目录路径: ${cliExecutable.absolutePath}")
                if (cliExecutable.exists()) {
                    log("在祖父目录找到可执行文件: ${cliExecutable.absolutePath}")
                    return cliExecutable.absolutePath
                }
            } else {
                log("无法获取祖父目录")
            }
        }

        // 检查系统 PATH 中是否有 proxinjector-cli
        try {
            log("检查系统PATH...")
            val process = ProcessBuilder("where", "proxinjector-cli.exe").start()
            val result = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && result.isNotEmpty()) {
                log("在系统PATH中找到: $result")
                return result.lines().first()
            }
        } catch (e: Exception) {
            log("检查系统PATH时出错: ${e.message}")
        }

        log("未找到 proxinjector-cli.exe 可执行文件")
        return null
    }

    /**
     * 监控进程状态
     */
    private suspend fun monitorProcess() {
        val proc = process ?: return
        
        try {
            // 读取进程输出
            val reader = proc.inputStream.bufferedReader()
            while (proc.isAlive && !currentCoroutineContext().job.isCancelled) {
                val line = reader.readLine()
                if (line != null) {
                    log("[proxinject] $line")
                } else {
                    delay(100)
                }
            }
        } catch (e: Exception) {
            if (!currentCoroutineContext().job.isCancelled) {
                log("进程监控错误: ${e.message}")
            }
        }

        // 进程结束
        if (!currentCoroutineContext().job.isCancelled) {
            val exitCode = proc.exitValue()
            if (exitCode == 0) {
                log("Proxinject 进程正常退出")
                _status.value = ProxinjectStatus.STOPPED
            } else {
                log("Proxinject 进程异常退出，退出码: $exitCode")
                _status.value = ProxinjectStatus.ERROR
            }
        }
    }

    /**
     * 记录日志
     */
    private fun log(message: String) {
        val timestamp = java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        )
        val logMessage = "[$timestamp] $message"
        println(logMessage)
        _logs.tryEmit(logMessage)
    }
}

/**
 * Proxinject 状态枚举
 */
enum class ProxinjectStatus {
    STOPPED,    // 已停止
    STARTING,   // 启动中
    RUNNING,    // 运行中
    STOPPING,   // 停止中
    ERROR       // 错误状态
}

/**
 * Proxinject 配置
 */
data class ProxinjectConfig(
    val enableLogging: Boolean = true,          // 启用网络连接日志
    val injectSubprocesses: Boolean = false,    // 注入子进程
    val autoRestart: Boolean = true             // 自动重启（进程意外退出时）
)