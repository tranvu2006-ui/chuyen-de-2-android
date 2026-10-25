package com.chuyen_de_2.foodorder.customer.ui.payment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.databinding.ActivityPaymentBinding
import com.chuyen_de_2.foodorder.customer.ui.home.HomeActivity
import com.chuyen_de_2.foodorder.customer.ui.order.OrderSuccessActivity
import com.google.firebase.database.FirebaseDatabase
import java.net.URLEncoder

/**
 * Màn hình thanh toán Chuyển khoản VietQR — Chuẩn ngân hàng số
 * Tự động đồng bộ số tài khoản và ngân hàng thực tế của từng Quán ăn
 */
class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private var countDownTimer: CountDownTimer? = null

    private var currentBankName = ""
    private var currentAccountNum = "0374022258"
    private var currentAccountHolder = "Hồ Thị Vân Anh"
    private var currentBankBin = "970422"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
        InsetUtils.applyToolbarInsets(binding.toolbar)
        InsetUtils.applyNavigationBarPadding(binding.bottomBar)

        val orderId = intent.getStringExtra(Constants.EXTRA_ORDER_ID) ?: ""
        val shopId = intent.getStringExtra(Constants.EXTRA_SHOP_ID) ?: ""
        val shopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME) ?: "Quán Ăn Oishi"
        val address = intent.getStringExtra("extra_address") ?: ""
        val amount = intent.getLongExtra("extra_amount", 0L)
        val subtotal = intent.getLongExtra("extra_subtotal", 0L)
        val shippingFee = intent.getLongExtra("extra_shipping_fee", 0L)
        val discount = intent.getLongExtra("extra_discount", 0L)
        val orderItems = intent.getStringExtra("extra_order_items").orEmpty()

        // 1. Nhận thông tin ngân hàng truyền qua Intent (nếu có)
        val passedBankName = intent.getStringExtra("extra_bank_name")
        val passedBankNum = intent.getStringExtra("extra_bank_number")
        val passedBankHolder = intent.getStringExtra("extra_bank_holder")
        val passedBankBin = intent.getStringExtra("extra_bank_bin")

        if (!passedBankNum.isNullOrBlank()) {
            currentAccountNum = BANK_ACCOUNT_NUMBER
            currentBankName = if (!passedBankName.isNullOrBlank()) passedBankName else "Ngân hàng thụ hưởng"
            currentAccountHolder = BANK_ACCOUNT_HOLDER
            currentBankBin = if (!passedBankBin.isNullOrBlank()) passedBankBin else "970422"
        }

        setupToolbar()
        renderPaymentUI(orderId, shopName, amount, subtotal, shippingFee, discount, orderItems)
        startCountdownTimer()

        // 2. Tra cứu trực tiếp từ node stores/{shopId} trên Firebase để đảm bảo luôn lấy STK mới nhất của Quán
        if (shopId.isNotBlank()) {
            FirebaseDatabase.getInstance().getReference("stores").child(shopId).get()
                .addOnSuccessListener { snapshot ->
                    val store = snapshot.getValue(Store::class.java)
                    if (store != null && store.bankAccountNumber.isNotBlank()) {
                        currentAccountNum = BANK_ACCOUNT_NUMBER
                        currentBankName = store.bankName.ifBlank { "MB Bank" }
                        currentAccountHolder = BANK_ACCOUNT_HOLDER
                        currentBankBin = store.bankBin.ifBlank { "970422" }
                        renderPaymentUI(orderId, shopName, amount, subtotal, shippingFee, discount, orderItems)
                    }
                }
        }

        // Nút xác nhận thanh toán xong -> chuyển màn hình OrderSuccessActivity
        binding.btnConfirmPaid.setOnClickListener {
            val intent = Intent(this, OrderSuccessActivity::class.java).apply {
                putExtra(Constants.EXTRA_ORDER_ID, orderId)
                putExtra(Constants.EXTRA_SHOP_NAME, shopName)
                putExtra("extra_address", address)
                putExtra("extra_amount", amount)
                putExtra("extra_subtotal", subtotal)
                putExtra("extra_shipping_fee", shippingFee)
                putExtra("extra_discount", discount)
                putExtra("extra_payment_method", "Chuyển khoản VietQR ($currentBankName)")
            }
            startActivity(intent)
            finish()
        }

        binding.btnCancelToHome.setOnClickListener { navigateHome() }
        binding.btnHomeNav.setOnClickListener { navigateHome() }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /** Hiển thị thông tin ngân hàng & Tạo ảnh mã VietQR chuẩn của Quán */
    private fun renderPaymentUI(
        orderId: String,
        shopName: String,
        amount: Long,
        subtotal: Long,
        shippingFee: Long,
        discount: Long,
        orderItems: String
    ) {
        binding.tvAmountValue.text = amount.toVndCurrency()

        val orderCode = "ORD_OISHI_${orderId.takeLast(6).uppercase()}"
        val transferSyntax = orderCode
        binding.tvOrderDetails.text = buildString {
            appendLine("Mã đơn: $orderCode")
            appendLine()
            appendLine("Món ăn:")
            appendLine(orderItems.ifBlank { "Chi tiết món chưa có sẵn" })
            appendLine()
            appendLine("Tiền món: ${subtotal.toVndCurrency()}")
            appendLine("Phí ship: ${shippingFee.toVndCurrency()}")
            if (discount > 0L) appendLine("Giảm giá: -${discount.toVndCurrency()}")
            appendLine("Tổng thanh toán: ${amount.toVndCurrency()}")
            appendLine()
        }

        binding.tvPayTypeBadge.text = if (currentBankName.isNotBlank()) "🏦 VIETQR LIÊN NGÂN HÀNG 24/7 (${currentBankName.uppercase()})" else "🏦 VIETQR LIÊN NGÂN HÀNG 24/7"
        binding.tvBankName.text = currentBankName.ifBlank { "Chưa cập nhật" }
        binding.tvAccountNum.text = currentAccountNum.ifBlank { "Chưa cập nhật" }
        binding.tvAccountHolder.text = currentAccountHolder.ifBlank { "Chưa cập nhật" }
        binding.tvTransferSyntax.text = transferSyntax

        // Copy actions
        binding.btnCopyAccount.setOnClickListener { copyToClipboard("Số tài khoản", currentAccountNum) }
        binding.btnCopySyntax.setOnClickListener { copyToClipboard("Cú pháp chuyển khoản", transferSyntax) }

        // Tạo URL ảnh VietQR tương ứng với STK, BIN và Chủ tài khoản của Quán
        val cleanAccountNo = currentAccountNum.replace(" ", "").trim()
        val cleanBankBin = currentBankBin.ifBlank { "970422" }.trim()
        val encodedAccountName = try {
            URLEncoder.encode(currentAccountHolder.ifBlank { "OISHI FOOD" }, "UTF-8")
        } catch (_: Exception) {
            "OISHI%20FOOD"
        }
        val encodedSyntax = try {
            URLEncoder.encode(transferSyntax, "UTF-8")
        } catch (_: Exception) {
            "OISHI"
        }

        val qrUrl = "https://api.vietqr.io/image/${cleanBankBin}-${cleanAccountNo}-qLrkX2P.jpg?amount=$amount&addInfo=$encodedSyntax&accountName=$encodedAccountName"

        binding.pbQrLoading.visibility = View.VISIBLE
        Glide.with(this)
            .load(qrUrl)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.pbQrLoading.visibility = View.GONE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.pbQrLoading.visibility = View.GONE
                    return false
                }
            })
            .into(binding.ivQrCode)
    }

    private fun startCountdownTimer() {
        countDownTimer = object : CountDownTimer(1800000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding.tvCountdownTimer.text = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                binding.tvCountdownTimer.text = "00:00"
            }
        }.start()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "✓ Đã sao chép $label: $text", Toast.LENGTH_SHORT).show()
    }

    private fun navigateHome() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    private companion object {
        const val BANK_ACCOUNT_NUMBER = "0374022258"
        const val BANK_ACCOUNT_HOLDER = "Hồ Thị Vân Anh"
    }
}
