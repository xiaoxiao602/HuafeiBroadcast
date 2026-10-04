package com.family.huafei

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 金额 -> 人类自然中文读法。
 * 36.00 -> 三十六元    36.20 -> 三十六元二角
 * 36.26 -> 三十六元二角六分    36.05 -> 三十六元零五分
 * 0.50  -> 五角        0.05  -> 五分    0 -> 零元
 * -5.20 -> 欠费五元二角(负数按欠费读,读绝对值)
 */
object MoneyFormatter {

    private val DIGITS = arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    private val SMALL_UNITS = arrayOf("", "十", "百", "千")

    fun toChineseReading(amount: BigDecimal): String {
        if (amount.signum() < 0) return "欠费" + toChineseReading(amount.abs())
        val plain = amount.setScale(2, RoundingMode.DOWN).toPlainString()
        val dot = plain.indexOf('.')
        val intPart = if (dot >= 0) plain.substring(0, dot) else plain
        val decPart = if (dot >= 0) plain.substring(dot + 1) else ""
        val intValue = intPart.toLong()
        val jiao = decPart.getOrNull(0)?.let { it - '0' } ?: 0
        val fen = decPart.getOrNull(1)?.let { it - '0' } ?: 0

        val sb = StringBuilder()
        if (intValue > 0) sb.append(readInteger(intValue)).append("元")
        if (jiao > 0) {
            sb.append(DIGITS[jiao]).append("角")
        } else if (fen > 0 && intValue > 0) {
            sb.append("零")
        }
        if (fen > 0) sb.append(DIGITS[fen]).append("分")
        if (sb.isEmpty()) sb.append("零元")
        return sb.toString()
    }

    /** 播报整句:余额为正读「您当前的话费余额是…」,为负读「您当前欠费…」 */
    fun balancePhrase(amount: BigDecimal): String =
        if (amount.signum() < 0) "您当前${toChineseReading(amount)}"
        else "您当前的话费余额是${toChineseReading(amount)}"

    private fun readInteger(value: Long): String {
        if (value <= 0) return "零"
        if (value >= 100_000_000L) return value.toString() // 千万以上属异常数据,直接交给 TTS 读数字
        val wan = value / 10_000L
        val rest = (value % 10_000L).toInt()
        val sb = StringBuilder()
        if (wan > 0) sb.append(readSection(wan.toInt())).append("万")
        if (rest > 0) {
            if (rest < 1000 && wan > 0) sb.append("零")
            sb.append(readSection(rest))
        }
        return sb.toString()
    }

    /** 1..9999 节内读法,零只补在中间,开头不补 */
    private fun readSection(n: Int): String {
        val sb = StringBuilder()
        var pendingZero = false
        var started = false
        val digits = intArrayOf(n / 1000, n / 100 % 10, n / 10 % 10, n % 10)
        for (i in 0..3) {
            val d = digits[i]
            if (d == 0) {
                if (started) pendingZero = true
            } else {
                if (pendingZero) {
                    sb.append("零")
                    pendingZero = false
                }
                if (i == 2 && d == 1 && !started) {
                    // 十、十一……开头省略「一」
                } else {
                    sb.append(DIGITS[d])
                }
                sb.append(SMALL_UNITS[3 - i])
                started = true
            }
        }
        return sb.toString()
    }
}
