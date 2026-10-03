package com.family.huafei

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 查询超时兜底:由系统闹钟在发送后 60 秒触发,进程被杀、界面不在前台同样生效。
 */
class TimeoutReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        QueryResultHandler.onQueryTimeout(context)
    }
}
