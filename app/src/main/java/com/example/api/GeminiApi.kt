package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // Base64 raw audio data
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val responseType: String? = null,
    val responseMimeType: String? = null,
    val responseModalities: List<String>? = null,
    val speechConfig: SpeechConfig? = null
)

@JsonClass(generateAdapter = true)
data class SpeechConfig(
    val voiceConfig: VoiceConfig
)

@JsonClass(generateAdapter = true)
data class VoiceConfig(
    val prebuiltVoiceConfig: PrebuiltVoiceConfig
)

@JsonClass(generateAdapter = true)
data class PrebuiltVoiceConfig(
    val voiceName: String
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

@JsonClass(generateAdapter = true)
data class GroqMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class GroqResponseFormat(
    val type: String
)

@JsonClass(generateAdapter = true)
data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Float? = null,
    val response_format: GroqResponseFormat? = null
)

@JsonClass(generateAdapter = true)
data class GroqChoice(
    val message: GroqMessage?
)

@JsonClass(generateAdapter = true)
data class GroqResponse(
    val choices: List<GroqChoice>?
)

interface GroqApiService {
    @POST("chat/completions")
    suspend fun generateContent(
        @Header("Authorization") authHeader: String,
        @Body request: GroqRequest
    ): GroqResponse
}

@JsonClass(generateAdapter = true)
data class TtsInput(
    val text: String? = null,
    val ssml: String? = null
)

