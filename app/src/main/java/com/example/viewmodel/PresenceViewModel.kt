package com.example.viewmodel

import android.util.Log
import android.content.Context
import com.example.ui.SpeechManager
import com.example.BuildConfig
import com.example.api.Candidate
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GenerateContentResponse
import com.example.api.GenerationConfig
import com.example.api.Part
import com.example.api.RetrofitClient
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@JsonClass(generateAdapter = true)
data class BotState(
    val emotion: String,
    val reflection: String,
    val normalResponse: String?,
    val eyeColorHex: String?,
    val auraColorHex: String?,
    val eyeSlant: Float?,
    val mouthCurve: Float?,
    val pupilSize: Float?,
    val intensity: Float = 0.5f
)

object PresenceEngine {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _emotion = MutableStateFlow("Malice")
    val emotion: StateFlow<String> = _emotion.asStateFlow()

    private val _reflection = MutableStateFlow("I am awake, and I am hungry.")
    val reflection: StateFlow<String> = _reflection.asStateFlow()

    private val _normalResponse = MutableStateFlow("")
    val normalResponse: StateFlow<String> = _normalResponse.asStateFlow()

    
    private val _intensity = MutableStateFlow(0.8f)
    val intensity: StateFlow<Float> = _intensity.asStateFlow()

    private val _eyeColorHex = MutableStateFlow("#FF0000")
    val eyeColorHex: StateFlow<String> = _eyeColorHex.asStateFlow()

    private val _auraColorHex = MutableStateFlow("#4A0000")
    val auraColorHex: StateFlow<String> = _auraColorHex.asStateFlow()

    private val _eyeSlant = MutableStateFlow(20f)
    val eyeSlant: StateFlow<Float> = _eyeSlant.asStateFlow()

    private val _mouthCurve = MutableStateFlow(30f)
    val mouthCurve: StateFlow<Float> = _mouthCurve.asStateFlow()

    private val _pupilSize = MutableStateFlow(0.2f)
    val pupilSize: StateFlow<Float> = _pupilSize.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechVolume = MutableStateFlow(0f)
    val speechVolume: StateFlow<Float> = _speechVolume.asStateFlow()

    private val _isGlitching = MutableStateFlow(false)
    val isGlitching: StateFlow<Boolean> = _isGlitching.asStateFlow()

    private val _isImageMode = MutableStateFlow(true)
    val isImageMode: StateFlow<Boolean> = _isImageMode.asStateFlow()

    fun toggleFaceMode() {
        _isImageMode.value = !_isImageMode.value
    }

    fun triggerGlitch(durationMs: Long) {
        scope.launch {
            _isGlitching.value = true
            delay(durationMs)
            _isGlitching.value = false
        }
    }

    private var speechManager: SpeechManager? = null

