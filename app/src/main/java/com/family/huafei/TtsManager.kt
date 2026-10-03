package com.family.huafei

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 系统 TTS 封装:优先简体中文、优先离线 Voice。
 * 国产 ROM 常未设置默认 TTS 引擎,初始化失败时显式尝试小米引擎。
 * TTS 不可用不影响查询,只影响播报。
 */
class TtsManager(context: Context) {

    private val appContext = context.applicationContext
    private var fallbackTried = false
    private var tts: TextToSpeech? = null
    private var pendingText: String? = null

    @Volatile
    var ready = false
        private set

    var rate: Float = 1.0f

    private val initListener = TextToSpeech.OnInitListener { status -> onTtsInit(status) }

    private fun onTtsInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        Log.i(TAG, "TTS init status=$status ready=$ready")
        if (ready) {
            setupLanguage()
            pendingText?.let { text ->
                pendingText = null
                speak(text)
            }
        } else if (!fallbackTried) {
            fallbackTried = true
            Log.i(TAG, "default TTS engine failed, retry with Xiaomi engine")
            try {
                tts?.shutdown()
            } catch (e: Exception) {
            }
            tts = TextToSpeech(appContext, initListener, XIAOMI_ENGINE)
        }
    }

    init {
        tts = TextToSpeech(appContext, initListener)
    }

    private fun setupLanguage() {
        val engine = tts ?: return
        val result = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        Log.i(TAG, "setLanguage(zh-CN)=$result")
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "zh-CN not available in current engine")
            return
        }
        try {
            val voices = engine.voices.orEmpty()
            voices.forEach { v ->
                Log.i(TAG, "voice: ${v.name} ${v.locale} network=${v.isNetworkConnectionRequired}")
            }
            // 音色默认跟随引擎;若引擎提供离线小爱音色则显式指定(家属期望听到小爱同学的声音)
            val xiaoai = voices.firstOrNull {
                it.locale.language == "zh" && !it.isNetworkConnectionRequired &&
                    XIAOAI_VOICE.containsMatchIn(it.name)
            }
            if (xiaoai != null) {
                Log.i(TAG, "select offline xiaoai voice: ${xiaoai.name}")
                engine.setVoice(xiaoai)
            } else {
                Log.i(TAG, "no offline xiaoai voice matched, using engine default voice")
            }
        } catch (e: Exception) {
            Log.w(TAG, "inspect voices failed: ${e.message}")
        }
        engine.setSpeechRate(rate)
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                TtsVolumeBoost.restore(appContext)
            }

            override fun onError(utteranceId: String?) {
                TtsVolumeBoost.restore(appContext)
            }
        })
    }

    fun speak(text: String): Boolean {
        if (!ready) {
            Log.w(TAG, "TTS not ready, queue text")
            pendingText = text
            return false
        }
        val engine = tts ?: return false
        engine.setSpeechRate(rate)
        TtsVolumeBoost.boost(appContext)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "huafei_${System.currentTimeMillis()}")
        Log.i(TAG, "speak [$text] result=$result")
        return result == TextToSpeech.SUCCESS
    }

    fun shutdown() {
        TtsVolumeBoost.restore(appContext)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "Huafei"
        private const val XIAOMI_ENGINE = "com.xiaomi.mibrain.speech"
        private val XIAOAI_VOICE = Regex("xiaoai|xiao_ai", RegexOption.IGNORE_CASE)
    }
}
