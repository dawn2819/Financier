package com.financier.app.ui.profile

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.financier.app.data.local.entity.FinancialAccountEntity
import com.financier.app.databinding.ItemAccountBinding

class AccountAdapter(
    private val onDeleteClick: (FinancialAccountEntity) -> Unit
) : ListAdapter<FinancialAccountEntity, AccountAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemAccountBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(account: FinancialAccountEntity) {
            binding.tvAccountName.text = account.name
            binding.tvAccountType.text = getTypeDisplay(account.accountType)
            binding.tvCurrency.text = account.currency

            // Color accent
            try {
                val gd = GradientDrawable()
                gd.shape = GradientDrawable.RECTANGLE
                gd.cornerRadius = 6f
                gd.setColor(Color.parseColor(account.colorHex))
                binding.viewColor.background = gd
            } catch (_: Exception) {}

            // Long press to delete
            binding.root.setOnLongClickListener {
                onDeleteClick(account)
                true
            }
        }

        private fun getTypeDisplay(type: String): String = when (type) {
            "CASH" -> "💵 Tiền mặt"
            "BANK" -> "🏦 Ngân hàng"
            "CREDIT_CARD" -> "💳 Thẻ tín dụng"
            "E_WALLET" -> "📱 Ví điện tử"
            else -> type
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<FinancialAccountEntity>() {
            override fun areItemsTheSame(a: FinancialAccountEntity, b: FinancialAccountEntity) = a.id == b.id
            override fun areContentsTheSame(a: FinancialAccountEntity, b: FinancialAccountEntity) = a == b
        }
    }
}
