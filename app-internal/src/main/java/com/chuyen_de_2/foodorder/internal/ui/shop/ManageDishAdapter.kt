package com.chuyen_de_2.foodorder.internal.ui.shop

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.core.data.model.Dish
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.ItemManageDishBinding

/**
 * ManageDishAdapter — Adapter hiển thị danh sách thực đơn quán theo Quán/2.html
 */
class ManageDishAdapter(
    private val onEditClick: (Dish) -> Unit,
    private val onDeleteClick: (Dish) -> Unit
) : ListAdapter<Dish, ManageDishAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemManageDishBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(dish: Dish) {
            binding.tvDishName.text = dish.name
            val catDisplay = buildString {
                append(dish.category.ifBlank { "Món Quán" })
                if (dish.platformCategory.isNotBlank()) {
                    append(" • ")
                    append(dish.platformCategory)
                }
            }
            binding.tvDishCategory.text = catDisplay
            binding.tvComboBadge.visibility = if (dish.isCombo) View.VISIBLE else View.GONE

            val displayDesc = buildString {
                if (dish.isCombo && dish.comboItems.isNotEmpty()) {
                    append("🍱 Gồm: ")
                    append(dish.comboItems.joinToString(", ") { "${it.quantity}x ${it.name}" })
                    if (dish.description.isNotBlank()) {
                        append(" • ")
                    }
                }
                if (dish.description.isNotBlank()) {
                    append(dish.description)
                }
            }

            if (displayDesc.isNotBlank()) {
                binding.tvDishDescription.visibility = View.VISIBLE
                binding.tvDishDescription.text = displayDesc
            } else {
                binding.tvDishDescription.visibility = View.GONE
            }

            val price = if (dish.basePrice > 0) dish.basePrice else dish.price
            binding.tvDishPrice.text = price.toVndCurrency()

            if (dish.originalPrice > price) {
                binding.tvOriginalPrice.visibility = View.VISIBLE
                binding.tvOriginalPrice.text = dish.originalPrice.toVndCurrency()
                binding.tvOriginalPrice.paintFlags = binding.tvOriginalPrice.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.tvOriginalPrice.visibility = View.GONE
            }

            if (dish.imageUrl.isNotBlank()) {
                if (dish.imageUrl.startsWith("data:image") || dish.imageUrl.length > 500) {
                    try {
                        val base64Data = if (dish.imageUrl.contains(",")) dish.imageUrl.substringAfter(",") else dish.imageUrl
                        val decoded = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        Glide.with(binding.root.context)
                            .asBitmap()
                            .load(decoded)
                            .placeholder(R.drawable.bg_badge_orange)
                            .error(R.drawable.bg_badge_orange)
                            .centerCrop()
                            .into(binding.ivDishThumb)
                    } catch (_: Exception) {
                        Glide.with(binding.root.context)
                            .load(dish.imageUrl)
                            .placeholder(R.drawable.bg_badge_orange)
                            .error(R.drawable.bg_badge_orange)
                            .centerCrop()
                            .into(binding.ivDishThumb)
                    }
                } else {
                    Glide.with(binding.root.context)
                        .load(dish.imageUrl)
                        .placeholder(R.drawable.bg_badge_orange)
                        .error(R.drawable.bg_badge_orange)
                        .centerCrop()
                        .into(binding.ivDishThumb)
                }
            } else {
                binding.ivDishThumb.setImageResource(R.drawable.bg_badge_orange)
            }

            binding.btnEditDish.setOnClickListener { onEditClick(dish) }
            binding.btnDeleteDish.setOnClickListener { onDeleteClick(dish) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemManageDishBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class DiffCallback : DiffUtil.ItemCallback<Dish>() {
        override fun areItemsTheSame(old: Dish, new: Dish) = old.id == new.id
        override fun areContentsTheSame(old: Dish, new: Dish) = old == new
    }
}
