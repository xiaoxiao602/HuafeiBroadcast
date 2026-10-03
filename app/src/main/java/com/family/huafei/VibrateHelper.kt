package com.family.huafei

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 轻微震动反馈,遵守家属设置里的震动开关。 */
object VibrateHelper {

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** 点击查询:轻微一震 */
    fun tick(context: Context) = vibrate(context, 60)

    /** 查询成功:两下轻震 */
    fun double(context: Context) {
        if (!Prefs(context).vibrateEnabled) return
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 90, 50), -1))
    }

    private fun vibrate(context: Context, ms: Long) {
        if (!Prefs(context).vibrateEnabled) return
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
