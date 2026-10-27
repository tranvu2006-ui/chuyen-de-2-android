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
            currentAccountNum = passedShopPhone?.ifBlank { passedBankNum }.orEmpty().ifBlank { "0988888888" }
            currentAccountHolder = passedMomoHolder?.ifBlank { passedBankHolder?.ifBlank { shopName } } ?: shopName
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
                            currentAccountNum = store.momoPhone.ifBlank { store.phone.ifBlank { store.bankAccountNumber.ifBlank { "0988888888" } } }
                            currentAccountHolder = store.momoAccountName.ifBlank { store.ownerName.ifBlank { store.bankAccountName.ifBlank { store.name } } }
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

        // 3. Lắng nghe Realtime trạng thái thanh toán của đơn hàng & Tự động đối soát khi chuyển MoMo xong (không dùng nút bấm tay)
        observeOrderPaymentRealtime()
        startAutoVerifyPaymentWatcher()

        binding.btnCancelToHome.setOnClickListener { navigateHome() }
        binding.btnHomeNav.setOnClickListener { navigateHome() }
    }

    /** Lắng nghe trực tiếp node orders/{orderId} trên Firebase — Khi paymentStatus == PAID thì tự động chuyển trang */
    private fun observeOrderPaymentRealtime() {
        if (orderId.isBlank()) return
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

    /** Khởi tạo giao dịch qua API MoMo (/v2/gateway/api/create) và tự động gọi API (/v2/gateway/api/query) mỗi 3 giây để đọc trạng thái thanh toán */
    private fun startAutoVerifyPaymentWatcher() {
        if (isMomoChannel) {
            binding.tvAutoVerifyStatus.text = "🔄 Đang kết nối API MoMo (/v2/gateway/api/create) để khởi tạo mã QR giao dịch..."
            initMomoGatewayAndStartPolling()
        } else {
            binding.tvAutoVerifyStatus.text = "🔄 Đang chờ hệ thống ngân hàng VietQR xác nhận giao dịch..."
        }
    }

    private fun initMomoGatewayAndStartPolling() {
        if (hasInitializedMomoApi) return
        hasInitializedMomoApi = true

        val transferSyntax = "OISHI ${if (orderId.isNotBlank()) orderId.takeLast(6).uppercase() else ""}".trim()

        lifecycleScope.launch {
            binding.pbQrLoading.visibility = View.VISIBLE
            val createRes = MomoPaymentGatewayClient.createPayment(
                appOrderId = orderId,
                amount = amount,
                momoPhone = currentAccountNum.ifBlank { "0988888888" },
                momoHolderName = currentAccountHolder.ifBlank { shopName },
                transferSyntax = transferSyntax
            )

            currentMomoPayUrl = createRes.payUrl
            currentMomoDeeplink = createRes.deeplink

            // Vẽ mã QR từ dữ liệu qrCodeUrl trả về trực tiếp từ API MoMo
            val qrBitmap = QRCodeUtils.generateQRCodeBitmap(
                text = createRes.qrCodeData,
                width = 512,
                height = 512,
                darkColorHex = "#A50064"
            )
            binding.pbQrLoading.visibility = View.GONE
            binding.ivQrCode.setImageBitmap(qrBitmap)

            // Cho phép chạm vào mã QR hoặc thanh trạng thái để mở trực tiếp Cổng Thanh Toán MoMo (payUrl / deeplink) nếu đang test trên cùng 1 máy
            val openMomoGatewayAction = View.OnClickListener {
                val targetUrl = currentMomoPayUrl.ifBlank { currentMomoDeeplink }
                if (targetUrl.isNotBlank()) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                    } catch (_: Exception) {
                        Toast.makeText(this@PaymentActivity, "Không thể mở trình duyệt thanh toán MoMo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            binding.ivQrCode.setOnClickListener(openMomoGatewayAction)
            binding.layoutAutoVerifyBox.setOnClickListener(openMomoGatewayAction)

            // Lưu mã giao dịch MoMo vào đơn hàng trên Firebase để đối soát
            if (orderId.isNotBlank()) {
                FirebaseDatabase.getInstance().getReference("orders").child(orderId)
                    .updateChildren(
                        mapOf(
                            "momoOrderId" to createRes.momoOrderId,
                            "momoRequestId" to createRes.requestId
                        )
                    )
            }

            // Bắt đầu vòng lặp gọi API MoMo /v2/gateway/api/query mỗi 3 giây để đọc trạng thái thanh toán
            startMomoStatusPollingLoop(createRes.momoOrderId, createRes.requestId)
        }
    }

    private fun startMomoStatusPollingLoop(momoOrderId: String, requestId: String) {
        momoPollingJob?.cancel()
        momoPollingJob = lifecycleScope.launch {
            while (isActive && !hasNavigatedSuccess) {
                binding.tvAutoVerifyStatus.text =
                    "🔄 API MoMo (/v2/gateway/api/query): Đang kiểm tra giao dịch #${momoOrderId.takeLast(6)}...\n(Quét mã QR bằng MoMo hoặc chạm vào đây để mở Cổng MoMo Test)"

                val queryRes = MomoPaymentGatewayClient.queryPaymentStatus(
                    momoOrderId = momoOrderId,
                    requestId = requestId
                )

                if (queryRes.isPaid) {
                    binding.tvAutoVerifyStatus.text =
                        "✅ API MoMo xác nhận ĐÃ THANH TOÁN (TransID: ${queryRes.transId})! Đang chuyển trạng thái..."
                    if (orderId.isNotBlank()) {
                        val updates = mapOf<String, Any>(
                            "paymentStatus" to Order.PaymentStatus.PAID,
                            "paidAt" to System.currentTimeMillis(),
                            "momoTransId" to queryRes.transId
                        )
                        FirebaseDatabase.getInstance().getReference("orders").child(orderId)
                            .updateChildren(updates)
                            .addOnCompleteListener {
                                navigateToOrderSuccessAuto()
                            }
                    } else {
                        navigateToOrderSuccessAuto()
                    }
                    break
                }

                delay(3000L)
            }
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

        val transferSyntax = "OISHI ${if (orderId.isNotBlank()) orderId.takeLast(6).uppercase() else ""}".trim()

        if (isMomoChannel) {
            binding.toolbar.title = "Quét Mã Ví MoMo (API)"
            binding.tvPayTypeBadge.text = "📱 CỔNG THANH TOÁN API MOMO 24/7"
            binding.tvPayTypeBadge.setTextColor(android.graphics.Color.parseColor("#A50064"))
            binding.tvBankName.text = "Ví điện tử MoMo (API Gateway)"
            binding.tvAccountNum.text = currentAccountNum.ifBlank { "0988888888" }
            binding.tvAccountHolder.text = currentAccountHolder.ifBlank { "OISHI FOOD" }
            binding.tvTransferSyntax.text = transferSyntax
            binding.tvPaymentGuide.text =
                "1. Dùng Camera điện thoại / Zalo quét mã QR ở trên (hoặc chạm trực tiếp vào mã QR) để mở Cổng Thanh Toán MoMo.\n" +
                "2. Trên trang MoMo, chọn 'Thẻ ATM nội địa (Napas)' -> Vietcombank và nhập thẻ test miễn phí của MoMo:\n" +
                "   • Số thẻ: 97040000000018  |  Tên: NGUYEN VAN A\n" +
                "   • Ngày phát hành: 03/07   |  Mã OTP: otp\n" +
                "3. Ngay khi bấm Thanh toán xong trên trang MoMo, API MoMo (/v2/gateway/api/query) sẽ tự động đọc kết quả và chuyển trạng thái đơn hàng sang Cửa hàng!"

            binding.btnCopyAccount.setOnClickListener { copyToClipboard("Số điện thoại Ví MoMo", currentAccountNum) }
            binding.btnCopySyntax.setOnClickListener { copyToClipboard("Số thẻ Test MoMo", "97040000000018") }
            return
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
        momoPollingJob?.cancel()
        autoDetectHandler.removeCallbacksAndMessages(null)
        orderPaymentListener?.let { orderRef?.removeEventListener(it) }
    }
}
