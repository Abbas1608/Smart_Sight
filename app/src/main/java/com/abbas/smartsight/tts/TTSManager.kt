package com.abbas.smartsight.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import java.util.*

class TTSManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentJob: Job? = null
    private var lastSpokenMessage = ""
    private val debounceTime = 2000L

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTSManager", "Language not supported")
                } else {
                    isInitialized = true
                    speak("Welcome to Smart Sight. Starting scan now.")
                }
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
    }

    fun speakWithDebounce(message: String, forceSpeak: Boolean = false) {
        if (!isInitialized || tts == null) return
        if (!forceSpeak && message == lastSpokenMessage) return

        currentJob?.cancel()

        currentJob = coroutineScope.launch {
            try {
                if (tts?.isSpeaking == true) delay(500)

                speak(message)
                lastSpokenMessage = message

                delay(debounceTime)
                lastSpokenMessage = ""
            } catch (e: CancellationException) {
                Log.d("TTSManager", "Speech cancelled")
            }
        }
    }

    private fun speak(text: String) {
        Log.d("TTSManager", "TTS: $text")
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
        currentJob?.cancel()
        lastSpokenMessage = ""
    }

    fun shutdown() {
        currentJob?.cancel()
        coroutineScope.cancel()
        tts?.stop()
        tts?.shutdown()
    }
}
