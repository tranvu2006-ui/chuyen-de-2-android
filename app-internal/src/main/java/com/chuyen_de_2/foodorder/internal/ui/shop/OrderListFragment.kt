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
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.FragmentOrderListBinding
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint

/**
 * OrderListFragment — Kênh Quản Lý Đơn Hàng Dành Cho Quán (Quán/5.html)
 * Quy trình 3 bước chuẩn ShopeeFood:
 * 1. Quán xác nhận đơn của khách ➔ Bếp bắt đầu nấu (Tài xế chưa nhận đơn)
 * 2. Nấu xong ➔ Bấm xác nhận đã nấu xong (Phát đơn cho tài xế)
 * 3. Tài xế tới quầy ➔ Bấm xác nhận giao cho tài xế ➔ Đang giao
 */
@AndroidEntryPoint
class OrderListFragment : Fragment() {

    private var _binding: FragmentOrderListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShopViewModel by viewModels()
    private lateinit var adapter: OrderAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupTabs()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupUI() {
        // Toggle Switch Đang Bán / Tạm Đóng (Quán/5.html: toggleShopOpen)
        binding.switchShopStatus.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tvShopStatusTxt.text = "Đang Bán"
                binding.tvShopStatusTxt.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_green))
                Toast.makeText(requireContext(), "🟢 Quán đã mở cửa nhận đơn hàng mới!", Toast.LENGTH_SHORT).show()
            } else {
                binding.tvShopStatusTxt.text = "Tạm Đóng"
                binding.tvShopStatusTxt.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                Toast.makeText(requireContext(), "⚪ Quán tạm đóng cửa ngưng nhận đơn mới.", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                // BƯỚC 3: XÁC NHẬN GIAO CHO TÀI XẾ
                viewModel.handoverToDriver(order.orderId)
                Toast.makeText(
                    requireContext(),
                    "✓ ĐÃ XÁC NHẬN GIAO TÚI MÓN CHO TÀI XẾ!\nTài xế bắt đầu di chuyển giao tới khách hàng.",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                showOrderDetailDialog(order)
            }
        }
    }

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
        tvInfo.text = "Mã đơn: #ORD_OISHI_${order.orderId.takeLast(6).uppercase()}\nKhách hàng: ${order.customerName.ifBlank { "Trần Thị Mai" }}\nĐịa chỉ: ${order.address.ifBlank { "88 Nguyễn Đình Chiểu, Q.1" }}\nTài xế giao: ${order.shipperName.ifBlank { "Nguyễn Văn Hùng (TX-09)" }}\nTổng bill: ${total.toVndCurrency()}\nThời gian báo sự cố: Hệ thống ghi nhận 3 cuộc gọi nhỡ"

        val dismissAction = View.OnClickListener { dialog.dismiss() }
        btnClose.setOnClickListener(dismissAction)
        btnDismiss.setOnClickListener(dismissAction)

        dialog.show()
    }

    private fun observeViewModel() {
        // Danh sách đơn hàng theo tab
        viewModel.filteredOrders.observe(viewLifecycleOwner) { orders ->
            adapter.submitList(orders)
            binding.layoutEmptyOrders.isVisible = orders.isEmpty()
            binding.rvOrders.isVisible = orders.isNotEmpty()
        }

        // Số lượng đếm trên các tabs (Quán/5.html: countTabNew, countTabCooking, countTabDelivering)
        viewModel.countNew.observe(viewLifecycleOwner) { count ->
            binding.tvCountNew.text = "($count)"
        }
        viewModel.countCooking.observe(viewLifecycleOwner) { count ->
            binding.tvCountCooking.text = "($count)"
        }
        viewModel.countDelivering.observe(viewLifecycleOwner) { count ->
            binding.tvCountDelivering.text = "($count)"
        }

        // Doanh thu và đơn hoàn tất hôm nay (Quán/5.html: stats-grid)
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

        // Chuông báo đơn mới (Quán/5.html)
        viewModel.newOrderAlert.observe(viewLifecycleOwner) { hasNewOrder ->
            if (hasNewOrder) {
                playAlertSound()
                Toast.makeText(requireContext(), "🔔 Có đơn hàng mới từ khách! Vui lòng kiểm tra và xác nhận.", Toast.LENGTH_LONG).show()
                viewModel.dismissAlert()
            }
        }
    }

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
