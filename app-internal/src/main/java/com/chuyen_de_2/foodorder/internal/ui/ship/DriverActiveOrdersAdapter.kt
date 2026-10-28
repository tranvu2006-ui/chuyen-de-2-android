package com.chuyen_de_2.foodorder.internal.ui.ship

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.databinding.ItemDriverActiveOrderBinding

class DriverActiveOrdersAdapter(
    private val onContinueTrip: (Order) -> Unit,
    private val onCancelOrder: (Order) -> Unit
) : RecyclerView.Adapter<DriverActiveOrdersAdapter.ActiveOrderViewHolder>() {

    private val orders = mutableListOf<Order>()

    fun submitList(list: List<Order>) {
        orders.clear()
        orders.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActiveOrderViewHolder {
        val binding = ItemDriverActiveOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ActiveOrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ActiveOrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    inner class ActiveOrderViewHolder(
        private val binding: ItemDriverActiveOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            val shortCode = if (order.orderId.length > 5) order.orderId.takeLast(6).uppercase() else order.orderId
            binding.tvActiveOrderCode.text = "#ORD_OISHI_$shortCode"

            val ch = bindingAdapterPosition + 1
            if (orders.size > 1) {
                binding.tvActiveOrderBatchBadge.visibility = View.VISIBLE
                binding.tvActiveOrderBatchBadge.text = "Đơn $ch/${orders.size}"
            } else {
                binding.tvActiveOrderBatchBadge.visibility = View.GONE
            }

            val fee = if (order.shippingFee > 0) order.shippingFee else 15000L
            binding.tvActiveOrderFee.text = "💰 Tiền ship: +${fee.toVndCurrency()}"

            val isCod = order.paymentMethod.equals("COD", ignoreCase = true)
            val totalPayment = if (order.totalPayment > 0) order.totalPayment else (order.total + fee)
            if (isCod) {
                binding.tvActivePaymentBadge.text = "💵 Thu COD: ${totalPayment.toVndCurrency()}"
                binding.tvActivePaymentBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFBEB"))
                binding.tvActivePaymentBadge.setTextColor(Color.parseColor("#D97706"))
            } else {
                binding.tvActivePaymentBadge.text = "✓ Đã thanh toán online"
                binding.tvActivePaymentBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F0FDF4"))
                binding.tvActivePaymentBadge.setTextColor(Color.parseColor("#15803D"))
            }

            binding.tvActiveOrderStatusTag.text = when (order.status) {
                Order.Status.ARRIVED_AT_SHOP -> "Đã đến quán"
                Order.Status.DELIVERING -> "Đang giao khách"
                Order.Status.ARRIVED_AT_CUSTOMER -> "Đã đến nhà khách"
                Order.Status.RETURNING_TO_STORE -> "Đang trả quán"
                else -> "Đang lấy món"
            }

            val cusName = order.customerName.ifBlank { "Khách hàng" }
            val address = order.address.ifBlank { "Quận 1, TP.HCM" }
            binding.tvActiveOrderRoute.text = "$cusName - $address"

            val dishSummary = if (order.items.isNotEmpty()) {
                order.items.values.joinToString(", ") { "${it.quantity}x ${it.dishName.ifBlank { it.name }}" }
            } else {
                "Món ăn theo đơn"
            }
            binding.tvActiveOrderDishes.text = "Món: $dishSummary"

            if (orders.size > 1) {
                binding.btnContinueTrip.text = "🛵 TIẾP TỤC CHẶNG $ch/${orders.size} ➔"
            } else {
                binding.btnContinueTrip.text = "🛵 TIẾP TỤC LỘ TRÌNH ➔"
            }

            binding.btnContinueTrip.setOnClickListener {
                onContinueTrip(order)
            }

            binding.btnCancelOrder.setOnClickListener {
                onCancelOrder(order)
            }
        }
    }
}
