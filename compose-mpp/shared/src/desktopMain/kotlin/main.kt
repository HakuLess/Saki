package mahjongcopilot

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import mahjongcopilot.presentation.RealGameApp

fun main() = application {
    Window(
        title = "雀魂游戏拦截器 - 真实数据展示",
        onCloseRequest = ::exitApplication
    ) {
        RealGameApp()
    }
}