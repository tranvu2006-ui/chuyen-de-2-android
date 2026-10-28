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
 * Tên chức năng: Kênh Nhận Đơn & Trực Tuyến Của Tài Xế (Shipper Orders & Dispatch Center)
 * Cách hoạt động:
 *  - Bật/Tắt chế độ trực tuyến (Online/Offline) nhận cuốc xe giao thức ăn.
 *  - Lắng nghe danh sách đơn chờ nhận (READY) từ Quán trực thuộc của tài xế.
 *  - Quản lý đơn đang thực hiện (Active Orders) và cho phép bấm "Tiếp tục giao" để mở màn hình lộ trình GPS [DeliveryTripActivity].
 *  - Hỗ trợ hủy đơn với lý do cụ thể (xe hỏng, việc gấp...) để hoàn trả đơn về danh sách chờ cho tài xế khác.
 *  - Thống kê tức thời: Số cuốc hoàn thành hôm nay và tổng thu nhập phí ship kiếm được.
 * Luồng hướng tới:
 *  - Nhận đơn -> Cập nhật trạng thái đơn sang ASSIGNED trên Firestore.
 *  - Bấm "Tiếp tục giao" -> Mở màn hình điều hướng lộ trình bản đồ [DeliveryTripActivity].
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

    /**
     * Tên chức năng: Khởi tạo View màn hình tài xế
     * Cách hoạt động: Gắn adapter, bật/tắt chế độ trực, gắn nút đăng xuất và lắng nghe dữ liệu từ ViewModel.
     * Luồng hướng tới: Sẵn sàng nhận đơn giao hàng.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupDutyToggle()
        setupLogout()
        observeData()
    }

    /**
     * Tên chức năng: Xử lý Đăng xuất ca trực của Tài xế
     * Cách hoạt động: Hiển thị AlertDialog cảnh báo; nếu xác nhận, xóa phiên Auth và chuyển về [LoginActivity].
     * Luồng hướng tới: Quay về [LoginActivity].
     */
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

    /**
     * Tên chức năng: Khởi tạo Adapter danh sách đơn đang giao và đơn chờ nhận
     * Cách hoạt động: Cấu hình 2 RecyclerView độc lập (rvActiveOrders và rvPendingOrders) kèm các sự kiện bấm nhận đơn, tiếp tục cuốc đi, và hủy nhận đơn.
     * Luồng hướng tới: Hiển thị các đơn hàng cho shipper.
     */
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

    /**
     * Tên chức năng: Chuyển đổi trạng thái Trực tuyến / Tạm nghỉ (Duty Toggle)
     * Cách hoạt động: Gạt trạng thái trực của tài xế và đồng bộ lên Firebase qua [ShipperViewModel.toggleDuty].
     * Luồng hướng tới: Hệ thống chỉ phát đơn mới khi tài xế ĐANG TRỰC.
     */
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

    /**
     * Tên chức năng: Lắng nghe và đồng bộ dữ liệu đơn hàng tài xế
     * Cách hoạt động:
     *  - Lắng nghe đơn do chính tài xế đang giao (ASSIGNED, DELIVERING, ARRIVED_AT_SHOP, ARRIVED_AT_CUSTOMER).
     *  - Lắng nghe đơn chờ nhận từ quán được phân công.
     *  - Tính toán số cuốc hoàn thành và tiền cước tích lũy trong ngày.
     * Luồng hướng tới: Cập nhật giao diện tức thời.
     */
    private fun observeData() {
        viewModel.assignedOrders.observe(viewLifecycleOwner) { orders ->
            val currentUid = authRepository.currentUser?.uid ?: ""
            val activeOrders = orders.filter {
                (it.status == Order.Status.DELIVERING ||
                 it.status == Order.Status.ASSIGNED ||
                 it.status == Order.Status.ARRIVED_AT_SHOP ||
                 it.status == Order.Status.ARRIVED_AT_CUSTOMER) &&
                        it.shipperId == currentUid
            }

            val hasActive = activeOrders.isNotEmpty()
            activeAdapter.submitList(activeOrders)
            binding.lblActiveBadge.text = "${activeOrders.size} đơn"
            binding.tvStatActiveCount.text = "${activeOrders.size} đơn"
            binding.tvEmptyActive.visibility = if (activeOrders.isEmpty()) View.VISIBLE else View.GONE
            binding.tvEmptyActive.text = "Bạn chưa có đơn đang giao. Hãy bấm nhận đơn bên dưới để đi giao!"

            viewModel.storePendingOrders.value?.let { pendingOrders ->
                pendingAdapter.submitList(pendingOrders, activeOrders.size)
            }

            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val startOfToday = calendar.timeInMillis

            val todayCompleted = orders.filter {
                it.status == Order.Status.COMPLETED &&
                it.shipperId == currentUid &&
                (it.createdAt >= startOfToday || it.timestamp >= startOfToday)
            }
            val completedCount = todayCompleted.size
            val earnings = todayCompleted.sumOf { if (it.shippingFee > 0) it.shippingFee else 0L }

            binding.tvStatCompletedCount.text = "$completedCount cuốc"
            binding.tvStatTodayIncome.text = "+${earnings.toVndCurrency()}"
        }

        viewModel.shipperStore.observe(viewLifecycleOwner) { (storeId, storeName) ->
            if (storeName.isNotBlank()) {
                binding.tvShopBadge.text = "🏪 $storeName"
            } else if (storeId.isNotBlank()) {
                binding.tvShopBadge.text = "🏪 Quán $storeId"
            } else {
                binding.tvShopBadge.text = "⚠️ Chưa gán quán"
            }
        }

        viewModel.storePendingOrders.observe(viewLifecycleOwner) { pendingOrders ->
            val hasActive = activeAdapter.itemCount > 0
            pendingAdapter.submitList(pendingOrders, activeAdapter.itemCount)
            binding.lblPendingBadge.text = "${pendingOrders.size} đơn"
            binding.tvEmptyPending.visibility = if (pendingOrders.isEmpty()) View.VISIBLE else View.GONE

            val currentStore = viewModel.shipperStore.value
            val storeTitle = currentStore?.second?.ifBlank { currentStore.first } ?: ""
            if (storeTitle.isNotBlank()) {
                binding.tvEmptyPending.text = "Hiện tại $storeTitle chưa có đơn mới cần giao."
            } else {
                binding.tvEmptyPending.text = "Tài xế chưa được liên kết với Quán nào. Vui lòng liên hệ Quán để được thêm vào danh sách tài xế nội bộ."
            }
        }
    }

    /**
     * Tên chức năng: Hộp thoại Hủy nhận đơn giao hàng (Cancel Order Modal)
     * Cách hoạt động: Cho phép tài xế chọn lý do bất khả kháng (hỏng xe, quán làm lâu...) và trả lại đơn về danh sách chờ.
     * Luồng hướng tới: Cập nhật đơn về trạng thái READY không có tài xế trên Firestore.
     */
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
