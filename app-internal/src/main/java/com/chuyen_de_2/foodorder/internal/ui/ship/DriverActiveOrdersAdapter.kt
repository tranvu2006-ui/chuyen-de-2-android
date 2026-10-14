package com.chuyen_de_2.foodorder.internal.ui.ship

import android.view.LayoutInflater
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

            val fee = if (order.shippingFee > 0) order.shippingFee else 25000L
            binding.tvActiveOrderFee.text = "(+${fee.toVndCurrency()})"

            val isDelivering = order.status == Order.Status.DELIVERING
            binding.tvActiveOrderStatusTag.text = if (isDelivering) "Đang giao khách" else "Đang lấy món"

            val cusName = order.customerName.ifBlank { "Khách hàng" }
            val address = order.address.ifBlank { "Quận 1, TP.HCM" }
            binding.tvActiveOrderRoute.text = "📍 Giao đến: $cusName - $address"

            val dishSummary = if (order.items.isNotEmpty()) {
                order.items.values.joinToString(", ") { "${it.quantity}x ${it.dishName}" }
            } else {
                "1x Trà Ô Long, 1x Trà Ngô Gia (Size M)"
            }
            binding.tvActiveOrderDishes.text = "📦 Món: $dishSummary"

            binding.btnContinueTrip.setOnClickListener {
                onContinueTrip(order)
            }

            binding.btnCancelOrder.setOnClickListener {
                onCancelOrder(order)
            }
        }
    }
}
