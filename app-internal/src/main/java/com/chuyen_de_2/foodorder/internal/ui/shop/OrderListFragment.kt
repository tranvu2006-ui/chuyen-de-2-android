package com.chuyen_de_2.foodorder.internal.ui.shop

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.DialogStoreCancelOrderBinding
import com.chuyen_de_2.foodorder.internal.databinding.FragmentOrderListBinding
import com.google.android.gms.tasks.Tasks
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
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
    private var currentStoreName = ""

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
                    currentStoreName = store.name.trim()
                    currentB.tvShopName.text = currentStoreName.ifBlank { "Quán của bạn" }
                    currentB.tvShopAddress.text = if (store.address.isNotBlank()) "📍 ${store.address}" else "📍 Chưa cập nhật địa chỉ"

                    com.chuyen_de_2.foodorder.core.util.ImageLoadUtils.loadStoreImage(
                        imageView = currentB.ivShopHeaderLogo,
                        imageUrl = store.effectiveImageUrl,
                        placeholderResId = com.chuyen_de_2.foodorder.core.R.drawable.ic_store,
                        cornerDp = 10
                    )

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
                val chatSheet = ChatCustomerBottomSheetDialogFragment.newInstance(
                    customerName = order.customerName.ifBlank { "Khách hàng" },
                    orderId = order.orderId,
                    orderNote = order.note
                )
                chatSheet.show(childFragmentManager, "ChatCustomerBottomSheet")
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
        val isDepositOrder = order.paymentMode == Order.PaymentMode.DEPOSIT
        val isDepositPaid = isDepositOrder && (
            order.paymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true) ||
                order.isPaid || order.isDepositPaid
            )
        val remainingAfterDeposit = order.remainAmount.takeIf { it > 0L }
            ?: (total - order.depositAmount).coerceAtLeast(0L)
        val amountDue = if (isDepositPaid) remainingAfterDeposit else order.depositAmount
        val totalDetail = if (isDepositOrder) {
            val depositLabel = if (isDepositPaid) "Tiền cọc đã trả" else "Tiền cọc cần thanh toán"
            "Tổng tiền hóa đơn: ${total.toVndCurrency()}\n$depositLabel: ${order.depositAmount.toVndCurrency()}\nSố tiền cần thanh toán: ${amountDue.toVndCurrency()}"
        } else {
            "Tổng tiền hóa đơn: ${total.toVndCurrency()}\nSố tiền cần thanh toán: ${total.toVndCurrency()}"
        }
        val dishes = if (order.items.isNotEmpty()) {
            order.items.values.joinToString("\n") { "• ${it.quantity}x ${it.name}" }
        } else {
            "• 1x Suất ăn tiêu chuẩn"
        }
        val isPaidOnline = order.paymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true) ||
            order.isPaid || order.isDepositPaid
        val payDetail = when {
            isDepositOrder && isDepositPaid ->
                "✅ Đã nhận cọc ${order.depositAmount.toVndCurrency()}"
            isDepositOrder ->
                "⏳ Chưa nhận cọc ${order.depositAmount.toVndCurrency()} • Chờ khách thanh toán cọc"
            order.paymentMethod.equals(Order.PaymentMethod.MOMO, ignoreCase = true) ->
                if (isPaidOnline) "✅ Đã chuyển khoản Ví MoMo" else "⏳ Chờ khách chuyển khoản Ví MoMo"
            order.paymentMethod.equals(Order.PaymentMethod.BANKING, ignoreCase = true) ->
                "✅ Đã thanh toán Ngân hàng (VietQR)"
            else -> "💵 Thu tiền mặt khi nhận (COD)"
        }
        val refundDetail = when (order.refundStatus) {
            Order.RefundStatus.PENDING_INFO ->
                "\nHoàn tiền: CHỜ khách cung cấp thông tin nhận • ${order.refundAmount.toVndCurrency()}"
            Order.RefundStatus.PENDING_REFUND ->
                "\nHoàn tiền: CHỜ quán chuyển khoản • ${order.refundAmount.toVndCurrency()}\nNội dung chuyển khoản: ${refundTransferContent(order)}"
            Order.RefundStatus.REFUNDED -> "\nSePay đã ghi nhận hoàn tiền • ${order.refundAmount.toVndCurrency()}"
            Order.RefundStatus.NOT_REQUIRED -> "\nHoàn tiền: Không cần hoàn"
            else -> ""
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("📋 Đơn Hàng #${com.chuyen_de_2.foodorder.core.util.QRCodeUtils.getShortOrderCode(order.orderId)}")
            .setMessage("Khách hàng: ${order.customerName}\nĐịa chỉ: ${order.address}\n\nThực đơn:\n$dishes\n\n$totalDetail\nThanh toán: $payDetail\nTrạng thái: ${order.status}$refundDetail")
            .setNegativeButton("Đóng", null)

        val canCancelByStore = order.effectiveStoreId == currentStoreId && (
            order.status == Order.Status.PENDING ||
                order.status == Order.Status.PREPARING ||
                (order.status == Order.Status.READY && order.shipperId.isNullOrBlank() && order.driverId.isNullOrBlank())
            )
        when {
            canCancelByStore -> dialog.setPositiveButton("Hủy đơn") { _, _ ->
                promptStoreOrderCancellation(order)
            }
            order.status == Order.Status.CANCELLED &&
                order.refundStatus in setOf(Order.RefundStatus.PENDING_INFO, Order.RefundStatus.PENDING_REFUND) &&
                order.effectiveStoreId == currentStoreId -> dialog.setPositiveButton(
                    "Kiểm tra / tạo mã QR hoàn tiền"
                ) { _, _ ->
                showRefundTransferDetails(order)
            }
            order.paymentMethod.equals(Order.PaymentMethod.MOMO, ignoreCase = true) &&
            !isPaidOnline &&
            order.status == Order.Status.AWAITING_PAYMENT -> dialog.setPositiveButton("Xác nhận đã nhận tiền") { _, _ ->
                confirmMomoPayment(order)
            }
        }
        dialog.show()
    }

    private fun promptStoreOrderCancellation(order: Order) {
        val dialogBinding = DialogStoreCancelOrderBinding.inflate(layoutInflater)
        val reasons = resources.getStringArray(R.array.store_cancel_reasons)
        val isPaid = order.paymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true) ||
            order.isPaid || order.isDepositPaid
        val shortCode = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.getShortOrderCode(order.orderId)
        dialogBinding.tvCancelOrderCode.text = getString(R.string.store_cancel_order_code, shortCode)
        dialogBinding.tvCancelPaymentNotice.text = if (isPaid) {
            getString(R.string.store_cancel_paid_notice, getRefundAmount(order).toVndCurrency())
        } else {
            getString(R.string.store_cancel_unpaid_notice)
        }
        if (!isPaid) {
            dialogBinding.tvCancelPaymentNotice.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_secondary)
            )
        }

        dialogBinding.actvCancelReason.setAdapter(
            ArrayAdapter(requireContext(), R.layout.item_store_cancel_reason, reasons)
        )
        dialogBinding.actvCancelReason.setText(reasons.first(), false)
        dialogBinding.actvCancelReason.setOnItemClickListener { _, _, position, _ ->
            val isCustomReason = position == reasons.lastIndex
            dialogBinding.tilCustomCancelReason.visibility = if (isCustomReason) View.VISIBLE else View.GONE
            dialogBinding.tilCancelReason.error = null
            if (isCustomReason) dialogBinding.etCustomCancelReason.requestFocus()
        }
        dialogBinding.etCustomCancelReason.doAfterTextChanged {
            dialogBinding.tilCustomCancelReason.error = null
        }

        val confirmation = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelStoreCancellation.setOnClickListener { confirmation.dismiss() }
        dialogBinding.btnConfirmStoreCancellation.setOnClickListener {
            val selectedReason = dialogBinding.actvCancelReason.text?.toString()?.trim().orEmpty()
            val reasonIndex = reasons.indexOf(selectedReason)
            if (reasonIndex <= 0) {
                dialogBinding.tilCancelReason.error = getString(R.string.store_cancel_reason_required)
                return@setOnClickListener
            }
            val reason = if (reasonIndex == reasons.lastIndex) {
                val customReason = dialogBinding.etCustomCancelReason.text?.toString()?.trim().orEmpty()
                if (customReason.isEmpty()) {
                    dialogBinding.tilCustomCancelReason.error =
                        getString(R.string.store_cancel_custom_reason_required)
                    return@setOnClickListener
                }
                customReason
            } else {
                selectedReason
            }
            confirmation.dismiss()
            viewModel.cancelOrderByStore(order.orderId, currentStoreId, reason) { success, message ->
                if (isAdded) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    val refundAmount = getRefundAmount(order)
                    if (success && refundAmount > 0) {
                        showRefundTransferDetails(
                            order.copy(
                                status = Order.Status.CANCELLED,
                                orderStatus = Order.Status.CANCELLED,
                                refundStatus = Order.RefundStatus.PENDING_INFO,
                                refundAmount = refundAmount,
                                refundReference = order.orderId,
                                refundReason = reason
                            )
                        )
                    }
                }
            }
        }
        confirmation.setOnShowListener {
            confirmation.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val width = (resources.displayMetrics.widthPixels -
                (32 * resources.displayMetrics.density).toInt()).coerceAtLeast(0)
            confirmation.window?.apply {
                setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }
        }
        confirmation.show()
    }

    /**
     * Tên chức năng: Lấy thông tin tài khoản ngân hàng hoàn tiền của Khách
     * Cách hoạt động:
     *   1. Kiểm tra đơn có trạng thái PENDING_REFUND (khách đã cung cấp STK nhận hoàn)
     *   2. Đọc node Firebase: refund_payment_details/{orderId}/customerProvided
     *   3. Nếu đủ thông tin → gọi showRefundTransferQr() để tạo mã QR chuyển tiền hoàn
     * Luồng hướng tới: Quán ăn → nhấn "Hoàn tiền" → quét QR → chuyển khoản → SePay ghi nhận
     */
    private fun showRefundTransferDetails(order: Order) {
        val database = FirebaseDatabase.getInstance()
        val orderRef = database.getReference("orders").child(order.orderId)
        val detailsRef = database.getReference("refund_payment_details")
            .child(order.orderId)
            .child("customerProvided")
        Tasks.whenAllSuccess<DataSnapshot>(orderRef.get(), detailsRef.get())
            .addOnSuccessListener results@{ snapshots ->
                if (!isAdded) return@results
                val orderSnapshot = snapshots[0]
                val customerDetails = snapshots[1]
                val latestOrder = orderSnapshot.getValue(Order::class.java)
                if (latestOrder == null ||
                    latestOrder.status != Order.Status.CANCELLED ||
                    latestOrder.effectiveStoreId != currentStoreId
                ) {
                    Toast.makeText(requireContext(), "Không tải được đơn hàng để kiểm tra hoàn tiền.", Toast.LENGTH_LONG).show()
                    return@results
                }

                val bankName = customerDetails.child("bankName").getValue(String::class.java).orEmpty()
                val bankBin = customerDetails.child("bankBin").getValue(String::class.java).orEmpty()
                val accountNumber = customerDetails.child("accountNumber").getValue(String::class.java).orEmpty()
                val accountHolder = customerDetails.child("accountHolder").getValue(String::class.java).orEmpty()
                if (bankName.isBlank() || bankBin.isBlank() || accountNumber.isBlank() || accountHolder.isBlank()) {
                    val waitingForCustomer = latestOrder.refundStatus == Order.RefundStatus.PENDING_INFO
                    AlertDialog.Builder(requireContext())
                        .setTitle(if (waitingForCustomer) "Chờ khách cung cấp thông tin" else "Chưa tải đủ thông tin nhận hoàn")
                        .setMessage(
                            if (waitingForCustomer) {
                                "Đơn hàng đã hủy. Khách cần nhập tài khoản nhận hoàn trước khi quán tạo mã QR chuyển tiền."
                            } else {
                                "Khách đã gửi thông tin nhưng dữ liệu chưa đầy đủ. Hãy kiểm tra lại sau."
                            }
                        )
                        .setPositiveButton("Đóng", null)
                        .show()
                    return@results
                }

                val orderForRefund = latestOrder.copy(refundStatus = Order.RefundStatus.PENDING_REFUND)
                if (latestOrder.refundStatus != Order.RefundStatus.PENDING_REFUND) {
                    orderRef.child("refundStatus").setValue(Order.RefundStatus.PENDING_REFUND)
                        .addOnFailureListener {
                            if (isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    "Không đồng bộ được trạng thái hoàn tiền; hãy kiểm tra kết nối sau khi chuyển khoản.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                }
                showRefundTransferQr(orderForRefund, bankName, bankBin, accountNumber, accountHolder)
            }
            .addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Không tải được trạng thái hoặc thông tin nhận hoàn. Hãy kiểm tra kết nối hoặc quyền truy cập.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    /**
     * Tên chức năng: Hiển thị mã QR VietQR để hoàn tiền cho Khách
     * Cách hoạt động:
     *   1. Tạo nội dung chuyển khoản hoàn tiền (refundTransferContent)
     *   2. Ghép URL VietQR từ BIN ngân hàng + STK của Khách
     *   3. Hiển thị Dialog có ảnh QR + thông tin chi tiết
     *   4. Dùng Glide tải ảnh QR từ api.vietqr.io
     * Luồng hướng tới: Quán quét QR → chuyển khoản → SePay ghi nhận vào refund_transactions
     */
    private fun showRefundTransferQr(
        order: Order,
        bankName: String,
        bankBin: String,
        accountNumber: String,
        accountHolder: String,
    ) {
        // Kiểm tra số tiền hoàn hợp lệ (> 0) trước khi tạo QR
        if (order.refundAmount <= 0L) {
            Toast.makeText(requireContext(), "Số tiền hoàn không hợp lệ nên không thể tạo mã QR.", Toast.LENGTH_LONG).show()
            return
        }
        // Lấy tổng hóa đơn thực tế (ưu tiên totalPayment, fallback total)
        val invoiceTotal = order.totalPayment.takeIf { it > 0 } ?: order.total
        // Tạo nội dung chuyển khoản hoàn tiền (không dấu, dạng: "OishiFood HT OI... huy don")
        val memo = refundTransferContent(order)
        // Ghép URL VietQR: api.vietqr.io/image/{BIN}-{STK}-{template}.jpg?amount=...&addInfo=...&accountName=...
        // - BIN: mã định danh ngân hàng (6 chữ số) của tài khoản KHÁCH nhận hoàn
        // - accountNumber: STK của Khách (do khách nhập trên app-customer)
        // - amount: số tiền hoàn (VNĐ)
        // - addInfo: nội dung CK để SePay nhận diện đây là giao dịch hoàn tiền
        val qrUrl = "https://api.vietqr.io/image/$bankBin-$accountNumber-qLrkX2P.jpg" +
            "?amount=${order.refundAmount}&addInfo=${Uri.encode(memo)}&accountName=${Uri.encode(accountHolder)}"
        val details = buildString {
            append("Tên khách hàng: ${order.customerName.ifBlank { "Chưa có dữ liệu" }}")
            append("\nSố tài khoản nhận: $accountNumber")
            append("\nNgân hàng nhận: $bankName")
            append("\nSố tiền hoàn: ${order.refundAmount.toVndCurrency()}")
            append("\nTổng hóa đơn: ${invoiceTotal.toVndCurrency()}")
            append("\nLý do hoàn: ${order.refundReason.ifBlank { order.cancelReason.ifBlank { "Không có lý do" } }}")
            append("\nNội dung chuyển khoản: $memo")
        }
        val density = resources.displayMetrics.density
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((20 * density).toInt(), 0, (20 * density).toInt(), (12 * density).toInt())
        }
        val qrImage = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                (280 * density).toInt(),
                (280 * density).toInt()
            )
            contentDescription = "Mã QR chuyển khoản hoàn tiền"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        content.addView(qrImage)
        content.addView(TextView(requireContext()).apply {
            text = "$details\n\nQuét mã bằng ứng dụng ngân hàng và kiểm tra đúng người nhận, số tiền trước khi chuyển."
            setPadding(0, (8 * density).toInt(), 0, 0)
        })
        AlertDialog.Builder(requireContext())
            .setTitle("Quét mã để hoàn tiền")
            .setView(content)
            .setPositiveButton("Đóng", null)
            .show()
        Glide.with(this)
            .load(qrUrl)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Không tải được mã QR. Kiểm tra kết nối rồi thử lại.", Toast.LENGTH_LONG).show()
                    }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource,
                    isFirstResource: Boolean
                ): Boolean = false
            })
            .into(qrImage)
    }

    private fun refundTransferContent(order: Order): String {
        val orderCode = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.getShortOrderCode(order.orderId)
            .ifBlank { order.orderId.trim() }
        return "Hoàn tiền $orderCode"
    }

    private fun getRefundAmount(order: Order): Long {
        val paid = order.paymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true) ||
            order.isPaid || order.isDepositPaid
        if (!paid) return 0L
        return if (order.paymentMode == Order.PaymentMode.DEPOSIT) {
            order.depositAmount
        } else {
            order.totalPayment.takeIf { it > 0 } ?: order.total
        }
    }

    /**
     * Tên chức năng: Quán xác nhận thủ công đã nhận tiền MoMo
     * Cách hoạt động:
     *   - Dùng khi SePay không tự ghi nhận được (ví dụ: khách chuyển qua app MoMo tới MoMo)
     *   - Quán kiểm tra lịch sử giao dịch MoMo thực tế, rồi nhấn "Đã nhận tiền"
     *   - Ghi trực tiếp lên Firebase: paymentStatus=PAID, status=PENDING
     * Chú ý: Nếu SePay đã tự động ghi nhận (webhook hoạt động tốt), hàm này không cần thiết.
     */
    private fun confirmMomoPayment(order: Order) {
        // Kiểm tra bảo vệ: chỉ cho phép xác nhận nếu đúng quán, đúng phương thức MOMO,
        // chưa PAID và đang chờ thanh toán (AWAITING_PAYMENT)
        if (
            currentStoreId.isBlank() ||
            (order.storeId.ifBlank { order.shopId }) != currentStoreId ||
            !order.paymentMethod.equals(Order.PaymentMethod.MOMO, ignoreCase = true) ||
            order.paymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true) ||
            order.status != Order.Status.AWAITING_PAYMENT
        ) {
            Toast.makeText(requireContext(), "Đơn MoMo không còn ở trạng thái chờ xác nhận.", Toast.LENGTH_SHORT).show()
            return
        }

        // Hiển thị dialog xác nhận — yêu cầu quán kiểm tra thực tế trước khi bấm
        AlertDialog.Builder(requireContext())
            .setTitle("Xác nhận đã nhận thanh toán?")
            .setMessage("Chỉ xác nhận khi đã kiểm tra giao dịch MoMo thực tế và số tiền khớp với đơn hàng.")
            .setNegativeButton("Kiểm tra lại", null)
            .setPositiveButton("Đã nhận tiền") { _, _ ->
                // Chuẩn bị map cập nhật Firebase (tương tự SePay worker)
                val updates = mutableMapOf<String, Any>(
                    "paymentStatus" to Order.PaymentStatus.PAID, // Đánh dấu đã thanh toán
                    "paidAt" to System.currentTimeMillis(),       // Thời điểm xác nhận (Unix ms)
                    "status" to Order.Status.PENDING,            // Chuyển đơn vào hàng đợi
                    "orderStatus" to Order.Status.PENDING         // Đồng bộ field legacy
                )
                // Phân biệt chế độ DEPOSIT (cọc 20%) và thanh toán toàn bộ
                if (order.paymentMode == Order.PaymentMode.DEPOSIT) {
                    updates["isDepositPaid"] = true  // Đã cọc 20%
                } else {
                    updates["isPaid"] = true           // Đã thanh toán 100%
                }

                // Ghi cập nhật lên Firebase Realtime Database node orders/{orderId}
                FirebaseDatabase.getInstance()
                    .getReference("orders")
                    .child(order.orderId)         // Chọn đúng node đơn hàng
                    .updateChildren(updates)      // Partial update (chỉ ghi các field thay đổi)
                    .addOnSuccessListener {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Đã xác nhận thanh toán MoMo.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { error ->
                        // Lỗi mạng hoặc quyền Firebase → thông báo lý do thất bại
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                "Không thể xác nhận thanh toán: ${error.message ?: "lỗi Firebase"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
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
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvSubHeader = dialog.findViewById<TextView>(R.id.tvModalSubHeader)
        val tvReason = dialog.findViewById<TextView>(R.id.tvIncidentReason)
        val tvDriver = dialog.findViewById<TextView>(R.id.tvIncidentDriver)
        val tvInfo = dialog.findViewById<TextView>(R.id.tvOrderIncidentInfo)
        val tvDriverNotes = dialog.findViewById<TextView>(R.id.tvDriverNotes)
        val ivCallProof = dialog.findViewById<ImageView>(R.id.ivCallProofImage)
        val boxCallEmpty = dialog.findViewById<View>(R.id.boxCallProofEmpty)
        val ivMsgProof = dialog.findViewById<ImageView>(R.id.ivMsgProofImage)
        val boxMsgEmpty = dialog.findViewById<View>(R.id.boxMsgProofEmpty)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseModal)
        val btnDismiss = dialog.findViewById<MaterialButton>(R.id.btnDismissModal)

        val total = if (order.totalPayment > 0) order.totalPayment else order.total
        val customer = order.customerName.ifBlank { "Khách hàng" }
        val address = order.address.ifBlank { order.deliveryAddress.ifBlank { "Chưa cập nhật địa chỉ" } }
        val driver = order.shipperName.ifBlank { "Chưa có tài xế" }
        val driverPhone = order.shipperPhone.ifBlank { "" }
        val shortCode = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.getShortOrderCode(order.orderId)

        tvSubHeader.text = "#ORD_OISHI_$shortCode • Ghi nhận hệ thống"

        val incidentReason = order.riskReason.ifBlank {
            order.cancelReason.ifBlank {
                order.incidentWarningText.ifBlank {
                    "Khách không nghe máy sau thời gian đếm ngược (Cảnh báo mất đơn)"
                }
            }
        }
        tvReason.text = incidentReason
        tvDriver.text = if (driverPhone.isNotBlank()) "$driver ($driverPhone)" else driver

        tvInfo.text = "Khách hàng: $customer\nĐịa chỉ: $address\nTổng bill: ${total.toVndCurrency()}"

        val noteText = order.note.ifBlank {
            order.incidentWarningText.ifBlank {
                "Tài xế đã có mặt tại địa chỉ giao hàng và liên hệ nhiều cuộc gọi nhưng không có phản hồi."
            }
        }
        tvDriverNotes.text = "\"$noteText\""

        // Hiển thị ảnh chụp bằng chứng cuộc gọi từ tài xế
        val callProof = order.incidentCallProofImage
        if (callProof.isNotBlank()) {
            ivCallProof.visibility = View.VISIBLE
            boxCallEmpty.visibility = View.GONE
            com.chuyen_de_2.foodorder.core.util.ImageLoadUtils.loadStoreImage(
                ivCallProof,
                callProof,
                R.drawable.ic_camera
            )
        } else {
            ivCallProof.visibility = View.GONE
            boxCallEmpty.visibility = View.VISIBLE
        }

        // Hiển thị ảnh chụp bằng chứng nhắn tin từ tài xế
        val msgProof = order.incidentMessageProofImage
        if (msgProof.isNotBlank()) {
            ivMsgProof.visibility = View.VISIBLE
            boxMsgEmpty.visibility = View.GONE
            com.chuyen_de_2.foodorder.core.util.ImageLoadUtils.loadStoreImage(
                ivMsgProof,
                msgProof,
                R.drawable.ic_camera
            )
        } else {
            ivMsgProof.visibility = View.GONE
            boxMsgEmpty.visibility = View.VISIBLE
        }

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
