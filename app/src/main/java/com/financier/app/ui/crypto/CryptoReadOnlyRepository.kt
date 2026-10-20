package com.financier.app.ui.crypto

import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL

/** Read-only market and BNB Chain calls. This class never signs or broadcasts transactions. */
internal object CryptoReadOnlyRepository {
    data class MarketPrice(val usd: String, val vnd: String, val change24h: String)
    data class MarketSnapshot(val bitcoin: MarketPrice, val bnb: MarketPrice)
    data class WalletSnapshot(val bnb: String, val token: String? = null, val tokenSymbol: String? = null)

    fun getMarketSnapshot(): MarketSnapshot {
        val json = JSONObject(get("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,binancecoin&vs_currencies=usd,vnd&include_24hr_change=true"))
        fun price(id: String) = json.getJSONObject(id).let { item ->
            MarketPrice(
                usd = item.optString("usd", "—"),
                vnd = item.optString("vnd", "—"),
                change24h = item.optString("usd_24h_change", "—")
            )
        }
        return MarketSnapshot(price("bitcoin"), price("binancecoin"))
    }

    fun getWalletSnapshot(address: String, tokenContract: String?): WalletSnapshot {
        val rawBnb = rpc("eth_getBalance", "\"$address\"", "\"latest\"")
        val bnb = BigDecimal(BigInteger(rawBnb.removePrefix("0x").ifBlank { "0" }, 16))
            .divide(BigDecimal.TEN.pow(18), 8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        if (tokenContract.isNullOrBlank()) return WalletSnapshot(bnb)

        val encodedAddress = address.removePrefix("0x").lowercase().padStart(64, '0')
        val data = "0x70a08231$encodedAddress"
        val tokenRaw = rpc("eth_call", "{\"to\":\"$tokenContract\",\"data\":\"$data\"}", "\"latest\"")
        val decimalsRaw = rpc("eth_call", "{\"to\":\"$tokenContract\",\"data\":\"0x313ce567\"}", "\"latest\"")
        val decimals = BigInteger(decimalsRaw.removePrefix("0x").ifBlank { "0" }, 16).toInt().coerceIn(0, 36)
        val tokenBalance = BigDecimal(BigInteger(tokenRaw.removePrefix("0x").ifBlank { "0" }, 16))
            .divide(BigDecimal.TEN.pow(decimals), 8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        val symbolRaw = runCatching {
            rpc("eth_call", "{\"to\":\"$tokenContract\",\"data\":\"0x95d89b41\"}", "\"latest\"")
        }.getOrNull()
        val symbol = symbolRaw?.let(::decodeString).orEmpty().ifBlank { "BEP-20" }
        return WalletSnapshot(bnb, tokenBalance, symbol)
    }

    private fun rpc(method: String, vararg params: String): String {
        val paramsJson = params.joinToString(prefix = "[", postfix = "]")
        val request = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method)
            .put("params", org.json.JSONArray(paramsJson))
        val response = post("https://bsc-dataseed.bnbchain.org", request.toString())
        val json = JSONObject(response)
        if (json.has("error")) throw IllegalStateException("BNB RPC error")
        return json.getString("result")
    }

    private fun decodeString(hex: String): String = runCatching {
        val bytes = hex.removePrefix("0x").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val length = BigInteger(1, bytes.copyOfRange(32, 64)).toInt().coerceAtMost(bytes.size - 64)
        String(bytes, 64, length, Charsets.UTF_8).trim('\u0000')
    }.getOrDefault("")

    private fun get(url: String): String = request(url, null)
    private fun post(url: String, body: String): String = request(url, body)

    private fun request(url: String, body: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = if (body == null) "GET" else "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Financier-Android/1.0")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return response
        } finally {
            connection.disconnect()
        }
    }
}
