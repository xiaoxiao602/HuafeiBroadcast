package com.family.huafei

import java.math.BigDecimal

interface BalanceParser {
    /** 解析出余额,识别不了返回 null(宁可不播报,不可播错) */
    fun parse(body: String): BigDecimal?

    /** 解析本月消费金额,识别不了返回 null */
    fun parseConsumption(body: String): BigDecimal? = null
}

/**
 * 通用解析器:扫描短信中所有「xx元」金额,按金额前面的关键词上下文打分。
 * 正面词(余额类)才接受;负面词(消费/套餐/流量等)比正面词更靠近金额时拒绝。
 * 多个不同金额时取得分最高者,得分并列且金额不同则放弃。
 */
open class KeywordBalanceParser(protected val extraPatterns: List<Regex> = emptyList()) : BalanceParser {

    private data class Candidate(val amount: BigDecimal, val score: Int)

    private val moneyRegex = Regex("""(\d+(?:\.\d{1,2})?)\s*元""")

    // 关键词 -> 权重,越具体权重越高
    private val positiveKeywords = linkedMapOf(
        "话费余额" to 4, "可用余额" to 4, "账户余额" to 4, "当前余额" to 4,
        "实时余额" to 4, "剩余话费" to 4, "话费剩余" to 4, "余额" to 2
    )
    private val negativeKeywords = listOf(
        "本月消费", "消费", "套餐", "流量", "赠送", "积分", "欠费", "充值",
        "已用", "使用", "缴费", "实缴", "扣款", "扣费", "合计", "返还", "网龄"
    )

    override fun parse(body: String): BigDecimal? {
        val candidates = ArrayList<Candidate>()
        for (match in moneyRegex.findAll(body)) {
            val amount = match.groupValues[1].toBigDecimalOrNull() ?: continue
            val lookback = body.substring(maxOf(0, match.range.first - 14), match.range.first)
            val score = scoreContext(lookback) ?: continue
            candidates.add(Candidate(amount, score))
        }
        for (pattern in extraPatterns) {
            pattern.find(body)?.let {
                it.groupValues[1].toBigDecimalOrNull()?.let { v -> candidates.add(Candidate(v, 5)) }
            }
        }
        if (candidates.isEmpty()) return null
        val values = candidates.map { it.amount }.toSet()
        if (values.size == 1) return values.first()
        val best = candidates.maxByOrNull { it.score }!!
        return if (candidates.count { it.score == best.score } == 1) best.amount else null
    }

    /** 金额前的上下文打分:返回正分数表示接受,null 表示拒绝 */
    protected open fun scoreContext(before: String): Int? {
        var keywordEnd = -1
        var keywordScore = 0
        for ((keyword, weight) in positiveKeywords) {
            val idx = before.lastIndexOf(keyword)
            if (idx >= 0) {
                val end = idx + keyword.length
                if (end > keywordEnd || (end == keywordEnd && weight > keywordScore)) {
                    keywordEnd = end
                    keywordScore = weight
                }
            }
        }
        if (keywordEnd < 0) return null
        for (negative in negativeKeywords) {
            val idx = before.lastIndexOf(negative)
            if (idx >= keywordEnd) return null
        }
        return keywordScore
    }

    private val consumptionKeywords = linkedMapOf(
        "本月消费" to 4, "本月已消费" to 4, "当月消费" to 4, "消费合计" to 4,
        "本月产生话费" to 4, "话费消费" to 3, "共消费" to 3, "已消费" to 3, "消费" to 2
    )
    private val balanceKeywords = listOf("余额", "剩余话费", "话费剩余")

    /**
     * 本月消费:取「消费类关键词」紧邻的金额;
     * 若离金额更近的关键词是余额类,则该金额属于余额,不算消费。
     */
    override fun parseConsumption(body: String): BigDecimal? {
        val candidates = ArrayList<Candidate>()
        for (match in moneyRegex.findAll(body)) {
            val amount = match.groupValues[1].toBigDecimalOrNull() ?: continue
            val lookback = body.substring(maxOf(0, match.range.first - 12), match.range.first)
            var bestEnd = -1
            var bestScore = 0
            var nearestIsBalance = false
            for ((keyword, weight) in consumptionKeywords) {
                val idx = lookback.lastIndexOf(keyword)
                if (idx >= 0) {
                    val end = idx + keyword.length
                    if (end > bestEnd || (end == bestEnd && weight > bestScore)) {
                        bestEnd = end
                        bestScore = weight
                        nearestIsBalance = false
                    }
                }
            }
            for (keyword in balanceKeywords) {
                val idx = lookback.lastIndexOf(keyword)
                if (idx >= 0) {
                    val end = idx + keyword.length
                    if (end > bestEnd) {
                        bestEnd = end
                        nearestIsBalance = true
                    }
                }
            }
            if (bestEnd < 0 || nearestIsBalance) continue
            candidates.add(Candidate(amount, bestScore))
        }
        if (candidates.isEmpty()) return null
        val values = candidates.map { it.amount }.toSet()
        if (values.size == 1) return values.first()
        val best = candidates.maxByOrNull { it.score }!!
        return if (candidates.count { it.score == best.score } == 1) best.amount else null
    }
}

class ChinaMobileBalanceParser : KeywordBalanceParser(
    listOf(Regex("""话费余额\s*(?:为|是)?\s*[:：]?\s*(?:人民币|￥|¥)?\s*(\d+(?:\.\d{1,2})?)"""))
)

class ChinaUnicomBalanceParser : KeywordBalanceParser(
    listOf(Regex("""账户\s*(?:当前)?余额\s*(?:为|是)?\s*[:：]?\s*(?:人民币|￥|¥)?\s*(\d+(?:\.\d{1,2})?)"""))
)

class ChinaTelecomBalanceParser : KeywordBalanceParser(
    listOf(Regex("""(?:当前)?可用余额\s*(?:为|是)?\s*[:：]?\s*(?:人民币|￥|¥)?\s*(\d+(?:\.\d{1,2})?)"""))
)

/** 兜底解析器:只认「余额」紧邻的金额,最保守 */
class FallbackBalanceParser : BalanceParser {
    private val strict =
        Regex("""余额\s*(?:为|是)?\s*[:：]?\s*(?:人民币|￥|¥)?\s*(\d+(?:\.\d{1,2})?)\s*元""")

    override fun parse(body: String): BigDecimal? =
        strict.find(body)?.groupValues?.get(1)?.toBigDecimalOrNull()
}

object ParserFactory {
    fun forId(id: String): BalanceParser = when (id) {
        "mobile" -> ChinaMobileBalanceParser()
        "unicom" -> ChinaUnicomBalanceParser()
        "telecom" -> ChinaTelecomBalanceParser()
        else -> FallbackBalanceParser()
    }
}
