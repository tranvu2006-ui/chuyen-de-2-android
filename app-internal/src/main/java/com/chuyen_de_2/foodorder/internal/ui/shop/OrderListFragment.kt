package com.chuyen_de_2.foodorder.internal.ui.shop

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.FragmentOrderListBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tên chức năng: Kênh Quản Lý Đơn Hàng Dành Cho Quán (Merchant Order Kanban)
 * Cách hoạt động:
 *  - Lắng nghe Realtime luồng đơn hàng của Quán theo thời gian thực từ Firestore.
 *  - Vận hành quy trình 3 bước chuẩn thương mại điện tử:
 *      1. Đơn mới (PENDING) -> Quán bấm Xác nhận đơn -> Chuyển sang Bếp nấu (PREPARING).
 *      2. Bếp nấu (PREPARING) -> Nấu xong bấm Xác nhận đã nấu xong -> Chuyển sang Sẵn sàng (READY) để Tài xế nhận đơn.
 *      3. Tài xế đến quầy (READY / ASSIGNED) -> Bấm Xác nhận giao cho tài xế -> Chuyển sang Đang giao (DELIVERING).
 *      4. Lịch sử đơn (COMPLETED / CANCELLED).
 *  - Theo dõi nhanh Doanh thu hôm nay, Số đơn hoàn thành, và Bật/Tắt trạng thái mở bán của Quán.
 * Luồng hướng tới:
 *  - Cập nhật trực tiếp trạng thái đơn hàng trên Firestore và đồng bộ tới app Khách hàng và app Tài xế.
 *  - Bấm vào thông tin Quán -> Chuyển sang [StoreProfileFragment].
 */
@AndroidEntryPoint
class OrderListFragment : Fragment() {

    private var _binding: FragmentOrderListBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var authRepository: com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository

