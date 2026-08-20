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
import com.appbox.runtime.core.model.HoshiUserConfig
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
 * Écoute vocale HOSHI — filtrage bruit, voix TTS personnalisable.
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

    private var voiceConfig: HoshiUserConfig = HoshiUserConfig()
    private var speechStarted = false
    private var peakRms = 0f
    private var speechStartTime = 0L

    private val _lastTranscript = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val lastTranscript: SharedFlow<String> = _lastTranscript.asSharedFlow()

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
        applyVoiceProfile(voiceConfig)
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
        if (continuousEnabled) scheduleListen(delayMs = 400)
    }

    fun applyVoiceProfile(config: HoshiUserConfig) {
        voiceConfig = config
        val engine = tts ?: return
        if (!ttsReady) return
        engine.language = Locale.FRANCE
        engine.setSpeechRate(config.ttsSpeechRate)
        engine.setPitch(config.ttsPitch)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val voices = engine.voices?.filter { it.locale.language == "fr" }.orEmpty()
            val selected = when {
                config.ttsVoiceName.isNotBlank() ->
                    voices.firstOrNull { it.name == config.ttsVoiceName || it.name.contains(config.ttsVoiceName, ignoreCase = true) }
                else -> voices.maxByOrNull { voiceQualityScore(it) }
            }
            selected?.let { engine.voice = it }
        }
    }

    fun getAvailableFrenchVoices(): List<String> {
        val engine = tts ?: return emptyList()
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) return emptyList()
        return engine.voices
            ?.filter { it.locale.language == "fr" }
            ?.sortedByDescending { voiceQualityScore(it) }
            ?.map { "${it.name} (${it.locale})" }
            .orEmpty()
    }

    private fun voiceQualityScore(voice: Voice): Int {
        var score = 0
        if (voice.quality >= Voice.QUALITY_HIGH) score += 10
        if (voice.locale.country.equals("FR", ignoreCase = true)) score += 5
        if (voice.name.contains("network", ignoreCase = true)) score += 3
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
        if (!isSpeaking) scheduleListen(delayMs = 300)
    }

    override fun stopContinuousListening() = stopListening()

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
        speechStarted = false
        peakRms = 0f
        try {
            recognizer.cancel()
            val silence = voiceConfig.sttSilenceMs.coerceIn(1200L, 5000L)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, voiceConfig.sttMinSpeechMs)
            }
            recognizer.startListening(intent)
        } catch (_: Exception) {
            recreateRecognizer()
            scheduleListen(delayMs = 1200)
        }
    }

    private fun pauseRecognition() {
        speechRecognizer?.cancel()
    }

    private fun resumeRecognition() {
        if (continuousEnabled) scheduleListen(delayMs = 700)
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {
            speechStarted = true
            speechStartTime = System.currentTimeMillis()
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (rmsdB > peakRms) peakRms = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onError(error: Int) {
            if (!continuousEnabled || isSpeaking) return
            when (error) {
                SpeechRecognizer.ERROR_CLIENT -> recreateRecognizer()
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> recreateRecognizer()
            }
            val backoff = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> 400L
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 800L
                SpeechRecognizer.ERROR_AUDIO -> 1200L
                else -> 700L
            }
            scheduleListen(delayMs = backoff)
        }

        override fun onResults(results: Bundle?) {
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            val text = texts.firstOrNull()?.trim()?.lowercase(Locale.FRANCE)

            if (shouldAcceptTranscript(text, confidences)) {
                _lastTranscript.tryEmit(text!!)
                scope.launch { onCommand(text) }
            }
            if (continuousEnabled && !isSpeaking) scheduleListen(delayMs = 500)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun shouldAcceptTranscript(text: String?, confidences: FloatArray?): Boolean {
        if (text.isNullOrBlank()) return false
        if (text.length < 2 && !text.contains("hoshi")) return false

        if (voiceConfig.sttNoiseFilterEnabled) {
            if (!speechStarted && peakRms < 2f) return false
            val duration = System.currentTimeMillis() - speechStartTime
            if (speechStartTime > 0 && duration < voiceConfig.sttMinSpeechMs / 2) return false
        }

        if (confidences != null && confidences.isNotEmpty()) {
            if (confidences[0] < voiceConfig.sttMinConfidence) return false
        }
        return true
    }
}
