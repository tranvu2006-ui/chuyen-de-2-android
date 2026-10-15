package com.chuyen_de_2.foodorder.internal.ui.ship

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.GoogleMapUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.ActivityDeliveryTripBinding
import com.chuyen_de_2.foodorder.internal.databinding.DialogCancelOrderReasonBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
                                Toast.makeText(this, "✓ ĐÃ ĐẾN QUÁN TRÀ SỮA NGÔ GIA!", Toast.LENGTH_SHORT).show()
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
                    currentOrder = order
                    bindOrderData(order)
                }
            }
        }
    }

    private fun bindOrderData(order: Order) {
        val shortCode = if (order.orderId.length > 5) order.orderId.takeLast(6).uppercase() else order.orderId
        binding.tvTripOrderCode.text = "Đơn #ORD-$shortCode"

        val storeName = order.shopName.ifBlank { "Trà Sữa Ngô Gia (Q.Thủ Đức)" }
        binding.tvStep1StoreName.text = storeName
        binding.tvTripStoreBadge.text = storeName

        val cusName = order.customerName.ifBlank { "An Nguyễn" }
        binding.tvStep3CustomerName.text = cusName

        val dest = order.address.ifBlank { "104 Hai Bà Trưng, P. Đa Kao, Q.1" }
        binding.tvStep3DeliveryAddress.text = "📍 Địa chỉ: $dest"

        val fee = if (order.shippingFee > 0) order.shippingFee else 25000L
        val totalPayment = if (order.totalPayment > 0) order.totalPayment else (order.total + fee)

        val isCod = order.paymentMethod.equals("COD", ignoreCase = true)
        if (isCod) {
            binding.tvStep3PaymentMethodBadge.text = "💵 Thu tiền mặt COD: ${totalPayment.toVndCurrency()}"
            binding.tvStep4CodCollection.text = "Thu Tiền Mặt COD: ${totalPayment.toVndCurrency()}"
        } else {
            binding.tvStep3PaymentMethodBadge.text = "✓ ĐÃ THANH TOÁN ONLINE (MOMO/VNPAY) - KHÔNG THU TIỀN"
            binding.tvStep4CodCollection.text = "ĐÃ THANH TOÁN TRƯỚC (COD: 0 đ)"
        }

        binding.tvStep4Summary.text = "Khách hàng: $cusName • 0938 112 233\nĐịa chỉ: $dest"

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
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:02838229999"))
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
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0938112233"))
            startActivity(intent)
        }

        // Bước 3: Nhắn tin khách
        binding.btnMessageCustomerStep3.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:0938112233"))
            startActivity(intent)
        }

        // Bước 3: Nút "Đã đến điểm giao khách" -> sang Bước 4
        binding.btnArrivedAtCustomer.setOnClickListener {
            updateStepUI(4)
        }

        // Bước 4: Nút quét mã QR
        binding.btnSimulateScan.setOnClickListener {
            Toast.makeText(this, "✓ Quét mã QR thành công! Đã khớp mã đơn của khách hàng", Toast.LENGTH_SHORT).show()
        }

        // Bước 4: Hoàn tất đơn hàng
        binding.btnCompleteTripFinal.setOnClickListener {
            completeTripOrder()
        }
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

    private fun completeTripOrder() {
        lifecycleScope.launch {
            try {
                firebaseRepository.updateOrderStatus(currentOrderId, Order.Status.COMPLETED)
                firebaseRepository.clearTracking(currentOrderId)

                AlertDialog.Builder(this@DeliveryTripActivity)
                    .setTitle("🎉 Giao Hàng Thành Công!")
                    .setMessage("Đơn hàng đã được hoàn tất thành công.\n\n+25.000 đ tiền công ship đã được cộng vào Ví Thu Nhập của bạn!")
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
        val shortCode = if (currentOrderId.length > 5) currentOrderId.takeLast(6).uppercase() else currentOrderId
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
}
