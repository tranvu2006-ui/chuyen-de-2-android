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

        InsetUtils.setLightStatusBar(this, true)
        InsetUtils.applyToolbarInsets(binding.layoutTopHeader)

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
                val intent = Intent(this, TrackingActivity::class.java).apply {
                    putExtra(Constants.EXTRA_ORDER_ID, order.orderId)
                }
                startActivity(intent)
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
                val modal = IncidentProofBottomSheetDialogFragment.newInstance(
                    orderId = order.orderId,
                    driverName = order.shipperName.ifBlank { "Trần Văn Nam (TX-19)" },
                    reason = if (order.status == Order.Status.BOOM) {
                        "Khách không nghe máy sau 15 phút đếm ngược (Bom hàng)"
                    } else {
                        "Tài xế cảnh báo đếm ngược 15 phút tại điểm giao"
                    },
                    notes = order.note.ifBlank { "Tài xế đã có mặt tại điểm giao nhưng không liên lạc được." }
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
            }
        )

        binding.rvOrders.layoutManager = LinearLayoutManager(this)
        binding.rvOrders.adapter = adapter
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

    /**
     * Cập nhật số đếm badge trên các tab và kiểm tra banner khẩn cấp
     */
    private fun updateTabBadgeCounts(orders: List<Order>) {
        val countWarning = orders.count { it.note.contains("cảnh báo", ignoreCase = true) || it.note.contains("gọi", ignoreCase = true) }
        val countActive = orders.count { it.status == Order.Status.DELIVERING }
        val countCancelled = orders.count { it.status == Order.Status.CANCELLED || it.status == Order.Status.BOOM }
        val countUnpaid = orders.count { it.status == Order.Status.PENDING && it.paymentMethod != Order.PaymentMethod.COD && it.totalPayment == 0L }
        val countConfirming = orders.count { (it.status == Order.Status.PENDING || it.status == Order.Status.PREPARING || it.status == Order.Status.READY) && !it.note.contains("cảnh báo", ignoreCase = true) }

        binding.tabWarning.text = "🚨 Bị cảnh báo ($countWarning)"
        binding.tabActive.text = "Đang đến ($countActive)"
        binding.tabCancelled.text = "🚫 Đã hủy ($countCancelled)"
        binding.tabUnpaid.text = "Chờ thanh toán ($countUnpaid)"
        binding.tabConfirming.text = "Chờ xác nhận ($countConfirming)"

        // Điều khiển Emergency Warning Banner
        if (countWarning > 0) {
            binding.layoutEmergencyWarningBanner.visibility = View.VISIBLE
            val warnOrder = orders.firstOrNull { it.note.contains("cảnh báo", ignoreCase = true) }
            val code = warnOrder?.orderId?.takeLast(6)?.uppercase() ?: "88291"
            binding.tvEmergencyWarningTitle.text = "Đơn #$code đang bị tài xế cảnh báo mất đơn!"
        } else {
            binding.layoutEmergencyWarningBanner.visibility = View.GONE
        }
    }

    private fun applyFilter() {
        val filtered = when (currentFilterTab) {
            "WARNING" -> allOrders.filter { it.note.contains("cảnh báo", ignoreCase = true) || it.note.contains("gọi", ignoreCase = true) }
            "ACTIVE" -> allOrders.filter { it.status == Order.Status.DELIVERING }
            "CANCELLED" -> allOrders.filter { it.status == Order.Status.CANCELLED || it.status == Order.Status.BOOM }
            "UNPAID" -> allOrders.filter { it.status == Order.Status.PENDING && it.paymentMethod != Order.PaymentMethod.COD && it.totalPayment == 0L }
            "CONFIRMING" -> allOrders.filter { it.status == Order.Status.PENDING || it.status == Order.Status.PREPARING || it.status == Order.Status.READY }
            "COMPLETED" -> allOrders.filter { it.status == Order.Status.COMPLETED }
            else -> allOrders
        }

        adapter.submitList(filtered)
        binding.layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}
