package com.chuyen_de_2.foodorder.admin.ui.monitor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.ItemAdminOrderBinding
import com.chuyen_de_2.foodorder.core.data.model.Order
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminOrderAdapter(
    private val onOrderClick: ((Order) -> Unit)? = null
) : ListAdapter<Order, AdminOrderAdapter.OrderViewHolder>(OrderDiffCallback()) {

    private val currencyFormat = DecimalFormat("#,### đ")
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemAdminOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(private val binding: ItemAdminOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            val context = itemView.context

            binding.tvOrderCode.text = "#${order.orderId.takeLast(8)}"
            val timeStr = if (order.createdAt > 0) dateFormat.format(Date(order.createdAt)) else "Vừa xong"
            binding.tvOrderTime.text = "Thời gian đặt: $timeStr"
            binding.tvOrderShopName.text = "Quán: ${order.shopName.ifBlank { "Quán ăn #${order.shopId.takeLast(6)}" }}"
            binding.tvOrderCustomer.text = "Khách: ${order.customerName.ifBlank { "Khách hàng" }} (${order.customerPhone.ifBlank { "Không có SĐT" }})"
            binding.tvOrderPrice.text = currencyFormat.format(order.totalPrice)

            val statusUpper = order.orderStatus.uppercase(Locale.getDefault())
            binding.tvOrderStatusBadge.text = statusUpper

            when (statusUpper) {
                Order.OrderStatus.COMPLETED -> {
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
                    binding.tvOrderStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_green))
                }
                Order.OrderStatus.BOOM, Order.OrderStatus.CANCELLED -> {
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
                    binding.tvOrderStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.danger))
                }
                Order.OrderStatus.DELIVERING -> {
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_badge_blue)
                    binding.tvOrderStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_blue))
                }
                else -> {
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                    binding.tvOrderStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
                }
            }

            if (order.cancelRequested) {
                binding.tvOrderCancelNotice.visibility = android.view.View.VISIBLE
                val reason = order.cancelRequestReason.ifBlank { "Khách xin hủy đơn" }
                val reqStatus = when (order.cancelRequestStatus.uppercase(Locale.getDefault())) {
                    "PENDING" -> "Chờ quán xử lý"
                    "REJECTED" -> "Quán từ chối hủy"
                    "APPROVED" -> "Quán chấp thuận hủy"
                    else -> order.cancelRequestStatus.ifBlank { "Chờ xử lý" }
                }
                binding.tvOrderCancelNotice.text = "Khách yêu cầu hủy: $reason • $reqStatus"
                binding.tvOrderCancelNotice.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.tvOrderCancelNotice.setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
            } else if (statusUpper == Order.OrderStatus.CANCELLED) {
                binding.tvOrderCancelNotice.visibility = android.view.View.VISIBLE
                val reason = order.cancelReason.ifBlank { order.cancelRequestReason.ifBlank { "Đã hủy đơn hàng" } }
                binding.tvOrderCancelNotice.text = "Lý do hủy: $reason"
                binding.tvOrderCancelNotice.setBackgroundResource(R.drawable.bg_badge_red)
                binding.tvOrderCancelNotice.setTextColor(ContextCompat.getColor(context, R.color.danger))
            } else {
                binding.tvOrderCancelNotice.visibility = android.view.View.GONE
            }

            itemView.setOnClickListener {
                onOrderClick?.invoke(order)
            }
        }
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean =
            oldItem.orderId == newItem.orderId

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean =
            oldItem == newItem
    }
}
