package com.chuyen_de_2.foodorder.customer.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.core.data.model.Category
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ItemHomeCategoryBinding

class HomeCategoryAdapter(
    private val onCategoryClick: (Category?) -> Unit
) : RecyclerView.Adapter<HomeCategoryAdapter.CategoryViewHolder>() {

    private val categories = mutableListOf<Category>()
    var selectedCategoryId: String? = null
        private set

    private val bgDrawables = listOf(
        R.drawable.bg_cat_orange,
        R.drawable.bg_cat_green,
        R.drawable.bg_cat_blue,
        R.drawable.bg_cat_red,
        R.drawable.bg_cat_amber,
        R.drawable.bg_cat_purple,
        R.drawable.bg_cat_pink
    )

    fun submitCategories(list: List<Category>) {
        categories.clear()
        // Item đầu tiên luôn là Tất Cả
        categories.add(
            Category(
                categoryId = "ALL",
                categoryName = "Tất cả",
                iconUrl = "✨",
                displayOrder = 0,
                isActive = true
            )
        )
        categories.addAll(list.filter { it.isActive }.sortedBy { it.displayOrder })
        notifyDataSetChanged()
    }

    fun setSelected(catId: String?) {
        selectedCategoryId = catId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemHomeCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position], position)
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(private val binding: ItemHomeCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category, position: Int) {
            val context = itemView.context
            val isAll = category.categoryId == "ALL"
            val isSelected = if (selectedCategoryId.isNullOrBlank() || selectedCategoryId == "ALL") {
                isAll
            } else {
                selectedCategoryId == category.categoryId ||
                        selectedCategoryId.equals(category.categoryName, ignoreCase = true) ||
                        category.categoryName.contains(selectedCategoryId!!, ignoreCase = true) ||
                        selectedCategoryId!!.contains(category.categoryName, ignoreCase = true)
            }

            // Gán màu nền icon theo vòng lặp màu
            val bgRes = bgDrawables[position % bgDrawables.size]
            binding.layoutIconCircle.setBackgroundResource(bgRes)

            // Hiển thị Icon (URL ảnh hoặc Emoji)
            val icon = category.icon.ifBlank { "🍽️" }
            if (category.isImageUrl) {
                binding.tvCategoryEmoji.visibility = View.GONE
                binding.ivCategoryIcon.visibility = View.VISIBLE
                Glide.with(context)
                    .load(icon)
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_image)
                    .into(binding.ivCategoryIcon)
            } else {
                binding.ivCategoryIcon.visibility = View.GONE
                binding.tvCategoryEmoji.visibility = View.VISIBLE
                binding.tvCategoryEmoji.text = icon
            }

            binding.tvCategoryName.text = category.name

            // Trạng thái chọn (Highlight)
            if (isSelected) {
                itemView.setBackgroundResource(R.drawable.bg_chip_selected)
                binding.tvCategoryName.setTextColor(ContextCompat.getColor(context, R.color.primary))
            } else {
                itemView.background = null
                binding.tvCategoryName.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }

            itemView.setOnClickListener {
                if (isAll) {
                    selectedCategoryId = null
                    notifyDataSetChanged()
                    onCategoryClick(null)
                } else if (selectedCategoryId == category.categoryId || selectedCategoryId == category.name) {
                    // Bấm lại danh mục đang chọn -> Hủy chọn
                    selectedCategoryId = null
                    notifyDataSetChanged()
                    onCategoryClick(null)
                } else {
                    selectedCategoryId = category.categoryId
                    notifyDataSetChanged()
                    onCategoryClick(category)
                }
            }
        }
    }
}
