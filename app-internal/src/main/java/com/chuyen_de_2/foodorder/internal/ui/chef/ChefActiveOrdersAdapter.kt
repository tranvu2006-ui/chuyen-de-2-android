package com.chuyen_de_2.foodorder.internal.ui.chef

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.QRCodeUtils
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.ItemChefActiveOrderBinding
import com.google.android.material.card.MaterialCardView

class ChefActiveOrdersAdapter(
    private val onMarkDone: (Order) -> Unit,
    private val onCancelOrder: (Order) -> Unit,
    private val onDismissCancelled: (Order) -> Unit = {}
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
            val context = binding.root.context
            val cardView = binding.root as? MaterialCardView
            val isCancelled = order.status.equals(Order.Status.CANCELLED, ignoreCase = true)

            binding.tvChefActiveCode.text = QRCodeUtils.formatOrderCode(order.orderId)

            val dishSummary = if (order.items.isNotEmpty()) {
                order.items.values.joinToString("\n") { "• ${it.quantity}x ${it.name}" }
            } else {
                "• Món ăn theo đơn"
            }
            binding.tvChefActiveDishes.text = dishSummary

            if (isCancelled) {
                // Đơn bị khách hủy: hiển thị cảnh báo đỏ và giữ trên màn hình bếp
                cardView?.strokeColor = ContextCompat.getColor(context, R.color.danger)
                cardView?.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()

                binding.tvChefActiveStatus.text = "🚫 KHÁCH ĐÃ HỦY • DỪNG NẤU"
                binding.tvChefActiveStatus.setBackgroundResource(R.drawable.bg_badge_red)
                binding.tvChefActiveStatus.setTextColor(Color.WHITE)

                val reason = order.cancelReason.ifBlank { "Khách đã hủy đơn hàng" }
                binding.tvChefActiveNote.visibility = View.VISIBLE
                binding.tvChefActiveNote.text = "⚠️ Khách đã hủy: $reason (Dừng làm món!)"
                binding.tvChefActiveNote.setBackgroundResource(R.drawable.bg_badge_red)
                binding.tvChefActiveNote.setTextColor(Color.WHITE)

                binding.btnChefCancel.visibility = View.GONE
                binding.btnChefDone.visibility = View.VISIBLE
                binding.btnChefDone.text = "✓ ĐÃ BIẾT (XÓA ĐƠN)"
                binding.btnChefDone.backgroundTintList = ContextCompat.getColorStateList(context, R.color.text_secondary)
                binding.btnChefDone.setOnClickListener {
                    onDismissCancelled(order)
                }
            } else {
                // Đơn đang nấu bình thường
                cardView?.strokeColor = ContextCompat.getColor(context, R.color.border)
                cardView?.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()

                binding.tvChefActiveStatus.text = "🔥 Đang Nấu"
                binding.tvChefActiveStatus.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.tvChefActiveStatus.setTextColor(ContextCompat.getColor(context, R.color.brand_orange))

                if (order.note.isNotBlank()) {
                    binding.tvChefActiveNote.visibility = View.VISIBLE
                    binding.tvChefActiveNote.text = "📝 Ghi chú: ${order.note}"
                    binding.tvChefActiveNote.setBackgroundResource(R.drawable.bg_badge_orange)
                    binding.tvChefActiveNote.setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
                } else {
                    binding.tvChefActiveNote.visibility = View.GONE
                }

                binding.btnChefCancel.visibility = View.VISIBLE
                binding.btnChefCancel.setOnClickListener {
                    onCancelOrder(order)
                }

                binding.btnChefDone.visibility = View.VISIBLE
                binding.btnChefDone.text = "🍳 ĐÃ NẤU XONG ➔"
                binding.btnChefDone.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_ready)
                binding.btnChefDone.setOnClickListener {
                    onMarkDone(order)
                }
            }
        }
    }
}
