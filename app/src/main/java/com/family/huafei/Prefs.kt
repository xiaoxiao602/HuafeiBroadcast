package com.family.huafei

import android.content.Context

/** 仅保存:余额缓存/查询时间/运营商与SIM设置/TTS设置/最近一次错误,不存短信历史。 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("huafei", Context.MODE_PRIVATE)

    var subId: Int
        get() = sp.getInt(KEY_SUB_ID, -1)
        set(value) = sp.edit().putInt(KEY_SUB_ID, value).apply()

    var subIccid: String
        get() = sp.getString(KEY_SUB_ICCID, "") ?: ""
        set(value) = sp.edit().putString(KEY_SUB_ICCID, value).apply()

    var carrierId: String
        get() = sp.getString(KEY_CARRIER_ID, "") ?: ""
        set(value) = sp.edit().putString(KEY_CARRIER_ID, value).apply()

    /** 首次使用引导(授权+选运营商)是否完成,完成前不允许查询 */
    var setupDone: Boolean
        get() = sp.getBoolean(KEY_SETUP_DONE, false)
        set(value) = sp.edit().putBoolean(KEY_SETUP_DONE, value).apply()

    /** 是否至少点过一次「一键授权」:用于识别「拒绝且不再询问」的静默失败 */
    var permAsked: Boolean
        get() = sp.getBoolean(KEY_PERM_ASKED, false)
        set(value) = sp.edit().putBoolean(KEY_PERM_ASKED, value).apply()

    var queryNumber: String
        get() = sp.getString(KEY_QUERY_NUMBER, "") ?: ""
        set(value) = sp.edit().putString(KEY_QUERY_NUMBER, value).apply()

    var queryCommand: String
        get() = sp.getString(KEY_QUERY_COMMAND, "") ?: ""
        set(value) = sp.edit().putString(KEY_QUERY_COMMAND, value).apply()

    var autoQuery: Boolean
        get() = sp.getBoolean(KEY_AUTO_QUERY, false)
        set(value) = sp.edit().putBoolean(KEY_AUTO_QUERY, value).apply()

    var vibrateEnabled: Boolean
        get() = sp.getBoolean(KEY_VIBRATE, true)
        set(value) = sp.edit().putBoolean(KEY_VIBRATE, value).apply()

    var debugMode: Boolean
        get() = sp.getBoolean(KEY_DEBUG, false)
        set(value) = sp.edit().putBoolean(KEY_DEBUG, value).apply()

    var speechRate: Float
        get() = sp.getFloat(KEY_SPEECH_RATE, 1.0f)
        set(value) = sp.edit().putFloat(KEY_SPEECH_RATE, value).apply()

    var lastBalance: String
        get() = sp.getString(KEY_LAST_BALANCE, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_BALANCE, value).apply()

    var lastConsumption: String
        get() = sp.getString(KEY_LAST_CONSUMPTION, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_CONSUMPTION, value).apply()

    var lastQueryTime: Long
        get() = sp.getLong(KEY_LAST_QUERY_TIME, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_QUERY_TIME, value).apply()

    var querySentAt: Long
        get() = sp.getLong(KEY_QUERY_SENT_AT, 0L)
        set(value) = sp.edit().putLong(KEY_QUERY_SENT_AT, value).apply()

    var lastError: String
        get() = sp.getString(KEY_LAST_ERROR, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_ERROR, value).apply()

    var lastErrorAt: Long
        get() = sp.getLong(KEY_LAST_ERROR_AT, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_ERROR_AT, value).apply()

    var lastRawSms: String
        get() = sp.getString(KEY_LAST_RAW_SMS, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_RAW_SMS, value).apply()

    var state: QueryState
        get() = QueryState.valueOf(sp.getString(KEY_STATE, QueryState.IDLE.name) ?: QueryState.IDLE.name)
        set(value) = sp.edit().putString(KEY_STATE, value.name).apply()

    /** 生效的运营商配置:家属自定义号码/指令优先,否则用默认 Profile */
    fun effectiveProfile(): CarrierProfile {
        val base = if (carrierId.isEmpty()) CarrierProfile.MOBILE else CarrierProfile.byId(carrierId)
        return base.copy(
            destinationNumber = queryNumber.ifBlank { base.destinationNumber },
            queryCommand = queryCommand.ifBlank { base.queryCommand }
        )
    }

    private companion object {
        const val KEY_SUB_ID = "subId"
        const val KEY_SUB_ICCID = "subIccid"
        const val KEY_CARRIER_ID = "carrierId"
        const val KEY_SETUP_DONE = "setupDone"
        const val KEY_PERM_ASKED = "permAsked"
        const val KEY_QUERY_NUMBER = "queryNumber"
        const val KEY_QUERY_COMMAND = "queryCommand"
        const val KEY_AUTO_QUERY = "autoQuery"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_DEBUG = "debug"
        const val KEY_SPEECH_RATE = "speechRate"
        const val KEY_LAST_BALANCE = "lastBalance"
        const val KEY_LAST_CONSUMPTION = "lastConsumption"
        const val KEY_LAST_QUERY_TIME = "lastQueryTime"
        const val KEY_QUERY_SENT_AT = "querySentAt"
        const val KEY_LAST_ERROR = "lastError"
        const val KEY_LAST_ERROR_AT = "lastErrorAt"
        const val KEY_LAST_RAW_SMS = "lastRawSms"
        const val KEY_STATE = "state"
    }
}
