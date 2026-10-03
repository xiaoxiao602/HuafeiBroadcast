package com.family.huafei

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/** 权限相关:运行时权限检查 + 小米机型打开安全中心权限页(放行通知类短信) */
object PermissionHelper {

    private const val TAG = "Huafei"

    /** 查询所需的三项运行时权限:发短信、收短信、读卡信息 */
    fun missingRuntime(activity: Activity): List<String> = listOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_PHONE_STATE
    ).filter { activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    fun isXiaomi(): Boolean {
        val maker = Build.MANUFACTURER ?: return false
        return maker.contains("Xiaomi", true) || maker.contains("Redmi", true)
    }

    /**
     * 打开 MIUI 安全中心的本应用权限页。「通知类短信」是小米特有的应用权限,
     * 系统没有公开 API 可查可授,只能引导家属到这里手动放行。
     * @return 是否成功拉起
     */
    fun openMiuiPermissionEditor(activity: Activity): Boolean = try {
        val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            .putExtra("extra_pkgname", activity.packageName)
        activity.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "open miui perm editor failed: ${e.message}")
        false
    }
}
