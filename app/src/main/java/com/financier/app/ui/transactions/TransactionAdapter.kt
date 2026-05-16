package com.financier.app.ui.transactions

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.financier.app.R
import com.financier.app.common.CurrencyFormatter
import com.financier.app.data.local.entity.TransactionEntity
import com.financier.app.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private val currency: String,
    private val onItemClick: (TransactionEntity) -> Unit,
    private val onEditClick: (TransactionEntity) -> Unit,
    private val onDeleteClick: (TransactionEntity) -> Unit
) : ListAdapter<TransactionEntity, TransactionAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TransactionEntity) {
            val ctx = binding.root.context

            // Category info
            val (iconRes, categoryName, colorHex) = getCategoryInfo(ctx, item.category)
            binding.tvTransactionName.text = item.note.ifEmpty { categoryName }
            binding.tvCategory.text = "$categoryName • ${formatTime(item.dateMs)}"

            // Amount
            val isIncome = item.type == "INCOME"
            val prefix = if (isIncome) "+" else "-"
            val amountColor = if (isIncome)
                Color.parseColor("#78DC77")
            else
                Color.parseColor("#FFB4AB")

            binding.tvAmount.text = "$prefix${CurrencyFormatter.format(item.amount, currency)}"
            binding.tvAmount.setTextColor(amountColor)

            // Category icon background color
            try {
                binding.ivCategoryIcon.setBackgroundColor(Color.parseColor(colorHex + "33"))
                binding.ivCategoryIcon.setColorFilter(Color.parseColor(colorHex))
            } catch (e: Exception) { /* skip */ }

            binding.ivCategoryIcon.setImageResource(iconRes)

            // Click listeners
            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnEdit.setOnClickListener { onEditClick(item) }
            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TransactionEntity>() {
            override fun areItemsTheSame(a: TransactionEntity, b: TransactionEntity) = a.id == b.id
            override fun areContentsTheSame(a: TransactionEntity, b: TransactionEntity) = a == b
        }

        fun getCategoryInfo(ctx: android.content.Context, category: String): Triple<Int, String, String> {
            return when (category.lowercase()) {
                "food" -> Triple(R.drawable.ic_cat_food, ctx.getString(R.string.cat_food), "#4CAF50")
                "transport" -> Triple(R.drawable.ic_cat_transport, ctx.getString(R.string.cat_transport), "#2196F3")
                "shopping" -> Triple(R.drawable.ic_cat_shopping, ctx.getString(R.string.cat_shopping), "#FF9800")
                "health" -> Triple(R.drawable.ic_cat_health, ctx.getString(R.string.cat_health), "#F44336")
                "entertainment" -> Triple(R.drawable.ic_cat_entertainment, ctx.getString(R.string.cat_entertainment), "#9C27B0")
                "housing" -> Triple(R.drawable.ic_cat_housing, ctx.getString(R.string.cat_housing), "#795548")
                "education" -> Triple(R.drawable.ic_cat_education, ctx.getString(R.string.cat_education), "#00BCD4")
                "gym" -> Triple(R.drawable.ic_cat_gym, ctx.getString(R.string.cat_gym), "#E91E63")
                "bills" -> Triple(R.drawable.ic_cat_bills, ctx.getString(R.string.cat_bills), "#FF5722")
                "travel" -> Triple(R.drawable.ic_cat_travel, ctx.getString(R.string.cat_travel), "#03A9F4")
                "pets" -> Triple(R.drawable.ic_cat_pets, ctx.getString(R.string.cat_pets), "#8BC34A")
                "salary", "income" -> Triple(R.drawable.ic_cat_income, ctx.getString(R.string.cat_salary), "#78DC77")
                "freelance" -> Triple(R.drawable.ic_cat_income, ctx.getString(R.string.cat_freelance), "#78DC77")
                "gift" -> Triple(R.drawable.ic_cat_income, ctx.getString(R.string.cat_gift), "#FFB1C7")
                else -> Triple(R.drawable.ic_cat_others, ctx.getString(R.string.cat_others), "#607D8B")
            }
        }

        fun formatTime(ms: Long): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(ms))
        }

        fun formatDate(ms: Long): String {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            return sdf.format(Date(ms))
        }
    }
}
