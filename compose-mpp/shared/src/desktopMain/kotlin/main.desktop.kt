import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import mahjongcopilot.domain.service.impl.AutomationServiceImpl
import mahjongcopilot.data.repository.WindowsAutomationRepositoryImpl
import mahjongcopilot.data.repository.GameStateRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

actual fun getPlatformName(): String = "Desktop"

fun main() = application {
    // 初始化仓库和服务
    val automationRepository = WindowsAutomationRepositoryImpl()
    val gameStateRepository = GameStateRepositoryImpl()
    val automationService = AutomationServiceImpl(automationRepository, gameStateRepository)
    
    // 在后台线程中启动服务监控
    GlobalScope.launch(Dispatchers.Default) {
        // 初始化监控逻辑
        println("雀魂助手服务已启动")
    }
    
    Window(onCloseRequest = { 
        // 清理资源 - 在协程中调用挂起函数
        GlobalScope.launch(Dispatchers.Default) {
            automationService.disableAutomation()
            println("雀魂助手服务已关闭")
            // 延迟一点时间确保清理完成
            kotlinx.coroutines.delay(100)
            exitApplication()
        }
    }, title = "雀魂助手 (Saki)") {
        App()
    }
}