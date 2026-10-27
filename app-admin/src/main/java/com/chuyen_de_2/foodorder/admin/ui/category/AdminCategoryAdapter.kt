package com.chuyen_de_2.foodorder.admin.ui.category

import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.ItemAdminCategoryBinding
import com.chuyen_de_2.foodorder.core.data.model.Category
import com.chuyen_de_2.foodorder.core.util.CategoryIconCache

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
            val icon = category.iconUrl.trim()
            if (category.isImageUrl) {
                val cachedBmp = CategoryIconCache.getOrDecode(icon)
                if (cachedBmp != null) {
                    Glide.with(itemView.context).clear(binding.ivCategoryIcon)
                    binding.ivCategoryIcon.setImageBitmap(cachedBmp)
                } else if (icon.startsWith("data:image", ignoreCase = true) || icon.length > 100) {
                    try {
                        val clean = if (icon.contains(",")) icon.substringAfter(",") else icon
                        val decoded = Base64.decode(clean, Base64.DEFAULT)
                        Glide.with(itemView.context)
                            .asBitmap()
                            .load(decoded)
                            .dontAnimate()
                            .error(R.drawable.ic_image)
                            .into(binding.ivCategoryIcon)
                    } catch (_: Exception) {
                        binding.ivCategoryIcon.setImageResource(R.drawable.ic_image)
                    }
                } else {
                    Glide.with(itemView.context)
                        .load(icon)
                        .dontAnimate()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .error(R.drawable.ic_image)
                        .into(binding.ivCategoryIcon)
                }
            } else {
                Glide.with(itemView.context).clear(binding.ivCategoryIcon)
                binding.ivCategoryIcon.setImageResource(R.drawable.ic_image)
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