@JsonClass(generateAdapter = true)
data class TtsVoice(
    val languageCode: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class TtsAudioConfig(
    val audioEncoding: String,
    val speakingRate: Double? = null,
    val pitch: Double? = null,
    val effectsProfileId: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class TtsRequest(
    val input: TtsInput,
    val voice: TtsVoice,
    val audioConfig: TtsAudioConfig
)

@JsonClass(generateAdapter = true)
data class TtsResponse(
    val audioContent: String
)

interface GoogleTtsApiService {
    @POST("v1/text:synthesize")
    suspend fun synthesizeText(
        @Query("key") apiKey: String,
        @Body request: TtsRequest
    ): TtsResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    @Volatile
    var lastTtsError: String? = null

    @Volatile
    var lastGeminiError: String? = null

    @Volatile
    var customApiKey: String? = null

    @Volatile
    var groqApiKey: String? = null

    @Volatile
    var googleTtsApiKey: String? = null

    @Volatile
    var aiProvider: String = "gemini"

    @Volatile
    var lastGeneratedAudioBase64: String? = null

    @Volatile
    var lastGeneratedAudioMimeType: String? = null

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }

    private val groqRetrofit = Retrofit.Builder()
        .baseUrl("https://api.groq.com/openai/v1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val groqService: GroqApiService by lazy {
        groqRetrofit.create(GroqApiService::class.java)
    }

    private val ttsRetrofit = Retrofit.Builder()
        .baseUrl("https://texttospeech.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val googleTtsService: GoogleTtsApiService by lazy {
        ttsRetrofit.create(GoogleTtsApiService::class.java)
    }

    fun prepareForSpeech(text: String): String {
        return text
            .replace(Regex("\\*+"), "")
            .replace(Regex("#+"), "")
            .replace(Regex("`+"), "")
            .replace("- ", " ")
            .replace(Regex("\\.\\s+"), ". ")
            .replace(Regex("\\?\\s+"), "? ")
            .replace(Regex("!\\s+"), "! ")
            .replace(Regex(",\\s+"), ", ")
            .replace("—", " — ")
            .replace("...", " ")
            .trim()
    }

    fun cleanAndFormatSsml(inputText: String): String {
        // Remove markdown formatting
        var cleaned = inputText
            .replace(Regex("\\*+"), "")
            .replace(Regex("#+"), "")
            .replace(Regex("`+"), "")
            .replace("- ", " ")
            .trim()

        // Escape XML entities so we don't break the SSML parsing inside the voice engine
        cleaned = cleaned
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        // Inject breathing pauses after punctuation
        cleaned = cleaned
            .replace(". ", ". <break time=\"400ms\"/> ")
            .replace(", ", ", <break time=\"200ms\"/> ")
            .replace("? ", "? <break time=\"400ms\"/> ")
            .replace("! ", "! <break time=\"400ms\"/> ")
            .replace("; ", "; <break time=\"200ms\"/> ")
            .replace("... ", "... <break time=\"500ms\"/> ")

        return "<speak>$cleaned</speak>"
    }

    suspend fun synthesizeText(text: String, voiceName: String = "pt-BR-Neural2-A"): String? {
        val apiKey = googleTtsApiKey?.takeIf { it.isNotEmpty() }
            ?: try { BuildConfig.GOOGLE_TTS_API_KEY } catch (e: Throwable) { "" }
                .takeIf { it.isNotEmpty() && it != "MY_GOOGLE_TTS_API_KEY" }
            ?: customApiKey?.takeIf { it.isNotEmpty() }
            ?: try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }
                .takeIf { it.isNotEmpty() && it != "MY_GEMINI_API_KEY" }
            ?: return null

        val preparedText = prepareForSpeech(text)

        val request = TtsRequest(
            input = TtsInput(text = preparedText),
            voice = TtsVoice(
                languageCode = "pt-BR",
                name = voiceName
            ),
            audioConfig = TtsAudioConfig(
                audioEncoding = "MP3",
                speakingRate = 1.08,
                pitch = -1.0,
                effectsProfileId = listOf("headphone-class-device")
            )
        )

        return try {
            lastTtsError = null
            val response = googleTtsService.synthesizeText(apiKey, request)
            response.audioContent
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            android.util.Log.e("GoogleTts", "Google Cloud TTS REST call HTTP ${e.code()}: $errorBody")
            lastTtsError = "HTTP ${e.code()}: $errorBody"
            null
        } catch (e: Exception) {
            android.util.Log.e("GoogleTts", "Google Cloud TTS REST call failed: ${e.message}")
            lastTtsError = e.message ?: e.toString()
            null
        }
    }

    suspend fun callGemini(
        prompt: String,
        systemInstruction: String? = null,
        jsonMode: Boolean = false,
        voiceEnabled: Boolean = false,
        voiceName: String = "Aoede"
    ): String {
        // Clear previous audio and error status
        lastGeneratedAudioBase64 = null
        lastGeneratedAudioMimeType = null
        lastGeminiError = null

        // 1. Get Groq Key (prioritize custom set, then environment secret)
        val groqKey = groqApiKey?.takeIf { it.isNotEmpty() }
            ?: try { BuildConfig.GROQ_API_KEY } catch (e: Throwable) { "" }.takeIf { it.isNotEmpty() && it != "MY_GROQ_API_KEY" }
            ?: ""

        // 2. Google Gemini Fallback Key
        val geminiKey = customApiKey?.takeIf { it.isNotEmpty() }
            ?: try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }.takeIf { it.isNotEmpty() && it != "MY_GEMINI_API_KEY" }
            ?: ""

        var lastGeminiError: String? = null
        var lastGroqError: String? = null

        suspend fun executeGroq(): String? {
            if (groqKey.isEmpty()) {
                lastGroqError = "Chave do Groq não configurada"
                return null
            }
            val messages = mutableListOf<GroqMessage>()
            if (systemInstruction != null) {
                messages.add(GroqMessage(role = "system", content = systemInstruction))
            }
            messages.add(GroqMessage(role = "user", content = prompt))

            val request = GroqRequest(
                model = "llama-3.1-8b-instant",
                messages = messages,
                temperature = 0.3f,
                response_format = if (jsonMode) GroqResponseFormat(type = "json_object") else null
            )

            return try {
                val response = groqService.generateContent("Bearer $groqKey", request)
                val textResponse = response.choices?.firstOrNull()?.message?.content
                if (textResponse != null) {
                    textResponse
                } else {
                    lastGroqError = "Resposta vazia do Groq"
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                lastGroqError = e.localizedMessage ?: e.message ?: "Erro de conexão ou taxa de limite no Groq"
                android.util.Log.e("RetrofitClient", "Groq failed: $lastGroqError")
                null
            }
        }

        suspend fun executeGemini(): String? {
            if (geminiKey.isEmpty()) {
                lastGeminiError = "Chave do Gemini não configurada"
                return null
            }
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)), role = "user")),
                generationConfig = if (jsonMode) {
                    GenerationConfig(
                        temperature = 0.2f,
                        responseMimeType = "application/json"
                    )
                } else if (voiceEnabled) {
                    GenerationConfig(
                        temperature = 0.5f,
                        responseModalities = listOf("TEXT", "AUDIO"),
                        speechConfig = SpeechConfig(
                            voiceConfig = VoiceConfig(
                                prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = voiceName)
                            )
                        )
                    )
                } else {
                    null
                },
                systemInstruction = systemInstruction?.let {
                    Content(parts = listOf(Part(text = it)))
                }
            )

            return try {
                val response = service.generateContent(geminiKey, request)
                var textResult = ""
                var audioBase64: String? = null
                var audioMimeType: String? = null

                response.candidates?.firstOrNull()?.content?.parts?.forEach { part ->
                    if (part.text != null) {
                        textResult += part.text
                    }
                    if (part.inlineData != null) {
                        audioBase64 = part.inlineData.data
                        audioMimeType = part.inlineData.mimeType
                    }
                }

                lastGeneratedAudioBase64 = audioBase64
                lastGeneratedAudioMimeType = audioMimeType

                if (textResult.isNotEmpty()) {
                    textResult
                } else {
                    lastGeminiError = "Resposta de conteúdo vazia do Gemini"
                    RetrofitClient.lastGeminiError = lastGeminiError
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val is429 = e is retrofit2.HttpException && e.code() == 429
                lastGeminiError = if (is429) {
                    "Limite Excedido no Gemini (HTTP 429). A cota de testes compartilhada está temporariamente esgotada."
                } else {
                    e.localizedMessage ?: e.message ?: "Erro de conexão ao servidor Gemini"
                }
                RetrofitClient.lastGeminiError = lastGeminiError
                android.util.Log.e("RetrofitClient", "Gemini failed: $lastGeminiError")
                null
            }
        }

        // Determine retry execution order.
        // If voice synthesis is active, we MUST prioritize Gemini because Groq doesn't support audio modal output.
        // Otherwise, if the preferred provider is Groq, or if we have no Gemini key but do have a Groq key, try Groq first.
        val tryGroqFirst = !voiceEnabled && (aiProvider == "groq" || (aiProvider == "gemini" && geminiKey.isEmpty() && groqKey.isNotEmpty()))

        if (tryGroqFirst) {
            val res1 = executeGroq()
            if (res1 != null) return res1

            android.util.Log.w("RetrofitClient", "Groq failed or was unconfigured. Attempting Gemini backup call...")
            val res2 = executeGemini()
            if (res2 != null) return res2
        } else {
            val res1 = executeGemini()
            if (res1 != null) return res1

            android.util.Log.w("RetrofitClient", "Gemini failed (e.g. 429 quota exceed) or was unconfigured. Attempting Groq backup call...")
            val res2 = executeGroq()
            if (res2 != null) return res2
        }

        // Both providers failed
        val finalErrorMessage = if (lastGeminiError?.contains("429") == true) {
            "Cota esgotada no Gemini (429 Limite Excedido) e o backup via Groq também falhou. Erro Groq: $lastGroqError"
        } else {
            "Ambos os provedores falharam. Gemini: $lastGeminiError | Groq: $lastGroqError"
        }

        return if (jsonMode) {
            """{"error": "$finalErrorMessage"}"""
        } else {
            finalErrorMessage
        }
    }

}
