package com.financier.app.ui.budget

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.financier.app.R
import com.financier.app.common.CurrencyFormatter
import com.financier.app.databinding.ItemBudgetBinding
import com.financier.app.ui.transactions.TransactionAdapter

class BudgetAdapter(
    private val onDeleteClick: (BudgetItem) -> Unit
) : ListAdapter<BudgetItem, BudgetAdapter.ViewHolder>(DIFF) {

    var currency: String = "VND"
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBudgetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    private val colorCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private fun getCachedColor(colorStr: String): Int {
        return colorCache.getOrPut(colorStr) { Color.parseColor(colorStr) }
    }

    inner class ViewHolder(private val binding: ItemBudgetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(pos))
                    true
                } else {
                    false
                }
            }
        }

        fun bind(item: BudgetItem) {
            val ctx = binding.root.context
            val (iconRes, name, colorHex) = TransactionAdapter.getCategoryInfo(ctx, item.budget.category)

            binding.ivCategoryIcon.setImageResource(iconRes)
            binding.tvCategoryName.text = name
            binding.tvSpentOfLimit.text = "${CurrencyFormatter.formatShort(item.spent, currency)} / ${CurrencyFormatter.formatShort(item.budget.limitAmount, currency)}"
            val difference = item.budget.limitAmount - item.spent
            binding.tvRemainingAmount.text = if (difference < 0.0) {
                ctx.getString(R.string.budget_over_by, CurrencyFormatter.formatShort(-difference, currency))
            } else {
                ctx.getString(R.string.budget_item_remaining, CurrencyFormatter.formatShort(difference, currency))
            }
            binding.tvPercentage.text = "${item.percentage}%"
            binding.progressBudget.progress = item.percentage

            // Color based on percentage
            val indicatorColor = when {
                item.percentage >= 90 -> ContextCompat.getColor(ctx, R.color.error)
                item.percentage >= 70 -> ContextCompat.getColor(ctx, R.color.warning)
                else -> ContextCompat.getColor(ctx, R.color.finance_income)
            }
            binding.progressBudget.setIndicatorColor(indicatorColor)
            binding.tvPercentage.setTextColor(indicatorColor)
            binding.tvRemainingAmount.setTextColor(
                ContextCompat.getColor(ctx, if (difference < 0.0) R.color.error else R.color.finance_income)
            )

            // Category icon color
            try {
                binding.ivCategoryIcon.setColorFilter(getCachedColor(colorHex))
            } catch (_: Exception) {}
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BudgetItem>() {
            override fun areItemsTheSame(a: BudgetItem, b: BudgetItem) = a.budget.id == b.budget.id
            override fun areContentsTheSame(a: BudgetItem, b: BudgetItem) = a == b
        }
    }
}
