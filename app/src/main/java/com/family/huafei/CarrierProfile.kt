package com.family.huafei

/** 运营商配置:指令与号码存在地区差异,家属可在设置页修改号码与指令。 */
data class CarrierProfile(
    val id: String,
    val displayName: String,
    val destinationNumber: String,
    val queryCommand: String,
    val allowedSenderNumbers: List<String>,
    val parserType: String
) {
    companion object {
        val MOBILE = CarrierProfile(
            id = "mobile", displayName = "中国移动",
            destinationNumber = "10086", queryCommand = "YE",
            allowedSenderNumbers = listOf("10086"), parserType = "mobile"
        )
        val UNICOM = CarrierProfile(
            id = "unicom", displayName = "中国联通",
            destinationNumber = "10010", queryCommand = "102",
            allowedSenderNumbers = listOf("10010"), parserType = "unicom"
        )
        val TELECOM = CarrierProfile(
            id = "telecom", displayName = "中国电信",
            destinationNumber = "10001", queryCommand = "102",
            allowedSenderNumbers = listOf("10001"), parserType = "telecom"
        )
        val ALL = listOf(MOBILE, UNICOM, TELECOM)

        fun byId(id: String): CarrierProfile = ALL.firstOrNull { it.id == id } ?: MOBILE

        /** 按 SIM 的 mcc/mnc 猜测运营商,猜不出返回 null */
        fun guessByMccMnc(mcc: String?, mnc: String?): CarrierProfile? {
            if (mcc.isNullOrEmpty()) return null
            return when ("$mcc$mnc") {
                "46000", "46002", "46004", "46007", "46008" -> MOBILE
                "46001", "46006", "46009" -> UNICOM
                "46003", "46005", "46011" -> TELECOM
                else -> null
            }
        }
    }
}
