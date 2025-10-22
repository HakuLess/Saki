import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.saki.mahjong.desktop.DesktopMahjongGameService
import com.saki.mahjong.service.MahjongGameService

actual fun getPlatformName(): String = "Desktop"

// 实现平台特定的服务创建函数
actual fun createMahjongService(): MahjongGameService {
    return DesktopMahjongGameService()
}

@Composable fun MainView() = App()

@Preview
@Composable
fun AppPreview() {
    App()
}