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

    private val _lastTranscript = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val lastTranscript: SharedFlow<String> = _lastTranscript.asSharedFlow()

    private val _isListening = MutableStateFlow(false)
    override val isListening: Boolean get() = _isListening.value
    val listeningState: StateFlow<Boolean> = _isListening.asStateFlow()

    init {
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(createListener())
            }
        }
        tts = TextToSpeech(appContext, this, "com.google.android.tts")
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) return
        configureFrenchVoice()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                stopListeningInternal()
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                if (continuousEnabled) scheduleRestart(delayMs = 400)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                if (continuousEnabled) scheduleRestart(delayMs = 400)
            }
        })
    }

    private fun configureFrenchVoice() {
        val engine = tts ?: return
        engine.language = Locale.FRANCE
        engine.setSpeechRate(0.92f)
        engine.setPitch(1.02f)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val frenchVoice = engine.voices
                ?.filter { voice ->
                    voice.locale.language == "fr" &&
                        !voice.isNetworkConnectionRequired
                }
                ?.maxByOrNull { voiceQualityScore(it) }
                ?: engine.voices
                    ?.filter { it.locale.language == "fr" }
                    ?.maxByOrNull { voiceQualityScore(it) }

            frenchVoice?.let { engine.voice = it }
        }
    }

    private fun voiceQualityScore(voice: Voice): Int {
        var score = 0
        if (voice.quality >= Voice.QUALITY_HIGH) score += 10
        if (voice.locale.country.equals("FR", ignoreCase = true)) score += 5
        if (!voice.isNetworkConnectionRequired) score += 3
        return score
    }

    override fun startListening() {
        continuousEnabled = true
        beginListening()
    }

    override fun stopListening() {
        continuousEnabled = false
        stopListeningInternal()
    }

    override fun startContinuousListening() {
        continuousEnabled = true
        if (!isSpeaking) beginListening()
    }

    override fun stopContinuousListening() {
        continuousEnabled = false
        stopListeningInternal()
    }

    override fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        val cleaned = text.trim()
        tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "hoshi_${System.currentTimeMillis()}")
    }

    override fun speakAsHoshi(text: String) {
        speak(text)
    }

    fun release() {
        continuousEnabled = false
        stopListeningInternal()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }

    private fun beginListening() {
        val recognizer = speechRecognizer ?: return
        if (_isListening.value || isSpeaking) return
        _isListening.value = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }
        recognizer.startListening(intent)
    }

    private fun stopListeningInternal() {
        _isListening.value = false
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
    }

    private fun scheduleRestart(delayMs: Long) {
        scope.launch {
            delay(delayMs)
            if (continuousEnabled && !isSpeaking) beginListening()
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _isListening.value = false
        }

        override fun onError(error: Int) {
            _isListening.value = false
            if (continuousEnabled && !isSpeaking) {
                val backoff = if (error == SpeechRecognizer.ERROR_NO_MATCH) 300L else 800L
                scheduleRestart(backoff)
            }
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                ?.lowercase(Locale.FRANCE)
                ?: run {
                    if (continuousEnabled) scheduleRestart(300)
                    return
                }
            _lastTranscript.tryEmit(text)
            scope.launch {
                onCommand(text)
                if (continuousEnabled && !isSpeaking) scheduleRestart(500)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
