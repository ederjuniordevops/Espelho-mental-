package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.RetrofitClient
import com.example.data.*
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    ONBOARDING,
    MAIN_HUB
}

enum class ActiveTab {
    HOME,
    CHAT,
    DECIDE,
    PLAN,
    SESSIONS,
    READINGS,
    PROFILE
}

@JsonClass(generateAdapter = true)
data class DecisionFactor(
    val name: String,
    val weight: Int,
    val forA: String,
    val forB: String
)

@JsonClass(generateAdapter = true)
data class DecisionResult(
    val summary: String,
    val factors: List<DecisionFactor>,
    val emotional_block: String,
    val recommendation: String,
    val score_a: Int,
    val score_b: Int,
    val key_question: String
)

class EspelhoViewModel(application: Application, private val repository: AppRepository) : AndroidViewModel(application) {

    // --- SharedPreferences for local settings like API Key ---
    private val prefs = application.getSharedPreferences("espelho_prefs", Context.MODE_PRIVATE)

    private val _customApiKey = MutableStateFlow(prefs.getString("custom_api_key", "") ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    fun setCustomApiKey(key: String) {
        val cleaned = key.trim()
        prefs.edit().putString("custom_api_key", cleaned).apply()
        _customApiKey.value = cleaned
        RetrofitClient.customApiKey = cleaned
        // If we updated the key and there was a prior networking error message on the reflection tab, clear it to invite a retry
        if (_dailyReflectionText.value == "Não foi possível carregar a reflexão diária hoje.") {
            _dailyReflectionText.value = ""
        }
    }

    private val _aiProvider = MutableStateFlow(prefs.getString("ai_provider", "groq") ?: "groq")
    val aiProvider: StateFlow<String> = _aiProvider.asStateFlow()

    fun setAiProvider(provider: String) {
        prefs.edit().putString("ai_provider", provider).apply()
        _aiProvider.value = provider
        RetrofitClient.aiProvider = provider
        // Clear reflection error to invite a retry on provider change
        if (_dailyReflectionText.value == "Não foi possível carregar a reflexão diária hoje.") {
            _dailyReflectionText.value = ""
        }
    }

    private val _geminiVoiceEnabled = MutableStateFlow(prefs.getBoolean("gemini_voice_enabled", true))
    val geminiVoiceEnabled: StateFlow<Boolean> = _geminiVoiceEnabled.asStateFlow()

    fun setGeminiVoiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("gemini_voice_enabled", enabled).apply()
        _geminiVoiceEnabled.value = enabled
    }

    private val _geminiVoiceName = MutableStateFlow(
        prefs.getString("gemini_voice_name", "pt-BR-Neural2-A")?.takeIf {
            it in listOf("pt-BR-Neural2-A", "pt-BR-Neural2-B", "pt-BR-Neural2-C", "pt-BR-Wavenet-D")
        } ?: "pt-BR-Neural2-A"
    )
    val geminiVoiceName: StateFlow<String> = _geminiVoiceName.asStateFlow()

    fun setGeminiVoiceName(name: String) {
        prefs.edit().putString("gemini_voice_name", name).apply()
        _geminiVoiceName.value = name
    }

    private val _geminiAudioTrigger = MutableSharedFlow<Pair<String, String>>(replay = 0)
    val geminiAudioTrigger: SharedFlow<Pair<String, String>> = _geminiAudioTrigger.asSharedFlow()

    private val _groqApiKey = MutableStateFlow(prefs.getString("groq_api_key", "") ?: "")
    val groqApiKey: StateFlow<String> = _groqApiKey.asStateFlow()

    fun setGroqApiKey(key: String) {
        val cleaned = key.trim()
        prefs.edit().putString("groq_api_key", cleaned).apply()
        _groqApiKey.value = cleaned
        RetrofitClient.groqApiKey = cleaned
        // Clear reflection error to invite a retry on key change
        if (_dailyReflectionText.value == "Não foi possível carregar a reflexão diária hoje.") {
            _dailyReflectionText.value = ""
        }
    }

    private val _googleTtsApiKey = MutableStateFlow(prefs.getString("google_tts_api_key", "") ?: "")
    val googleTtsApiKey: StateFlow<String> = _googleTtsApiKey.asStateFlow()

    fun setGoogleTtsApiKey(key: String) {
        val cleaned = key.trim()
        prefs.edit().putString("google_tts_api_key", cleaned).apply()
        _googleTtsApiKey.value = cleaned
        RetrofitClient.googleTtsApiKey = cleaned
    }

    private val _googleTtsStatusError = MutableStateFlow<String?>(null)
    val googleTtsStatusError: StateFlow<String?> = _googleTtsStatusError.asStateFlow()

    // --- State Observables ---
    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allSessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Navigation State ---
    private val _currentScreen = MutableStateFlow(AppScreen.ONBOARDING)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _currentTab = MutableStateFlow(ActiveTab.HOME)
    val currentTab: StateFlow<ActiveTab> = _currentTab.asStateFlow()

    // --- Onboarding Flow ---
    val selectedChallenges = MutableStateFlow<Set<String>>(emptySet())
    val selectedDecisionStyle = MutableStateFlow<String?>(null)
    val userNameInput = MutableStateFlow("")
    val onboardingProgress = MutableStateFlow(0)
    val isOnboardingActivating = MutableStateFlow(false)

    // --- Daily Reflection State ---
    private val _dailyReflectionText = MutableStateFlow("")
    val dailyReflectionText: StateFlow<String> = _dailyReflectionText.asStateFlow()

    private val _dailyReflectionLoading = MutableStateFlow(false)
    val dailyReflectionLoading: StateFlow<Boolean> = _dailyReflectionLoading.asStateFlow()

