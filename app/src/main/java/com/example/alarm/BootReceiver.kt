package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.ReminderRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device rebooted, rescheduling active reminders...")
            val repository = ReminderRepository.getInstance(context)
            val scheduler = AlarmScheduler(context)
            val activeReminders = repository.getAllPendingReminders()
            for (reminder in activeReminders) {
                scheduler.schedule(reminder)
            }
        }
    }
}
