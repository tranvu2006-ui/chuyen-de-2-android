package com.chuyen_de_2.foodorder.internal.ui.chef

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.QRCodeUtils
import com.chuyen_de_2.foodorder.internal.databinding.ItemChefPendingOrderBinding

class ChefPendingOrdersAdapter(
    private val onAcceptOrder: (Order) -> Unit
) : RecyclerView.Adapter<ChefPendingOrdersAdapter.PendingChefViewHolder>() {

    private val orders = mutableListOf<Order>()

    fun submitList(list: List<Order>) {
        orders.clear()
        orders.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingChefViewHolder {
        val binding = ItemChefPendingOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PendingChefViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PendingChefViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    inner class PendingChefViewHolder(
        private val binding: ItemChefPendingOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            binding.tvChefPendingCode.text = QRCodeUtils.formatOrderCode(order.orderId)
            binding.tvChefPendingTime.text = "Chờ Bếp Nhận"

            val dishSummary = if (order.items.isNotEmpty()) {
                order.items.values.joinToString("\n") { "• ${it.quantity}x ${it.name}" }
            } else {
                "• Món ăn theo đơn"
            }
            binding.tvChefPendingDishes.text = dishSummary

            if (order.note.isNotBlank()) {
                binding.tvChefPendingNote.visibility = View.VISIBLE
                binding.tvChefPendingNote.text = "📝 Ghi chú: ${order.note}"
            } else {
                binding.tvChefPendingNote.visibility = View.GONE
            }

            binding.btnChefAcceptOrder.setOnClickListener {
                onAcceptOrder(order)
            }
        }
    }
}
