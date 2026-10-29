package com.example.model

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val avatarLetter: String = name.take(1).uppercase()
)


data class Reminder(
    val id: String,
    val userId: String,
    val title: String,
    val category: String, // "Trabalho", "Saúde", "Finanças", "Pessoal", "Estudos"
    val scheduledEpochMs: Long,
    val advanceNoticeMinutes: Int = 0, // 0, 5, 10, 15, 30
    val status: String = "pending", // "pending", "postponed", "completed"
    val notificationId: Int = (1000..999999).random(),
    val createdAt: Long = System.currentTimeMillis(),
    val recurrence: String = "none" // "none", "daily", "weekdays", "weekly"
)
