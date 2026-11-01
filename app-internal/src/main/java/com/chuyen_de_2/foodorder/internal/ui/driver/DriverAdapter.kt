package com.chuyen_de_2.foodorder.internal.ui.driver

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.core.data.model.Driver
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.ItemDriverBinding

class DriverAdapter(
    private val onCallClick: (String) -> Unit
) : ListAdapter<Driver, DriverAdapter.DriverViewHolder>(DriverDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DriverViewHolder {
        val binding = ItemDriverBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DriverViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DriverViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DriverViewHolder(
        private val binding: ItemDriverBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(driver: Driver) {
            binding.tvDriverName.text = driver.name.ifBlank { "Tài xế ${driver.driverCode}" }
            binding.tvDriverCode.text = driver.driverCode.ifBlank { "TX" }
            binding.tvDriverPhone.text = "📞 ${driver.phone.ifBlank { "Chưa cập nhật" }}"

            // Load avatar
            if (driver.avatarUrl.isNotBlank()) {
                Glide.with(binding.root.context)
                    .load(driver.avatarUrl)
                    .placeholder(R.drawable.bg_badge_offline)
                    .error(R.drawable.bg_badge_offline)
                    .into(binding.ivDriverAvatar)
            } else {
                binding.ivDriverAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
            }

            // Status Badge & Styling
            when (driver.dutyStatus) {
                Driver.DutyStatus.AVAILABLE -> {
                    binding.tvStatusBadge.text = "🟢 Đang trực (Sẵn sàng)"
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_free)
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#10B981"))

                    binding.tvActionNote.text = "✓ Sẵn sàng nhận đơn"
                    binding.tvActionNote.setBackgroundResource(R.drawable.bg_badge_free)
                    binding.tvActionNote.setTextColor(Color.parseColor("#10B981"))
                }
                Driver.DutyStatus.DELIVERING -> {
                    val isReturning = driver.currentOrderCode.contains("Trả hàng", ignoreCase = true) ||
                            driver.currentOrderCode.contains("Trả món", ignoreCase = true)
                    if (isReturning) {
                        binding.tvStatusBadge.text = "🚨 Đang trả món về quán"
                        binding.tvActionNote.text = "Quay về quán trả món"
                    } else {
                        binding.tvStatusBadge.text = "🚚 Đang giao đơn"
                        binding.tvActionNote.text = "Đang giao trên đường"
                    }
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_busy)
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#EE4D2D"))
                    binding.tvActionNote.background = null
                    binding.tvActionNote.setTextColor(Color.parseColor("#EE4D2D"))
                }
                else -> {
                    binding.tvStatusBadge.text = "⏸️ Nghỉ ca (OFF)"
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_offline)
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#64748B"))

                    val noteText = driver.note.ifBlank { "Tài xế đang ngắt kết nối" }
                    binding.tvActionNote.text = noteText
                    binding.tvActionNote.background = null
                    binding.tvActionNote.setTextColor(Color.parseColor("#64748B"))
                }
            }

            // Xe máy
            val vehicleModelStr = if (driver.vehicleModel.isNotBlank()) driver.vehicleModel else "Yamaha Sirius"
            val vehiclePlateStr = if (driver.vehiclePlate.isNotBlank()) " (${driver.vehiclePlate})" else ""
            binding.tvVehicleInfo.text = "🏍️ Xe máy: $vehicleModelStr$vehiclePlateStr"

            // Đơn giao
            if (driver.dutyStatus == Driver.DutyStatus.DELIVERING) {
                val ordCode = driver.currentOrderCode.ifBlank { "Đang giao đơn của quán" }
                binding.tvOrderStats.text = "📦 Đang giao đơn: $ordCode"
                binding.tvOrderStats.setTextColor(Color.parseColor("#DC2626"))
            } else if (driver.dutyStatus == Driver.DutyStatus.AVAILABLE) {
                val today = driver.todayOrdersCount
                val total = driver.totalOrdersCount
                binding.tvOrderStats.text = "📦 Sẵn sàng • Đã giao $today đơn hôm nay ($total đơn tổng)"
                binding.tvOrderStats.setTextColor(Color.parseColor("#059669"))
            } else {
                binding.tvOrderStats.text = "📦 Tài xế đang tắt ca, không nhận đơn"
                binding.tvOrderStats.setTextColor(Color.parseColor("#64748B"))
            }

            // Đánh giá
            val ratingStr = String.format("%.1f", if (driver.ratingAvg > 0) driver.ratingAvg else 5.0f)
            binding.tvRating.text = "⭐ Đánh giá uy tín: $ratingStr / 5.0 (Tài xế trực thuộc quán)"

            // Gọi điện
            binding.btnCall.setOnClickListener {
                if (driver.phone.isNotBlank()) {
                    onCallClick(driver.phone)
                }
            }
        }
    }

    private class DriverDiffCallback : DiffUtil.ItemCallback<Driver>() {
        override fun areItemsTheSame(oldItem: Driver, newItem: Driver): Boolean {
            return oldItem.driverId == newItem.driverId
        }

        override fun areContentsTheSame(oldItem: Driver, newItem: Driver): Boolean {
            return oldItem == newItem
        }
    }
}