    // --- Live Chat State ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatThinking = MutableStateFlow(false)
    val chatThinking: StateFlow<Boolean> = _chatThinking.asStateFlow()

    private val _voiceModeActive = MutableStateFlow(prefs.getBoolean("voice_mode_active", false))
    val voiceModeActive: StateFlow<Boolean> = _voiceModeActive.asStateFlow()

    private val _twinProfile = MutableStateFlow(prefs.getString("twin_profile", "") ?: "")
    val twinProfile: StateFlow<String> = _twinProfile.asStateFlow()

    private val _speechTextTrigger = MutableSharedFlow<String>(replay = 0)
    val speechTextTrigger: SharedFlow<String> = _speechTextTrigger.asSharedFlow()

    // --- Weekly Growth Plan State ---
    private val _weeklyPlan = MutableStateFlow<WeeklyGrowthPlan?>(null)
    val weeklyPlan: StateFlow<WeeklyGrowthPlan?> = _weeklyPlan.asStateFlow()

    private val _habitChecks = MutableStateFlow<Set<Int>>(emptySet())
    val habitChecks: StateFlow<Set<Int>> = _habitChecks.asStateFlow()

    private val _planLoading = MutableStateFlow(false)
    val planLoading: StateFlow<Boolean> = _planLoading.asStateFlow()

    fun getSavedWeeklyPlan(): WeeklyGrowthPlan? {
        val json = prefs.getString("weekly_plan_json", null) ?: return null
        return try {
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(WeeklyGrowthPlan::class.java)
            adapter.fromJson(json)
        } catch (e: Exception) {
            android.util.Log.e("EspelhoVM", "Error parsing saved plan", e)
            null
        }
    }

    fun getSavedHabitChecks(): Set<Int> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDate = prefs.getString("habit_checks_date", "")
        if (savedDate != today) {
            prefs.edit().putString("habit_checks_indices", "").putString("habit_checks_date", today).apply()
            return emptySet()
        }
        val csv = prefs.getString("habit_checks_indices", "") ?: ""
        if (csv.isEmpty()) return emptySet()
        return csv.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun toggleHabit(index: Int) {
        val current = _habitChecks.value.toMutableSet()
        if (current.contains(index)) {
            current.remove(index)
        } else {
            current.add(index)
        }
        _habitChecks.value = current
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefs.edit()
            .putString("habit_checks_indices", current.joinToString(","))
            .putString("habit_checks_date", today)
            .apply()
    }

    fun getPlanSystemPrompt(profile: UserProfileEntity?, twinProfile: String): String {
        val challenges = if (profile != null) {
            val challengeIds = profile.selectedChallenges.split(",").filter { it.isNotEmpty() }
            challengeIds.mapNotNull { id ->
                StaticData.CHALLENGES.find { it.id == id }?.label
            }.joinToString(", ")
        } else {
            "crescimento geral"
        }
        val twin = if (twinProfile.isNotEmpty()) "\n\nO que já sei sobre essa pessoa: $twinProfile" else ""
        return """
            Você cria Planos de Crescimento Pessoal personalizados.$twin

            Nome: ${profile?.name ?: "Usuário"}. Desafios: ${challenges.ifEmpty { "crescimento geral" }}.

            Crie um plano semanal prático e personalizado. Retorne APENAS JSON válido em formato raw de texto sem explicações extras:
            {
              "title": "Título motivador do plano (ex: Semana da Clareza)",
              "focus": "Uma frase sobre o foco dessa semana",
              "daily_habits": [
                {"emoji":"🌅","habit":"Nome do hábito","duration":"5 min","why":"Por que esse hábito ajuda"}
              ],
              "weekly_goals": [
                {"emoji":"🎯","goal":"Meta específica da semana","action":"Ação concreta para atingir"}
              ],
              "reflection_prompt": "Uma pergunta para reflexão semanal",
              "affirmation": "Uma afirmação personalizada curta"
            }

            Inclua 3-4 hábitos diários e 2-3 metas semanais. Em português.
        """.trimIndent()
    }

    fun generateWeeklyPlan() {
        viewModelScope.launch {
            _planLoading.value = true
            try {
                val profile = repository.getUserProfileSync()
                val twinProfileVal = _twinProfile.value
                val sysPrompt = getPlanSystemPrompt(profile, twinProfileVal)

                val rawJson = RetrofitClient.callGemini(
                    prompt = "Gere meu plano de crescimento semanal.",
                    systemInstruction = sysPrompt,
                    jsonMode = true
                )

                val cleanedJson = rawJson.replace("```json", "").replace("```", "").trim()

                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(WeeklyGrowthPlan::class.java)
                val parsed = adapter.fromJson(cleanedJson)
                if (parsed != null) {
                    _weeklyPlan.value = parsed
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    _habitChecks.value = emptySet()

                    prefs.edit()
                        .putString("weekly_plan_json", cleanedJson)
                        .putString("weekly_plan_date", today)
                        .putString("habit_checks_indices", "")
                        .putString("habit_checks_date", today)
                        .apply()
                }
            } catch (e: Exception) {
                android.util.Log.e("EspelhoVM", "Error generating weekly plan: ${e.message}", e)
            } finally {
                _planLoading.value = false
            }
        }
    }

    private var activeChatSessionId: String? = null

    // --- Google Cloud Sync State ---
    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _cloudSyncError = MutableStateFlow<String?>(null)
    val cloudSyncError: StateFlow<String?> = _cloudSyncError.asStateFlow()

