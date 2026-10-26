package com.chuyen_de_2.foodorder.admin.ui.store

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.ItemAdminStoreBinding
import com.chuyen_de_2.foodorder.core.data.model.Store

class AdminStoreAdapter(
    private val onStoreClick: (Store) -> Unit
) : ListAdapter<Store, AdminStoreAdapter.StoreViewHolder>(StoreDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val binding = ItemAdminStoreBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StoreViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StoreViewHolder(private val binding: ItemAdminStoreBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(store: Store) {
            val context = itemView.context

            binding.tvStoreName.text = store.name.ifBlank { "Quán ăn #${store.id.takeLast(6)}" }
            binding.tvOwnerInfo.text = "Chủ: ${store.ownerName.ifBlank { "Chưa cập nhật" }} • ${store.phone.ifBlank { "Không có SĐT" }}"
            binding.tvAddress.text = "Địa chỉ: ${store.address.ifBlank { "Chưa cập nhật địa chỉ" }}"
            binding.tvRating.text = "⭐ ${String.format("%.1f", if (store.ratingAvg > 0) store.ratingAvg else 5.0f)}"

            // CCCD Badge
            if (store.cccdNumber.isNotBlank()) {
                binding.tvCccdBadge.text = "CCCD: ${store.cccdNumber}"
                binding.tvCccdBadge.visibility = View.VISIBLE
            } else {
                binding.tvCccdBadge.text = "Chưa nộp CCCD"
                binding.tvCccdBadge.visibility = View.VISIBLE
            }

            // Status Badge
            if (!store.isVerified || store.storeStatus.equals(Store.StoreStatus.PENDING, ignoreCase = true)) {
                binding.tvStoreStatusBadge.text = "CHỜ DUYỆT CCCD"
                binding.tvStoreStatusBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.tvStoreStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
            } else if (store.storeStatus.equals(Store.StoreStatus.LOCKED, ignoreCase = true)) {
                binding.tvStoreStatusBadge.text = "BỊ KHÓA"
                binding.tvStoreStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
                binding.tvStoreStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.danger))
            } else if (store.storeStatus.equals(Store.StoreStatus.OPEN, ignoreCase = true)) {
                binding.tvStoreStatusBadge.text = "ĐANG MỞ CỬA"
                binding.tvStoreStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
                binding.tvStoreStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_green))
            } else {
                binding.tvStoreStatusBadge.text = "TẠM ĐÓNG CỬA"
                binding.tvStoreStatusBadge.setBackgroundResource(R.drawable.bg_badge_duty_off)
                binding.tvStoreStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }

            // Penalty Badge
            if (store.penaltyTier != Store.PenaltyTier.NONE && store.penaltyTier.isNotBlank()) {
                binding.tvPenaltyBadge.visibility = View.VISIBLE
                binding.tvPenaltyBadge.text = "PHẠT: ${store.penaltyTier}"
            } else {
                binding.tvPenaltyBadge.visibility = View.GONE
            }

            // Avatar
            if (store.imageUrl.isNotBlank()) {
                Glide.with(context)
                    .load(store.imageUrl)
                    .placeholder(R.drawable.ic_store)
                    .error(R.drawable.ic_store)
                    .into(binding.ivStoreAvatar)
            } else {
                binding.ivStoreAvatar.setImageResource(R.drawable.ic_store)
            }

            binding.root.setOnClickListener {
                onStoreClick(store)
            }
        }
    }

    class StoreDiffCallback : DiffUtil.ItemCallback<Store>() {
        override fun areItemsTheSame(oldItem: Store, newItem: Store): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Store, newItem: Store): Boolean =
            oldItem == newItem
    }
}
