package mahjongcopilot.domain.service.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mahjongcopilot.data.model.Decision
import mahjongcopilot.data.model.DecisionType
import mahjongcopilot.data.model.GameState
import mahjongcopilot.data.repository.AutomationRepository
import mahjongcopilot.data.repository.GameStateRepository
import mahjongcopilot.domain.service.AutomationAction
import mahjongcopilot.domain.service.AutomationService
import mahjongcopilot.domain.service.AutomationSettings
import mahjongcopilot.domain.service.AutomationState

/**
 * 自动化服务的默认实现
 */
class AutomationServiceImpl(
    private val automationRepository: AutomationRepository,
    private val gameStateRepository: GameStateRepository
) : AutomationService {
    
    // 自动化状态
    private val _automationStateFlow = MutableStateFlow<AutomationState>(AutomationState.Disabled)
    override val automationStateFlow: StateFlow<AutomationState> = _automationStateFlow.asStateFlow()
    
    // 动作历史记录
    private val _actionHistoryFlow = MutableStateFlow<List<AutomationAction>>(emptyList())
    override val actionHistoryFlow: StateFlow<List<AutomationAction>> = _actionHistoryFlow.asStateFlow()
    
    // 设置
    private val _settings = MutableStateFlow(AutomationSettings())
    
    // 互斥锁，确保线程安全
    private val mutex = Mutex()
    
    // 自动化循环任务
    private var automationJob: Job? = null
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Default)
    
    override suspend fun enableAutomation() {
        mutex.withLock {
            if (_automationStateFlow.value == AutomationState.Disabled) {
                _automationStateFlow.value = AutomationState.Enabled
                startAutomationLoop()
                addToHistory("自动化已启用", true)
            }
        }
    }
    
    override suspend fun disableAutomation() {
        mutex.withLock {
            _automationStateFlow.value = AutomationState.Disabled
            automationJob?.cancel()
            automationJob = null
            addToHistory("自动化已禁用", true)
        }
    }
    
    override suspend fun pauseAutomation() {
        mutex.withLock {
            if (_automationStateFlow.value == AutomationState.Enabled) {
                _automationStateFlow.value = AutomationState.Paused
                automationJob?.cancel()
                automationJob = null
                addToHistory("自动化已暂停", true)
            }
        }
    }
    
    override suspend fun resumeAutomation() {
        mutex.withLock {
            if (_automationStateFlow.value == AutomationState.Paused) {
                _automationStateFlow.value = AutomationState.Enabled
                startAutomationLoop()
                addToHistory("自动化已恢复", true)
            }
        }
    }
    
    override suspend fun executeAutomaticAction(decision: Decision) {
        mutex.withLock {
            // 只有在启用状态下才能执行自动操作
            if (_automationStateFlow.value != AutomationState.Enabled) {
                return
            }
            
            // 检查置信度阈值
            if (decision.confidence < _settings.value.decisionConfidenceThreshold) {
                addToHistory("跳过低置信度决策: ${decision.type}", false)
                return
            }
            
            // 根据决策类型执行相应的操作
            val success = simulateActionExecution(decision)
            
            // 添加到历史记录
            val description = "执行${decision.type}操作${decision.tile?.let { " - ${it.getDisplayName()}" } ?: ""}"
            addToHistory(description, success)
        }
    }
    
    override suspend fun updateSettings(settings: AutomationSettings) {
        mutex.withLock {
            _settings.value = settings
            addToHistory("自动化设置已更新", true)
        }
    }
    
    override fun getSettings(): AutomationSettings {
        return _settings.value
    }
    
    // 启动自动化监控循环
    private fun startAutomationLoop() {
        automationJob = scope.launch {
            while (isActive && _automationStateFlow.value == AutomationState.Enabled) {
                try {
                    // 在这里实现自动化监控逻辑
                    // 1. 获取当前游戏状态
                    // 2. 检查是否需要执行操作
                    // 3. 如果需要，获取AI决策并执行
                    
                    // 暂时只是一个示例框架，后续会完善
                    
                    // 等待一段时间再检查，避免CPU占用过高
                    kotlinx.coroutines.delay(500)
                } catch (e: Exception) {
                    // 记录错误但不中断循环
                    addToHistory("自动化循环错误: ${e.message}", false)
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }
    
    // 模拟动作执行
    private suspend fun simulateActionExecution(decision: Decision): Boolean {
        try {
            // 获取当前游戏状态
            // 由于gameStateRepository.gameStateFlow是Flow类型，我们需要使用collect来获取值
            var currentState: GameState? = null
            gameStateRepository.gameStateFlow.collect { state ->
                currentState = state
                return@collect
            }
            
            if (currentState == null) return false
            
            // 根据决策类型执行不同的操作
            when (decision.type) {
                DecisionType.DISCARD -> {
                    if (!_settings.value.autoDiscard || decision.tile == null) return false
                    // 实现打牌逻辑
                    // 这里需要根据具体的游戏界面布局来确定点击位置
                    return true
                }
                DecisionType.CHI -> {
                    if (!_settings.value.autoMeld || decision.tile == null) return false
                    // 实现吃牌逻辑
                    return true
                }
                DecisionType.PON -> {
                    if (!_settings.value.autoMeld || decision.tile == null) return false
                    // 实现碰牌逻辑
                    return true
                }
                DecisionType.KAN -> {
                    if (!_settings.value.autoMeld || decision.tile == null) return false
                    // 实现杠牌逻辑
                    return true
                }
                DecisionType.RIICHI -> {
                    if (!_settings.value.autoRiichi) return false
                    // 实现立直逻辑
                    return true
                }
                DecisionType.AGARI -> {
                    if (!_settings.value.autoAgari) return false
                    // 实现和牌逻辑
                    return true
                }
                DecisionType.PASS -> {
                    // 不需要执行任何操作
                    return true
                }
            }
        } catch (e: Exception) {
            addToHistory("动作执行错误: ${e.message}", false)
        }
        return false
    }
    
    // 添加到历史记录
    private fun addToHistory(description: String, success: Boolean) {
        _actionHistoryFlow.update { currentHistory ->
            val action = AutomationAction(
                timestamp = System.currentTimeMillis(),
                type = if (success) "SUCCESS" else "ERROR",
                description = description,
                success = success
            )
            
            // 保持历史记录不超过100条
            (listOf(action) + currentHistory).take(100)
        }
    }
}