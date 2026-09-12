package com.arra.saccadence.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

enum class SpokenLanguage(val locale: Locale, val code: String) {
    ENGLISH(Locale.ENGLISH, "en"),
    TAMIL(Locale("ta", "IN"), "ta"),
    HINDI(Locale("hi", "IN"), "hi"),
}

/**
 * Android's built-in offline TextToSpeech — no cloud speech service, per
 * the plan's Sarvam-to-offline-TTS correction. The patient's eyes are
 * locked on the laptop target for the whole trial, so voice is the only
 * instruction channel available; this fires from [com.arra.saccadence.trial.TrialSessionController]
 * on the same block_start/block_end events driving the stimulus mirror.
 */
class SpokenInstructions(context: Context, private var language: SpokenLanguage = SpokenLanguage.ENGLISH) {

    private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) applyLanguage()
    }

    fun setLanguage(language: SpokenLanguage) {
        this.language = language
        if (ready) applyLanguage()
    }

    private fun applyLanguage() {
        val result = tts.setLanguage(language.locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH) // Always-available fallback rather than silently dropping instructions.
        }
    }

    fun speak(phrase: Phrase) {
        if (!ready) return
        tts.speak(phrase.text(language), TextToSpeech.QUEUE_FLUSH, null, phrase.name)
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    /** One instruction per trial-phase transition, phrased in all three supported languages. */
    enum class Phrase {
        FIXATION_START, SACCADE_START, PURSUIT_START, TRIAL_COMPLETE, HOLD_STILL, MARKER_LOST;

        fun text(language: SpokenLanguage): String = TEXT.getValue(this).getValue(language)

        companion object {
            private val TEXT: Map<Phrase, Map<SpokenLanguage, String>> = mapOf(
                FIXATION_START to mapOf(
                    SpokenLanguage.ENGLISH to "Please look at the center dot and hold still.",
                    SpokenLanguage.TAMIL to "நடுவில் உள்ள புள்ளியை பாருங்கள், அசையாமல் இருங்கள்.",
                    SpokenLanguage.HINDI to "कृपया बीच वाले बिंदु को देखें और स्थिर रहें।",
                ),
                SACCADE_START to mapOf(
                    SpokenLanguage.ENGLISH to "Follow the dot with your eyes as it jumps.",
                    SpokenLanguage.TAMIL to "புள்ளி குதிக்கும்போது அதை உங்கள் கண்களால் பின்தொடருங்கள்.",
                    SpokenLanguage.HINDI to "बिंदु के उछलने पर उसे अपनी आँखों से देखें।",
                ),
                PURSUIT_START to mapOf(
                    SpokenLanguage.ENGLISH to "Now smoothly follow the moving dot.",
                    SpokenLanguage.TAMIL to "இப்போது நகரும் புள்ளியை மென்மையாக பின்தொடருங்கள்.",
                    SpokenLanguage.HINDI to "अब धीरे-धीरे चलते बिंदु का अनुसरण करें।",
                ),
                TRIAL_COMPLETE to mapOf(
                    SpokenLanguage.ENGLISH to "Test complete. Thank you.",
                    SpokenLanguage.TAMIL to "சோதனை முடிந்தது. நன்றி.",
                    SpokenLanguage.HINDI to "परीक्षण पूरा हुआ। धन्यवाद।",
                ),
                HOLD_STILL to mapOf(
                    SpokenLanguage.ENGLISH to "Please hold your head still.",
                    SpokenLanguage.TAMIL to "தலையை அசைக்காமல் வைத்திருங்கள்.",
                    SpokenLanguage.HINDI to "कृपया अपना सिर स्थिर रखें।",
                ),
                MARKER_LOST to mapOf(
                    SpokenLanguage.ENGLISH to "One moment, re-checking calibration.",
                    SpokenLanguage.TAMIL to "ஒரு நிமிடம், மறுஅளவீடு செய்கிறோம்.",
                    SpokenLanguage.HINDI to "एक क्षण, अंशांकन जाँच रहे हैं।",
                ),
            )
        }
    }
}
