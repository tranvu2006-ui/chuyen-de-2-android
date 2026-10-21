package com.chuyen_de_2.foodorder.internal.ui.ship

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.GoogleMapUtils
import com.chuyen_de_2.foodorder.core.util.QRCodeUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.ActivityDeliveryTripBinding
import com.chuyen_de_2.foodorder.internal.databinding.DialogCancelOrderReasonBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DeliveryTripActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeliveryTripBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private var currentOrderId: String = ""
    private var currentOrder: Order? = null
    private var currentStep = 1 // 1: Đến quán, 2: Lấy hàng, 3: Đến khách, 4: Quét QR xong
    private var isQrVerified: Boolean = false
    private var verifiedTokenString: String = ""
    private var activeScannerView: DecoratedBarcodeView? = null

    // Tọa độ GPS thực tế của Shipper và Điểm đến
    private var driverLat: Double = 10.8522
    private var driverLng: Double = 106.7685
    private var storeLat: Double = 10.8548
    private var storeLng: Double = 106.7720
    private var customerLat: Double = 10.8580
    private var customerLng: Double = 106.7765

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            fetchRealShipperLocation()
        } else {
            Toast.makeText(this, "Đang dùng vị trí mặc định do chưa cấp quyền GPS", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showQrScannerDialog()
        } else {
            Toast.makeText(
                this,
                "Vui lòng cấp quyền Camera để quét mã QR trên ứng dụng của khách hàng!",
                Toast.LENGTH_LONG
            ).show()
            // Vẫn mở dialog để hỗ trợ nhập mã Token dự phòng nếu thiết bị không có camera
            showQrScannerDialog()
        }
    }

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"

        fun start(context: Context, orderId: String) {
            val intent = Intent(context, DeliveryTripActivity::class.java).apply {
                putExtra(EXTRA_ORDER_ID, orderId)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeliveryTripBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        currentOrderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: ""
        if (currentOrderId.isBlank()) {
            Toast.makeText(this, "Không tìm thấy mã đơn hàng", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupWebViews()
        setupSwipeToConfirm()
        setupListeners()
        observeOrderDetails()
        updateStepUI(1)
        checkAndRequestLocation()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViews() {
        // Cấu hình WebView cho bản đồ bước 1 (Google Maps Roadmap Tiles không cần API key)
        binding.webViewMapStep1.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    evaluateJavascript("updateDriverLocation($driverLat, $driverLng);", null)
                    evaluateJavascript("setDestination($storeLat, $storeLng);", null)
                }
            }
            loadUrl("file:///android_asset/map_step1.html")
        }

        // Cấu hình WebView cho bản đồ bước 3 (Google Maps Roadmap Tiles không cần API key)
        binding.webViewMapStep3.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    evaluateJavascript("updateDriverLocation($driverLat, $driverLng);", null)
                    val name = currentOrder?.customerName ?: "Khách hàng"
                    evaluateJavascript("setDestination($customerLat, $customerLng, '$name');", null)
                }
            }
            loadUrl("file:///android_asset/map_step3.html")
        }
    }

    /**
     * Thanh trượt gạt sang phải xác nhận đã đến quán (4.html: swipe-container)
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeToConfirm() {
        val track = binding.swipeTrack
        val thumb = binding.swipeThumb
        val label = binding.tvSwipeLabel
        var startX = 0f
        var isConfirmed = false

        thumb.setOnTouchListener { v, event ->
            if (isConfirmed) return@setOnTouchListener true
            val maxDrag = (track.width - thumb.width - 16).toFloat()
            if (maxDrag <= 0) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX - thumb.translationX
                    thumb.animate().cancel()
                    label.animate().cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = (event.rawX - startX).coerceIn(0f, maxDrag)
                    thumb.translationX = newX
                    label.alpha = (1f - (newX / maxDrag)).coerceIn(0.1f, 1f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (thumb.translationX >= maxDrag * 0.65f) {
                        // Kéo thành công vượt ngưỡng 65% -> trượt hết về đích
                        isConfirmed = true
                        thumb.animate()
                            .translationX(maxDrag)
                            .setDuration(120)
                            .withEndAction {
                                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                Toast.makeText(this, "✓ ĐÃ ĐẾN QUÁN ĂN!", Toast.LENGTH_SHORT).show()
                                updateStepUI(2)
                                thumb.translationX = 0f
                                label.alpha = 1f
                                isConfirmed = false
                            }
                            .start()
                    } else {
                        // Thả tay giữa chừng -> nảy lùi về ban đầu
                        thumb.animate()
                            .translationX(0f)
                            .setDuration(220)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                        label.animate().alpha(1f).setDuration(220).start()
                    }
                    true
                }
                else -> false
            }
        }

        // Hỗ trợ click trực tiếp vào thanh trượt nếu không vuốt
        track.setOnClickListener {
            Toast.makeText(this, "Gạt nút tròn sang phải để xác nhận đã đến quán!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeOrderDetails() {
        lifecycleScope.launch {
            firebaseRepository.observeOrder(currentOrderId).collectLatest { order ->
                if (order != null) {
                    val isFirstLoad = (currentOrder == null)
                    currentOrder = order
                    bindOrderData(order)
                    if (isFirstLoad && order.status == Order.Status.DELIVERING && currentStep < 3) {
                        updateStepUI(3)
                    }
                }
            }
        }
    }

    private fun bindOrderData(order: Order) {
        val shortCode = QRCodeUtils.getShortOrderCode(order.orderId.ifBlank { currentOrderId })
        binding.tvTripOrderCode.text = "Đơn #ORD-$shortCode"

        val storeName = order.shopName.ifBlank { "Quán ăn" }
        binding.tvStep1StoreName.text = storeName
        binding.tvTripStoreBadge.text = storeName

        val cusName = order.customerName.ifBlank { "Khách hàng" }
        binding.tvStep3CustomerName.text = cusName

        val dest = order.address.ifBlank { "Địa chỉ giao hàng" }
        binding.tvStep3DeliveryAddress.text = "📍 Địa chỉ: $dest"

        val fee = if (order.shippingFee > 0) order.shippingFee else 25000L
        val totalPayment = if (order.totalPayment > 0) order.totalPayment else (order.total + fee)

        val isCod = order.paymentMethod.equals("COD", ignoreCase = true)
        if (isCod) {
            binding.tvStep3PaymentMethodBadge.text = "💵 Thu tiền mặt COD: ${totalPayment.toVndCurrency()}"
            binding.tvStep4CodCollection.text = "Thu Tiền Mặt COD: ${totalPayment.toVndCurrency()}"
        } else {
            binding.tvStep3PaymentMethodBadge.text = "✓ ĐÃ THANH TOÁN ONLINE (VIETQR) - KHÔNG THU TIỀN"
            binding.tvStep4CodCollection.text = "ĐÃ THANH TOÁN TRƯỚC (COD: 0 đ)"
        }

        val phoneDisplay = if (order.customerPhone.isNotBlank()) order.customerPhone else "Khách hàng"
        binding.tvStep4Summary.text = "$cusName • $phoneDisplay\nĐịa chỉ: $dest"

        // Geocoding địa chỉ khách thực tế để vẽ lộ trình thật
        lifecycleScope.launch {
            try {
                val coords = GoogleMapUtils.geocodeAddress(this@DeliveryTripActivity, dest)
                customerLat = coords.latitude
                customerLng = coords.longitude
                binding.webViewMapStep3.evaluateJavascript("setDestination($customerLat, $customerLng, '$cusName');", null)
            } catch (e: Exception) {
                // Giữ nguyên tọa độ mặc định nếu lỗi mạng
            }
        }
    }

    private fun checkAndRequestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            fetchRealShipperLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchRealShipperLocation() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        driverLat = location.latitude
                        driverLng = location.longitude
                        binding.webViewMapStep1.evaluateJavascript("updateDriverLocation($driverLat, $driverLng);", null)
                        binding.webViewMapStep3.evaluateJavascript("updateDriverLocation($driverLat, $driverLng);", null)
                    }
                }
                .addOnFailureListener {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) {
                            driverLat = loc.latitude
                            driverLng = loc.longitude
                            binding.webViewMapStep1.evaluateJavascript("updateDriverLocation($driverLat, $driverLng);", null)
                            binding.webViewMapStep3.evaluateJavascript("updateDriverLocation($driverLat, $driverLng);", null)
                        }
                    }
                }
        } catch (e: Exception) {
            // Không làm gián đoạn UI
        }
    }

    private fun setupListeners() {
        binding.btnTripBack.setOnClickListener {
            finish()
        }

        binding.btnTripCancel.setOnClickListener {
            showCancelOrderDialog()
        }

        // Bước 1: Nút "Đến quán" trên hộp Google Navigation (Mở Google Maps Turn-by-Turn thật)
        binding.btnOpenNavStep1.setOnClickListener {
            binding.webViewMapStep1.evaluateJavascript("focusShop();", null)
            GoogleMapUtils.openGoogleMapsNavigation(this, storeLat, storeLng, binding.tvStep1StoreName.text.toString())
        }

        // Bước 1: Gọi điện cho quán
        binding.btnCallStoreStep1.setOnClickListener {
            val phone = currentOrder?.shopPhone?.ifBlank { "19001000" } ?: "19001000"
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            startActivity(intent)
        }

        // Bước 2: Checklist chọn tất cả
        binding.btnCheckAllDishes.setOnClickListener {
            binding.cbDish1.isChecked = true
            binding.cbDish2.isChecked = true
            binding.cbSealPackage.isChecked = true
            Toast.makeText(this, "✓ Đã kiểm tra đủ tất cả món!", Toast.LENGTH_SHORT).show()
        }

        // Bước 2: Nút xác nhận lấy hàng -> sang Bước 3
        binding.btnConfirmPickDishes.setOnClickListener {
            lifecycleScope.launch {
                firebaseRepository.updateOrderStatus(currentOrderId, Order.Status.DELIVERING)
                Toast.makeText(this@DeliveryTripActivity, "🛵 Xuất phát đi giao hàng!", Toast.LENGTH_SHORT).show()
                updateStepUI(3)
                fetchRealShipperLocation()
            }
        }

        // Bước 3: Dẫn đường đến khách (Mở Google Maps Turn-by-Turn thật)
        binding.btnOpenNavStep3.setOnClickListener {
            val cusName = currentOrder?.customerName ?: "Khách hàng"
            binding.webViewMapStep3.evaluateJavascript("focusCustomer();", null)
            GoogleMapUtils.openGoogleMapsNavigation(this, customerLat, customerLng, cusName)
        }

        // Bước 3: Gọi khách
        binding.btnCallCustomerStep3.setOnClickListener {
            val phone = currentOrder?.customerPhone?.ifBlank { "19001000" } ?: "19001000"
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            startActivity(intent)
        }

        // Bước 3: Nhắn tin khách
        binding.btnMessageCustomerStep3.setOnClickListener {
            val phone = currentOrder?.customerPhone?.ifBlank { "19001000" } ?: "19001000"
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
            startActivity(intent)
        }

        // Bước 3: Nút "Đã đến điểm giao khách" -> sang Bước 4 & mở Camera quét QR
        binding.btnArrivedAtCustomer.setOnClickListener {
            updateStepUI(4)
            checkCameraAndOpenScanner()
        }

        // Bước 4: Nút mở Camera quét mã QR trực tiếp từ app Khách hàng
        binding.btnSimulateScan.setOnClickListener {
            checkCameraAndOpenScanner()
        }
        binding.tvStep4QrIcon.setOnClickListener {
            checkCameraAndOpenScanner()
        }

        // Bước 4: Hoàn tất đơn hàng (bắt buộc phải quét & xác minh mã QR của khách trước)
        binding.btnCompleteTripFinal.setOnClickListener {
            if (!isQrVerified) {
                Toast.makeText(
                    this,
                    "⚠️ Vui lòng quét mã QR trực tiếp từ ứng dụng của khách hàng để xác minh giao đơn!",
                    Toast.LENGTH_LONG
                ).show()
                checkCameraAndOpenScanner()
            } else {
                completeTripOrder()
            }
        }
    }

    private fun checkCameraAndOpenScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showQrScannerDialog()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun markStep4QrVerified(matchedToken: String) {
        isQrVerified = true
        verifiedTokenString = matchedToken
        binding.tvStep4QrIcon.text = "✅\nĐÃ KHỚP QR"
        binding.tvStep4QrIcon.setTextColor(Color.parseColor("#34D399"))
        binding.tvStep4QrStatus.text = "✓ Đã xác minh mã QR khách hàng: $matchedToken"
        binding.tvStep4QrStatus.setTextColor(Color.parseColor("#34D399"))
        binding.btnSimulateScan.text = "✓ Đã Quét Mã QR Hợp Lệ ($matchedToken)"
        binding.btnSimulateScan.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_green)
    }

    private fun updateStepUI(step: Int) {
        currentStep = step

        binding.layoutStep1.visibility = if (step == 1) View.VISIBLE else View.GONE
        binding.layoutStep2.visibility = if (step == 2) View.VISIBLE else View.GONE
        binding.layoutStep3.visibility = if (step == 3) View.VISIBLE else View.GONE
        binding.layoutStep4.visibility = if (step == 4) View.VISIBLE else View.GONE

        val colorPrimary = ContextCompat.getColor(this, R.color.primary)
        val colorGreen = ContextCompat.getColor(this, R.color.brand_green)
        val colorMuted = ContextCompat.getColor(this, R.color.text_secondary)

        // Cập nhật Stepper 2 Chặng theo 4.html
        if (step <= 2) {
            // Chặng 1 đang chạy (Đến quán lấy món)
            binding.step1Badge.setBackgroundResource(R.drawable.bg_badge_orange)
            binding.step1Badge.text = "1"
            binding.step1Text.setTextColor(colorPrimary)

            binding.step2Badge.setBackgroundResource(R.drawable.bg_badge_duty_off)
            binding.step2Badge.text = "2"
            binding.step2Text.setTextColor(colorMuted)
            binding.stepDivider.setBackgroundColor(ContextCompat.getColor(this, R.color.border))

            binding.btnTripCancel.visibility = View.VISIBLE
        } else {
            // Chặng 2 đang chạy (Giao cho khách)
            binding.step1Badge.setBackgroundResource(R.drawable.bg_badge_green)
            binding.step1Badge.text = "✓"
            binding.step1Text.setTextColor(colorGreen)

            binding.step2Badge.setBackgroundResource(if (step == 4) R.drawable.bg_badge_green else R.drawable.bg_badge_orange)
            binding.step2Badge.text = if (step == 4) "✓" else "2"
            binding.step2Text.setTextColor(if (step == 4) colorGreen else colorPrimary)
            binding.stepDivider.setBackgroundColor(colorGreen)

            binding.btnTripCancel.visibility = View.GONE
        }
    }

    /**
     * Mở hộp thoại Camera Quét Mã QR thực tế (ZXing DecoratedBarcodeView)
     * Quét trực tiếp mã QR hiển thị trên màn hình ứng dụng Khách hàng (TrackingActivity).
     */
    private fun showQrScannerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_qr_scanner, null)
        val scannerView = dialogView.findViewById<DecoratedBarcodeView>(R.id.barcodeScannerView)
        val tvExpectedInfo = dialogView.findViewById<TextView>(R.id.tvExpectedOrderInfo)
        val tvScannerStatus = dialogView.findViewById<TextView>(R.id.tvScannerStatus)
        val tvDetectedToken = dialogView.findViewById<TextView>(R.id.tvDetectedToken)
        val btnSwitchCamera = dialogView.findViewById<TextView>(R.id.btnSwitchCamera)
        val btnToggleFlash = dialogView.findViewById<TextView>(R.id.btnToggleFlash)
        val etManualToken = dialogView.findViewById<EditText>(R.id.etManualTokenInput)
        val btnVerifyManual = dialogView.findViewById<MaterialButton>(R.id.btnVerifyManualToken)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btnCloseScanner)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelScan)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirmScannedToken)

        val shortCode = QRCodeUtils.getShortOrderCode(currentOrderId)
        val expectedToken = QRCodeUtils.getDisplayToken(currentOrderId, currentOrder?.deliveryToken ?: "")
        tvExpectedInfo.text = "Đơn cần xác minh: #ORD-$shortCode • Hướng camera vào mã QR trên ứng dụng của khách hàng."

        var isTorchOn = false
        var currentCameraId = 0 // 0: Camera sau, 1: Camera trước
        var lastScannedText = ""
        var lastScanTime = 0L

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Hàm xử lý chung khi đã nhận được chuỗi dữ liệu (từ Camera QR hoặc nhập tay)
        fun handleScannedPayload(rawPayload: String, isFromCamera: Boolean) {
            val verifyResult = QRCodeUtils.verifyOrderQrPayload(
                scannedRaw = rawPayload,
                expectedOrderId = currentOrderId,
                expectedDeliveryToken = currentOrder?.deliveryToken ?: ""
            )

            if (verifyResult.isValid) {
                try {
                    scannerView.pause()
                    window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                        .startTone(ToneGenerator.TONE_PROP_ACK, 200)
                } catch (_: Exception) {}

                tvScannerStatus.text = "✅ ĐÃ QUÉT & XÁC MINH MÃ QR HỢP LỆ!"
                tvScannerStatus.setTextColor(Color.parseColor("#34D399"))
                tvDetectedToken.text = verifyResult.message
                tvDetectedToken.setTextColor(Color.parseColor("#059669"))

                btnConfirm.isEnabled = true
                btnConfirm.text = "✓ Hoàn Tất Giao Đơn #ORD-$shortCode"
                markStep4QrVerified(verifyResult.matchedToken)

                if (isFromCamera) {
                    Toast.makeText(
                        this,
                        "🎉 Quét mã QR khách hàng thành công! Đang hoàn tất đơn hàng...",
                        Toast.LENGTH_SHORT
                    ).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (dialog.isShowing) {
                            dialog.dismiss()
                            completeTripOrder()
                        }
                    }, 750)
                }
            } else {
                try {
                    window.decorView.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                        .startTone(ToneGenerator.TONE_PROP_NACK, 220)
                } catch (_: Exception) {}

                tvScannerStatus.text = "❌ MÃ QR KHÔNG KHỚP ĐƠN #ORD-$shortCode"
                tvScannerStatus.setTextColor(Color.parseColor("#F87171"))
                tvDetectedToken.text = verifyResult.message
                tvDetectedToken.setTextColor(Color.parseColor("#DC2626"))
            }
        }

        // Cấu hình Camera ZXing DecoratedBarcodeView
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCameraPermission) {
            activeScannerView = scannerView
            scannerView.setStatusText("")
            scannerView.barcodeView.decoderFactory = DefaultDecoderFactory(
                listOf(BarcodeFormat.QR_CODE)
            )
            scannerView.barcodeView.cameraSettings.requestedCameraId = currentCameraId
            scannerView.barcodeView.cameraSettings.isAutoFocusEnabled = true
            scannerView.resume()

            scannerView.decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult?) {
                    val text = result?.text?.trim().orEmpty()
                    if (text.isBlank()) return
                    val now = System.currentTimeMillis()
                    if (text == lastScannedText && (now - lastScanTime) < 2000L) {
                        return
                    }
                    lastScannedText = text
                    lastScanTime = now
                    handleScannedPayload(text, isFromCamera = true)
                }

                override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
            })
        } else {
            tvScannerStatus.text = "⚠️ Chưa cấp quyền Camera — Vui lòng cấp quyền hoặc nhập mã bên dưới"
            tvScannerStatus.setTextColor(Color.parseColor("#FBBF24"))
        }

        // Nút đổi Camera Trước / Sau (Hữu ích khi quét qua Webcam máy tính hoặc điện thoại)
        btnSwitchCamera.setOnClickListener {
            if (!hasCameraPermission) return@setOnClickListener
            currentCameraId = if (currentCameraId == 0) 1 else 0
            scannerView.pause()
            scannerView.barcodeView.cameraSettings.requestedCameraId = currentCameraId
            scannerView.resume()
            val camName = if (currentCameraId == 0) "Camera sau" else "Camera trước"
            tvScannerStatus.text = "● Đang quét bằng $camName..."
            tvScannerStatus.setTextColor(Color.parseColor("#34D399"))
        }

        // Nút Bật / Tắt Đèn Flash
        btnToggleFlash.setOnClickListener {
            if (!hasCameraPermission) return@setOnClickListener
            try {
                isTorchOn = !isTorchOn
                if (isTorchOn) {
                    scannerView.setTorchOn()
                    btnToggleFlash.text = "🔦 Tắt Flash"
                } else {
                    scannerView.setTorchOff()
                    btnToggleFlash.text = "🔦 Flash"
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Thiết bị không hỗ trợ đèn Flash", Toast.LENGTH_SHORT).show()
            }
        }

        // Nút kiểm tra mã Token / PIN nhập thủ công
        btnVerifyManual.setOnClickListener {
            val manualInput = etManualToken.text.toString().trim()
            if (manualInput.isBlank()) {
                Toast.makeText(
                    this,
                    "Vui lòng nhập mã Token (VD: $expectedToken) hiển thị trên máy khách hàng!",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            handleScannedPayload(manualInput, isFromCamera = false)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            if (isQrVerified) {
                dialog.dismiss()
                completeTripOrder()
            }
        }

        dialog.setOnDismissListener {
            try {
                scannerView.pause()
            } catch (_: Exception) {}
            activeScannerView = null
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun completeTripOrder() {
        lifecycleScope.launch {
            try {
                firebaseRepository.updateOrderStatus(currentOrderId, Order.Status.COMPLETED)
                firebaseRepository.clearTracking(currentOrderId)

                val shortCode = QRCodeUtils.getShortOrderCode(currentOrderId)
                val tokenInfo = verifiedTokenString.ifBlank {
                    QRCodeUtils.getDisplayToken(currentOrderId, currentOrder?.deliveryToken ?: "")
                }

                AlertDialog.Builder(this@DeliveryTripActivity)
                    .setTitle("🎉 Giao Hàng & Đối Soát QR Thành Công!")
                    .setMessage(
                        "Đơn hàng #ORD-$shortCode đã được xác minh mã QR ($tokenInfo) và hoàn tất thành công.\n\n" +
                            "Ứng dụng của Khách hàng đã tự động chuyển sang trạng thái 'Đã nhận hàng'.\n" +
                            "+25.000 đ tiền công ship đã được cộng vào Ví Thu Nhập của bạn!"
                    )
                    .setPositiveButton("Về Trang Chủ") { _, _ ->
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@DeliveryTripActivity, "Lỗi cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCancelOrderDialog() {
        val dialogBinding = DialogCancelOrderReasonBinding.inflate(layoutInflater)
        val shortCode = QRCodeUtils.getShortOrderCode(currentOrderId)
        dialogBinding.tvCancelDialogDesc.text = "Bạn đang hủy nhận đơn #ORD-$shortCode khi chưa lấy hàng. Đơn này sẽ được tự động hoàn trả về danh sách chờ của Quán để tài xế khác nhận."

        val reasons = arrayOf(
            "Xe gặp sự cố hỏng hóc dọc đường (Thủng lốp, hỏng xe)",
            "Quán chuẩn bị quá lâu",
            "Bị kẹt xe hoặc sự cố giao thông",
            "Lý do cá nhân đột xuất"
        )
        dialogBinding.spinnerCancelReason.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, reasons
        )

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelDialogBack.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnCancelDialogConfirm.setOnClickListener {
            val selectedReason = dialogBinding.spinnerCancelReason.selectedItem.toString()
            firebaseRepository.unassignShipper(currentOrderId) { success, msg ->
                if (success) {
                    Toast.makeText(this, "Đã hủy đơn thành công: $selectedReason", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    finish()
                } else {
                    Toast.makeText(this, "Lỗi hủy đơn: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        try {
            activeScannerView?.resume()
        } catch (_: Exception) {}
    }

    override fun onPause() {
        try {
            activeScannerView?.pause()
        } catch (_: Exception) {}
        super.onPause()
    }
}
