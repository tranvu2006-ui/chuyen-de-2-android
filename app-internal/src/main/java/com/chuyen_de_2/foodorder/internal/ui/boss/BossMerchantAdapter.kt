package com.chuyen_de_2.foodorder.internal.ui.boss

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.ItemBossMerchantBinding

class BossMerchantAdapter(
    private val onAssignBranchClick: (User) -> Unit,
    private val onUnassignBranchClick: (User) -> Unit,
    private val onToggleLockMerchantClick: (User) -> Unit
) : ListAdapter<User, BossMerchantAdapter.MerchantViewHolder>(MerchantDiffCallback()) {

    private var storesMap: Map<String, Store> = emptyMap()

    fun setStores(stores: List<Store>) {
        storesMap = stores.associateBy { it.id }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MerchantViewHolder {
        val binding = ItemBossMerchantBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MerchantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MerchantViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MerchantViewHolder(
        private val binding: ItemBossMerchantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(merchant: User) {
            binding.tvMerchantItemName.text = merchant.name.ifBlank { "Quản lý chưa đặt tên" }
            binding.tvMerchantItemEmail.text = merchant.email
            binding.tvMerchantItemPhone.text = merchant.phone.ifBlank { "Chưa có SĐT" }

            val isLocked = !merchant.isActive || merchant.status == User.Status.LOCKED || merchant.status == "SUSPENDED"

            if (isLocked) {
                binding.tvMerchantStatusBadge.text = "🔴 Đã khóa"
                binding.tvMerchantStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
                binding.tvMerchantStatusBadge.setTextColor(Color.parseColor("#B91C1C"))

                binding.btnToggleLockMerchant.text = "🔓 Mở khóa"
                binding.btnToggleLockMerchant.setTextColor(Color.parseColor("#15803D"))

                binding.tvMerchantAssignedBranch.text = "Tài khoản đang bị khóa"
                binding.tvMerchantAssignedBranch.setTextColor(Color.parseColor("#EF4444"))
                binding.ivBranchBadgeIcon.setColorFilter(Color.parseColor("#EF4444"))

                binding.btnAssignMerchantItem.isEnabled = false
                binding.btnAssignMerchantItem.alpha = 0.5f
                binding.btnUnassignMerchantItem.visibility = View.GONE
            } else {
                binding.tvMerchantStatusBadge.text = "🟢 Hoạt động"
                binding.tvMerchantStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
                binding.tvMerchantStatusBadge.setTextColor(Color.parseColor("#15803D"))

                binding.btnToggleLockMerchant.text = "🔒 Khóa"
                binding.btnToggleLockMerchant.setTextColor(Color.parseColor("#EF4444"))

                binding.btnAssignMerchantItem.isEnabled = true
                binding.btnAssignMerchantItem.alpha = 1.0f

                val assignedStore = if (merchant.storeId.isNotBlank()) storesMap[merchant.storeId] else null
                if (merchant.storeId.isNotBlank()) {
                    val branchTitle = assignedStore?.name?.ifBlank { null } ?: "Chi nhánh (${merchant.storeId.take(6)}...)"
                    binding.tvMerchantAssignedBranch.text = "Chi nhánh: $branchTitle"
                    binding.tvMerchantAssignedBranch.setTextColor(Color.parseColor("#2563EB"))
                    binding.ivBranchBadgeIcon.setColorFilter(Color.parseColor("#2563EB"))
                    binding.btnAssignMerchantItem.text = "Đổi quán"
                    binding.btnUnassignMerchantItem.visibility = View.VISIBLE
                } else {
                    binding.tvMerchantAssignedBranch.text = "Chưa phân công chi nhánh"
                    binding.tvMerchantAssignedBranch.setTextColor(Color.parseColor("#EA580C"))
                    binding.ivBranchBadgeIcon.setColorFilter(Color.parseColor("#EA580C"))
                    binding.btnAssignMerchantItem.text = "Phân công"
                    binding.btnUnassignMerchantItem.visibility = View.GONE
                }
            }

            binding.btnAssignMerchantItem.setOnClickListener {
                if (!isLocked) {
                    onAssignBranchClick(merchant)
                }
            }

            binding.btnUnassignMerchantItem.setOnClickListener {
                onUnassignBranchClick(merchant)
            }

            binding.btnToggleLockMerchant.setOnClickListener {
                onToggleLockMerchantClick(merchant)
            }
        }
    }

    class MerchantDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean = oldItem == newItem
    }
}
