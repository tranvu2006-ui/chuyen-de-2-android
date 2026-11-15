package com.chuyen_de_2.foodorder.internal.ui.chef

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.QRCodeUtils
import com.chuyen_de_2.foodorder.internal.databinding.ItemChefActiveOrderBinding

class ChefActiveOrdersAdapter(
    private val onMarkDone: (Order) -> Unit,
    private val onCancelOrder: (Order) -> Unit
) : RecyclerView.Adapter<ChefActiveOrdersAdapter.ActiveChefViewHolder>() {

    private val orders = mutableListOf<Order>()

    fun submitList(list: List<Order>) {
        orders.clear()
        orders.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActiveChefViewHolder {
        val binding = ItemChefActiveOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ActiveChefViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ActiveChefViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    inner class ActiveChefViewHolder(
        private val binding: ItemChefActiveOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            binding.tvChefActiveCode.text = QRCodeUtils.formatOrderCode(order.orderId)
            binding.tvChefActiveStatus.text = "🔥 Đang Nấu"

            val dishSummary = if (order.items.isNotEmpty()) {
                order.items.values.joinToString("\n") { "• ${it.quantity}x ${it.name}" }
            } else {
                "• Món ăn theo đơn"
            }
            binding.tvChefActiveDishes.text = dishSummary

            if (order.note.isNotBlank()) {
                binding.tvChefActiveNote.visibility = View.VISIBLE
                binding.tvChefActiveNote.text = "📝 Ghi chú: ${order.note}"
            } else {
                binding.tvChefActiveNote.visibility = View.GONE
            }

            binding.btnChefDone.setOnClickListener {
                onMarkDone(order)
            }

            binding.btnChefCancel.setOnClickListener {
                onCancelOrder(order)
            }
        }
    }
}
