package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Reminder
import com.example.model.USER_MAYA
import com.example.model.USER_MICHEL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ReminderViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showUserSwitchDialog by remember { mutableStateOf(false) }
    var showBackgroundInfoDialog by remember { mutableStateOf(false) }

    // Native RECORD_AUDIO permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleNativeVoiceRecording()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF090D1C),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF6366F1), Color(0xFF06B6D4))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Row {
                            Text(
                                text = "Lembrete ",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Inteligente",
                                color = Color(0xFF818CF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                },
                actions = {
                    // APK Nativo badge
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF4F46E5),
                        modifier = Modifier
                            .height(28.dp)
                            .clickable { showBackgroundInfoDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "APK Nativo",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Ativar 2º Plano badge
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E284A),
                        modifier = Modifier
                            .height(28.dp)
                            .clickable { showBackgroundInfoDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "2º Plano",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Sound Toggle Icon
                    IconButton(
                        onClick = { viewModel.toggleSound() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (state.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Alternar som",
                            tint = if (state.soundEnabled) Color(0xFF818CF8) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Haptics Toggle Icon
                    IconButton(
                        onClick = { viewModel.toggleVibration() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (state.vibrationEnabled) Icons.Default.Vibration else Icons.Default.PhoneAndroid,
                            contentDescription = "Alternar vibração",
                            tint = if (state.vibrationEnabled) Color(0xFF06B6D4) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // User Badge (M Michel / M Maya)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1B2344),
                        modifier = Modifier
                            .height(30.dp)
                            .clickable { showUserSwitchDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4F46E5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = state.currentUser.avatarLetter,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = state.currentUser.name,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Logout Icon
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sair",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF090D1C)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Active Alarm Ringing Alert Banner
            AnimatedVisibility(
                visible = state.isRinging,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1219)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "ALARME ATIVO DISPARANDO!",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = state.activeAlarmTitle ?: "Lembrete",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { viewModel.snoozeActiveAlarm() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("+10m", fontSize = 11.sp, color = Color.White)
                            }
                            Button(
                                onClick = { viewModel.dismissActiveAlarm() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Parar", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Command input card with native microphone recognition and live sound visualizer
                item {
                    CommandInputCard(
                        inputText = state.inputText,
                        onInputChanged = { viewModel.setInputText(it) },
                        selectedAdvanceNotice = state.advanceNoticeMinutes,
                        onAdvanceNoticeChanged = { viewModel.setAdvanceNotice(it) },
                        onSchedule = { viewModel.scheduleFromInput() },
                        isListening = state.isListening,
                        soundLevel = state.soundLevel,
                        speechStatusText = state.speechStatusText,
                        onStartMic = {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onExampleSelected = { exampleText ->
                            viewModel.setInputText(exampleText)
                        }
                    )
                }

                // Instant Native Alarm Test Trigger
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.testAlarmNow() },
                        color = Color(0xFF161E3D),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3A68))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Testar Disparo no AlarmManager (3s)",
                                    color = Color(0xFFF8FAFC),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Disparar",
                                color = Color(0xFF818CF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Cronograma & Agenda Section Header
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Cronograma & Agenda",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1E2648)
                                ) {
                                    Text(
                                        text = "Ordem Cronológica",
                                        color = Color(0xFF818CF8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Lembretes isolados para: ${state.currentUser.name}",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                        )

                        // Search Bar
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = {
                                Text(
                                    "Buscar compromisso...",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF121933),
                                unfocusedContainerColor = Color(0xFF121933),
                                focusedBorderColor = Color(0xFF4F46E5),
                                unfocusedBorderColor = Color(0xFF26335C),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                    }
                }

                // Status Filter Tabs
                item {
                    val statusCounts = mapOf(
                        "Pendentes" to state.reminders.count { it.status == "pending" },
                        "Adiados" to state.reminders.count { it.status == "postponed" },
                        "Concluídos" to state.reminders.count { it.status == "completed" },
                        "Todos" to state.reminders.size
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF121933))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("Pendentes", "Adiados", "Concluídos", "Todos").forEach { label ->
                            val count = statusCounts[label] ?: 0
                            val isSelected = state.statusFilter == label
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF4F46E5) else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setStatusFilter(label) }
                            ) {
                                Text(
                                    text = "$label ($count)",
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Category Filter Chips
                item {
                    val scrollChips = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollChips),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 2.dp)
                        )
                        listOf("Todas", "Trabalho", "Saúde", "Finanças", "Pessoal", "Estudos").forEach { cat ->
                            val isSelected = state.categoryFilter == cat
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF141C38),
                                border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222D52)) else null,
                                modifier = Modifier.clickable { viewModel.setCategoryFilter(cat) }
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Reminders list OR Empty State
                if (state.filteredReminders.isEmpty()) {
                    item {
                        EmptyStateView()
                    }
                } else {
                    items(state.filteredReminders, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            onToggleComplete = { viewModel.toggleComplete(reminder.id) },
                            onPostpone = { viewModel.postponeReminder(reminder.id, 10) },
                            onDelete = { viewModel.deleteReminder(reminder.id) },
                            onSpeak = { viewModel.speakReminder(reminder) },
                            onTriggerAlarmNow = {
                                val testReminder = reminder.copy(
                                    scheduledEpochMs = System.currentTimeMillis() + 2000L
                                )
                                com.example.alarm.AlarmScheduler(viewModel.getApplication())
                                    .schedule(testReminder)
                            }
                        )
                    }
                }

                // Bottom Brand Indicator
                item {
                    Text(
                        text = "• Lembrete Inteligente • Agenda de Lembretes e Compromissos",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    )
                }
            }
        }
    }

    // Switch User Dialog (Michel vs Maya)
    if (showUserSwitchDialog) {
        AlertDialog(
            onDismissRequest = { showUserSwitchDialog = false },
            containerColor = Color(0xFF121933),
            title = {
                Text("Contas Isoladas no Firestore", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Alterne entre as contas para verificar o isolamento das subcoleções Firestore (UIDs Michel e Maya):",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )

                    // Michel
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.switchUser(USER_MICHEL)
                                showUserSwitchDialog = false
                            },
                        color = if (state.currentUser.uid == USER_MICHEL.uid) Color(0xFF1E284A) else Color(0xFF0D142E),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (state.currentUser.uid == USER_MICHEL.uid) Color(0xFF4F46E5) else Color(0xFF26335C)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4F46E5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("M", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Michel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("UID: ${USER_MICHEL.uid}", color = Color(0xFF818CF8), fontSize = 10.sp)
                                Text(USER_MICHEL.email, color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }
                    }

                    // Maya
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.switchUser(USER_MAYA)
                                showUserSwitchDialog = false
                            },
                        color = if (state.currentUser.uid == USER_MAYA.uid) Color(0xFF1E284A) else Color(0xFF0D142E),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (state.currentUser.uid == USER_MAYA.uid) Color(0xFF4F46E5) else Color(0xFF26335C)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF06B6D4)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("M", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Maya", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("UID: ${USER_MAYA.uid}", color = Color(0xFF06B6D4), fontSize = 10.sp)
                                Text(USER_MAYA.email, color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserSwitchDialog = false }) {
                    Text("Fechar", color = Color(0xFF818CF8))
                }
            }
        )
    }

    // Background Architecture Info Dialog
    if (showBackgroundInfoDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundInfoDialog = false },
            containerColor = Color(0xFF121933),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color(0xFF10B981))
                    Text("Camada Nativa Android", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "• Microfone Nativo do Sistema: Reconhecimento de voz em tempo real através do SpeechRecognizer interno do Android (sem depender de diálogo popup externo do Google).",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                    Text(
                        "• Síntese de Voz (TTS): Confirmação em áudio em português falado na hora do agendamento e no disparo do alarme.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                    Text(
                        "• AlarmManager Core: Alarmes registrados com setExactAndAllowWhileIdle() diretamente no kernel do Android.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                    Text(
                        "• Tela de Bloqueio: Intent disparada com WAKE_LOCK e CATEGORY_ALARM com prioridade máxima.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showBackgroundInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("Entendido", color = Color.White)
                }
            }
        )
    }

    // Snackbar Host
    state.snackbarMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSnackbar()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E284A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4F46E5)),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun CommandInputCard(
    inputText: String,
    onInputChanged: (String) -> Unit,
    selectedAdvanceNotice: Int,
    onAdvanceNoticeChanged: (Int) -> Unit,
    onSchedule: () -> Unit,
    isListening: Boolean,
    soundLevel: Float,
    speechStatusText: String,
    onStartMic: () -> Unit,
    onExampleSelected: (String) -> Unit
) {
    // Pulse animation for native microphone
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isListening) Color(0xFF06B6D4) else Color(0xFF1F2A52),
                RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121933)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Live voice recognition active indicator
            if (isListening || speechStatusText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isListening) Color(0xFF0D253A) else Color(0xFF1B2344),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isListening) Color(0xFF06B6D4) else Color(0xFF3B487A)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isListening) Color(0xFF10B981) else Color(0xFF818CF8))
                            )
                            Text(
                                text = if (isListening) "🎙️ Microfone nativo do sistema ouvindo..." else speechStatusText,
                                color = if (isListening) Color(0xFF6EE7B7) else Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Live audio wave bars reacting to microphone sound volume
                        if (isListening) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(5) { index ->
                                    val barHeight = ((soundLevel * (index + 2) * 5) % 18).coerceIn(4f, 18f).dp
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(0xFF06B6D4))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Text Area Input
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = {
                    Text(
                        "Fale no microfone ou digite: \"Hoje às 15h tomar remédio\" ou \"Pagar conta amanhã às 10h\"...",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .testTag("command_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Advance notice pills & Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Advance notice selector
                val scrollNotice = rememberScrollState()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollNotice),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "⏰ Avisar:",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                    listOf(
                        0 to "No horário",
                        5 to "5m antes",
                        10 to "10m antes",
                        15 to "15m antes",
                        30 to "30m antes"
                    ).forEach { (minutes, label) ->
                        val isSelected = selectedAdvanceNotice == minutes
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF161F3D),
                            modifier = Modifier.clickable { onAdvanceNoticeChanged(minutes) }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Microphone round button (native system audio)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isListening)
                                Brush.radialGradient(listOf(Color(0xFF06B6D4), Color(0xFF059669)))
                            else
                                Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5)))
                        )
                        .clickable { onStartMic() }
                        .testTag("mic_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = "Falar com microfone do sistema",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Agendar button (teal/green)
                Button(
                    onClick = onSchedule,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("schedule_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Agendar",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Exemplos Row
            val scrollExamples = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollExamples),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Exemplos:",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                listOf(
                    "Tomar remédio todo dia às 08h",
                    "Reunião toda segunda às 09h",
                    "Pagar boleto amanhã às 14h",
                    "Alongamento de segunda a sexta às 16h"
                ).forEach { example ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF172242),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26335C)),
                        modifier = Modifier.clickable { onExampleSelected(example) }
                    ) {
                        Text(
                            text = example,
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderCard(
    reminder: Reminder,
    onToggleComplete: () -> Unit,
    onPostpone: () -> Unit,
    onDelete: () -> Unit,
    onSpeak: () -> Unit,
    onTriggerAlarmNow: () -> Unit
) {
    val categoryColor = when (reminder.category) {
        "Saúde" -> Color(0xFF10B981)
        "Trabalho" -> Color(0xFF6366F1)
        "Finanças" -> Color(0xFFF59E0B)
        "Estudos" -> Color(0xFF06B6D4)
        else -> Color(0xFFA855F7)
    }

    val isCompleted = reminder.status == "completed"
    val isPostponed = reminder.status == "postponed"

    val sdf = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
    val formattedDate = sdf.format(Date(reminder.scheduledEpochMs))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isCompleted) Color(0xFF1B2642) else Color(0xFF222F56),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) Color(0xFF0E1428) else Color(0xFF131A36)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Category Tag & Status & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = categoryColor.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, categoryColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = reminder.category,
                            color = categoryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (isPostponed) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFD97706).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Adiado",
                                color = Color(0xFFFBBF24),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Concluído",
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Speak reminder aloud button
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Ouvir lembrete falado",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Excluir lembrete",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = reminder.title,
                color = if (isCompleted) Color(0xFF64748B) else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Time & Advance Notice
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = formattedDate,
                    color = Color(0xFF818CF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                if (reminder.advanceNoticeMinutes > 0) {
                    Text(
                        text = "• Avisar ${reminder.advanceNoticeMinutes}m antes",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Actions: Complete, Postpone, Quick Test
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Test alarm right now
                TextButton(
                    onClick = onTriggerAlarmNow,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF06B6D4),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Testar Som",
                            color = Color(0xFF06B6D4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E284A),
                            modifier = Modifier.clickable { onPostpone() }
                        ) {
                            Text(
                                text = "Adiar (+10m)",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCompleted) Color(0xFF1E284A) else Color(0xFF10B981),
                        modifier = Modifier.clickable { onToggleComplete() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isCompleted) Icons.Default.Replay else Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isCompleted) "Reativar" else "Concluir",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .border(1.dp, Color(0xFF1A2242), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10162E)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1D2447)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = Color(0xFFF43F5E),
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Nenhum lembrete nesta visualização",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tudo em dia! Diga ou digite um comando acima para agendar novas tarefas.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
