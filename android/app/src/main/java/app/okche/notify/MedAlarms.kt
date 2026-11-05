package app.okche.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.app.Notification
import app.okche.MainActivity
import app.okche.R
import app.okche.store.OkcheDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// 탕약 알림을 휴대폰에 걸기·지우기(plan.md 8-4b). 무엇을 언제 울릴지는 MedNotify.kt(8-4a).

private const val PREFS = "med_alarms"
private const val ARMED = "armed"
private const val ACTION = "app.okche.MED_ALARM"
/** 알림을 누르면 MainActivity가 받아 그 아이의 오늘 먹일 약 시트를 띄운다. */
const val OPEN_MED_PET_ID = "openMedPetId"
private val lock = Mutex()
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val watching = java.util.concurrent.atomic.AtomicBoolean(false)

/** 같은 id면 같은 PendingIntent(요청 번호 + data로 구분 — 해시가 겹쳐도 다른 알림을 덮지 않게). */
internal fun medAlarmIntent(context: Context, id: String): Intent =
    Intent(context, MedAlarmReceiver::class.java).setAction(ACTION).setData(Uri.parse("okche://med/$id"))

/** 걸어 둔 탕약 알림을 모두 지우고 앞으로 72시간치를 다시 건다. 정확한 시각은 '알람 및 리마인더' 권한이 있을 때만(없으면 대략, 묻지 않음). */
suspend fun rescheduleMedAlarms(context: Context) = lock.withLock {
    withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val dao = OkcheDatabase.get(app).dao()
        val alerts = medAlerts(
            dao.medSchedules(), dao.medRecords(), dao.pets(), dao.restPeriods(), dao.medications(),
            dao.loadOrCreateSetting().notify.medTime, System.currentTimeMillis(),
        )
        val am = app.getSystemService(AlarmManager::class.java)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (id in prefs.getStringSet(ARMED, null).orEmpty()) {
            PendingIntent.getBroadcast(app, id.hashCode(), medAlarmIntent(app, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let {
                am.cancel(it)
                it.cancel()
            }
        }
        // 첫 알림 전에도 휴대폰 설정에 '탕약 알림'이 보이게(만들기는 몇 번 불려도 같음).
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("med", "탕약 알림", NotificationManager.IMPORTANCE_HIGH))
        // 걸기 전에 적어 둔다 — 걸다 멈춰도 다음에 지울 수 있게.
        prefs.edit().putStringSet(ARMED, alerts.mapTo(mutableSetOf()) { it.id }).commit()
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        for (a in alerts) {
            val intent = medAlarmIntent(app, a.id).putExtra("id", a.id).putExtra("title", a.title).putExtra("body", a.body).putExtra("petId", a.petId)
            val pi = PendingIntent.getBroadcast(app, a.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            // 권한은 걸던 중에도 꺼질 수 있어 막히면 대략으로.
            if (exact) {
                runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.fireAt, pi) }
                    .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.fireAt, pi) }
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.fireAt, pi)
            }
        }
    }
}

/** 앱이 살아 있는 동안 약 주기·기록·약·아이·휴양·설정이 바뀌면 0.5초 모아 다시 건다(한 번만 시작). */
@OptIn(FlowPreview::class)
fun watchMedAlarms(context: Context) {
    if (!watching.compareAndSet(false, true)) return
    val app = context.applicationContext
    appScope.launch {
        OkcheDatabase.get(app).invalidationTracker.createFlow("med_schedule", "record", "medication", "pet", "rest_period", "setting")
            .debounce(500)
            .collect { runCatching { rescheduleMedAlarms(app) } }
    }
}

/** 울릴 때: 휴대폰 알림 하나(같은 알림 id면 덮음). 누르면 앱 — 아이가 있으면 그 아이의 오늘 먹일 약 시트. */
class MedAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val id = intent.getStringExtra("id") ?: return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("med", "탕약 알림", NotificationManager.IMPORTANCE_HIGH))
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.getStringExtra("petId")?.let { open.putExtra(OPEN_MED_PET_ID, it) }
        val body = intent.getStringExtra("body")
        val notification = Notification.Builder(context, "med")
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(intent.getStringExtra("title"))
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(PendingIntent.getActivity(context, id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .build()
        nm.notify(id.hashCode(), notification)
    }
}

/** 켤 때·앱을 새로 깔았을 때·시각·시간대가 바뀔 때 휴대폰이 지운(또는 어긋난) 알림을 다시 건다. */
class MedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val pending = goAsync()
        appScope.launch {
            try {
                runCatching { rescheduleMedAlarms(context) }
            } finally {
                pending.finish()
            }
        }
    }
}
