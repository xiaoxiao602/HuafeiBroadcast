package com.family.huafei

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 检查更新:家属手动点击时才联网,仅向 GitHub API 查询最新 Release 版本号,
 * 不上传任何数据;下载通过系统浏览器完成。App 其余场景保持零联网。
 */
object UpdateChecker {

    private const val TAG = "Huafei"

    enum class Status { NEWER, UP_TO_DATE, FAILED }

    data class Result(
        val status: Status,
        val latestVersion: String = "",
        val apkUrl: String = "",
        val message: String = ""
    )

    const val RELEASES_PAGE = "https://github.com/xiaoxiao602/HuafeiBroadcast/releases/latest"
    private const val API_LATEST = "https://api.github.com/repos/xiaoxiao602/HuafeiBroadcast/releases/latest"

    fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /** 后台请求 GitHub,结果回调回主线程 */
    fun checkAsync(context: Context, cb: (Result) -> Unit) {
        val appContext = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        val current = currentVersion(appContext)
        Thread {
            val result = try {
                val conn = URL(API_LATEST).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.setRequestProperty("User-Agent", "HuafeiBroadcast")
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                if (conn.responseCode != 200) {
                    Result(Status.FAILED, message = "GitHub 返回 ${conn.responseCode}")
                } else {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name").removePrefix("v").removePrefix("V")
                    var apkUrl = RELEASES_PAGE
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            if (asset.optString("name").endsWith(".apk", true)) {
                                apkUrl = asset.optString("browser_download_url", RELEASES_PAGE)
                                break
                            }
                        }
                    }
                    if (isNewer(tag, current)) Result(Status.NEWER, tag, apkUrl)
                    else Result(Status.UP_TO_DATE, tag)
                }
            } catch (e: Exception) {
                Log.w(TAG, "update check failed: ${e.message}")
                Result(Status.FAILED, message = e.message ?: "网络异常")
            }
            main.post { cb(result) }
        }.start()
    }

    /** 按段比较版本号:1.2 = 1.2.0,1.2 < 1.2.1;无法解析的段按 0 处理 */
    fun isNewer(latest: String, current: String): Boolean {
        val parse: (String) -> List<Int> = { v ->
            v.split('.').map { part -> part.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        }
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val li = l.getOrElse(i) { 0 }
            val ci = c.getOrElse(i) { 0 }
            if (li != ci) return li > ci
        }
        return false
    }

    /** 跳浏览器下载:优先直链 APK,异常时退回 Releases 页面 */
    fun openDownload(context: Context, apkUrl: String) {
        for (url in listOf(apkUrl, RELEASES_PAGE)) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                return
            } catch (e: Exception) {
                Log.w(TAG, "open download failed: $url ${e.message}")
            }
        }
    }
}
