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
        val shopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME) ?: "Phở Hà Nội 36"
        val address = intent.getStringExtra("extra_address") ?: "TP. Hồ Chí Minh"
        val totalAmount = intent.getLongExtra("extra_amount", 130000L)
        val subtotal = intent.getLongExtra("extra_subtotal", 115000L)
        val shippingFee = intent.getLongExtra("extra_shipping_fee", 15000L)
        val discount = intent.getLongExtra("extra_discount", 0L)
        val paymentMethod = intent.getStringExtra("extra_payment_method") ?: "Tiền mặt khi nhận hàng (COD)"

        setupToolbar()
        bindOrderData(orderId, shopName, address, subtotal, shippingFee, discount, totalAmount, paymentMethod)
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
        paymentMethod: String
    ) {
        val displayOrderId = if (orderId.isNotBlank()) orderId.takeLast(6).uppercase() else "DH8888"
        binding.tvOrderId.text = "🏷️ Mã đơn: #$displayOrderId • Quán: $shopName"
        binding.tvAddress.text = "📍 Địa chỉ: $address"

        binding.tvSubtotal.text = subtotal.toVndCurrency()
        binding.tvShippingFee.text = shippingFee.toVndCurrency()
        binding.tvTotalAmount.text = totalAmount.toVndCurrency()
        binding.tvPaymentMethod.text = paymentMethod

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
