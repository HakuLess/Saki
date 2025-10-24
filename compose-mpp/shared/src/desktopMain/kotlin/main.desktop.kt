import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.saki.mahjong.desktop.DesktopMahjongGameService
import com.saki.mahjong.service.MahjongGameService

/**
 * 获取平台名称
 */
actual fun getPlatformName(): String = "Desktop"

/**
 * 创建桌面端麻将游戏服务实例
 */
actual fun createMahjongGameService(): MahjongGameService {
    return DesktopMahjongGameService.create()
}

@Composable fun MainView() = App()

@Preview
@Composable
fun AppPreview() {
    App()
}