import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import mahjongcopilot.presentation.RealGameApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Mahjong Copilot",
        state = rememberWindowState(size = DpSize(800.dp, 600.dp))
    ) {
        RealGameApp()
    }
}