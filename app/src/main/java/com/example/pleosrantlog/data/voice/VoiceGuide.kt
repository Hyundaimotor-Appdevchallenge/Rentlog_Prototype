package com.example.pleosrantlog.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech as AndroidTts
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale

fun interface Speaker { fun speak(text: String) }

/** Pleos TTS (ON_DEVICE) with automatic fallback to android.speech.tts. Never throws. */
class VoiceGuide(context: Context) : Speaker {
    private val app = context.applicationContext
    @Volatile private var pleos: PleosTts? = null
    @Volatile private var pleosReady = false
    private var fallback: AndroidTts? = null
    @Volatile private var fallbackReady = false

    init {
        try {
            val t = PleosTts(app, Mode.ON_DEVICE)
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() { pleosReady = true }
                override fun onFailure(error: ErrorType) { Log.w(TAG, "Pleos TTS init failed: $error"); initFallback() }
            })
            pleos = t
        } catch (t: Throwable) {
            Log.w(TAG, "Pleos TTS unavailable", t); initFallback()
        }
    }

    private fun initFallback() {
        if (fallback != null) return
        try {
            fallback = AndroidTts(app) { status ->
                if (status == AndroidTts.SUCCESS) {
                    fallback?.language = Locale.KOREAN
                    fallbackReady = true
                }
            }
        } catch (t: Throwable) { Log.w(TAG, "Android TTS unavailable", t) }
    }

    override fun speak(text: String) {
        try {
            if (pleosReady) { pleos?.speak(text, false); return }
            initFallback()
            if (fallbackReady) fallback?.speak(text, AndroidTts.QUEUE_FLUSH, null, "rentlog")
            else Log.i(TAG, "TTS not ready, skipped: $text")
        } catch (t: Throwable) { Log.w(TAG, "speak failed", t) }
    }

    fun release() {
        try { pleos?.release() } catch (_: Throwable) {}
        try { fallback?.shutdown() } catch (_: Throwable) {}
    }

    private companion object { const val TAG = "VoiceGuide" }
}
