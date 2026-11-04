package com.chuyen_de_2.foodorder.customer.ui.order

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.databinding.ActivityOrderSuccessBinding
import com.chuyen_de_2.foodorder.customer.ui.home.HomeActivity
import com.chuyen_de_2.foodorder.customer.ui.tracking.ChatStoreBottomSheetDialogFragment
import com.chuyen_de_2.foodorder.customer.ui.tracking.TrackingActivity

class OrderSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderSuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
        InsetUtils.applyToolbarInsets(binding.toolbar)
        InsetUtils.applyNavigationBarPadding(binding.bottomBar)

        val orderId = intent.getStringExtra(Constants.EXTRA_ORDER_ID) ?: ""
        val shopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME) ?: "Quán Ăn"
        val address = intent.getStringExtra("extra_address") ?: ""
        val totalAmount = intent.getLongExtra("extra_amount", 0L)
        val subtotal = intent.getLongExtra("extra_subtotal", 0L)
        val shippingFee = intent.getLongExtra("extra_shipping_fee", 0L)
        val discount = intent.getLongExtra("extra_discount", 0L)
        val paymentMethod = intent.getStringExtra("extra_payment_method") ?: "Tiền mặt khi nhận hàng (COD)"
        val paymentMode = intent.getStringExtra("extra_payment_mode") ?: com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.FULL
        val deposit = intent.getLongExtra("extra_deposit", 0L)
        val remain = intent.getLongExtra("extra_remain", 0L)
        val totalOrder = intent.getLongExtra("extra_total", totalAmount)

        setupToolbar()
        bindOrderData(orderId, shopName, address, subtotal, shippingFee, discount, totalAmount, paymentMethod, paymentMode, deposit, remain, totalOrder)
        setupActions(orderId, shopName)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            navigateHome()
        }
    }

    private fun bindOrderData(
        orderId: String,
        shopName: String,
        address: String,
        subtotal: Long,
        shippingFee: Long,
        discount: Long,
        totalAmount: Long,
        paymentMethod: String,
        paymentMode: String,
        deposit: Long,
        remain: Long,
        totalOrder: Long
    ) {
        val shortCode = if (orderId.isNotBlank()) orderId.takeLast(6).uppercase() else ""
        val fullOrderCode = if (shortCode.isNotBlank()) "#ORD_OISHI_$shortCode" else ""
        binding.tvOrderId.text = if (fullOrderCode.isNotBlank()) "🏷️ Mã đơn: $fullOrderCode • Quán: $shopName" else "🏷️ Quán: $shopName"
        binding.tvAddress.text = "📍 Địa chỉ: $address"

        binding.tvSubtotal.text = subtotal.toVndCurrency()
        binding.tvShippingFee.text = shippingFee.toVndCurrency()
        binding.tvPaymentMethod.text = paymentMethod

        when (paymentMode) {
            com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.DEPOSIT -> {
                binding.tvHeadlineTitle.text = "ĐÃ CỌC THÀNH CÔNG!"
                binding.tvHeadlineSubtitle.text = "Quán đã nhận ${deposit.toVndCurrency()} tiền cọc giữ đơn.\nĐơn hàng đang chờ xác nhận của quán trước khi làm món."
                binding.tvBadgeTitle.text = "⚡ ĐÃ XÁC NHẬN CỌC 20%"
                binding.tvBadgeDesc.text = "Đã thanh toán cọc 20%. Số tiền còn lại ${remain.toVndCurrency()} bạn trả cho tài xế shipper khi nhận hàng."
                binding.layoutDepositBreakdown.visibility = View.VISIBLE
                binding.lblPaidDeposit.text = "Tiền cọc đã trả (20%):"
                binding.tvPaidDeposit.text = deposit.toVndCurrency()
                binding.lblRemainAmount.text = "Còn lại thu khi giao hàng:"
                binding.tvRemainAmount.text = remain.toVndCurrency()
                binding.tvTotalAmount.text = totalOrder.toVndCurrency()
            }
            com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.COD -> {
                binding.tvHeadlineTitle.text = "ĐÃ ĐẶT ĐƠN COD THÀNH CÔNG!"
                binding.tvHeadlineSubtitle.text = "Đơn hàng COD đã gửi tới quán.\nĐang chờ xác nhận của quán trước khi làm món. Miễn 100% tiền cọc và trả trước."
                binding.tvBadgeTitle.text = "👑 ĐƠN COD UY TÍN CAO"
                binding.tvBadgeDesc.text = "Đơn hàng áp dụng COD trả sau 0đ. Shipper sẽ thu ${totalOrder.toVndCurrency()} khi giao món tận nơi."
                binding.layoutDepositBreakdown.visibility = View.VISIBLE
                binding.lblPaidDeposit.text = "Trả trước ngay:"
                binding.tvPaidDeposit.text = "0 đ"
                binding.lblRemainAmount.text = "Thu khi nhận món:"
                binding.tvRemainAmount.text = totalOrder.toVndCurrency()
                binding.tvTotalAmount.text = totalOrder.toVndCurrency()
            }
            else -> {
                binding.tvHeadlineTitle.text = "ĐÃ THANH TOÁN 100% THÀNH CÔNG!"
                binding.tvHeadlineSubtitle.text = "Quán đã nhận đủ 100% thanh toán đơn hàng.\nShipper sẽ giao đồ ăn tới bạn, không cần chuẩn bị tiền mặt."
                binding.tvBadgeTitle.text = "💰 ĐÃ THANH TOÁN ĐỦ 100%"
                binding.tvBadgeDesc.text = "Đơn hàng đã thanh toán trực tuyến đầy đủ. Khi nhận hàng chỉ cần kiểm tra món và nhận hàng."
                binding.layoutDepositBreakdown.visibility = View.GONE
                binding.tvTotalAmount.text = totalOrder.toVndCurrency()
            }
        }

        if (discount > 0) {
            binding.layoutDiscount.visibility = View.VISIBLE
            binding.tvDiscount.text = "-${discount.toVndCurrency()}"
        } else {
            binding.layoutDiscount.visibility = View.GONE
        }
    }

    private fun setupActions(orderId: String, shopName: String) {
        // Nút Theo dõi đơn hàng -> mở TrackingActivity
        binding.btnTrackOrder.setOnClickListener {
            val intent = Intent(this, TrackingActivity::class.java).apply {
                putExtra(Constants.EXTRA_ORDER_ID, orderId)
                putExtra(Constants.EXTRA_SHOP_NAME, shopName)
            }
            startActivity(intent)
            finish()
        }

        // Nút Nhắn tin cho quán -> mở BottomSheet chat trực tiếp
        binding.btnChatShop.setOnClickListener {
            val chatSheet = ChatStoreBottomSheetDialogFragment.newInstance(shopName, orderId)
            chatSheet.show(supportFragmentManager, "ChatStoreBottomSheet")
        }

        // Nút Về Trang Chủ
        binding.btnGoHome.setOnClickListener {
            navigateHome()
        }
    }

    private fun navigateHome() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}
