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
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.MomoPaymentGatewayClient
import com.chuyen_de_2.foodorder.core.util.QRCodeUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.databinding.ActivityPaymentBinding
import com.chuyen_de_2.foodorder.customer.ui.home.HomeActivity
import com.chuyen_de_2.foodorder.customer.ui.order.OrderSuccessActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private var isMomoChannel = false

    private var orderId = ""
    private var shopName = "Quán Ăn Oishi"
    private var address = ""
    private var amount = 0L
    private var subtotal = 0L
    private var shippingFee = 0L
    private var discount = 0L

    private var orderRef: DatabaseReference? = null
    private var orderPaymentListener: ValueEventListener? = null
    private var hasNavigatedSuccess = false
    private var momoPollingJob: Job? = null
    private var currentMomoPayUrl = ""
    private var currentMomoDeeplink = ""
    private var hasInitializedMomoApi = false
    private var leftAppTimestamp = 0L
    private var screenOpenedTimestamp = 0L
    private val autoDetectHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
        InsetUtils.applyToolbarInsets(binding.toolbar)
        InsetUtils.applyNavigationBarPadding(binding.bottomBar)

        orderId = intent.getStringExtra(Constants.EXTRA_ORDER_ID) ?: ""
        val shopId = intent.getStringExtra(Constants.EXTRA_SHOP_ID) ?: ""
        shopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME) ?: "Quán Ăn Oishi"
        address = intent.getStringExtra("extra_address") ?: ""
        amount = intent.getLongExtra("extra_amount", 0L)
        subtotal = intent.getLongExtra("extra_subtotal", 0L)
        shippingFee = intent.getLongExtra("extra_shipping_fee", 0L)
        discount = intent.getLongExtra("extra_discount", 0L)
        val channel = intent.getStringExtra("extra_channel").orEmpty()
        isMomoChannel = channel.equals("momo", ignoreCase = true)

        // 1. Nhận thông tin ngân hàng / ví MoMo truyền qua Intent (nếu có)
        val passedShopPhone = intent.getStringExtra("extra_shop_phone")
        val passedMomoHolder = intent.getStringExtra("extra_momo_holder")
        val passedBankName = intent.getStringExtra("extra_bank_name")
        val passedBankNum = intent.getStringExtra("extra_bank_number")
        val passedBankHolder = intent.getStringExtra("extra_bank_holder")
        val passedBankBin = intent.getStringExtra("extra_bank_bin")

        if (isMomoChannel) {
            currentBankName = "Ví điện tử MoMo"
            currentAccountNum = passedShopPhone?.ifBlank { passedBankNum }.orEmpty().ifBlank { "0798351169" }
            currentAccountHolder = passedMomoHolder?.ifBlank { passedBankHolder?.ifBlank { shopName } } ?: shopName
            currentBankBin = if (!passedBankBin.isNullOrBlank()) passedBankBin else "970422"
        } else if (!passedBankNum.isNullOrBlank()) {
            currentAccountNum = passedBankNum
            currentBankName = if (!passedBankName.isNullOrBlank()) passedBankName else "Ngân hàng thụ hưởng"
            currentAccountHolder = if (!passedBankHolder.isNullOrBlank()) passedBankHolder else shopName
            currentBankBin = if (!passedBankBin.isNullOrBlank()) passedBankBin else "970422"
        }

        setupToolbar()
        renderPaymentUI(orderId, amount)
        startCountdownTimer()

        // 2. Tra cứu trực tiếp từ node stores/{shopId} trên Firebase để luôn lấy SĐT MoMo / STK Ngân hàng mới nhất của Quán
        if (shopId.isNotBlank()) {
            FirebaseDatabase.getInstance().getReference("stores").child(shopId).get()
                .addOnSuccessListener { snapshot ->
                    val store = snapshot.getValue(Store::class.java)
                    if (store != null) {
                        if (isMomoChannel) {
                            currentBankName = "Ví điện tử MoMo"
                            currentAccountNum = store.momoPhone.ifBlank { store.phone.ifBlank { store.bankAccountNumber.ifBlank { "0798351169" } } }
                            currentAccountHolder = store.momoAccountName.ifBlank { store.ownerName.ifBlank { store.bankAccountName.ifBlank { store.name } } }
                            currentBankBin = store.bankBin.ifBlank { "970422" }
                            renderPaymentUI(orderId, amount)
                        } else if (store.bankAccountNumber.isNotBlank()) {
                            currentAccountNum = store.bankAccountNumber
                            currentBankName = store.bankName.ifBlank { "MB Bank" }
                            currentAccountHolder = store.bankAccountName.ifBlank { store.name }
                            currentBankBin = store.bankBin.ifBlank { "970422" }
                            renderPaymentUI(orderId, amount)
                        }
                    }
                }
        }

        // 3. Nếu là Ví MoMo: ẩn nút thủ công, tự động lắng nghe & đối soát giao dịch. Nếu là VietQR: giữ nguyên nút xác nhận chuẩn gốc.
        if (isMomoChannel) {
            binding.btnConfirmPaid.visibility = View.GONE
            binding.layoutAutoVerifyBox.visibility = View.VISIBLE
            observeOrderPaymentRealtime()
            startAutoVerifyPaymentWatcher()
        } else {
            binding.btnConfirmPaid.visibility = View.VISIBLE
            binding.layoutAutoVerifyBox.visibility = View.GONE
        }

        // Nút xác nhận thanh toán xong -> chuyển màn hình OrderSuccessActivity (Giữ nguyên cho VietQR)
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

    /** Lắng nghe trực tiếp node orders/{orderId} trên Firebase — Khi paymentStatus == PAID thì tự động chuyển trang */
    private fun observeOrderPaymentRealtime() {
        if (!isMomoChannel || orderId.isBlank()) return
        val ref = FirebaseDatabase.getInstance().getReference("orders").child(orderId)
        orderRef = ref
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("paymentStatus").getValue(String::class.java).orEmpty()
                if (status.equals(Order.PaymentStatus.PAID, ignoreCase = true)) {
                    navigateToOrderSuccessAuto()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        orderPaymentListener = listener
        ref.addValueEventListener(listener)
    }

    /** Tự động gọi API đối soát giao dịch chuyển khoản Ví MoMo mỗi 3 giây (Không dùng nút bấm thủ công) */
    private fun startAutoVerifyPaymentWatcher() {
        if (!isMomoChannel) return
        screenOpenedTimestamp = System.currentTimeMillis()
        binding.tvAutoVerifyStatus.text =
            "🔄 Đang tự động kiểm tra giao dịch chuyển khoản Ví MoMo... (Tự động chuyển trạng thái sau khi bạn quét mã chuyển xong)"

        startRealTransferApiPolling()
    }

    override fun onStop() {
        super.onStop()
        if (isMomoChannel && !isFinishing && !hasNavigatedSuccess) {
            leftAppTimestamp = System.currentTimeMillis()
        }
    }

    override fun onResume() {
        super.onResume()
        // Chỉ tự động đồng bộ khi thực sự phát hiện giao dịch chuyển khoản hợp lệ (tuyệt đối không tự xác nhận theo thời gian chờ)
        if (isMomoChannel && !hasNavigatedSuccess) {
            if (com.chuyen_de_2.foodorder.customer.service.CustomerPaymentNotificationService.hasVerifiedTransferForOrder(
                    orderId = orderId,
                    expectedAmount = amount,
                    sinceTimestamp = screenOpenedTimestamp
                )
            ) {
                binding.tvAutoVerifyStatus.text = "✅ Đã nhận diện hoàn tất chuyển khoản! Đang đồng bộ sang Cửa hàng..."
                confirmPaidAndSyncToStore()
            }
        }
    }

    private fun confirmPaidAndSyncToStore() {
        if (hasNavigatedSuccess) return
        if (orderId.isNotBlank()) {
            FirebaseDatabase.getInstance().getReference("orders").child(orderId)
                .updateChildren(
                    mapOf(
                        "paymentStatus" to Order.PaymentStatus.PAID,
                        "paidAt" to System.currentTimeMillis()
                    )
                )
                .addOnCompleteListener {
                    navigateToOrderSuccessAuto()
                }
        } else {
            navigateToOrderSuccessAuto()
        }
    }

    private fun startRealTransferApiPolling() {
        lifecycleScope.launch {
            while (isActive && !hasNavigatedSuccess) {
                val verifyRes = MomoPaymentGatewayClient.checkRealTransferViaApi(
                    orderId = orderId,
                    expectedAmount = amount
                )
                val notifiedPaid = com.chuyen_de_2.foodorder.customer.service.CustomerPaymentNotificationService.hasVerifiedTransferForOrder(
                    orderId = orderId,
                    expectedAmount = amount,
                    sinceTimestamp = screenOpenedTimestamp
                )
                if (verifyRes.isPaid || notifiedPaid) {
                    binding.tvAutoVerifyStatus.text = "✅ Đã ghi nhận giao dịch chuyển khoản thành công! Đang đồng bộ sang Cửa hàng..."
                    confirmPaidAndSyncToStore()
                    break
                }
                delay(2500L)
            }
        }
    }

    private fun generateRealMomoQrCode(orderId: String, amount: Long) {
        val rawCode = if (orderId.isNotBlank()) orderId.takeLast(6).uppercase() else ""
        val cleanCode = rawCode.replace(Regex("[^A-Z0-9]"), "")
        val transferSyntax = "OISHI $cleanCode".trim()

        momoPollingJob?.cancel()
        momoPollingJob = lifecycleScope.launch {
            binding.pbQrLoading.visibility = View.VISIBLE
            val qrResult = MomoPaymentGatewayClient.generateRealMomoQr(
                momoPhone = currentAccountNum.ifBlank { "0798351169" },
                momoHolderName = currentAccountHolder.ifBlank { shopName },
                amount = amount,
                transferSyntax = transferSyntax,
                bankBin = currentBankBin.ifBlank { "970422" }
            )
            val qrBitmap = QRCodeUtils.generateQRCodeBitmap(
                text = qrResult.qrCodeData,
                width = 512,
                height = 512,
                darkColorHex = "#A50064"
            )
            binding.pbQrLoading.visibility = View.GONE
            binding.ivQrCode.setImageBitmap(qrBitmap)
        }
    }

    private fun navigateToOrderSuccessAuto() {
        if (hasNavigatedSuccess || isFinishing || isDestroyed) return
        hasNavigatedSuccess = true
        autoDetectHandler.removeCallbacksAndMessages(null)

        val payMethodDisplay = if (isMomoChannel) {
            "Ví điện tử MoMo (Đã chuyển khoản)"
        } else {
            "Chuyển khoản VietQR ($currentBankName)"
        }

        Toast.makeText(this, "✅ Hệ thống đã tự động ghi nhận chuyển khoản thành công!", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, OrderSuccessActivity::class.java).apply {
            putExtra(Constants.EXTRA_ORDER_ID, orderId)
            putExtra(Constants.EXTRA_SHOP_NAME, shopName)
            putExtra("extra_address", address)
            putExtra("extra_amount", amount)
            putExtra("extra_subtotal", subtotal)
            putExtra("extra_shipping_fee", shippingFee)
            putExtra("extra_discount", discount)
            putExtra("extra_payment_method", payMethodDisplay)
        }
        startActivity(intent)
        finish()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /** Hiển thị thông tin ngân hàng / Ví MoMo & Tạo ảnh mã QR chuẩn của Quán */
    private fun renderPaymentUI(orderId: String, amount: Long) {
        binding.tvAmountValue.text = amount.toVndCurrency()

        if (isMomoChannel) {
            val rawCode = if (orderId.isNotBlank()) orderId.takeLast(6).uppercase() else ""
            val cleanCode = rawCode.replace(Regex("[^A-Z0-9]"), "")
            val momoSyntax = "OISHI $cleanCode".trim()

            binding.toolbar.title = "Quét Mã Ví MoMo"
            binding.tvPayTypeBadge.text = "📱 VÍ ĐIỆN TỬ MOMO 24/7"
            binding.tvPayTypeBadge.setTextColor(android.graphics.Color.parseColor("#A50064"))
            binding.tvBankLabel.text = "Ví điện tử"
            binding.tvAccountNumLabel.text = "Số điện thoại Ví"
            binding.tvBankName.text = "Ví điện tử MoMo"
            binding.tvAccountNum.text = currentAccountNum.ifBlank { "0988888888" }
            binding.tvAccountHolder.text = currentAccountHolder.ifBlank { "OISHI FOOD" }
            binding.tvTransferSyntax.text = momoSyntax
            binding.tvPaymentGuide.text =
                "1. Mở ứng dụng Ví MoMo trên điện thoại và chọn Quét Mã QR.\n" +
                "2. Quét mã QR ở trên để chuyển khoản trực tiếp vào SĐT Ví MoMo của Quán (${currentAccountNum.ifBlank { "0988888888" }}).\n" +
                "3. Sau khi chuyển tiền thành công trên MoMo, đơn hàng sẽ tự động đồng bộ trạng thái Đã chuyển MoMo sang Cửa hàng!"

            binding.btnCopyAccount.setOnClickListener { copyToClipboard("Số điện thoại Ví MoMo", currentAccountNum) }
            binding.btnCopySyntax.setOnClickListener { copyToClipboard("Nội dung chuyển khoản", momoSyntax) }

            generateRealMomoQrCode(orderId, amount)
            return
        }

        val transferSyntax = "OISHI ${if (orderId.isNotBlank()) orderId.takeLast(6).uppercase() else ""}".trim()

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
        momoPollingJob?.cancel()
        autoDetectHandler.removeCallbacksAndMessages(null)
        orderPaymentListener?.let { orderRef?.removeEventListener(it) }
    }
}
