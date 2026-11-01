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

            val isReturningToStore = order.status == Order.Status.RETURNING_TO_STORE
            if (isReturningToStore) {
                binding.tvActiveOrderStatusTag.text = "🚨 Đang trả món về quán"
                binding.tvActiveOrderStatusTag.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                binding.tvActiveOrderStatusTag.setTextColor(Color.parseColor("#DC2626"))
                binding.btnCancelOrder.visibility = View.GONE
                binding.btnContinueTrip.text = "🏪 QUAY VỀ QUÁN TRẢ MÓN ➔"
                binding.btnContinueTrip.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))
            } else {
                binding.tvActiveOrderStatusTag.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EFF6FF"))
                binding.tvActiveOrderStatusTag.setTextColor(Color.parseColor("#1D4ED8"))
                binding.tvActiveOrderStatusTag.text = when (order.status) {
                    Order.Status.ARRIVED_AT_SHOP -> "Đã đến quán"
                    Order.Status.DELIVERING -> "Đang giao khách"
                    Order.Status.ARRIVED_AT_CUSTOMER -> "Đã đến nhà khách"
                    else -> "Đang lấy món"
                }
                binding.btnCancelOrder.visibility = View.VISIBLE
                binding.btnContinueTrip.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EE4D2D"))
                if (orders.size > 1) {
                    binding.btnContinueTrip.text = "🛵 TIẾP TỤC CHẶNG $ch/${orders.size} ➔"
                } else {
                    binding.btnContinueTrip.text = "🛵 TIẾP TỤC LỘ TRÌNH ➔"
                }
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

            binding.btnContinueTrip.setOnClickListener {
                onContinueTrip(order)
            }

            binding.btnCancelOrder.setOnClickListener {
                onCancelOrder(order)
            }
        }
    }
}
