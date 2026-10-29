package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.lembreteinteligente.R

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "alarms_high_priority"
        const val ACTION_TRIGGER_ALARM = "com.lembreteinteligente.ACTION_TRIGGER_ALARM"
        const val ACTION_DISMISS_ALARM = "com.lembreteinteligente.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.lembreteinteligente.ACTION_SNOOZE_ALARM"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_USER_ID = "extra_user_id"

        var activeAlarmReminderTitle: String? = null
        var activeAlarmReminderId: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: ACTION_TRIGGER_ALARM
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1001)
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Lembrete Inteligente"
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "Geral"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (action) {
            ACTION_DISMISS_ALARM -> {
                Log.d("AlarmReceiver", "Dismissing alarm $notificationId")
                AlarmSoundPlayer.stopAlarmSound(context)
                notificationManager.cancel(notificationId)
                activeAlarmReminderTitle = null
                activeAlarmReminderId = null
                // Broadcast dismissal to update UI
                val updateIntent = Intent("com.lembreteinteligente.ALARM_STATUS_CHANGED")
                context.sendBroadcast(updateIntent)
            }

            ACTION_SNOOZE_ALARM -> {
                Log.d("AlarmReceiver", "Snoozing alarm $notificationId")
                AlarmSoundPlayer.stopAlarmSound(context)
                notificationManager.cancel(notificationId)
                activeAlarmReminderTitle = null
                activeAlarmReminderId = null

                // Schedule snooze for +10 minutes
                val scheduler = AlarmScheduler(context)
                val snoozeItem = com.example.model.Reminder(
                    id = reminderId.ifEmpty { "snooze_${System.currentTimeMillis()}" },
                    userId = intent.getStringExtra(EXTRA_USER_ID) ?: "",
                    title = "[Adiado 10m] $title",
                    category = category,
                    scheduledEpochMs = System.currentTimeMillis() + 10 * 60 * 1000L,
                    notificationId = notificationId,
                    status = "postponed"
                )
                scheduler.schedule(snoozeItem)

                val updateIntent = Intent("com.lembreteinteligente.ALARM_STATUS_CHANGED")
                context.sendBroadcast(updateIntent)
            }

            ACTION_TRIGGER_ALARM -> {
                Log.d("AlarmReceiver", "Triggering high-priority alarm $notificationId: $title")

                // Acquire WakeLock for 30s to guarantee screen wakeup and execution in deep sleep
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "LembreteInteligente:AlarmWakeLock"
                )
                wakeLock.acquire(30_000L)

                activeAlarmReminderTitle = title
                activeAlarmReminderId = reminderId

                createNotificationChannel(context, notificationManager)

                // Intent to open app
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("ALARM_TRIGGERED", true)
                    putExtra(EXTRA_TITLE, title)
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Dismiss action intent
                val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
                    this.action = ACTION_DISMISS_ALARM
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
                val dismissPendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId + 1,
                    dismissIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Snooze action intent
                val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
                    this.action = ACTION_SNOOZE_ALARM
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(EXTRA_REMINDER_ID, reminderId)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_CATEGORY, category)
                }
                val snoozePendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId + 2,
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("⏰ $title")
                    .setContentText("Categoria: $category • Alarme em execução")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(openPendingIntent)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setColor(0xFF4F46E5.toInt())
                    .addAction(0, "Parar Alarme", dismissPendingIntent)
                    .addAction(0, "Adiar (+10m)", snoozePendingIntent)
                    .build()

                notificationManager.notify(notificationId, notification)

                // Start continuous audio siren & vibration
                AlarmSoundPlayer.startAlarmSound(context)

                // Notify UI
                val updateIntent = Intent("com.lembreteinteligente.ALARM_STATUS_CHANGED")
                context.sendBroadcast(updateIntent)
            }
        }
    }

    private fun createNotificationChannel(context: Context, notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val soundUri = Uri.parse("android.resource://" + context.packageName + "/" + R.raw.alarm_loud)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarmes e Lembretes Críticos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações prioritárias do Lembrete Inteligente com som contínuo e vibração na tela de bloqueio"
                enableLights(true)
                lightColor = Color.parseColor("#4F46E5")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 800)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
