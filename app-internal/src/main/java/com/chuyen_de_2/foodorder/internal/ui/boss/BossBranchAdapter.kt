package com.chuyen_de_2.foodorder.internal.ui.boss

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.internal.databinding.ItemBossBranchBinding

/**
 * Adapter danh sách chi nhánh cửa hàng dành riêng cho BOSSMERCHANT.
 */
class BossBranchAdapter(
    private val onEnterBranchClick: (Store) -> Unit,
    private val onAssignManagerClick: (Store) -> Unit
) : ListAdapter<Store, BossBranchAdapter.BranchViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BranchViewHolder {
        val binding = ItemBossBranchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BranchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BranchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BranchViewHolder(private val binding: ItemBossBranchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(store: Store) {
            binding.tvBranchName.text = store.name.ifBlank { "Chi nhánh không tên" }
            
            val isClosed = store.storeStatus.equals(Store.StoreStatus.CLOSED, ignoreCase = true)
            if (isClosed) {
                binding.tvBranchStatus.text = "🔴 Tạm đóng"
                binding.tvBranchStatus.setTextColor(android.graphics.Color.parseColor("#EF4444"))
            } else {
                binding.tvBranchStatus.text = "🟢 Đang mở cửa"
                binding.tvBranchStatus.setTextColor(android.graphics.Color.parseColor("#10B981"))
            }

            val addressDisplay = buildString {
                if (store.address.isNotBlank()) append(store.address)
                if (store.district.isNotBlank()) {
                    if (isNotEmpty()) append(", ")
                    append(store.district)
                }
            }
            binding.tvBranchAddress.text = addressDisplay.ifBlank { "Chưa cập nhật địa chỉ chi nhánh" }

            // Hiển thị người quản lý (Merchant)
            if (store.merchantName.isNotBlank()) {
                binding.tvBranchManager.text = "Quản lý: ${store.merchantName}"
                binding.tvBranchManager.setTextColor(android.graphics.Color.parseColor("#0F172A"))
            } else if (store.merchantId.isNotBlank()) {
                binding.tvBranchManager.text = "Quản lý: ${store.merchantId.take(8)}..."
                binding.tvBranchManager.setTextColor(android.graphics.Color.parseColor("#0F172A"))
            } else {
                binding.tvBranchManager.text = "Quản lý: ⚠️ Chưa gán (Boss tự quản)"
                binding.tvBranchManager.setTextColor(android.graphics.Color.parseColor("#F59E0B"))
            }

            binding.btnEnterBranch.setOnClickListener { onEnterBranchClick(store) }
            binding.btnAssignManager.setOnClickListener { onAssignManagerClick(store) }
            binding.root.setOnClickListener { onEnterBranchClick(store) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Store>() {
            override fun areItemsTheSame(oldItem: Store, newItem: Store): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Store, newItem: Store): Boolean =
                oldItem == newItem
        }
    }
}

