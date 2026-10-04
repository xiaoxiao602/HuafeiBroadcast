package com.family.huafei

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyFormatterTest {

    private fun read(v: String): String = MoneyFormatter.toChineseReading(BigDecimal(v))

    @Test fun `整数元`() {
        assertEquals("三十六元", read("36"))
        assertEquals("三十六元", read("36.00"))
    }

    @Test fun `一位小数读角`() {
        assertEquals("三十六元二角", read("36.2"))
        assertEquals("三十六元二角", read("36.20"))
        assertEquals("三十五元", read("35.00"))
    }

    @Test fun `两位小数读角分`() {
        assertEquals("三十五元二角六分", read("35.26"))
        assertEquals("三十六元零五分", read("36.05"))
    }

    @Test fun `纯小数`() {
        assertEquals("五角", read("0.50"))
        assertEquals("五角", read("0.5"))
        assertEquals("五分", read("0.05"))
    }

    @Test fun `零元`() {
        assertEquals("零元", read("0"))
        assertEquals("零元", read("0.00"))
    }

    @Test fun `整十整百`() {
        assertEquals("一百元", read("100"))
        assertEquals("一百零五元", read("105"))
        assertEquals("一百一十元", read("110"))
        assertEquals("十元", read("10"))
        assertEquals("十一元", read("11"))
        assertEquals("一千元", read("1000"))
        assertEquals("一千零五元", read("1005"))
    }

    @Test fun `万位`() {
        assertEquals("一万二千三百四十五元", read("12345"))
        assertEquals("十万元", read("100000"))
        assertEquals("一万零一元", read("10001"))
        assertEquals("二十万零六百元", read("200600"))
    }

    @Test fun `负数读欠费`() {
        assertEquals("欠费五元二角", read("-5.20"))
        assertEquals("欠费五元", read("-5.00"))
        assertEquals("欠费五角", read("-0.50"))
        assertEquals("欠费五分", read("-0.05"))
    }

    @Test fun `播报整句区分余额与欠费`() {
        assertEquals("您当前的话费余额是三十六元二角", MoneyFormatter.balancePhrase(BigDecimal("36.20")))
        assertEquals("您当前欠费五元二角", MoneyFormatter.balancePhrase(BigDecimal("-5.20")))
        assertEquals("您当前欠费五角", MoneyFormatter.balancePhrase(BigDecimal("-0.50")))
    }
}
