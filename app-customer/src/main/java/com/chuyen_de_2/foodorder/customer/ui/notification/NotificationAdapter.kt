package com.chuyen_de_2.foodorder.customer.ui.notification

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.customer.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val onItemClick: (NotificationItem) -> Unit
) : ListAdapter<NotificationItem, NotificationAdapter.NotificationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(
        private val binding: ItemNotificationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationItem) {
            binding.tvNotifTitle.text = item.title
            binding.tvNotifBody.text = item.body
            binding.tvNotifTime.text = formatTimestamp(item.timestamp)

            // Hiển thị icon và màu nền icon
            binding.ivNotifIcon.setImageResource(item.iconRes)
            binding.ivNotifIcon.imageTintList = ColorStateList.valueOf(item.iconColor)

            // Điểm báo chưa đọc
            binding.viewUnreadDot.visibility = if (item.isUnread) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun formatTimestamp(time: Long): String {
            if (time <= 0) return "Gần đây"
            val now = System.currentTimeMillis()
            val diff = now - time
            return when {
                diff < 60_000 -> "Vừa xong"
                diff < 3600_000 -> "${diff / 60_000} phút trước"
                diff < 86400_000 -> "${diff / 3600_000} giờ trước"
                diff < 7 * 86400_000 -> "${diff / 86400_000} ngày trước"
                else -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(time))
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<NotificationItem>() {
            override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean =
                oldItem == newItem
        }
    }
}
