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

class AdminOrderAdapter :
    ListAdapter<Order, AdminOrderAdapter.OrderViewHolder>(OrderDiffCallback()) {

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
        }
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean =
            oldItem.orderId == newItem.orderId

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean =
            oldItem == newItem
    }
}
