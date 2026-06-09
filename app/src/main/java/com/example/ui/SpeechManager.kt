package com.example.ui

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.random.Random

class SpeechManager(
    context: Context,
    private val onVolumeChange: (Float) -> Unit,
    private val onDone: () -> Unit
) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var simJob: Job? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(0.4f) // Demonic deep pitch
            tts?.setSpeechRate(0.85f)
            
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    simJob?.cancel()
                    simJob = scope.launch {
                        while (isActive) {
                            onVolumeChange(Random.nextFloat() * 0.3f) // low rumble
                            delay(100)
                        }
                    }
                }
                
                override fun onDone(utteranceId: String?) {
                    simJob?.cancel()
                    onVolumeChange(0f)
                    onDone()
                }
                
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    simJob?.cancel()
                    onVolumeChange(0f)
                    onDone()
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    super.onRangeStart(utteranceId, start, end, frame)
                    // A word is spoken: envelope pulse
                    simJob?.cancel()
                    simJob = scope.launch {
                        // Attack
                        onVolumeChange(0.7f + Random.nextFloat() * 0.3f)
                        delay(60)
                        // Sustain
                        onVolumeChange(0.4f + Random.nextFloat() * 0.3f)
                        delay(80)
                        // Decay
                        while (isActive) {
                            onVolumeChange(Random.nextFloat() * 0.2f)
                            delay(50)
                        }
                    }
                }
            })
            isReady = true
        }
    }

    fun speak(text: String) {
        if (!isReady) {
            onDone()
            return
        }
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "speech")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "speech")
    }

    fun shutdown() {
        simJob?.cancel()
        tts?.stop()
        tts?.shutdown()
    }
}
