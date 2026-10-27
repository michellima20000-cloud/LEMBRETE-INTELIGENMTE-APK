package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.Reminder
import com.example.model.USER_MAYA
import com.example.model.USER_MICHEL
import com.example.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class ReminderRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lembrete_inteligente_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var instance: ReminderRepository? = null

        fun getInstance(context: Context): ReminderRepository {
            return instance ?: synchronized(this) {
                instance ?: ReminderRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getCurrentUser(): UserProfile {
        val uid = prefs.getString("current_user_uid", USER_MICHEL.uid) ?: USER_MICHEL.uid
        return if (uid == USER_MAYA.uid) USER_MAYA else USER_MICHEL
    }

    fun setCurrentUser(user: UserProfile) {
        prefs.edit().putString("current_user_uid", user.uid).apply()
    }

    fun getRemindersForUser(userId: String): List<Reminder> {
        val raw = prefs.getString("reminders_$userId", null)
        if (raw.isNullOrEmpty()) {
            val initial = getInitialRemindersFor(userId)
            saveRemindersForUser(userId, initial)
            return initial
        }
        val list = mutableListOf<Reminder>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Reminder(
                        id = obj.getString("id"),
                        userId = obj.getString("userId"),
                        title = obj.getString("title"),
                        category = obj.getString("category"),
                        scheduledEpochMs = obj.getLong("scheduledEpochMs"),
                        advanceNoticeMinutes = obj.optInt("advanceNoticeMinutes", 0),
                        status = obj.optString("status", "pending"),
                        notificationId = obj.optInt("notificationId", (1000..999999).random()),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        recurrence = obj.optString("recurrence", "none")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedBy { it.scheduledEpochMs }
    }

    fun saveRemindersForUser(userId: String, reminders: List<Reminder>) {
        val arr = JSONArray()
        for (r in reminders) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("userId", r.userId)
                put("title", r.title)
                put("category", r.category)
                put("scheduledEpochMs", r.scheduledEpochMs)
                put("advanceNoticeMinutes", r.advanceNoticeMinutes)
                put("status", r.status)
                put("notificationId", r.notificationId)
                put("createdAt", r.createdAt)
                put("recurrence", r.recurrence)
            }
            arr.put(obj)
        }
        prefs.edit().putString("reminders_$userId", arr.toString()).apply()
    }

    fun addReminder(reminder: Reminder) {
        val current = getRemindersForUser(reminder.userId).toMutableList()
        current.add(0, reminder)
        saveRemindersForUser(reminder.userId, current)
    }

    fun updateReminder(reminder: Reminder) {
        val current = getRemindersForUser(reminder.userId).toMutableList()
        val index = current.indexOfFirst { it.id == reminder.id }
        if (index != -1) {
            current[index] = reminder
            saveRemindersForUser(reminder.userId, current)
        }
    }

    fun deleteReminder(id: String, userId: String) {
        val current = getRemindersForUser(userId).toMutableList()
        current.removeAll { it.id == id }
        saveRemindersForUser(userId, current)
    }

    fun getAllPendingReminders(): List<Reminder> {
        val users = listOf(USER_MICHEL.uid, USER_MAYA.uid)
        val all = mutableListOf<Reminder>()
        for (uid in users) {
            all.addAll(getRemindersForUser(uid).filter { it.status == "pending" })
        }
        return all
    }

    private fun getInitialRemindersFor(userId: String): List<Reminder> {
        val now = Calendar.getInstance()
        val list = mutableListOf<Reminder>()

        if (userId == USER_MICHEL.uid) {
            val cal1 = now.clone() as Calendar
            cal1.add(Calendar.HOUR_OF_DAY, 2)
            list.add(
                Reminder(
                    id = "m1",
                    userId = userId,
                    title = "Tomar remédio controlado",
                    category = "Saúde",
                    scheduledEpochMs = cal1.timeInMillis,
                    advanceNoticeMinutes = 5,
                    status = "pending",
                    notificationId = 101
                )
            )

            val cal2 = now.clone() as Calendar
            cal2.add(Calendar.DAY_OF_MONTH, 1)
            cal2.set(Calendar.HOUR_OF_DAY, 14)
            cal2.set(Calendar.MINUTE, 0)
            list.add(
                Reminder(
                    id = "m2",
                    userId = userId,
                    title = "Pagar fatura do cartão de crédito",
                    category = "Finanças",
                    scheduledEpochMs = cal2.timeInMillis,
                    advanceNoticeMinutes = 15,
                    status = "pending",
                    notificationId = 102
                )
            )

            val cal3 = now.clone() as Calendar
            cal3.add(Calendar.DAY_OF_MONTH, 2)
            cal3.set(Calendar.HOUR_OF_DAY, 9)
            cal3.set(Calendar.MINUTE, 30)
            list.add(
                Reminder(
                    id = "m3",
                    userId = userId,
                    title = "Reunião de alinhamento com equipe de TI",
                    category = "Trabalho",
                    scheduledEpochMs = cal3.timeInMillis,
                    advanceNoticeMinutes = 10,
                    status = "pending",
                    notificationId = 103
                )
            )
        } else {
            val cal1 = now.clone() as Calendar
            cal1.add(Calendar.HOUR_OF_DAY, 1)
            list.add(
                Reminder(
                    id = "y1",
                    userId = userId,
                    title = "Estudar módulo de Arquitetura Android",
                    category = "Estudos",
                    scheduledEpochMs = cal1.timeInMillis,
                    advanceNoticeMinutes = 5,
                    status = "pending",
                    notificationId = 201
                )
            )
            val cal2 = now.clone() as Calendar
            cal2.add(Calendar.HOUR_OF_DAY, 4)
            list.add(
                Reminder(
                    id = "y2",
                    userId = userId,
                    title = "Alongamento e pausa ativa",
                    category = "Saúde",
                    scheduledEpochMs = cal2.timeInMillis,
                    advanceNoticeMinutes = 0,
                    status = "pending",
                    notificationId = 202
                )
            )
        }
        return list
    }
}
