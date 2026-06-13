package com.example.ui

import com.example.BuildConfig
import android.speech.tts.TextToSpeech
import com.example.api.RetrofitClient
import com.example.api.GoogleTtsPlayer
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Intent
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.data.ChatMessage
import com.example.data.ChatSessionEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainHubScreen(
    viewModel: EspelhoViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    // Setup self-contained premium Google TTS
    LaunchedEffect(Unit) {
        val googleTtsEnginePackage = "com.google.android.tts"
        val onInitListener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale("pt", "BR")
                tts?.language = locale
                
                // Use natural settings to completely avoid artificial robotic/metallic artifacts
                tts?.setSpeechRate(1.08f) // Conversational and natural speaking rate (slightly accelerated for a better response time)
                tts?.setPitch(1.0f)      // 100% natural, pristine human pitch (no artificial distortion!)
                
                // Programmatically select Google's highly polished, lifelike neural offline voices
                try {
                    val voices = tts?.voices
                    if (!voices.isNullOrEmpty()) {
                        // Filter specifically for Portuguese (Brazil) 
                        val ptBrVoices = voices.filter { voice ->
                            val vLocale = voice.locale
                            vLocale.language == "pt" && (vLocale.country.equals("br", ignoreCase = true) || vLocale.country.isEmpty())
                        }
                        
                        // Select Google's modern premium natural voices (prefixed with pt-br-x-)
                        // These sound identical to real professional assistants (like Google Assistant)
                        val premiumVoice = ptBrVoices.find { voice ->
                            val vName = voice.name.lowercase()
                            vName.contains("pt-br-x-yef") || // Ultra premium high-fidelity female voice
                            vName.contains("pt-br-x-afs") || // Extremely natural local voice
                            vName.contains("pt-br-x-gdm") || // Premium conversational accent
                            vName.contains("pt-br-x-ptm")    // Highly realistic male voice
                        } ?: ptBrVoices.find { it.name.lowercase().contains("x-") } // Any Google premium neural local voice
                          ?: ptBrVoices.find { !it.isNetworkConnectionRequired }   // Any offline fallback
                          ?: ptBrVoices.firstOrNull()

                        if (premiumVoice != null) {
                            tts?.voice = premiumVoice
                            android.util.Log.d("EspelhoTTS", "Successfully selected premium Google voice: ${premiumVoice.name}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EspelhoTTS", "Error choosing premium voice: ${e.message}")
                }
            } else {
                android.util.Log.e("EspelhoTTS", "Google TTS engine initialization failed with code $status")
            }
        }

        // Initialize with Google's High-Quality Speech Engine specifically. 
        // If not installed on the system, gracefully falls back to the default device synthesizer.
        tts = try {
            TextToSpeech(context, onInitListener, googleTtsEnginePackage)
        } catch (e: Throwable) {
            TextToSpeech(context, onInitListener)
        }
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel.speechTextTrigger) {
        viewModel.speechTextTrigger.collect { text ->
            coroutineScope.launch {
                GoogleTtsPlayer.stop()
                tts?.stop()
                // Sanitize formatting from text to avoid speaking artifacts or robotic pronunciation
                val cleanText = text
                    .replace(Regex("\\*+"), "")
                    .replace(Regex("#+"), "")
                    .replace(Regex("`+"), "")
                    .replace("- ", " ")
                    .trim()
                // Use a voz local de alta fidelidade nativa do Google para latência zero e total estabilidade!
                tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "espelho_tts_id")
            }
        }
    }

    LaunchedEffect(viewModel.geminiAudioTrigger) {
        viewModel.geminiAudioTrigger.collect { (base64, mimeType) ->
            coroutineScope.launch {
                tts?.stop()
                GoogleTtsPlayer.stop()
                GoogleTtsPlayer.playBase64Audio(context, base64, mimeType)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            GoogleTtsPlayer.stop()
            tts?.stop()
            tts?.shutdown()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBackground)
            .windowInsetsPadding(WindowInsets.navigationBars),
        bottomBar = {
            NavigationBar(
                containerColor = CosmicSurface,
                modifier = Modifier
                    .height(72.dp)
                    .border(width = 1.dp, color = CosmicBorder, shape = RoundedCornerShape(18.dp, 18.dp, 0.dp, 0.dp))
                    .clip(RoundedCornerShape(18.dp, 18.dp, 0.dp, 0.dp))
            ) {
                val menuItems = listOf(
                    Triple(ActiveTab.HOME, "Início", Icons.Default.Home),
                    Triple(ActiveTab.CHAT, "Conversar", Icons.Default.ChatBubble),
                    Triple(ActiveTab.PLAN, "Plano", Icons.Default.Map),
                    Triple(ActiveTab.SESSIONS, "Sessão", Icons.Default.SelfImprovement),
                    Triple(ActiveTab.PROFILE, "Perfil", Icons.Default.AccountCircle)
                )

                menuItems.forEach { (tab, label, icon) ->
                    val active = currentTab == tab
                    NavigationBarItem(
                        selected = active,
                        onClick = { viewModel.setTab(tab) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (active) CosmicTextPrimary else CosmicTextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) CosmicTextPrimary else CosmicTextSecondary.copy(alpha = 0.5f),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = CosmicPrimary.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CosmicBackground)
                .padding(innerPadding)
        ) {
            when (currentTab) {
                ActiveTab.HOME -> HomeTab(viewModel = viewModel)
                ActiveTab.CHAT -> ChatTab(viewModel = viewModel, tts = tts)
                ActiveTab.DECIDE -> DecideTab(viewModel = viewModel)
                ActiveTab.PLAN -> PlanTab(viewModel = viewModel)
                ActiveTab.SESSIONS -> SessionsTab(viewModel = viewModel, tts = tts)
                ActiveTab.READINGS -> ReadingsTab(viewModel = viewModel)
                ActiveTab.PROFILE -> ProfileTab(viewModel = viewModel)
            }
        }
    }
}

// ==================== 1. HOME TAB ====================

@Composable
fun HomeTab(viewModel: EspelhoViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()
    val dailyText by viewModel.dailyReflectionText.collectAsState()
    val dailyLoading by viewModel.dailyReflectionLoading.collectAsState()

    val totalSessions = sessions.size
    val totalReflections = sessions.sumOf { it.messageCount }
    val streak = profile?.streak ?: 1

    val todayDate = remember {
        val sdf = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))
        sdf.format(Date())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)
    ) {
        // Welcome Header
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = todayDate,
                    color = CosmicTextSecondary.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
                Text(
                    text = "Olá, ${profile?.name ?: "você"} 👋",
                    color = CosmicTextPrimary,
                    fontSize = 26.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Stats Dashboard Card Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val stats = listOf(
                    Triple(totalSessions.toString(), "Sessões", CosmicPrimaryGlow),
                    Triple(totalReflections.toString(), "Reflexões", CosmicSecondary),
                    Triple(streak.toString(), "Dias ativos", CosmicAccentRose)
                )

                stats.forEach { (value, label, color) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        backgroundColor = Color(0x0C7C5FE6),
                        borderStrokeColor = CosmicBorder.copy(alpha = 0.15f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = value,
                                color = color,
                                fontSize = 22.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Text(
                                text = label,
                                color = CosmicTextSecondary.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Daily Reflection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0x1F130F30),
                borderStrokeColor = CosmicPrimary.copy(alpha = 0.25f)
            ) {
                Label(text = "✨ REFLEXÃO DO DIA")
                if (dailyLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Dots()
                    }
                } else if (dailyText.isEmpty()) {
                    Text(
                        text = "Toque abaixo para revelar sua pergunta reflexiva personalizada de hoje, baseada em seus principais desafios.",
                        color = CosmicTextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontStyle = FontStyle.Normal,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )

                    Button(
                        onClick = { viewModel.generateDailyReflection() },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, CosmicPrimaryGlow.copy(alpha = 0.40f)),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("generate_reflection_button")
                    ) {
                        Text(
                            text = "Revelar reflexão de hoje ✨",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                } else if (dailyText == "Não foi possível carregar a reflexão diária hoje." || dailyText.contains("Limite Excedido") || dailyText.contains("Erro")) {
                    Text(
                        text = if (dailyText.contains("Limite Excedido") || dailyText.contains("Erro")) dailyText else "Não foi possível conectar com a IA no momento (Limite Excedido/HTTP 429). Por favor, aguarde um instante e tente novamente.",
                        color = CosmicTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )

                    Button(
                        onClick = { viewModel.generateDailyReflection() },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.35f)),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("retry_reflection_button")
                    ) {
                        Text(
                            text = "🔄 Tentar novamente",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                } else {
                    Text(
                        text = "\"$dailyText\"",
                        color = CosmicTextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )

                    Button(
                        onClick = { viewModel.setTab(ActiveTab.CHAT, initialChatPrompt = dailyText) },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary.copy(alpha = 0.28f)),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.35f)),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("reflect_button")
                    ) {
                        Text(
                            text = "Refletir sobre isso →",
                            color = CosmicPrimaryGlow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }

        // Feature shortcuts
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Label(text = "FERRAMENTAS", color = CosmicTextSecondary.copy(alpha = 0.45f))
                Spacer(modifier = Modifier.height(10.dp))

                val tools = listOf(
                    Triple(ActiveTab.CHAT, "💬 Conversar", "Espelho da mente: fale tudo o que sente"),
                    Triple(ActiveTab.DECIDE, "⚖️ Decidir", "Análise socrática de decisões travadas"),
                    Triple(ActiveTab.SESSIONS, "🧘 Sessão guiada", "Trabalho psicológico focado por tema"),
                    Triple(ActiveTab.READINGS, "📖 Leitura profunda", "Ensaios personalizados baseados em você")
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tools.forEach { (tab, title, desc) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tool_${tab.name.lowercase()}"),
                            onClick = { viewModel.setTab(tab) }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CosmicTextPrimary,
                                    fontFamily = FontFamily.SansSerif,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Text(
                                    text = desc,
                                    fontSize = 12.sp,
                                    color = CosmicTextSecondary.copy(alpha = 0.5f),
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 2. CHAT TAB ====================

@Composable
fun ChatTab(viewModel: EspelhoViewModel, tts: TextToSpeech?) {
    val messages by viewModel.chatMessages.collectAsState()
    val thinking by viewModel.chatThinking.collectAsState()
    val voiceActive by viewModel.voiceModeActive.collectAsState()

    var input by remember { mutableStateOf("") }
    val listState = rememberScrollState()
    val context = LocalContext.current

    var isListeningSpeech by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "speech_pulsate")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    fun triggerStartSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Reconhecimento de voz offline não suportado neste aparelho.", Toast.LENGTH_LONG).show()
            isListeningSpeech = false
            return
        }

        if (speechRecognizer == null) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListeningSpeech = true
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        
                        override fun onError(error: Int) {
                            if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                                if (isListeningSpeech) {
                                    try {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                        }
                                        speechRecognizer?.startListening(intent)
                                    } catch (e: Exception) {
                                        isListeningSpeech = false
                                    }
                                }
                            } else {
                                isListeningSpeech = false
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val spokenText = matches[0]
                                input = if (input.isEmpty()) spokenText else "$input $spokenText"
                            }
                            
                            if (isListeningSpeech) {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                    }
                                    speechRecognizer?.startListening(intent)
                                } catch (e: Exception) {
                                    isListeningSpeech = false
                                }
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Não foi possível carregar o comando de voz: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                isListeningSpeech = false
                return
            }
        }

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }
            speechRecognizer?.startListening(intent)
            isListeningSpeech = true
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao iniciar o reconhecimento de voz.", Toast.LENGTH_SHORT).show()
            isListeningSpeech = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            triggerStartSpeech()
        } else {
            Toast.makeText(context, "Permissão de microfone é necessária para falar diretamente.", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleSpeechRecognition() {
        if (isListeningSpeech) {
            isListeningSpeech = false
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {}
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            
            if (hasPermission) {
                triggerStartSpeech()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initLiveChatIfNeeded()
    }

    LaunchedEffect(messages.size, thinking) {
        // Auto scrolls when new message arrives
        listState.animateScrollTo(listState.maxValue)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top voice bar toggler
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = CosmicBorder.copy(alpha = 0.08f))
                .padding(8.dp, 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    if (voiceActive) {
                        tts?.stop()
                    }
                    viewModel.toggleVoiceMode()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (voiceActive) CosmicPrimary.copy(alpha = 0.28f) else Color(0x06FFFFFF)
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (voiceActive) CosmicPrimary else CosmicBorder),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("voice_toggle")
            ) {
                Text(
                    text = if (voiceActive) "🎙️ Voz ativa" else "🎙️ Ativar voz",
                    color = if (voiceActive) CosmicPrimaryGlow else CosmicTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        // Call thread
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(listState)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            messages.forEach { msg ->
                val isAssistant = msg.role == "assistant"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = if (isAssistant) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.Top
                ) {
                    if (isAssistant) {
                        Orb(size = 30.dp, modifier = Modifier.padding(end = 8.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .border(
                                width = 1.dp,
                                color = if (isAssistant) CosmicPrimary.copy(alpha = 0.24f) else Color(0x08FFFFFF),
                                shape = if (isAssistant) RoundedCornerShape(2.dp, 16.dp, 16.dp, 16.dp) else RoundedCornerShape(16.dp, 2.dp, 16.dp, 16.dp)
                            )
                            .clip(if (isAssistant) RoundedCornerShape(2.dp, 16.dp, 16.dp, 16.dp) else RoundedCornerShape(16.dp, 2.dp, 16.dp, 16.dp))
                            .background(
                                if (isAssistant) CosmicPrimary.copy(alpha = 0.11f) else Color(0x0EFFFFFF)
                            )
                            .padding(14.dp, 10.dp)
                    ) {
                        Text(
                            text = msg.content,
                            color = CosmicTextPrimary,
                            fontSize = 14.sp,
                            fontFamily = if (isAssistant) FontFamily.Serif else FontFamily.SansSerif,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            if (thinking) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    Orb(size = 30.dp, anim = "pulse", modifier = Modifier.padding(end = 8.dp))
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = CosmicPrimary.copy(alpha = 0.24f),
                                shape = RoundedCornerShape(2.dp, 16.dp, 16.dp, 16.dp)
                            )
                            .clip(RoundedCornerShape(2.dp, 16.dp, 16.dp, 16.dp))
                            .background(CosmicPrimary.copy(alpha = 0.11f))
                            .padding(18.dp, 14.dp)
                    ) {
                        Dots()
                    }
                }
            }
        }

        // Input Actions area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = CosmicBorder.copy(alpha = 0.08f))
                .background(Color(0xFF060417))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = CosmicPrimary.copy(alpha = 0.28f), shape = RoundedCornerShape(18.dp))
                    .background(Color(0x05FFFFFF))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            text = if (isListeningSpeech) "🎙️ Ouvindo... fale continuamente" else "Fale o que está sentindo...",
                            color = if (isListeningSpeech) CosmicAccentRose else Color(0x44C8BEFF),
                            fontSize = 14.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input"),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Serif, fontSize = 14.sp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { toggleSpeechRecognition() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListeningSpeech) CosmicAccentRose.copy(alpha = pulseAlpha) else Color(0x1F7C5FE6)
                            )
                            .testTag("audio_input_button")
                    ) {
                        Icon(
                            imageVector = if (isListeningSpeech) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Falar por áudio",
                            tint = if (isListeningSpeech) Color.White else CosmicTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val senderText = input
                            if (senderText.trim().isNotEmpty()) {
                                viewModel.sendChatMessage(senderText)
                                input = ""
                            }
                        },
                        enabled = input.trim().isNotEmpty() && !thinking,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (input.trim().isNotEmpty() && !thinking) CosmicPrimary else Color(0x1F7C5FE6)
                            )
                            .testTag("send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Enviar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== 3. DECIDE TAB ====================

@Composable
fun DecideTab(viewModel: EspelhoViewModel) {
    val view by viewModel.deciderView.collectAsState()
    val decision by viewModel.deciderInputDecision.collectAsState()
    val optA by viewModel.deciderOptionA.collectAsState()
    val optB by viewModel.deciderOptionB.collectAsState()
    val result by viewModel.deciderResult.collectAsState()
    val loading by viewModel.deciderLoading.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp)
            .padding(top = 18.dp, bottom = 40.dp)
    ) {
        when (view) {
            "input" -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Text(text = "⚖️", fontSize = 32.sp, modifier = Modifier.padding(end = 12.dp))
                    Column {
                        Text(text = "Análise de Decisão", color = CosmicTextPrimary, fontSize = 21.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
                        Text(text = "Traga clareza socrática a suas escolhas travadas", color = CosmicTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }
                }

                Label(text = "QUAL É A DECISÃO?")
                OutlinedTextField(
                    value = decision,
                    onValueChange = { viewModel.deciderInputDecision.value = it },
                    placeholder = { Text(text = "Ex: Devo pedir demissão para abrir minha própria empresa? Devo me mudar de cidade?", color = Color(0x22C8BEFF)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0x08FFFFFF),
                        unfocusedContainerColor = Color(0x08FFFFFF),
                        focusedBorderColor = CosmicPrimary,
                        unfocusedBorderColor = CosmicBorder,
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("decision_input"),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Serif, fontSize = 14.sp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.deciderView.value = "options" },
                    enabled = decision.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CosmicPrimary,
                        disabledContainerColor = Color(0x1F7C5FE6),
                        disabledContentColor = Color(0x33E8E0FF)
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("decider_continue")
                ) {
                    Text(text = "Configurar Opções →", fontSize = 15.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium)
                }
            }

            "options" -> {
                BackBtn(onClick = { viewModel.deciderView.value = "input" })
                
                Text(
                    text = "Quais são as opções?",
                    color = CosmicTextPrimary,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "Opcional — o sistema irá inferir as rotas ideais se deixar em branco.",
                    color = CosmicTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Label(text = "OPÇÃO A")
                OutlinedTextField(
                    value = optA,
                    onValueChange = { viewModel.deciderOptionA.value = it },
                    placeholder = { Text(text = "Ex: Pedir demissão e arriscar", color = Color(0x22C8BEFF)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0x08FFFFFF),
                        unfocusedContainerColor = Color(0x08FFFFFF),
                        focusedBorderColor = CosmicPrimary,
                        unfocusedBorderColor = CosmicBorder,
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .testTag("option_a_input"),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif, fontSize = 14.sp)
                )

                Label(text = "OPÇÃO B")
                OutlinedTextField(
                    value = optB,
                    onValueChange = { viewModel.deciderOptionB.value = it },
                    placeholder = { Text(text = "Ex: Manter estabilidade do trabalho atual", color = Color(0x22C8BEFF)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0x08FFFFFF),
                        unfocusedContainerColor = Color(0x08FFFFFF),
                        focusedBorderColor = CosmicPrimary,
                        unfocusedBorderColor = CosmicBorder,
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .testTag("option_b_input"),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif, fontSize = 14.sp)
                )

                Button(
                    onClick = { viewModel.analyzeDecision() },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("decider_analyze")
                ) {
                    Text(text = "⚖️ Analisar Decisão", fontSize = 15.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium)
                }
            }

            "result" -> {
                BackBtn(onClick = { viewModel.resetDecider() }, text = "Nova decisão")

                if (loading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Orb(size = 56.dp, anim = "pulse", glow = true)
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(text = "Analisando sua decisão...", color = CosmicTextSecondary, fontSize = 13.sp, fontFamily = FontFamily.SansSerif)
                    }
                } else if (result != null) {
                    val r = result!!
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Analysis summary
                        Card(
                            backgroundColor = CosmicPrimary.copy(alpha = 0.1f),
                            borderStrokeColor = CosmicPrimary.copy(alpha = 0.25f)
                        ) {
                            Label(text = "ANÁLISE")
                            Text(
                                text = r.summary,
                                color = CosmicTextPrimary,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Serif,
                                lineHeight = 21.sp
                            )
                        }

                        // Comparative score
                        Card {
                            Label(text = "SCORE COMPARATIVO")
                            Spacer(modifier = Modifier.height(6.dp))

                            // Opt A
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = optA.ifEmpty { "Opção A" },
                                    color = CosmicTextPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(text = "${r.score_a}%", color = CosmicPrimaryGlow, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                            }
                            Bar(value = r.score_a.toFloat(), color = CosmicPrimary)

                            Spacer(modifier = Modifier.height(14.dp))

                            // Opt B
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = optB.ifEmpty { "Opção B" },
                                    color = CosmicTextPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(text = "${r.score_b}%", color = CosmicSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                            }
                            Bar(value = r.score_b.toFloat(), color = CosmicSecondary)
                        }

                        // Factors list
                        Card {
                            Label(text = "FATORES PRINCIPAIS")
                            Spacer(modifier = Modifier.height(6.dp))

                            r.factors.forEachIndexed { idx, factor ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = factor.name, color = CosmicTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
                                        Text(text = "peso ${factor.weight}/10", color = CosmicTextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "A: ${factor.forA}  ·  B: ${factor.forB}",
                                        color = CosmicTextSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        lineHeight = 16.sp
                                    )

                                    if (idx < r.factors.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = CosmicBorder.copy(alpha = 0.12f)
                                        )
                                    }
                                }
                            }
                        }

                        // Emotional block alert
                        Card(
                            backgroundColor = CosmicAccentRose.copy(alpha = 0.08f),
                            borderStrokeColor = CosmicAccentRose.copy(alpha = 0.22f)
                        ) {
                            Label(text = "🔍 O QUE PODE TE TRAVAR", color = CosmicAccentRose)
                            Text(
                                text = r.emotional_block,
                                color = CosmicTextPrimary,
                                fontSize = 14.sp,
                                fontStyle = FontStyle.Italic,
                                fontFamily = FontFamily.Serif,
                                lineHeight = 20.sp
                            )
                        }

                        // Recommendation and socratic key question
                        Card(
                            backgroundColor = Color(0x1F130F30),
                            borderStrokeColor = CosmicPrimary.copy(alpha = 0.3f)
                        ) {
                            Label(text = "💡 RECOMENDAÇÃO")
                            Text(
                                text = r.recommendation,
                                color = CosmicTextPrimary,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Serif,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            HorizontalDivider(color = CosmicPrimary.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 12.dp))
                            Text(
                                text = "💭 ${r.key_question}",
                                color = CosmicPrimaryGlow,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 4. SESSIONS TAB ====================

@Composable
fun SessionsTab(viewModel: EspelhoViewModel, tts: TextToSpeech?) {
    val selectedTopic by viewModel.selectedSessionTopic.collectAsState()
    val sessionMsgs by viewModel.sessionMessages.collectAsState()
    val thinking by viewModel.sessionThinking.collectAsState()
    val voiceActive by viewModel.voiceModeActive.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val listState = rememberScrollState()
    val context = LocalContext.current

    var isListeningSpeech by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "sessions_speech_pulsate")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sessions_pulse_alpha"
    )

    fun triggerStartSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Reconhecimento de voz offline não suportado neste aparelho.", Toast.LENGTH_LONG).show()
            isListeningSpeech = false
            return
        }

        if (speechRecognizer == null) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListeningSpeech = true
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        
                        override fun onError(error: Int) {
                            if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                                if (isListeningSpeech) {
                                    try {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                        }
                                        speechRecognizer?.startListening(intent)
                                    } catch (e: Exception) {
                                        isListeningSpeech = false
                                    }
                                }
                            } else {
                                isListeningSpeech = false
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val spokenText = matches[0]
                                textInput = if (textInput.isEmpty()) spokenText else "$textInput $spokenText"
                            }
                            
                            if (isListeningSpeech) {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                    }
                                    speechRecognizer?.startListening(intent)
                                } catch (e: Exception) {
                                    isListeningSpeech = false
                                }
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Não foi possível carregar o comando de voz: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                isListeningSpeech = false
                return
            }
        }

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }
            speechRecognizer?.startListening(intent)
            isListeningSpeech = true
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao iniciar o reconhecimento de voz.", Toast.LENGTH_SHORT).show()
            isListeningSpeech = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            triggerStartSpeech()
        } else {
            Toast.makeText(context, "Permissão de microfone é necessária para falar diretamente.", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleSpeechRecognition() {
        if (isListeningSpeech) {
            isListeningSpeech = false
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {}
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            
            if (hasPermission) {
                triggerStartSpeech()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(sessionMsgs.size, thinking) {
        listState.animateScrollTo(listState.maxValue)
    }

    if (selectedTopic != null) {
        val topic = selectedTopic!!
        Column(modifier = Modifier.fillMaxSize()) {
            // Topic custom header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = CosmicBorder.copy(alpha = 0.08f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    if (voiceActive) {
                        tts?.stop()
                    }
                    if (isListeningSpeech) {
                        isListeningSpeech = false
                        try {
                            speechRecognizer?.stopListening()
                        } catch (e: Exception) {}
                    }
                    viewModel.exitSession() 
                }) {
                    Text(text = "←", color = CosmicTextSecondary, fontSize = 20.sp)
                }
                Text(text = topic.emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                Text(text = topic.label, color = CosmicTextPrimary, fontSize = 15.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium)
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = {
                        if (voiceActive) {
                            tts?.stop()
                        }
                        viewModel.toggleVoiceMode()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (voiceActive) CosmicPrimary.copy(alpha = 0.28f) else Color(0x06FFFFFF)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (voiceActive) CosmicPrimary else CosmicBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("session_voice_toggle")
                ) {
                    Text(
                        text = if (voiceActive) "🎙️ Voz ativa" else "🎙️ Ativar voz",
                        color = if (voiceActive) CosmicPrimaryGlow else CosmicTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // Msg list Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(listState)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                sessionMsgs.forEach { msg ->
                    val isAssistant = msg.role == "assistant"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = if (isAssistant) Arrangement.Start else Arrangement.End,
                        verticalAlignment = Alignment.Top
                    ) {
                        if (isAssistant) {
                            Orb(size = 30.dp, modifier = Modifier.padding(end = 8.dp))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.78f)
                                .border(
                                    width = 1.dp,
                                    color = if (isAssistant) CosmicPrimary.copy(alpha = 0.24f) else Color(0x08FFFFFF),
                                    shape = if (isAssistant) RoundedCornerShape(2.dp, 16.dp, 16.dp, 16.dp) else RoundedCornerShape(16.dp, 2.dp, 16.dp, 16.dp)
                                )
                                .clip(if (isAssistant) RoundedCornerShape(2.dp, 16.dp, 16.dp, 16.dp) else RoundedCornerShape(16.dp, 2.dp, 16.dp, 16.dp))
                                .background(
                                    if (isAssistant) CosmicPrimary.copy(alpha = 0.11f) else Color(0x0EFFFFFF)
                                )
                                .padding(14.dp, 10.dp)
                        ) {
                            Text(
                                text = msg.content,
                                color = CosmicTextPrimary,
                                fontSize = 14.sp,
                                fontFamily = if (isAssistant) FontFamily.Serif else FontFamily.SansSerif,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                if (thinking) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        Orb(size = 30.dp, anim = "pulse", modifier = Modifier.padding(end = 8.dp))
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = CosmicPrimary.copy(alpha = 0.24f),
                                    shape = RoundedCornerShape(2.dp, 16.dp, 16.dp, 16.dp)
                                )
                                .clip(RoundedCornerShape(2.dp, 16.dp, 16.dp, 16.dp))
                                .background(CosmicPrimary.copy(alpha = 0.11f))
                                .padding(18.dp, 14.dp)
                        ) {
                            Dots()
                        }
                    }
                }
            }

            // Input Action bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = CosmicBorder.copy(alpha = 0.08f))
                    .background(Color(0xFF060417))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 1.dp, color = CosmicPrimary.copy(alpha = 0.28f), shape = RoundedCornerShape(18.dp))
                        .background(Color(0x05FFFFFF))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                text = if (isListeningSpeech) "🎙️ Ouvindo... fale continuamente" else "Responda com total honestidade...",
                                color = if (isListeningSpeech) CosmicAccentRose else Color(0x44C8BEFF),
                                fontSize = 14.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = CosmicTextPrimary,
                            unfocusedTextColor = CosmicTextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("session_chat_input"),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Serif, fontSize = 14.sp),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { toggleSpeechRecognition() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isListeningSpeech) CosmicAccentRose.copy(alpha = pulseAlpha) else Color(0x1F7C5FE6)
                                )
                                .testTag("session_audio_input_button")
                        ) {
                            Icon(
                                imageVector = if (isListeningSpeech) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Falar por áudio",
                                tint = if (isListeningSpeech) Color.White else CosmicTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val msgToSend = textInput
                                if (msgToSend.trim().isNotEmpty()) {
                                    if (isListeningSpeech) {
                                        isListeningSpeech = false
                                        try {
                                            speechRecognizer?.stopListening()
                                        } catch (e: Exception) {}
                                    }
                                    viewModel.sendSessionMessage(msgToSend)
                                    textInput = ""
                                }
                            },
                            enabled = textInput.trim().isNotEmpty() && !thinking,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (textInput.trim().isNotEmpty() && !thinking) CosmicPrimary else Color(0x1F7C5FE6)
                                )
                                .testTag("session_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Enviar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Selection list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Text(text = "Sessões Guiadas", color = CosmicTextPrimary, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
                Text(text = "Trabalho interior focado em dores sutilmente omitidas", color = CosmicTextSecondary, fontSize = 13.sp, fontFamily = FontFamily.SansSerif)
            }

            val cats = listOf(
                Triple("deep", "PROFUNDO", CosmicPrimaryGlow),
                Triple("healing", "CURA", CosmicAccentRose),
                Triple("practical", "PRÁTICO", CosmicSecondary)
            )

            cats.forEach { (catId, headerLabel, color) ->
                val topics = StaticData.SESSION_TOPICS.filter { it.cat == catId }
                item {
                    Column {
                        Label(text = headerLabel, color = color)
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            topics.forEach { t ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("session_topic_${t.id}"),
                                    onClick = { viewModel.selectSessionTopic(t) }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = t.emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
                                        Text(text = t.label, color = CosmicTextPrimary, fontSize = 14.sp, fontFamily = FontFamily.SansSerif, modifier = Modifier.weight(1f))
                                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Acessar", tint = CosmicTextSecondary.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 5. READINGS TAB ====================

@Composable
fun ReadingsTab(viewModel: EspelhoViewModel) {
    val selectedItem by viewModel.selectedReadingTopic.collectAsState()
    val readingText by viewModel.readingText.collectAsState()
    val loading by viewModel.readingLoading.collectAsState()

    val scrollState = rememberScrollState()

    if (selectedItem != null) {
        val sel = selectedItem!!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 40.dp)
        ) {
            BackBtn(onClick = { viewModel.exitReading() })

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 22.dp)) {
                Text(text = sel.emoji, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
                Text(text = sel.label, color = CosmicTextPrimary, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
            }

            if (loading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Orb(size = 48.dp, anim = "pulse", glow = true)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "Escrevendo leitura analítica personalizada...", color = CosmicTextSecondary, fontSize = 13.sp, fontFamily = FontFamily.SansSerif)
                }
            } else {
                Text(
                    text = readingText,
                    color = CosmicTextPrimary.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        // Topic selection details
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .verticalScroll(scrollState)
                .padding(top = 18.dp, bottom = 40.dp)
        ) {
            Text(text = "Leituras Profundas", color = CosmicTextPrimary, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, modifier = Modifier.padding(bottom = 5.dp))
            Text(text = "Ensaios denso-analíticos gerados especificamente para seu mapa comportamental", color = CosmicTextSecondary, fontSize = 13.sp, fontFamily = FontFamily.SansSerif, modifier = Modifier.padding(bottom = 22.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Split entries in 2 columns
                val topics = StaticData.READING_TOPICS
                val size = topics.size
                for (i in 0 until size step 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (j in 0..1) {
                            val index = i + j
                            if (index < size) {
                                val t = topics[index]
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("reading_topic_${t.id}"),
                                    onClick = { viewModel.selectReadingTopic(t) }
                                ) {
                                    Column {
                                        Text(text = t.emoji, fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
                                        Text(text = t.label, color = CosmicTextPrimary, fontSize = 13.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium)
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 6. PROFILE TAB ====================

@Composable
fun ProfileTab(viewModel: EspelhoViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isSyncing by viewModel.isCloudSyncing.collectAsState()
    val syncError by viewModel.cloudSyncError.collectAsState()
    val syncSuccess by viewModel.cloudSyncSuccess.collectAsState()
    var showGoogleDialog by remember { mutableStateOf(false) }

    val chosenChallenges = remember(profile) {
        val idList = profile?.selectedChallenges?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        idList.mapNotNull { id -> StaticData.CHALLENGES.find { it.id == id } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp)
            .padding(top = 18.dp, bottom = 40.dp)
    ) {
        // Profile Info Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 26.dp)
        ) {
            Orb(size = 54.dp, glow = true, modifier = Modifier.padding(end = 16.dp))
            Column {
                Text(text = profile?.name ?: "você", color = CosmicTextPrimary, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
                Text(text = "Seu IA de espelho mental pessoal", color = CosmicTextSecondary.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = FontFamily.SansSerif, modifier = Modifier.padding(top = 2.dp))
            }
        }

        // --- Google Cloud Sync Connection Center ---
        Card(
            modifier = Modifier.padding(bottom = 22.dp),
            backgroundColor = if (profile?.googleEmail != null) Color(0x0C7C5FE6) else Color(0x06FFFFFF),
            borderStrokeColor = if (profile?.googleEmail != null) CosmicPrimary.copy(alpha = 0.3f) else CosmicBorder.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "☁️",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = "Sincronização em Nuvem (Google)",
                        color = CosmicTextPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal
                    )
                }

                if (profile?.googleEmail != null) {
                    val email = profile?.googleEmail ?: ""
                    Text(
                        text = "Conectado como: $email",
                        color = CosmicTextPrimary.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = "Suas reflexões estão protegidas e salvas na nuvem. Se apagar ou reinstalar o aplicativo, faça login com esta mesma conta para resgatar todos os seus dados.",
                        color = CosmicTextSecondary.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )

                    // Sync Status
                    if (isSyncing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = CosmicPrimaryGlow,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sincronizando dados com servidor...",
                                color = CosmicPrimaryGlow,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    } else {
                        if (syncError != null) {
                            Text(
                                text = "⚠️ ${syncError}",
                                color = CosmicAccentRose,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        } else if (syncSuccess) {
                            Text(
                                text = "✓ Dados e histórico salvos perfeitamente!",
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        } else {
                            Text(
                                text = "✓ Monitoramento em tempo real ativo",
                                color = CosmicTextSecondary.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.backupToCloud() },
                            colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("Sincronizar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.logoutGoogle() },
                            border = BorderStroke(1.dp, CosmicAccentRose.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmicAccentRose),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("Sair", fontSize = 12.sp)
                        }
                    }
                } else {
                    Text(
                        text = "Vincule uma conta do Google para fazer backups do seu histórico de diálogos socráticos e análises reflexivas. Recupere tudo ao reinstalar.",
                        color = CosmicTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = { showGoogleDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("Vincular Conta Google", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (showGoogleDialog) {
            GoogleSignInDialog(
                onDismiss = { showGoogleDialog = false },
                onConfirm = { email, name ->
                    showGoogleDialog = false
                    viewModel.loginWithGoogle(email, name)
                }
            )
        }

        // --- AI Configuration Panel Card ---
        val aiProvider by viewModel.aiProvider.collectAsState()
        val googleTtsStatusError by viewModel.googleTtsStatusError.collectAsState()

        val hasGeminiSecret = try { BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" } catch (e: Throwable) { false }
        val hasGroqSecret = try { BuildConfig.GROQ_API_KEY.isNotEmpty() && BuildConfig.GROQ_API_KEY != "MY_GROQ_API_KEY" } catch (e: Throwable) { false }
        val hasTtsSecret = try { BuildConfig.GOOGLE_TTS_API_KEY.isNotEmpty() && BuildConfig.GOOGLE_TTS_API_KEY != "MY_GOOGLE_TTS_API_KEY" } catch (e: Throwable) { false }

        Card(
            modifier = Modifier.padding(bottom = 22.dp),
            backgroundColor = Color(0x06FFFFFF),
            borderStrokeColor = CosmicBorder.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "🧠",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = "Cérebro & Configurações de IA",
                        color = CosmicTextPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "As chaves e credenciais de todas as inteligências artificiais são configuradas com total segurança através do painel de Secrets ou no arquivo .env no Google AI Studio. Nenhuma chave é exibida ou inserida no frontend para garantir máxima segurança.",
                    color = CosmicTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                // Status of secrets
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Gemini Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x06FFFFFF), RoundedCornerShape(8.dp))
                            .border(1.dp, CosmicBorder.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🔑 Gemini AI Key",
                            color = CosmicTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (hasGeminiSecret) Color(0x154CAF50) else Color(0x15FFB74D),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (hasGeminiSecret) "Ativo via Secrets" else "Padrão AI Studio",
                                color = if (hasGeminiSecret) Color(0xFF81C784) else Color(0xFFFFB74D),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Groq Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x06FFFFFF), RoundedCornerShape(8.dp))
                            .border(1.dp, CosmicBorder.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "⚡ Groq Cloud Key (Llama 3)",
                            color = CosmicTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (hasGroqSecret) Color(0x154CAF50) else Color(0x15FF5252),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (hasGroqSecret) "Ativo via Secrets" else "Inativo (Pendente)",
                                color = if (hasGroqSecret) Color(0xFF81C784) else Color(0xFFFF5252),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Google TTS Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x06FFFFFF), RoundedCornerShape(8.dp))
                            .border(1.dp, CosmicBorder.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🗣️ Google Cloud TTS",
                            color = CosmicTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (hasTtsSecret) Color(0x154CAF50) else Color(0x15FFB74D),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (hasTtsSecret) "Premium Neural Ativo" else "Voz Robótica Local (Backup)",
                                color = if (hasTtsSecret) Color(0xFF81C784) else Color(0xFFFFB74D),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = CosmicBorder.copy(alpha = 0.25f),
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Text(
                    text = "Alternar Cérebro Ativo:",
                    color = CosmicTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Provider Selector buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val providers = listOf(
                        "gemini" to "Google Gemini 🌌",
                        "groq" to "Groq Cloud ⚡"
                    )
                    providers.forEach { (provId, provLabel) ->
                        val isSelected = aiProvider == provId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) CosmicPrimary.copy(alpha = 0.25f) else Color(0x04FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) CosmicPrimary else CosmicBorder.copy(alpha = 0.2f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.setAiProvider(provId) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = provLabel,
                                color = if (isSelected) CosmicTextPrimary else CosmicTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }

                // Highlight status of High Fidelity TTS API
                if (!googleTtsStatusError.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CosmicAccentRose.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .border(1.dp, CosmicAccentRose.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Aviso: A última tentativa de síntese do Google Cloud TTS falhou com o erro: \"$googleTtsStatusError\". O aplicativo ativou automaticamente a voz robótica local de backup. Certifique-se de que a API 'Cloud Text-to-Speech API' está ativada no projeto GCP desta chave nos Segredos do seu Studio.",
                            color = CosmicAccentRose,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }

        // --- Google Cloud TTS Premium Voice Configuration Card ---
        val geminiVoiceEnabled by viewModel.geminiVoiceEnabled.collectAsState()
        val geminiVoiceName by viewModel.geminiVoiceName.collectAsState()

        Card(
            modifier = Modifier.padding(bottom = 22.dp),
            backgroundColor = if (geminiVoiceEnabled) Color(0x0C7C5FE6) else Color(0x06FFFFFF),
            borderStrokeColor = if (geminiVoiceEnabled) CosmicPrimary.copy(alpha = 0.3f) else CosmicBorder.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "🗣️ ✨",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = "Vozes Premium (Google Cloud TTS Neural2)",
                        color = CosmicTextPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Sintetize vozes incrivelmente realistas, humanas e super expressivas diretamente na nuvem utilizando as melhores redes neurais do Google (Premium Neural2)! Totalmente estável e independente do mecanismo do seu celular.",
                    color = CosmicTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (geminiVoiceEnabled) "Voz Cloud Premium: Ativada ✓" else "Voz Cloud Premium: Desativada (Voz Local)",
                        color = if (geminiVoiceEnabled) Color(0xFF4CAF50) else CosmicTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )

                    Switch(
                        checked = geminiVoiceEnabled,
                        onCheckedChange = { viewModel.setGeminiVoiceEnabled(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = CosmicPrimaryGlow,
                            checkedTrackColor = CosmicPrimary.copy(alpha = 0.4f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                if (geminiVoiceEnabled) {
                    Text(
                        text = "Toque para escolher a identidade da voz:",
                        color = CosmicTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    val voices = listOf(
                        "pt-BR-Neural2-A" to "Feminina A 👩",
                        "pt-BR-Neural2-B" to "Masculina B 👨",
                        "pt-BR-Neural2-C" to "Feminina C 🌸",
                        "pt-BR-Wavenet-D" to "Feminina D 🌻"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        voices.forEach { (voiceId, label) ->
                            val isSelected = geminiVoiceName == voiceId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) CosmicPrimary.copy(alpha = 0.25f) else Color(0x06FFFFFF),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) CosmicPrimary else CosmicBorder.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setGeminiVoiceName(voiceId) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = voiceId.substringAfter("pt-BR-"),
                                        color = if (isSelected) CosmicTextPrimary else CosmicTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                    Text(
                                        text = label,
                                        color = CosmicTextSecondary,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    var testingVoice by remember { mutableStateOf(false) }

                    Button(
                        onClick = {
                            if (testingVoice) return@Button
                            testingVoice = true
                            coroutineScope.launch {
                                try {
                                    GoogleTtsPlayer.stop()
                                    
                                    val testPrompt = "Olá! Esta é uma demonstração de síntese da voz premium no aplicativo Espelho Gêmeo."
                                    var generatedAudio: String? = null
                                    var ttsErrDetail: String? = null

                                    try {
                                        val targetVoice = if (geminiVoiceEnabled) geminiVoiceName else "pt-BR-Neural2-A"
                                        val ttsResult = RetrofitClient.synthesizeText(testPrompt, targetVoice)
                                        val lastTtsError = RetrofitClient.lastTtsError
                                        viewModel.updateGoogleTtsError(lastTtsError)
                                        
                                        if (ttsResult != null) {
                                            generatedAudio = ttsResult
                                        } else {
                                            ttsErrDetail = lastTtsError ?: "Sem resposta"
                                        }
                                    } catch (e: Exception) {
                                        ttsErrDetail = e.message ?: e.toString()
                                    }

                                    if (generatedAudio != null) {
                                        Toast.makeText(context, "Sintetizado com sucesso com Google Cloud TTS!", Toast.LENGTH_SHORT).show()
                                        GoogleTtsPlayer.playBase64Audio(context, generatedAudio, "audio/mp3") {
                                            testingVoice = false
                                        }
                                    } else {
                                        val finalMsg = buildString {
                                            append("Falha na síntese em nuvem.\n")
                                            append("- Google TTS Premium: ${ttsErrDetail ?: "Chave não configurada ou inválida"}\n")
                                            append("Iniciando voz robótica local de backup.")
                                        }
                                        Toast.makeText(context, finalMsg, Toast.LENGTH_LONG).show()
                                        
                                        // Ativa voz do sistema local
                                        viewModel.triggerLocalSpeech(testPrompt)
                                        testingVoice = false
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Falha na conexão: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    testingVoice = false
                                }
                            }
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (testingVoice) CosmicPrimary.copy(alpha = 0.5f) else CosmicPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !testingVoice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        if (testingVoice) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Processando...", color = Color.White, fontSize = 12.sp)
                            }
                        } else {
                            Text("🔊 Testar Voz Atualmente Escolhida", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- Perfil Psicológico Gêmeo Card ---
        val twinProfile by viewModel.twinProfile.collectAsState()

        Card(
            modifier = Modifier.padding(bottom = 22.dp),
            backgroundColor = if (twinProfile.isNotEmpty()) Color(0x0C7C5FE6) else Color(0x06FFFFFF),
            borderStrokeColor = if (twinProfile.isNotEmpty()) CosmicPrimary.copy(alpha = 0.3f) else CosmicBorder.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "🧩",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = "Perfil Psicológico Gêmeo",
                        color = CosmicTextPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (twinProfile.isNotEmpty()) {
                    Text(
                        text = "Este é o seu perfil de comportamento e padrões emocionais acumulado em background pela sua IA Gêmea com base nas sessões e diálogos:",
                        color = CosmicTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Text(
                        text = twinProfile,
                        color = CosmicTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x08FFFFFF), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                } else {
                    Text(
                        text = "Sua IA Gêmea está analisando seus pensamentos e calibrando seu perfil em background. Continue conversando no Chat de Espelho para registrar seus padrões, temas evitados e medos subjacentes (a calibração ocorre de forma automática a cada 4 mensagens trocadas).",
                        color = CosmicTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        // MindCores Interactive Custom Graphics Node
        Card(
            modifier = Modifier.padding(bottom = 22.dp),
            backgroundColor = Color(0x0C7C5FE6),
            borderStrokeColor = CosmicPrimary.copy(alpha = 0.2f)
        ) {
            Label(text = "🧠 MINDCORES — mapa comportamental")
            Spacer(modifier = Modifier.height(10.dp))
            
            // Drawing connected map with canvas
            MindCoresGraph(challenges = chosenChallenges)
        }

        // Previous Sessions History list
        Column(modifier = Modifier.fillMaxWidth()) {
            Label(text = "ÚLTIMOS DIÁLOGOS", color = CosmicTextSecondary.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.height(10.dp))

            if (sessions.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Nenhum diálogo salvo ainda. Comece a conversar para registrar insights.",
                        color = CosmicTextSecondary.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    sessions.take(6).forEach { session ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "💬", fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        color = CosmicTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.SansSerif,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${session.messageCount} interações socráticas",
                                        color = CosmicTextSecondary.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Reset Profile button
        Button(
            onClick = { viewModel.resetProfile() },
            colors = ButtonDefaults.buttonColors(
                containerColor = CosmicAccentRose.copy(alpha = 0.15f),
                contentColor = CosmicAccentRose
            ),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, CosmicAccentRose.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("reset_profile_button")
        ) {
            Text(
                text = "Recomeçar & Apagar Diálogos",
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun MindCoresGraph(challenges: List<Challenge>) {
    val infiniteTransition = rememberInfiniteTransition(label = "graph_oscillation")
    
    // Create animated oscillation representing neural activity/pulsing
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "osc"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f

            // Calculate connected outer node positions based on selected challenges count
            val points = ArrayList<Offset>()
            val maxNodes = challenges.size.coerceAtMost(3)
            val angles = when (maxNodes) {
                1 -> listOf(270f)
                2 -> listOf(210f, 330f)
                else -> listOf(270f, 150f, 30f) // 3 nodes spread
            }

            for (i in 0 until maxNodes) {
                val angleRad = Math.toRadians((angles[i] + scaleFactor).toDouble())
                val radius = 55.dp.toPx()
                val px = centerX + (radius * cos(angleRad)).toFloat()
                val py = centerY + (radius * sin(angleRad)).toFloat()
                points.add(Offset(px, py))
            }

            // Draw line connections with dashes representing active neural synapses
            points.forEach { pt ->
                drawLine(
                    color = CosmicPrimary.copy(alpha = 0.28f),
                    start = Offset(centerX, centerY),
                    end = pt,
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            // Central Core representation node (User)
            drawCircle(
                color = CosmicPrimary.copy(alpha = 0.15f),
                radius = 28.dp.toPx(),
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = CosmicPrimary,
                radius = 18.dp.toPx(),
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = CosmicPrimaryGlow,
                radius = 6.dp.toPx(),
                center = Offset(centerX, centerY)
            )

            // Outer representation nodes representing Selected challenges
            points.forEachIndexed { i, pt ->
                drawCircle(
                    color = CosmicSecondary.copy(alpha = 0.12f),
                    radius = 18.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = CosmicSecondary,
                    radius = 12.dp.toPx(),
                    center = pt,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }

        // Orbit overlays (floating texts overlayed using coordinates inside Compose Layout)
        Box(modifier = Modifier.fillMaxSize()) {
            // "VOCÊ" tag floating at center
            Text(
                text = "VOCÊ",
                color = CosmicTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 26.dp)
            )

            // Floating tags on outer orbits
            val maxNodes = challenges.size.coerceAtMost(3)
            val alignments = when (maxNodes) {
                1 -> listOf(Alignment.TopCenter)
                2 -> listOf(Alignment.BottomStart, Alignment.BottomEnd)
                else -> listOf(Alignment.TopCenter, Alignment.BottomStart, Alignment.BottomEnd)
            }

            val offsets = when (maxNodes) {
                1 -> listOf(DpOffset(0.dp, 10.dp))
                2 -> listOf(DpOffset(20.dp, (-15).dp), DpOffset((-20).dp, (-15).dp))
                else -> listOf(DpOffset(0.dp, 10.dp), DpOffset(20.dp, (-5).dp), DpOffset((-20).dp, (-5).dp))
            }

            for (i in 0 until maxNodes) {
                val challenge = challenges[i]
                val alignment = alignments[i]
                val offset = offsets[i]

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentAlignment = alignment
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(x = offset.x, y = offset.y)
                    ) {
                        Text(text = challenge.emoji, fontSize = 20.sp)
                        Text(
                            text = challenge.label,
                            color = CosmicTextPrimary.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

data class DpOffset(val x: Dp, val y: Dp)

// ==================== PLAN TAB (Weekly Growth Plan) ====================

@Composable
fun PlanTab(viewModel: EspelhoViewModel) {
    val plan by viewModel.weeklyPlan.collectAsState()
    val loading by viewModel.planLoading.collectAsState()
    val habits by viewModel.habitChecks.collectAsState()

    val completedHabits = habits.size
    val totalHabits = plan?.daily_habits?.size ?: 0

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp)
            .padding(top = 18.dp, bottom = 100.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 22.dp)
        ) {
            Text(text = "🗺️", fontSize = 32.sp, modifier = Modifier.padding(end = 12.dp))
            Column {
                Text(
                    text = "Plano de Crescimento",
                    color = CosmicTextPrimary,
                    fontSize = 21.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Roteiro semanal gerado especialmente para você",
                    color = CosmicTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        if (plan == null && !loading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(text = "🗺️", fontSize = 48.sp)
                Text(
                    text = "Sua IA gêmea vai criar um plano semanal personalizado com hábitos e metas baseados no seu perfil.",
                    color = CosmicTextSecondary.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.widthIn(max = 280.dp)
                )
                Button(
                    onClick = { viewModel.generateWeeklyPlan() },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("generate_plan_btn")
                ) {
                    Text(text = "✨ Gerar meu plano", fontSize = 15.sp, color = Color.White)
                }
            }
        }

        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Orb(size = 52.dp, anim = "pulse", glow = true)
                Text(
                    text = "Criando seu plano personalizado...",
                    color = CosmicTextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        if (plan != null && !loading) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header do plano
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = CosmicPrimary.copy(alpha = 0.15f),
                    borderStrokeColor = CosmicPrimary.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = "SEU PLANO DESTA SEMANA",
                        color = CosmicPrimaryGlow,
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = plan!!.title,
                        color = CosmicTextPrimary,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = plan!!.focus,
                        color = CosmicTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }

                // Daily habits checklist
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    borderStrokeColor = CosmicPrimary.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Label(text = "🌅 HÁBITOS DIÁRIOS", modifier = Modifier.padding(bottom = 0.dp))
                        Text(
                            text = "$completedHabits de $totalHabits hoje",
                            color = CosmicPrimaryGlow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (totalHabits > 0) {
                        Bar(
                            value = (completedHabits.toFloat() / totalHabits) * 100,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }

                    plan!!.daily_habits.forEachIndexed { i, h ->
                        val checked = habits.contains(i)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (checked) CosmicPrimary.copy(alpha = 0.2f) else Color(0x08FFFFFF))
                                .border(1.dp, if (checked) CosmicPrimary.copy(alpha = 0.4f) else CosmicPrimary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .clickable { viewModel.toggleHabit(i) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Safe customized checkbox indicator
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (checked) CosmicPrimary else Color.Transparent)
                                    .border(2.dp, if (checked) CosmicPrimary else CosmicPrimaryGlow.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (checked) {
                                    Text(text = "✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(text = h.emoji, fontSize = 16.sp)
                                        Text(
                                            text = h.habit,
                                            color = if (checked) CosmicTextSecondary else CosmicTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            style = androidx.compose.ui.text.TextStyle(
                                                textDecoration = if (checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                            )
                                        )
                                    }
                                    Text(
                                        text = h.duration,
                                        color = CosmicPrimaryGlow.copy(alpha = 0.5f),
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = h.why,
                                    color = CosmicTextSecondary.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Weekly goals
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    borderStrokeColor = CosmicPrimary.copy(alpha = 0.16f)
                ) {
                    Label(text = "🎯 METAS DA SEMANA", modifier = Modifier.padding(bottom = 12.dp))
                    plan!!.weekly_goals.forEachIndexed { i, g ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (i < plan!!.weekly_goals.size - 1) 12.dp else 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(text = g.emoji, fontSize = 18.sp)
                                Text(
                                    text = g.goal,
                                    color = CosmicTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "→ ${g.action}",
                                color = CosmicTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(start = 26.dp)
                            )
                        }
                        if (i < plan!!.weekly_goals.size - 1) {
                            HorizontalDivider(
                                color = CosmicPrimary.copy(alpha = 0.1f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Custom Reflection details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = CosmicPrimary.copy(alpha = 0.08f),
                    borderStrokeColor = CosmicPrimary.copy(alpha = 0.22f)
                ) {
                    Label(text = "💭 REFLEXÃO DA SEMANA", modifier = Modifier.padding(bottom = 8.dp))
                    Text(
                        text = "\"${plan!!.reflection_prompt}\"",
                        color = CosmicTextPrimary,
                        fontSize = 14.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    HorizontalDivider(color = CosmicPrimary.copy(alpha = 0.15f), modifier = Modifier.padding(bottom = 12.dp))
                    Text(
                        text = "✨ ${plan!!.affirmation}",
                        color = CosmicPrimaryGlow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Regenerate Growth Plan
                OutlinedButton(
                    onClick = { viewModel.generateWeeklyPlan() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmicTextSecondary)
                ) {
                    Text(text = "🔄 Gerar novo plano", fontSize = 12.sp)
                }
            }
        }
    }
}
