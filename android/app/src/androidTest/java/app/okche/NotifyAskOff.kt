package app.okche

import app.okche.ui.med.notifyAskOn
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/** 화면 테스트는 허락 안내 창 없이(build.gradle.kts `listener`) — 안내 창 테스트만 켰다가 끈다. */
class NotifyAskOff : RunListener() {
    override fun testRunStarted(description: Description?) {
        notifyAskOn = false
    }
}
