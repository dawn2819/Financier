package com.financier.app.data.bank

object VietQrParser {

    data class VietQrData(
        val bankBin: String?,
        val accountNumber: String?,
        val amount: Double?,
        val description: String?
    )

    fun parse(qrCode: String): VietQrData? {
        val tlvMap = parseTlv(qrCode) ?: return null

        var bankBin: String? = null
        var accountNumber: String? = null

        // ID 38 contains Napas/VietQR info
        val merchantInfoRaw = tlvMap["38"] ?: tlvMap["26"]
        if (merchantInfoRaw != null) {
            val merchantTlv = parseTlv(merchantInfoRaw)
            if (merchantTlv != null) {
                val guid = merchantTlv["00"]
                if (guid?.uppercase() == "A000000727" || guid?.uppercase()?.contains("NAPAS") == true) {
                    val bankInfoRaw = merchantTlv["01"]
                    if (bankInfoRaw != null) {
                        val bankTlv = parseTlv(bankInfoRaw)
                        if (bankTlv != null) {
                            bankBin = bankTlv["00"]
                            accountNumber = bankTlv["01"]
                        }
                    }
                }
            }
        }

        val amountStr = tlvMap["54"]
        val amount = amountStr?.toDoubleOrNull()

        var description: String? = null
        val additionalInfoRaw = tlvMap["62"]
        if (additionalInfoRaw != null) {
            val additionalTlv = parseTlv(additionalInfoRaw)
            if (additionalTlv != null) {
                description = additionalTlv["08"]
            }
        }

        return VietQrData(bankBin, accountNumber, amount, description)
    }

    private fun parseTlv(raw: String): Map<String, String>? {
        val map = mutableMapOf<String, String>()
        var index = 0
        try {
            while (index < raw.length) {
                if (index + 4 > raw.length) break
                val tag = raw.substring(index, index + 2)
                val lengthStr = raw.substring(index + 2, index + 4)
                val length = lengthStr.toIntOrNull() ?: return null
                index += 4
                if (index + length > raw.length) return null
                val value = raw.substring(index, index + length)
                map[tag] = value
                index += length
            }
        } catch (e: Exception) {
            return null
        }
        return map
    }
}
