package com.chuyen_de_2.foodorder.customer.ui.order

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityOrderHistoryBinding
import com.chuyen_de_2.foodorder.customer.ui.complaint.ComplaintActivity
import com.chuyen_de_2.foodorder.customer.ui.menu.MenuActivity
import com.chuyen_de_2.foodorder.customer.ui.tracking.TrackingActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tên chức năng: Màn Hình Lịch Sử Đơn Hàng & Xử Lý Sự Cố (Customer Order History & Incidents)
 * Cách hoạt động:
 *  - Truy xuất toàn bộ lịch sử đơn hàng của khách hàng từ Firestore.
 *  - Phân loại đơn hàng theo các Tab Chip: Tất cả, Khẩn cấp (Cảnh báo nhỡ cuộc gọi/Bom), Đang giao, Chờ xác nhận, Chưa thanh toán, Đã giao, Đã hủy.
 *  - Cung cấp các tính năng thao tác nhanh trên từng đơn:
 *      + Bấm 'Theo dõi' -> Mở bản đồ GPS realtime [TrackingActivity].
 *      + Bấm 'Khiếu nại' -> Mở form phản hồi sự cố [ComplaintActivity].
 *      + Bấm 'Đặt lại' -> Mở lại thực đơn quán [MenuActivity] để mua lại.
 *      + Bấm 'Xem bằng chứng' -> Xem hình ảnh chụp tại chỗ của shipper [IncidentProofBottomSheetDialogFragment].
 *      + Bấm 'Gọi điện' -> Quay số gọi quán hoặc tài xế.
 * Luồng hướng tới:
 *  - Chuyển hướng tới [TrackingActivity], [ComplaintActivity], hoặc [MenuActivity].
 */
