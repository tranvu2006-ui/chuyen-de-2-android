package com.chuyen_de_2.foodorder.customer.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ItemHotDishBinding

data class HotDish(
    val id: String,
    val name: String,
    val price: Long,
    val storeId: String,
    val storeName: String,
    val imageUrl: String = "",
    val emoji: String = "🍲"
)

class HotDishAdapter(
    private val onClick: (HotDish) -> Unit
) : RecyclerView.Adapter<HotDishAdapter.ViewHolder>() {

    private val items = mutableListOf<HotDish>()

    fun submitList(newItems: List<HotDish>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemHotDishBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(dish: HotDish) {
            val context = binding.root.context
            binding.tvDishName.text = dish.name
            binding.tvShopName.text = if (dish.storeName.startsWith("🏪")) dish.storeName else "🏪 ${dish.storeName}"
            binding.tvDishPrice.text = dish.price.toVndCurrency()

            if (dish.imageUrl.isNotBlank()) {
                binding.ivDishThumb.visibility = View.VISIBLE
                binding.tvDishEmoji.visibility = View.GONE
                if (dish.imageUrl.startsWith("data:image") || dish.imageUrl.length > 500) {
                    try {
                        val clean = if (dish.imageUrl.contains(",")) dish.imageUrl.substringAfter(",") else dish.imageUrl
                        val decoded = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                        Glide.with(context)
                            .asBitmap()
                            .load(decoded)
                            .placeholder(R.drawable.placeholder_food)
                            .error(R.drawable.placeholder_food)
                            .into(binding.ivDishThumb)
                    } catch (_: Exception) {
                        Glide.with(context)
                            .load(dish.imageUrl)
                            .placeholder(R.drawable.placeholder_food)
                            .error(R.drawable.placeholder_food)
                            .into(binding.ivDishThumb)
                    }
                } else {
                    Glide.with(context)
                        .load(dish.imageUrl)
                        .placeholder(R.drawable.placeholder_food)
                        .error(R.drawable.placeholder_food)
                        .into(binding.ivDishThumb)
                }
            } else {
                binding.ivDishThumb.visibility = View.VISIBLE
                binding.ivDishThumb.setImageResource(R.drawable.placeholder_food)
                binding.tvDishEmoji.text = dish.emoji
                binding.tvDishEmoji.visibility = View.VISIBLE
            }

            binding.root.setOnClickListener {
                it.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    onClick(dish)
                }.start()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHotDishBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
