package app.okche

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import app.okche.store.OkcheDatabase
import app.okche.store.onboardingDone
import app.okche.store.setOnboardingDone
import app.okche.ui.OnboardingScreen
import app.okche.ui.RootTabs
import app.okche.ui.pet.PetRegisterScreen
import app.okche.ui.theme.OkcheColor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 밝은 화면만 쓰므로 기기가 다크 모드여도 상태바·내비게이션 바 아이콘을 어둡게 고정한다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent { OkcheApp() }
        // 저장소를 열고 설정 줄을 없을 때만 만든다(IGNORE라 화면을 다시 만들 때 여러 번 불려도 안전).
        lifecycleScope.launch { OkcheDatabase.get(this@MainActivity).dao().loadOrCreateSetting() }
    }
}

private enum class Screen { Start, Register, Tabs }

/** 첫 화면 고르기: 지운 줄 뺀 아이가 없고 '먼저 둘러볼게요'를 누른 적 없으면 시작하기, 아니면 탭. */
@Composable
private fun OkcheApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf<Screen?>(null) }
    LaunchedEffect(Unit) {
        if (screen == null) {
            val noPets = OkcheDatabase.get(context).dao().pets().isEmpty()
            screen = if (noPets && !context.onboardingDone()) Screen.Start else Screen.Tabs
        }
    }
    when (screen) {
        null -> Box(Modifier.fillMaxSize().background(OkcheColor.Hanji))
        Screen.Start -> OnboardingScreen(
            onRegister = { screen = Screen.Register },
            onBrowse = {
                scope.launch {
                    context.setOnboardingDone(true)
                    screen = Screen.Tabs
                }
            },
        )
        Screen.Register -> PetRegisterScreen(onClose = { screen = Screen.Start }, onSaved = { screen = Screen.Tabs })
        Screen.Tabs -> RootTabs()
    }
}
