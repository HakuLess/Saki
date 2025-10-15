import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import mahjongcopilot.presentation.MahjongCopilotApp
// 导入Windows平台特定的实现
import mahjongcopilot.platform.MitmNetworkInterceptor

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        // 使用Windows平台特定的实现
        val networkInterceptor = MitmNetworkInterceptor()
        
        MahjongCopilotApp(
            networkInterceptor = networkInterceptor
        )
    }
}