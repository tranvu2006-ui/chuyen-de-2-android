package com.chuyen_de_2.foodorder.customer.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ItemNearbyStoreBinding

data class NearbyStoreItem(
    val id: String,
    val name: String,
    val address: String,
    val distanceKm: Double,
    val eta: String,
    val rating: Float,
    val imageUrl: String = "",
    val emoji: String = "🏪",
    val originalStore: Store? = null
)

class NearbyStoreAdapter(
    private val onStoreClick: (NearbyStoreItem) -> Unit
) : RecyclerView.Adapter<NearbyStoreAdapter.ViewHolder>() {

    private val items = mutableListOf<NearbyStoreItem>()

    fun submitList(newItems: List<NearbyStoreItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemNearbyStoreBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(store: NearbyStoreItem) {
            val context = binding.root.context
            binding.tvNearbyStoreName.text = store.name
            binding.tvNearbyStoreAddress.text = if (store.address.startsWith("📍")) store.address else "📍 ${store.address}"
            binding.tvNearbyDistance.text = if (store.distanceKm > 0.0) "📍 ${store.distanceKm} km" else "📍 Gần bạn"
            binding.tvNearbyEta.text = "⏱ ${store.eta}"
            binding.tvNearbyRating.text = "⭐ ${store.rating}"

            val displayImg = store.imageUrl.ifBlank { store.originalStore?.effectiveImageUrl.orEmpty() }
            if (displayImg.isNotBlank()) {
                binding.ivNearbyStore.visibility = View.VISIBLE
                binding.tvNearbyStoreEmoji.visibility = View.GONE
                com.chuyen_de_2.foodorder.core.util.ImageLoadUtils.loadStoreImage(
                    imageView = binding.ivNearbyStore,
                    imageUrl = displayImg,
                    placeholderResId = R.drawable.placeholder_restaurant,
                    cornerDp = 10
                )
            } else {
                binding.ivNearbyStore.visibility = View.VISIBLE
                binding.ivNearbyStore.setImageResource(R.drawable.placeholder_restaurant)
                binding.tvNearbyStoreEmoji.text = store.emoji
                binding.tvNearbyStoreEmoji.visibility = View.VISIBLE
            }

            binding.root.setOnClickListener {
                it.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    onStoreClick(store)
                }.start()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNearbyStoreBinding.inflate(
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
