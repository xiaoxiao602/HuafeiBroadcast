package com.family.huafei

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

/**
 * 首次使用引导:授权(短信/电话,小米机型再放行通知类短信) + 选择运营商。
 * 两步都完成前,主界面不允许查询。
 * 权限状态每次回到本页(onResume)都会重算并逐项显示——中途被安全中心等页面
 * 挡住再回来,家属也能看到当前走到哪一步、还差什么。
 */
class SetupActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var permSendState: TextView
    private lateinit var permRecvState: TextView
    private lateinit var permPhoneState: TextView
    private lateinit var permStatus: TextView
    private lateinit var grantBtn: Button
    private lateinit var deniedBox: View
    private lateinit var appDetailsBtn: Button
    private lateinit var miuiBlock: View
    private lateinit var miuiPermBtn: Button
    private lateinit var carrierGroup: RadioGroup
    private lateinit var doneBtn: Button

    private var suppressCarrierCallback = false
    private var miuiDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_setup)

        permSendState = findViewById(R.id.permSendState)
        permRecvState = findViewById(R.id.permRecvState)
        permPhoneState = findViewById(R.id.permPhoneState)
        permStatus = findViewById(R.id.permStatus)
        grantBtn = findViewById(R.id.grantBtn)
        deniedBox = findViewById(R.id.deniedBox)
        appDetailsBtn = findViewById(R.id.appDetailsBtn)
        miuiBlock = findViewById(R.id.miuiBlock)
        miuiPermBtn = findViewById(R.id.miuiPermBtn)
        carrierGroup = findViewById(R.id.setupCarrierGroup)
        doneBtn = findViewById(R.id.doneBtn)

        grantBtn.setOnClickListener { requestPerms() }
        appDetailsBtn.setOnClickListener { openAppDetails() }
        miuiPermBtn.setOnClickListener { openMiuiEditor() }
        doneBtn.setOnClickListener {
            prefs.setupDone = true
            VibrateHelper.tick(this)
            Toast.makeText(this, "设置完成,可以查询话费了", Toast.LENGTH_SHORT).show()
            finish()
        }
        carrierGroup.setOnCheckedChangeListener { _, checkedId ->
            if (suppressCarrierCallback) return@setOnCheckedChangeListener
            prefs.carrierId = when (checkedId) {
                R.id.setupRbUnicom -> "unicom"
                R.id.setupRbTelecom -> "telecom"
                else -> "mobile"
            }
            prefs.queryNumber = ""
            prefs.queryCommand = ""
            refresh()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onBackPressed() {
        if (!prefs.setupDone) {
            Toast.makeText(this, "请先完成设置,完成后才能使用", Toast.LENGTH_SHORT).show()
            return
        }
        super.onBackPressed()
    }

    private fun refresh() {
        renderPermRow(permSendState, Manifest.permission.SEND_SMS)
        renderPermRow(permRecvState, Manifest.permission.RECEIVE_SMS)
        renderPermRow(permPhoneState, Manifest.permission.READ_PHONE_STATE)

        val missing = PermissionHelper.missingRuntime(this)
        if (missing.isEmpty()) {
            permStatus.setTextColor(getColor(R.color.huafei_green))
            permStatus.text = "第 1 步完成:短信与电话权限已就绪"
            grantBtn.text = "重新授权"
        } else {
            permStatus.setTextColor(getColor(R.color.error_red))
            permStatus.text = "第 1 步未完成:还有 ${missing.size} 项权限未允许,点下方按钮授权"
            grantBtn.text = "一键授权"
        }

        // 「拒绝且不再询问」后 requestPermissions 会静默失败,按钮像坏了一样;
        // 检测到这种情况就把用户导向系统应用设置页手动打开
        val askedBefore = prefs.permAsked
        val permanentlyDenied = missing.any { askedBefore && !shouldShowRequestPermissionRationale(it) }
        deniedBox.visibility = if (permanentlyDenied) View.VISIBLE else View.GONE

        miuiBlock.visibility = if (PermissionHelper.isXiaomi()) View.VISIBLE else View.GONE

        suppressCarrierCallback = true
        carrierGroup.check(
            when (prefs.carrierId) {
                "unicom" -> R.id.setupRbUnicom
                "telecom" -> R.id.setupRbTelecom
                "mobile" -> R.id.setupRbMobile
                else -> -1
            }
        )
        suppressCarrierCallback = false

        val ready = missing.isEmpty() && prefs.carrierId.isNotEmpty()
        doneBtn.isEnabled = ready
        doneBtn.alpha = if (ready) 1f else 0.5f
    }

    private fun renderPermRow(state: TextView, permission: String) {
        val granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        state.text = if (granted) "✓ 已允许" else "✗ 未允许"
        state.setTextColor(getColor(if (granted) R.color.huafei_green else R.color.error_red))
    }

    private fun requestPerms() {
        val missing = PermissionHelper.missingRuntime(this)
        if (missing.isEmpty()) {
            Toast.makeText(this, "权限已就绪", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.permAsked = true
        requestPermissions(missing.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        refresh()
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (PermissionHelper.isXiaomi() && !miuiDialogShown) {
                miuiDialogShown = true
                maybeOpenMiuiEditor()
            }
        } else {
            Toast.makeText(this, "权限不齐无法查询话费,页面里有每项权限的状态", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppDetails() {
        if (!PermissionHelper.openAppDetails(this)) {
            Toast.makeText(this, "请手动打开:系统设置 → 应用管理 → 话费播报 → 权限", Toast.LENGTH_LONG).show()
        }
    }

    private fun openMiuiEditor() {
        if (!PermissionHelper.openMiuiPermissionEditor(this)) {
            Toast.makeText(
                this,
                "请手动打开:安全中心 → 应用管理 → 权限 → 话费播报 → 其他权限 → 设置相关 → 通知类短信 → 始终允许",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** 小米机型:运行时权限就绪后,弹一次指引并主动拉起安全中心权限页(页面里也保留常驻按钮) */
    private fun maybeOpenMiuiEditor() {
        AlertDialog.Builder(this)
            .setTitle("小米手机还要一步")
            .setMessage("打开权限页后,点「其他权限」,往下滑到「设置相关」,点「通知类短信」,选「始终允许」。若页面没弹出来,回本页再点一次按钮。")
            .setPositiveButton("打开权限设置") { _, _ ->
                if (!PermissionHelper.openMiuiPermissionEditor(this)) {
                    openMiuiEditor()
                }
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    companion object {
        private const val REQ_PERMS = 43
    }
}
