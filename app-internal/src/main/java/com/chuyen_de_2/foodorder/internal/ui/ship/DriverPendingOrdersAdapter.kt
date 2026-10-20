package com.chuyen_de_2.foodorder.internal.ui.ship

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.databinding.ItemDriverPendingOrderBinding

class DriverPendingOrdersAdapter(
    private val onAcceptOrder: (Order) -> Unit
) : RecyclerView.Adapter<DriverPendingOrdersAdapter.PendingOrderViewHolder>() {

    private val orders = mutableListOf<Order>()
    private var hasActiveOrders: Boolean = false

    fun submitList(list: List<Order>, hasActive: Boolean = false) {
        orders.clear()
        orders.addAll(list)
        this.hasActiveOrders = hasActive
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingOrderViewHolder {
        val binding = ItemDriverPendingOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PendingOrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PendingOrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    inner class PendingOrderViewHolder(
        private val binding: ItemDriverPendingOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            val shortCode = if (order.orderId.length > 5) order.orderId.takeLast(6).uppercase() else order.orderId
            binding.tvPendingOrderCode.text = "#ORD_OISHI_$shortCode"

            val fee = if (order.shippingFee > 0) order.shippingFee else 20000L
            binding.tvPendingOrderFee.text = "+${fee.toVndCurrency()}"

            val cusName = order.customerName.ifBlank { "Khách hàng" }
            val address = order.address.ifBlank { "Quận 1, TP.HCM" }
            binding.tvPendingOrderDest.text = "📍 Giao đến: $cusName - $address"

            val dishSummary = if (order.items.isNotEmpty()) {
                order.items.values.joinToString(", ") { "${it.quantity}x ${it.name}" }
            } else {
                "1x Món ăn theo đơn • Đã đóng túi niêm phong"
            }
            binding.tvPendingOrderDishes.text = dishSummary

            // Khi chưa có đơn nào đang giao thì chỉ hiện "NHẬN ĐƠN NÀY", chỉ khi đã có đơn mới hiện "(GHÉP CHẠY CÙNG)"
            if (hasActiveOrders) {
                binding.btnAcceptOrder.text = "🛵 NHẬN ĐƠN NÀY (GHÉP CHẠY CÙNG)"
            } else {
                binding.btnAcceptOrder.text = "🛵 NHẬN ĐƠN NÀY"
            }

            binding.btnAcceptOrder.setOnClickListener {
                onAcceptOrder(order)
            }
        }
    }
}