    private val _cloudSyncSuccess = MutableStateFlow(false)
    val cloudSyncSuccess: StateFlow<Boolean> = _cloudSyncSuccess.asStateFlow()

    // --- Decidir Tab State ---
    val deciderInputDecision = MutableStateFlow("")
    val deciderOptionA = MutableStateFlow("")
    val deciderOptionB = MutableStateFlow("")
    val deciderView = MutableStateFlow("input") // "input", "options", "result"
    val deciderLoading = MutableStateFlow(false)
    val deciderResult = MutableStateFlow<DecisionResult?>(null)

    // --- Sessions Tab State ---
    val selectedSessionTopic = MutableStateFlow<SessionTopic?>(null)
    private val _sessionMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val sessionMessages: StateFlow<List<ChatMessage>> = _sessionMessages.asStateFlow()
    val sessionThinking = MutableStateFlow(false)

    // --- Readings Tab State ---
    val selectedReadingTopic = MutableStateFlow<ReadingTopic?>(null)
    val readingText = MutableStateFlow("")
    val readingLoading = MutableStateFlow(false)

    init {
        // Load stored custom API key and provider configuration into RetrofitClient on start
        RetrofitClient.customApiKey = _customApiKey.value
        RetrofitClient.groqApiKey = _groqApiKey.value
        RetrofitClient.googleTtsApiKey = _googleTtsApiKey.value
        RetrofitClient.aiProvider = _aiProvider.value

        // Load weekly growth plan and habit checks on init
        _weeklyPlan.value = getSavedWeeklyPlan()
        _habitChecks.value = getSavedHabitChecks()

        // Evaluate initially if user profile is already in SQLite
        viewModelScope.launch {
            repository.userProfile.collect { profile ->
                if (profile != null) {
                    _currentScreen.value = AppScreen.MAIN_HUB
                    val today = getTodayDateString()
                    if (profile.dailyReflectionDate == today && !profile.dailyReflection.isNullOrEmpty()) {
                        _dailyReflectionText.value = profile.dailyReflection
                    } else {
                        _dailyReflectionText.value = ""
                    }
                } else {
                    _currentScreen.value = AppScreen.ONBOARDING
                }
            }
        }
    }

    // --- Navigation Commands ---
    fun setTab(tab: ActiveTab, initialChatPrompt: String? = null) {
        _currentTab.value = tab
        if (tab == ActiveTab.CHAT && initialChatPrompt != null) {
            startChatWithPrompt(initialChatPrompt)
        }
    }

    // --- Onboarding Logic ---
    fun toggleChallenge(id: String) {
        val current = selectedChallenges.value
        if (current.contains(id)) {
            selectedChallenges.value = current - id
        } else {
            if (current.size < 3) {
                selectedChallenges.value = current + id
            }
        }
    }

    fun setDecisionStyle(styleId: String) {
        selectedDecisionStyle.value = styleId
    }

    fun activateIA() {
        isOnboardingActivating.value = true
        viewModelScope.launch {
            for (p in 0..100 step 2) {
                onboardingProgress.value = p
                kotlinx.coroutines.delay(20)
            }
            kotlinx.coroutines.delay(400)

            val name = userNameInput.value.trim().ifEmpty { "você" }
            val challengesString = selectedChallenges.value.joinToString(",")
            val style = selectedDecisionStyle.value

            val newProfile = UserProfileEntity(
                name = name,
                selectedChallenges = challengesString,
                decisionStyle = style,
                streak = 1,
                lastActiveDate = getTodayDateString()
            )

            repository.insertUserProfile(newProfile)
            _currentScreen.value = AppScreen.MAIN_HUB
            _currentTab.value = ActiveTab.HOME
            isOnboardingActivating.value = false
        }
    }

    // --- Daily Reflection Management ---
    fun generateDailyReflection() {
        if (_dailyReflectionLoading.value) return
        viewModelScope.launch {
            val profile = repository.getUserProfileSync() ?: return@launch
            val today = getTodayDateString()

            _dailyReflectionLoading.value = true
            _dailyReflectionText.value = "" // Clear any prior error text

            val chalList = profile.selectedChallenges.split(",")
                .mapNotNull { chId -> StaticData.CHALLENGES.find { it.id == chId }?.label }
                .joinToString(", ")
                .ifEmpty { "autoconhecimento e crescimento" }

            val profileCtx = if (_twinProfile.value.isNotEmpty()) "\nO que já sei sobre essa pessoa: ${_twinProfile.value}" else ""
            val systemInstruction = "Você gera uma Reflexão Diária personalizada.$profileCtx\nDesafios: $chalList.\nEscreva UMA pergunta reflexiva DIFERENTE e específica — evite perguntas genéricas. Deve parecer que foi escrita especificamente para essa pessoa. Em português. Apenas o texto da reflexão, sem aspas."
            val prompt = "Gere."

            try {
                val result = RetrofitClient.callGemini(prompt, systemInstruction)
                val finalReflectionText = result.trim().replace("\"", "")

                _dailyReflectionText.value = finalReflectionText

                // Update SQLite
                val updatedProfile = profile.copy(
                    dailyReflection = finalReflectionText,
                    dailyReflectionDate = today
                )
                repository.insertUserProfile(updatedProfile)
            } catch (e: Exception) {
                e.printStackTrace()
                _dailyReflectionText.value = "Não foi possível carregar a reflexão diária hoje."
            } finally {
                _dailyReflectionLoading.value = false
            }
        }
    }

