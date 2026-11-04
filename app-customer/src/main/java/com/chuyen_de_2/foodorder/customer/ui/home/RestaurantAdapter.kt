package com.chuyen_de_2.foodorder.customer.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ItemRestaurantBinding

typealias StoreAdapter = RestaurantAdapter

class RestaurantAdapter(
    private val onClick: (Store) -> Unit
) : ListAdapter<Store, RestaurantAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemRestaurantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(store: Store) {
            val context = binding.root.context
            binding.tvName.text = store.name
            binding.tvAddress.text = store.address
            binding.tvCategory.text = if (store.category.isNotBlank()) store.category else "Ẩm thực"
            binding.tvDeliveryTime.text = if (store.deliveryTime.isNotBlank()) "⏱ ${store.deliveryTime}" else ""
            binding.tvDeliveryTime.visibility = if (store.deliveryTime.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
            binding.tvRating.text = if (store.rating > 0f) "⭐ ${store.rating}" else ""
            binding.tvRating.visibility = if (store.rating > 0f) android.view.View.VISIBLE else android.view.View.GONE

            // Status badge (ShopeeFood / Oishi style)
            if (store.isOpen) {
                binding.tvStatus.text = "Đang Mở Cửa"
                binding.tvStatus.setBackgroundResource(R.drawable.bg_status_open)
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.brand_green))
            } else {
                binding.tvStatus.text = "Đã Đóng Cửa"
                binding.tvStatus.setBackgroundResource(R.drawable.bg_status_closed)
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_closed))
            }

            // Load ảnh quán đồng bộ URL / Base64 qua ImageLoadUtils
            com.chuyen_de_2.foodorder.core.util.ImageLoadUtils.loadStoreImage(
                imageView = binding.ivRestaurant,
                imageUrl = store.effectiveImageUrl,
                placeholderResId = R.drawable.placeholder_restaurant,
                cornerDp = 16
            )

            // Click animation
            binding.root.setOnClickListener {
                it.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    onClick(store)
                }.start()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRestaurantBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class DiffCallback : DiffUtil.ItemCallback<Store>() {
        override fun areItemsTheSame(old: Store, new: Store) = old.id == new.id
        override fun areContentsTheSame(old: Store, new: Store) = old == new
    }
}
