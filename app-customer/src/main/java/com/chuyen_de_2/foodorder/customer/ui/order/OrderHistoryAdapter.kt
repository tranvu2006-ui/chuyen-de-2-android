package com.chuyen_de_2.foodorder.customer.ui.order

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ItemOrderHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderHistoryAdapter(
    private val onTrackClick: (Order) -> Unit,
    private val onComplaintClick: (Order) -> Unit,
    private val onReorderClick: (Order) -> Unit,
    private val onViewProofClick: (Order) -> Unit,
    private val onCallClick: (String) -> Unit
) : ListAdapter<Order, OrderHistoryAdapter.OrderViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(
        private val binding: ItemOrderHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            val context = binding.root.context
            val shopName = order.shopName.ifBlank { "Quán Ăn Oishi Food" }
            binding.tvShopName.text = shopName

            val orderCode = "#ORD_OISHI_${order.orderId.takeLast(6).uppercase()}"
            binding.tvOrderCodeMono.text = orderCode

            // Logo mini quán dạng emoji
            val shopEmoji = when {
                shopName.contains("trà", ignoreCase = true) || shopName.contains("sữa", ignoreCase = true) -> "🧋"
                shopName.contains("cơm", ignoreCase = true) -> "🍛"
                shopName.contains("phở", ignoreCase = true) || shopName.contains("bún", ignoreCase = true) -> "🍜"
                shopName.contains("gà", ignoreCase = true) -> "🍗"
                shopName.contains("bánh mì", ignoreCase = true) -> "🥖"
                else -> "🍲"
            }
            binding.tvShopLogoMini.text = shopEmoji

            // Tóm tắt món
            val summaryText = if (order.items.isNotEmpty()) {
                order.items.values.joinToString("\n") { item -> "• ${item.quantity}x ${item.name}" }
            } else {
                "• 1x Suất ăn tiêu chuẩn"
            }
            binding.tvDishesSummary.text = summaryText

            // Tổng tiền
            val total = if (order.totalPayment > 0) order.totalPayment else order.total
            binding.tvOrderPrice.text = total.toVndCurrency()

            // Ghi chú ngày giờ / thanh toán
            val time = if (order.timestamp > 0) order.timestamp else order.createdAt
            val dateStr = if (time > 0) {
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(time))
            } else {
                "Vừa xong"
            }
            binding.tvOrderSubStatus.text = dateStr

            // Phân loại trạng thái chi tiết theo b3.html
            val isWarningOrder = (order.note.contains("cảnh báo", ignoreCase = true) || order.note.contains("gọi", ignoreCase = true))

            if (isWarningOrder) {
                // 1. ĐƠN BỊ CẢNH BÁO MẤT ĐƠN (15 PHÚT)
                binding.cardOrder.strokeColor = ContextCompat.getColor(context, R.color.error)
                binding.cardOrder.setCardBackgroundColor(Color.parseColor("#FFFDFD"))

                binding.tvOrderStatusBadge.text = "🚨 CẢNH BÁO MẤT ĐƠN"
                binding.tvOrderStatusBadge.setTextColor(Color.parseColor("#DC2626"))
                binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_tab_badge_red)

                binding.tvShipperInfo.visibility = View.VISIBLE
                val shipper = order.shipperName.ifBlank { "Trần Văn Nam (TX-19)" }
                binding.tvShipperInfo.text = "🛵 Shipper: $shipper (Đã đến điểm giao)"
                binding.tvShipperInfo.setTextColor(Color.parseColor("#DC2626"))

                binding.layoutAlertBox.visibility = View.VISIBLE
                binding.tvAlertIcon.text = "📢"
                binding.tvAlertTitle.text = "Tài xế cảnh báo đếm ngược 15 phút!"
                binding.tvAlertCountdown.visibility = View.VISIBLE
                binding.tvAlertCountdown.text = "14:45"
                binding.tvAlertMessage.text = "Tài xế đã có mặt tại điểm giao và gọi nhiều cuộc nhưng chưa nghe máy. Vui lòng nhận đơn kẻo bị hủy do bom hàng!"

                binding.btnActionSecondary.visibility = View.VISIBLE
                binding.btnActionSecondary.text = "📋 Xem Chi Tiết"
                binding.btnActionSecondary.setOnClickListener { onViewProofClick(order) }

                binding.btnActionPrimary.text = "🚨 Xử Lý Ngay ➔"
                binding.btnActionPrimary.backgroundTintList = ContextCompat.getColorStateList(context, R.color.error)
                binding.btnActionPrimary.setOnClickListener { onTrackClick(order) }

            } else when (order.status) {
                Order.Status.PENDING -> {
                    // 2. CHỜ THANH TOÁN HOẶC CHỜ XÁC NHẬN
                    val isUnpaid = (order.paymentMethod != Order.PaymentMethod.COD && order.totalPayment == 0L)
                    if (isUnpaid) {
                        binding.cardOrder.strokeColor = Color.parseColor("#FDBA74")
                        binding.cardOrder.setCardBackgroundColor(Color.parseColor("#FFFCF9"))

                        binding.tvOrderStatusBadge.text = "⏳ Chờ thanh toán"
                        binding.tvOrderStatusBadge.setTextColor(Color.parseColor("#EA580C"))
                        binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_voucher_chip)

                        binding.tvShipperInfo.visibility = View.GONE

                        binding.layoutAlertBox.visibility = View.VISIBLE
                        binding.tvAlertIcon.text = "💡"
                        binding.tvAlertTitle.text = "Chưa hoàn tất thanh toán"
                        binding.tvAlertCountdown.visibility = View.VISIBLE
                        binding.tvAlertCountdown.text = "28:40"
                        binding.tvAlertMessage.text = "Vui lòng thanh toán để quán nhận đơn và chế biến."

                        binding.btnActionSecondary.visibility = View.VISIBLE
                        binding.btnActionSecondary.text = "✕ Hủy đơn"
                        binding.btnActionSecondary.setOnClickListener { onComplaintClick(order) }

                        binding.btnActionPrimary.text = "💳 Thanh Toán Ngay ➔"
                        binding.btnActionPrimary.backgroundTintList = ContextCompat.getColorStateList(context, R.color.primary)
                        binding.btnActionPrimary.setOnClickListener { onTrackClick(order) }
                    } else {
                        // CHỜ QUÁN XÁC NHẬN
                        binding.cardOrder.strokeColor = Color.parseColor("#BFDBFE")
                        binding.cardOrder.setCardBackgroundColor(Color.parseColor("#F8FAFC"))

                        binding.tvOrderStatusBadge.text = "⏱️ Chờ quán xác nhận"
                        binding.tvOrderStatusBadge.setTextColor(Color.parseColor("#2563EB"))
                        binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_tab_badge_blue)

                        binding.tvShipperInfo.visibility = View.VISIBLE
                        binding.tvShipperInfo.text = "👨‍🍳 Quán đang nhận đơn và chuẩn bị pha chế"
                        binding.tvShipperInfo.setTextColor(Color.parseColor("#2563EB"))

                        binding.layoutAlertBox.visibility = View.VISIBLE
                        binding.tvAlertIcon.text = "✓"
                        binding.tvAlertTitle.text = "Đã nhận đơn"
                        binding.tvAlertCountdown.visibility = View.GONE
                        binding.tvAlertMessage.text = "Quán đang chuẩn bị món ăn thơm ngon cho bạn."

                        binding.btnActionSecondary.visibility = View.VISIBLE
                        binding.btnActionSecondary.text = "📞 Gọi Quán"
                        binding.btnActionSecondary.setOnClickListener { onCallClick("02838229999") }

                        binding.btnActionPrimary.text = "Chi Tiết Đơn ➔"
                        binding.btnActionPrimary.backgroundTintList = ContextCompat.getColorStateList(context, R.color.brand_blue)
                        binding.btnActionPrimary.setOnClickListener { onTrackClick(order) }
                    }
                }

                Order.Status.PREPARING, Order.Status.READY -> {
                    binding.cardOrder.strokeColor = Color.parseColor("#BFDBFE")
                    binding.cardOrder.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))

                    binding.tvOrderStatusBadge.text = "🍳 Đang nấu món"
                    binding.tvOrderStatusBadge.setTextColor(Color.parseColor("#2563EB"))
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_tab_badge_blue)

                    binding.tvShipperInfo.visibility = View.VISIBLE
                    binding.tvShipperInfo.text = "👨‍🍳 Quán đang nấu món và đóng gói"
                    binding.tvShipperInfo.setTextColor(Color.parseColor("#2563EB"))

                    binding.layoutAlertBox.visibility = View.GONE

                    binding.btnActionSecondary.visibility = View.VISIBLE
                    binding.btnActionSecondary.text = "📞 Gọi Quán"
                    binding.btnActionSecondary.setOnClickListener { onCallClick("02838229999") }

                    binding.btnActionPrimary.text = "Theo Dõi Đơn ➔"
                    binding.btnActionPrimary.backgroundTintList = ContextCompat.getColorStateList(context, R.color.brand_blue)
                    binding.btnActionPrimary.setOnClickListener { onTrackClick(order) }
                }

                Order.Status.DELIVERING -> {
                    // ĐƠN ĐANG ĐẾN (Shipper đang giao)
                    binding.cardOrder.strokeColor = Color.parseColor("#BFDBFE")
                    binding.cardOrder.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))

                    binding.tvOrderStatusBadge.text = "🚚 Đang giao hàng"
                    binding.tvOrderStatusBadge.setTextColor(Color.parseColor("#2563EB"))
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_tab_badge_blue)

                    val shipper = order.shipperName.ifBlank { "Tài xế Oishi Food (TX-19)" }
                    binding.tvShipperInfo.visibility = View.VISIBLE
                    binding.tvShipperInfo.text = "🛵 Shipper: $shipper đang di chuyển"
                    binding.tvShipperInfo.setTextColor(Color.parseColor("#2563EB"))

                    binding.layoutAlertBox.visibility = View.GONE

                    binding.btnActionSecondary.visibility = View.VISIBLE
                    binding.btnActionSecondary.text = "📞 Gọi Shipper"
                    binding.btnActionSecondary.setOnClickListener { onCallClick("0909555888") }

                    binding.btnActionPrimary.text = "Theo Dõi Trực Tiếp ➔"
                    binding.btnActionPrimary.backgroundTintList = ContextCompat.getColorStateList(context, R.color.primary)
                    binding.btnActionPrimary.setOnClickListener { onTrackClick(order) }
                }

                Order.Status.COMPLETED -> {
                    // ĐƠN ĐÃ HOÀN TẤT
                    binding.cardOrder.strokeColor = Color.parseColor("#E2E8F0")
                    binding.cardOrder.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))

                    binding.tvOrderStatusBadge.text = "✓ Đã giao thành công"
                    binding.tvOrderStatusBadge.setTextColor(Color.parseColor("#10B981"))
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_status_open)

                    binding.tvShipperInfo.visibility = View.GONE
                    binding.layoutAlertBox.visibility = View.GONE

                    binding.btnActionSecondary.visibility = View.VISIBLE
                    binding.btnActionSecondary.text = "⚠️ Khiếu nại"
                    binding.btnActionSecondary.setOnClickListener { onComplaintClick(order) }

                    binding.btnActionPrimary.text = "↺ Đặt lại món"
                    binding.btnActionPrimary.backgroundTintList = ContextCompat.getColorStateList(context, R.color.text_primary)
                    binding.btnActionPrimary.setOnClickListener { onReorderClick(order) }
                }

                Order.Status.CANCELLED, Order.Status.BOOM -> {
                    // ĐƠN ĐÃ BỊ HỦY
                    binding.cardOrder.strokeColor = Color.parseColor("#FECACA")
                    binding.cardOrder.setCardBackgroundColor(Color.parseColor("#FFFDFD"))

                    binding.tvOrderStatusBadge.text = "✕ Đã bị hủy"
                    binding.tvOrderStatusBadge.setTextColor(Color.parseColor("#DC2626"))
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_tab_badge_red)

                    binding.tvShipperInfo.visibility = View.GONE

                    binding.layoutAlertBox.visibility = View.VISIBLE
                    binding.tvAlertIcon.text = "🚫"
                    binding.tvAlertTitle.text = "LÝ DO HỦY ĐƠN HÀNG:"
                    binding.tvAlertCountdown.visibility = View.GONE
                    binding.tvAlertMessage.text = if (order.status == Order.Status.BOOM) {
                        "Tài xế báo sự cố: Khách không nghe máy sau 15 phút đếm ngược (Bom hàng)"
                    } else {
                        "Hệ thống ghi nhận đơn đã bị hủy theo yêu cầu hoặc quá thời gian xác nhận."
                    }

                    binding.btnActionSecondary.visibility = View.VISIBLE
                    binding.btnActionSecondary.text = "📄 Xem biên bản"
                    binding.btnActionSecondary.setOnClickListener { onViewProofClick(order) }

                    binding.btnActionPrimary.text = "↺ Đặt lại món"
                    binding.btnActionPrimary.backgroundTintList = ContextCompat.getColorStateList(context, R.color.primary)
                    binding.btnActionPrimary.setOnClickListener { onReorderClick(order) }
                }

                else -> {
                    binding.cardOrder.strokeColor = Color.parseColor("#E2E8F0")
                    binding.cardOrder.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))

                    binding.tvOrderStatusBadge.text = order.status
                    binding.tvOrderStatusBadge.setTextColor(Color.parseColor("#64748B"))
                    binding.tvOrderStatusBadge.setBackgroundResource(R.drawable.bg_chip_unselected)

                    binding.tvShipperInfo.visibility = View.GONE
                    binding.layoutAlertBox.visibility = View.GONE

                    binding.btnActionSecondary.visibility = View.GONE
                    binding.btnActionPrimary.text = "Xem Chi Tiết ➔"
                    binding.btnActionPrimary.setOnClickListener { onTrackClick(order) }
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean =
            oldItem.orderId == newItem.orderId

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean =
            oldItem == newItem
    }
}
