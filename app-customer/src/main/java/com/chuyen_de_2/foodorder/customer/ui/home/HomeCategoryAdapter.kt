package com.chuyen_de_2.foodorder.customer.ui.home

import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.chuyen_de_2.foodorder.core.data.model.Category
import com.chuyen_de_2.foodorder.core.util.CategoryIconCache
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ItemHomeCategoryBinding

class HomeCategoryAdapter(
    private val onCategoryClick: (Category?) -> Unit
) : RecyclerView.Adapter<HomeCategoryAdapter.CategoryViewHolder>() {

    companion object {
        private const val PAYLOAD_SELECTION = "PAYLOAD_SELECTION"
    }

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
                iconUrl = "",
                displayOrder = 0,
                isActive = true
            )
        )
        categories.addAll(list.filter { it.isActive }.sortedBy { it.displayOrder })
        notifyDataSetChanged()
    }

    private fun findCategoryIndex(targetId: String?): Int {
        if (targetId.isNullOrBlank() || targetId == "ALL") {
            return categories.indexOfFirst { it.categoryId == "ALL" }
        }
        return categories.indexOfFirst {
            it.categoryId == targetId ||
                    it.categoryName.equals(targetId, ignoreCase = true) ||
                    it.categoryName.contains(targetId, ignoreCase = true) ||
                    targetId.contains(it.categoryName, ignoreCase = true)
        }
    }

    private fun isCategorySelected(category: Category, isAll: Boolean): Boolean {
        return if (selectedCategoryId.isNullOrBlank() || selectedCategoryId == "ALL") {
            isAll
        } else {
            selectedCategoryId == category.categoryId ||
                    selectedCategoryId.equals(category.categoryName, ignoreCase = true) ||
                    category.categoryName.contains(selectedCategoryId!!, ignoreCase = true) ||
                    selectedCategoryId!!.contains(category.categoryName, ignoreCase = true)
        }
    }

    fun setSelected(catId: String?) {
        if (selectedCategoryId == catId) return
        val oldId = selectedCategoryId
        selectedCategoryId = catId

        val oldIndex = findCategoryIndex(oldId)
        val newIndex = findCategoryIndex(catId)

        if (oldIndex != -1) notifyItemChanged(oldIndex, PAYLOAD_SELECTION)
        if (newIndex != -1 && newIndex != oldIndex) notifyItemChanged(newIndex, PAYLOAD_SELECTION)
        if (oldIndex == -1 && newIndex == -1) notifyDataSetChanged()
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

    override fun onBindViewHolder(
        holder: CategoryViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.bindSelection(categories[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(private val binding: ItemHomeCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindSelection(category: Category) {
            val context = itemView.context
            val isAll = category.categoryId == "ALL"
            val isSelected = isCategorySelected(category, isAll)

            if (isSelected) {
                itemView.setBackgroundResource(R.drawable.bg_chip_selected)
                binding.tvCategoryName.setTextColor(ContextCompat.getColor(context, R.color.primary))
            } else {
                itemView.background = null
                binding.tvCategoryName.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }
        }

        fun bind(category: Category, position: Int) {
            val context = itemView.context
            val isAll = category.categoryId == "ALL"

            // Gán màu nền icon theo vòng lặp màu
            val bgRes = bgDrawables[position % bgDrawables.size]
            binding.layoutIconCircle.setBackgroundResource(bgRes)

            // Hiển thị ảnh Icon tải lên từ Admin (Base64 hoặc URL)
            val icon = category.iconUrl.trim()
            if (category.isImageUrl) {
                binding.ivCategoryIcon.imageTintList = null
                val cachedBmp = CategoryIconCache.getOrDecode(icon)
                if (cachedBmp != null) {
                    // Tải ngay lập tức từ bộ nhớ đệm RAM (0ms delay, không giật, không chớp icon mặc định)
                    Glide.with(context).clear(binding.ivCategoryIcon)
                    binding.ivCategoryIcon.setImageBitmap(cachedBmp)
                } else if (icon.startsWith("data:image", ignoreCase = true) || icon.length > 100) {
                    try {
                        val clean = if (icon.contains(",")) icon.substringAfter(",") else icon
                        val decoded = Base64.decode(clean, Base64.DEFAULT)
                        Glide.with(context)
                            .asBitmap()
                            .load(decoded)
                            .dontAnimate()
                            .error(R.drawable.ic_utensils_crossed)
                            .into(binding.ivCategoryIcon)
                    } catch (_: Exception) {
                        binding.ivCategoryIcon.setImageResource(R.drawable.ic_utensils_crossed)
                    }
                } else {
                    Glide.with(context)
                        .load(icon)
                        .dontAnimate()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .error(R.drawable.ic_utensils_crossed)
                        .into(binding.ivCategoryIcon)
                }
            } else {
                Glide.with(context).clear(binding.ivCategoryIcon)
                val fallbackRes = if (isAll) R.drawable.ic_sparkles else R.drawable.ic_utensils_crossed
                binding.ivCategoryIcon.setImageResource(fallbackRes)
                binding.ivCategoryIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.primary)
            }

            binding.tvCategoryName.text = category.name
            bindSelection(category)

            itemView.setOnClickListener {
                val oldId = selectedCategoryId
                val newId = if (isAll) {
                    null
                } else if (selectedCategoryId == category.categoryId || selectedCategoryId == category.name) {
                    null
                } else {
                    category.categoryId
                }

                if (oldId != newId) {
                    selectedCategoryId = newId
                    val oldIndex = findCategoryIndex(oldId)
                    val newIndex = findCategoryIndex(newId)
                    if (oldIndex != -1) notifyItemChanged(oldIndex, PAYLOAD_SELECTION)
                    if (newIndex != -1 && newIndex != oldIndex) notifyItemChanged(newIndex, PAYLOAD_SELECTION)
                    if (oldIndex == -1 && newIndex == -1) notifyDataSetChanged()
                }

                val clickedCategory = if (newId == null) null else category
                onCategoryClick(clickedCategory)
            }
        }
    }
}
