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
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Job
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
    private var momoQrJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
        InsetUtils.applyToolbarInsets(binding.toolbar)
        InsetUtils.applyNavigationBarPadding(binding.bottomBar)

        checkSmsPermission()

        orderId = intent.getStringExtra(Constants.EXTRA_ORDER_ID) ?: ""
        val shopId = intent.getStringExtra(Constants.EXTRA_SHOP_ID) ?: ""
        shopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME) ?: "Quán Ăn Oishi"
        address = intent.getStringExtra("extra_address") ?: ""
        amount = intent.getLongExtra("extra_amount", 0L)
        subtotal = intent.getLongExtra("extra_subtotal", 0L)
        shippingFee = intent.getLongExtra("extra_shipping_fee", 0L)
        discount = intent.getLongExtra("extra_discount", 0L)
        val paymentMode = intent.getStringExtra("extra_payment_mode") ?: com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.FULL
        val remainAmount = intent.getLongExtra("extra_remain", 0L)
        val createdAt = intent.getLongExtra("extra_created_at", System.currentTimeMillis())
        isMomoChannel = intent.getStringExtra("extra_channel").orEmpty().equals("momo", ignoreCase = true)

        // MoMo orders use the store's MoMo receiving details and are confirmed manually by the store.
        val passedBankName = intent.getStringExtra("extra_bank_name")
        val passedBankNum = intent.getStringExtra("extra_bank_number")
        val passedBankHolder = intent.getStringExtra("extra_bank_holder")
        val passedBankBin = intent.getStringExtra("extra_bank_bin")
        val passedMomoPhone = intent.getStringExtra("extra_shop_phone")
        val passedMomoHolder = intent.getStringExtra("extra_momo_holder")

        if (isMomoChannel) {
            currentBankName = "Ví MoMo"
            currentAccountNum = passedMomoPhone.orEmpty().ifBlank { passedBankNum.orEmpty() }
            currentAccountHolder = passedMomoHolder.orEmpty().ifBlank { shopName }
            currentBankBin = passedBankBin?.ifBlank { currentBankBin } ?: currentBankBin
        } else if (!passedBankNum.isNullOrBlank()) {
            currentAccountNum = passedBankNum
            currentBankName = if (!passedBankName.isNullOrBlank()) passedBankName else "Ngân hàng thụ hưởng"
            currentAccountHolder = if (!passedBankHolder.isNullOrBlank()) passedBankHolder else shopName
            currentBankBin = if (!passedBankBin.isNullOrBlank()) passedBankBin else "970422"
        }

        setupToolbar(paymentMode)
        renderPaymentUI(orderId, amount, paymentMode, remainAmount)
        startCountdownTimer(orderId, createdAt)

        if (orderId.isNotBlank()) {
            observeOrderPaymentRealtime()
        }

        if (shopId.isNotBlank()) {
            FirebaseDatabase.getInstance().getReference("stores").child(shopId).get()
                .addOnSuccessListener { snapshot ->
                    val store = snapshot.getValue(Store::class.java)
                    if (store != null) {
                        if (isMomoChannel) {
                            currentAccountNum = store.momoPhone.ifBlank {
                                store.phone.ifBlank { store.bankAccountNumber }
                            }
                            currentBankName = "Ví MoMo"
                            currentAccountHolder = store.momoAccountName
                                .ifBlank {
                                    store.ownerName.ifBlank {
                                        store.bankAccountName.ifBlank { store.name }
                                    }
                                }
                            currentBankBin = store.bankBin.ifBlank { currentBankBin }
                            renderPaymentUI(orderId, amount, paymentMode, remainAmount)
                        } else if (store.bankAccountNumber.isNotBlank()) {
                            currentAccountNum = store.bankAccountNumber
                            currentBankName = store.bankName.ifBlank { "MB Bank" }
                            currentAccountHolder = store.bankAccountName.ifBlank { store.name }
                            currentBankBin = store.bankBin.ifBlank { "970422" }
                            renderPaymentUI(orderId, amount, paymentMode, remainAmount)
                        }
                    }
                }
        }

        binding.btnCancelToHome.setOnClickListener { navigateHome() }
        binding.btnHomeNav.setOnClickListener { navigateHome() }
    }

    private fun checkSmsPermission() {
        val permissions = arrayOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_SMS
        )
        val missingPermissions = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                101
            )
        }
    }

    /**
     * BƯỚC QUAN TRỌNG: Lắng nghe biến động Realtime từ Firebase Database.
     * Khi khách chuyển khoản -> BankSmsReceiver (máy Quán) bắt SMS biến động số dư -> Cập nhật paymentStatus = "PAID".
     * Bộ lắng nghe này phát hiện và tự động chuyển trang OrderSuccessActivity.
     */
    private fun observeOrderPaymentRealtime() {
        if (orderId.isBlank()) return
        // 1. Trỏ trực tiếp đến node đơn hàng trên Realtime Database: orders/{orderId}
        val ref = FirebaseDatabase.getInstance().getReference("orders").child(orderId)
        orderRef = ref

        // 2. Thiết lập bộ lắng nghe ValueEventListener
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Đọc trạng thái thanh toán từ Firebase
                val status = snapshot.child("paymentStatus").getValue(String::class.java).orEmpty()
                // Nếu trạng thái đã là PAID -> Kích hoạt chuyển màn hình tự động
                if (status.equals(Order.PaymentStatus.PAID, ignoreCase = true)) {
                    navigateToOrderSuccessAuto()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        orderPaymentListener = listener
        ref.addValueEventListener(listener)
    }

    /**
     * Tự động điều hướng sang màn hình Đặt Hàng Thành Công (OrderSuccessActivity)
     */
    private fun navigateToOrderSuccessAuto() {
        // Tránh điều hướng trùng lặp nếu sự kiện gọi nhiều lần
        if (hasNavigatedSuccess || isFinishing || isDestroyed) return
        hasNavigatedSuccess = true

        // Xác định tên phương thức thanh toán hiển thị
        val payMethodDisplay = if (isMomoChannel) {
            "Ví MoMo (Tự động xác nhận)"
        } else {
            "Chuyển khoản VietQR ($currentBankName)"
        }

        // Bật thông báo Toast thành công
        val successMessage = if (isMomoChannel) {
            "Hệ thống đã tự động ghi nhận thanh toán MoMo thành công!"
        } else {
            "Hệ thống đã tự động ghi nhận chuyển khoản thành công!"
        }
        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()

        // Mở màn hình OrderSuccessActivity kèm thông tin chi tiết đơn hàng
        val intent = Intent(this, OrderSuccessActivity::class.java).apply {
            putExtra(Constants.EXTRA_ORDER_ID, orderId)
            putExtra(Constants.EXTRA_SHOP_NAME, shopName)
            putExtra("extra_address", address)
            putExtra("extra_amount", amount)
            putExtra("extra_subtotal", subtotal)
            putExtra("extra_shipping_fee", shippingFee)
            putExtra("extra_discount", discount)
            putExtra("extra_payment_method", payMethodDisplay)
            putExtra(
                "extra_payment_mode",
                intent.getStringExtra("extra_payment_mode") ?: Order.PaymentMode.FULL
            )
            putExtra("extra_deposit", intent.getLongExtra("extra_deposit", amount))
            putExtra("extra_remain", intent.getLongExtra("extra_remain", 0L))
            putExtra("extra_total", intent.getLongExtra("extra_total", amount))
        }
        startActivity(intent)
        finish()
    }

    /**
     * Cài đặt Toolbar: thay đổi tiêu đề theo kênh thanh toán (MoMo / Đặt Cọc / Thanh Toán 100%).
     */
    private fun setupToolbar(paymentMode: String = com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.FULL) {
        if (isMomoChannel) {
            binding.toolbar.title = "Quét Mã Ví MoMo"
        } else if (paymentMode == com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.DEPOSIT) {
            binding.toolbar.title = "Quét Mã Đặt Cọc 20%"
        } else {
            binding.toolbar.title = "Quét Mã Thanh Toán 100%"
        }
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /** Hiển thị thông tin thanh toán và tạo QR tương ứng với kênh đã chọn */
    private fun renderPaymentUI(orderId: String, amount: Long, paymentMode: String = com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.FULL, remainAmount: Long = 0L) {
        if (orderId.isBlank()) return
        binding.tvAmountValue.text = amount.toVndCurrency()
        val transferSyntax = orderId.trim()

        if (isMomoChannel) {
            binding.toolbar.title = "Quét Mã Ví MoMo"
            binding.tvPayTypeBadge.text = "📱 VÍ ĐIỆN TỬ MOMO 24/7"
            binding.tvPayTypeBadge.setTextColor(android.graphics.Color.parseColor("#A50064"))
            binding.tvAmountLabel.text = if (paymentMode == Order.PaymentMode.DEPOSIT) {
                "Số tiền cọc cần thanh toán qua MoMo (20%):"
            } else {
                "Số tiền cần thanh toán qua MoMo:"
            }
            binding.tvPaymentSubNotice.text = if (paymentMode == Order.PaymentMode.DEPOSIT) {
                "Đã cọc 20% giữ đơn. Số tiền còn lại ${remainAmount.toVndCurrency()} bạn thanh toán khi nhận hàng."
            } else {
                "Thanh toán qua ví MoMo; cửa hàng sẽ kiểm tra và xác nhận giao dịch."
            }
            binding.tvBankLabel.text = "Ví điện tử"
            binding.tvAccountNumLabel.text = "Số điện thoại ví"
            binding.tvBankName.text = "MoMo"
            binding.tvAccountNum.text = currentAccountNum.ifBlank { "Chưa cập nhật" }
            binding.tvAccountHolder.text = currentAccountHolder.ifBlank { "Chưa cập nhật" }
            val prefix = if (paymentMode == Order.PaymentMode.DEPOSIT) "COC" else "OISHI"
            val shortCode = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.getShortOrderCode(orderId)
            val momoTransferSyntax = "$prefix $shortCode".trim()
            binding.tvTransferSyntax.text = momoTransferSyntax
            binding.tvPaymentGuide.text =
                "1. Mở ứng dụng MoMo và chọn Quét Mã QR.\n" +
                    "2. Quét mã để chuyển tiền vào tài khoản liên kết với ví MoMo; kiểm tra người nhận, số tiền và nội dung $momoTransferSyntax.\n" +
                    "3. Cửa hàng sẽ kiểm tra giao dịch thực tế và xác nhận đơn thủ công."

            if (currentAccountNum.isBlank()) {
                binding.pbQrLoading.visibility = View.GONE
                Toast.makeText(this, "Quán chưa cập nhật số điện thoại ví MoMo để nhận thanh toán.", Toast.LENGTH_SHORT).show()
                return
            }

            binding.btnCopyAccount.setOnClickListener {
                copyToClipboard("Số điện thoại ví MoMo", currentAccountNum)
            }
            binding.btnCopySyntax.setOnClickListener {
                copyToClipboard("Nội dung chuyển khoản", momoTransferSyntax)
            }
            generateRealMomoQrCode(currentAccountNum, currentAccountHolder, amount, momoTransferSyntax)
            return
        } else if (paymentMode == com.chuyen_de_2.foodorder.core.data.model.Order.PaymentMode.DEPOSIT) {
            binding.tvPayTypeBadge.text = "⚡ THANH TOÁN TIỀN CỌC GIỮ ĐƠN (20%)"
            binding.tvAmountLabel.text = "Số tiền cọc cần quét ngay (20%):"
            binding.tvPaymentSubNotice.text = "Đã cọc 20% giữ đơn. Số tiền còn lại ${remainAmount.toVndCurrency()} bạn thanh toán cho shipper khi nhận hàng."
        } else {
            binding.tvPayTypeBadge.text = if (currentBankName.isNotBlank()) "🏦 VIETQR LIÊN NGÂN HÀNG 24/7 (${currentBankName.uppercase()})" else "🏦 VIETQR LIÊN NGÂN HÀNG 24/7"
            binding.tvAmountLabel.text = "Số tiền cần thanh toán ngay (100%):"
            binding.tvPaymentSubNotice.text = "Đã thanh toán 100%, khi nhận hàng bạn không cần chuẩn bị tiền mặt."
        }

        if (!isMomoChannel) {
            binding.tvBankLabel.text = "Ngân hàng"
            binding.tvAccountNumLabel.text = "Số tài khoản"
        }

        binding.tvBankName.text = currentBankName.ifBlank { "Chưa cập nhật" }
        binding.tvAccountNum.text = currentAccountNum.ifBlank { "Chưa cập nhật" }
        binding.tvAccountHolder.text = currentAccountHolder.ifBlank { "Chưa cập nhật" }
        binding.tvTransferSyntax.text = transferSyntax

        // Copy actions
        binding.btnCopyAccount.setOnClickListener { copyToClipboard("Số tài khoản", currentAccountNum) }
        binding.btnCopySyntax.setOnClickListener { copyToClipboard("Cú pháp chuyển khoản", transferSyntax) }

        // ── BƯỚC 1: Tạo URL ảnh QR VietQR ──────────────────────────────────────────
        // Làm sạch số tài khoản: xóa khoảng trắng để URL không bị lỗi
        val cleanAccountNo = currentAccountNum.replace(" ", "").trim()
        // Lấy mã BIN ngân hàng (ví dụ 970422 = MB Bank); dùng mặc định nếu trống
        val cleanBankBin = currentBankBin.ifBlank { "970422" }.trim()
        // Mã hóa tên chủ tài khoản sang UTF-8 để nhúng vào URL an toàn
        val encodedAccountName = try {
            URLEncoder.encode(currentAccountHolder.ifBlank { "OISHI FOOD" }, "UTF-8")
        } catch (_: Exception) {
            "OISHI%20FOOD" // Fallback nếu encoding thất bại
        }
        // Mã hóa nội dung chuyển khoản (cú pháp) sang UTF-8 để nhúng vào URL
        val encodedSyntax = try {
            URLEncoder.encode(transferSyntax, "UTF-8")
        } catch (_: Exception) {
            transferSyntax // Dùng nguyên văn nếu encoding thất bại
        }

        // ── BƯỚC 2: Ghép URL VietQR hoàn chỉnh ─────────────────────────────────────
        // Cấu trúc: https://api.vietqr.io/image/{BIN}-{STK}-{template}.jpg?amount=...&addInfo=...&accountName=...
        // - amount: số tiền VNĐ (không có dấu phẩy)
        // - addInfo: cú pháp đơn hàng để BroadcastReceiver (Di Động 2) hoặc Quán nhận diện đối soát
        // - accountName: tên chủ tài khoản hiển thị trên app ngân hàng
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

    private fun generateRealMomoQrCode(
        phone: String,
        holderName: String,
        paymentAmount: Long,
        transferSyntax: String
    ) {
        momoQrJob?.cancel()
        binding.pbQrLoading.visibility = View.VISIBLE
        momoQrJob = lifecycleScope.launch {
            val result = MomoPaymentGatewayClient.generateRealMomoQr(
                momoPhone = phone,
                momoHolderName = holderName.ifBlank { shopName },
                amount = paymentAmount,
                transferSyntax = transferSyntax,
                bankBin = currentBankBin
            )
            if (isFinishing || isDestroyed) return@launch
            binding.pbQrLoading.visibility = View.GONE
            if (result.qrCodeData.isNotBlank()) {
                binding.ivQrCode.setImageBitmap(
                    QRCodeUtils.generateQRCodeBitmap(
                        text = result.qrCodeData,
                        width = 512,
                        height = 512,
                        darkColorHex = "#A50064"
                    )
                )
                if (!result.isSuccess) {
                    Toast.makeText(this@PaymentActivity, result.message, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@PaymentActivity, "Không tạo được mã MoMo: ${result.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Đếm ngược 30 phút từ thời điểm tạo đơn.
     * Hết giờ → tự động hủy đơn (handleExpiredOrder).
     */
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

    /**
     * Xử lý đơn hàng hết hạn 30 phút: ghi Firebase status = CANCELLED và quay về màn hình chính.
     */
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

    /** Sao chép text (số tài khoản, nội dung chuyển khoản) vào Clipboard và hiện Toast xác nhận. */
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
        orderPaymentListener?.let { orderRef?.removeEventListener(it) }
    }
}
