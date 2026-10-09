package com.chuyen_de_2.foodorder.customer.ui.menu

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.chuyen_de_2.foodorder.core.data.model.Dish
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ItemMenuBinding

typealias DishAdapter = MenuAdapter

class MenuAdapter(
    private val onAddClick: (Dish) -> Unit,
    private val onRemoveClick: (String) -> Unit,
    private val getQuantity: (String) -> Int,
    private val onItemClick: (Dish) -> Unit
) : ListAdapter<Dish, MenuAdapter.ViewHolder>(DiffCallback()) {

    private var selectedDishId: String? = null

    fun setSelectedDishId(id: String) {
        selectedDishId = id
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemMenuBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Dish) {
            val context = binding.root.context
            binding.tvName.text = item.name
            binding.tvDescription.text = item.description
            binding.tvPrice.text = item.price.toVndCurrency()
            binding.tvSold.text = "${item.soldCount} đã bán"

            // Nhãn promo
            if (item.category.contains("mới", ignoreCase = true) || item.soldCount > 500) {
                binding.tvPromoBadge.visibility = View.VISIBLE
                binding.tvPromoBadge.text = if (item.soldCount > 1000) "HOT" else "-20%"
            } else {
                binding.tvPromoBadge.visibility = View.GONE
            }

            // Load ảnh thumbnail món 64x64 bo góc 10dp
            Glide.with(context)
                .load(item.imageUrl)
                .transform(CenterCrop(), RoundedCorners(20))
                .placeholder(R.drawable.placeholder_food)
                .error(R.drawable.placeholder_food)
                .into(binding.ivFood)

            // Trạng thái món đang chọn (selected-view)
            val isSelected = (item.id == selectedDishId)
            if (isSelected) {
                binding.cardDish.strokeColor = ContextCompat.getColor(context, R.color.text_primary)
                binding.cardDish.setCardBackgroundColor(ContextCompat.getColor(context, R.color.primary_light))
            } else {
                binding.cardDish.strokeColor = ContextCompat.getColor(context, R.color.border)
                binding.cardDish.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
            }

            // Click vào card món -> Đưa lên nửa trên (Top Detail Section)
            binding.root.setOnClickListener {
                onItemClick(item)
            }

            // Stepper cộng trừ số lượng ở bên phải món (b5.html: dish-stepper-box)
            val qty = getQuantity(item.id)
            updateStepperUI(qty)

            binding.btnPlus.setOnClickListener {
                onAddClick(item)
                updateStepperUI(getQuantity(item.id))
            }

            binding.btnMinus.setOnClickListener {
                if (getQuantity(item.id) > 0) {
                    onRemoveClick(item.id)
                    updateStepperUI(getQuantity(item.id))
                }
            }
        }

        private fun updateStepperUI(qty: Int) {
            val context = binding.root.context
            binding.tvQuantity.text = qty.toString()

            if (qty > 0) {
                binding.layoutStepper.setBackgroundResource(R.drawable.bg_stepper_box_active)
                binding.tvQuantity.setTextColor(ContextCompat.getColor(context, R.color.primary))
                binding.btnMinus.setTextColor(ContextCompat.getColor(context, R.color.primary))
                binding.btnPlus.setTextColor(ContextCompat.getColor(context, R.color.primary))
            } else {
                binding.layoutStepper.setBackgroundResource(R.drawable.bg_stepper_box)
                binding.tvQuantity.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                binding.btnMinus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                binding.btnPlus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMenuBinding.inflate(
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
