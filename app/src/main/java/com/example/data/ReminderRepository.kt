package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.Reminder
import com.example.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

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

    fun getCurrentUser(): UserProfile? {
        val uid = prefs.getString("current_user_uid", null) ?: return null
        val name = prefs.getString("current_user_name", "Usuário") ?: "Usuário"
        val email = prefs.getString("current_user_email", "") ?: ""
        return UserProfile(uid = uid, name = name, email = email)
    }

    fun setCurrentUser(user: UserProfile?) {
        val editor = prefs.edit()
        if (user != null) {
            editor.putString("current_user_uid", user.uid)
            editor.putString("current_user_name", user.name)
            editor.putString("current_user_email", user.email)
        } else {
            editor.remove("current_user_uid")
            editor.remove("current_user_name")
            editor.remove("current_user_email")
        }
        editor.apply()
    }

    fun getRemindersForUser(userId: String): List<Reminder> {
        val raw = prefs.getString("reminders_$userId", null)
        if (raw.isNullOrEmpty()) {
            return emptyList()
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
        val currentUser = getCurrentUser() ?: return emptyList()
        return getRemindersForUser(currentUser.uid).filter { it.status == "pending" }
    }
}
