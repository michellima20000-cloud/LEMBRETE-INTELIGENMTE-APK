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
import com.example.model.UserProfile
import com.example.speech.SpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ReminderUiState(
    val currentUser: UserProfile? = null,
    val isAuthenticated: Boolean = false,
    val isAuthLoading: Boolean = false,
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
        try {
            val fbUser = FirebaseAuth.getInstance().currentUser
            if (fbUser != null) {
                val name = fbUser.displayName?.ifBlank { null }
                    ?: fbUser.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                    ?: "Usuário"
                val profile = UserProfile(
                    uid = fbUser.uid,
                    name = name,
                    email = fbUser.email ?: "",
                    avatarLetter = name.take(1).uppercase()
                )
                repository.setCurrentUser(profile)
                _uiState.value = _uiState.value.copy(
                    currentUser = profile,
                    isAuthenticated = true
                )
                loadReminders()
            } else {
                val savedUser = repository.getCurrentUser()
                if (savedUser != null) {
                    _uiState.value = _uiState.value.copy(
                        currentUser = savedUser,
                        isAuthenticated = true
                    )
                    loadReminders()
                } else {
                    _uiState.value = _uiState.value.copy(
                        currentUser = null,
                        isAuthenticated = false
                    )
                }
            }
        } catch (e: Exception) {
            val savedUser = repository.getCurrentUser()
            if (savedUser != null) {
                _uiState.value = _uiState.value.copy(
                    currentUser = savedUser,
                    isAuthenticated = true
                )
                loadReminders()
            } else {
                _uiState.value = _uiState.value.copy(
                    currentUser = null,
                    isAuthenticated = false
                )
            }
        }

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
        val currentUser = _uiState.value.currentUser ?: return
        val parsed = parsePortugueseCommand(text)
        val reminder = Reminder(
            id = "rem_${System.currentTimeMillis()}",
            userId = currentUser.uid,
            title = parsed.cleanTitle,
            category = parsed.category,
            scheduledEpochMs = parsed.targetEpochMs,
            advanceNoticeMinutes = _uiState.value.advanceNoticeMinutes,
            status = "pending",
            notificationId = (1000..999999).random(),
            createdAt = System.currentTimeMillis()
        )

        val dayDesc = if (parsed.isTomorrow) "amanhã" else "hoje"
        val hourStr = String.format(Locale("pt", "BR"), "%02d:%02d", parsed.detectedHour, parsed.detectedMinute)
        val successMsg = "Agendado para $dayDesc às $hourStr: ${parsed.cleanTitle}"

        repository.addReminder(reminder)
        syncReminderToFirestore(reminder)
        scheduler.schedule(reminder)
        _uiState.value = _uiState.value.copy(
            inputText = "",
            isListening = false,
            speechStatusText = successMsg
        )
        loadReminders()
        showSnackbar(successMsg)

        if (_uiState.value.soundEnabled) {
            speechManager.speak("Lembrete agendado para $dayDesc às $hourStr: ${parsed.cleanTitle}")
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

        val currentUser = _uiState.value.currentUser ?: return
        val currentList = _uiState.value.reminders
        val existing = currentList.find { it.id == reminderId }
        val targetCategory = existing?.category ?: "Geral"

        val snoozeReminder = Reminder(
            id = "snooze_${System.currentTimeMillis()}",
            userId = currentUser.uid,
            title = "[Adiado 10m] $title",
            category = targetCategory,
            scheduledEpochMs = System.currentTimeMillis() + 10 * 60 * 1000L,
            advanceNoticeMinutes = 0,
            status = "postponed"
        )
        repository.addReminder(snoozeReminder)
        syncReminderToFirestore(snoozeReminder)
        scheduler.schedule(snoozeReminder)
        loadReminders()
        showSnackbar("Adiado por 10 minutos. Novo alarme agendado!")
        if (_uiState.value.soundEnabled) {
            speechManager.speak("Lembrete adiado por dez minutos.")
        }
    }

    fun registerUser(name: String, email: String, password: String = "") {
        val cleanName = name.trim().ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } }
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()

        if (cleanEmail.isBlank()) {
            showSnackbar("Por favor, informe um e-mail válido.")
            return
        }

        if (cleanPassword.isNotEmpty()) {
            if (cleanPassword.length < 6) {
                showSnackbar("A senha deve ter pelo menos 6 caracteres no Firebase.")
                return
            }
            _uiState.value = _uiState.value.copy(isAuthLoading = true)
            try {
                val auth = FirebaseAuth.getInstance()
                auth.createUserWithEmailAndPassword(cleanEmail, cleanPassword)
                    .addOnSuccessListener { result ->
                        val fbUser = result.user
                        val uid = fbUser?.uid ?: "user_${cleanEmail.hashCode().toLong() and 0xFFFFFFFFL}"

                        fbUser?.updateProfile(
                            UserProfileChangeRequest.Builder()
                                .setDisplayName(cleanName)
                                .build()
                        )

                        try {
                            val firestore = FirebaseFirestore.getInstance()
                            val doc = hashMapOf(
                                "uid" to uid,
                                "name" to cleanName,
                                "email" to cleanEmail,
                                "createdAt" to System.currentTimeMillis()
                            )
                            firestore.collection("users").document(uid).set(doc)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        val newUser = UserProfile(
                            uid = uid,
                            name = cleanName,
                            email = cleanEmail,
                            avatarLetter = cleanName.take(1).uppercase()
                        )
                        repository.setCurrentUser(newUser)
                        _uiState.value = _uiState.value.copy(
                            currentUser = newUser,
                            isAuthenticated = true,
                            isAuthLoading = false,
                            searchQuery = "",
                            statusFilter = "Pendentes",
                            categoryFilter = "Todas"
                        )
                        loadReminders()
                        showSnackbar("Conta criada com sucesso no Firebase! Bem-vindo(a), $cleanName!")
                    }
                    .addOnFailureListener { exception ->
                        _uiState.value = _uiState.value.copy(isAuthLoading = false)
                        val errorMsg = when {
                            exception.message?.contains("email address is already in use", ignoreCase = true) == true ->
                                "Este e-mail já está cadastrado no Firebase. Vá em 'Entrar'."
                            exception.message?.contains("badly formatted", ignoreCase = true) == true ->
                                "Formato de e-mail inválido."
                            exception.message?.contains("weak password", ignoreCase = true) == true ->
                                "A senha é muito fraca. Use pelo menos 6 caracteres."
                            exception.message?.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) == true ||
                            exception.message?.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) == true ->
                                "Atenção: Ative o provedor 'E-mail/Senha' no Console do Firebase (Authentication > Sign-in method)."
                            else -> "Erro no Firebase: ${exception.localizedMessage ?: "Verifique sua conexão"}"
                        }
                        showSnackbar(errorMsg)
                    }
                return
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isAuthLoading = false)
            }
        }

        // Local fallback (used if password is empty or in unit tests)
        val newUser = UserProfile(
            uid = "user_${cleanEmail.hashCode().toLong() and 0xFFFFFFFFL}",
            name = cleanName,
            email = cleanEmail,
            avatarLetter = cleanName.take(1).uppercase()
        )
        repository.setCurrentUser(newUser)
        _uiState.value = _uiState.value.copy(
            currentUser = newUser,
            isAuthenticated = true,
            isAuthLoading = false,
            searchQuery = "",
            statusFilter = "Pendentes",
            categoryFilter = "Todas"
        )
        loadReminders()
        showSnackbar("Conta criada com sucesso! Bem-vindo(a), $cleanName!")
    }

    fun login(email: String, password: String = "") {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()

        if (cleanEmail.isBlank()) {
            showSnackbar("Por favor, informe seu e-mail.")
            return
        }

        if (cleanPassword.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(isAuthLoading = true)
            try {
                val auth = FirebaseAuth.getInstance()
                auth.signInWithEmailAndPassword(cleanEmail, cleanPassword)
                    .addOnSuccessListener { result ->
                        val fbUser = result.user
                        val uid = fbUser?.uid ?: "user_${cleanEmail.hashCode().toLong() and 0xFFFFFFFFL}"
                        val name = fbUser?.displayName?.ifBlank { null }
                            ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

                        val user = UserProfile(
                            uid = uid,
                            name = name,
                            email = cleanEmail,
                            avatarLetter = name.take(1).uppercase()
                        )
                        repository.setCurrentUser(user)
                        _uiState.value = _uiState.value.copy(
                            currentUser = user,
                            isAuthenticated = true,
                            isAuthLoading = false,
                            searchQuery = "",
                            statusFilter = "Pendentes",
                            categoryFilter = "Todas"
                        )
                        loadReminders()
                        showSnackbar("Bem-vindo(a) de volta, $name!")
                    }
                    .addOnFailureListener { exception ->
                        _uiState.value = _uiState.value.copy(isAuthLoading = false)
                        val errorMsg = when {
                            exception.message?.contains("no user record", ignoreCase = true) == true ||
                            exception.message?.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) == true ->
                                "E-mail ou senha incorretos, ou usuário ainda não cadastrado."
                            exception.message?.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) == true ||
                            exception.message?.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) == true ->
                                "Atenção: Ative o provedor 'E-mail/Senha' no Console do Firebase (Authentication > Sign-in method)."
                            else -> "Erro ao entrar: ${exception.localizedMessage ?: "Verifique suas credenciais"}"
                        }
                        showSnackbar(errorMsg)
                    }
                return
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isAuthLoading = false)
            }
        }

        // Local fallback (used if password is empty or in unit tests)
        val name = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        val user = UserProfile(
            uid = "user_${cleanEmail.hashCode().toLong() and 0xFFFFFFFFL}",
            name = name,
            email = cleanEmail,
            avatarLetter = name.take(1).uppercase()
        )
        repository.setCurrentUser(user)
        _uiState.value = _uiState.value.copy(
            currentUser = user,
            isAuthenticated = true,
            isAuthLoading = false,
            searchQuery = "",
            statusFilter = "Pendentes",
            categoryFilter = "Todas"
        )
        loadReminders()
        showSnackbar("Bem-vindo(a), $name!")
    }

    fun logout() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        repository.setCurrentUser(null)
        _uiState.value = _uiState.value.copy(
            currentUser = null,
            isAuthenticated = false,
            reminders = emptyList(),
            filteredReminders = emptyList()
        )
        showSnackbar("Desconectado com sucesso")
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

    private fun syncReminderToFirestore(reminder: Reminder) {
        val user = _uiState.value.currentUser ?: return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val doc = hashMapOf(
                "id" to reminder.id,
                "userId" to reminder.userId,
                "title" to reminder.title,
                "category" to reminder.category,
                "scheduledEpochMs" to reminder.scheduledEpochMs,
                "advanceNoticeMinutes" to reminder.advanceNoticeMinutes,
                "status" to reminder.status,
                "notificationId" to reminder.notificationId,
                "createdAt" to reminder.createdAt,
                "recurrence" to reminder.recurrence
            )
            firestore.collection("users")
                .document(user.uid)
                .collection("reminders")
                .document(reminder.id)
                .set(doc)
        } catch (e: Exception) {
            // Offline or unit test fallback
        }
    }

    private fun deleteReminderFromFirestore(reminderId: String) {
        val user = _uiState.value.currentUser ?: return
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users")
                .document(user.uid)
                .collection("reminders")
                .document(reminderId)
                .delete()
        } catch (e: Exception) {
            // Offline or unit test fallback
        }
    }

    fun loadReminders() {
        val currentUser = _uiState.value.currentUser
        if (currentUser == null) {
            _uiState.value = _uiState.value.copy(reminders = emptyList(), filteredReminders = emptyList())
            return
        }
        val list = repository.getRemindersForUser(currentUser.uid)
        _uiState.value = _uiState.value.copy(reminders = list)
        applyFilters()

        // Sync from Firestore in background
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users")
                .document(currentUser.uid)
                .collection("reminders")
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && !snapshot.isEmpty) {
                        val cloudList = mutableListOf<Reminder>()
                        for (doc in snapshot.documents) {
                            try {
                                cloudList.add(
                                    Reminder(
                                        id = doc.getString("id") ?: doc.id,
                                        userId = doc.getString("userId") ?: currentUser.uid,
                                        title = doc.getString("title") ?: "",
                                        category = doc.getString("category") ?: "Trabalho",
                                        scheduledEpochMs = doc.getLong("scheduledEpochMs") ?: System.currentTimeMillis(),
                                        advanceNoticeMinutes = doc.getLong("advanceNoticeMinutes")?.toInt() ?: 0,
                                        status = doc.getString("status") ?: "pending",
                                        notificationId = doc.getLong("notificationId")?.toInt() ?: (1000..999999).random(),
                                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                        recurrence = doc.getString("recurrence") ?: "none"
                                    )
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        if (cloudList.isNotEmpty()) {
                            repository.saveRemindersForUser(currentUser.uid, cloudList)
                            _uiState.value = _uiState.value.copy(reminders = cloudList)
                            applyFilters()
                        }
                    }
                }
        } catch (e: Exception) {
            // Offline or unit test fallback
        }
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

    fun scheduleFromInput() {
        val currentUser = _uiState.value.currentUser ?: return
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

        val parsed = parsePortugueseCommand(text)
        val reminder = Reminder(
            id = "rem_${System.currentTimeMillis()}",
            userId = currentUser.uid,
            title = parsed.cleanTitle,
            category = parsed.category,
            scheduledEpochMs = parsed.targetEpochMs,
            advanceNoticeMinutes = _uiState.value.advanceNoticeMinutes,
            status = "pending",
            notificationId = (1000..999999).random(),
            createdAt = System.currentTimeMillis()
        )

        val dayDesc = if (parsed.isTomorrow) "amanhã" else "hoje"
        val hourStr = String.format(Locale("pt", "BR"), "%02d:%02d", parsed.detectedHour, parsed.detectedMinute)
        val successMsg = "Agendado para $dayDesc às $hourStr: ${parsed.cleanTitle}"

        repository.addReminder(reminder)
        syncReminderToFirestore(reminder)
        scheduler.schedule(reminder)
        _uiState.value = _uiState.value.copy(inputText = "")
        loadReminders()
        showSnackbar(successMsg)

        if (_uiState.value.soundEnabled) {
            speechManager.speak("Lembrete agendado para $dayDesc às $hourStr: ${parsed.cleanTitle}")
        }
    }

    fun testAlarmNow() {
        val currentUser = _uiState.value.currentUser ?: return
        val testItem = Reminder(
            id = "test_${System.currentTimeMillis()}",
            userId = currentUser.uid,
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
        syncReminderToFirestore(updated)
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
        syncReminderToFirestore(updated)
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
        val currentUser = _uiState.value.currentUser ?: return
        val existing = _uiState.value.reminders.find { it.id == id }
        if (existing != null) {
            scheduler.cancel(existing)
        }
        repository.deleteReminder(id, currentUser.uid)
        deleteReminderFromFirestore(id)
        loadReminders()
        showSnackbar("Lembrete excluído com sucesso")
    }

    data class ParsedCommand(
        val cleanTitle: String,
        val category: String,
        val targetEpochMs: Long,
        val isTomorrow: Boolean = false,
        val detectedHour: Int = 0,
        val detectedMinute: Int = 0
    )

    fun parsePortugueseCommandForTesting(input: String): ParsedCommand = parsePortugueseCommand(input)

    private fun parsePortugueseCommand(input: String): ParsedCommand {
        val text = input.trim()
        val lowerRaw = text.lowercase(Locale("pt", "BR"))

        // Category Classification
        var category = "Pessoal"
        if (lowerRaw.contains("remédio") || lowerRaw.contains("remedio") || lowerRaw.contains("médico") ||
            lowerRaw.contains("medico") || lowerRaw.contains("saúde") || lowerRaw.contains("saude") ||
            lowerRaw.contains("dentista") || lowerRaw.contains("consulta") || lowerRaw.contains("exame") ||
            lowerRaw.contains("alongamento") || lowerRaw.contains("treino") || lowerRaw.contains("academia") ||
            lowerRaw.contains("água") || lowerRaw.contains("agua") || lowerRaw.contains("caminhada") ||
            lowerRaw.contains("pressão") || lowerRaw.contains("insulina") || lowerRaw.contains("dormir")) {
            category = "Saúde"
        } else if (lowerRaw.contains("reunião") || lowerRaw.contains("reuniao") || lowerRaw.contains("trabalho") ||
            lowerRaw.contains("projeto") || lowerRaw.contains("cliente") || lowerRaw.contains("call") ||
            lowerRaw.contains("apresentação") || lowerRaw.contains("apresentacao") || lowerRaw.contains("relatório") ||
            lowerRaw.contains("relatorio") || lowerRaw.contains("equipe") || lowerRaw.contains("empresa") ||
            lowerRaw.contains("entrevista") || lowerRaw.contains("chefe") || lowerRaw.contains("prazo")) {
            category = "Trabalho"
        } else if (lowerRaw.contains("pagar") || lowerRaw.contains("pagamento") || lowerRaw.contains("boleto") ||
            lowerRaw.contains("conta") || lowerRaw.contains("fatura") || lowerRaw.contains("banco") ||
            lowerRaw.contains("pix") || lowerRaw.contains("dinheiro") || lowerRaw.contains("transferência") ||
            lowerRaw.contains("transferencia") || lowerRaw.contains("cartão") || lowerRaw.contains("cartao") ||
            lowerRaw.contains("aluguel") || lowerRaw.contains("salário") || lowerRaw.contains("comprar")) {
            category = "Finanças"
        } else if (lowerRaw.contains("estudar") || lowerRaw.contains("estudo") || lowerRaw.contains("estudos") ||
            lowerRaw.contains("aula") || lowerRaw.contains("prova") || lowerRaw.contains("curso") ||
            lowerRaw.contains("livro") || lowerRaw.contains("leitura") || lowerRaw.contains("faculdade") ||
            lowerRaw.contains("escola") || lowerRaw.contains("lição") || lowerRaw.contains("licao") ||
            lowerRaw.contains("inglês") || lowerRaw.contains("ingles") || lowerRaw.contains("tcc")) {
            category = "Estudos"
        }

        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        // 1. Relative duration: "daqui a X minutos / horas" or "em X minutos / horas"
        val relativeMinRegex = Pattern.compile("(?:daqui\\s*a|em)\\s*(\\d+|meia|uma|duas|três|quatro|cinco|dez|quinze|vinte|trinta)\\s*(?:minutos?|min)", Pattern.CASE_INSENSITIVE)
        val relMinMatcher = relativeMinRegex.matcher(lowerRaw)
        if (relMinMatcher.find()) {
            val minStr = relMinMatcher.group(1)?.lowercase() ?: "0"
            val mins = when (minStr) {
                "meia" -> 30
                "uma" -> 1
                "duas" -> 2
                "três", "tres" -> 3
                "quatro" -> 4
                "cinco" -> 5
                "dez" -> 10
                "quinze" -> 15
                "vinte" -> 20
                "trinta" -> 30
                else -> minStr.toIntOrNull() ?: 10
            }
            cal.add(Calendar.MINUTE, mins)
            val cleanTitle = cleanReminderTitle(text, relMinMatcher.group(0) ?: "")
            return ParsedCommand(cleanTitle, category, cal.timeInMillis, false, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }

        val relativeHourRegex = Pattern.compile("(?:daqui\\s*a|em)\\s*(\\d+|uma|duas|três|quatro)\\s*(?:horas?|h)", Pattern.CASE_INSENSITIVE)
        val relHourMatcher = relativeHourRegex.matcher(lowerRaw)
        if (relHourMatcher.find()) {
            val hourStr = relHourMatcher.group(1)?.lowercase() ?: "1"
            val hours = when (hourStr) {
                "uma" -> 1
                "duas" -> 2
                "três", "tres" -> 3
                "quatro" -> 4
                else -> hourStr.toIntOrNull() ?: 1
            }
            cal.add(Calendar.HOUR_OF_DAY, hours)
            val cleanTitle = cleanReminderTitle(text, relHourMatcher.group(0) ?: "")
            return ParsedCommand(cleanTitle, category, cal.timeInMillis, false, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }

        // 2. Date determination
        val isAmanha = lowerRaw.contains("amanhã") || lowerRaw.contains("amanha")
        val isDepoisAmanha = lowerRaw.contains("depois de amanhã") || lowerRaw.contains("depois de amanha")
        if (isDepoisAmanha) {
            cal.add(Calendar.DAY_OF_MONTH, 2)
        } else if (isAmanha) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        // 3. Normalize written words for hours and minutes
        val normalized = lowerRaw
            .replace("meio-dia", "12:00")
            .replace("meio dia", "12:00")
            .replace("meia-noite", "00:00")
            .replace("meia noite", "00:00")
            .replace("uma hora", "1 hora")
            .replace("duas horas", "2 horas")
            .replace("três horas", "3 horas")
            .replace("tres horas", "3 horas")
            .replace("quatro horas", "4 horas")
            .replace("cinco horas", "5 horas")
            .replace("seis horas", "6 horas")
            .replace("sete horas", "7 horas")
            .replace("oito horas", "8 horas")
            .replace("nove horas", "9 horas")
            .replace("dez horas", "10 horas")
            .replace("onze horas", "11 horas")
            .replace("doze horas", "12 horas")
            .replace("treze horas", "13 horas")
            .replace("quatorze horas", "14 horas")
            .replace("catorze horas", "14 horas")
            .replace("quinze horas", "15 horas")
            .replace("dezesseis horas", "16 horas")
            .replace("dezessete horas", "17 horas")
            .replace("dezoito horas", "18 horas")
            .replace("dezenove horas", "19 horas")
            .replace("vinte horas", "20 horas")
            .replace("vinte e uma horas", "21 horas")
            .replace("vinte e duas horas", "22 horas")
            .replace("vinte e três horas", "23 horas")
            .replace(Regex("(?:às|as)\\s+uma\\b"), "às 1")
            .replace(Regex("(?:às|as)\\s+duas\\b"), "às 2")
            .replace(Regex("(?:às|as)\\s+três\\b"), "às 3")
            .replace(Regex("(?:às|as)\\s+tres\\b"), "às 3")
            .replace(Regex("(?:às|as)\\s+quatro\\b"), "às 4")
            .replace(Regex("(?:às|as)\\s+cinco\\b"), "às 5")
            .replace(Regex("(?:às|as)\\s+seis\\b"), "às 6")
            .replace(Regex("(?:às|as)\\s+sete\\b"), "às 7")
            .replace(Regex("(?:às|as)\\s+oito\\b"), "às 8")
            .replace(Regex("(?:às|as)\\s+nove\\b"), "às 9")
            .replace(Regex("(?:às|as)\\s+dez\\b"), "às 10")
            .replace(Regex("(?:às|as)\\s+onze\\b"), "às 11")
            .replace(Regex("(?:às|as)\\s+doze\\b"), "às 12")
            .replace(Regex("e\\s+meia\\b"), "e 30")
            .replace(Regex("e\\s+quinze\\b"), "e 15")
            .replace(Regex("e\\s+quarenta\\s+e\\s+cinco\\b"), "e 45")
            .replace(Regex("e\\s+vinte\\b"), "e 20")
            .replace(Regex("e\\s+dez\\b"), "e 10")

        // 4. Period modifiers (AM/PM)
        val isTarde = normalized.contains("tarde")
        val isNoite = normalized.contains("noite")
        val isManha = normalized.contains("manhã") || normalized.contains("manha") || normalized.contains("madrugada")

        // 5. Match Time Patterns
        // Pattern 1: HH:MM or HHhMM (e.g. 14:30, 14h30, 9:15, 09h00)
        val digitalPattern = Pattern.compile("\\b(\\d{1,2})[:hH](\\d{2})\\b")
        // Pattern 2: HH horas e MM (e.g. 14 horas e 30 minutos, 10 horas e 15, 8 horas)
        val horasPattern = Pattern.compile("\\b(\\d{1,2})\\s*(?:horas?|hrs?|hs?|h)\\s*(?:(?:e|com)\\s*(\\d{1,2})(?:\\s*minutos?)?)?\\b")
        // Pattern 3: às HH e MM / às HH (e.g. às 14 e 30, às 10, às 9)
        val asPattern = Pattern.compile("(?:às|as|para\\s+(?:às|as)?)\\s*(\\d{1,2})(?:\\s*e\\s*(\\d{1,2}))?\\b")

        var matchedHour: Int? = null
        var matchedMinute = 0
        var matchedPhrase = ""

        val m1 = digitalPattern.matcher(normalized)
        if (m1.find()) {
            matchedHour = m1.group(1)?.toIntOrNull()
            matchedMinute = m1.group(2)?.toIntOrNull() ?: 0
            matchedPhrase = m1.group(0) ?: ""
        } else {
            val m2 = horasPattern.matcher(normalized)
            if (m2.find()) {
                matchedHour = m2.group(1)?.toIntOrNull()
                matchedMinute = m2.group(2)?.toIntOrNull() ?: 0
                matchedPhrase = m2.group(0) ?: ""
            } else {
                val m3 = asPattern.matcher(normalized)
                if (m3.find()) {
                    matchedHour = m3.group(1)?.toIntOrNull()
                    matchedMinute = m3.group(2)?.toIntOrNull() ?: 0
                    matchedPhrase = m3.group(0) ?: ""
                }
            }
        }

        if (matchedHour != null) {
            var hour = matchedHour.coerceIn(0, 23)
            val minute = matchedMinute.coerceIn(0, 59)

            // Adjust AM/PM if stated or implied
            if ((isTarde || isNoite) && hour in 1..11) {
                hour += 12
            } else if (isManha && hour == 12) {
                hour = 0
            } else if (!isTarde && !isNoite && !isManha && !isAmanha && !isDepoisAmanha) {
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                if (hour in 1..11 && hour <= currentHour && (hour + 12) > currentHour) {
                    hour += 12
                }
            }

            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            if (cal.timeInMillis <= now && !isAmanha && !isDepoisAmanha) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        } else {
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }

        val cleanTitle = cleanReminderTitle(text, matchedPhrase)

        return ParsedCommand(
            cleanTitle = cleanTitle,
            category = category,
            targetEpochMs = cal.timeInMillis,
            isTomorrow = isAmanha || (cal.get(Calendar.DAY_OF_YEAR) != Calendar.getInstance().get(Calendar.DAY_OF_YEAR)),
            detectedHour = cal.get(Calendar.HOUR_OF_DAY),
            detectedMinute = cal.get(Calendar.MINUTE)
        )
    }

    private fun cleanReminderTitle(input: String, matchedTimePhrase: String): String {
        var clean = input.trim()

        val prefixes = listOf(
            Regex("^me\\s+lembre\\s+de\\s+", RegexOption.IGNORE_CASE),
            Regex("^me\\s+lembra\\s+de\\s+", RegexOption.IGNORE_CASE),
            Regex("^me\\s+lembre\\s+para\\s+", RegexOption.IGNORE_CASE),
            Regex("^me\\s+lembre\\s+que\\s+", RegexOption.IGNORE_CASE),
            Regex("^me\\s+lembre\\s+", RegexOption.IGNORE_CASE),
            Regex("^me\\s+lembra\\s+", RegexOption.IGNORE_CASE),
            Regex("^lembrete\\s+de\\s+", RegexOption.IGNORE_CASE),
            Regex("^lembrete\\s+para\\s+", RegexOption.IGNORE_CASE),
            Regex("^lembrete\\s+", RegexOption.IGNORE_CASE),
            Regex("^lembrar\\s+de\\s+", RegexOption.IGNORE_CASE),
            Regex("^lembrar\\s+", RegexOption.IGNORE_CASE),
            Regex("^agendar\\s+", RegexOption.IGNORE_CASE),
            Regex("^marcar\\s+", RegexOption.IGNORE_CASE),
            Regex("^criar\\s+lembrete\\s+de\\s+", RegexOption.IGNORE_CASE),
            Regex("^criar\\s+lembrete\\s+para\\s+", RegexOption.IGNORE_CASE),
            Regex("^criar\\s+lembrete\\s+", RegexOption.IGNORE_CASE),
            Regex("^alarme\\s+para\\s+", RegexOption.IGNORE_CASE),
            Regex("^alarme\\s+de\\s+", RegexOption.IGNORE_CASE),
            Regex("^alarme\\s+", RegexOption.IGNORE_CASE),
            Regex("^avise-me\\s+de\\s+", RegexOption.IGNORE_CASE),
            Regex("^avise\\s+me\\s+de\\s+", RegexOption.IGNORE_CASE)
        )

        for (p in prefixes) {
            clean = clean.replace(p, "").trim()
        }

        clean = clean.replace(Regex("\\bdepois\\s+de\\s+amanh[ãa]\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bamanh[ãa]\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bhoje\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bda\\s+manh[ãa]\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bde\\s+manh[ãa]\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bda\\s+tarde\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bda\\s+noite\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bda\\s+madrugada\\b", RegexOption.IGNORE_CASE), "")

        if (matchedTimePhrase.isNotBlank()) {
            clean = clean.replace(matchedTimePhrase, "", ignoreCase = true)
        }

        clean = clean
            .replace(Regex("\\s+às\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+as\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+para\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+para\\s+às\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^\\s*para\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^\\s*de\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        return if (clean.isNotBlank()) {
            clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }
        } else {
            input.trim()
        }
    }
}
