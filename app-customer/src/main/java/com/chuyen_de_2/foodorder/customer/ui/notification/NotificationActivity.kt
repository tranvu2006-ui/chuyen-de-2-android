package com.chuyen_de_2.foodorder.customer.ui.notification

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityNotificationBinding
import com.chuyen_de_2.foodorder.customer.ui.order.OrderHistoryActivity
import com.chuyen_de_2.foodorder.customer.ui.tracking.TrackingActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private lateinit var adapter: NotificationAdapter
    private var allNotifications = listOf<NotificationItem>()
    private var allOrders: List<Order> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFilterChips()
        loadNotifications()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter { item ->
            when {
                item.orderId != null -> {
                    val activeStatuses = listOf("PENDING", "PREPARING", "READY", "PICKED_UP", "DELIVERING")
                    val matchingOrder = allOrders.find { it.orderId == item.orderId }
                    if (matchingOrder != null && matchingOrder.status in activeStatuses) {
                        val intent = Intent(this, TrackingActivity::class.java).apply {
                            putExtra("orderId", item.orderId)
                        }
                        startActivity(intent)
                    } else {
                        startActivity(Intent(this, OrderHistoryActivity::class.java))
                    }
                }
                else -> {
                    // Thông báo ưu đãi / hệ thống
                }
            }
        }

        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            adapter = this@NotificationActivity.adapter
        }
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipOrders) -> filterList(NotificationType.ORDER)
                checkedIds.contains(R.id.chipPromos) -> filterList(NotificationType.PROMO)
                else -> filterList(null)
            }
        }
    }

    private fun filterList(type: NotificationType?) {
        val filtered = if (type == null) {
            allNotifications
        } else {
            allNotifications.filter { it.type == type }
        }
        adapter.submitList(filtered)
        updateEmptyState(filtered.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.llEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvNotifications.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun loadNotifications() {
        val currentUid = firebaseAuth.currentUser?.uid ?: ""
        if (currentUid.isBlank()) {
            updateEmptyState(true)
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            firebaseRepository.observeOrdersByCustomer(currentUid).collectLatest { orders ->
                binding.progressBar.visibility = View.GONE
                allOrders = orders

                val items = mutableListOf<NotificationItem>()

                // 1. Tạo thông báo động từ trạng thái các đơn hàng
                orders.sortedByDescending { it.timestamp }.forEach { order ->
                    val notif = createNotificationFromOrder(order)
                    items.add(notif)
                }

                // 2. Thêm thông báo ưu đãi / chào mừng
                items.add(
                    NotificationItem(
                        id = "promo_welcome",
                        type = NotificationType.PROMO,
                        title = "Ưu đãi chào bạn mới Oishi Food!",
                        body = "Giảm ngay 20.000đ cho đơn hàng đầu tiên với mã OISHI20. Đặt món ngon ngay hôm nay!",
                        timestamp = System.currentTimeMillis() - 3600_000 * 4,
                        isUnread = false,
                        iconRes = R.drawable.ic_tag,
                        iconColor = 0xFFEE4D2D.toInt(),
                        iconBgColor = 0xFFFFF1EE.toInt()
                    )
                )

                items.add(
                    NotificationItem(
                        id = "sys_trust_score",
                        type = NotificationType.SYSTEM,
                        title = "Điểm uy tín tài khoản của bạn",
                        body = "Hoàn tất các đơn hàng thành công giúp tích lũy điểm uy tín và mở khóa thanh toán tiền mặt COD không giới hạn.",
                        timestamp = System.currentTimeMillis() - 86400_000,
                        isUnread = false,
                        iconRes = R.drawable.ic_shield,
                        iconColor = 0xFF10B981.toInt(),
                        iconBgColor = 0xFFECFDF5.toInt()
                    )
                )

                allNotifications = items

                // Áp dụng bộ lọc hiện tại
                val checkedIds = binding.chipGroupFilter.checkedChipIds
                when {
                    checkedIds.contains(R.id.chipOrders) -> filterList(NotificationType.ORDER)
                    checkedIds.contains(R.id.chipPromos) -> filterList(NotificationType.PROMO)
                    else -> filterList(null)
                }
            }
        }
    }

    private fun createNotificationFromOrder(order: Order): NotificationItem {
        val shortId = if (order.orderId.length > 6) order.orderId.takeLast(6) else order.orderId
        val shopName = if (order.shopName.isNotBlank()) order.shopName else "Quán ăn"

        return when (order.status) {
            "PENDING" -> NotificationItem(
                id = "order_${order.orderId}_pending",
                type = NotificationType.ORDER,
                title = "Đơn hàng #$shortId đang chờ duyệt",
                body = "Đơn của bạn tại $shopName đã gửi đi và đang chờ quán tiếp nhận.",
                timestamp = order.timestamp,
                isUnread = true,
                orderId = order.orderId,
                iconRes = R.drawable.ic_clock,
                iconColor = 0xFFF59E0B.toInt()
            )
            "PREPARING" -> NotificationItem(
                id = "order_${order.orderId}_prep",
                type = NotificationType.ORDER,
                title = "Quán đang chuẩn bị món",
                body = "$shopName đang làm món cho đơn hàng #$shortId của bạn.",
                timestamp = order.timestamp,
                isUnread = true,
                orderId = order.orderId,
                iconRes = R.drawable.ic_nav_menu,
                iconColor = 0xFFEE4D2D.toInt()
            )
            "READY" -> NotificationItem(
                id = "order_${order.orderId}_ready",
                type = NotificationType.ORDER,
                title = "Món ăn đã sẵn sàng",
                body = "Món của bạn tại $shopName đã nấu xong, đang chờ tài xế nhận đơn.",
                timestamp = order.timestamp,
                isUnread = true,
                orderId = order.orderId,
                iconRes = R.drawable.ic_package,
                iconColor = 0xFF3B82F6.toInt()
            )
            "PICKED_UP", "DELIVERING" -> NotificationItem(
                id = "order_${order.orderId}_delivering",
                type = NotificationType.ORDER,
                title = "Tài xế đang giao hàng",
                body = "Đơn hàng #$shortId đang được tài xế vận chuyển đến địa chỉ của bạn.",
                timestamp = order.timestamp,
                isUnread = true,
                orderId = order.orderId,
                iconRes = R.drawable.ic_bike,
                iconColor = 0xFF0284C7.toInt()
            )
            "COMPLETED" -> NotificationItem(
                id = "order_${order.orderId}_completed",
                type = NotificationType.ORDER,
                title = "Giao hàng thành công!",
                body = "Đơn hàng #$shortId tại $shopName đã hoàn tất. Cảm ơn bạn đã lựa chọn Oishi Food!",
                timestamp = order.timestamp,
                isUnread = false,
                orderId = order.orderId,
                iconRes = R.drawable.ic_check_circle_green,
                iconColor = 0xFF10B981.toInt()
            )
            "CANCELLED" -> NotificationItem(
                id = "order_${order.orderId}_cancelled",
                type = NotificationType.ORDER,
                title = "Đơn hàng đã hủy",
                body = "Đơn hàng #$shortId đã bị hủy.",
                timestamp = order.timestamp,
                isUnread = false,
                orderId = order.orderId,
                iconRes = R.drawable.ic_close,
                iconColor = 0xFFEF4444.toInt()
            )
            else -> NotificationItem(
                id = "order_${order.orderId}_general",
                type = NotificationType.ORDER,
                title = "Cập nhật đơn hàng #$shortId",
                body = "Đơn hàng tại $shopName có thay đổi trạng thái: ${order.status}",
                timestamp = order.timestamp,
                isUnread = false,
                orderId = order.orderId,
                iconRes = R.drawable.ic_receipt,
                iconColor = 0xFF64748B.toInt()
            )
        }
    }
}
