package com.family.huafei

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class BalanceParserTest {

    private val mobile = ParserFactory.forId("mobile")
    private val unicom = ParserFactory.forId("unicom")
    private val telecom = ParserFactory.forId("telecom")
    private val fallback = ParserFactory.forId("other")

    private fun assertAmount(expected: String, actual: BigDecimal?) =
        assertEquals(BigDecimal(expected), actual)

    // ---- 文档要求的基础用例 ----

    @Test fun `余额为36元`() = assertAmount("36", mobile.parse("话费余额为36元"))

    @Test fun `余额为36点2元`() = assertAmount("36.2", mobile.parse("话费余额为36.2元"))

    @Test fun `余额为36点20元`() = assertAmount("36.20", mobile.parse("话费余额为36.20元"))

    @Test fun `账户余额冒号`() = assertAmount("36.20", mobile.parse("账户余额:36.20元"))

    @Test fun `可用余额带空格`() = assertAmount("36.20", mobile.parse("可用余额 36.20 元"))

    @Test fun `当前余额人民币符号`() = assertAmount("36.20", mobile.parse("当前余额为￥36.20元"))

    // ---- 真实运营商短信 ----

    @Test fun `移动完整回复`() {
        val body = "尊敬的客户,截至10月2日,您的账户可用余额为36.20元。"
        assertAmount("36.20", mobile.parse(body))
    }

    @Test fun `移动带消费明细`() {
        val body = "您本月已消费42.15元,其中套餐费18.00元,当前话费余额为36.20元,祝您生活愉快!"
        assertAmount("36.20", mobile.parse(body))
    }

    @Test fun `联通完整回复`() {
        val body = "尊敬的用户,您当前账户余额为18.60元,本月已使用话费31.40元。"
        assertAmount("18.60", unicom.parse(body))
    }

    @Test fun `电信完整回复`() {
        val body = "尊敬的用户,截至10月02日,当前可用余额12.34元,本月消费25.00元。"
        assertAmount("12.34", telecom.parse(body))
    }

    // ---- 干扰案例:不能抓错金额 ----

    @Test fun `消费与余额并存取余额`() {
        val body = "本月消费36.20元,账户余额20.50元"
        assertAmount("20.50", mobile.parse(body))
    }

    @Test fun `套餐费与余额并存取余额`() {
        val body = "套餐费59元,余额100元"
        assertAmount("100", mobile.parse(body))
    }

    @Test fun `剩余流量不误报`() = assertNull(mobile.parse("您剩余流量36.20GB,国内流量充足"))

    @Test fun `只有消费金额不误报`() = assertNull(mobile.parse("您本月消费36.20元"))

    @Test fun `欠费金额不误报`() = assertNull(mobile.parse("您尚有欠费5.00元,请及时充值"))

    @Test fun `负余额解析为负值`() = assertAmount("-5.00", mobile.parse("您的余额为-5.00元,请及时充值"))

    @Test fun `兜底解析器解析负余额`() = assertAmount("-5.20", fallback.parse("当前余额为-5.20元"))

    @Test fun `移动额外正则解析负余额`() = assertAmount("-3.50", mobile.parse("话费余额为:-3.50元"))

    @Test fun `负数消费解析为负值`() = assertAmount("-8.00", mobile.parseConsumption("本月消费-8.00元,余额20元"))

    @Test fun `赠送金额不误报`() {
        val body = "赠送金额10元已到账,当前可用余额36.20元"
        assertAmount("36.20", mobile.parse(body))
    }

    @Test fun `充值金额不误报`() {
        val body = "您充值50元已到账,当前余额36.20元"
        assertAmount("36.20", mobile.parse(body))
    }

    @Test fun `两个余额得分并列则放弃`() = assertNull(mobile.parse("余额36.20元,余额18.00元"))

    // ---- 兜底解析器 ----

    @Test fun `兜底解析器识别`() = assertAmount("8.88", fallback.parse("您的话费余额:8.88元"))

    @Test fun `兜底解析器拒绝无关文本`() = assertNull(fallback.parse("积分可兑换36.20元话费券"))

    // ---- 本月消费解析 ----

    @Test fun `本月产生话费解析为消费`() {
        val body = "尊敬的客户，您本月产生话费16.00元，当前可用余额24.89元。"
        assertAmount("16.00", mobile.parseConsumption(body))
        assertAmount("24.89", mobile.parse(body))
    }

    @Test fun `本月已消费解析为消费`() {
        val body = "您本月已消费42.15元,当前话费余额为36.20元,祝您生活愉快!"
        assertAmount("42.15", mobile.parseConsumption(body))
    }

    @Test fun `消费合计解析为消费`() {
        val body = "您本月消费合计15.30元,话费余额66.00元"
        assertAmount("15.30", mobile.parseConsumption(body))
    }

    @Test fun `余额金额不算消费`() = assertNull(mobile.parseConsumption("账户余额20.50元"))

    @Test fun `无消费关键词返回空`() = assertNull(mobile.parseConsumption("套餐费59元,余额100元"))

    // ---- 发送方白名单 ----

    @Test fun `白名单精确匹配`() {
        assertEquals(true, SmsSenderMatcher.matches("10086", listOf("10086")))
        assertEquals(true, SmsSenderMatcher.matches("+8610086", listOf("10086")))
        assertEquals(true, SmsSenderMatcher.matches("1065810086", listOf("10086")))
        assertEquals(false, SmsSenderMatcher.matches("10010", listOf("10086")))
        assertEquals(false, SmsSenderMatcher.matches("10612345", listOf("10086")))
    }
}
