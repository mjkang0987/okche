package app.okche

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.okche.store.OkcheDatabase
import app.okche.ui.RootTabs
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 밝은 화면만 쓰므로 기기가 다크 모드여도 상태바·내비게이션 바 아이콘을 어둡게 고정한다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent { RootTabs() }
        // 저장소를 열고 설정 줄을 없을 때만 만든다(IGNORE라 화면을 다시 만들 때 여러 번 불려도 안전).
        lifecycleScope.launch { OkcheDatabase.get(this@MainActivity).dao().loadOrCreateSetting() }
    }
}
