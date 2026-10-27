package com.chuyen_de_2.foodorder.internal.ui.shop

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.internal.databinding.ItemManageStoreCategoryBinding

data class StoreCategoryItem(
    val name: String,
    val dishCount: Int = 0
)

/**
 * Adapter quản lý danh mục riêng của quán (Thêm / Sửa / Xóa)
 */
class ManageStoreCategoryAdapter(
    private val onEditClick: (String) -> Unit,
    private val onDeleteClick: (String, Int) -> Unit
) : ListAdapter<StoreCategoryItem, ManageStoreCategoryAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemManageStoreCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StoreCategoryItem) {
            binding.tvCategoryName.text = item.name
            binding.tvDishCount.text = "${item.dishCount} món đang thuộc danh mục"

            binding.btnEditCategory.setOnClickListener {
                onEditClick(item.name)
            }

            binding.btnDeleteCategory.setOnClickListener {
                onDeleteClick(item.name, item.dishCount)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemManageStoreCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<StoreCategoryItem>() {
        override fun areItemsTheSame(oldItem: StoreCategoryItem, newItem: StoreCategoryItem): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: StoreCategoryItem, newItem: StoreCategoryItem): Boolean =
            oldItem == newItem
    }
}
