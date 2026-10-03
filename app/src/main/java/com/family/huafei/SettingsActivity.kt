package com.family.huafei

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private var tts: TtsManager? = null

    private lateinit var simContainer: LinearLayout
    private lateinit var carrierGroup: RadioGroup
    private lateinit var rbMobile: RadioButton
    private lateinit var rbUnicom: RadioButton
    private lateinit var rbTelecom: RadioButton
    private lateinit var queryNumberEt: EditText
    private lateinit var queryCommandEt: EditText
    private lateinit var announceSw: Switch
    private lateinit var ttsBlock: View
    private lateinit var autoQuerySw: Switch
    private lateinit var vibrateSw: Switch
    private lateinit var debugSw: Switch
    private lateinit var rateBar: SeekBar
    private lateinit var rateValue: TextView
    private lateinit var parseEt: EditText
    private lateinit var parseResult: TextView
    private lateinit var rawSmsTv: TextView
    private lateinit var updateStatus: TextView

    private var suppressCarrierCallback = false
    private var updateChecking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_settings)

        simContainer = findViewById(R.id.simContainer)
        carrierGroup = findViewById(R.id.carrierGroup)
        rbMobile = findViewById(R.id.rbMobile)
        rbUnicom = findViewById(R.id.rbUnicom)
        rbTelecom = findViewById(R.id.rbTelecom)
        queryNumberEt = findViewById(R.id.queryNumberEt)
        queryCommandEt = findViewById(R.id.queryCommandEt)
        announceSw = findViewById(R.id.announceSw)
        ttsBlock = findViewById(R.id.ttsBlock)
        autoQuerySw = findViewById(R.id.autoQuerySw)
        vibrateSw = findViewById(R.id.vibrateSw)
        debugSw = findViewById(R.id.debugSw)
        rateBar = findViewById(R.id.rateBar)
        rateValue = findViewById(R.id.rateValue)
        parseEt = findViewById(R.id.parseEt)
        parseResult = findViewById(R.id.parseResult)
        rawSmsTv = findViewById(R.id.rawSmsTv)
        updateStatus = findViewById(R.id.updateStatus)
        findViewById<Button>(R.id.ttsTestBtn).setOnClickListener { testTts() }
        findViewById<Button>(R.id.parseBtn).setOnClickListener { runParseTest() }
        findViewById<Button>(R.id.permBtn).setOnClickListener { requestAppPermissions() }
        findViewById<Button>(R.id.miuiPermBtn).setOnClickListener { openMiuiPermissionEditor() }
        findViewById<Button>(R.id.simulateSmsBtn).setOnClickListener { simulateCarrierSms() }
        findViewById<Button>(R.id.checkUpdateBtn).setOnClickListener { checkUpdate() }
        findViewById<Button>(R.id.resetCmdBtn).setOnClickListener { resetQueryDefaults() }

        findViewById<TextView>(R.id.backBtn).setOnClickListener { finish() }

        updateStatus.text = "当前版本 ${UpdateChecker.currentVersion(this)},点上方按钮检查新版本"

        bindControls()
        refreshSimList()
        renderRawSms()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
        tts = null
    }

    private fun bindControls() {
        suppressCarrierCallback = true
        carrierGroup.check(
            when (prefs.carrierId) {
                "unicom" -> R.id.rbUnicom
                "telecom" -> R.id.rbTelecom
                else -> R.id.rbMobile
            }
        )
        suppressCarrierCallback = false

        carrierGroup.setOnCheckedChangeListener { _, checkedId ->
            if (suppressCarrierCallback) return@setOnCheckedChangeListener
            val id = when (checkedId) {
                R.id.rbUnicom -> "unicom"
                R.id.rbTelecom -> "telecom"
                else -> "mobile"
            }
            prefs.carrierId = id
            prefs.queryNumber = ""
            prefs.queryCommand = ""
            showProfileDefaults()
            Toast.makeText(this, "已切换为${CarrierProfile.byId(id).displayName}", Toast.LENGTH_SHORT).show()
        }

        showProfileDefaults()
        queryNumberEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.queryNumber = s?.toString()?.trim() ?: ""
            }
        })
        queryCommandEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.queryCommand = s?.toString()?.trim() ?: ""
            }
        })

        announceSw.isChecked = prefs.announceEnabled
        announceSw.setOnCheckedChangeListener { _, checked ->
            prefs.announceEnabled = checked
            ttsBlock.visibility = if (checked) View.VISIBLE else View.GONE
        }
        ttsBlock.visibility = if (prefs.announceEnabled) View.VISIBLE else View.GONE

        autoQuerySw.isChecked = prefs.autoQuery
        autoQuerySw.setOnCheckedChangeListener { _, checked -> prefs.autoQuery = checked }
        vibrateSw.isChecked = prefs.vibrateEnabled
        vibrateSw.setOnCheckedChangeListener { _, checked -> prefs.vibrateEnabled = checked }
        debugSw.isChecked = prefs.debugMode
        debugSw.setOnCheckedChangeListener { _, checked ->
            prefs.debugMode = checked
            if (!checked) prefs.lastRawSms = ""
            renderRawSms()
        }

        rateBar.min = 50   // x0.5
        rateBar.max = 150  // x1.5
        rateBar.progress = (prefs.speechRate * 50).toInt().coerceIn(50, 150)
        rateValue.text = "x${"%.1f".format(prefs.speechRate)}"
        rateBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rate = progress / 50f
                prefs.speechRate = rate
                rateValue.text = "x${"%.1f".format(rate)}"
                tts?.rate = rate
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
    }

    private fun showProfileDefaults() {
        val base = if (prefs.carrierId.isEmpty()) CarrierProfile.MOBILE else CarrierProfile.byId(prefs.carrierId)
        queryNumberEt.setText(prefs.queryNumber.ifBlank { base.destinationNumber })
        queryCommandEt.setText(prefs.queryCommand.ifBlank { base.queryCommand })
    }

    /** 号码/指令被改坏时一键回到当前运营商的默认查询方式 */
    private fun resetQueryDefaults() {
        prefs.queryNumber = ""
        prefs.queryCommand = ""
        showProfileDefaults()
        Toast.makeText(
            this,
            "已恢复${CarrierProfile.byId(prefs.carrierId).displayName}默认查询方式",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun missingPermissions(): List<String> = listOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_PHONE_STATE
    ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    private fun requestAppPermissions() {
        val missing = missingPermissions()
        if (missing.isEmpty()) {
            Toast.makeText(this, "权限已就绪", Toast.LENGTH_SHORT).show()
            refreshSimList()
            return
        }
        requestPermissions(missing.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(
                this,
                "权限已授予。小米手机请再点「小米权限设置」→「其他权限」→「通知类短信」→ 始终允许",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "未授权将无法查询话费", Toast.LENGTH_LONG).show()
        }
        refreshSimList()
    }

    private fun refreshSimList() {
        simContainer.removeAllViews()
        if (missingPermissions().isNotEmpty()) {
            simContainer.addView(TextView(this).apply {
                text = "需要短信与电话权限后,才能读取手机卡。点击下方「授权短信权限」。"
                textSize = 17f
                setPadding(0, 8, 0, 8)
            })
            return
        }
        val subs = try {
            getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList
        } catch (e: Exception) {
            Log.w(TAG, "list subs failed: ${e.message}")
            null
        }
        if (subs.isNullOrEmpty()) {
            simContainer.addView(TextView(this).apply {
                text = if (SubscriptionManager.getDefaultSmsSubscriptionId() > 0) {
                    "未读取到卡槽信息(部分小米系统限制),将使用默认短信卡查询。单卡手机无影响。"
                } else {
                    "没有检测到手机卡,请让家人检查手机"
                }
                textSize = 17f
                setPadding(0, 8, 0, 8)
            })
            return
        }
        // 只有一张卡时自动认作查询卡并保存,与主界面查询单卡兜底一致,
        // 避免"正在用这张卡查询,设置页却显示未选"
        if (prefs.subId == -1 && subs.size == 1) {
            prefs.subId = subs[0].subscriptionId
            prefs.subIccid = subs[0].iccId ?: ""
        }
        subs.forEach { sub ->
            simContainer.addView(makeSimRow(sub))
        }
    }

    /** 每张卡渲染成整行可点的选中态卡片行,代替原先"文字+按钮"的两段式 */
    private fun makeSimRow(sub: SubscriptionInfo): View {
        val selected = sub.subscriptionId == prefs.subId
        val carrierName = CarrierProfile.guessByMccMnc(mccOf(sub), mncOf(sub))?.displayName
        val dot = View(this).apply {
            setBackgroundResource(R.drawable.sim_dot)
            isSelected = selected
            layoutParams = LinearLayout.LayoutParams(20.dp, 20.dp).apply {
                marginEnd = 14.dp
            }
        }
        val label = TextView(this).apply {
            textSize = 20f
            setTextColor(getColor(if (selected) R.color.huafei_green else R.color.text_dark))
            text = buildString {
                append(sub.displayName)
                append(" 卡槽").append(sub.simSlotIndex + 1)
                carrierName?.let { append(" · ").append(it) }
            }
        }
        val subLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(getColor(if (selected) R.color.huafei_green else R.color.text_gray))
            text = if (selected) "当前用这张卡查询" else "点一下选用这张卡"
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        textCol.addView(label)
        textCol.addView(subLabel)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_sim_row)
            isSelected = selected
            setPadding(14.dp, 12.dp, 14.dp, 12.dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp }
            setOnClickListener { selectSub(sub) }
            addView(dot)
            addView(textCol)
        }
    }

    private fun selectSub(sub: SubscriptionInfo) {
        prefs.subId = sub.subscriptionId
        prefs.subIccid = sub.iccId ?: ""
        if (prefs.carrierId.isEmpty()) {
            CarrierProfile.guessByMccMnc(mccOf(sub), mncOf(sub))?.let { prefs.carrierId = it.id }
        }
        Toast.makeText(this, "已选择:${sub.displayName} 卡槽${sub.simSlotIndex + 1}", Toast.LENGTH_SHORT).show()
        refreshSimList()
    }

    private fun mccOf(sub: SubscriptionInfo): String =
        if (Build.VERSION.SDK_INT >= 29) sub.mccString ?: "" else sub.mcc.toString()

    private fun mncOf(sub: SubscriptionInfo): String =
        if (Build.VERSION.SDK_INT >= 29) sub.mncString ?: "" else sub.mnc.toString()

    private fun testTts() {
        if (tts == null) tts = TtsManager(this)
        tts?.rate = prefs.speechRate
        val ok = tts?.speak("您好,这是话费播报测试,当前余额三十六元二角") ?: false
        if (!ok) {
            // speak() 已把文本排队,引擎初始化完成后会自动播报
            parseResult.text = "语音引擎正在启动,启动后会自动播报;若长时间没有声音,请稍后再按一次"
        }
    }

    private fun runParseTest() {
        val body = parseEt.text.toString().trim()
        if (body.isEmpty()) {
            parseResult.text = "请先粘贴一段运营商短信"
            return
        }
        val parser = ParserFactory.forId(prefs.effectiveProfile().parserType)
        val amount = parser.parse(body)
        parseResult.text = if (amount == null) {
            "识别结果:未识别出话费余额"
        } else {
            "识别结果:${amount.toPlainString()} 元\n播报:${MoneyFormatter.toChineseReading(amount)}"
        }
    }

    /** 直达小米安全中心的权限页:MIUI 层短信权限被关时,广播不会送达,需在此打开 */
    private fun openMiuiPermissionEditor() {
        if (!PermissionHelper.openMiuiPermissionEditor(this)) {
            Toast.makeText(
                this,
                "请手动打开:安全中心 → 应用管理 → 权限 → 话费播报 →「设置相关」→「通知类短信」→ 始终允许",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun simulateCarrierSms() {
        Toast.makeText(this, "5秒后将模拟收到运营商余额短信,可按Home键测试后台播报", Toast.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({
            SmsSimulator.deliver(
                applicationContext, "10086",
                "尊敬的客户,截至10月2日,您的账户可用余额为36.20元。"
            )
        }, 5_000)
    }

    /** 手动检查 GitHub 上的最新版本;平时不联网,点这一下才联网 */
    private fun checkUpdate() {
        if (updateChecking) return
        updateChecking = true
        val local = UpdateChecker.currentVersion(this)
        updateStatus.setTextColor(getColor(R.color.text_gray))
        updateStatus.text = "正在检查更新……(本机版本 $local)"
        updateStatus.setOnClickListener(null)
        UpdateChecker.checkAsync(this) { result ->
            updateChecking = false
            when (result.status) {
                UpdateChecker.Status.NEWER -> {
                    updateStatus.setTextColor(getColor(R.color.huafei_green))
                    updateStatus.text = "本机版本 $local,发现新版本 ${result.latestVersion},点这里下载安装"
                    updateStatus.setOnClickListener {
                        VibrateHelper.tick(this)
                        UpdateChecker.openDownload(this, result.apkUrl)
                    }
                }
                UpdateChecker.Status.UP_TO_DATE -> {
                    updateStatus.setTextColor(getColor(R.color.text_gray))
                    updateStatus.text = "本机版本 $local 已是最新"
                }
                UpdateChecker.Status.FAILED -> {
                    updateStatus.setTextColor(getColor(R.color.error_red))
                    updateStatus.text = "检查失败(${result.message}),请确认手机能上网后再试"
                }
            }
        }
    }

    private fun renderRawSms() {
        rawSmsTv.text = if (prefs.debugMode && prefs.lastRawSms.isNotEmpty()) {
            "最近收到的原始短信:\n${prefs.lastRawSms}"
        } else {
            "开启调试模式后,将保存最近一条运营商原始短信,便于家属调整解析规则。"
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "Huafei"
        private const val REQ_PERMS = 42
    }
}
