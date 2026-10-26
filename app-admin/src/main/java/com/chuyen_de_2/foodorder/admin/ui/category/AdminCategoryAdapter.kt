package com.chuyen_de_2.foodorder.admin.ui.category

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.admin.databinding.ItemAdminCategoryBinding
import com.chuyen_de_2.foodorder.core.data.model.Category

class AdminCategoryAdapter(
    private val onEdit: (Category) -> Unit,
    private val onDelete: (Category) -> Unit,
    private val onToggleActive: (Category, Boolean) -> Unit
) : ListAdapter<Category, AdminCategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemAdminCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CategoryViewHolder(private val binding: ItemAdminCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category) {
            binding.tvCategoryIcon.text = category.icon.ifBlank { "🍽️" }
            binding.tvCategoryName.text = category.name
            binding.tvCategoryOrder.text = "ID: ${category.id} • Thứ tự: #${category.displayOrder}"

            // Detach listener before setting checked to avoid triggering during scroll
            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = category.isActive

            binding.switchActive.setOnCheckedChangeListener { _, isChecked ->
                onToggleActive(category, isChecked)
            }

            binding.btnEditCategory.setOnClickListener {
                onEdit(category)
            }

            binding.btnDeleteCategory.setOnClickListener {
                onDelete(category)
            }
        }
    }

    class CategoryDiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(oldItem: Category, newItem: Category): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Category, newItem: Category): Boolean =
            oldItem == newItem
    }
}
