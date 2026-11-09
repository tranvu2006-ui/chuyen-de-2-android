package com.chuyen_de_2.foodorder.customer.ui.order

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.google.firebase.database.FirebaseDatabase
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
    private data class RefundBank(val name: String, val bin: String)

    private val refundBanks = listOf(
        RefundBank("MB Bank (Quân Đội)", "970422"),
        RefundBank("Vietcombank (Ngoại Thương)", "970436"),
        RefundBank("Techcombank (Kỹ Thương)", "970407"),
        RefundBank("VPBank (Việt Nam Thịnh Vượng)", "970432"),
        RefundBank("ACB (Á Châu)", "970416"),
        RefundBank("BIDV (Đầu Tư & Phát Triển)", "970418"),
        RefundBank("Agribank (Nông Nghiệp)", "970405"),
        RefundBank("TPBank (Tiên Phong)", "970423"),
        RefundBank("Sacombank (Sài Gòn Thương Tín)", "970403"),
        RefundBank("VIB (Quốc Tế)", "970441"),
        RefundBank("OCB (Phương Đông)", "970448")
    )

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
            onRefundDetailsClick = { order -> showRefundAccountForm(order) },
            onCallClick = { phone ->
                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, "Số điện thoại: $phone", Toast.LENGTH_SHORT).show()
                }
            }
        )

        binding.rvOrders.layoutManager = LinearLayoutManager(this)
        binding.rvOrders.adapter = adapter
    }

    private fun showRefundAccountForm(order: Order) {
        val customerId = firebaseAuth.currentUser?.uid
        if (customerId.isNullOrBlank() ||
            order.status != Order.Status.CANCELLED ||
            order.refundStatus != Order.RefundStatus.PENDING_INFO
        ) {
            Toast.makeText(this, "Đơn hàng không còn ở trạng thái chờ nhập thông tin hoàn tiền.", Toast.LENGTH_LONG).show()
            return
        }

        val padding = (20 * resources.displayMetrics.density).toInt()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }
        val bankSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@OrderHistoryActivity,
                android.R.layout.simple_spinner_dropdown_item,
                refundBanks.map { it.name }
            )
        }
        val accountNumber = EditText(this).apply {
            hint = "Số tài khoản nhận hoàn"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val accountHolder = EditText(this).apply {
            hint = "Tên chủ tài khoản"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
        }
        form.addView(TextView(this).apply { text = "Ngân hàng nhận tiền" })
        form.addView(bankSpinner)
        form.addView(accountNumber)
        form.addView(accountHolder)

        val detailsRef = FirebaseDatabase.getInstance()
            .getReference("refund_payment_details")
            .child(order.orderId)
            .child("customerProvided")
        detailsRef.get().addOnSuccessListener { snapshot ->
                snapshot.child("accountNumber").getValue(String::class.java)?.let { accountNumber.setText(it) }
                snapshot.child("accountHolder").getValue(String::class.java)?.let { accountHolder.setText(it) }
            val savedBin = snapshot.child("bankBin").getValue(String::class.java).orEmpty()
            refundBanks.indexOfFirst { it.bin == savedBin }.takeIf { it >= 0 }?.let(bankSpinner::setSelection)
        }

        AlertDialog.Builder(this)
            .setTitle("Thông tin nhận hoàn tiền")
            .setMessage("Nhập đúng thông tin tài khoản ngân hàng của bạn. Quán sẽ dùng thông tin này để tạo mã QR hoàn tiền.")
            .setView(form)
            .setNegativeButton("Để sau", null)
            .setPositiveButton("Lưu thông tin", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val number = accountNumber.text.toString().trim()
                        val holder = accountHolder.text.toString().trim().uppercase()
                        val bank = refundBanks.getOrNull(bankSpinner.selectedItemPosition)
                        if (number.length !in 6..24 || !number.all(Char::isDigit) || holder.isBlank() || bank == null) {
                            Toast.makeText(this, "Vui lòng nhập đúng số tài khoản, tên chủ tài khoản và ngân hàng.", Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        val values = mapOf(
                            "bankName" to bank.name,
                            "bankBin" to bank.bin,
                            "accountNumber" to number,
                            "accountHolder" to holder,
                            "updatedAt" to System.currentTimeMillis()
                        )
                        detailsRef.setValue(values)
                            .addOnSuccessListener {
                                FirebaseDatabase.getInstance()
                                    .getReference("orders")
                                    .child(order.orderId)
                                    .child("refundStatus")
                                    .setValue(Order.RefundStatus.PENDING_REFUND)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Đã lưu thông tin. Hoàn tiền đang chờ quán chuyển khoản.", Toast.LENGTH_LONG).show()
                                        dialog.dismiss()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(this, "Đã lưu thông tin nhưng chưa cập nhật được trạng thái. Hãy thử lưu lại.", Toast.LENGTH_LONG).show()
                                    }
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Không lưu được thông tin. Hãy thử lại hoặc kiểm tra quyền truy cập.", Toast.LENGTH_LONG).show()
                            }
                    }
                }
                dialog.show()
            }
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
        return order.hasIncidentWarning ||
                order.note.contains("cảnh báo", ignoreCase = true) ||
                order.note.contains("gọi", ignoreCase = true)
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
            val code = warnOrder?.orderId?.let {
                com.chuyen_de_2.foodorder.core.util.QRCodeUtils.getShortOrderCode(it)
            } ?: ""
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
