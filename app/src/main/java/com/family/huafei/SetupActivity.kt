package com.family.huafei

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
 */
class SetupActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var grantBtn: Button
    private lateinit var permStatus: TextView
    private lateinit var xiaomiHint: TextView
    private lateinit var carrierGroup: RadioGroup
    private lateinit var doneBtn: Button

    private var suppressCarrierCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_setup)

        grantBtn = findViewById(R.id.grantBtn)
        permStatus = findViewById(R.id.permStatus)
        xiaomiHint = findViewById(R.id.xiaomiHint)
        carrierGroup = findViewById(R.id.setupCarrierGroup)
        doneBtn = findViewById(R.id.doneBtn)

        grantBtn.setOnClickListener { requestPerms() }
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
        val missing = PermissionHelper.missingRuntime(this)
        if (missing.isEmpty()) {
            permStatus.setTextColor(getColor(R.color.huafei_green))
            permStatus.text = "✔ 短信与电话权限已就绪"
            grantBtn.text = "重新授权"
        } else {
            permStatus.setTextColor(getColor(R.color.error_red))
            permStatus.text = "还有 ${missing.size} 项权限未授予,点上方按钮授权"
            grantBtn.text = "一键授权"
        }
        xiaomiHint.visibility = if (PermissionHelper.isXiaomi()) View.VISIBLE else View.GONE

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

    private fun requestPerms() {
        val missing = PermissionHelper.missingRuntime(this)
        if (missing.isEmpty()) {
            Toast.makeText(this, "权限已就绪", Toast.LENGTH_SHORT).show()
            if (PermissionHelper.isXiaomi()) maybeOpenMiuiEditor()
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
        refresh()
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (PermissionHelper.isXiaomi()) maybeOpenMiuiEditor()
        } else {
            Toast.makeText(this, "权限不齐无法查询话费,请重新授权", Toast.LENGTH_LONG).show()
        }
    }

    /** 小米机型:运行时权限就绪后,主动拉起安全中心权限页让家属放行「通知类短信」 */
    private fun maybeOpenMiuiEditor() {
        AlertDialog.Builder(this)
            .setTitle("小米手机还要一步")
            .setMessage("在接下来打开的页面里,请把「短信、通知类短信、电话」都设为允许(无需自启动),否则收不到运营商的回复短信。")
            .setPositiveButton("打开权限设置") { _, _ ->
                if (!PermissionHelper.openMiuiPermissionEditor(this)) {
                    Toast.makeText(
                        this,
                        "请手动打开:安全中心 → 应用管理 → 权限 → 话费播报,允许短信、通知类短信和电话",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    companion object {
        private const val REQ_PERMS = 43
    }
}
