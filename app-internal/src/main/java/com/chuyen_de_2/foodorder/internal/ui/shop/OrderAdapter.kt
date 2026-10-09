package com.chuyen_de_2.foodorder.internal.ui.shop

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.ItemOrderBinding

/**
 * OrderAdapter — Hiển thị danh sách đơn hàng cho Quán theo mẫu Quán/5.html
 * Quy trình 3 bước: Xác nhận đơn -> Nấu xong -> Giao tài xế
 */
class OrderAdapter(
    private val onActionClick: (Order) -> Unit,
    private val onDetailClick: (Order) -> Unit,
    private val onChatCustomerClick: (Order) -> Unit,
    private val onCallDriverClick: (String) -> Unit,
    private val onWarningDetailClick: (Order) -> Unit
) : ListAdapter<Order, OrderAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            val context = binding.root.context
            val shortCode = order.orderId.takeLast(6).uppercase()
            binding.tvOrderCode.text = "#ORD_OISHI_$shortCode"

            // 1. Danh sách món ăn
            val dishesSummary = if (order.items.isNotEmpty()) {
                order.items.values.joinToString("\n") { "• ${it.quantity}x ${it.name}" }
            } else {
                "• 1x Suất ăn đặt trực tiếp"
            }
            val payText = when (order.paymentMethod) {
                Order.PaymentMethod.MOMO_QR -> "Đã thanh toán Ví MoMo"
                Order.PaymentMethod.BANKING -> "Đã thanh toán Ngân hàng"
                else -> "Thu tiền mặt khi nhận (COD)"
            }
            val total = if (order.totalPayment > 0) order.totalPayment else order.total
            binding.tvOrderDishes.text = "$dishesSummary\nTổng bill: ${total.toVndCurrency()} ($payText)"

            // 2. Địa chỉ & Khách
            val customerName = order.customerName.ifBlank { "Khách Hàng" }
            val address = order.address.ifBlank { order.deliveryAddress.ifBlank { "Địa chỉ nhận món" } }
            binding.tvOrderAddress.text = "📍 Giao đến: $customerName - $address"

            // 3. Kiểm tra xem đơn có bị cảnh báo mất đơn không
            val isWarning = order.note.contains("cảnh báo", ignoreCase = true) ||
                    order.note.contains("gọi", ignoreCase = true) ||
                    order.status == "WARNING"

            if (isWarning) {
                binding.cardOrderItem.strokeColor = ContextCompat.getColor(context, R.color.error)
                binding.cardOrderItem.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
                binding.tvOrderBadge.text = "🚨 CẢNH BÁO MẤT ĐƠN"
                binding.tvOrderBadge.setBackgroundResource(R.drawable.bg_badge_red)
                binding.tvOrderBadge.setTextColor(Color.WHITE)

                binding.boxWarningIncident.visibility = View.VISIBLE
                binding.btnWarningDetail.visibility = View.VISIBLE
            } else {
                binding.cardOrderItem.strokeColor = ContextCompat.getColor(context, R.color.border)
                binding.cardOrderItem.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
                binding.boxWarningIncident.visibility = View.GONE
                binding.btnWarningDetail.visibility = View.GONE
            }

            // 4. Trạng thái Quy trình 3 bước (Quán/5.html)
            setupStatusAndActions(context, order)

            // Click Listeners
            binding.btnMainAction.setOnClickListener { onActionClick(order) }
            binding.btnDetail.setOnClickListener { onDetailClick(order) }
            binding.btnChatCustomer.setOnClickListener { onChatCustomerClick(order) }
            binding.btnCallDriver.setOnClickListener { onCallDriverClick("0903882910") }
            binding.btnWarningDetail.setOnClickListener { onWarningDetailClick(order) }
        }

        private fun setupStatusAndActions(context: Context, order: Order) {
            when (order.status) {
                Order.Status.PENDING -> {
                    // Bước 1: Mới nhận -> Chờ quán xác nhận
                    binding.tvOrderBadge.text = "⏳ Chờ Quán Xác Nhận Đơn"
                    binding.tvOrderBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                    binding.tvOrderBadge.setTextColor(ContextCompat.getColor(context, R.color.primary))

                    binding.boxDriverStatus.visibility = View.GONE

                    binding.btnMainAction.apply {
                        isEnabled = true
                        text = "✓ XÁC NHẬN ĐƠN CỦA KHÁCH ➔"
                        backgroundTintList = ContextCompat.getColorStateList(context, R.color.primary)
                        setTextColor(Color.WHITE)
                    }
                }

                Order.Status.PREPARING -> {
                    // Bước 2: Đã xác nhận -> Bếp đang nấu
                    binding.tvOrderBadge.text = "✓ Đã Xác Nhận Đơn • Bếp Đang Nấu"
                    binding.tvOrderBadge.setBackgroundResource(R.drawable.bg_badge_blue)
                    binding.tvOrderBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_blue))

                    binding.boxDriverStatus.visibility = View.VISIBLE
                    binding.boxDriverStatus.setBackgroundResource(R.drawable.bg_driver_searching)
                    binding.tvDriverStatusTxt.apply {
                        text = "⏳ Bếp đang chế biến món • Nấu xong mới phát đơn cho tài xế nhận"
                        setTextColor(Color.parseColor("#B45309"))
                    }
                    binding.btnCallDriver.visibility = View.GONE

                    binding.btnMainAction.apply {
                        isEnabled = true
                        text = "🍳 XÁC NHẬN ĐÃ NẤU XONG ➔"
                        backgroundTintList = ContextCompat.getColorStateList(context, R.color.brand_green)
                        setTextColor(Color.WHITE)
                    }
                }

                Order.Status.READY -> {
                    // Bước 3: Nấu xong -> Chờ giao tài xế
                    val driverText = if (order.shipperName.isNotBlank()) order.shipperName else "Trần Văn Nam (TX-14)"
                    binding.tvOrderBadge.text = "🛵 Món đã nấu xong • Chờ giao tài xế"
                    binding.tvOrderBadge.setBackgroundResource(R.drawable.bg_status_ready)
                    binding.tvOrderBadge.setTextColor(Color.WHITE)

                    binding.boxDriverStatus.visibility = View.VISIBLE
                    binding.boxDriverStatus.setBackgroundResource(R.drawable.bg_driver_assigned)
                    binding.tvDriverStatusTxt.apply {
                        text = "🛵 Món đã nấu xong • Tài xế $driverText đã nhận đơn, đang đến quầy"
                        setTextColor(Color.parseColor("#047857"))
                    }
                    binding.btnCallDriver.visibility = View.VISIBLE

                    binding.btnMainAction.apply {
                        isEnabled = true
                        text = "🛵 XÁC NHẬN GIAO CHO TÀI XẾ ➔"
                        backgroundTintList = ContextCompat.getColorStateList(context, R.color.brand_green)
                        setTextColor(Color.WHITE)
                    }
                }

                Order.Status.ASSIGNED, Order.Status.DELIVERING -> {
                    // Bước 4: Đã giao tài xế đi giao
                    val driverText = if (order.shipperName.isNotBlank()) order.shipperName else "Trần Văn Nam (TX-14)"
                    binding.tvOrderBadge.text = "🛵 Đang giao hàng • Tài xế đã nhận đơn"
                    binding.tvOrderBadge.setBackgroundResource(R.drawable.bg_status_delivering)
                    binding.tvOrderBadge.setTextColor(Color.WHITE)

                    binding.boxDriverStatus.visibility = View.VISIBLE
                    binding.boxDriverStatus.setBackgroundResource(R.drawable.bg_driver_assigned)
                    binding.tvDriverStatusTxt.apply {
                        text = "🛵 Đã giao túi món cho tài xế $driverText • Đang chạy đến khách"
                        setTextColor(Color.parseColor("#047857"))
                    }
                    binding.btnCallDriver.visibility = View.VISIBLE

                    binding.btnMainAction.apply {
                        isEnabled = false
                        text = "✓ ĐÃ GIAO CHO TÀI XẾ"
                        backgroundTintList = ContextCompat.getColorStateList(context, R.color.brand_green_bg)
                        setTextColor(Color.parseColor("#047857"))
                    }
                }

                Order.Status.COMPLETED -> {
                    binding.tvOrderBadge.text = "🎉 ĐÃ GIAO THÀNH CÔNG"
                    binding.tvOrderBadge.setBackgroundResource(R.drawable.bg_status_completed)
                    binding.tvOrderBadge.setTextColor(Color.WHITE)

                    binding.boxDriverStatus.visibility = View.GONE
                    binding.btnMainAction.apply {
                        isEnabled = false
                        text = "✓ ĐƠN ĐÃ HOÀN TẤT"
                        backgroundTintList = ContextCompat.getColorStateList(context, R.color.border)
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    }
                }

                Order.Status.CANCELLED, Order.Status.BOOM -> {
                    binding.tvOrderBadge.text = "🚫 ĐÃ HỦY ĐƠN"
                    binding.tvOrderBadge.setBackgroundResource(R.drawable.bg_badge_red)
                    binding.tvOrderBadge.setTextColor(Color.WHITE)

                    binding.boxDriverStatus.visibility = View.GONE
                    binding.btnMainAction.apply {
                        isEnabled = false
                        text = "✕ ĐƠN ĐÃ HỦY"
                        backgroundTintList = ContextCompat.getColorStateList(context, R.color.border)
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class DiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(old: Order, new: Order) = old.orderId == new.orderId
        override fun areContentsTheSame(old: Order, new: Order) = old == new
    }
}
