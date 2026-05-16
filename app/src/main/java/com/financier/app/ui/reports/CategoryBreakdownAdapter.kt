package com.financier.app.ui.reports

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.financier.app.common.CurrencyFormatter
import com.financier.app.databinding.ItemCategoryBreakdownBinding
import com.financier.app.ui.transactions.TransactionAdapter

class CategoryBreakdownAdapter : RecyclerView.Adapter<CategoryBreakdownAdapter.ViewHolder>() {

    private var items: List<CategorySpending> = emptyList()

    fun submitList(list: List<CategorySpending>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBreakdownBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemCategoryBreakdownBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CategorySpending) {
            val ctx = binding.root.context
            val (_, name, colorHex) = TransactionAdapter.getCategoryInfo(ctx, item.category)

            binding.tvCategoryName.text = name
            binding.tvAmount.text = CurrencyFormatter.formatShort(item.amount, "VND")
            binding.tvPercentage.text = "${String.format("%.1f", item.percentage)}%"

            try {
                val gd = android.graphics.drawable.GradientDrawable()
                gd.shape = android.graphics.drawable.GradientDrawable.OVAL
                gd.setColor(Color.parseColor(colorHex))
                binding.viewColorDot.background = gd
            } catch (_: Exception) {}
        }
    }
}