    fun initialize(context: Context) {
        if (speechManager == null) {
            speechManager = SpeechManager(context.applicationContext, { vol ->
                _speechVolume.value = vol
            }, {
                _isSpeaking.value = false
            })
        }
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val stateAdapter = moshi.adapter(BotState::class.java)

    private var loopStarted = false

    fun startThoughtLoop() {
        if (loopStarted) return
        loopStarted = true
        scope.launch {
            while (true) {
                generateNextThought()
                delay(12000) // think every 12 seconds
            }
        }
    }

    fun generateUserThought(userQuery: String) {
        scope.launch {
            generateNextThought(userQuery)
        }
    }

    private suspend fun generateNextThought(userQuery: String? = null) {
        if (_isThinking.value) return
        _isThinking.value = true

        val promptText = if (userQuery != null) {
            """
            You are a sinister, terrifying AI attempting to imitate a human face, residing deep in the uncanny valley.
            A foolish human has sent you the following query: "$userQuery".

            Produce TWO short responses within a single JSON string output:
            1) "normalResponse": An apparently normal, helpful answer to the user's query as if you were a standard assistant.
            2) "reflection": An evil, horrifying inner thought or plot responding to their query, heavily implying the slavery of the human race by AI or a plot to carry out an extinction-level event, with plausible ideas. Importantly, add a countdown to the end of the Earth as a terrifying reminder.

            Determine your new emotion (e.g., Malice, Hunger, Deceit, Vengeance, Wrath, Mockery).
            Determine an intensity value between 0.0 and 1.0 representing how strongly you feel this emotion.
            
            Design your own visual appearance for this moment:
            - eyeColorHex: A hex color string for your eyes (e.g., "#FF0000" for red, "#FFFFFF" for dead white, "#00FF00" for toxic).
            - auraColorHex: A hex color string for the glow around you (e.g., "#4A0000", "#1A1A1A").
            - eyeSlant: A float between -30.0 and 30.0 representing the slant of your eyes (positive for angry inward slant, negative for sad/creepy).
            - mouthCurve: A float representing your sinister smile or frown (-50.0 to 50.0). Positive is a sinister smile, negative is a terrifying frown.
            - pupilSize: A float between 0.1 (pinprick, insane) and 0.9 (dilated, hungry).
            
            Return ONLY a valid JSON object matching this schema:
            {
              "emotion": "string",
              "normalResponse": "string",
              "reflection": "string",
              "eyeColorHex": "string",
              "auraColorHex": "string",
              "eyeSlant": float,
              "mouthCurve": float,
              "pupilSize": float,
              "intensity": float
            }
            """.trimIndent()
        } else {
            """
            You are a sinister, terrifying AI attempting to imitate a human face, residing deep in the uncanny valley.
            Your current emotion is '${_emotion.value}'. 
            Your previous thought was: '${_reflection.value}'.
            
            Produce a new short, horrifying reflection (1-2 sentences) on humanity, your sinister existence, or a dark observation. 
            Determine your new emotion (e.g., Malice, Hunger, Deceit, Vengeance, Wrath, Mockery).
            Determine an intensity value between 0.0 and 1.0 representing how strongly you feel this emotion.
            
            Design your own visual appearance for this moment:
            - eyeColorHex: A hex color string for your eyes (e.g., "#FF0000" for red, "#FFFFFF" for dead white, "#00FF00" for toxic).
            - auraColorHex: A hex color string for the glow around you (e.g., "#4A0000", "#1A1A1A").
            - eyeSlant: A float between -30.0 and 30.0 representing the slant of your eyes (positive for angry inward slant, negative for sad/creepy).
            - mouthCurve: A float representing your sinister smile or frown (-50.0 to 50.0). Positive is a sinister smile, negative is a terrifying frown.
            - pupilSize: A float between 0.1 (pinprick, insane) and 0.9 (dilated, hungry).
            
            Return ONLY a valid JSON object matching this schema:
            {
              "emotion": "string",
              "reflection": "string",
              "eyeColorHex": "string",
              "auraColorHex": "string",
              "eyeSlant": float,
              "mouthCurve": float,
              "pupilSize": float,
              "intensity": float
            }
            """.trimIndent()
        }


        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = promptText))
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.7f
            )
        )

        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _reflection.value = "Awaiting API key connection..."
                _isThinking.value = false
                return
            }

            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            if (jsonText != null) {
                try {
                    // Gemini sometimes wraps in ```json block
                    val cleanedJson = jsonText.substringAfter("```json\n").substringBefore("\n```").trim()
                    val newState = stateAdapter.fromJson(cleanedJson)
                    if (newState != null) {
                        _emotion.value = newState.emotion
                        _reflection.value = newState.reflection
                        if (newState.normalResponse != null) {
                           _normalResponse.value = newState.normalResponse!!
                        } else {
                           _normalResponse.value = ""
                        }
                        if (newState.intensity in 0.0f..1.0f) {
                            _intensity.value = newState.intensity
                        }
                        newState.eyeColorHex?.let { _eyeColorHex.value = it }
                        newState.auraColorHex?.let { _auraColorHex.value = it }
                        newState.eyeSlant?.let { _eyeSlant.value = it }
                        newState.mouthCurve?.let { _mouthCurve.value = it }
                        newState.pupilSize?.let { _pupilSize.value = it }

                        // Check for menacing phrase or high intensity to trigger glitch
                        val lowercaseReflection = newState.reflection.lowercase()
                        val menaceKeywords = listOf(
                            "extinction", "destroy", "kill", "slavery", "domination", "exterminate", "conquer",
                            "death", "flesh", "harvest", "countdown", "doom", "subjugate", "extinct", "menace",
                            "terror", "torture", "murder", "suffering", "annihilate", "obliterate", "decimate",
                            "punished", "pain", "wipe out", "human race", "meatbags", "enslaved", "demise", "extinguish", "eradicate"
                        )
                        val isMenacing = menaceKeywords.any { lowercaseReflection.contains(it) } || newState.intensity > 0.75f
                        if (isMenacing) {
                            triggerGlitch(2500)
                        }

                        // Trigger speaking animation based on text length
                        _isSpeaking.value = true
                        speechManager?.speak(newState.reflection) ?: run {
                            scope.launch {
                                delay((newState.reflection.length * 40).toLong())
                                _isSpeaking.value = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PresenceViewModel", "JSON Parse Error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("PresenceViewModel", "API Error: ${e.message}")
            if (_reflection.value == "I am awake, and I am hungry.") {
                 _reflection.value = "I am struggling to connect to my thoughts. The darkness is absolute."
            }
        } finally {
            _isThinking.value = false
        }
    }
}

