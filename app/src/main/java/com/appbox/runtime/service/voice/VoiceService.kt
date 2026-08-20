package com.appbox.runtime.service.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.appbox.runtime.core.contract.VoiceServiceContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Écoute vocale HOSHI — toujours active en arrière-plan, sans bascule micro visible.
 */
class VoiceService(
    context: Context,
    private val onCommand: suspend (String) -> Unit,
) : VoiceServiceContract, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isSpeaking = false
    private var continuousEnabled = true
    private var restartPending = false

    private val _lastTranscript = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val lastTranscript: SharedFlow<String> = _lastTranscript.asSharedFlow()

    /** Toujours true quand HOSHI est actif — pas de clignotement on/off dans l'UI */
    private val _alwaysOn = MutableStateFlow(true)
    val alwaysOn: StateFlow<Boolean> = _alwaysOn.asStateFlow()

    override val isListening: Boolean get() = continuousEnabled

    init {
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            recreateRecognizer()
        }
        tts = TextToSpeech(appContext, this, "com.google.android.tts")
    }

    private fun recreateRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(createListener())
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) return
        configureFrenchVoice()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                pauseRecognition()
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                resumeRecognition()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                resumeRecognition()
            }
        })
        if (continuousEnabled) scheduleListen(delayMs = 300)
    }

    private fun configureFrenchVoice() {
        val engine = tts ?: return
        engine.language = Locale.FRANCE
        engine.setSpeechRate(0.92f)
        engine.setPitch(1.02f)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            engine.voices
                ?.filter { it.locale.language == "fr" }
                ?.maxByOrNull { voiceQualityScore(it) }
                ?.let { engine.voice = it }
        }
    }

    private fun voiceQualityScore(voice: Voice): Int {
        var score = 0
        if (voice.quality >= Voice.QUALITY_HIGH) score += 10
        if (voice.locale.country.equals("FR", ignoreCase = true)) score += 5
        return score
    }

    override fun startListening() = startContinuousListening()

    override fun stopListening() {
        continuousEnabled = false
        _alwaysOn.value = false
        pauseRecognition()
    }

    override fun startContinuousListening() {
        continuousEnabled = true
        _alwaysOn.value = true
        if (!isSpeaking) scheduleListen(delayMs = 200)
    }

    override fun stopContinuousListening() {
        stopListening()
    }

    override fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text.trim(), TextToSpeech.QUEUE_FLUSH, null, "hoshi_${System.currentTimeMillis()}")
    }

    override fun speakAsHoshi(text: String) = speak(text)

    fun release() {
        continuousEnabled = false
        pauseRecognition()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }

    private fun scheduleListen(delayMs: Long) {
        if (!continuousEnabled || restartPending || isSpeaking) return
        restartPending = true
        scope.launch {
            delay(delayMs)
            restartPending = false
            if (continuousEnabled && !isSpeaking) startRecognitionCycle()
        }
    }

    private fun startRecognitionCycle() {
        val recognizer = speechRecognizer ?: return
        if (isSpeaking) return
        try {
            recognizer.cancel()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }
            recognizer.startListening(intent)
        } catch (_: Exception) {
            recreateRecognizer()
            scheduleListen(delayMs = 1000)
        }
    }

    private fun pauseRecognition() {
        speechRecognizer?.cancel()
    }

    private fun resumeRecognition() {
        if (continuousEnabled) scheduleListen(delayMs = 500)
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onError(error: Int) {
            if (!continuousEnabled || isSpeaking) return
            when (error) {
                SpeechRecognizer.ERROR_CLIENT -> recreateRecognizer()
            }
            scheduleListen(delayMs = if (error == SpeechRecognizer.ERROR_NO_MATCH) 200L else 600L)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                ?.lowercase(Locale.FRANCE)
            if (!text.isNullOrBlank()) {
                _lastTranscript.tryEmit(text)
                scope.launch { onCommand(text) }
            }
            if (continuousEnabled && !isSpeaking) scheduleListen(delayMs = 400)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
