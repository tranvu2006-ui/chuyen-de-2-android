package com.chuyen_de_2.foodorder.customer.ui.home

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ItemHomeDishBinding

data class HomeDishItem(
    val id: String,
    val name: String,
    val price: Long,
    val originalPrice: Long = 0,
    val storeId: String,
    val storeName: String,
    val imageUrl: String = "",
    val emoji: String = "🍲",
    val rating: Float = 4.8f,
    val soldCount: String = "1.2k đã bán",
    val distanceKm: Double = 1.2,
    val promoBadge: String = "",
    val tag1: String = "Bán Chạy Nhất",
    val tag2: String = "Freeship Extra",
    val category: String = ""
)

class HomeDishAdapter(
    private val onDishClick: (HomeDishItem) -> Unit,
    private val onAddClick: (HomeDishItem) -> Unit
) : RecyclerView.Adapter<HomeDishAdapter.ViewHolder>() {

    private val items = mutableListOf<HomeDishItem>()

    fun submitList(newItems: List<HomeDishItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemHomeDishBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(dish: HomeDishItem) {
            val context = binding.root.context
            binding.tvDishName.text = dish.name
            binding.tvDishPrice.text = dish.price.toVndCurrency()

            // Giá cũ gạch ngang (nếu có)
            if (dish.originalPrice > dish.price) {
                binding.tvDishOldPrice.visibility = View.VISIBLE
                binding.tvDishOldPrice.text = dish.originalPrice.toVndCurrency()
                binding.tvDishOldPrice.paintFlags = binding.tvDishOldPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.tvDishOldPrice.visibility = View.GONE
            }

            // Tên quán bán món
            binding.tvStoreName.text = if (dish.storeName.startsWith("🏪")) dish.storeName else "🏪 ${dish.storeName}"

            // Meta đánh giá & cự ly km
            binding.tvRatingMeta.text = "⭐ ${dish.rating} (${dish.soldCount})"
            binding.tvDistanceMeta.text = "• 📍 ${dish.distanceKm} km"

            // Badge khuyến mãi
            if (dish.promoBadge.isNotBlank()) {
                binding.tvDishPromo.visibility = View.VISIBLE
                binding.tvDishPromo.text = dish.promoBadge
            } else {
                binding.tvDishPromo.visibility = View.GONE
            }

            // Tags
            if (dish.tag1.isNotBlank()) {
                binding.tvTag1.visibility = View.VISIBLE
                binding.tvTag1.text = dish.tag1
            } else {
                binding.tvTag1.visibility = View.GONE
            }

            if (dish.tag2.isNotBlank()) {
                binding.tvTag2.visibility = View.VISIBLE
                binding.tvTag2.text = dish.tag2
            } else {
                binding.tvTag2.visibility = View.GONE
            }

            // Ảnh hoặc Emoji
            if (dish.imageUrl.isNotBlank()) {
                binding.ivDishThumb.visibility = View.VISIBLE
                binding.tvDishEmoji.visibility = View.GONE
                Glide.with(context)
                    .load(dish.imageUrl)
                    .transform(CenterCrop(), RoundedCorners(12))
                    .placeholder(R.drawable.placeholder_food)
                    .error(R.drawable.placeholder_food)
                    .into(binding.ivDishThumb)
            } else {
                binding.ivDishThumb.visibility = View.VISIBLE
                binding.ivDishThumb.setImageResource(R.drawable.placeholder_food)
                binding.tvDishEmoji.text = dish.emoji
                binding.tvDishEmoji.visibility = View.VISIBLE
            }

            // Click vào card để xem món và mở quán
            binding.root.setOnClickListener {
                it.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    onDishClick(dish)
                }.start()
            }

            // Click vào nút '+'
            binding.btnAddDish.setOnClickListener {
                it.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    onAddClick(dish)
                }.start()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHomeDishBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
