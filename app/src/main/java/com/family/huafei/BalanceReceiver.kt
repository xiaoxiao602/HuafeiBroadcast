package com.family.huafei

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Manifest 静态注册,进程未运行时同样会被系统唤醒。
 * 只处理:等待结果状态 + 60 秒内 + 白名单运营商号码,其余短信一概不碰。
 */
class BalanceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val prefs = Prefs(context)
        val elapsed = System.currentTimeMillis() - prefs.querySentAt
        if (prefs.state == QueryState.WAITING && elapsed <= TIMEOUT_MS) {
            // 正常路径:等待结果窗口内,解析并播报
            handle(context, intent, announce = true)
        } else if (elapsed <= LATE_GRACE_MS) {
            // 宽限路径:回复迟到(如运营商限流),界面可见则补播报,否则静默更新显示
            Log.i(TAG, "late carrier sms after ${elapsed / 1000}s, silent update")
            handle(context, intent, announce = MainActivity.uiVisible)
        } else {
            Log.i(TAG, "sms received but outside query window (${elapsed / 1000}s), ignore")
        }
    }

    private fun handle(context: Context, intent: Intent, announce: Boolean) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return
        val sender = messages[0].originatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        Log.i(TAG, "sms received, sender=$sender len=${body.length} announce=$announce")
        QueryResultHandler.onCarrierSms(context, sender, body, announce)
    }

    companion object {
        private const val TAG = "Huafei"
        const val TIMEOUT_MS = 60_000L

        /** 迟到回复宽限期:查询后 10 分钟内到达的运营商短信仍会更新显示 */
        const val LATE_GRACE_MS = 10 * 60_000L
    }
}

object SmsSenderMatcher {

    /** 去掉 +86/86 前缀与空格,只留数字 */
    fun normalize(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length > 5 && digits.startsWith("86")) digits.substring(2) else digits
    }

    fun matches(rawSender: String, allowed: List<String>): Boolean {
        val sender = normalize(rawSender)
        if (sender.isEmpty()) return false
        return allowed.any { target ->
            val t = normalize(target)
            sender == t || (sender.startsWith("106") && sender.length > t.length && sender.endsWith(t))
        }
    }
}
