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

    @javax.inject.Inject
    lateinit var authRepository: com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository

    private val viewModel: ShipperViewModel by activityViewModels()

    private lateinit var activeAdapter: DriverActiveOrdersAdapter
    private lateinit var pendingAdapter: DriverPendingOrdersAdapter

    private var isOnline = true

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
        setupLogout()
        observeData()
    }

    private fun setupLogout() {
        binding.btnShipperLogoutHeader.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("🚪 Đăng Xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất ca trực tài xế không?")
                .setPositiveButton("Đăng xuất") { _, _ ->
                    authRepository.logout()
                    val intent = android.content.Intent(requireContext(), com.chuyen_de_2.foodorder.internal.ui.auth.LoginActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("LOGOUT", true)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
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
            Toast.makeText(requireContext(), "Bạn đã BẬT trực tuyến, sẵn sàng nhận đơn!", Toast.LENGTH_SHORT).show()
        } else {
            binding.btnDutyToggle.text = "⏸️ TẠM NGHỈ"
            binding.btnDutyToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            binding.btnDutyToggle.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
            Toast.makeText(requireContext(), "Bạn đã chuyển sang chế độ TẠM NGHỈ.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeData() {
        // Lắng nghe các đơn đang nhận giao của tài xế (chỉ ASSIGNED hoặc DELIVERING do chính tài xế nhận)
        viewModel.assignedOrders.observe(viewLifecycleOwner) { orders ->
            val currentUid = authRepository.currentUser?.uid ?: ""
            val activeOrders = orders.filter {
                (it.status == Order.Status.DELIVERING || it.status == Order.Status.ASSIGNED) &&
                        it.shipperId == currentUid
            }

            activeAdapter.submitList(activeOrders)
            binding.lblActiveBadge.text = "${activeOrders.size} đơn"
            binding.tvStatActiveCount.text = "${activeOrders.size} đơn"
            binding.tvEmptyActive.visibility = if (activeOrders.isEmpty()) View.VISIBLE else View.GONE

            // Thống kê đơn hoàn thành & thu nhập thực tế
            val completed = orders.filter { it.status == Order.Status.COMPLETED && it.shipperId == currentUid }
            val completedCount = completed.size
            val earnings = completed.sumOf { if (it.shippingFee > 0) it.shippingFee else 25000L }

            binding.tvStatCompletedCount.text = "$completedCount cuốc"
            binding.tvStatTodayIncome.text = "+${earnings.toVndCurrency()}"
        }

        // Lắng nghe các đơn chờ nhận của quán (hiển thị trực tiếp trong danh sách theo chuẩn ship/2.html)
        viewModel.storePendingOrders.observe(viewLifecycleOwner) { pendingOrders ->
            pendingAdapter.submitList(pendingOrders)
            binding.lblPendingBadge.text = "${pendingOrders.size} đơn"
            binding.tvEmptyPending.visibility = if (pendingOrders.isEmpty()) View.VISIBLE else View.GONE
        }
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