@AndroidEntryPoint
class OrderHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderHistoryBinding
    private lateinit var adapter: OrderHistoryAdapter

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private var allOrders: List<Order> = emptyList()
    private var currentFilterTab: String = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
        InsetUtils.applyToolbarInsets(binding.layoutTopHeader, 8)

        setupHeader()
        setupRecyclerView()
        setupTabs()
        setupEmergencyBanner()
        loadOrders()
    }

    private fun setupHeader() {
        binding.btnNavBack.setOnClickListener { finish() }

        val userName = firebaseAuth.currentUser?.displayName ?: firebaseAuth.currentUser?.email?.substringBefore("@") ?: "Bạn"
        binding.tvCustomerNameHeader.text = "Khách: $userName"
    }

    private fun setupRecyclerView() {
        adapter = OrderHistoryAdapter(
            onTrackClick = { order ->
                if (order.status == Order.Status.AWAITING_PAYMENT) {
                    val finalTotal = if (order.totalPayment > 0) order.totalPayment else order.total
                    val depositAmt = if (order.paymentMode == Order.PaymentMode.DEPOSIT) order.depositAmount else 0L
                    val remainAmt = if (order.paymentMode == Order.PaymentMode.DEPOSIT) order.remainAmount else 0L
                    val intent = Intent(this, com.chuyen_de_2.foodorder.customer.ui.payment.PaymentActivity::class.java).apply {
                        putExtra(Constants.EXTRA_ORDER_ID, order.orderId)
                        putExtra(Constants.EXTRA_SHOP_ID, order.storeId.ifBlank { order.shopId })
                        putExtra(Constants.EXTRA_SHOP_NAME, order.shopName)
                        putExtra("extra_address", order.deliveryAddress.ifBlank { order.address })
                        putExtra("extra_amount", if (order.paymentMode == Order.PaymentMode.DEPOSIT) depositAmt else finalTotal)
                        putExtra("extra_deposit", depositAmt)
                        putExtra("extra_remain", remainAmt)
                        putExtra("extra_total", finalTotal)
                        putExtra("extra_payment_mode", order.paymentMode)
                        putExtra("extra_created_at", order.createdAt)
                    }
                    startActivity(intent)
                } else {
                    val intent = Intent(this, TrackingActivity::class.java).apply {
                        putExtra(Constants.EXTRA_ORDER_ID, order.orderId)
                    }
                    startActivity(intent)
                }
            },
            onComplaintClick = { order ->
                val intent = Intent(this, ComplaintActivity::class.java).apply {
                    putExtra(Constants.EXTRA_ORDER_ID, order.orderId)
                    putExtra("EXTRA_STORE_ID", order.storeId.ifBlank { order.shopId })
                }
                startActivity(intent)
            },
            onReorderClick = { order ->
                val shopId = order.storeId.ifBlank { order.shopId }
                if (shopId.isNotBlank()) {
                    val intent = Intent(this, MenuActivity::class.java).apply {
                        putExtra(Constants.EXTRA_SHOP_ID, shopId)
                        putExtra(Constants.EXTRA_SHOP_NAME, order.shopName)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Không tìm thấy thông tin quán để đặt lại!", Toast.LENGTH_SHORT).show()
                }
            },
            onViewProofClick = { order ->
                val isBombed = isBombedOrCancelledOrder(order) && (
                        order.effectiveStatus.uppercase() == Order.Status.BOOM ||
                        order.effectiveStatus.uppercase() == Order.Status.RETURNING_TO_STORE ||
                        order.effectiveStatus.uppercase() == Order.Status.RETURNED_TO_STORE ||
                        order.isRiskOrder ||
                        order.cancelReason.contains("bom", ignoreCase = true) ||
                        order.riskReason.contains("bom", ignoreCase = true)
                )
                val modal = IncidentProofBottomSheetDialogFragment.newInstance(
                    orderId = order.orderId,
                    driverName = order.shipperName.ifBlank { "Tài xế giao hàng" },
                    reason = if (isBombed) {
                        order.cancelReason.ifBlank {
                            order.riskReason.ifBlank {
                                "Khách không nghe máy / từ chối nhận món sau thời gian chờ (Bom hàng)"
                            }
                        }
                    } else if (isActiveWarningOrder(order)) {
                        "Tài xế cảnh báo đếm ngược tại điểm giao"
                    } else {
                        order.cancelReason.ifBlank { "Đơn hàng đã bị hủy" }
                    },
                    notes = order.note.ifBlank {
                        order.cancelReason.ifBlank {
                            order.incidentWarningText.ifBlank {
                                "Tài xế đã có mặt tại điểm giao nhưng không liên lạc được."
                            }
                        }
                    }
                )
                modal.show(supportFragmentManager, "IncidentProofModal")
            },
            onCallClick = { phone ->
                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, "Số điện thoại: $phone", Toast.LENGTH_SHORT).show()
                }
            },
            onChatStoreClick = { order ->
                val chatSheet = com.chuyen_de_2.foodorder.customer.ui.tracking.ChatStoreBottomSheetDialogFragment.newInstance(
                    storeName = order.shopName.ifBlank { "Quán Ăn Oishi Food" },
                    orderId = order.orderId
                )
                chatSheet.show(supportFragmentManager, "ChatStoreBottomSheet_${order.orderId}")
            },
            onCancelOrderClick = { order ->
                handleCancelOrderClick(order)
            },
            onWithdrawCancelClick = { order ->
                handleWithdrawCancelClick(order)
            }
        )

        binding.rvOrders.layoutManager = LinearLayoutManager(this)
        binding.rvOrders.adapter = adapter
    }

    private fun handleCancelOrderClick(order: Order) {
        val status = order.effectiveStatus.uppercase()
        if (status == Order.Status.PENDING || status == Order.Status.PAID || status == Order.Status.AWAITING_PAYMENT) {
            showInstantCancelDialog(order)
        } else if (status == Order.Status.PREPARING || status == Order.Status.READY) {
            showRequestCancelDialog(order)
        }
    }

    private fun showInstantCancelDialog(order: Order) {
        val reasons = arrayOf(
            "Đặt nhầm món / trùng đơn",
            "Đặt nhầm địa chỉ giao hàng",
            "Đổi ý, không muốn mua nữa",
            "Quán lâu xác nhận",
            "Lý do khác..."
        )
        var selectedIndex = 0
        val input = android.widget.EditText(this).apply {
            hint = "Nhập lý do cụ thể..."
            visibility = View.GONE
            setPadding(40, 20, 40, 20)
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(input)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("✕ Hủy Đơn Hàng")
            .setMessage("Do quán chưa xác nhận làm món, bạn có thể hủy đơn ngay lập tức mà không bị ảnh hưởng đến điểm uy tín.\n\nVui lòng chọn lý do hủy:")
            .setSingleChoiceItems(reasons, 0) { _, which ->
                selectedIndex = which
                input.visibility = if (which == reasons.size - 1) View.VISIBLE else View.GONE
            }
            .setView(container)
            .setPositiveButton("Xác Nhận Hủy") { _, _ ->
                val reasonText = if (selectedIndex == reasons.size - 1) {
                    input.text.toString().trim().ifBlank { "Khách hủy do đặt nhầm" }
                } else {
                    reasons[selectedIndex]
                }
                lifecycleScope.launch {
                    firebaseRepository.cancelOrderImmediately(order.orderId, reasonText)
                    Toast.makeText(this@OrderHistoryActivity, "✓ Đã hủy đơn hàng thành công!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Giữ Lại Đơn", null)
            .show()
    }

    private fun showRequestCancelDialog(order: Order) {
        val reasons = arrayOf(
            "Đặt nhầm món, cần đặt lại",
            "Đặt nhầm địa chỉ nhận hàng",
            "Chờ quán chuẩn bị quá lâu",
            "Có việc bận đột xuất",
            "Lý do khác..."
        )
        var selectedIndex = 0
        val input = android.widget.EditText(this).apply {
            hint = "Nhập lý do cụ thể gửi quán..."
            visibility = View.GONE
            setPadding(40, 20, 40, 20)
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(input)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ Yêu Cầu Hủy Đơn")
            .setMessage("Quán đã tiếp nhận và đang chế biến món ăn. Để hủy đơn, yêu cầu của bạn sẽ được gửi tới quán để quán xác nhận hủy.\n\nVui lòng chọn lý do:")
            .setSingleChoiceItems(reasons, 0) { _, which ->
                selectedIndex = which
                input.visibility = if (which == reasons.size - 1) View.VISIBLE else View.GONE
            }
            .setView(container)
            .setPositiveButton("Gửi Yêu Cầu Hủy") { _, _ ->
                val reasonText = if (selectedIndex == reasons.size - 1) {
                    input.text.toString().trim().ifBlank { "Khách yêu cầu hủy do đặt nhầm" }
                } else {
                    reasons[selectedIndex]
                }
                lifecycleScope.launch {
                    firebaseRepository.requestCancelOrder(order.orderId, reasonText)
                    Toast.makeText(this@OrderHistoryActivity, "✓ Đã gửi yêu cầu hủy đơn đến quán! Vui lòng chờ phản hồi.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Quay Lại", null)
            .show()
    }

    private fun handleWithdrawCancelClick(order: Order) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Rút lại yêu cầu hủy đơn?")
            .setMessage("Bạn có muốn tiếp tục nhận đơn hàng này và rút lại yêu cầu hủy gửi quán không?")
            .setPositiveButton("Rút Lại Yêu Cầu") { _, _ ->
                lifecycleScope.launch {
                    firebaseRepository.withdrawCancelRequest(order.orderId)
                    Toast.makeText(this@OrderHistoryActivity, "✓ Đã rút lại yêu cầu hủy đơn", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun setupTabs() {
        binding.chipGroupTabs.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            currentFilterTab = when (checkedIds.first()) {
                R.id.tabWarning -> "WARNING"
                R.id.tabActive -> "ACTIVE"
                R.id.tabCancelled -> "CANCELLED"
                R.id.tabUnpaid -> "UNPAID"
                R.id.tabConfirming -> "CONFIRMING"
                R.id.tabHistory -> "COMPLETED"
                else -> "ALL"
            }
            applyFilter()
        }
    }

    private fun setupEmergencyBanner() {
        binding.btnViewWarningNow.setOnClickListener {
            binding.tabWarning.isChecked = true
        }
    }

    private fun loadOrders() {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId.isNullOrBlank()) {
            binding.progressBar.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            firebaseRepository.observeOrdersByCustomer(currentUserId).collectLatest { orders ->
                binding.progressBar.visibility = View.GONE
                allOrders = orders
                updateTabBadgeCounts(orders)
                applyFilter()
            }
        }
    }

    private fun isBombedOrCancelledOrder(order: Order): Boolean {
        val st = order.effectiveStatus.uppercase()
        return st == Order.Status.CANCELLED ||
                st == Order.Status.BOOM ||
                st == Order.Status.RETURNING_TO_STORE ||
                st == Order.Status.RETURNED_TO_STORE
    }

    private fun isActiveWarningOrder(order: Order): Boolean {
        val st = order.effectiveStatus.uppercase()
        if (st == Order.Status.COMPLETED || isBombedOrCancelledOrder(order)) return false
        return ((order.hasIncidentWarning || order.isRiskOrder) && order.countdownRound >= 2) ||
                order.riskStatus == "RISK_REPORTED" ||
                order.riskStatus == "RISK_CONFIRMED"
    }

    /**
     * Cập nhật số đếm badge trên các tab và kiểm tra banner khẩn cấp
     */
    private fun updateTabBadgeCounts(orders: List<Order>) {
        val now = System.currentTimeMillis()
        val countWarning = orders.count { isActiveWarningOrder(it) }
        val countActive = orders.count {
            val st = it.effectiveStatus.uppercase()
            st == Order.Status.DELIVERING ||
            st == Order.Status.ARRIVED_AT_CUSTOMER ||
            st == Order.Status.ARRIVED_AT_SHOP ||
            st == Order.Status.ASSIGNED
        }
        val countCancelled = orders.count { isBombedOrCancelledOrder(it) }
        val countUnpaid = orders.count {
            val st = it.effectiveStatus.uppercase()
            val isAwaiting = st == Order.Status.AWAITING_PAYMENT ||
                    (st == Order.Status.PENDING && it.paymentMethod != Order.PaymentMethod.COD && it.totalPayment == 0L)
            val isNotExpired = (now - it.createdAt) <= 30 * 60 * 1000L
            isAwaiting && isNotExpired
        }
        val countConfirming = orders.count {
            val st = it.effectiveStatus.uppercase()
            (st == Order.Status.PENDING || st == Order.Status.PREPARING || st == Order.Status.READY) && !isActiveWarningOrder(it)
        }

        // Tự động hủy trên database các đơn chờ thanh toán đã quá 30 phút
        orders.forEach { o ->
            if (o.effectiveStatus.uppercase() == Order.Status.AWAITING_PAYMENT && (now - o.createdAt) > 30 * 60 * 1000L) {
                com.google.firebase.database.FirebaseDatabase.getInstance().getReference("orders").child(o.orderId).updateChildren(
                    mapOf(
                        "status" to Order.Status.CANCELLED,
                        "orderStatus" to Order.Status.CANCELLED,
                        "note" to "Đã tự động hủy do quá hạn thanh toán 30 phút",
                        "updatedAt" to now
                    )
                )
            }
        }

        binding.tabWarning.text = "🚨 Bị cảnh báo ($countWarning)"
        binding.tabActive.text = "Đang đến ($countActive)"
        binding.tabCancelled.text = "🚫 Đã hủy / Bom ($countCancelled)"
        binding.tabUnpaid.text = "Chờ thanh toán ($countUnpaid)"
        binding.tabConfirming.text = "Chờ xác nhận ($countConfirming)"

        // Điều khiển Emergency Warning Banner
        if (countWarning > 0) {
            binding.layoutEmergencyWarningBanner.visibility = View.VISIBLE
            val warnOrder = orders.firstOrNull { isActiveWarningOrder(it) }
            val code = warnOrder?.orderId?.takeLast(6)?.uppercase() ?: ""
            binding.tvEmergencyWarningTitle.text = if (code.isNotBlank()) "Đơn #$code đang bị tài xế cảnh báo mất đơn!" else "Đơn hàng của bạn đang bị tài xế cảnh báo mất đơn!"
        } else {
            binding.layoutEmergencyWarningBanner.visibility = View.GONE
        }
    }

    private fun applyFilter() {
        val now = System.currentTimeMillis()
        val filtered = when (currentFilterTab) {
            "WARNING" -> allOrders.filter { isActiveWarningOrder(it) }
            "ACTIVE" -> allOrders.filter {
                val st = it.effectiveStatus.uppercase()
                st == Order.Status.DELIVERING ||
                st == Order.Status.ARRIVED_AT_CUSTOMER ||
                st == Order.Status.ARRIVED_AT_SHOP ||
                st == Order.Status.ASSIGNED
            }
            "CANCELLED" -> allOrders.filter { isBombedOrCancelledOrder(it) }
            "UNPAID" -> allOrders.filter {
                val st = it.effectiveStatus.uppercase()
                val isAwaiting = st == Order.Status.AWAITING_PAYMENT ||
                        (st == Order.Status.PENDING && it.paymentMethod != Order.PaymentMethod.COD && it.totalPayment == 0L)
                val isNotExpired = (now - it.createdAt) <= 30 * 60 * 1000L
                isAwaiting && isNotExpired
            }
            "CONFIRMING" -> allOrders.filter {
                val st = it.effectiveStatus.uppercase()
                st == Order.Status.PENDING || st == Order.Status.PREPARING || st == Order.Status.READY
            }
            "COMPLETED" -> allOrders.filter { it.effectiveStatus.uppercase() == Order.Status.COMPLETED }
            else -> allOrders
        }

        adapter.submitList(filtered)
        binding.layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}
