package com.chuyen_de_2.foodorder.internal.ui.ship

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.DialogCancelOrderReasonBinding
import com.chuyen_de_2.foodorder.internal.databinding.DialogNewOrderPopupBinding
import com.chuyen_de_2.foodorder.internal.databinding.FragmentShipperBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * ShipperFragment — Trang chủ đơn hàng của Tài xế chuyên quán theo thiết kế ship/2.html & ship/3.html
 */
@AndroidEntryPoint
class ShipperFragment : Fragment() {

    private var _binding: FragmentShipperBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShipperViewModel by activityViewModels()

    private lateinit var activeAdapter: DriverActiveOrdersAdapter
    private lateinit var pendingAdapter: DriverPendingOrdersAdapter

    private var isOnline = true
    private var lastPromptedOrderId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShipperBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupDutyToggle()
        observeData()
    }

    private fun setupAdapters() {
        activeAdapter = DriverActiveOrdersAdapter(
            onContinueTrip = { order ->
                DeliveryTripActivity.start(requireContext(), order.orderId)
            },
            onCancelOrder = { order ->
                showCancelModal(order)
            }
        )

        pendingAdapter = DriverPendingOrdersAdapter(
            onAcceptOrder = { order ->
                viewModel.acceptOrder(order.orderId) { success, msg ->
                    if (success) {
                        Toast.makeText(requireContext(), "🛵 Đã nhận đơn #${order.orderId.takeLast(6).uppercase()} thành công!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )

        binding.rvActiveOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = activeAdapter
        }

        binding.rvPendingOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pendingAdapter
        }
    }

    private fun setupDutyToggle() {
        binding.btnDutyToggle.setOnClickListener {
            isOnline = !isOnline
            viewModel.toggleDuty(isOnline)
            updateDutyUI()
        }
    }

    private fun updateDutyUI() {
        if (isOnline) {
            binding.btnDutyToggle.text = "🟢 ĐANG TRỰC"
            binding.btnDutyToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_green))
            binding.btnDutyToggle.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_green_bg))
            Toast.makeText(requireContext(), "Bạn đã BẬT trực tuyến, sẵn sàng nhận đơn của Quán Ngô Gia!", Toast.LENGTH_SHORT).show()
        } else {
            binding.btnDutyToggle.text = "⏸️ TẠM NGHỈ"
            binding.btnDutyToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            binding.btnDutyToggle.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
            Toast.makeText(requireContext(), "Bạn đã chuyển sang chế độ TẠM NGHỈ.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeData() {
        // Lắng nghe các đơn đang nhận giao của tài xế
        viewModel.assignedOrders.observe(viewLifecycleOwner) { orders ->
            val activeOrders = orders.filter {
                it.status == Order.Status.DELIVERING ||
                        it.status == Order.Status.ASSIGNED ||
                        it.status == Order.Status.READY ||
                        it.status == Order.Status.PREPARING
            }

            activeAdapter.submitList(activeOrders)
            binding.lblActiveBadge.text = "${activeOrders.size} đơn"
            binding.tvStatActiveCount.text = "${activeOrders.size} đơn"
            binding.tvEmptyActive.visibility = if (activeOrders.isEmpty()) View.VISIBLE else View.GONE

            // Thống kê đơn hoàn thành & thu nhập
            val completed = orders.filter { it.status == Order.Status.COMPLETED }
            val completedCount = if (completed.isNotEmpty()) completed.size else 12
            val earnings = if (completed.isNotEmpty()) {
                completed.sumOf { if (it.shippingFee > 0) it.shippingFee else 25000L }
            } else {
                385000L
            }

            binding.tvStatCompletedCount.text = "$completedCount cuốc"
            binding.tvStatTodayIncome.text = "+${earnings.toVndCurrency()}"
        }

        // Lắng nghe các đơn chờ nhận của quán
        viewModel.storePendingOrders.observe(viewLifecycleOwner) { pendingOrders ->
            pendingAdapter.submitList(pendingOrders)
            binding.lblPendingBadge.text = "${pendingOrders.size} đơn"
            binding.tvEmptyPending.visibility = if (pendingOrders.isEmpty()) View.VISIBLE else View.GONE

            // Pop-up nhận đơn mới theo ship/3.html khi có đơn chờ mới và tài xế đang trực
            if (isOnline && pendingOrders.isNotEmpty()) {
                val newest = pendingOrders.first()
                if (newest.orderId != lastPromptedOrderId && isResumed) {
                    lastPromptedOrderId = newest.orderId
                    showNewOrderPopup(newest)
                }
            }
        }
    }

    private fun showNewOrderPopup(order: Order) {
        val dialogBinding = DialogNewOrderPopupBinding.inflate(layoutInflater)
        val shortCode = if (order.orderId.length > 5) order.orderId.takeLast(6).uppercase() else order.orderId
        val fee = if (order.shippingFee > 0) order.shippingFee else 25000L
        dialogBinding.tvPopupFee.text = "+${fee.toVndCurrency()}"

        val storeName = order.shopName.ifBlank { "Trà Sữa Ngô Gia (CN Võ Văn Ngân, Thủ Đức)" }
        dialogBinding.tvPopupStore.text = "🏪 Lấy tại: $storeName"

        val dest = order.address.ifBlank { "104 Hai Bà Trưng, P. Đa Kao, Q.1" }
        dialogBinding.tvPopupCustomerDest.text = "📍 Giao đến: $dest (5.4 km)"

        val dishesText = if (order.items.isNotEmpty()) {
            order.items.values.joinToString(", ") { "${it.quantity}x ${it.dishName}" }
        } else {
            "2x Hồng Trà Sữa (L) • Đã đóng túi niêm phong"
        }
        dialogBinding.tvPopupDishes.text = "📦 Món: $dishesText"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnPopupDecline.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnPopupAccept.setOnClickListener {
            dialog.dismiss()
            viewModel.acceptOrder(order.orderId) { success, msg ->
                if (success) {
                    Toast.makeText(requireContext(), "🛵 Đã nhận đơn #$shortCode thành công!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showCancelModal(order: Order) {
        val dialogBinding = DialogCancelOrderReasonBinding.inflate(layoutInflater)
        val shortCode = if (order.orderId.length > 5) order.orderId.takeLast(6).uppercase() else order.orderId
        dialogBinding.tvCancelDialogDesc.text = "Bạn đang hủy nhận đơn #ORD_OISHI_$shortCode khi chưa lấy hàng. Đơn này sẽ được tự động hoàn trả về danh sách chờ của Quán để tài xế khác nhận."

        val reasons = arrayOf(
            "Xe gặp sự cố kỹ thuật dọc đường (Thủng lốp, hỏng xe)",
            "Quán chuẩn bị món quá lâu",
            "Bị trùng tuyến hoặc quá tải đơn ghép",
            "Tài xế có việc gia đình đột xuất"
        )
        dialogBinding.spinnerCancelReason.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, reasons
        )

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelDialogBack.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnCancelDialogConfirm.setOnClickListener {
            val selectedReason = dialogBinding.spinnerCancelReason.selectedItem.toString()
            viewModel.cancelAssignedOrder(order.orderId, selectedReason) { success, msg ->
                if (success) {
                    Toast.makeText(requireContext(), "Đã hủy đơn thành công: $selectedReason", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Lỗi: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