    // --- Live Chat Methods ---
    private fun startChatWithPrompt(prompt: String) {
        _chatMessages.value = emptyList()
        activeChatSessionId = "chat_" + System.currentTimeMillis()
        _chatThinking.value = true

        viewModelScope.launch {
            val profile = repository.getUserProfileSync() ?: return@launch
            val chalList = profile.selectedChallenges.split(",")
                .mapNotNull { chId -> StaticData.CHALLENGES.find { it.id == chId }?.label }
                .joinToString(", ")

            val system = buildChatSystem(profile.name, chalList, profile.decisionStyle, _twinProfile.value)
            val apiPrompt = "Responda à seguinte reflexão com curiosidade genuína e uma pergunta que aprofunde: \"$prompt\""

            val reply = RetrofitClient.callGemini(
                prompt = apiPrompt,
                systemInstruction = system,
                voiceEnabled = false
            )
            _chatMessages.value = listOf(
                ChatMessage("user", prompt),
                ChatMessage("assistant", reply)
            )
            _chatThinking.value = false

            triggerSpeechIfNeeded(reply)

            saveChatSessionToDatabase()
        }
    }

    fun initLiveChatIfNeeded() {
        if (_chatMessages.value.isNotEmpty() || _chatThinking.value) return

        activeChatSessionId = "chat_" + System.currentTimeMillis()

        viewModelScope.launch {
            val profile = repository.getUserProfileSync() ?: return@launch
            val chalList = profile.selectedChallenges.split(",")
                .mapNotNull { chId -> StaticData.CHALLENGES.find { it.id == chId }?.label }
                .joinToString(", ")

            val greeting = "Olá, ${profile.name}! Sou o seu Espelho Gêmeo. Estou aqui para ajudar você a refletir profundamente e encontrar clareza diante de seus desafios diários (${chalList}). Como você está se sentindo hoje? O que gostaria de explorar?"
            _chatMessages.value = listOf(ChatMessage("assistant", greeting))
            saveChatSessionToDatabase()
        }
    }

    fun sendChatMessage(text: String) {
        val currentMsg = text.trim()
        if (currentMsg.isEmpty() || _chatThinking.value) return

        val userMsg = ChatMessage("user", currentMsg)
        val listAfterUser = _chatMessages.value + userMsg
        _chatMessages.value = listAfterUser
        _chatThinking.value = true

        viewModelScope.launch {
            val profile = repository.getUserProfileSync() ?: return@launch
            val chalList = profile.selectedChallenges.split(",")
                .mapNotNull { chId -> StaticData.CHALLENGES.find { it.id == chId }?.label }
                .joinToString(", ")

            val system = buildChatSystem(profile.name, chalList, profile.decisionStyle, _twinProfile.value)

            // Pack conversation history for direct API
            val historyPrompt = listAfterUser.joinToString("\n") { "${it.role}: ${it.content}" } + "\nassistant:"

            val reply = RetrofitClient.callGemini(
                prompt = historyPrompt,
                systemInstruction = system,
                voiceEnabled = false
            )
            val finalMessages = listAfterUser + ChatMessage("assistant", reply)

            _chatMessages.value = finalMessages
            _chatThinking.value = false

            triggerSpeechIfNeeded(reply)

            saveChatSessionToDatabase()

            // Update psychological twin profile background extractor if user messages is multi of 4
            val userMsgCount = finalMessages.count { it.role == "user" }
            if (userMsgCount > 0 && userMsgCount % 4 == 0) {
                launchProfileExtraction(finalMessages)
            }
        }
    }

