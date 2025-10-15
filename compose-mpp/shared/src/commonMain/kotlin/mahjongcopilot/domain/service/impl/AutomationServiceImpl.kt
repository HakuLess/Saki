package mahjongcopilot.domain.service.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mahjongcopilot.data.model.*
import mahjongcopilot.domain.service.AutomationService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 自动化服务实现
 */
class AutomationServiceImpl : AutomationService {
    
    private val _automationState = MutableStateFlow(AutomationState.DISABLED)
    private val automationState: Flow<AutomationState> = _automationState.asStateFlow()
    
    private val _actionHistory = MutableStateFlow<List<AutomationAction>>(emptyList())
    private val actionHistory: Flow<List<AutomationAction>> = _actionHistory.asStateFlow()
    
    private val mutex = Mutex()
    private var isEnabled = false
    private var isPaused = false
    private var settings = AutomationSettings()
    private var automationJob: Job? = null
    
    override suspend fun enableAutomation(): Result<Unit> {
        return try {
            mutex.withLock {
                if (isEnabled) {
                    return Result.success(Unit)
                }
                
                isEnabled = true
                isPaused = false
                _automationState.value = AutomationState.ENABLED
                
                // 启动自动化监控
                automationJob = CoroutineScope(Dispatchers.IO).launch {
                    startAutomationLoop()
                }
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun disableAutomation(): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isEnabled) {
                    return Result.success(Unit)
                }
                
                isEnabled = false
                isPaused = false
                _automationState.value = AutomationState.DISABLED
                
                automationJob?.cancel()
                automationJob = null
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun isAutomationEnabled(): Boolean {
        return mutex.withLock { isEnabled }
    }
    
    override suspend fun executeAutomaticAction(decision: AiDecision): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isEnabled || isPaused) {
                    return Result.failure(Exception("Automation is not enabled or paused"))
                }
                
                val action = AutomationAction(
                    timestamp = System.currentTimeMillis(),
                    decision = decision,
                    success = true,
                    executionTime = decision.processingTime
                )
                
                // 添加到历史记录
                val currentHistory = _actionHistory.value
                _actionHistory.value = currentHistory + action
                
                // 模拟执行动作
                simulateActionExecution(decision)
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun pauseAutomation(): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isEnabled) {
                    return Result.failure(Exception("Automation is not enabled"))
                }
                
                isPaused = true
                _automationState.value = AutomationState.PAUSED
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun resumeAutomation(): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isEnabled) {
                    return Result.failure(Exception("Automation is not enabled"))
                }
                
                isPaused = false
                _automationState.value = AutomationState.ENABLED
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateSettings(newSettings: AutomationSettings): Result<Unit> {
        return try {
            mutex.withLock {
                settings = newSettings
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getSettings(): AutomationSettings {
        return mutex.withLock { settings }
    }
    
    override suspend fun getActionHistory(): List<AutomationAction> {
        return mutex.withLock { _actionHistory.value }
    }
    
    override suspend fun clearActionHistory(): Result<Unit> {
        return try {
            mutex.withLock {
                _actionHistory.value = emptyList()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun observeAutomationState(): Flow<AutomationState> {
        return automationState
    }
    
    override fun observeActionHistory(): Flow<List<AutomationAction>> {
        return actionHistory
    }
    
    /**
     * 启动自动化循环
     */
    private suspend fun startAutomationLoop() {
        try {
            while (isEnabled && !isPaused) {
                // 模拟自动化监控
                delay(1000)
                
                if (isPaused) {
                    _automationState.value = AutomationState.PAUSED
                } else {
                    _automationState.value = AutomationState.ENABLED
                }
            }
        } catch (e: Exception) {
            _automationState.value = AutomationState.ERROR
        }
    }
    
    /**
     * 模拟动作执行
     */
    private suspend fun simulateActionExecution(decision: AiDecision) {
        // 根据设置添加延迟
        if (settings.humanLikeDelay) {
            val delay = settings.actionDelay + (0..500).random()
            delay(delay)
        } else {
            delay(settings.actionDelay)
        }
        
        // 模拟执行不同类型的动作
        when (decision.action.type) {
            MjaiActionType.DAHAI -> {
                // 模拟打牌动作
                simulateDiscardAction(decision.action.pai)
            }
            MjaiActionType.CHI -> {
                // 模拟吃牌动作
                simulateCallAction("chi", decision.action.consumed)
            }
            MjaiActionType.PON -> {
                // 模拟碰牌动作
                simulateCallAction("pon", decision.action.consumed)
            }
            MjaiActionType.KAN -> {
                // 模拟杠牌动作
                simulateCallAction("kan", decision.action.consumed)
            }
            MjaiActionType.REACH -> {
                // 模拟立直动作
                simulateReachAction()
            }
            MjaiActionType.HORA -> {
                // 模拟和牌动作
                simulateAgariAction()
            }
            else -> {
                // 无动作
            }
        }
    }
    
    /**
     * 模拟打牌动作
     */
    private suspend fun simulateDiscardAction(tile: String?) {
        // 模拟点击打牌按钮
        println("AI 自动打牌: $tile")
    }
    
    /**
     * 模拟吃碰杠动作
     */
    private suspend fun simulateCallAction(type: String, consumed: List<String>?) {
        // 模拟点击吃碰杠按钮
        println("AI 自动$type: ${consumed?.joinToString(", ")}")
    }
    
    /**
     * 模拟立直动作
     */
    private suspend fun simulateReachAction() {
        // 模拟点击立直按钮
        println("AI 自动立直")
    }
    
    /**
     * 模拟和牌动作
     */
    private suspend fun simulateAgariAction() {
        // 模拟点击和牌按钮
        println("AI 自动和牌")
    }
}

