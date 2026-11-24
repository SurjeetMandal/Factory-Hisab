package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

interface SpeechBridge {
    fun speak(
        text: String,
        language: VoiceLanguage,
        onStarted: () -> Unit = {},
        onDone: () -> Unit = {}
    )
    fun stopSpeaking()
    fun startListening(
        language: VoiceLanguage,
        onReady: () -> Unit = {},
        onPartial: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (Int) -> Unit
    )
    fun stopListening()
    fun isSpeechRecognitionAvailable(): Boolean
    fun release()
}

class OfflineSpeechManager(context: Context) : SpeechBridge {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var ttsReady = false
    private var tts: TextToSpeech? = null
    private var pendingSpeakRunnable: Runnable? = null
    private var activeUtteranceId: String? = null
    private var onUtteranceDoneCallback: (() -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isCurrentlyListening = false

    init {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    configureTtsListener()
                    applyLanguageToTts(VoiceLanguage.HINGLISH)
                    pendingSpeakRunnable?.let { runnable ->
                        pendingSpeakRunnable = null
                        mainHandler.post(runnable)
                    }
                }
            }
        } catch (_: Throwable) {
            ttsReady = false
        }
    }

    private fun configureTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) {
                    val cb = onUtteranceDoneCallback
                    onUtteranceDoneCallback = null
                    mainHandler.post { cb?.invoke() }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) {
                    val cb = onUtteranceDoneCallback
                    onUtteranceDoneCallback = null
                    mainHandler.post { cb?.invoke() }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == activeUtteranceId) {
                    val cb = onUtteranceDoneCallback
                    onUtteranceDoneCallback = null
                    mainHandler.post { cb?.invoke() }
                }
            }
        })
    }

    private fun applyLanguageToTts(language: VoiceLanguage) {
        val engine = tts ?: return
        val locale = when (language) {
            VoiceLanguage.HINGLISH, VoiceLanguage.HINDI ->
                Locale.Builder().setLanguage("hi").setRegion("IN").build()
            VoiceLanguage.ENGLISH ->
                Locale.Builder().setLanguage("en").setRegion("IN").build()
        }
        val res = engine.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.Builder().setLanguage("en").setRegion("IN").build())
        }
        // Prefer crisp Indian voice if installed
        val matchingVoice = runCatching {
            engine.voices
                ?.filter {
                    it.locale.language.equals(locale.language, ignoreCase = true) &&
                        !it.isNetworkConnectionRequired
                }
                ?.firstOrNull()
                ?: engine.voices?.firstOrNull {
                    it.locale.language.equals(locale.language, ignoreCase = true)
                }
        }.getOrNull()
        if (matchingVoice != null) {
            runCatching { engine.voice = matchingVoice }
        }
        engine.setPitch(1.02f)
        engine.setSpeechRate(if (language == VoiceLanguage.HINGLISH) 0.98f else 0.95f)
    }

    override fun speak(
        text: String,
        language: VoiceLanguage,
        onStarted: () -> Unit,
        onDone: () -> Unit
    ) {
        mainHandler.post {
            stopListening()
            val cleanedForSpeech = sanitizeForSpeech(text)
            if (!ttsReady || tts == null) {
                pendingSpeakRunnable = Runnable {
                    speakInternal(cleanedForSpeech, language, onStarted, onDone)
                }
                return@post
            }
            speakInternal(cleanedForSpeech, language, onStarted, onDone)
        }
    }

    private fun speakInternal(
        text: String,
        language: VoiceLanguage,
        onStarted: () -> Unit,
        onDone: () -> Unit
    ) {
        val engine = tts
        if (engine == null || !ttsReady) {
            onDone()
            return
        }
        applyLanguageToTts(language)
        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId
        onUtteranceDoneCallback = onDone
        onStarted()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            onUtteranceDoneCallback = null
            onDone()
        }
    }

    private fun sanitizeForSpeech(text: String): String {
        return text
            .replace("₹", "rupees ")
            .replace("(+/-)", "")
            .replace("/", " ")
            .replace("•", ",")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    override fun stopSpeaking() {
        mainHandler.post {
            pendingSpeakRunnable = null
            onUtteranceDoneCallback = null
            activeUtteranceId = null
            runCatching { tts?.stop() }
        }
    }

    override fun isSpeechRecognitionAvailable(): Boolean {
        return runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }
            .getOrDefault(false)
    }

    override fun startListening(
        language: VoiceLanguage,
        onReady: () -> Unit,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (Int) -> Unit
    ) {
        mainHandler.post {
            stopSpeaking()
            if (!isSpeechRecognitionAvailable()) {
                onError(SpeechRecognizer.ERROR_CLIENT)
                return@post
            }

            runCatching { speechRecognizer?.destroy() }
            val recognizer = runCatching {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }.getOrNull()

            if (recognizer == null) {
                onError(SpeechRecognizer.ERROR_CLIENT)
                return@post
            }

            speechRecognizer = recognizer
            isCurrentlyListening = true

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onReady()
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    isCurrentlyListening = false
                    onError(error)
                }

                override fun onResults(results: Bundle?) {
                    isCurrentlyListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val top = matches?.firstOrNull()?.trim().orEmpty()
                    if (top.isBlank()) {
                        onError(SpeechRecognizer.ERROR_NO_MATCH)
                    } else {
                        onResult(top)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim().orEmpty()
                    if (partial.isNotBlank()) {
                        onPartial(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            runCatching {
                recognizer.startListening(intent)
            }.onFailure {
                isCurrentlyListening = false
                onError(SpeechRecognizer.ERROR_CLIENT)
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            if (isCurrentlyListening) {
                isCurrentlyListening = false
                runCatching { speechRecognizer?.stopListening() }
            }
            runCatching { speechRecognizer?.cancel() }
        }
    }

    override fun release() {
        mainHandler.post {
            pendingSpeakRunnable = null
            onUtteranceDoneCallback = null
            isCurrentlyListening = false
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
            tts = null
            ttsReady = false
        }
    }
}
