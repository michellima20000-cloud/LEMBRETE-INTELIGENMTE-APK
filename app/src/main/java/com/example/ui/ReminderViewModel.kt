package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmReceiver
import com.example.alarm.AlarmScheduler
import com.example.alarm.AlarmSoundPlayer
import com.example.data.ReminderRepository
import com.example.model.Reminder
import com.example.model.USER_MAYA
import com.example.model.USER_MICHEL
import com.example.model.UserProfile
import com.example.speech.SpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ReminderUiState(
    val currentUser: UserProfile = USER_MICHEL,
    val isAuthenticated: Boolean = true,
    val reminders: List<Reminder> = emptyList(),
    val filteredReminders: List<Reminder> = emptyList(),
    val statusFilter: String = "Pendentes", // "Pendentes", "Adiados", "Concluídos", "Todos"
    val categoryFilter: String = "Todas", // "Todas", "Trabalho", "Saúde", "Finanças", "Pessoal", "Estudos"
    val searchQuery: String = "",
    val advanceNoticeMinutes: Int = 0,
    val inputText: String = "",
    val isRinging: Boolean = false,
    val activeAlarmTitle: String? = null,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val isListening: Boolean = false,
    val soundLevel: Float = 0f,
    val speechStatusText: String = "",
    val snackbarMessage: String? = null
)

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReminderRepository.getInstance(application)
    private val scheduler = AlarmScheduler(application)
    private val speechManager = SpeechManager(application)

    private val _uiState = MutableStateFlow(ReminderUiState())
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshAlarmStatus()
        }
    }

    init {
        val user = repository.getCurrentUser()
        _uiState.value = _uiState.value.copy(currentUser = user)
        loadReminders()

        try {
            val filter = IntentFilter("com.lembreteinteligente.ALARM_STATUS_CHANGED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.registerReceiver(alarmReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                application.registerReceiver(alarmReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        refreshAlarmStatus()

        // Observe native speech listening and sound level
        viewModelScope.launch {
            speechManager.isListening.collect { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        }
        viewModelScope.launch {
            speechManager.soundLevel.collect { level ->
                _uiState.value = _uiState.value.copy(soundLevel = level)
            }
        }
        viewModelScope.launch {
            speechManager.partialText.collect { partial ->
                if (partial.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(
                        inputText = partial,
                        speechStatusText = "Reconhecendo voz nativa..."
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(alarmReceiver)
        } catch (_: Exception) {}
        speechManager.destroy()
    }

    fun toggleNativeVoiceRecording() {
        if (_uiState.value.isListening) {
            speechManager.stopListening()
            _uiState.value = _uiState.value.copy(
                isListening = false,
                speechStatusText = ""
            )
        } else {
            _uiState.value = _uiState.value.copy(
                speechStatusText = "Microfone do sistema ativo. Pode falar..."
            )
            speechManager.startListening { finalTranscript ->
                if (finalTranscript.isNotBlank()) {
                    scheduleDirectlyFromTranscript(finalTranscript)
                }
            }
        }
    }

    fun scheduleDirectlyFromTranscript(text: String) {
        val parsed = parsePortugueseCommand(text)
        val reminder = Reminder(
            id = "rem_${System.currentTimeMillis()}",
            userId = _uiState.value.currentUser.uid,
            title = parsed.cleanTitle,
            category = parsed.category,
            scheduledEpochMs = parsed.targetEpochMs,
            advanceNoticeMinutes = _uiState.value.advanceNoticeMinutes,
            status = "pending",
            notificationId = (1000..999999).random(),
            createdAt = System.currentTimeMillis()
        )

        repository.addReminder(reminder)
        scheduler.schedule(reminder)
        _uiState.value = _uiState.value.copy(
            inputText = "",
            isListening = false,
            speechStatusText = "Agendado automaticamente por voz!"
        )
        loadReminders()
        showSnackbar("Lembrete agendado automaticamente por voz!")

        if (_uiState.value.soundEnabled) {
            val sdf = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
            val formattedTime = sdf.format(Date(parsed.targetEpochMs))
            speechManager.speak("Agendado automaticamente para às $formattedTime: ${parsed.cleanTitle}")
        }
    }

    fun speakReminder(reminder: Reminder) {
        val sdf = SimpleDateFormat("dd 'de' MMMM 'às' HH 'horas e' mm 'minutos'", Locale("pt", "BR"))
        val formattedDate = sdf.format(Date(reminder.scheduledEpochMs))
        val speech = "Lembrete: ${reminder.title}. Categoria ${reminder.category}, programado para $formattedDate."
        speechManager.speak(speech)
        showSnackbar("Reproduzindo lembrete em áudio...")
    }

    fun refreshAlarmStatus() {
        val isRinging = AlarmReceiver.activeAlarmReminderTitle != null
        _uiState.value = _uiState.value.copy(
            isRinging = isRinging,
            activeAlarmTitle = AlarmReceiver.activeAlarmReminderTitle
        )
        if (isRinging && _uiState.value.soundEnabled) {
            val title = AlarmReceiver.activeAlarmReminderTitle ?: "Lembrete"
            speechManager.speak("Atenção! Hora do seu lembrete: $title")
        }
    }

    fun dismissActiveAlarm() {
        AlarmSoundPlayer.stopAlarmSound(getApplication())
        AlarmReceiver.activeAlarmReminderTitle = null
        AlarmReceiver.activeAlarmReminderId = null
        _uiState.value = _uiState.value.copy(isRinging = false, activeAlarmTitle = null)
        showSnackbar("Alarme desativado com sucesso!")
        if (_uiState.value.soundEnabled) {
            speechManager.speak("Alarme desativado.")
        }
    }

    fun snoozeActiveAlarm() {
        val title = AlarmReceiver.activeAlarmReminderTitle ?: "Compromisso"
        val reminderId = AlarmReceiver.activeAlarmReminderId ?: "snooze"
        dismissActiveAlarm()

        val currentList = _uiState.value.reminders
        val existing = currentList.find { it.id == reminderId }
        val targetCategory = existing?.category ?: "Geral"

        val snoozeReminder = Reminder(
            id = "snooze_${System.currentTimeMillis()}",
            userId = _uiState.value.currentUser.uid,
            title = "[Adiado 10m] $title",
            category = targetCategory,
            scheduledEpochMs = System.currentTimeMillis() + 10 * 60 * 1000L,
            advanceNoticeMinutes = 0,
            status = "postponed"
        )
        repository.addReminder(snoozeReminder)
        scheduler.schedule(snoozeReminder)
        loadReminders()
        showSnackbar("Adiado por 10 minutos. Novo alarme agendado!")
        if (_uiState.value.soundEnabled) {
            speechManager.speak("Lembrete adiado por dez minutos.")
        }
    }

    fun switchUser(user: UserProfile) {
        repository.setCurrentUser(user)
        _uiState.value = _uiState.value.copy(
            currentUser = user,
            isAuthenticated = true,
            searchQuery = "",
            statusFilter = "Pendentes",
            categoryFilter = "Todas"
        )
        loadReminders()
        showSnackbar("Conectado como ${user.name} (${user.email})")
    }

    fun logout() {
        _uiState.value = _uiState.value.copy(isAuthenticated = false)
    }

    fun login(email: String) {
        val target = if (email.contains("maya", ignoreCase = true)) USER_MAYA else USER_MICHEL
        switchUser(target)
    }

    fun setInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun setAdvanceNotice(minutes: Int) {
        _uiState.value = _uiState.value.copy(advanceNoticeMinutes = minutes)
    }

    fun setStatusFilter(status: String) {
        _uiState.value = _uiState.value.copy(statusFilter = status)
        applyFilters()
    }

    fun setCategoryFilter(category: String) {
        _uiState.value = _uiState.value.copy(categoryFilter = category)
        applyFilters()
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun toggleSound() {
        val next = !_uiState.value.soundEnabled
        _uiState.value = _uiState.value.copy(soundEnabled = next)
        showSnackbar(if (next) "Som ativado" else "Som silenciado")
    }

    fun toggleVibration() {
        val next = !_uiState.value.vibrationEnabled
        _uiState.value = _uiState.value.copy(vibrationEnabled = next)
        showSnackbar(if (next) "Vibração ativada" else "Vibração desativada")
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    private fun showSnackbar(msg: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = msg)
    }

    fun loadReminders() {
        val list = repository.getRemindersForUser(_uiState.value.currentUser.uid)
        _uiState.value = _uiState.value.copy(reminders = list)
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        val filtered = state.reminders.filter { r ->
            val matchesStatus = when (state.statusFilter) {
                "Pendentes" -> r.status == "pending"
                "Adiados" -> r.status == "postponed"
                "Concluídos" -> r.status == "completed"
                else -> true
            }
            val matchesCategory = if (state.categoryFilter == "Todas") true else r.category == state.categoryFilter
            val matchesSearch = if (state.searchQuery.isBlank()) true else {
                r.title.contains(state.searchQuery, ignoreCase = true) ||
                        r.category.contains(state.searchQuery, ignoreCase = true)
            }
            matchesStatus && matchesCategory && matchesSearch
        }
        _uiState.value = _uiState.value.copy(filteredReminders = filtered)
    }

    /**
     * Parses natural language command into structured Reminder, schedules it in native AlarmManager,
     * and speaks spoken confirmation back to the user ("tanto escrito como falado").
     */
    fun scheduleFromInput() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

        val parsed = parsePortugueseCommand(text)
        val reminder = Reminder(
            id = "rem_${System.currentTimeMillis()}",
            userId = _uiState.value.currentUser.uid,
            title = parsed.cleanTitle,
            category = parsed.category,
            scheduledEpochMs = parsed.targetEpochMs,
            advanceNoticeMinutes = _uiState.value.advanceNoticeMinutes,
            status = "pending",
            notificationId = (1000..999999).random(),
            createdAt = System.currentTimeMillis()
        )

        repository.addReminder(reminder)
        scheduler.schedule(reminder)
        _uiState.value = _uiState.value.copy(inputText = "")
        loadReminders()
        showSnackbar("Compromisso agendado no AlarmManager nativo com som e vibração!")

        // Spoken confirmation to fulfill "tanto escrito como falado"
        if (_uiState.value.soundEnabled) {
            val sdf = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
            val formattedTime = sdf.format(Date(parsed.targetEpochMs))
            speechManager.speak("Lembrete agendado para às $formattedTime: ${parsed.cleanTitle}")
        }
    }

    fun testAlarmNow() {
        val testItem = Reminder(
            id = "test_${System.currentTimeMillis()}",
            userId = _uiState.value.currentUser.uid,
            title = "Teste de Alarme de Alta Prioridade",
            category = "Saúde",
            scheduledEpochMs = System.currentTimeMillis() + 3000L,
            advanceNoticeMinutes = 0,
            status = "pending",
            notificationId = 99999
        )
        scheduler.schedule(testItem)
        showSnackbar("Alarme de teste disparará em 3 segundos no AlarmManager!")
        if (_uiState.value.soundEnabled) {
            speechManager.speak("Alarme de teste acionado. Disparando em três segundos.")
        }
    }

    fun postponeReminder(id: String, additionalMinutes: Int = 10) {
        val existing = _uiState.value.reminders.find { it.id == id } ?: return
        val newEpoch = System.currentTimeMillis() + additionalMinutes * 60 * 1000L
        val updated = existing.copy(
            scheduledEpochMs = newEpoch,
            status = "postponed"
        )
        repository.updateReminder(updated)
        scheduler.schedule(updated)
        loadReminders()
        showSnackbar("Compromisso adiado em $additionalMinutes minutos!")
        if (_uiState.value.soundEnabled) {
            speechManager.speak("Compromisso adiado por dez minutos.")
        }
    }

    fun toggleComplete(id: String) {
        val existing = _uiState.value.reminders.find { it.id == id } ?: return
        val newStatus = if (existing.status == "completed") "pending" else "completed"
        val updated = existing.copy(status = newStatus)
        repository.updateReminder(updated)
        if (newStatus == "completed") {
            scheduler.cancel(existing)
            showSnackbar("Lembrete concluído!")
            if (_uiState.value.soundEnabled) {
                speechManager.speak("Lembrete marcado como concluído.")
            }
        } else {
            scheduler.schedule(updated)
            showSnackbar("Lembrete reativado como pendente!")
        }
        loadReminders()
    }

    fun deleteReminder(id: String) {
        val existing = _uiState.value.reminders.find { it.id == id }
        if (existing != null) {
            scheduler.cancel(existing)
        }
        repository.deleteReminder(id, _uiState.value.currentUser.uid)
        loadReminders()
        showSnackbar("Lembrete excluído com sucesso")
    }

    data class ParsedCommand(
        val cleanTitle: String,
        val category: String,
        val targetEpochMs: Long
    )

    private fun parsePortugueseCommand(input: String): ParsedCommand {
        val lower = input.lowercase()
        var category = "Pessoal"

        if (lower.contains("remédio") || lower.contains("remedio") || lower.contains("médico") ||
            lower.contains("saúde") || lower.contains("saude") || lower.contains("alongamento") ||
            lower.contains("treino") || lower.contains("água") || lower.contains("agua")) {
            category = "Saúde"
        } else if (lower.contains("reunião") || lower.contains("reuniao") || lower.contains("trabalho") ||
            lower.contains("projeto") || lower.contains("cliente") || lower.contains("call") ||
            lower.contains("apresentação") || lower.contains("relatório")) {
            category = "Trabalho"
        } else if (lower.contains("pagar") || lower.contains("boleto") || lower.contains("conta") ||
            lower.contains("fatura") || lower.contains("banco") || lower.contains("pix") ||
            lower.contains("dinheiro") || lower.contains("transferência")) {
            category = "Finanças"
        } else if (lower.contains("estudar") || lower.contains("estudo") || lower.contains("aula") ||
            lower.contains("prova") || lower.contains("curso") || lower.contains("livro") ||
            lower.contains("leitura")) {
            category = "Estudos"
        }

        val cal = Calendar.getInstance()
        val isAmanha = lower.contains("amanhã") || lower.contains("amanha")
        if (isAmanha) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        val timePattern = Pattern.compile("(?:às|as)?\\s*(\\d{1,2})(?:h|:(\\d{2}))")
        val matcher = timePattern.matcher(lower)

        if (matcher.find()) {
            val hour = matcher.group(1)?.toIntOrNull() ?: cal.get(Calendar.HOUR_OF_DAY)
            val minute = matcher.group(2)?.toIntOrNull() ?: 0
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            if (cal.timeInMillis <= System.currentTimeMillis() && !isAmanha) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        } else {
            cal.add(Calendar.HOUR_OF_DAY, 1)
        }

        val cleanTitle = input.trim()

        return ParsedCommand(
            cleanTitle = cleanTitle,
            category = category,
            targetEpochMs = cal.timeInMillis
        )
    }
}
