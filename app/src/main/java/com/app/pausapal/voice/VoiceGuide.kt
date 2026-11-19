package com.app.pausapal.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Optional voice guidance with Android's built-in TextToSpeech. Off by default.
 *
 *  - The engine is created only in [enable] (when the user turns "Voice guidance" on).
 *  - Only an OFFLINE voice is used (isNetworkConnectionRequired == false), so spoken text never
 *    leaves the phone. The prompts are English, so only English voices are considered.
 *  - Callers pass fixed prompts only. No reading or measurement is ever spoken.
 */
class VoiceGuide(context: Context) {

    enum class Result { Ready, NoOfflineVoice, Unavailable }

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false

    /** Bumped on every enable/shutdown so a late init callback from an old engine is ignored. */
    private var generation = 0

    val isSpeaking: Boolean get() = ready && tts?.isSpeaking == true

    /** Creates the engine and picks an offline voice. [onResult] runs on the main thread. */
    fun enable(onResult: (Result) -> Unit) {
        if (ready) { onResult(Result.Ready); return }
        shutdown()
        val gen = ++generation
        var engine: TextToSpeech? = null
        engine = TextToSpeech(appContext) { status ->
            val t = engine
            if (gen != generation || t == null) return@TextToSpeech
            Log.d(TAG, "init status=$status")
            if (status != TextToSpeech.SUCCESS) {
                shutdown(); onResult(Result.Unavailable); return@TextToSpeech
            }
            val voice = pickOfflineVoice(t)
            if (voice == null || t.setVoice(voice) != TextToSpeech.SUCCESS) {
                shutdown(); onResult(Result.NoOfflineVoice); return@TextToSpeech
            }
            t.setSpeechRate(0.9f)
            ready = true
            onResult(Result.Ready)
        }
        tts = engine
    }

    /** Speaks a fixed prompt, replacing anything currently being spoken. */
    fun speak(prompt: String) {
        if (!ready) return
        tts?.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, "pp-${prompt.hashCode()}")
    }

    fun stop() {
        if (ready) tts?.stop()
    }

    fun shutdown() {
        generation++
        ready = false
        tts?.let { runCatching { it.stop() }; runCatching { it.shutdown() } }
        tts = null
    }

    private fun pickOfflineVoice(t: TextToSpeech): Voice? {
        val all = runCatching { t.voices }.getOrNull().orEmpty()
        Log.d(TAG, "engine=${t.defaultEngine} voices=${all.size}")
        all.filter { it.locale.language == Locale.ENGLISH.language }.forEach { v ->
            Log.d(TAG, "  ${v.name} ${v.locale} network=${v.isNetworkConnectionRequired} features=${v.features}")
        }
        val offline = all.filter { v ->
            !v.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features.orEmpty() &&
                v.locale.language == Locale.ENGLISH.language
        }
        val country = Locale.getDefault().country
        offline.sortedWith(
            compareByDescending<Voice> { it.locale.country == country }.thenByDescending { it.quality }
        ).firstOrNull()?.let { return it }

        // Some engines list no voices (or list them late). Ask for English by language instead,
        // and keep the result only if the engine reports it as an offline voice.
        val country2 = Locale.getDefault().country.takeIf { it.isNotEmpty() }
        val tries = listOfNotNull(country2?.let { Locale(Locale.ENGLISH.language, it) }, Locale("en", "IN"), Locale.US, Locale.UK)
        for (loc in tries) {
            val r = runCatching { t.setLanguage(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            val v = runCatching { t.voice }.getOrNull()
            Log.d(TAG, "setLanguage($loc)=$r voice=${v?.name} network=${v?.isNetworkConnectionRequired}")
            if (r >= TextToSpeech.LANG_AVAILABLE && v != null && !v.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features.orEmpty()
            ) return v
        }
        return null
    }

    private companion object { const val TAG = "PausaPalVoice" }
}
