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
    private var currentAccountNum = ""
    private var currentAccountHolder = ""
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
        val paymentMode = intent.getStringExtra("extra_payment_mode") ?: com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.FULL
        val depositAmount = intent.getLongExtra("extra_deposit", 0L)
        val remainAmount = intent.getLongExtra("extra_remain", 0L)
        val totalOrder = intent.getLongExtra("extra_total", amount)

        val createdAt = intent.getLongExtra("extra_created_at", System.currentTimeMillis())

        // 1. Nhận thông tin ngân hàng truyền qua Intent (nếu có)
        val passedBankName = intent.getStringExtra("extra_bank_name")
        val passedBankNum = intent.getStringExtra("extra_bank_number")
        val passedBankHolder = intent.getStringExtra("extra_bank_holder")
        val passedBankBin = intent.getStringExtra("extra_bank_bin")

        if (!passedBankNum.isNullOrBlank()) {
            currentAccountNum = passedBankNum
            currentBankName = if (!passedBankName.isNullOrBlank()) passedBankName else "Ngân hàng thụ hưởng"
            currentAccountHolder = if (!passedBankHolder.isNullOrBlank()) passedBankHolder else shopName
            currentBankBin = if (!passedBankBin.isNullOrBlank()) passedBankBin else "970422"
        }

        setupToolbar(paymentMode)
        renderPaymentUI(orderId, amount, paymentMode, remainAmount)
        startCountdownTimer(orderId, createdAt)

        // 2. Tra cứu trực tiếp từ node stores/{shopId} trên Firebase để đảm bảo luôn lấy STK mới nhất của Quán
        if (shopId.isNotBlank()) {
            FirebaseDatabase.getInstance().getReference("stores").child(shopId).get()
                .addOnSuccessListener { snapshot ->
                    val store = snapshot.getValue(Store::class.java)
                    if (store != null && store.bankAccountNumber.isNotBlank()) {
                        currentAccountNum = store.bankAccountNumber
                        currentBankName = store.bankName.ifBlank { "MB Bank" }
                        currentAccountHolder = store.bankAccountName.ifBlank { store.name }
                        currentBankBin = store.bankBin.ifBlank { "970422" }
                        renderPaymentUI(orderId, amount, paymentMode, remainAmount)
                    }
                }
        }

        // Nút xác nhận thanh toán xong -> Cập nhật trạng thái đơn sang PENDING cho quán nhận & chuyển màn hình OrderSuccessActivity
        binding.btnConfirmPaid.setOnClickListener {
            if (orderId.isNotBlank()) {
                val updates = mutableMapOf<String, Any>(
                    "status" to com.chuyen_de_2.foodorder.core.data.model.Order.Status.PENDING,
                    "orderStatus" to com.chuyen_de_2.foodorder.core.data.model.Order.Status.PENDING,
                    "updatedAt" to System.currentTimeMillis()
                )
                if (paymentMode == com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.DEPOSIT) {
                    updates["isDepositPaid"] = true
                } else {
                    updates["isPaid"] = true
                }
                FirebaseDatabase.getInstance().getReference("orders").child(orderId).updateChildren(updates)
            }

            val methodLabel = if (paymentMode == com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.DEPOSIT) {
                "Đặt cọc 20% (VietQR $currentBankName)"
            } else {
                "Chuyển khoản VietQR 100% ($currentBankName)"
            }
            val intent = Intent(this, OrderSuccessActivity::class.java).apply {
                putExtra(Constants.EXTRA_ORDER_ID, orderId)
                putExtra(Constants.EXTRA_SHOP_NAME, shopName)
                putExtra("extra_address", address)
                putExtra("extra_amount", if (paymentMode == com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.DEPOSIT) depositAmount else totalOrder)
                putExtra("extra_deposit", depositAmount)
                putExtra("extra_remain", remainAmount)
                putExtra("extra_total", totalOrder)
                putExtra("extra_subtotal", subtotal)
                putExtra("extra_shipping_fee", shippingFee)
                putExtra("extra_discount", discount)
                putExtra("extra_payment_method", methodLabel)
                putExtra("extra_payment_mode", paymentMode)
            }
            startActivity(intent)
            finish()
        }

        binding.btnCancelToHome.setOnClickListener { navigateHome() }
        binding.btnHomeNav.setOnClickListener { navigateHome() }
    }

    private fun setupToolbar(paymentMode: String) {
        if (paymentMode == com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.DEPOSIT) {
            binding.toolbar.title = "Quét Mã Đặt Cọc 20%"
        } else {
            binding.toolbar.title = "Quét Mã Thanh Toán 100%"
        }
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /** Hiển thị thông tin ngân hàng & Tạo ảnh mã VietQR chuẩn của Quán */
    private fun renderPaymentUI(orderId: String, amount: Long, paymentMode: String = com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.FULL, remainAmount: Long = 0L) {
        binding.tvAmountValue.text = amount.toVndCurrency()

        val prefix = if (paymentMode == com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.DEPOSIT) "COC" else "OISHI"
        val code = if (orderId.isNotBlank()) orderId.takeLast(6).uppercase() else ""
        val transferSyntax = "$prefix $code".trim()

        if (paymentMode == com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.DEPOSIT) {
            binding.tvPayTypeBadge.text = "⚡ THANH TOÁN TIỀN CỌC GIỮ ĐƠN (20%)"
            binding.tvAmountLabel.text = "Số tiền cọc cần quét ngay (20%):"
            binding.tvPaymentSubNotice.text = "Đã cọc 20% giữ đơn. Số tiền còn lại ${remainAmount.toVndCurrency()} bạn thanh toán cho shipper khi nhận hàng."
            binding.btnConfirmPaid.text = "Tôi Đã Chuyển Tiền Cọc Xong"
        } else {
            binding.tvPayTypeBadge.text = if (currentBankName.isNotBlank()) "🏦 VIETQR LIÊN NGÂN HÀNG 24/7 (${currentBankName.uppercase()})" else "🏦 VIETQR LIÊN NGÂN HÀNG 24/7"
            binding.tvAmountLabel.text = "Số tiền cần thanh toán ngay (100%):"
            binding.tvPaymentSubNotice.text = "Đã thanh toán 100%, khi nhận hàng bạn không cần chuẩn bị tiền mặt."
            binding.btnConfirmPaid.text = "Tôi Đã Chuyển Khoản Xong"
        }
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

    private fun startCountdownTimer(orderId: String, createdAt: Long) {
        val now = System.currentTimeMillis()
        val orderCreated = if (createdAt > 0L) createdAt else now
        val elapsed = now - orderCreated
        val totalAllowedMs = 30 * 60 * 1000L // 30 phút
        val remainingMs = maxOf(0L, totalAllowedMs - elapsed)

        if (remainingMs <= 0L) {
            handleExpiredOrder(orderId)
            return
        }

        countDownTimer = object : CountDownTimer(remainingMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding.tvCountdownTimer.text = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                binding.tvCountdownTimer.text = "00:00"
                handleExpiredOrder(orderId)
            }
        }.start()
    }

    private fun handleExpiredOrder(orderId: String) {
        if (orderId.isNotBlank()) {
            val cancelUpdates = mapOf<String, Any>(
                "status" to com.chuyen_de_2.foodorder.core.data.model.Order.Status.CANCELLED,
                "orderStatus" to com.chuyen_de_2.foodorder.core.data.model.Order.Status.CANCELLED,
                "note" to "Đã tự động hủy do quá hạn thanh toán 30 phút",
                "updatedAt" to System.currentTimeMillis()
            )
            FirebaseDatabase.getInstance().getReference("orders").child(orderId).updateChildren(cancelUpdates)
        }
        Toast.makeText(this@PaymentActivity, "Đơn hàng đã quá hạn thanh toán 30 phút và tự động bị hủy.", Toast.LENGTH_LONG).show()
        navigateHome()
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
}
