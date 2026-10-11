package com.financier.app.ui.transactions

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.financier.app.R
import com.financier.app.common.CurrencyFormatter
import com.financier.app.data.local.entity.TransactionEntity
import com.financier.app.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private var currency: String,
    private var accountCurrencyMap: Map<Long, String> = emptyMap(),
    private val onItemClick: (TransactionEntity) -> Unit,
    private val onEditClick: (TransactionEntity) -> Unit,
    private val onDeleteClick: (TransactionEntity) -> Unit
) : ListAdapter<TransactionEntity, TransactionAdapter.ViewHolder>(DIFF_CALLBACK) {

    fun updateAccountCurrencyMap(map: Map<Long, String>) {
        accountCurrencyMap = map
        notifyDataSetChanged()
    }

    fun updateDisplayCurrency(newCurrency: String) {
        currency = newCurrency
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private val colorCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private fun getCachedColor(colorStr: String): Int {
        return colorCache.getOrPut(colorStr) { Color.parseColor(colorStr) }
    }

    inner class ViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(pos))
                }
            }
            binding.btnEdit.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onEditClick(getItem(pos))
                }
            }
            binding.btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(pos))
                }
            }
        }

        fun bind(item: TransactionEntity) {
            val ctx = binding.root.context

            // Bind iv_sms_badge visibility
            binding.ivSmsBadge.visibility = if (item.isSmsSynced) android.view.View.VISIBLE else android.view.View.GONE

            // Category info
            val (iconRes, categoryName, colorHex) = getCategoryInfo(ctx, item.category)
            binding.tvTransactionName.text = item.note.ifEmpty { categoryName }
            binding.tvCategory.text = "$categoryName • ${com.financier.app.common.DateFormatter.formatTime(item.dateMs)}"

            // Amount
            val isIncome = item.type == "INCOME"
            val prefix = if (isIncome) "+" else "-"
            val amountColor = if (isIncome)
                ContextCompat.getColor(ctx, R.color.cat_income)
            else
                ContextCompat.getColor(ctx, R.color.error)

            // Convert amount dynamically to the display currency
            val accountCurrency = accountCurrencyMap[item.accountId] ?: "VND"
            val displayAmount = com.financier.app.common.CurrencyFormatter.convert(item.amount, accountCurrency, currency)

            binding.tvAmount.text = "$prefix${CurrencyFormatter.format(displayAmount, currency)}"
            binding.tvAmount.setTextColor(amountColor)

            // Category icon background color
            try {
                binding.ivCategoryIcon.setBackgroundColor(getCachedColor(colorHex + "33"))
                binding.ivCategoryIcon.setColorFilter(getCachedColor(colorHex))
            } catch (e: Exception) { /* skip */ }

            binding.ivCategoryIcon.setImageResource(iconRes)
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TransactionEntity>() {
            override fun areItemsTheSame(a: TransactionEntity, b: TransactionEntity) = a.id == b.id
            override fun areContentsTheSame(a: TransactionEntity, b: TransactionEntity) = a == b
        }

        fun getCategoryInfo(ctx: android.content.Context, category: String): Triple<Int, String, String> {
            val (icon, nameRes, colorRes) = when (category.lowercase()) {
                "food" -> Triple(R.drawable.ic_cat_food, R.string.cat_food, R.color.cat_food)
                "transport" -> Triple(R.drawable.ic_cat_transport, R.string.cat_transport, R.color.cat_transport)
                "shopping" -> Triple(R.drawable.ic_cat_shopping, R.string.cat_shopping, R.color.cat_shopping)
                "health" -> Triple(R.drawable.ic_cat_health, R.string.cat_health, R.color.cat_health)
                "entertainment" -> Triple(R.drawable.ic_cat_entertainment, R.string.cat_entertainment, R.color.cat_entertainment)
                "housing" -> Triple(R.drawable.ic_cat_housing, R.string.cat_housing, R.color.cat_housing)
                "education" -> Triple(R.drawable.ic_cat_education, R.string.cat_education, R.color.cat_education)
                "gym" -> Triple(R.drawable.ic_cat_gym, R.string.cat_gym, R.color.cat_gym)
                "bills" -> Triple(R.drawable.ic_cat_bills, R.string.cat_bills, R.color.cat_bills)
                "travel" -> Triple(R.drawable.ic_cat_travel, R.string.cat_travel, R.color.cat_travel)
                "pets" -> Triple(R.drawable.ic_cat_pets, R.string.cat_pets, R.color.cat_pets)
                "salary", "income" -> Triple(R.drawable.ic_cat_income, R.string.cat_salary, R.color.cat_income)
                "freelance" -> Triple(R.drawable.ic_cat_income, R.string.cat_freelance, R.color.cat_income)
                "gift" -> Triple(R.drawable.ic_cat_income, R.string.cat_gift, R.color.secondary)
                else -> Triple(R.drawable.ic_cat_others, R.string.cat_others, R.color.cat_others)
            }
            val color = ContextCompat.getColor(ctx, colorRes)
            val colorHex = String.format(Locale.US, "#%06X", 0xFFFFFF and color)
            return Triple(icon, ctx.getString(nameRes), colorHex)
        }

        fun formatTime(ms: Long): String {
            return com.financier.app.common.DateFormatter.formatTime(ms)
        }

        fun formatDate(ms: Long): String {
            return com.financier.app.common.DateFormatter.formatDate(ms)
        }
    }
}
