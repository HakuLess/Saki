import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import mahjongcopilot.presentation.MahjongCopilotApp

@OptIn(ExperimentalResourceApi::class)
@Composable
fun App() {
    MaterialTheme {
        MahjongCopilotApp()
    }
}

expect fun getPlatformName(): String