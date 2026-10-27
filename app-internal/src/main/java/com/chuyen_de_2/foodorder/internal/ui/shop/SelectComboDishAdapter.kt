package com.chuyen_de_2.foodorder.internal.ui.shop

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.core.data.model.Dish
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.ItemSelectComboDishBinding

data class SelectableComboDish(
    val dish: Dish,
    var isSelected: Boolean = false,
    var quantity: Int = 1
)

/**
 * Adapter danh sách món ăn chọn đưa vào Combo
 */
class SelectComboDishAdapter(
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<SelectComboDishAdapter.ViewHolder>() {

    private val items = mutableListOf<SelectableComboDish>()

    fun submitDishes(dishes: List<Dish>) {
        items.clear()
        // Chỉ chọn từ các món lẻ thông thường (không phải combo lồng combo)
        items.addAll(dishes.filter { !it.isCombo }.map { SelectableComboDish(it) })
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<SelectableComboDish> {
        return items.filter { it.isSelected }
    }

    fun calculateTotalOriginalPrice(): Long {
        return items.filter { it.isSelected }.sumOf {
            val price = if (it.dish.basePrice > 0) it.dish.basePrice else it.dish.price
            price * it.quantity
        }
    }

    inner class ViewHolder(val binding: ItemSelectComboDishBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SelectableComboDish) {
            val dish = item.dish
            binding.tvDishName.text = dish.name
            val price = if (dish.basePrice > 0) dish.basePrice else dish.price
            binding.tvDishPrice.text = price.toVndCurrency()

            binding.cbSelectDish.setOnCheckedChangeListener(null)
            binding.cbSelectDish.isChecked = item.isSelected
            binding.layoutQuantity.visibility = if (item.isSelected) View.VISIBLE else View.GONE
            binding.tvQuantity.text = item.quantity.toString()

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
                            .into(binding.ivDishThumb)
                    } catch (_: Exception) {
                        Glide.with(binding.root.context)
                            .load(dish.imageUrl)
                            .placeholder(R.drawable.bg_badge_orange)
                            .error(R.drawable.bg_badge_orange)
                            .into(binding.ivDishThumb)
                    }
                } else {
                    Glide.with(binding.root.context)
                        .load(dish.imageUrl)
                        .placeholder(R.drawable.bg_badge_orange)
                        .error(R.drawable.bg_badge_orange)
                        .into(binding.ivDishThumb)
                }
            } else {
                binding.ivDishThumb.setImageResource(R.drawable.bg_badge_orange)
            }

            binding.cbSelectDish.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
                binding.layoutQuantity.visibility = if (isChecked) View.VISIBLE else View.GONE
                onSelectionChanged()
            }

            binding.root.setOnClickListener {
                binding.cbSelectDish.isChecked = !binding.cbSelectDish.isChecked
            }

            binding.btnPlus.setOnClickListener {
                item.quantity++
                binding.tvQuantity.text = item.quantity.toString()
                onSelectionChanged()
            }

            binding.btnMinus.setOnClickListener {
                if (item.quantity > 1) {
                    item.quantity--
                    binding.tvQuantity.text = item.quantity.toString()
                    onSelectionChanged()
                } else {
                    binding.cbSelectDish.isChecked = false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectComboDishBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
