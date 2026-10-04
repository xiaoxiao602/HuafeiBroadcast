package com.family.huafei

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as SystemSettings
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var tts: TtsManager

    private lateinit var titleView: TextView
    private lateinit var simLine: TextView
    private lateinit var balanceView: TextView
    private lateinit var consumptionLine: TextView
    private lateinit var lastQueryView: TextView
    private lateinit var queryButton: Button
    private lateinit var statusHint: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var autoQueryPending = false
    private var sentAckReceived = false
    private var dualSimTipShowing = false

    /** 发送后 8 秒仍无系统回执:多半是点掉了系统的「发送确认」,给出指引而非干等 60 秒 */
    private val ackHintRunnable = Runnable {
        if (prefs.state == QueryState.WAITING && !sentAckReceived) {
            statusHint.setTextColor(getColor(R.color.text_gray))
            statusHint.text = "若刚才取消了发送确认,请重新点击查询"
        }
    }

    private val uiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            render()
            maybeSpeakResult()
            maybeShowDualSimTip()
        }
    }

    private val sentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            sentAckReceived = true
            val code = resultCode
            Log.i(TAG, "sms sent result code=$code")
            if (code == Activity.RESULT_OK || code == SmsManager.RESULT_ERROR_NONE) return
            if (prefs.state == QueryState.SENDING || prefs.state == QueryState.WAITING) {
                fail("短信发送失败,请稍后再试")
            }
        }
    }

    private val timeoutRunnable = Runnable {
        if (prefs.state == QueryState.WAITING) fail(QueryResultHandler.NO_REPLY_ERROR)
    }

    private val openSettingsRunnable = Runnable {
        if (prefs.state == QueryState.SENDING || prefs.state == QueryState.WAITING) return@Runnable
        VibrateHelper.tick(this)
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        tts = TtsManager(this).also { it.rate = prefs.speechRate }
        setContentView(R.layout.activity_main)
        titleView = findViewById(R.id.title)
        simLine = findViewById(R.id.simLine)
        balanceView = findViewById(R.id.balance)
        consumptionLine = findViewById(R.id.consumptionLine)
        lastQueryView = findViewById(R.id.lastQuery)
        queryButton = findViewById(R.id.queryButton)
        statusHint = findViewById(R.id.statusHint)

        queryButton.setOnClickListener { startQuery() }
        balanceView.setOnClickListener {
            if (prefs.lastBalance.isEmpty()) return@setOnClickListener
            VibrateHelper.tick(this)
            speakBalance()
        }
        findViewById<TextView>(R.id.settingsBtn).setOnClickListener {
            VibrateHelper.tick(this)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        titleView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> handler.postDelayed(openSettingsRunnable, 5_000)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    handler.removeCallbacks(openSettingsRunnable)
            }
            true
        }
        if (savedInstanceState == null && prefs.autoQuery) autoQueryPending = true
        Log.i(TAG, "onCreate state=${prefs.state} autoQueryPending=$autoQueryPending")
    }

    override fun onStart() {
        super.onStart()
        uiVisible = true
        val resultFilter = IntentFilter(ACTION_RESULT)
        val sentFilter = IntentFilter(ACTION_SMS_SENT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(uiReceiver, resultFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(sentReceiver, sentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(uiReceiver, resultFilter)
            registerReceiver(sentReceiver, sentFilter)
        }
    }

    override fun onStop() {
        super.onStop()
        uiVisible = false
        unregisterReceiver(uiReceiver)
        unregisterReceiver(sentReceiver)
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.setupDone) {
            // 首次使用(或清除数据后):先完成授权+选运营商,才能查询
            startActivity(Intent(this, SetupActivity::class.java))
        }
        healWaitingState()
        // 后台闹钟超时后回到界面:状态已是 FAILED 且错误是「无回复」,补弹双卡提示
        maybeShowDualSimTip()
        render()
        if (autoQueryPending) {
            autoQueryPending = false
            val missing = missingPermissions()
            if (missing.isEmpty()) {
                handler.postDelayed({
                    if (prefs.state == QueryState.IDLE) startQuery()
                }, 1_500)
            } else {
                statusHint.setTextColor(getColor(R.color.error_red))
                statusHint.text = "查询功能需要短信权限,请让家人设置"
                announce("查询功能需要短信权限,请让家人设置")
                requestPermissions(missing.toTypedArray(), REQ_PERMS)
            }
        }
    }

    /** 进程被杀重启后,Handler 里的超时任务已丢失:恢复或治愈 WAITING 状态 */
    private fun healWaitingState() {
        if (prefs.state != QueryState.WAITING) return
        val elapsed = System.currentTimeMillis() - prefs.querySentAt
        if (elapsed >= BalanceReceiver.TIMEOUT_MS) {
            cancelTimeoutAlarm()
            prefs.state = QueryState.FAILED
            prefs.lastError = QueryResultHandler.NO_REPLY_ERROR
            prefs.lastErrorAt = System.currentTimeMillis()
        } else {
            handler.removeCallbacks(timeoutRunnable)
            handler.postDelayed(timeoutRunnable, BalanceReceiver.TIMEOUT_MS - elapsed)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        tts.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            render()
        } else {
            announce("查询功能需要短信权限,请让家人设置")
            try {
                startActivity(
                    Intent(
                        SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "open app details failed: ${e.message}")
            }
        }
    }

    private fun missingPermissions(): List<String> = listOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_PHONE_STATE
    ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    private fun startQuery() {
        if (prefs.state == QueryState.SENDING || prefs.state == QueryState.WAITING) return
        if (!prefs.setupDone) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            statusHint.setTextColor(getColor(R.color.error_red))
            statusHint.text = "查询功能需要短信权限,请让家人设置"
            announce("查询功能需要短信权限,请让家人设置")
            requestPermissions(missing.toTypedArray(), REQ_PERMS)
            return
        }
        val subId = resolveQuerySubId()
        if (subId == null) {
            fail("查询卡发生变化,请让家人重新设置")
            return
        }
        if (subId != prefs.subId && prefs.subIccid.isNotEmpty()) {
            // 卡换槽后 subscriptionId 会变,按 ICCID 重新匹配上时静默更新
            prefs.subId = subId
            Log.i(TAG, "subscription re-matched, new subId=$subId")
        }
        val profile = prefs.effectiveProfile()
        VibrateHelper.tick(this)
        prefs.state = QueryState.SENDING
        prefs.lastError = ""
        prefs.pendingSmsBody = ""
        render()
        val sentIntent = PendingIntent.getBroadcast(
            this, 0,
            Intent(ACTION_SMS_SENT).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            // getSmsManagerForSubscriptionId 自 API 22 可用;createForSubscriptionId 是 API 31+,
            // Android 8~11(如荣耀 Magic UI)会 NoSuchMethodError 闪退,不可用
            val sms = if (subId > 0) SmsManager.getSmsManagerForSubscriptionId(subId)
                      else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendTextMessage(profile.destinationNumber, null, profile.queryCommand, sentIntent, null)
            Log.i(TAG, "query sent: to=${profile.destinationNumber} cmd=${profile.queryCommand} subId=$subId")
            prefs.state = QueryState.WAITING
            prefs.querySentAt = System.currentTimeMillis()
            render()
            sentAckReceived = false
            handler.removeCallbacks(ackHintRunnable)
            handler.postDelayed(ackHintRunnable, 8_000)
            scheduleTimeoutAlarm()
            handler.removeCallbacks(timeoutRunnable)
            handler.postDelayed(timeoutRunnable, BalanceReceiver.TIMEOUT_MS)
        } catch (e: SecurityException) {
            Log.w(TAG, "send blocked by system: ${e.message}")
            fail("短信权限被系统拦截,请让家人在设置里点「小米权限设置」放行")
        } catch (e: Exception) {
            Log.w(TAG, "send failed: ${e.message}")
            fail("短信发送失败,请稍后再试")
        }
    }

    /** 所有自动播报的唯一闸口:用户在设置里关闭「语音播报」后,查询结果与错误只显示不朗读 */
    private fun announce(text: String) {
        if (!prefs.announceEnabled) return
        tts.speak(text)
    }

    private fun fail(message: String) {
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(ackHintRunnable)
        cancelTimeoutAlarm()
        prefs.state = QueryState.FAILED
        prefs.lastError = message
        prefs.lastErrorAt = System.currentTimeMillis()
        render()
        announce(message)
        maybeShowDualSimTip()
        handler.postDelayed({
            if (prefs.state == QueryState.FAILED) {
                prefs.state = QueryState.IDLE
                render()
            }
        }, 4_000)
    }

    /**
     * 双卡用户查询无回复时的两条出路:关掉非查询卡,或复制查询指令手动发送。
     * 手动发出后运营商回复仍走 BalanceReceiver 的自动识别与播报链路。
     * 触发:查询失败(含后台超时后回到界面);切换查询卡时的提示在 SettingsActivity。
     */
    private fun maybeShowDualSimTip() {
        if (dualSimTipShowing) return
        if (prefs.state != QueryState.FAILED) return
        if (prefs.lastError != QueryResultHandler.NO_REPLY_ERROR) return
        if (!DualSimTip.isDualSim(this)) return
        if (isFinishing || isDestroyed) return
        Log.i(TAG, "dual sim tip: query failed without reply, show tip")
        dualSimTipShowing = true
        DualSimTip.show(this, prefs.effectiveProfile()) { dualSimTipShowing = false }
    }

    /** 查询后安排系统闹钟兜底超时:进程被杀、清理后台后同样会触发提示 */
    private fun scheduleTimeoutAlarm() {
        val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(timeoutPendingIntent())
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + BalanceReceiver.TIMEOUT_MS,
            timeoutPendingIntent()
        )
    }

    private fun cancelTimeoutAlarm() = QueryResultHandler.cancelTimeoutAlarm(this)

    private fun timeoutPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        this, 0, Intent(this, TimeoutReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** 解析发送用 subId:先按保存的卡匹配,读不到列表时兜底用默认短信卡 */
    private fun resolveQuerySubId(): Int? {
        findSubscription()?.let { return it.subscriptionId }
        val defaultSms = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (defaultSms > 0) {
            Log.i(TAG, "subscription list unavailable, fallback to default SMS subId=$defaultSms")
            return defaultSms
        }
        Log.w(TAG, "no usable subscription")
        return null
    }

    /** 按「先 subId、后 ICCID」重新匹配当前 SIM;匹配不到视为查询卡变化 */
    private fun findSubscription(): SubscriptionInfo? {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "findSubscription: no READ_PHONE_STATE")
            return null
        }
        val subs = try {
            getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            Log.w(TAG, "findSubscription: SecurityException ${e.message}")
            null
        } ?: return null
        Log.i(TAG, "findSubscription: ${subs.size} active subs, saved subId=${prefs.subId} iccid=${prefs.subIccid}")
        if (subs.isEmpty()) return null
        val byId = subs.firstOrNull { it.subscriptionId == prefs.subId }
        if (byId != null) return byId
        if (prefs.subIccid.isNotEmpty()) {
            val byIccid = subs.firstOrNull { it.iccId == prefs.subIccid }
            if (byIccid != null) return byIccid
        }
        return if (prefs.subId == -1 && subs.size == 1) subs[0] else null
    }

    private fun describeActiveCard(): String {
        val sub = findSubscription()
        if (sub != null) {
            val carrier = if (prefs.carrierId.isEmpty()) CarrierProfile.MOBILE else CarrierProfile.byId(prefs.carrierId)
            return "查询卡:${sub.displayName} 卡槽${sub.simSlotIndex + 1} · ${carrier.displayName}"
        }
        return if (SubscriptionManager.getDefaultSmsSubscriptionId() > 0) {
            "查询卡:默认短信卡"
        } else {
            "尚未选择查询卡,点右上角设置"
        }
    }

    private fun render() {
        val now = System.currentTimeMillis()
        if (prefs.state == QueryState.FAILED && now - prefs.lastErrorAt > 30_000) {
            prefs.state = QueryState.IDLE
        }
        if (prefs.state == QueryState.SUCCESS) {
            prefs.state = QueryState.IDLE
        }
        balanceView.text = when {
            prefs.lastBalance.isEmpty() -> "¥ --"
            // 欠费显示负号在前:-¥5.20
            prefs.lastBalance.startsWith("-") -> "-¥" + prefs.lastBalance.substring(1)
            else -> "¥" + prefs.lastBalance
        }
        if (prefs.lastConsumption.isNotEmpty()) {
            consumptionLine.visibility = android.view.View.VISIBLE
            consumptionLine.text = "本月消费 ¥${prefs.lastConsumption}"
        } else {
            consumptionLine.visibility = android.view.View.GONE
        }
        lastQueryView.text = formatLastQuery(prefs.lastQueryTime)
        simLine.text = describeActiveCard()
        when (prefs.state) {
            QueryState.SENDING, QueryState.WAITING -> {
                queryButton.isEnabled = false
                queryButton.alpha = 0.6f
                queryButton.text = "正在查询……"
                statusHint.setTextColor(getColor(R.color.text_gray))
                statusHint.text =
                    if (prefs.state == QueryState.WAITING) "已发送查询短信,等待回复" else "正在发送查询短信"
            }
            else -> {
                queryButton.isEnabled = true
                queryButton.alpha = 1f
                queryButton.text = "查询话费"
                if (!prefs.setupDone) {
                    statusHint.setTextColor(getColor(R.color.error_red))
                    statusHint.text = "请先完成首次设置"
                } else if (prefs.state == QueryState.FAILED && prefs.lastError.isNotEmpty()) {
                    statusHint.setTextColor(getColor(R.color.error_red))
                    statusHint.text = prefs.lastError
                } else if (prefs.lastBalance.isNotEmpty() && now - prefs.lastQueryTime < 8_000) {
                    statusHint.setTextColor(getColor(R.color.huafei_green))
                    statusHint.text = "查询成功"
                } else {
                    statusHint.text = ""
                }
            }
        }
    }

    /** 界面在前台时,结果播报由 Activity 的 TTS 完成(接收器不发) */
    private fun maybeSpeakResult() {
        val now = System.currentTimeMillis()
        if (prefs.lastError.isNotEmpty() && now - prefs.lastErrorAt < 3_000) {
            announce(prefs.lastError)
        } else if (prefs.lastBalance.isNotEmpty() && now - prefs.lastQueryTime < 3_000) {
            speakBalance()
        }
    }

    /** 用当前缓存余额组织播报语,没有余额时不播;负数余额播「您当前欠费…」 */
    private fun speakBalance() {
        if (prefs.lastBalance.isEmpty()) return
        announce(MoneyFormatter.balancePhrase(BigDecimal(prefs.lastBalance)))
    }

    private fun formatLastQuery(ts: Long): String {
        if (ts <= 0) return "还没有查询过"
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ts }
        return if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        ) {
            "上次查询:今天 ${SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ts))}"
        } else {
            "上次查询:${SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(ts))}"
        }
    }

    companion object {
        private const val TAG = "Huafei"
        const val ACTION_RESULT = "com.family.huafei.RESULT"
        const val ACTION_SMS_SENT = "com.family.huafei.SMS_SENT"
        private const val REQ_PERMS = 41

        /** 接收器用它判断界面是否在前台:在前台则由 Activity 播报,不在则接收器自己播 */
        @Volatile
        var uiVisible = false
    }
}
