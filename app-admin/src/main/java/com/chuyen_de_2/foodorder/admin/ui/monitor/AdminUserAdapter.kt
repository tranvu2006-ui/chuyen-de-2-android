package com.chuyen_de_2.foodorder.admin.ui.monitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.ItemAdminUserBinding
import com.chuyen_de_2.foodorder.core.data.model.User
import java.util.Locale

class AdminUserAdapter(
    private val onToggleLock: (User) -> Unit,
    private val onUserClick: ((UserWithCustomerInfo) -> Unit)? = null
) : ListAdapter<UserWithCustomerInfo, AdminUserAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemAdminUserBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(private val binding: ItemAdminUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UserWithCustomerInfo) {
            val user = item.user
            val context = itemView.context

                        binding.tvUserName.text = user.name.ifBlank { "Tài khoản #${user.uid.takeLast(6)}" }
            binding.tvUserEmail.text = "${user.email.ifBlank { "Không có email" }} • ${user.phone.ifBlank { "Chưa có SĐT" }}"

            val avatar = user.avatarUrl.ifBlank { item.customer?.avatarUrl.orEmpty() }
            if (avatar.isNotBlank()) {
                binding.ivUserAvatar.clearColorFilter()
                Glide.with(context)
                    .load(avatar)
                    .circleCrop()
                    .placeholder(R.drawable.ic_user)
                    .error(R.drawable.ic_user)
                    .into(binding.ivUserAvatar)
            } else {
                Glide.with(context).clear(binding.ivUserAvatar)
                binding.ivUserAvatar.setImageResource(R.drawable.ic_user)
                binding.ivUserAvatar.setColorFilter(ContextCompat.getColor(context, R.color.secondary))
            }

            val roleUpper = user.role.uppercase(Locale.getDefault())
            binding.tvUserRoleBadge.text = roleUpper

            when (roleUpper) {
                User.Role.ADMIN -> {
                    binding.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_red)
                    binding.tvUserRoleBadge.setTextColor(ContextCompat.getColor(context, R.color.danger))
                }
                User.Role.MERCHANT -> {
                    binding.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                    binding.tvUserRoleBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
                }
                User.Role.DRIVER, User.Role.SHIPPER -> {
                    binding.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_green)
                    binding.tvUserRoleBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_green))
                }
                else -> {
                    binding.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_blue)
                    binding.tvUserRoleBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_blue))
                }
            }

            // Customer Trust Score & Tier Badge
            val c = item.customer
            val score = c?.trustScore ?: 100
            val completed = c?.completedOrders ?: 0
            val boomed = c?.boomOrders ?: 0
            val tierBadge = c?.tierBadge ?: "💎 Hạng Kim Cương"

            binding.tvCustomerTrust.visibility = View.VISIBLE
            binding.tvCustomerTrust.text = "$tierBadge ($score/100) • Nhận: $completed • Bom: $boomed"

            when {
                score >= 95 -> {
                    binding.tvCustomerTrust.setBackgroundResource(R.drawable.bg_badge_green)
                    binding.tvCustomerTrust.setTextColor(ContextCompat.getColor(context, R.color.brand_green))
                }
                score >= 80 -> {
                    binding.tvCustomerTrust.setBackgroundResource(R.drawable.bg_badge_orange)
                    binding.tvCustomerTrust.setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
                }
                score >= 50 -> {
                    binding.tvCustomerTrust.setBackgroundResource(R.drawable.bg_badge_orange)
                    binding.tvCustomerTrust.setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
                }
                else -> {
                    binding.tvCustomerTrust.setBackgroundResource(R.drawable.bg_badge_red)
                    binding.tvCustomerTrust.setTextColor(ContextCompat.getColor(context, R.color.danger))
                }
            }

            // Lock / Unlock button & indicator
            val isLocked = !user.isActive ||
                user.status.equals("LOCKED", ignoreCase = true) ||
                user.status.equals("SUSPENDED", ignoreCase = true) ||
                user.status.equals(User.Status.INACTIVE, ignoreCase = true)

            if (!isLocked) {
                binding.btnToggleLock.text = "Khóa"
                binding.btnToggleLock.setTextColor(ContextCompat.getColor(context, R.color.danger))
                binding.btnToggleLock.strokeColor = ContextCompat.getColorStateList(context, R.color.danger)
            } else {
                binding.tvUserRoleBadge.text = "$roleUpper • ĐÃ KHÓA"
                binding.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_red)
                binding.tvUserRoleBadge.setTextColor(ContextCompat.getColor(context, R.color.danger))

                binding.btnToggleLock.text = "Mở khóa"
                binding.btnToggleLock.setTextColor(ContextCompat.getColor(context, R.color.brand_green))
                binding.btnToggleLock.strokeColor = ContextCompat.getColorStateList(context, R.color.brand_green)
            }

            binding.btnToggleLock.setOnClickListener {
                onToggleLock(user)
            }

            itemView.setOnClickListener {
                onUserClick?.invoke(item)
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<UserWithCustomerInfo>() {
        override fun areItemsTheSame(
            oldItem: UserWithCustomerInfo,
            newItem: UserWithCustomerInfo
        ): Boolean = oldItem.user.uid == newItem.user.uid

        override fun areContentsTheSame(
            oldItem: UserWithCustomerInfo,
            newItem: UserWithCustomerInfo
        ): Boolean = oldItem == newItem
    }
}
