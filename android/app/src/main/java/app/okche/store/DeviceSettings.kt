package app.okche.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// 이 기기만의 설정(문서 10장) — 백업에 넣지 않는다. 열쇠 이름은 문서 칸 이름 그대로.
private val Context.deviceSettings by preferencesDataStore("device_settings")
private val ONBOARDING_DONE = booleanPreferencesKey("onboardingDone")
private val LAST_PET_ID = stringPreferencesKey("lastPetId")
private val MEAL_HINT_DISMISSED = stringSetPreferencesKey("mealHintDismissed")

/** '먼저 둘러볼게요'를 누른 적 있는지. */
suspend fun Context.onboardingDone(): Boolean = deviceSettings.data.first()[ONBOARDING_DONE] ?: false

suspend fun Context.setOnboardingDone(done: Boolean) {
    deviceSettings.edit { it[ONBOARDING_DONE] = done }
}

/** 마지막으로 보던 아이(지금 보는 아이, 6-3). 화면이 바뀌는 대로 따라가게 흐름으로. */
val Context.lastPetId: Flow<String?> get() = deviceSettings.data.map { it[LAST_PET_ID] }

suspend fun Context.setLastPetId(id: String) {
    deviceSettings.edit { it[LAST_PET_ID] = id }
}

/** 수라 끼니 안내에서 '괜찮아요'를 누른 아이 ID들(7-3 — 다시 안 띄움). */
val Context.mealHintDismissed: Flow<Set<String>> get() = deviceSettings.data.map { it[MEAL_HINT_DISMISSED].orEmpty() }

suspend fun Context.dismissMealHint(petId: String) {
    deviceSettings.edit { it[MEAL_HINT_DISMISSED] = it[MEAL_HINT_DISMISSED].orEmpty() + petId }
}
