package com.family.huafei

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.math.BigDecimal
import java.util.Locale

/**
 * 运营商短信结果处理:BalanceReceiver 与设置页「模拟短信」共用。
 * 界面在前台时由 Activity 的 TTS 播报,不在前台时由 BgTts 临时绑定播报,说完即关。
 */
object QueryResultHandler {

    fun onCarrierSms(context: Context, sender: String, body: String, announce: Boolean = true) {
        val prefs = Prefs(context)
        val profile = prefs.effectiveProfile()
        if (!SmsSenderMatcher.matches(sender, profile.allowedSenderNumbers)) {
            Log.i(TAG, "sms from $sender not in whitelist, ignore")
            return
        }
        Log.i(TAG, "carrier sms from $sender: $body")
        if (prefs.debugMode) prefs.lastRawSms = "[$sender] $body"

        val amount = ParserFactory.forId(profile.parserType).parse(body)
        val consumption = ParserFactory.forId(profile.parserType).parseConsumption(body)
        prefs.lastConsumption = consumption?.toPlainString() ?: ""
        if (amount == null) {
            failWith(context, "收到运营商短信,但没有识别出话费余额,请让家人检查", announce)
        } else {
            cancelTimeoutAlarm(context)
            prefs.lastBalance = amount.toPlainString()
            prefs.lastQueryTime = System.currentTimeMillis()
            prefs.state = QueryState.SUCCESS
            Log.i(TAG, "balance parsed: ${amount.toPlainString()} consumption=${consumption ?: "-"}")
            if (announce) {
                announce(context, "您当前的话费余额是${MoneyFormatter.toChineseReading(amount)}")
            }
            VibrateHelper.double(context)
            notifyUi(context)
        }
    }

    private fun failWith(context: Context, message: String, announce: Boolean) {
        cancelTimeoutAlarm(context)
        val prefs = Prefs(context)
        prefs.state = QueryState.FAILED
        prefs.lastError = message
        prefs.lastErrorAt = System.currentTimeMillis()
        Log.w(TAG, "parse failed: $message")
        if (announce) announce(context, message)
        notifyUi(context)
    }

    fun notifyUi(context: Context) {
        if (MainActivity.uiVisible) {
            context.sendBroadcast(Intent(MainActivity.ACTION_RESULT).setPackage(context.packageName))
        }
    }

    /**
     * 系统闹钟触发的查询超时:状态置为 FAILED 并兜底播报。
     * 界面在前台时由 Activity 自己的超时任务播报,这里只改状态防重复。
     */
    fun onQueryTimeout(context: Context) {
        val prefs = Prefs(context)
        if (prefs.state != QueryState.WAITING) return
        prefs.state = QueryState.FAILED
        prefs.lastError = "暂时没有收到运营商回复,请稍后再试"
        prefs.lastErrorAt = System.currentTimeMillis()
        Log.w(TAG, "query timeout fired by alarm")
        if (!MainActivity.uiVisible) announce(context, prefs.lastError)
        notifyUi(context)
    }

    /** 取消超时闹钟:收到结果、发送失败或状态已治愈时调用 */
    fun cancelTimeoutAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(timeoutPendingIntent(context))
    }

    private fun timeoutPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, TimeoutReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun announce(context: Context, text: String) {
        if (MainActivity.uiVisible) return // Activity 的 TtsManager 负责播报
        BgTts(context.applicationContext, text)
        Log.i(TAG, "background announce via BgTts")
    }

    private const val TAG = "Huafei"

    /**
     * 一次性后台播报器:init 成功即播,播完或出错自动 shutdown。
     * 用独立类持有引擎引用,避免在初始化 lambda 里自引用。
     */
    class BgTts(private val context: Context, private val text: String) : TextToSpeech.OnInitListener {

        private var engine: TextToSpeech? = null
        private val handler = Handler(Looper.getMainLooper())

        init {
            engine = TextToSpeech(context, this)
            handler.postDelayed({ shutdown() }, 9_000)
        }

        override fun onInit(status: Int) {
            val t = engine
            if (status != TextToSpeech.SUCCESS || t == null) {
                Log.w(TAG, "background TTS init failed")
                return
            }
            t.language = Locale.SIMPLIFIED_CHINESE
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    handler.post { shutdown() }
                }

                override fun onError(utteranceId: String?) {
                    handler.post { shutdown() }
                }
            })
            TtsVolumeBoost.boost(context)
            t.speak(text, TextToSpeech.QUEUE_ADD, null, "huafei_bg")
            Log.i(TAG, "background TTS: $text")
        }

        private fun shutdown() {
            TtsVolumeBoost.restore(context)
            try {
                engine?.stop()
                engine?.shutdown()
            } catch (e: Exception) {
            }
            engine = null
        }
    }
}

/** 供测试/家属调试:直接走与真实短信相同的结果处理链路 */
object SmsSimulator {
    fun deliver(context: Context, sender: String, body: String) {
        val prefs = Prefs(context)
        // 模拟期间置为等待状态,保证状态机与真实流程一致
        prefs.state = QueryState.WAITING
        prefs.querySentAt = System.currentTimeMillis()
        QueryResultHandler.onCarrierSms(context, sender, body)
    }
}
