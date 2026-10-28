package app.okche.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// 이 기기만의 설정(문서 10장) — 백업에 넣지 않는다. 열쇠 이름은 문서 칸 이름 그대로.
private val Context.deviceSettings by preferencesDataStore("device_settings")
private val ONBOARDING_DONE = booleanPreferencesKey("onboardingDone")

/** '먼저 둘러볼게요'를 누른 적 있는지. */
suspend fun Context.onboardingDone(): Boolean = deviceSettings.data.first()[ONBOARDING_DONE] ?: false

suspend fun Context.setOnboardingDone(done: Boolean) {
    deviceSettings.edit { it[ONBOARDING_DONE] = done }
}
