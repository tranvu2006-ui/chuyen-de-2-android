package com.chuyen_de_2.foodorder.admin.ui.category

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.admin.R
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
            val icon = category.icon.ifBlank { "🍽️" }
            if (category.isImageUrl) {
                binding.tvCategoryIcon.visibility = View.GONE
                binding.ivCategoryIcon.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(icon)
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_image)
                    .into(binding.ivCategoryIcon)
            } else {
                binding.ivCategoryIcon.visibility = View.GONE
                binding.tvCategoryIcon.visibility = View.VISIBLE
                binding.tvCategoryIcon.text = icon
            }

            binding.tvCategoryName.text = category.name
            binding.tvCategoryOrder.text = "Thứ tự: #${category.displayOrder} • ID: ${category.id}"

            if (category.description.isNotBlank()) {
                binding.tvCategoryDesc.visibility = View.VISIBLE
                binding.tvCategoryDesc.text = category.description
            } else {
                binding.tvCategoryDesc.visibility = View.GONE
            }

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