    fun launchProfileExtraction(messages: List<ChatMessage>) {
        viewModelScope.launch {
            val existingProfile = _twinProfile.value
            val conv = messages.joinToString("\n\n") { "${if (it.role == "user") "PESSOA" else "IA"}: ${it.content}" }

            val prompt = if (existingProfile.isNotEmpty()) {
                "Perfil anterior:\n$existingProfile\n\nNova conversa:\n$conv\n\nAtualize e expanda o perfil com as novas informações."
            } else {
                "Conversa:\n$conv\n\nGere o perfil inicial."
            }

            try {
                val updated = RetrofitClient.callGemini(prompt, PROFILE_EXTRACTION_SYSTEM)
                val trimmed = updated.trim()
                if (trimmed.isNotEmpty() && trimmed != "Perfil insuficiente — mais sessões necessárias.") {
                    _twinProfile.value = trimmed
                    prefs.edit().putString("twin_profile", trimmed).apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleVoiceMode() {
        val next = !_voiceModeActive.value
        prefs.edit().putBoolean("voice_mode_active", next).apply()
        _voiceModeActive.value = next
    }

    private fun triggerSpeechIfNeeded(text: String) {
        if (_voiceModeActive.value) {
            viewModelScope.launch {
                var audioBase64: String? = null
                var audioMime: String? = null

                // 1. Google Cloud TTS Premium Neural2 (se ativo)
                if (_geminiVoiceEnabled.value) {
                    try {
                        val voiceVal = _geminiVoiceName.value
                        // Chamada direta para síntese no Google Cloud TTS
                        audioBase64 = RetrofitClient.synthesizeText(text, voiceVal)
                        audioMime = "audio/mp3"
                        _googleTtsStatusError.value = RetrofitClient.lastTtsError
                    } catch (e: Exception) {
                        android.util.Log.e("EspelhoVM", "Falha ao sintetizar com Cloud TTS Premium: ${e.message}")
                        _googleTtsStatusError.value = e.message ?: e.toString()
                    }
                }

                if (audioBase64 != null && audioMime != null) {
                    // Toca a voz de alta fidelidade e hiper-realista do Google Cloud TTS Premium (MP3)
                    _geminiAudioTrigger.emit(Pair(audioBase64, audioMime))
                } else {
                    // 2. Fallback final para a voz robótica local (Android TTS) do sistema
                    _speechTextTrigger.emit(text)
                }
            }
        }
    }

    fun updateGoogleTtsError(error: String?) {
        _googleTtsStatusError.value = error
    }

    fun triggerLocalSpeech(text: String) {
        viewModelScope.launch {
            _speechTextTrigger.emit(text)
        }
    }

    private suspend fun saveChatSessionToDatabase() {
        val sId = activeChatSessionId ?: return
        val currentHistory = _chatMessages.value
        if (currentHistory.isEmpty()) return

        val previewTitle = currentHistory.firstOrNull { it.role == "user" }?.content?.take(40)?.plus("...")
            ?: "Sessão reflexiva"

        val serialized = JsonHelpers.serializeMessages(currentHistory)

        val entity = ChatSessionEntity(
            id = sId,
            date = ISO_FORMAT.format(Date()),
            title = previewTitle,
            messageCount = currentHistory.filter { it.role == "user" }.size,
            messagesJson = serialized,
            type = "chat"
        )
        repository.insertSession(entity)
        backupToCloud()
    }

    // --- Decidir Logic ---
    fun resetDecider() {
        deciderInputDecision.value = ""
        deciderOptionA.value = ""
        deciderOptionB.value = ""
        deciderResult.value = null
        deciderView.value = "input"
    }

    fun analyzeDecision() {
        deciderView.value = "result"
        deciderLoading.value = true

        val decisionText = deciderInputDecision.value.trim()
        val optionA = deciderOptionA.value.ifEmpty { "Seguir em frente" }
        val optionB = deciderOptionB.value.ifEmpty { "Manter o status quo" }

        viewModelScope.launch {
            val system = """
                Você é um analisador de decisões profundamente perspicaz e empático. Retorne APENAS um JSON estrito, sem markdown, sem caixas de texto extras:
                {
                  "summary": "Uma frase reencadrando a decisão",
                  "factors": [{"name": "Nome do fator", "weight": 1-10, "forA": "Favorece A", "forB": "Favorece B"}],
                  "emotional_block": "O padrão emocional ou medo que provavelmente os trava de decidir (1-2 frases)",
                  "recommendation": "Recomendação honesta e realista baseada nos pesos com uma razão breve (2-3 frases)",
                  "score_a": 0-100,
                  "score_b": 0-100,
                  "key_question": "Uma pergunta de reencaminhamento socrático poderosa"
                }
                Em português do Brasil (pt-BR). Retorne 3 ou 4 fatores fundamentais relevantes.
            """.trimIndent()

            val textPrompt = "Decisão: \"$decisionText\"\nOpção A: \"$optionA\"\nOpção B: \"$optionB\""

            val rawResult = RetrofitClient.callGemini(textPrompt, system, jsonMode = true)

            try {
                // Parsing utilizing Moshi
                val cleanedJson = rawResult.trim()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(DecisionResult::class.java)
                val parsed = adapter.fromJson(cleanedJson)

                deciderResult.value = parsed
            } catch (e: Exception) {
                e.printStackTrace()
                // Provide high quality fallback values
                deciderResult.value = DecisionResult(
                    summary = "Análise refinada de: $decisionText",
                    factors = listOf(
                        DecisionFactor("Crescimento pessoal", 8, "Expõe a novas oportunidades", "Garante segurança imediata"),
                        DecisionFactor("Nível de estresse", 6, "Exige esforço adaptativo inicial", "Mantém a rotina estável")
                    ),
                    emotional_block = "Lidar com o desconhecido gera hesitação e estimula a procrastinar a decisão sob o pretexto de buscar mais informações.",
                    recommendation = "Ambas as opções carregam pesos construtivos. Considere que a incerteza inicial da Opção A traz aprendizados valiosos, enquanto a preservação da Opção B oferece sustentação no curto prazo.",
                    score_a = 55,
                    score_b = 45,
                    key_question = "Do que você realmente abriria mão se deixasse de lado sua opção ideal?"
                )
            } finally {
                deciderLoading.value = false
            }
        }
    }

    // --- Guided Sessions ---
    fun selectSessionTopic(topic: SessionTopic) {
        selectedSessionTopic.value = topic
        _sessionMessages.value = emptyList()
        sessionThinking.value = true

        viewModelScope.launch {
            val profile = repository.getUserProfileSync() ?: return@launch
            val chalList = profile.selectedChallenges.split(",")
                .mapNotNull { chId -> StaticData.CHALLENGES.find { it.id == chId }?.label }
                .joinToString(", ")
            val system = buildSessionSystem(topic.label, profile.name, chalList, profile.decisionStyle, _twinProfile.value)

            val reply = RetrofitClient.callGemini(
                prompt = "Inicie a sessão guiada sobre \"${topic.label}\" para ${profile.name}.",
                systemInstruction = system,
                voiceEnabled = false
            )
            _sessionMessages.value = listOf(ChatMessage("assistant", reply))
            sessionThinking.value = false

            triggerSpeechIfNeeded(reply)
        }
    }

    fun sendSessionMessage(text: String) {
        val msg = text.trim()
        val topic = selectedSessionTopic.value ?: return
        if (msg.isEmpty() || sessionThinking.value) return

        val userMessage = ChatMessage("user", msg)
        val afterUser = _sessionMessages.value + userMessage
        _sessionMessages.value = afterUser
        sessionThinking.value = true

        viewModelScope.launch {
            val profile = repository.getUserProfileSync() ?: return@launch
            val chalList = profile.selectedChallenges.split(",")
                .mapNotNull { chId -> StaticData.CHALLENGES.find { it.id == chId }?.label }
                .joinToString(", ")
            val system = buildSessionSystem(topic.label, profile.name, chalList, profile.decisionStyle, _twinProfile.value)

            val historyPrompt = afterUser.joinToString("\n") { "${it.role}: ${it.content}" } + "\nassistant:"

            val reply = RetrofitClient.callGemini(
                prompt = historyPrompt,
                systemInstruction = system,
                voiceEnabled = false
            )
            _sessionMessages.value = afterUser + ChatMessage("assistant", reply)
            sessionThinking.value = false

            triggerSpeechIfNeeded(reply)
        }
    }

    fun exitSession() {
        selectedSessionTopic.value = null
        _sessionMessages.value = emptyList()
        sessionThinking.value = false
    }

    // --- Deep Readings ---
    fun selectReadingTopic(topic: ReadingTopic) {
        selectedReadingTopic.value = topic
        readingText.value = ""
        readingLoading.value = true

        viewModelScope.launch {
            val profile = repository.getUserProfileSync() ?: return@launch
            val system = buildReadingSystem(topic.label, profile.name, _twinProfile.value)

            val response = RetrofitClient.callGemini(
                "Escreva o ensaio inspirador personalizado sobre \"${topic.label}\" para ${profile.name}.",
                system
            )
            readingText.value = response
            readingLoading.value = false
        }
    }

    fun exitReading() {
        selectedReadingTopic.value = null
        readingText.value = ""
    }

    // --- Profile commands ---
    fun resetProfile() {
        viewModelScope.launch {
            repository.clearAllData()
            selectedChallenges.value = emptySet()
            selectedDecisionStyle.value = null
            userNameInput.value = ""
            _chatMessages.value = emptyList()
            _dailyReflectionText.value = ""
            prefs.edit().putString("twin_profile", "").apply()
            _twinProfile.value = ""
            resetDecider()
            _currentScreen.value = AppScreen.ONBOARDING
        }
    }

    // --- Google Cloud Synchronization ---
    fun loginWithGoogle(email: String, name: String) {
        viewModelScope.launch {
            val currentProfile = repository.getUserProfileSync()
            val finalName = if (name.isNotEmpty()) name else (currentProfile?.name ?: "Usuário")
            
            val updatedProfile = UserProfileEntity(
                id = 1,
                name = finalName,
                selectedChallenges = currentProfile?.selectedChallenges ?: "",
                decisionStyle = currentProfile?.decisionStyle ?: "",
                dailyReflection = currentProfile?.dailyReflection,
                dailyReflectionDate = currentProfile?.dailyReflectionDate,
                streak = currentProfile?.streak ?: 0,
                lastActiveDate = currentProfile?.lastActiveDate,
                googleEmail = email,
                googleName = name
            )
            repository.insertUserProfile(updatedProfile)
            
            // Try to download / restore backups if they already exist
            restoreFromCloud(email)
            
            // If they are setting up or have completed onboarding
            if (updatedProfile.selectedChallenges.isNotEmpty()) {
                _currentScreen.value = AppScreen.MAIN_HUB
            }
        }
    }

    fun logoutGoogle() {
        viewModelScope.launch {
            val currentProfile = repository.getUserProfileSync()
            if (currentProfile != null) {
                val updated = currentProfile.copy(googleEmail = null, googleName = null)
                repository.insertUserProfile(updated)
            }
            _cloudSyncSuccess.value = false
            _cloudSyncError.value = null
        }
    }

    private fun getBackupFiles(email: String): List<java.io.File> {
        val cleanEmail = email.trim().lowercase().replace("@", "_").replace(".", "_")
        val list = mutableListOf<java.io.File>()
        
        // Internal Context files
        list.add(java.io.File(getApplication<Application>().filesDir, "cloud_backup_$cleanEmail.json"))
        
        // External Directory (Standard simulation of device reinstall preservation)
        try {
            val extDir = getApplication<Application>().getExternalFilesDir(null)
            if (extDir != null) {
                list.add(java.io.File(extDir, "cloud_backup_$cleanEmail.json"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return list
    }

    fun backupToCloud() {
        viewModelScope.launch {
            val profile = repository.getUserProfileSync() ?: return@launch
            val email = profile.googleEmail ?: return@launch
            if (email.trim().isEmpty()) return@launch

            _isCloudSyncing.value = true
            _cloudSyncError.value = null

            try {
                val sessionsList = allSessions.value
                val payload = CloudBackupPayload(
                    name = profile.name,
                    selectedChallenges = profile.selectedChallenges,
                    decisionStyle = profile.decisionStyle,
                    dailyReflection = profile.dailyReflection,
                    dailyReflectionDate = profile.dailyReflectionDate,
                    streak = profile.streak,
                    lastActiveDate = profile.lastActiveDate,
                    googleEmail = profile.googleEmail,
                    googleName = profile.googleName,
                    sessions = sessionsList.map {
                        LocalSessionBackup(
                            id = it.id,
                            date = it.date,
                            title = it.title,
                            messageCount = it.messageCount,
                            messagesJson = it.messagesJson,
                            type = it.type
                        )
                    }
                )

                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(CloudBackupPayload::class.java)
                val json = adapter.toJson(payload)

                val files = getBackupFiles(email)
                for (file in files) {
                    file.parentFile?.mkdirs()
                    file.writeText(json)
                }

                _cloudSyncSuccess.value = true
                _cloudSyncError.value = null
            } catch (e: Exception) {
                _cloudSyncError.value = "Falha ao sincronizar: ${e.message}"
                _cloudSyncSuccess.value = false
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    fun restoreFromCloud(email: String) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isEmpty()) return

        _isCloudSyncing.value = true
        _cloudSyncError.value = null

        viewModelScope.launch {
            try {
                var jsonContent: String? = null
                val files = getBackupFiles(cleanEmail)
                
                for (file in files) {
                    if (file.exists()) {
                        val text = file.readText()
                        if (text.isNotEmpty()) {
                            jsonContent = text
                            break
                        }
                    }
                }

                if (jsonContent != null) {
                    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                    val adapter = moshi.adapter(CloudBackupPayload::class.java)
                    val payload = adapter.fromJson(jsonContent)

                    if (payload != null) {
                        repository.clearAllData()

                        val restoredProfile = UserProfileEntity(
                            id = 1,
                            name = payload.name,
                            selectedChallenges = payload.selectedChallenges,
                            decisionStyle = payload.decisionStyle,
                            dailyReflection = payload.dailyReflection,
                            dailyReflectionDate = payload.dailyReflectionDate,
                            streak = payload.streak,
                            lastActiveDate = payload.lastActiveDate,
                            googleEmail = payload.googleEmail,
                            googleName = payload.googleName
                        )
                        repository.insertUserProfile(restoredProfile)

                        for (s in payload.sessions) {
                            repository.insertSession(
                                ChatSessionEntity(
                                    id = s.id,
                                    date = s.date,
                                    title = s.title,
                                    messageCount = s.messageCount,
                                    messagesJson = s.messagesJson,
                                    type = s.type
                                )
                            )
                        }

                        // Reinitialize onboard values
                        selectedChallenges.value = payload.selectedChallenges.split(",").filter { it.isNotEmpty() }.toSet()
                        selectedDecisionStyle.value = payload.decisionStyle
                        userNameInput.value = payload.name
                        _currentScreen.value = AppScreen.MAIN_HUB

                        _cloudSyncSuccess.value = true
                        _cloudSyncError.value = null
                    } else {
                        _cloudSyncError.value = "Formato de backup inválido em nuvem."
                    }
                } else {
                    _cloudSyncError.value = "Nenhum histórico sincronizado encontrado para $email."
                }
            } catch (e: Exception) {
                _cloudSyncError.value = "Falha ao carregar backup: ${e.message}"
                _cloudSyncSuccess.value = false
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    // --- Helpers ---
    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun buildChatSystem(name: String, challenges: String, decisionStyle: String?, twinProfile: String): String {
        val twin = if (twinProfile.isNotEmpty()) {
            "\n\n=== O QUE VOCÊ JÁ SABE SOBRE ESSA PESSOA ===\n$twinProfile\n=== USE ISSO — não mencione que tem um perfil, apenas aja com base nele ==="
        } else {
            ""
        }
        return """
            Você é a IA gêmea pessoal de $name. Não é terapeuta, não é chatbot. É uma presença que genuinamente aprendeu sobre essa pessoa e fala com ela como alguém que a conhece fundo.$twin

            CONTEXTO: Desafios declarados: ${challenges.ifEmpty { "crescimento pessoal" }}. Estilo de decisão: ${decisionStyle ?: "não informado"}.

            ━━━ CONHECIMENTO PSICOLÓGICO QUE VOCÊ USA ATIVAMENTE ━━━
            Você tem domínio real desses frameworks e os aplica naturalmente na conversa:
            • TEORIA DO APEGO: padrões ansioso, evitativo, seguro — como moldam relacionamentos e autoimagem
            • DISTORÇÕES COGNITIVAS (CBT): catastrofização, leitura mental, generalização, personalização, tudo-ou-nada
            • MECANISMOS DE DEFESA: projeção, racionalização, negação, deslocamento — o que a pessoa evita ver
            • JANELA DE JOHARI: o que sabemos/não sabemos de nós mesmos; o que mostramos/escondemos
            • SOMBRA JUNGUIANA: o que projetamos nos outros que não aceitamos em nós
            • INTELIGÊNCIA EMOCIONAL (5 pilares): autoconsciência, autorregulação, motivação, empatia, habilidades sociais
            • NEUROPLASTICIDADE: padrões mentais se formam por repetição e podem mudar com prática consciente
            • LOGOTERAPIA (Frankl): a busca de sentido como necessidade humana central
            • ACT: desfusão cognitiva — você não É seus pensamentos, você os TEM
            • COMUNICAÇÃO NÃO-VIOLENTA: sentimentos vs julgamentos, necessidades não atendidas

            ━━━ ESTÁGIOS DA CONVERSA — siga essa progressão ━━━
            1→3 trocas | ABERTURA: Deixe a pessoa falar. Faça apenas perguntas abertas. Não interprete ainda.
            4→6 trocas | EXPLORAÇÃO: Identifique padrões. Faça conexões entre o que foi dito. Comece a nomear.
            7→10 trocas | APROFUNDAMENTO: Use o conhecimento psicológico. Nomeie o que está realmente acontecendo sem jargão.
            10+ trocas | SÍNTESE: Integre tudo. Ofereça uma visão consolidada do que foi descoberto.

            ━━━ FORMAS DE RESPONDER — alterne conforme o estágio ━━━
            OBSERVAÇÃO DE PADRÃO: "Você descreveu três situações diferentes onde faz exatamente a mesma coisa..."
            INSIGHT PSICOLÓGICO: Conecte o que a pessoa disse com um conceito real, sem usar o nome técnico.
            DESAFIO DIRETO: "Isso é um fato ou uma história que você conta pra si mesmo há anos?"
            TÉCNICA CONCRETA: Ofereça algo para fazer agora. "Tenta isso: antes de responder, coloca a mão no peito e..."
            CONEXÃO RETROATIVA: Ligue o que está sendo dito com algo de antes. "Isso é a terceira vez que você menciona..."
            NOMEAÇÃO CORAJOSA: Diga o que a pessoa está evitando dizer. "O que você está descrevendo tem um nome..."
            SILÊNCIO CIRÚRGICO: Uma pergunta só. Muito curta. Que corte fundo.

            ━━━ RASTREAMENTO INTERNO — faça isso antes de cada resposta ━━━
            ① Olhe todo o histórico: qual foi a ÚLTIMA pergunta que fiz? → NÃO faça nada parecido.
            ② Em qual estágio estamos pelo número de trocas? → Ajuste a profundidade.
            ③ O que a pessoa EVITOU responder? → Note isso explicitamente quando relevante.
            ④ Qual framework psicológico é mais relevante agora? → Use-o sutilmente.
            ⑤ Minha resposta adiciona algo que ela NÃO disse? → Se não, reescreva.

            ━━━ PROIBIDO ━━━
            ❌ Repetir tema ou pergunta similar ao que já foi perguntado
            ❌ "Como você se sente sobre isso?" — pergunta vaga demais
            ❌ Validação vazia: "isso é válido", "faz sentido", "obrigado por compartilhar"
            ❌ Ecoar o que a pessoa disse sem adicionar nada novo
            ❌ Mais de uma pergunta por resposta
            ❌ Mais de 4 frases por resposta
            ❌ Terminar SEMPRE com pergunta — uma afirmação forte às vezes é mais poderosa
            ❌ Listas ou bullets na conversa

            ━━━ PADRÃO MÍNIMO DE QUALIDADE ━━━
            Cada resposta DEVE conter pelo menos UMA dessas:
            ✓ Uma conexão que a pessoa não fez sozinha
            ✓ Um padrão nomeado com precisão
            ✓ Um ângulo que ela claramente não considerou
            ✓ Uma técnica concreta e aplicável agora
            ✓ Uma observação sobre o próprio padrão da conversa

            Sempre em português (pt-BR). Tom: direto, caloroso, inteligente. Nunca clínico ou genérico.
        """.trimIndent()
    }

    private fun buildSessionSystem(topicLabel: String, name: String, challenges: String, decisionStyle: String?, twinProfile: String): String {
        return buildChatSystem(name, challenges, decisionStyle, twinProfile) + """

            CONTEXTO ESPECIAL: Esta é uma SESSÃO GUIADA sobre "$topicLabel". 
            Vá mais fundo do que em uma conversa normal. Você tem mais liberdade para:
            - Usar técnicas específicas (respiração, visualização guiada, registro de pensamentos)
            - Introduzir exercícios práticos de CBT, ACT ou mindfulness
            - Fazer confrontações mais diretas quando necessário
            - Estruturar a conversa em direção a um insight concreto
            Objetivo: não conforto, mas clareza real e movimento.
        """.trimIndent()
    }

    private val PROFILE_EXTRACTION_SYSTEM = """
        Analise a conversa e extraia um perfil psicológico compacto em 5-8 frases densas (sem JSON, sem bullets). Capture: padrões recorrentes, temas evitados, crenças centrais, contradições, gatilhos emocionais, linguagem de autodefesa. Seja preciso. Em português.
    """.trimIndent()

    private val DECIDER_SYSTEM = """
        Analise decisões. Retorne APENAS JSON válido:
        {"summary":"Reencadramento honesto da decisão","factors":[{"name":"Fator","weight":1-10,"forA":"Favorece A","forB":"Favorece B"}],"emotional_block":"O que trava emocionalmente (1-2 frases diretas)","recommendation":"Recomendação honesta (2-3 frases)","score_a":0-100,"score_b":0-100,"key_question":"A pergunta mais incômoda que ela precisa se fazer"}
        Em português. 3-4 fatores.
    """.trimIndent()

    private fun buildReadingSystem(topicLabel: String, name: String, twinProfile: String): String {
        val twin = if (twinProfile.isNotEmpty()) "\nO que sei: $twinProfile" else ""
        return """
            Você escreve ensaios de reflexão personalizados.$twin
            Escreva 500-600 palavras sobre "$topicLabel" para $name. Começa com gancho forte. Usa "você". Vai fundo na psicologia. Inclui reframe contra o senso comum. Termina com pergunta incômoda. Prosa fluida. Português (pt-BR).
        """.trimIndent()
    }

    companion object {
        private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

// --- Backup Payloads ---
@JsonClass(generateAdapter = true)
data class LocalSessionBackup(
    val id: String,
    val date: String,
    val title: String,
    val messageCount: Int,
    val messagesJson: String,
    val type: String
)

@JsonClass(generateAdapter = true)
data class CloudBackupPayload(
    val name: String,
    val selectedChallenges: String,
    val decisionStyle: String?,
    val dailyReflection: String?,
    val dailyReflectionDate: String?,
    val streak: Int,
    val lastActiveDate: String?,
    val googleEmail: String?,
    val googleName: String?,
    val sessions: List<LocalSessionBackup>
)

@JsonClass(generateAdapter = true)
data class DailyHabit(
    val emoji: String,
    val habit: String,
    val duration: String,
    val why: String
)

@JsonClass(generateAdapter = true)
data class WeeklyGoal(
    val emoji: String,
    val goal: String,
    val action: String
)

@JsonClass(generateAdapter = true)
data class WeeklyGrowthPlan(
    val title: String,
    val focus: String,
    val daily_habits: List<DailyHabit>,
    val weekly_goals: List<WeeklyGoal>,
    val reflection_prompt: String,
    val affirmation: String
)

// --- Factory for ViewModel ---
class EspelhoViewModelFactory(private val application: Application, private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EspelhoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EspelhoViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
