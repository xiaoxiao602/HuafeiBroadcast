package com.family.huafei

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * 播报期间把媒体音量临时调到最大值的 80%,播报结束后还原。
 * boost 幂等(重复调用不会覆盖保存的原音量),restore 成对调用。
 */
object TtsVolumeBoost {

    private var savedVolume = -1

    fun boost(context: Context) {
        if (savedVolume >= 0) return
        try {
            val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) return
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val target = max * 80 / 100
            savedVolume = current
            if (target != current) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                Log.i("Huafei", "volume boost: $current -> $target (max=$max)")
            }
        } catch (e: Exception) {
            Log.w("Huafei", "volume boost failed: ${e.message}")
            savedVolume = -1
        }
    }

    fun restore(context: Context) {
        val saved = savedVolume
        if (saved < 0) return
        savedVolume = -1
        try {
            val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
            Log.i("Huafei", "volume restored -> $saved")
        } catch (e: Exception) {
            Log.w("Huafei", "volume restore failed: ${e.message}")
        }
    }
}