    private val viewModel: ShopViewModel by viewModels()
    private lateinit var adapter: OrderAdapter
    private var currentStoreId = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderListBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Tên chức năng: Khởi tạo View và kích hoạt các luồng dữ liệu
     * Cách hoạt động: Thiết lập giao diện, tab bar, RecyclerView, lắng nghe ViewModel và truy xuất storeId của user.
     * Luồng hướng tới: Chuẩn bị hiển thị danh sách đơn hàng.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupTabs()
        setupRecyclerView()
        observeViewModel()
        resolveCurrentStoreIdAndObserve()
    }

    /**
     * Tên chức năng: Xác định định danh Cửa hàng (Store ID Resolver)
     * Cách hoạt động: Lấy UID người dùng hiện tại, tra cứu bảng stores để tìm storeId quản lý, sau đó cấp phát cho ViewModel.
     * Luồng hướng tới: Lắng nghe snapshot đơn hàng của đúng cửa hàng đó.
     */
    private fun resolveCurrentStoreIdAndObserve() {
        val uid = auth.currentUser?.uid.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            currentStoreId = firebaseRepository.resolveStoreIdForUser(uid)
            viewModel.setStoreId(currentStoreId)
            observeStoreProfile()
        }
    }

    /**
     * Tên chức năng: Giám sát thông tin và trạng thái Quán theo thời gian thực
     * Cách hoạt động:
     *  - Lắng nghe sự thay đổi tên quán, địa chỉ và trạng thái (OPEN, CLOSED, LOCKED, PENDING).
     *  - Đồng bộ công tắc switch "Đang Bán / Tạm Đóng" lên giao diện.
     * Luồng hướng tới: Hiển thị đúng trạng thái mở cửa của quán trên màn hình khách hàng.
     */
    private fun observeStoreProfile() {
        val b = _binding ?: return
        val openProfile = {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, StoreProfileFragment())
                .addToBackStack(null)
                .commit()
        }
        b.btnEditStoreProfile.setOnClickListener { openProfile() }
        b.layoutShopInfo.setOnClickListener { openProfile() }

        b.btnLogoutChannel.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("🚪 Đăng Xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản quán ăn không?")
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

        viewLifecycleOwner.lifecycleScope.launch {
            firebaseRepository.observeStore(currentStoreId).collectLatest { store ->
                val currentB = _binding ?: return@collectLatest
                if (store != null) {
                    currentB.tvShopName.text = store.name.ifBlank { "Quán của bạn" }
                    currentB.tvShopAddress.text = if (store.address.isNotBlank()) "📍 ${store.address}" else "📍 Chưa cập nhật địa chỉ"

                    val st = store.storeStatus.uppercase()
                    currentB.switchShopStatus.setOnCheckedChangeListener(null)
                    when (st) {
                        com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.LOCKED -> {
                            currentB.switchShopStatus.isChecked = false
                            currentB.switchShopStatus.isEnabled = false
                            currentB.tvShopStatusTxt.text = "Bị Khóa 🔴"
                            currentB.tvShopStatusTxt.setTextColor(Color.parseColor("#EF4444"))
                        }
                        com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.PENDING -> {
                            currentB.switchShopStatus.isChecked = false
                            currentB.switchShopStatus.isEnabled = false
                            currentB.tvShopStatusTxt.text = "Chờ Duyệt ⏳"
                            currentB.tvShopStatusTxt.setTextColor(Color.parseColor("#F59E0B"))
                        }
                        com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.CLOSED -> {
                            currentB.switchShopStatus.isEnabled = true
                            currentB.switchShopStatus.isChecked = false
                            currentB.tvShopStatusTxt.text = "Tạm Đóng"
                            currentB.tvShopStatusTxt.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                        }
                        else -> {
                            currentB.switchShopStatus.isEnabled = true
                            currentB.switchShopStatus.isChecked = true
                            currentB.tvShopStatusTxt.text = "Đang Bán"
                            currentB.tvShopStatusTxt.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_green))
                        }
                    }
                    bindShopStatusSwitchListener()
                }
            }
        }
    }

    /**
     * Tên chức năng: Bật/Tắt trạng thái nhận đơn của Quán
     * Cách hoạt động: Khi người dùng gạt switch, cập nhật trạng thái `OPEN` hoặc `CLOSED` vào tài liệu Store trên Firestore.
     * Luồng hướng tới: Firebase cập nhật -> Màn hình Khách hàng cập nhật thẻ Quán đóng/mở cửa.
     */
    private fun bindShopStatusSwitchListener() {
        binding.switchShopStatus.setOnCheckedChangeListener { _, isChecked ->
            val newStatus = if (isChecked) {
                com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.OPEN
            } else {
                com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.CLOSED
            }
            if (isChecked) {
                binding.tvShopStatusTxt.text = "Đang Bán"
                binding.tvShopStatusTxt.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_green))
                Toast.makeText(requireContext(), "🟢 Quán đã mở cửa nhận đơn hàng mới!", Toast.LENGTH_SHORT).show()
            } else {
                binding.tvShopStatusTxt.text = "Tạm Đóng"
                binding.tvShopStatusTxt.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                Toast.makeText(requireContext(), "⚪ Quán tạm đóng cửa ngưng nhận đơn mới.", Toast.LENGTH_SHORT).show()
            }
            viewLifecycleOwner.lifecycleScope.launch {
                firebaseRepository.updateStoreStatus(currentStoreId, newStatus)
            }
        }
    }

    private fun setupUI() {
        bindShopStatusSwitchListener()
    }

    /**
     * Tên chức năng: Chuyển đổi tab Kanban trạng thái đơn hàng
     * Cách hoạt động: Bắt sự kiện Click vào các tab: Đơn mới, Bếp nấu, Đang giao, Lịch sử để lọc danh sách tương ứng.
     * Luồng hướng tới: Gọi [ShopViewModel.selectTab] để phát dữ liệu danh sách đơn theo tab đã chọn.
     */
    private fun setupTabs() {
        binding.tabNew.setOnClickListener {
            viewModel.selectTab(ShopViewModel.TabType.NEW)
            highlightTab(ShopViewModel.TabType.NEW)
        }
        binding.tabCooking.setOnClickListener {
            viewModel.selectTab(ShopViewModel.TabType.COOKING)
            highlightTab(ShopViewModel.TabType.COOKING)
        }
        binding.tabDelivering.setOnClickListener {
            viewModel.selectTab(ShopViewModel.TabType.DELIVERING)
            highlightTab(ShopViewModel.TabType.DELIVERING)
        }
        binding.tabHistory.setOnClickListener {
            viewModel.selectTab(ShopViewModel.TabType.HISTORY)
            highlightTab(ShopViewModel.TabType.HISTORY)
        }
    }

    /**
     * Tên chức năng: Làm nổi bật giao diện Tab đang chọn (Pill Highlight)
     * Cách hoạt động: Thay đổi background drawable và màu chữ trắng/xám tương ứng cho tab được kích hoạt.
     * Luồng hướng tới: Cập nhật visual feedback cho người dùng.
     */
    private fun highlightTab(tab: ShopViewModel.TabType) {
        val activeBg = R.drawable.bg_order_tab_active
        val idleBg = R.drawable.bg_order_tab_idle
        val colorWhite = ContextCompat.getColor(requireContext(), R.color.white)
        val colorMuted = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        // Reset all tabs
        binding.tabNew.setBackgroundResource(idleBg)
        binding.tvTabNewTitle.setTextColor(colorMuted)
        binding.tvCountNew.setTextColor(colorMuted)

        binding.tabCooking.setBackgroundResource(idleBg)
        binding.tvTabCookingTitle.setTextColor(colorMuted)
        binding.tvCountCooking.setTextColor(colorMuted)

        binding.tabDelivering.setBackgroundResource(idleBg)
        binding.tvTabDeliveringTitle.setTextColor(colorMuted)
        binding.tvCountDelivering.setTextColor(colorMuted)

        binding.tabHistory.setBackgroundResource(idleBg)
        binding.tvTabHistoryTitle.setTextColor(colorMuted)

        // Set active tab
        when (tab) {
            ShopViewModel.TabType.NEW -> {
                binding.tabNew.setBackgroundResource(activeBg)
                binding.tvTabNewTitle.setTextColor(colorWhite)
                binding.tvCountNew.setTextColor(colorWhite)
            }
            ShopViewModel.TabType.COOKING -> {
                binding.tabCooking.setBackgroundResource(activeBg)
                binding.tvTabCookingTitle.setTextColor(colorWhite)
                binding.tvCountCooking.setTextColor(colorWhite)
            }
            ShopViewModel.TabType.DELIVERING -> {
                binding.tabDelivering.setBackgroundResource(activeBg)
                binding.tvTabDeliveringTitle.setTextColor(colorWhite)
                binding.tvCountDelivering.setTextColor(colorWhite)
            }
            ShopViewModel.TabType.HISTORY -> {
                binding.tabHistory.setBackgroundResource(activeBg)
                binding.tvTabHistoryTitle.setTextColor(colorWhite)
            }
        }
    }

    /**
     * Tên chức năng: Khởi tạo danh sách RecyclerView đơn hàng và các Callback
     * Cách hoạt động: Gắn [OrderAdapter], gán các lambda xử lý tương tác (Xử lý đơn, Xem chi tiết, Nhắn tin khách, Gọi điện tài xế, Xem cảnh báo).
     * Luồng hướng tới: Hiển thị các thẻ đơn hàng [item_order.xml].
     */
    private fun setupRecyclerView() {
        adapter = OrderAdapter(
            onActionClick = { order -> handleOrderAction(order) },
            onDetailClick = { order -> showOrderDetailDialog(order) },
            onChatCustomerClick = { order ->
                Toast.makeText(requireContext(), "💬 Đang mở kênh tin nhắn với khách ${order.customerName}...", Toast.LENGTH_SHORT).show()
            },
            onCallDriverClick = { phone ->
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Không thể gọi số $phone", Toast.LENGTH_SHORT).show()
                }
            },
            onWarningDetailClick = { order -> showWarningDetailModal(order) }
        )

        binding.rvOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@OrderListFragment.adapter
        }
    }

    /**
     * Tên chức năng: Xử lý hành động nghiệp vụ theo vòng đời đơn hàng (Order State Machine)
     * Cách hoạt động:
     *  - PENDING -> Gọi [ShopViewModel.confirmCustomerOrder] chuyển sang PREPARING (Bếp nấu).
     *  - PREPARING -> Gọi [ShopViewModel.markCookingDone] chuyển sang READY (Nấu xong, phát đơn cho shipper).
     *  - READY / ASSIGNED -> Gọi [ShopViewModel.handoverToDriver] chuyển sang DELIVERING (Giao túi món cho tài xế).
     * Luồng hướng tới: Cập nhật Firebase -> Thông báo Realtime sang App Khách hàng & Tài xế.
     */
    private fun handleOrderAction(order: Order) {
        when (order.status) {
            Order.Status.PENDING -> {
                // BƯỚC 1: QUÁN XÁC NHẬN ĐƠN CỦA KHÁCH -> CHUYỂN SANG BẾP ĐANG NẤU
                viewModel.confirmCustomerOrder(order.orderId)
                Toast.makeText(
                    requireContext(),
                    "✓ Quán đã XÁC NHẬN ĐƠN CỦA KHÁCH thành công!\nBếp đang nấu món. Khi nấu xong, bấm 'Xác nhận đã nấu xong' để phát đơn cho tài xế.",
                    Toast.LENGTH_LONG
                ).show()
            }
            Order.Status.PREPARING -> {
                // BƯỚC 2: XÁC NHẬN ĐÃ NẤU XONG -> CHUYỂN SANG READY (TÀI XẾ NHẬN ĐƠN)
                viewModel.markCookingDone(order.orderId)
                Toast.makeText(
                    requireContext(),
                    "✓ Quán đã XÁC NHẬN ĐÃ NẤU XONG!\nMón ăn đã sẵn sàng. Khi tài xế đến quầy nhận túi món, bấm nút 'XÁC NHẬN GIAO CHO TÀI XẾ' để hoàn tất.",
                    Toast.LENGTH_LONG
                ).show()
            }
            Order.Status.READY -> {
                if (!order.shipperId.isNullOrBlank()) {
                    viewModel.handoverToDriver(order.orderId)
                    val driverName = order.shipperName.ifBlank { "tài xế" }
                    Toast.makeText(
                        requireContext(),
                        "✓ ĐÃ BÀN GIAO TÚI MÓN CHO $driverName!\nTài xế bắt đầu di chuyển giao tới khách hàng.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Món đã nấu xong. Đang chờ tài xế tự bấm nhận đơn trên ứng dụng tài xế!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            Order.Status.ASSIGNED, Order.Status.ARRIVED_AT_SHOP -> {
                // BƯỚC 3: XÁC NHẬN GIAO TÚI MÓN CHO TÀI XẾ ĐÃ NHẬN / ĐÃ ĐẾN QUÁN
                viewModel.handoverToDriver(order.orderId)
                val driverName = order.shipperName.ifBlank { "tài xế" }
                Toast.makeText(
                    requireContext(),
                    "✓ ĐÃ BÀN GIAO TÚI MÓN CHO $driverName!\nTài xế bắt đầu di chuyển giao tới khách hàng.",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                showOrderDetailDialog(order)
            }
        }
    }

    /**
     * Tên chức năng: Hiển thị Dialog chi tiết đơn hàng
     * Cách hoạt động: Bóc tách danh sách món ăn, số lượng, ghi chú, địa chỉ giao hàng và tổng tiền đơn hàng để hiển thị trực quan.
     * Luồng hướng tới: Hiển thị hộp thoại AlertDialog cho Quán đối soát.
     */
    private fun showOrderDetailDialog(order: Order) {
        val total = if (order.totalPayment > 0) order.totalPayment else order.total
        val dishes = if (order.items.isNotEmpty()) {
            order.items.values.joinToString("\n") { "• ${it.quantity}x ${it.name}" }
        } else {
            "• 1x Suất ăn tiêu chuẩn"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("📋 Đơn Hàng #${order.orderId.takeLast(6).uppercase()}")
            .setMessage("Khách hàng: ${order.customerName}\nĐịa chỉ: ${order.address}\n\nThực đơn:\n$dishes\n\nTổng thanh toán: ${total.toVndCurrency()}\nTrạng thái: ${order.status}")
            .setPositiveButton("Đóng", null)
            .show()
    }

    /**
     * Tên chức năng: Hiển thị thông báo sự cố giao hàng (Incident Modal)
     * Cách hoạt động: Mở Dialog cảnh báo khi phát hiện sự cố (ví dụ khách không nhận máy, tài xế gọi nhỡ).
     * Luồng hướng tới: Hỗ trợ chủ quán nắm bắt rủi ro đơn hàng đang giao.
     */
    private fun showWarningDetailModal(order: Order) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_store_warning_detail)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvInfo = dialog.findViewById<TextView>(R.id.tvOrderIncidentInfo)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseModal)
        val btnDismiss = dialog.findViewById<MaterialButton>(R.id.btnDismissModal)

        val total = if (order.totalPayment > 0) order.totalPayment else order.total
        val customer = order.customerName.ifBlank { "Khách hàng" }
        val address = order.address.ifBlank { "Chưa cập nhật địa chỉ" }
        val driver = order.shipperName.ifBlank { "Chưa có tài xế" }
        tvInfo.text = "Mã đơn: #ORD_OISHI_${order.orderId.takeLast(6).uppercase()}\nKhách hàng: $customer\nĐịa chỉ: $address\nTài xế giao: $driver\nTổng bill: ${total.toVndCurrency()}\nThời gian báo sự cố: Hệ thống ghi nhận 3 cuộc gọi nhỡ"

        val dismissAction = View.OnClickListener { dialog.dismiss() }
        btnClose.setOnClickListener(dismissAction)
        btnDismiss.setOnClickListener(dismissAction)

        dialog.show()
    }

    /**
     * Tên chức năng: Lắng nghe và cập nhật dữ liệu Realtime từ ViewModel
     * Cách hoạt động:
     *  - Cập nhật danh sách đơn theo tab.
     *  - Cập nhật số badge số lượng đơn trên từng tab.
     *  - Cập nhật tổng doanh thu và số đơn thành công hôm nay.
     *  - Phát chuông và thông báo khi có đơn hàng mới từ khách đặt.
     * Luồng hướng tới: Đồng bộ hiển thị giao diện tức thời.
     */
    private fun observeViewModel() {
        // Danh sách đơn hàng theo tab
        viewModel.filteredOrders.observe(viewLifecycleOwner) { orders ->
            adapter.submitList(orders)
            binding.layoutEmptyOrders.isVisible = orders.isEmpty()
            binding.rvOrders.isVisible = orders.isNotEmpty()
        }

        // Số lượng đếm trên các tabs
        viewModel.countNew.observe(viewLifecycleOwner) { count ->
            binding.tvCountNew.text = "($count)"
        }
        viewModel.countCooking.observe(viewLifecycleOwner) { count ->
            binding.tvCountCooking.text = "($count)"
        }
        viewModel.countDelivering.observe(viewLifecycleOwner) { count ->
            binding.tvCountDelivering.text = "($count)"
        }

        // Doanh thu và đơn hoàn tất hôm nay
        viewModel.todayRevenue.observe(viewLifecycleOwner) { revenue ->
            binding.tvTodayRevenue.text = revenue.toVndCurrency()
        }
        viewModel.todayCompletedCount.observe(viewLifecycleOwner) { count ->
            binding.tvCompletedOrdersCount.text = "$count đơn"
        }

        // Banner cảnh báo ở tab Đang Giao
        viewModel.hasDeliveringWarning.observe(viewLifecycleOwner) { hasWarning ->
            val isDeliveringTab = viewModel.currentTab.value == ShopViewModel.TabType.DELIVERING
            binding.bannerTabDeliveringWarning.isVisible = hasWarning && isDeliveringTab
        }

        viewModel.currentTab.observe(viewLifecycleOwner) { tab ->
            val hasWarning = viewModel.hasDeliveringWarning.value ?: false
            binding.bannerTabDeliveringWarning.isVisible = hasWarning && tab == ShopViewModel.TabType.DELIVERING
        }

        // Chuông báo đơn mới
        viewModel.newOrderAlert.observe(viewLifecycleOwner) { hasNewOrder ->
            if (hasNewOrder) {
                playAlertSound()
                Toast.makeText(requireContext(), "🔔 Có đơn hàng mới từ khách! Vui lòng kiểm tra và xác nhận.", Toast.LENGTH_LONG).show()
                viewModel.dismissAlert()
            }
        }
    }

    /**
     * Tên chức năng: Phát âm thanh chuông báo có đơn mới
     * Cách hoạt động: Sử dụng [RingtoneManager] để phát chuông Notification mặc định của hệ điều hành.
     * Luồng hướng tới: Cảnh báo âm thanh cho nhân viên quán.
     */
    private fun playAlertSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(requireContext(), notification)
            ringtone.play()
        } catch (e: Exception) {
            // Fallback silently
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
