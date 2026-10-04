package com.family.huafei

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.widget.Toast

/**
 * 双卡手机救援提示:切换查询卡或查询无回复时弹出。
 * 两条出路:关闭非查询卡;或复制当前卡运营商的查询指令手动发送——
 * 手动发出后运营商回复仍走 BalanceReceiver 的自动识别与播报链路。
 */
object DualSimTip {

    fun isDualSim(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val subs = try {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            return false
        } ?: return false
        return subs.size >= 2
    }

    /** @param onDismiss 弹窗关闭回调(调用方用于解除防重复标记) */
    fun show(activity: Activity, profile: CarrierProfile, onDismiss: (() -> Unit)? = null) {
        AlertDialog.Builder(activity)
            .setTitle("双卡手机查询提示")
            .setMessage(
                "当前查询卡是「${profile.displayName}」。双卡手机有时会把查询短信从另一张卡发出," +
                    "或者收不到运营商回复。可以:\n\n" +
                    "1. 关闭非查询卡(路径:设置 → 双卡与移动网络),只留这张卡后再查;\n\n" +
                    "2. 手动发送:点下方按钮复制查询指令「${profile.queryCommand}」,粘贴到短信发给 " +
                    "${profile.destinationNumber},运营商回复后本应用会自动识别并播报。"
            )
            .setPositiveButton("复制查询指令") { _, _ -> copy(activity, profile.queryCommand) }
            .setNegativeButton("知道了", null)
            .setOnDismissListener { onDismiss?.invoke() }
            .show()
    }

    private fun copy(activity: Activity, command: String) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("query", command))
        Toast.makeText(activity, "已复制「$command」,粘贴到短信发给运营商即可", Toast.LENGTH_LONG).show()
    }
}
