package com.example.finovate

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat

class BudgetCategoryAdapter(
    private val categories: List<BudgetCategory>,
    private val currencyFormat: NumberFormat,
    private val onEditClick: (BudgetCategory) -> Unit
) : RecyclerView.Adapter<BudgetCategoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budget_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.bind(category)
    }

    override fun getItemCount() = categories.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val tvBudgetAmount: TextView = itemView.findViewById(R.id.tvBudgetAmount)
        private val tvSpentAmount: TextView = itemView.findViewById(R.id.tvSpentAmount)
        private val tvRemainingAmount: TextView = itemView.findViewById(R.id.tvRemainingAmount)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.categoryProgressBar)
        private val ivCategoryIcon: ImageView = itemView.findViewById(R.id.ivCategoryIcon)
        private val btnEditCategory: ImageView = itemView.findViewById(R.id.btnEditCategory)

        fun bind(category: BudgetCategory) {
            tvCategoryName.text = category.name
            tvBudgetAmount.text = currencyFormat.format(category.budget)
            tvSpentAmount.text = "Spent: ${currencyFormat.format(category.spent)}"

            val remaining = category.budget - category.spent
            tvRemainingAmount.text = "Left: ${currencyFormat.format(remaining)}"

            // Set progress bar
            val progress = if (category.budget > 0) {
                (category.spent / category.budget * 100).toInt()
            } else {
                0
            }
            progressBar.progress = progress

            // Set progress bar color based on budget status
            if (progress > 90) {
                progressBar.progressTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, R.color.expense_red)
                )
            } else if (progress > 75) {
                progressBar.progressTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, R.color.warning_orange)
                )
            } else {
                progressBar.progressTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, R.color.primary)
                )
            }

            // Set category icon
            ivCategoryIcon.setImageResource(category.iconResId)
            ivCategoryIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(itemView.context, category.colorResId)
            )

            // Set edit button click listener
            btnEditCategory.setOnClickListener {
                onEditClick(category)
            }
        }
    }
}