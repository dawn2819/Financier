package com.financier.app.data.bank

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object BankDeepLinkHelper {

    data class BankAppInfo(
        val name: String,
        val bin: String,
        val packageName: String
    )

    private val BANK_APPS = listOf(
        BankAppInfo("Vietcombank", "970436", "com.VCB"),
        BankAppInfo("Techcombank", "970407", "com.techcombank.vietnammobile"),
        BankAppInfo("MB Bank", "970422", "com.mbmobile"),
        BankAppInfo("BIDV", "970418", "com.vrun.bidvshare"),
        BankAppInfo("Agribank", "970405", "com.vnpay.agribank"),
        BankAppInfo("VietinBank", "970415", "com.vietinbank.ipay"),
        BankAppInfo("TPBank", "970423", "com.tpb.mb.android"),
        BankAppInfo("VPBank", "970432", "com.vnpay.vpbank"),
        BankAppInfo("ACB", "970416", "com.acb.mobilebanking"),
        BankAppInfo("Sacombank", "970403", "com.sacombank.msacombank")
    )

    fun getBankInfoByBin(bin: String?): BankAppInfo? {
        if (bin == null) return null
        return BANK_APPS.find { it.bin == bin }
    }

    fun openBankApp(context: Context, bankBin: String?): Boolean {
        val bankInfo = getBankInfoByBin(bankBin)
        if (bankInfo == null) {
            Toast.makeText(context, "Không nhận dạng được ngân hàng này", Toast.LENGTH_SHORT).show()
            return false
        }

        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(bankInfo.packageName)
        return if (intent != null) {
            context.startActivity(intent)
            true
        } else {
            // App is not installed, open Play Store
            try {
                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${bankInfo.packageName}"))
                playStoreIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(playStoreIntent)
            } catch (e: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${bankInfo.packageName}"))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
            }
            false
        }
    }
}
