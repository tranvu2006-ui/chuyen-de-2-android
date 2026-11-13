package com.chuyen_de_2.foodorder.internal.ui.ship

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.CheckBox
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

/**
 * Tên chức năng: Màn Hình Chuyến Giao Hàng & Điều Hướng GPS (Shipper Delivery Trip & GPS Navigation)
 * Cách hoạt động:
 *  - Dẫn đường theo thời gian thực (Realtime Route Navigation) từ vị trí tài xế -> Quán lấy hàng -> Điểm giao của Khách hàng qua Leaflet / OSM Map.
 *  - Quản lý quy trình 4 bước giao hàng:
 *      + Bước 1: Tài xế di chuyển tới quán (Bấm quẹt xác nhận 'Đã đến quán').
 *      + Bước 2: Kiểm đếm món ăn tại quán (Checklist các món và quẹt xác nhận 'Đã lấy hàng & Bắt đầu giao').
 *      + Bước 3: Di chuyển tới vị trí khách hàng (Bấm quẹt xác nhận 'Đã đến điểm giao').
 *      + Bước 4: Xác thực giao hàng an toàn (Quét mã QR / Nhập mã Token bảo mật của khách để hoàn tất cuốc).
 *  - Hỗ trợ giao đơn gộp (Bundle / Batched Orders) nhiều điểm khách trên cùng tuyến đường.
 *  - Tích hợp gọi điện cho khách/quán, báo sự cố giao hàng và gửi vị trí GPS liên tục cho khách theo dõi.
 * Luồng hướng tới:
 *  - Hoàn tất đơn hàng -> Cập nhật trạng thái COMPLETED lên Firestore -> Trả về [ShipperFragment] và cộng doanh thu.
 */
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

    // Quản lý Đơn Gộp (5.html & 6.html)
    private var isBundleMode = false
    private val bundleOrders = mutableListOf<Order>()
    private var activeBundleIndex = 0 // 0: Khách 1, 1: Khách 2, 2: Khách 3
    private val completedBundleOrderIds = mutableSetOf<String>()
    private val checkedBundleOrderIds = mutableSetOf<String>()
    private val bomReturnOrders = mutableListOf<Order>()

    // Quản lý đếm ngược cảnh báo mất đơn (Cấu hình bởi Admin: mặc định 15p đầu và 15p sau)
    private var countdownTimer: android.os.CountDownTimer? = null
    private var round1Minutes: Int = 15
    private var round2Minutes: Int = 15
    private var countdownSecondsRemaining = 15 * 60
    private var isCountdownActive = false
    private var pendingIncidentReason = "Khách không nghe máy (Đã gọi 3 cuộc không liên lạc được)"
    private var countdownRound = 1
    private var isRiskReported = false
    private var isRestoringCountdown = false
    private var hasShownRound1ExpirationDialog = false
    private var activeIncidentDialog: AlertDialog? = null

    private val incidentCallProofByOrder = mutableMapOf<String, String>()
    private val incidentMsgProofByOrder = mutableMapOf<String, String>()
    private var onIncidentProofPicked: ((Uri) -> Unit)? = null

    private val pickIncidentProofLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onIncidentProofPicked?.invoke(uri)
        }
    }

    // Tọa độ GPS thực tế của Shipper và Điểm đến
    private var driverLat: Double = 0.0
    private var driverLng: Double = 0.0
    private var storeLat: Double = 0.0
    private var storeLng: Double = 0.0
    private var customerLat: Double = 0.0
    private var customerLng: Double = 0.0
    private var storePhone: String = ""
    private var storeAddress: String = ""

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

    private val pickQrImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            decodeAndVerifyQrFromUri(uri)
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
        com.chuyen_de_2.foodorder.core.util.InsetUtils.initEdgeToEdge(this, true)
        binding = ActivityDeliveryTripBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.chuyen_de_2.foodorder.core.util.InsetUtils.applyToolbarInsets(binding.toolbarTrip, 8)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        currentOrderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: ""
        if (currentOrderId.isBlank()) {
            Toast.makeText(this, "Không tìm thấy mã đơn hàng", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val cachedConfig = getSharedPreferences("delivery_countdown_config", Context.MODE_PRIVATE)
        round1Minutes = cachedConfig.getInt("round1Minutes", 15)
        round2Minutes = cachedConfig.getInt("round2Minutes", 15)

        setupWebViews()
        setupSwipeToConfirm()
        setupSwipeToConfirmStep2()
        setupSwipeToConfirmStep3()
        setupListeners()
        val initialStep = getSavedStep(currentOrderId)
        updateStepUI(initialStep)
        observeOrderDetails()
        observeShipperActiveOrders()
        checkAndRequestLocation()
    }

    private fun setupSwipeToConfirmStep2() {
        val track = binding.swipeTrackStep2
        val thumb = binding.swipeThumbStep2
        val label = binding.tvSwipeLabelStep2
        var startX = 0f
        var isConfirmed = false

        updateStep2ReadinessUI()

        thumb.setOnTouchListener { v, event ->
            if (isConfirmed) return@setOnTouchListener true

            if (!isStep2AllChecked()) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    Toast.makeText(this, "⚠️ Vui lòng tích chọn đủ tất cả các món trước khi quẹt giao hàng!", Toast.LENGTH_SHORT).show()
                }
                return@setOnTouchListener true
            }

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
                        isConfirmed = true
                        thumb.animate()
                            .translationX(maxDrag)
                            .setDuration(120)
                            .withEndAction {
                                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                updateStepUI(3)
                                fetchRealShipperLocation()
                                thumb.translationX = 0f
                                label.alpha = 1f
                                isConfirmed = false
                                if (isBundleMode && bundleOrders.isNotEmpty()) {
                                    Toast.makeText(this@DeliveryTripActivity, "🛵 Đã lấy đủ tất cả ${bundleOrders.size} đơn gộp! Bắt đầu giao chặng 1", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@DeliveryTripActivity, "🛵 Đã lấy hàng thành công! Xuất phát giao hàng", Toast.LENGTH_SHORT).show()
                                }
                                lifecycleScope.launch {
                                    if (isBundleMode && bundleOrders.isNotEmpty()) {
                                        bundleOrders.toList().forEach { o ->
                                            firebaseRepository.updateOrderStatus(o.orderId, Order.Status.DELIVERING)
                                        }
                                    } else {
                                        firebaseRepository.updateOrderStatus(currentOrderId, Order.Status.DELIVERING)
                                    }
                                }
                            }
                            .start()
                    } else {
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

        track.setOnClickListener {
            if (!isStep2AllChecked()) {
                Toast.makeText(this, "⚠️ Vui lòng tích chọn đủ tất cả các món trước khi quẹt!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Gạt nút tròn sang phải để xác nhận đã lấy hàng và xuất phát!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isStep2AllChecked(): Boolean {
        if (isBundleMode && bundleOrders.isNotEmpty()) {
            return bundleOrders.all { checkedBundleOrderIds.contains(it.orderId) }
        } else {
            for (i in 0 until binding.layoutSingleDishesContainer.childCount) {
                val view = binding.layoutSingleDishesContainer.getChildAt(i)
                if (view is CheckBox && !view.isChecked) return false
            }
            return binding.cbSealPackage.isChecked
        }
    }

    private fun updateStep2ReadinessUI() {
        val allChecked = isStep2AllChecked()
        if (allChecked) {
            binding.swipeTrackStep2.alpha = 1f
            binding.tvSwipeLabelStep2.text = "Gạt sang phải ĐÃ LẤY HÀNG & ĐI GIAO ›››"
            binding.tvSwipeLabelStep2.setTextColor(ContextCompat.getColor(this, R.color.primary))
        } else {
            binding.swipeTrackStep2.alpha = 0.55f
            binding.tvSwipeLabelStep2.text = "⚠️ Tích đủ các món để mở khóa quẹt"
            binding.tvSwipeLabelStep2.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun setupSwipeToConfirmStep3() {
        val track = binding.swipeTrackStep3
        val thumb = binding.swipeThumbStep3
        val label = binding.tvSwipeLabelStep3
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
                        isConfirmed = true
                        thumb.animate()
                            .translationX(maxDrag)
                            .setDuration(120)
                            .withEndAction {
                                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
                                targetOrder?.let {
                                    val updatedOrder = it.copy(status = Order.Status.ARRIVED_AT_CUSTOMER)
                                    if (isBundleMode && activeBundleIndex in bundleOrders.indices) {
                                        bundleOrders[activeBundleIndex] = updatedOrder
                                    }
                                    currentOrder = updatedOrder
                                    currentOrderId = it.orderId
                                    saveStep(it.orderId, 4)
                                    lifecycleScope.launch {
                                        firebaseRepository.updateOrderStatus(it.orderId, Order.Status.ARRIVED_AT_CUSTOMER)
                                    }
                                    start15MinCountdown(updatedOrder, "Đang đếm ngược ${round1Minutes} phút thời gian chờ nhận hàng")
                                    updateStepUI(4)
                                    bindCustomerQuickSwitchStep3()
                                    updateBundleStepperUI()
                                }
                                thumb.translationX = 0f
                                label.alpha = 1f
                                isConfirmed = false
                            }
                            .start()
                    } else {
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

        track.setOnClickListener {
            Toast.makeText(this, "Gạt nút tròn sang phải để xác nhận đã đến nhà khách!", Toast.LENGTH_SHORT).show()
        }
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
                    if (driverLat != 0.0 && driverLng != 0.0) {
                        evaluateJavascript("updateDriverLocation($driverLat, $driverLng);", null)
                    }
                    if (storeLat != 0.0 && storeLng != 0.0) {
                        evaluateJavascript("setDestination($storeLat, $storeLng);", null)
                    }
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
                    if (driverLat != 0.0 && driverLng != 0.0) {
                        evaluateJavascript("updateDriverLocation($driverLat, $driverLng);", null)
                    }
                    if (customerLat != 0.0 && customerLng != 0.0) {
                        val name = currentOrder?.customerName ?: "Khách hàng"
                        evaluateJavascript("setDestination($customerLat, $customerLng, '$name');", null)
                    }
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
                                lifecycleScope.launch {
                                    if (isBundleMode && bundleOrders.isNotEmpty()) {
                                        bundleOrders.toList().forEach { o ->
                                            firebaseRepository.updateOrderStatus(o.orderId, Order.Status.ARRIVED_AT_SHOP)
                                        }
                                    } else {
                                        firebaseRepository.updateOrderStatus(currentOrderId, Order.Status.ARRIVED_AT_SHOP)
                                    }
                                }
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

    private fun getSavedStep(orderId: String): Int {
        if (orderId.isBlank()) return 1
        val sp = getSharedPreferences("delivery_trip_state", Context.MODE_PRIVATE)
        return sp.getInt("step_$orderId", 1)
    }

    private fun saveStep(orderId: String, step: Int) {
        if (orderId.isBlank()) return
        val sp = getSharedPreferences("delivery_trip_state", Context.MODE_PRIVATE)
        sp.edit().putInt("step_$orderId", step).apply()
    }

    private fun getEffectiveStepForOrder(order: Order): Int {
        val targetStep = when (order.status) {
            Order.Status.COMPLETED -> 4
            Order.Status.ARRIVED_AT_CUSTOMER -> 4
            Order.Status.DELIVERING -> 3
            Order.Status.ARRIVED_AT_SHOP -> 2
            else -> 1
        }
        val saved = getSavedStep(order.orderId)
        return maxOf(targetStep, saved)
    }

    private fun syncStepFromOrderStatus(status: String) {
        val targetStep = when (status) {
            Order.Status.COMPLETED -> 4
            Order.Status.ARRIVED_AT_CUSTOMER -> 4
            Order.Status.DELIVERING -> 3
            Order.Status.ARRIVED_AT_SHOP -> 2
            else -> 1
        }
        val saved = getSavedStep(currentOrderId)
        val finalStep = maxOf(targetStep, saved)
        if (finalStep != currentStep) {
            updateStepUI(finalStep)
        }
    }

    private var hasShownReturningDialog = false

    private fun observeOrderDetails() {
        lifecycleScope.launch {
            firebaseRepository.observeCountdownConfig().collectLatest { cfg ->
                val changed = (round1Minutes != cfg.round1Minutes || round2Minutes != cfg.round2Minutes)
                round1Minutes = cfg.round1Minutes
                round2Minutes = cfg.round2Minutes

                getSharedPreferences("delivery_countdown_config", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("round1Minutes", round1Minutes)
                    .putInt("round2Minutes", round2Minutes)
                    .apply()

                if (isCountdownActive || currentStep >= 3) {
                    checkAndRestoreActiveCountdown()
                }
            }
        }

        lifecycleScope.launch {
            firebaseRepository.observeOrder(currentOrderId).collectLatest { order ->
                if (order != null) {
                    val isFirstLoad = (currentOrder == null)
                    currentOrder = order
                    if (!isBundleMode) {
                        bindOrderData(order)
                    }
                    if (order.status == Order.Status.RETURNING_TO_STORE) {
                        if (!hasShownReturningDialog) {
                            hasShownReturningDialog = true
                            if (!bomReturnOrders.any { it.orderId == order.orderId }) {
                                bomReturnOrders.add(order)
                            }
                            switchToReturningToStoreScreen(order)
                        }
                    } else {
                        syncStepFromOrderStatus(order.status)
                    }
                    checkAndRestoreActiveCountdown()
                }
            }
        }
    }

    private fun observeShipperActiveOrders() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            firebaseRepository.observeOrdersByShipper(uid).collectLatest { orders ->
                val active = orders.filter { o ->
                    (o.status == Order.Status.ASSIGNED ||
                     o.status == Order.Status.ARRIVED_AT_SHOP ||
                     o.status == Order.Status.DELIVERING ||
                     o.status == Order.Status.ARRIVED_AT_CUSTOMER ||
                     o.status == Order.Status.RETURNING_TO_STORE) &&
                     o.shipperId == uid
                }

                // Chuyến ghép: nếu có >= 2 đơn active hoặc chuyến đi đã là đơn ghép và vẫn còn đơn chưa hoàn tất
                val hasUnfinishedBundle = isBundleMode && bundleOrders.any { !completedBundleOrderIds.contains(it.orderId) }

                if (active.size >= 2 || hasUnfinishedBundle) {
                    isBundleMode = true
                    var needsRebindStep2 = false
                    if (bundleOrders.isEmpty()) {
                        val currentFirst = active.sortedByDescending { it.orderId == currentOrderId }
                        bundleOrders.clear()
                        bundleOrders.addAll(currentFirst)
                        needsRebindStep2 = true
                    } else {
                        // Cập nhật lại thông tin mới nhất của các đơn trong bundle
                        val existingIds = bundleOrders.map { it.orderId }.toSet()
                        val newActive = active.filter { !existingIds.contains(it.orderId) }
                        val activeIds = active.map { it.orderId }.toSet()
                        val hasOrderListChanged = newActive.isNotEmpty() || bundleOrders.any { !activeIds.contains(it.orderId) }

                        val updated = bundleOrders.map { old ->
                            active.find { it.orderId == old.orderId } ?: old
                        }.toMutableList()
                        updated.addAll(newActive)
                        bundleOrders.clear()
                        bundleOrders.addAll(updated)
                        if (hasOrderListChanged) {
                            needsRebindStep2 = true
                        }
                    }

                    // Đồng bộ các đơn đã hoàn tất
                    orders.filter { it.status == Order.Status.COMPLETED }.forEach { done ->
                        if (bundleOrders.any { it.orderId == done.orderId }) {
                            completedBundleOrderIds.add(done.orderId)
                        }
                    }

                    binding.tvTripBundleBadge.visibility = View.VISIBLE
                    binding.tvTripBundleBadge.text = "Ghép ${bundleOrders.size} đơn"

                    if (needsRebindStep2 || binding.layoutBundleCardsContainer.childCount == 0) {
                        bindBundleOrdersStep2()
                    }
                    val currentTarget = bundleOrders.getOrNull(activeBundleIndex)
                    val activeOrder = if (currentTarget != null && !completedBundleOrderIds.contains(currentTarget.orderId)) {
                        currentTarget
                    } else {
                        val firstUnfinished = bundleOrders.firstOrNull { !completedBundleOrderIds.contains(it.orderId) }
                        if (firstUnfinished != null) {
                            activeBundleIndex = bundleOrders.indexOf(firstUnfinished)
                            firstUnfinished
                        } else {
                            currentTarget ?: bundleOrders.firstOrNull() ?: currentOrder
                        }
                    }

                    bindCustomerQuickSwitchStep3()

                    if (activeOrder != null) {
                        currentOrderId = activeOrder.orderId
                        currentOrder = activeOrder
                        bindOrderData(activeOrder)
                    }

                    if (currentStep >= 3) {
                        val effStep = if (activeOrder != null) maxOf(3, getEffectiveStepForOrder(activeOrder)) else currentStep
                        updateStepUI(effStep)
                    }
                    checkAndRestoreActiveCountdown()
                } else if (!isBundleMode) {
                    isBundleMode = false
                    binding.tvTripBundleBadge.visibility = View.GONE
                    binding.layoutTripStepperBundle.visibility = View.GONE
                    binding.layoutTripStepper.visibility = View.VISIBLE
                    binding.cardShopBundleMetrics.visibility = View.GONE
                    binding.layoutBundleCardsContainer.visibility = View.GONE
                    binding.cardSingleChecklist.visibility = View.VISIBLE
                    binding.layoutCustomerQuickSwitch.visibility = View.GONE
                    currentOrder?.let { bindOrderData(it) }
                }
            }
        }
    }

    private fun bindOrderData(order: Order) {
        val shortCode = QRCodeUtils.getShortOrderCode(order.orderId.ifBlank { currentOrderId })
        if (!isBundleMode) {
            binding.tvTripOrderCode.text = "Đơn #${shortCode}"
            binding.tvTripBundleBadge.visibility = View.GONE
            binding.tvStep1OrderCode.text = "ĐƠN CẦN LẤY: #${shortCode}"
            binding.tvStep2SingleOrderCode.text = "MÃ ĐƠN: #${shortCode}"
        } else {
            val idx = bundleOrders.indexOfFirst { it.orderId == order.orderId }
            val ch = if (idx >= 0) idx + 1 else 1
            binding.tvTripOrderCode.text = "#${shortCode} (Chặng ${ch}/${bundleOrders.size})"
            binding.tvTripBundleBadge.visibility = View.VISIBLE
            binding.tvTripBundleBadge.text = "Ghép ${bundleOrders.size} đơn"

            val allCodes = bundleOrders.joinToString(", ") { "#" + QRCodeUtils.getShortOrderCode(it.orderId) }
            binding.tvStep1OrderCode.text = "CẦN LẤY ${bundleOrders.size} ĐƠN: ${allCodes}"
            binding.tvStep2SingleOrderCode.text = "MÃ ĐƠN: #${shortCode}"
        }

        // Đổ danh sách món thật vào checklist bước 2
        binding.layoutSingleDishesContainer.removeAllViews()
        binding.cbSealPackage.isChecked = false
        if (order.items.isNotEmpty()) {
            order.items.values.forEach { item ->
                val cb = CheckBox(this).apply {
                    text = "• ${item.quantity}x ${item.dishName}"
                    setTextColor(Color.parseColor("#1E293B"))
                    textSize = 12.5f
                    isChecked = false
                    setOnCheckedChangeListener { _, _ -> updateStep2ReadinessUI() }
                }
                binding.layoutSingleDishesContainer.addView(cb)
            }
        } else {
            val cb = CheckBox(this).apply {
                text = "• Món ăn theo đơn đặt của khách"
                setTextColor(Color.parseColor("#1E293B"))
                textSize = 12.5f
                isChecked = false
                setOnCheckedChangeListener { _, _ -> updateStep2ReadinessUI() }
            }
            binding.layoutSingleDishesContainer.addView(cb)
        }
        binding.cbSealPackage.setOnCheckedChangeListener { _, _ -> updateStep2ReadinessUI() }
        updateStep2ReadinessUI()

        binding.tvStep3OrderCode.text = "ĐANG GIAO: #${shortCode}"
        binding.tvStep4OrderCode.text = "XÁC MINH ĐƠN HÀNG: #${shortCode}"

        val storeName = order.shopName.ifBlank { "Quán ăn" }
        binding.tvStep1StoreName.text = storeName
        binding.tvTripStoreBadge.text = storeName

        val targetStoreId = order.effectiveStoreId
        if (targetStoreId.isNotBlank()) {
            lifecycleScope.launch {
                try {
                    val store = firebaseRepository.getStoreOnce(targetStoreId)
                    if (store != null) {
                        if (store.phone.isNotBlank()) {
                            storePhone = store.phone.trim()
                        }
                        if (store.address.isNotBlank()) {
                            storeAddress = store.address.trim()
                        }
                        if (store.latitude != 0.0 && store.longitude != 0.0) {
                            storeLat = store.latitude
                            storeLng = store.longitude
                        } else if (store.address.isNotBlank()) {
                            val coords = GoogleMapUtils.geocodeAddress(this@DeliveryTripActivity, store.address)
                            storeLat = coords.latitude
                            storeLng = coords.longitude
                        }
                        if (storeLat != 0.0 && storeLng != 0.0) {
                            binding.webViewMapStep1.evaluateJavascript("setDestination($storeLat, $storeLng);", null)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val cusName = order.customerName.ifBlank { "Khách hàng" }
        binding.tvStep3CustomerName.text = cusName
        binding.tvStep3CustomerPhone.text = order.customerPhone.ifBlank { "Chưa cập nhật SĐT" }

        val dest = order.address.ifBlank { "Địa chỉ giao hàng" }
        binding.tvStep3DeliveryAddress.text = "📍 Địa chỉ: ${dest}"

        if (order.note.isNotBlank()) {
            binding.tvStep3CustomerNote.visibility = View.VISIBLE
            binding.tvStep3CustomerNote.text = "📝 Ghi chú: \"${order.note}\""
        } else {
            binding.tvStep3CustomerNote.visibility = View.GONE
        }

        val fee = order.shippingFee
        val totalPayment = if (order.totalPayment > 0) order.totalPayment else (order.total + fee)

        val isCod = order.paymentMethod.equals("COD", ignoreCase = true)
        val isDepositPaid = order.paymentMode == Order.PaymentMode.DEPOSIT &&
            (order.paymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true) ||
                order.isDepositPaid)
        if (isDepositPaid) {
            binding.tvStep3PaymentMethodBadge.text =
                "⚡ ĐÃ CỌC ${order.depositAmount.toVndCurrency()} • CÒN THU ${order.remainAmount.toVndCurrency()} KHI GIAO"
            binding.tvStep4CodCollection.text =
                "Thu phần còn lại sau cọc: ${order.remainAmount.toVndCurrency()}"
        } else if (isCod) {
            binding.tvStep3PaymentMethodBadge.text = "💵 Thu tiền mặt COD: ${totalPayment.toVndCurrency()}"
            binding.tvStep4CodCollection.text = "Thu Tiền Mặt COD: ${totalPayment.toVndCurrency()}"
        } else if (order.paymentMode == Order.PaymentMode.DEPOSIT) {
            binding.tvStep3PaymentMethodBadge.text = "⚠️ Chưa xác nhận tiền cọc"
            binding.tvStep4CodCollection.text = "Kiểm tra thanh toán cọc trước khi giao"
        } else if (isOnlinePaymentConfirmed(order)) {
            binding.tvStep3PaymentMethodBadge.text = "✓ ĐÃ THANH TOÁN ONLINE (VIETQR/MOMO) - KHÔNG THU TIỀN"
            binding.tvStep4CodCollection.text = "ĐÃ THANH TOÁN TRƯỚC (COD: 0 đ)"
        } else {
            binding.tvStep3PaymentMethodBadge.text = "⚠️ Chưa xác nhận thanh toán online"
            binding.tvStep4CodCollection.text = "Kiểm tra thanh toán trước khi giao"
        }

        val phoneDisplay = if (order.customerPhone.isNotBlank()) order.customerPhone else "Khách hàng"
        binding.tvStep4Summary.text = "Đơn #${shortCode}\nKhách: ${cusName} • ${phoneDisplay}\nĐịa chỉ: ${dest}"
        binding.tvStep4PhoneSub.text = if (order.customerPhone.isNotBlank()) order.customerPhone else "Bấm gọi ngay"

        // Banner nhắc nhở giữ món bom đơn trước nếu có (8.html)
        if (bomReturnOrders.isNotEmpty()) {
            val bomOrder = bomReturnOrders.first()
            val bomSc = QRCodeUtils.getShortOrderCode(bomOrder.orderId)
            val bomRefund = if (bomOrder.subtotalAmount > 0) bomOrder.subtotalAmount else (bomOrder.effectiveTotal - bomOrder.shippingFee).coerceAtLeast(0) + 25000L
            binding.boxBomOnBikeReminder.visibility = View.VISIBLE
            binding.tvBomOnBikeReminderText.text = "📦 Lưu ý trên xe: Đang giữ món ăn của đơn bom #${bomSc}. Giao xong đơn này bạn sẽ quay về Quán trả món và nhận hoàn ${bomRefund.toVndCurrency()}!"
        } else {
            binding.boxBomOnBikeReminder.visibility = View.GONE
        }

        binding.tvStep3NavInstruction.text = "Điểm đến: ${dest}"
        binding.tvStep3NavSubtext.text = "Giao cho: ${cusName} (#${shortCode})"

        // Geocoding địa chỉ khách thực tế để vẽ lộ trình thật
        lifecycleScope.launch {
            try {
                val coords = GoogleMapUtils.geocodeAddress(this@DeliveryTripActivity, dest)
                customerLat = coords.latitude
                customerLng = coords.longitude
                if (customerLat != 0.0 && customerLng != 0.0) {
                    binding.webViewMapStep3.evaluateJavascript("setDestination($customerLat, $customerLng, '$cusName');", null)
                }
            } catch (e: Exception) {
                // Giữ nguyên tọa độ nếu lỗi mạng
            }
        }
    }

    private fun isOnlinePaymentConfirmed(order: Order): Boolean =
        order.paymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true) ||
            order.isPaid || order.isDepositPaid

    private fun amountToCollectFromCustomer(order: Order): Long = when {
        order.paymentMethod.equals(Order.PaymentMethod.COD, ignoreCase = true) ->
            order.totalPayment.takeIf { it > 0L } ?: (order.total + order.shippingFee)
        order.paymentMode == Order.PaymentMode.DEPOSIT && isOnlinePaymentConfirmed(order) ->
            order.remainAmount
        else -> 0L
    }

    private fun collectionLabel(order: Order, amount: Long): String = when {
        amount > 0L -> "Thu khi giao ${amount.toVndCurrency()}"
        !order.paymentMethod.equals(Order.PaymentMethod.COD, ignoreCase = true) &&
            !isOnlinePaymentConfirmed(order) -> "⚠ Chưa xác nhận thanh toán"
        else -> "0 đ (đã trả đủ)"
    }

    /**
     * Nạp dữ liệu và tạo giao diện danh sách Đơn Gộp tại Quán (5.html: list-order-card)
     */
    private fun bindBundleOrdersStep2() {
        if (!isBundleMode || bundleOrders.isEmpty()) return

        binding.cardShopBundleMetrics.visibility = View.VISIBLE
        binding.layoutBundleCardsContainer.visibility = View.VISIBLE
        binding.cardSingleChecklist.visibility = View.GONE

        val firstOrder = bundleOrders.firstOrNull()
        binding.tvStep2ShopName.text = firstOrder?.shopName?.ifBlank { "Quán ăn Oishi Food" } ?: "Quán ăn Oishi Food"

        val totalFee = bundleOrders.sumOf { if (it.shippingFee > 0) it.shippingFee else 25000L }
        val totalCod = bundleOrders.sumOf(::amountToCollectFromCustomer)

        binding.tvBundleTotalFee.text = "+${totalFee.toVndCurrency()}"
        binding.tvBundleOrderCount.text = "${bundleOrders.size} đơn"
        binding.tvBundleTotalCod.text = totalCod.toVndCurrency()

        // Xóa danh sách cũ và render động từng card đơn
        binding.layoutBundleCardsContainer.removeAllViews()
        bundleOrders.forEachIndexed { index, order ->
            val cardBinding = com.chuyen_de_2.foodorder.internal.databinding.ItemBundleOrderCardBinding.inflate(
                layoutInflater, binding.layoutBundleCardsContainer, false
            )

            val shortCode = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.getShortOrderCode(order.orderId)
            cardBinding.tvBundleOrderCode.text = "#${shortCode}"
            cardBinding.tvBundleSequenceTag.text = "Chặng ${index + 1}"

            val fee = if (order.shippingFee > 0) order.shippingFee else 25000L
            val amountToCollect = amountToCollectFromCustomer(order)
            if (amountToCollect > 0L) {
                cardBinding.tvBundlePaymentBadge.text =
                    "💵 Thu khi giao: ${amountToCollect.toVndCurrency()}"
                cardBinding.tvBundlePaymentBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                cardBinding.tvBundlePaymentBadge.setTextColor(ContextCompat.getColor(this, R.color.primary))
            } else if (!isOnlinePaymentConfirmed(order) &&
                !order.paymentMethod.equals(Order.PaymentMethod.COD, ignoreCase = true)
            ) {
                cardBinding.tvBundlePaymentBadge.text = "⚠️ Chưa xác nhận thanh toán"
                cardBinding.tvBundlePaymentBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                cardBinding.tvBundlePaymentBadge.setTextColor(ContextCompat.getColor(this, R.color.primary))
            } else {
                cardBinding.tvBundlePaymentBadge.text = "✓ Đã thanh toán đủ trước"
                cardBinding.tvBundlePaymentBadge.setBackgroundResource(R.drawable.bg_badge_green)
                cardBinding.tvBundlePaymentBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
            }

            cardBinding.tvBundleOrderFee.text = fee.toVndCurrency()

            // Nút đổi thứ tự ưu tiên
            if (index == 0) {
                cardBinding.btnTogglePriority.text = "⚡ Ưu tiên giao trước (1)"
                cardBinding.btnTogglePriority.setBackgroundResource(R.drawable.bg_priority_active)
            } else {
                cardBinding.btnTogglePriority.text = "🔄 Đổi lên giao trước (${index + 1})"
                cardBinding.btnTogglePriority.setBackgroundResource(R.drawable.bg_badge_duty_off)
            }

            cardBinding.btnTogglePriority.setOnClickListener {
                if (index > 0 && index < bundleOrders.size) {
                    val selectedOrder = bundleOrders.removeAt(index)
                    bundleOrders.add(0, selectedOrder)
                    activeBundleIndex = 0
                    bindBundleOrdersStep2()
                    bindCustomerQuickSwitchStep3()
                    Toast.makeText(this, "⚡ Đã đưa đơn của ${selectedOrder.customerName.ifBlank { "khách" }} lên giao trước!", Toast.LENGTH_SHORT).show()
                }
            }

            // Thông tin khách
            val cusName = order.customerName.ifBlank { "Khách hàng" }
            cardBinding.tvBundleCustomerName.text = "Khách: ${cusName}"
            cardBinding.tvBundleDeliveryAddress.text = "📍 ${order.address.ifBlank { "Địa chỉ giao hàng" }}"

            cardBinding.btnCallBundleCustomer.setOnClickListener {
                val phone = order.customerPhone
                if (phone.isNotBlank()) {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                } else {
                    Toast.makeText(this@DeliveryTripActivity, "Khách hàng chưa cập nhật số điện thoại", Toast.LENGTH_SHORT).show()
                }
            }

            // Danh sách món ăn
            if (order.items.isNotEmpty()) {
                val dishText = order.items.values.joinToString("\n") {
                    "• ${it.quantity}x ${it.dishName}"
                }
                cardBinding.tvBundleDishListText.text = dishText
            } else {
                cardBinding.tvBundleDishListText.text = "• Món ăn theo đơn đặt của khách"
            }

            // Ghi chú
            if (order.note.isNotBlank()) {
                cardBinding.tvBundleCustomerNote.visibility = View.VISIBLE
                cardBinding.tvBundleCustomerNote.text = "📝 Ghi chú: \"${order.note}\""
            } else {
                cardBinding.tvBundleCustomerNote.visibility = View.GONE
            }

            cardBinding.cbOrderReady.isChecked = checkedBundleOrderIds.contains(order.orderId)
            cardBinding.cbOrderReady.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkedBundleOrderIds.add(order.orderId)
                    Toast.makeText(this, "✓ Đã kiểm đủ món đơn #${shortCode}", Toast.LENGTH_SHORT).show()
                } else {
                    checkedBundleOrderIds.remove(order.orderId)
                }
                updateStep2ReadinessUI()
            }

            binding.layoutBundleCardsContainer.addView(cardBinding.root)
        }
        updateStep2ReadinessUI()
    }

    /**
     * Nạp dữ liệu và điều khiển Thẻ Chuyển Đổi Khách Nhanh tại Bước 3 (6.html: customer-quick-switch)
     */
    private fun bindCustomerQuickSwitchStep3() {
        if (!isBundleMode || bundleOrders.isEmpty()) {
            binding.layoutCustomerQuickSwitch.visibility = View.GONE
            return
        }

        binding.layoutCustomerQuickSwitch.visibility = View.VISIBLE

        val order1 = bundleOrders.getOrNull(0)
        val order2 = bundleOrders.getOrNull(1)
        val order3 = bundleOrders.getOrNull(2)

        // Khách 1
        if (order1 != null) {
            val sc1 = QRCodeUtils.getShortOrderCode(order1.orderId)
            binding.choiceName1.text = "1. ${order1.customerName.ifBlank { "Khách 1" }} (#${sc1})"
            binding.choiceAddr1.text = order1.address.ifBlank { "Địa chỉ 1" }
            val collect1 = amountToCollectFromCustomer(order1)
            binding.choicePayment1.text = collectionLabel(order1, collect1)
            binding.choicePayment1.setTextColor(if (collect1 > 0) ContextCompat.getColor(this, R.color.primary) else ContextCompat.getColor(this, R.color.brand_green))

            val isDone1 = completedBundleOrderIds.contains(order1.orderId)
            val isReturning1 = order1.status == Order.Status.RETURNING_TO_STORE
            val isArrived1 = order1.status == Order.Status.ARRIVED_AT_CUSTOMER || getSavedStep(order1.orderId) >= 4
            if (isDone1) {
                binding.choiceBadge1.text = "✓ Đã giao"
                binding.choiceBadge1.setBackgroundResource(R.drawable.bg_badge_green)
                binding.choiceBadge1.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
                binding.btnChoiceCust1.setBackgroundResource(R.drawable.bg_choice_card_idle)
            } else if (isReturning1) {
                binding.choiceBadge1.text = "🚨 Khách hủy (Về quán)"
                binding.choiceBadge1.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.choiceBadge1.setTextColor(Color.parseColor("#DC2626"))
                binding.btnChoiceCust1.setBackgroundResource(if (activeBundleIndex == 0) R.drawable.bg_choice_card_active else R.drawable.bg_choice_card_idle)
            } else if (activeBundleIndex == 0) {
                binding.choiceBadge1.text = if (isArrived1) "📍 Đã đến nhà khách" else "⚡ Đang giao"
                binding.choiceBadge1.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.choiceBadge1.setTextColor(ContextCompat.getColor(this, R.color.primary))
                binding.btnChoiceCust1.setBackgroundResource(R.drawable.bg_choice_card_active)
            } else if (isArrived1) {
                binding.choiceBadge1.text = "📍 Đã đến nhà khách"
                binding.choiceBadge1.setBackgroundResource(R.drawable.bg_badge_blue)
                binding.choiceBadge1.setTextColor(Color.parseColor("#1D4ED8"))
                binding.btnChoiceCust1.setBackgroundResource(R.drawable.bg_choice_card_idle)
            } else {
                binding.choiceBadge1.text = "Giao sau"
                binding.choiceBadge1.setBackgroundResource(R.drawable.bg_badge_duty_off)
                binding.choiceBadge1.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                binding.btnChoiceCust1.setBackgroundResource(R.drawable.bg_choice_card_idle)
            }

            binding.btnChoiceCust1.setOnClickListener {
                switchActiveBundleCustomer(0)
            }
            binding.stepBundleCust1.setOnClickListener {
                switchActiveBundleCustomer(0)
            }
        }

        // Khách 2
        if (order2 != null) {
            val sc2 = QRCodeUtils.getShortOrderCode(order2.orderId)
            binding.choiceName2.text = "2. ${order2.customerName.ifBlank { "Khách 2" }} (#${sc2})"
            binding.choiceAddr2.text = order2.address.ifBlank { "Địa chỉ 2" }
            val collect2 = amountToCollectFromCustomer(order2)
            binding.choicePayment2.text = collectionLabel(order2, collect2)
            binding.choicePayment2.setTextColor(if (collect2 > 0) ContextCompat.getColor(this, R.color.primary) else ContextCompat.getColor(this, R.color.brand_green))

            val isDone2 = completedBundleOrderIds.contains(order2.orderId)
            val isReturning2 = order2.status == Order.Status.RETURNING_TO_STORE
            val isArrived2 = order2.status == Order.Status.ARRIVED_AT_CUSTOMER || getSavedStep(order2.orderId) >= 4
            if (isDone2) {
                binding.choiceBadge2.text = "✓ Đã giao"
                binding.choiceBadge2.setBackgroundResource(R.drawable.bg_badge_green)
                binding.choiceBadge2.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
                binding.btnChoiceCust2.setBackgroundResource(R.drawable.bg_choice_card_idle)
            } else if (isReturning2) {
                binding.choiceBadge2.text = "🚨 Khách hủy (Về quán)"
                binding.choiceBadge2.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.choiceBadge2.setTextColor(Color.parseColor("#DC2626"))
                binding.btnChoiceCust2.setBackgroundResource(if (activeBundleIndex == 1) R.drawable.bg_choice_card_active else R.drawable.bg_choice_card_idle)
            } else if (activeBundleIndex == 1) {
                binding.choiceBadge2.text = if (isArrived2) "📍 Đã đến nhà khách" else "⚡ Đang giao"
                binding.choiceBadge2.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.choiceBadge2.setTextColor(ContextCompat.getColor(this, R.color.primary))
                binding.btnChoiceCust2.setBackgroundResource(R.drawable.bg_choice_card_active)
            } else if (isArrived2) {
                binding.choiceBadge2.text = "📍 Đã đến nhà khách"
                binding.choiceBadge2.setBackgroundResource(R.drawable.bg_badge_blue)
                binding.choiceBadge2.setTextColor(Color.parseColor("#1D4ED8"))
                binding.btnChoiceCust2.setBackgroundResource(R.drawable.bg_choice_card_idle)
            } else {
                binding.choiceBadge2.text = "Giao sau"
                binding.choiceBadge2.setBackgroundResource(R.drawable.bg_badge_duty_off)
                binding.choiceBadge2.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                binding.btnChoiceCust2.setBackgroundResource(R.drawable.bg_choice_card_idle)
            }

            binding.btnChoiceCust2.setOnClickListener {
                switchActiveBundleCustomer(1)
            }
            binding.stepBundleCust2.setOnClickListener {
                switchActiveBundleCustomer(1)
            }
        }

        // Khách 3 (Ghép 3 đơn)
        if (order3 != null) {
            val sc3 = QRCodeUtils.getShortOrderCode(order3.orderId)
            binding.btnChoiceCust3.visibility = View.VISIBLE
            binding.choiceName3.text = "3. ${order3.customerName.ifBlank { "Khách 3" }} (#${sc3})"
            binding.choiceAddr3.text = order3.address.ifBlank { "Địa chỉ 3" }
            val collect3 = amountToCollectFromCustomer(order3)
            binding.choicePayment3.text = collectionLabel(order3, collect3)
            binding.choicePayment3.setTextColor(if (collect3 > 0) ContextCompat.getColor(this, R.color.primary) else ContextCompat.getColor(this, R.color.brand_green))

            val isDone3 = completedBundleOrderIds.contains(order3.orderId)
            val isReturning3 = order3.status == Order.Status.RETURNING_TO_STORE
            val isArrived3 = order3.status == Order.Status.ARRIVED_AT_CUSTOMER || getSavedStep(order3.orderId) >= 4
            if (isDone3) {
                binding.choiceBadge3.text = "✓ Đã giao"
                binding.choiceBadge3.setBackgroundResource(R.drawable.bg_badge_green)
                binding.choiceBadge3.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
                binding.btnChoiceCust3.setBackgroundResource(R.drawable.bg_choice_card_idle)
            } else if (isReturning3) {
                binding.choiceBadge3.text = "🚨 Khách hủy (Về quán)"
                binding.choiceBadge3.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.choiceBadge3.setTextColor(Color.parseColor("#DC2626"))
                binding.btnChoiceCust3.setBackgroundResource(if (activeBundleIndex == 2) R.drawable.bg_choice_card_active else R.drawable.bg_choice_card_idle)
            } else if (activeBundleIndex == 2) {
                binding.choiceBadge3.text = if (isArrived3) "📍 Đã đến nhà khách" else "⚡ Đang giao"
                binding.choiceBadge3.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.choiceBadge3.setTextColor(ContextCompat.getColor(this, R.color.primary))
                binding.btnChoiceCust3.setBackgroundResource(R.drawable.bg_choice_card_active)
            } else if (isArrived3) {
                binding.choiceBadge3.text = "📍 Đã đến nhà khách"
                binding.choiceBadge3.setBackgroundResource(R.drawable.bg_badge_blue)
                binding.choiceBadge3.setTextColor(Color.parseColor("#1D4ED8"))
                binding.btnChoiceCust3.setBackgroundResource(R.drawable.bg_choice_card_idle)
            } else {
                binding.choiceBadge3.text = "Giao sau"
                binding.choiceBadge3.setBackgroundResource(R.drawable.bg_badge_duty_off)
                binding.choiceBadge3.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                binding.btnChoiceCust3.setBackgroundResource(R.drawable.bg_choice_card_idle)
            }

            binding.btnChoiceCust3.setOnClickListener {
                switchActiveBundleCustomer(2)
            }
            binding.stepBundleCust3.setOnClickListener {
                switchActiveBundleCustomer(2)
            }
        } else {
            binding.btnChoiceCust3.visibility = View.GONE
        }

        updateBundleStepperUI()
    }

    /**
     * Cập nhật Stepper 3 Chặng cho Đơn Gộp (6.html: trip-stepper)
     */
    private fun updateBundleStepperUI() {
        if (!isBundleMode) return

        val order1 = bundleOrders.getOrNull(0)
        val order2 = bundleOrders.getOrNull(1)
        val order3 = bundleOrders.getOrNull(2)

        val colorPrimary = ContextCompat.getColor(this, R.color.primary)
        val colorGreen = ContextCompat.getColor(this, R.color.brand_green)
        val colorMuted = ContextCompat.getColor(this, R.color.text_secondary)

        // Tên khách hiển thị trên Stepper kèm mã đơn
        val sc1 = order1?.let { QRCodeUtils.getShortOrderCode(it.orderId) } ?: ""
        val sc2 = order2?.let { QRCodeUtils.getShortOrderCode(it.orderId) } ?: ""
        binding.stepBundleCust1Text.text = if (order1 != null) "${order1.customerName.take(6).ifBlank { "Khách 1" }} (#${sc1})" else "Khách 1"
        binding.stepBundleCust2Text.text = if (order2 != null) "${order2.customerName.take(6).ifBlank { "Khách 2" }} (#${sc2})" else "Khách 2"

        val isDone1 = order1 != null && completedBundleOrderIds.contains(order1.orderId)
        val isArrived1 = order1 != null && (order1.status == Order.Status.ARRIVED_AT_CUSTOMER || getSavedStep(order1.orderId) >= 4)
        val isDone2 = order2 != null && completedBundleOrderIds.contains(order2.orderId)
        val isArrived2 = order2 != null && (order2.status == Order.Status.ARRIVED_AT_CUSTOMER || getSavedStep(order2.orderId) >= 4)

        // Trạng thái Khách 1
        if (isDone1) {
            binding.stepBundleCust1Badge.setBackgroundResource(R.drawable.bg_badge_green)
            binding.stepBundleCust1Badge.text = "✓"
            binding.stepBundleCust1Text.setTextColor(colorGreen)
            binding.stepBundleDivider1.setBackgroundColor(colorGreen)
        } else if (isArrived1) {
            binding.stepBundleCust1Badge.setBackgroundResource(if (activeBundleIndex == 0) R.drawable.bg_badge_orange else R.drawable.bg_badge_blue)
            binding.stepBundleCust1Badge.text = "📍"
            binding.stepBundleCust1Text.setTextColor(if (activeBundleIndex == 0) colorPrimary else Color.parseColor("#1D4ED8"))
        } else if (activeBundleIndex == 0) {
            binding.stepBundleCust1Badge.setBackgroundResource(R.drawable.bg_badge_orange)
            binding.stepBundleCust1Badge.text = "1"
            binding.stepBundleCust1Text.setTextColor(colorPrimary)
        } else {
            binding.stepBundleCust1Badge.setBackgroundResource(R.drawable.bg_badge_duty_off)
            binding.stepBundleCust1Badge.text = "1"
            binding.stepBundleCust1Text.setTextColor(colorMuted)
        }

        // Trạng thái Khách 2
        if (isDone2) {
            binding.stepBundleCust2Badge.setBackgroundResource(R.drawable.bg_badge_green)
            binding.stepBundleCust2Badge.text = "✓"
            binding.stepBundleCust2Text.setTextColor(colorGreen)
            binding.stepBundleDivider2.setBackgroundColor(colorGreen)
        } else if (isArrived2) {
            binding.stepBundleCust2Badge.setBackgroundResource(if (activeBundleIndex == 1) R.drawable.bg_badge_orange else R.drawable.bg_badge_blue)
            binding.stepBundleCust2Badge.text = "📍"
            binding.stepBundleCust2Text.setTextColor(if (activeBundleIndex == 1) colorPrimary else Color.parseColor("#1D4ED8"))
        } else if (activeBundleIndex == 1) {
            binding.stepBundleCust2Badge.setBackgroundResource(R.drawable.bg_badge_orange)
            binding.stepBundleCust2Badge.text = "2"
            binding.stepBundleCust2Text.setTextColor(colorPrimary)
        } else {
            binding.stepBundleCust2Badge.setBackgroundResource(R.drawable.bg_badge_duty_off)
            binding.stepBundleCust2Badge.text = "2"
            binding.stepBundleCust2Text.setTextColor(colorMuted)
        }

        // Trạng thái Khách 3 (Ghép 3 đơn)
        if (order3 != null) {
            val sc3 = QRCodeUtils.getShortOrderCode(order3.orderId)
            binding.stepBundleDivider3.visibility = View.VISIBLE
            binding.stepBundleCust3.visibility = View.VISIBLE
            binding.stepBundleCust3Text.text = "${order3.customerName.take(6).ifBlank { "Khách 3" }} (#${sc3})"

            val isDone3 = completedBundleOrderIds.contains(order3.orderId)
            val isArrived3 = order3.status == Order.Status.ARRIVED_AT_CUSTOMER || getSavedStep(order3.orderId) >= 4
            if (isDone3) {
                binding.stepBundleCust3Badge.setBackgroundResource(R.drawable.bg_badge_green)
                binding.stepBundleCust3Badge.text = "✓"
                binding.stepBundleCust3Text.setTextColor(colorGreen)
                binding.stepBundleDivider3.setBackgroundColor(colorGreen)
            } else if (isArrived3) {
                binding.stepBundleCust3Badge.setBackgroundResource(if (activeBundleIndex == 2) R.drawable.bg_badge_orange else R.drawable.bg_badge_blue)
                binding.stepBundleCust3Badge.text = "📍"
                binding.stepBundleCust3Text.setTextColor(if (activeBundleIndex == 2) colorPrimary else Color.parseColor("#1D4ED8"))
            } else if (activeBundleIndex == 2) {
                binding.stepBundleCust3Badge.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.stepBundleCust3Badge.text = "3"
                binding.stepBundleCust3Text.setTextColor(colorPrimary)
            } else {
                binding.stepBundleCust3Badge.setBackgroundResource(R.drawable.bg_badge_duty_off)
                binding.stepBundleCust3Badge.text = "3"
                binding.stepBundleCust3Text.setTextColor(colorMuted)
            }
        } else {
            binding.stepBundleDivider3.visibility = View.GONE
            binding.stepBundleCust3.visibility = View.GONE
        }
    }

    /**
     * Chuyển đổi khách hàng mục tiêu đang giao (6.html: selectDeliveryTarget)
     */
    private fun switchActiveBundleCustomer(index: Int) {
        if (!isBundleMode || index !in bundleOrders.indices) return
        val targetOrder = bundleOrders[index]

        if (completedBundleOrderIds.contains(targetOrder.orderId)) {
            Toast.makeText(this, "Đơn của ${targetOrder.customerName} đã được giao thành công!", Toast.LENGTH_SHORT).show()
            return
        }

        if (targetOrder.status == Order.Status.RETURNING_TO_STORE) {
            val remainingOrders = bundleOrders.filter { !completedBundleOrderIds.contains(it.orderId) && it.status != Order.Status.RETURNING_TO_STORE }
            val nextOrder = remainingOrders.firstOrNull()
            showBomReturnFlowDialog(targetOrder, isReturningToStore = (nextOrder == null), nextOrder = nextOrder)
            return
        }

        activeBundleIndex = index
        currentOrderId = targetOrder.orderId
        currentOrder = targetOrder
        resetStep4QrUI()
        bindCustomerQuickSwitchStep3()
        updateBundleStepperUI()
        bindOrderData(targetOrder)

        val targetStep = getEffectiveStepForOrder(targetOrder)
        val finalStep = maxOf(3, targetStep)
        updateStepUI(finalStep)

        val shortCode = QRCodeUtils.getShortOrderCode(targetOrder.orderId)
        val statusMsg = if (finalStep == 4) "📍 Đã đến nhà ${targetOrder.customerName}! Chờ đối soát." else "🛵 Đang giao đơn #${shortCode} (${targetOrder.customerName})!"
        binding.tvRouteToastText.text = statusMsg
        binding.layoutRouteToast.visibility = View.VISIBLE
        binding.layoutRouteToast.postDelayed({
            binding.layoutRouteToast.visibility = View.GONE
        }, 2200)

        checkAndRestoreActiveCountdown()
    }

    private fun resetStep4QrUI() {
        isQrVerified = false
        verifiedTokenString = ""
        binding.tvStep4QrIcon.text = "📷\nCHẠM ĐỂ QUÉT"
        binding.tvStep4QrIcon.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        binding.tvStep4QrStatus.text = "Chưa quét mã QR của khách hàng"
        binding.tvStep4QrStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        binding.btnSimulateScan.text = "📷 Quét Mã QR Khách Hàng (Camera)"
        binding.btnSimulateScan.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
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
            handleBackNavigation()
        }

        binding.btnBackToMapStep3.setOnClickListener {
            handleBackNavigation()
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        binding.btnTripCancel.setOnClickListener {
            showCancelOrderDialog()
        }

        // Bước 1: Nút "Đến quán" trên hộp Google Navigation (Mở Google Maps chỉ đường Xe Máy)
        binding.btnOpenNavStep1.setOnClickListener {
            binding.webViewMapStep1.evaluateJavascript("focusShop();", null)
            val storeName = binding.tvStep1StoreName.text.toString()
            val storeAddr = storeAddress.ifBlank { storeName }
            GoogleMapUtils.openGoogleMapsNavigation(
                context = this,
                destLat = storeLat,
                destLng = storeLng,
                destinationName = storeName,
                address = storeAddr,
                mode = "l" // Xe máy
            )
        }

        // Bước 1: Gọi điện cho quán
        binding.btnCallStoreStep1.setOnClickListener {
            val phone = currentOrder?.shopPhone?.ifBlank { storePhone } ?: storePhone
            if (phone.isNotBlank()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                Toast.makeText(this, "Quán chưa cập nhật số điện thoại", Toast.LENGTH_SHORT).show()
            }
        }

        // Bước 1 (Chế độ Trả Món Bị Bom): Xác nhận đã về tới quán & trả món xong
        binding.btnConfirmReturnFinished.setOnClickListener {
            val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
            AlertDialog.Builder(this)
                .setTitle("🏪 Xác nhận trả món cho Quán?")
                .setMessage("Bạn đã đến quán, bàn giao lại toàn bộ món ăn và nhận đầy đủ tiền hoàn trả từ Quán?")
                .setPositiveButton("Đã hoàn tất") { _, _ ->
                    lifecycleScope.launch {
                        targetOrder?.let { o ->
                            firebaseRepository.updateOrderStatus(o.orderId, Order.Status.RETURNED_TO_STORE, "Shipper đã bàn giao món về quán và nhận hoàn tiền")
                        }
                        bomReturnOrders.toList().forEach { bo ->
                            firebaseRepository.updateOrderStatus(bo.orderId, Order.Status.RETURNED_TO_STORE, "Shipper đã bàn giao món về quán và nhận hoàn tiền")
                        }
                        Toast.makeText(this@DeliveryTripActivity, "✓ Đã hoàn tất trả món về Quán & nhận tiền hoàn!", Toast.LENGTH_LONG).show()

                        val remainingOrders = bundleOrders.filter { !completedBundleOrderIds.contains(it.orderId) && it.orderId != targetOrder?.orderId }
                        if (isBundleMode && remainingOrders.isNotEmpty()) {
                            val nextRemaining = remainingOrders.first()
                            val nextIdx = bundleOrders.indexOf(nextRemaining)
                            switchActiveBundleCustomer(nextIdx)
                        } else {
                            finish()
                        }
                    }
                }
                .setNegativeButton("Chưa", null)
                .show()
        }

        // Bước 2: Gọi quán
        binding.btnCallStoreStep2.setOnClickListener {
            val phone = bundleOrders.firstOrNull()?.shopPhone?.ifBlank { storePhone } ?: currentOrder?.shopPhone?.ifBlank { storePhone } ?: storePhone
            if (phone.isNotBlank()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                Toast.makeText(this, "Quán chưa cập nhật số điện thoại", Toast.LENGTH_SHORT).show()
            }
        }

        // Bước 2: Checklist chọn tất cả (5.html: btn-check-all)
        binding.btnCheckAllDishes.setOnClickListener {
            for (i in 0 until binding.layoutSingleDishesContainer.childCount) {
                val v = binding.layoutSingleDishesContainer.getChildAt(i)
                if (v is CheckBox) v.isChecked = true
            }
            binding.cbSealPackage.isChecked = true
            bundleOrders.toList().forEach { checkedBundleOrderIds.add(it.orderId) }
            for (i in 0 until binding.layoutBundleCardsContainer.childCount) {
                val child = binding.layoutBundleCardsContainer.getChildAt(i)
                val cb = child.findViewById<CheckBox>(R.id.cbOrderReady)
                cb?.isChecked = true
            }
            updateStep2ReadinessUI()
            Toast.makeText(this, "✓ Đã kiểm tra đủ tất cả món!", Toast.LENGTH_SHORT).show()
        }

        // Bước 2: Nút xác nhận lấy hàng -> sang Bước 3 (5.html: bottom-sheet-fixed)
        binding.btnConfirmPickDishes.setOnClickListener {
            updateStepUI(3)
            fetchRealShipperLocation()
            if (isBundleMode && bundleOrders.isNotEmpty()) {
                Toast.makeText(this@DeliveryTripActivity, "🛵 Đã lấy đủ tất cả ${bundleOrders.size} đơn gộp! Bắt đầu giao chặng 1", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@DeliveryTripActivity, "🛵 Xuất phát đi giao hàng!", Toast.LENGTH_SHORT).show()
            }
            lifecycleScope.launch {
                if (isBundleMode && bundleOrders.isNotEmpty()) {
                    bundleOrders.toList().forEach { o ->
                        firebaseRepository.updateOrderStatus(o.orderId, Order.Status.DELIVERING)
                    }
                } else {
                    firebaseRepository.updateOrderStatus(currentOrderId, Order.Status.DELIVERING)
                }
            }
        }

        // Bước 3: Dẫn đường đến khách (Mở Google Maps chỉ đường Xe Máy)
        binding.btnOpenNavStep3.setOnClickListener {
            val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
            val cusName = targetOrder?.customerName ?: "Khách hàng"
            val cusAddr = targetOrder?.address.orEmpty()
            binding.webViewMapStep3.evaluateJavascript("focusCustomer();", null)
            GoogleMapUtils.openGoogleMapsNavigation(
                context = this,
                destLat = customerLat,
                destLng = customerLng,
                destinationName = cusName,
                address = cusAddr,
                mode = "l" // Xe máy
            )
        }

        // Bước 3: Gọi khách
        binding.btnCallCustomerStep3.setOnClickListener {
            val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
            val phone = targetOrder?.customerPhone.orEmpty()
            if (phone.isNotBlank()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                Toast.makeText(this, "Khách hàng chưa cập nhật số điện thoại", Toast.LENGTH_SHORT).show()
            }
        }

        // Bước 3: Nhắn tin khách (6.html: modal chat)
        binding.btnMessageCustomerStep3.setOnClickListener {
            val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
            targetOrder?.let { showChatDialog(it) }
        }

        // Bước 3: Nút Báo cáo sự cố / Bom hàng (6.html: cancelReportModal)
        binding.btnReportIncidentStep3.setOnClickListener {
            val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
            targetOrder?.let { showIncidentReportDialog(it) }
        }

        // Bước 3: Nút "Đã đến điểm giao khách" -> sang Bước 4 & bắt đầu đếm ngược 15P
        binding.btnArrivedAtCustomer.setOnClickListener {
            updateStepUI(4)
            val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
            targetOrder?.let {
                lifecycleScope.launch {
                    firebaseRepository.updateOrderStatus(it.orderId, Order.Status.ARRIVED_AT_CUSTOMER)
                }
                start15MinCountdown(it, "Đang đếm ngược ${round1Minutes} phút thời gian chờ nhận hàng")
            }
        }

        // Bước 4: Nút quét mã QR bằng camera thật
        binding.btnSimulateScan.setOnClickListener {
            launchCameraQrScanner()
        }
        binding.tvStep4QrIcon.setOnClickListener {
            launchCameraQrScanner()
        }
        binding.btnPickQrImage.setOnClickListener {
            pickQrImageLauncher.launch("image/*")
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

        // Bước 4 (8.html): BỘ 3 NÚT THAO TÁC (GỌI, NHẮN TIN, BÁO CÁO SỰ CỐ)
        binding.btnStep4CallCustomer.setOnClickListener {
            val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
            val phone = targetOrder?.customerPhone.orEmpty().replace(" ", "")
            if (phone.isNotBlank()) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Không có số điện thoại khách hàng", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnStep4ChatCustomer.setOnClickListener {
            val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
            targetOrder?.let { showChatDialog(it) }
        }

        binding.btnStep4ReportIncident.setOnClickListener {
            val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
            targetOrder?.let { showIncidentReportDialog(it) }
        }

        // Bước 4: CÁC NÚT ĐIỀU KHIỂN ĐẾM NGƯỢC
        binding.btnCancelCountdown.setOnClickListener {
            cancelCountdown()
        }

        binding.btnCountdownExpiredAction.setOnClickListener {
            triggerExpiredCancelOrder()
        }
    }

    /**
     * Xử lý điều hướng khi bấm nút Back hoặc nút Về Màn Hình Chính -> Ra thẳng màn hình chính (DashboardActivity)
     */
    private fun handleBackNavigation() {
        finish()
    }

    private fun start15MinCountdown(order: Order, reason: String, round: Int = 1, forceReset: Boolean = false) {
        countdownRound = round
        pendingIncidentReason = reason
        isCountdownActive = true
        isRiskReported = (round >= 2)
        hasShownRound1ExpirationDialog = false
        val targetMinutes = if (round == 1) round1Minutes else round2Minutes
        val totalSec = targetMinutes * 60

        val prefs = getSharedPreferences("delivery_countdown", Context.MODE_PRIVATE)
        val savedStartTime = prefs.getLong("start_time_${order.orderId}", 0L).let {
            if (it > 0L) it else order.incidentWarningTime
        }
        val savedRound = prefs.getInt("round_${order.orderId}", if (order.countdownRound > 0) order.countdownRound else 1)

        val startTimeToUse: Long
        if (!forceReset && savedStartTime > 0L && savedRound == round) {
            // Giữ nguyên mốc thời gian bắt đầu trước đó, tính thời gian còn lại
            startTimeToUse = savedStartTime
            val elapsedSec = ((System.currentTimeMillis() - savedStartTime) / 1000).toInt()
            if (elapsedSec >= totalSec) {
                countdownSecondsRemaining = 0
                isCountdownActive = false
                onCountdownExpired()
                return
            } else {
                countdownSecondsRemaining = maxOf(1, totalSec - elapsedSec)
            }
        } else {
            // Khởi tạo mốc đếm ngược mới
            startTimeToUse = System.currentTimeMillis()
            countdownSecondsRemaining = totalSec

            // Gửi thông báo / tin nhắn cảnh báo vào Firebase
            lifecycleScope.launch {
                try {
                    val sc = QRCodeUtils.getShortOrderCode(order.orderId)
                    val warningText = if (round >= 2) {
                        "⚠️ CẢNH BÁO GIA HẠN LẦN 2 #${sc}: Đơn đã vào danh sách Rủi ro. Bạn có ${round2Minutes} phút cuối cùng để ra nhận món trước khi đơn bị hủy bom hàng và trả về quán!"
                    } else {
                        ""
                    }
                    val updates = mapOf<String, Any>(
                        "hasIncidentWarning" to (round >= 2),
                        "incidentWarningTime" to startTimeToUse,
                        "incidentWarningText" to warningText,
                        "countdownRound" to round,
                        "isRiskOrder" to (round >= 2)
                    )
                    com.google.firebase.database.FirebaseDatabase.getInstance().getReference("orders").child(order.orderId).updateChildren(updates)
                } catch (_: Exception) {}
            }
        }

        // Lưu trạng thái cảnh báo vào SharedPreferences (luôn giữ startTimeToUse)
        prefs.edit()
            .putLong("start_time_${order.orderId}", startTimeToUse)
            .putString("reason_${order.orderId}", reason)
            .putInt("round_${order.orderId}", round)
            .apply()

        // Khung đếm ngược đã được chuyển vào bên trong modal Báo cáo sự cố
        binding.cardCountdownWarning.visibility = View.GONE
        binding.layoutCountdownActiveButtons.visibility = View.VISIBLE
        binding.btnCountdownExpiredAction.visibility = View.GONE

        val m = countdownSecondsRemaining / 60
        val s = countdownSecondsRemaining % 60
        binding.tvCountdownDigits.text = String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s)
        binding.tvCountdownDigits.setTextColor(Color.parseColor("#DC2626"))
        binding.tvCountdownBadge.text = if (round == 1) "Chờ ${round1Minutes} phút" else "Gia hạn ${round2Minutes} phút"

        if (round == 1) {
            binding.tvCountdownSubtext.text = "LẦN 1/2 • phút còn lại"
            binding.tvCountdownSubtext.setTextColor(Color.parseColor("#9A3412"))
            binding.tvCountdownStatusMsg.text = "LẦN 1/2: Đã đến nhà khách! Hệ thống đang đếm ngược ${round1Minutes} phút thời gian chờ nhận hàng. Vui lòng gọi điện hoặc nhắn tin cho khách. Nếu sau ${round1Minutes} phút không nhận, bạn có quyền báo cáo đơn rủi ro để kích hoạt ${round2Minutes} phút gia hạn Lần 2!"
            binding.layoutCountdownNotice.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
            binding.ivCountdownNoticeIcon.setColorFilter(Color.parseColor("#B45309"))
            binding.tvCountdownStatusMsg.setTextColor(Color.parseColor("#78350F"))
        } else {
            binding.tvCountdownSubtext.text = "LẦN 2/2 (Gia hạn) • còn lại"
            binding.tvCountdownSubtext.setTextColor(Color.parseColor("#DC2626"))
            binding.tvCountdownStatusMsg.text = "LẦN 2/2 (CẢNH BÁO GIA HẠN): Đơn đã chuyển sang trạng thái ĐƠN RỦI RO! Nếu sau ${round2Minutes} phút này khách vẫn không nhận, đơn sẽ chính thức bị hủy do bom hàng và bạn sẽ quay về Quán trả món nhận tiền hoàn!"
            binding.layoutCountdownNotice.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
            binding.ivCountdownNoticeIcon.setColorFilter(Color.parseColor("#DC2626"))
            binding.tvCountdownStatusMsg.setTextColor(Color.parseColor("#DC2626"))
            Toast.makeText(this, "⚠️ Đã kích hoạt đếm ngược ${round2Minutes} phút LẦN 2 (Đơn rủi ro)! Chờ khách gia hạn.", Toast.LENGTH_LONG).show()
        }

        runCountdownTimer()
    }

    private fun runCountdownTimer() {
        countdownTimer?.cancel()
        updateCountdownDisplay(countdownSecondsRemaining)

        countdownTimer = object : android.os.CountDownTimer((countdownSecondsRemaining * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownSecondsRemaining = (millisUntilFinished / 1000).toInt()
                updateCountdownDisplay(countdownSecondsRemaining)
            }

            override fun onFinish() {
                countdownSecondsRemaining = 0
                updateCountdownDisplay(0)
                onCountdownExpired()
            }
        }.start()
    }

    private fun updateCountdownDisplay(sec: Int) {
        val m = sec / 60
        val s = sec % 60
        val timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s)
        binding.tvCountdownDigits.text = timeStr
    }

    private fun cancelCountdown() {
        AlertDialog.Builder(this)
            .setTitle("✓ Khách đã ra nhận?")
            .setMessage("Khách hàng đã có mặt để nhận món? Hủy đếm ngược và tiến hành quét mã QR bàn giao món.")
            .setPositiveButton("Xác nhận") { _, _ ->
                countdownTimer?.cancel()
                isCountdownActive = false
                hasShownRound1ExpirationDialog = false
                val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
                targetOrder?.let { o ->
                    getSharedPreferences("delivery_countdown", Context.MODE_PRIVATE)
                        .edit()
                        .remove("start_time_${o.orderId}")
                        .remove("reason_${o.orderId}")
                        .remove("round_${o.orderId}")
                        .remove("call_proof_${o.orderId}")
                        .remove("msg_proof_${o.orderId}")
                        .apply()
                    incidentCallProofByOrder.remove(o.orderId)
                    incidentMsgProofByOrder.remove(o.orderId)

                    lifecycleScope.launch {
                        try {
                            val updates = mapOf<String, Any>(
                                "hasIncidentWarning" to false,
                                "incidentWarningTime" to 0L,
                                "incidentWarningText" to ""
                            )
                            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("orders").child(o.orderId).updateChildren(updates)
                        } catch (_: Exception) {}
                    }
                }
                binding.cardCountdownWarning.visibility = View.GONE
                Toast.makeText(this, "✓ Đã hủy cảnh báo mất đơn. Vui lòng quét mã QR để đối soát!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Tiếp tục chờ", null)
            .show()
    }

    private fun checkAndRestoreActiveCountdown() {
        if (isRestoringCountdown) return
        isRestoringCountdown = true
        try {
            val targetOrder = (if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder) ?: return
            val prefs = getSharedPreferences("delivery_countdown", Context.MODE_PRIVATE)
            var startTime = prefs.getLong("start_time_${targetOrder.orderId}", 0L)
            var round = prefs.getInt("round_${targetOrder.orderId}", 1)

            // Fallback từ Firebase nếu prefs local chưa có
            if (startTime <= 0L && targetOrder.incidentWarningTime > 0L) {
                startTime = targetOrder.incidentWarningTime
                round = if (targetOrder.countdownRound > 0) targetOrder.countdownRound else 1
                prefs.edit()
                    .putLong("start_time_${targetOrder.orderId}", startTime)
                    .putInt("round_${targetOrder.orderId}", round)
                    .putString("reason_${targetOrder.orderId}", targetOrder.incidentWarningText)
                    .apply()
            }

            if (startTime > 0L) {
                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                countdownRound = round
                val targetMinutes = if (countdownRound == 1) round1Minutes else round2Minutes
                val totalSec = targetMinutes * 60
                pendingIncidentReason = prefs.getString("reason_${targetOrder.orderId}", targetOrder.incidentWarningText.ifBlank { "Khách không nhận món" }).orEmpty()
                isRiskReported = (countdownRound >= 2)
                binding.cardCountdownWarning.visibility = View.GONE
                if (elapsedSec >= totalSec) {
                    if (countdownRound == 1) {
                        // Lần 1 đã trôi qua trong lúc app đóng: tính phần thời gian tràn sang Lần 2
                        val overtimeSec = elapsedSec - totalSec
                        val round2TotalSec = round2Minutes * 60
                        if (overtimeSec < round2TotalSec) {
                            // Đang ở Lần 2, tiếp tục đếm phần còn lại của Lần 2
                            countdownRound = 2
                            isRiskReported = true
                            val newStart = startTime + (totalSec * 1000L)
                            prefs.edit()
                                .putLong("start_time_${targetOrder.orderId}", newStart)
                                .putInt("round_${targetOrder.orderId}", 2)
                                .apply()
                            countdownSecondsRemaining = maxOf(1, round2TotalSec - overtimeSec)
                            isCountdownActive = true
                            binding.layoutCountdownActiveButtons.visibility = View.VISIBLE
                            binding.btnCountdownExpiredAction.visibility = View.GONE
                            binding.tvCountdownBadge.text = "Gia hạn ${round2Minutes} phút"
                            binding.tvCountdownSubtext.text = "LẦN 2/2 (Gia hạn) • còn lại"
                            binding.tvCountdownSubtext.setTextColor(Color.parseColor("#DC2626"))
                            binding.tvCountdownStatusMsg.text = "Đang tiếp tục đếm ngược ${round2Minutes} phút LẦN 2 (Cảnh báo gia hạn đơn rủi ro)..."
                            binding.layoutCountdownNotice.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                            binding.ivCountdownNoticeIcon.setColorFilter(Color.parseColor("#DC2626"))
                            binding.tvCountdownStatusMsg.setTextColor(Color.parseColor("#DC2626"))
                            runCountdownTimer()
                        } else {
                            // Cả Lần 1 và Lần 2 đều đã hết giờ
                            countdownRound = 2
                            countdownSecondsRemaining = 0
                            isCountdownActive = false
                            onCountdownExpired()
                        }
                    } else {
                        // Lần 2 đã hết giờ
                        countdownSecondsRemaining = 0
                        isCountdownActive = false
                        onCountdownExpired()
                    }
                } else {
                    isCountdownActive = true
                    countdownSecondsRemaining = maxOf(1, totalSec - elapsedSec)
                    binding.layoutCountdownActiveButtons.visibility = View.VISIBLE
                    binding.btnCountdownExpiredAction.visibility = View.GONE
                    binding.tvCountdownBadge.text = if (countdownRound == 1) "Chờ ${round1Minutes} phút" else "Gia hạn ${round2Minutes} phút"
                    if (countdownRound == 1) {
                        binding.tvCountdownSubtext.text = "LẦN 1/2 • phút còn lại"
                        binding.tvCountdownSubtext.setTextColor(Color.parseColor("#9A3412"))
                        binding.tvCountdownStatusMsg.text = "Đang tiếp tục đếm ngược ${round1Minutes} phút LẦN 1 chờ khách (${targetOrder.customerName})..."
                        binding.layoutCountdownNotice.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                        binding.ivCountdownNoticeIcon.setColorFilter(Color.parseColor("#B45309"))
                        binding.tvCountdownStatusMsg.setTextColor(Color.parseColor("#78350F"))
                    } else {
                        binding.tvCountdownSubtext.text = "LẦN 2/2 (Gia hạn) • còn lại"
                        binding.tvCountdownSubtext.setTextColor(Color.parseColor("#DC2626"))
                        binding.tvCountdownStatusMsg.text = "Đang tiếp tục đếm ngược ${round2Minutes} phút LẦN 2 (Cảnh báo gia hạn đơn rủi ro)..."
                        binding.layoutCountdownNotice.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                        binding.ivCountdownNoticeIcon.setColorFilter(Color.parseColor("#DC2626"))
                        binding.tvCountdownStatusMsg.setTextColor(Color.parseColor("#DC2626"))
                    }
                    runCountdownTimer()
                }
            }
        } finally {
            isRestoringCountdown = false
        }
    }

    private fun onCountdownExpired() {
        val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder

        if (countdownRound == 1) {
            // HẾT LẦN 1: TỰ ĐỘNG CHUYỂN SANG VÀ KÍCH HOẠT ĐẾM NGƯỢC LẦN 2 NGAY LẬP TỨC
            val autoReason = "Đơn rủi ro: Hết ${round1Minutes} phút chờ Lần 1 (Tự động gia hạn ${round2Minutes} phút Lần 2)"
            targetOrder?.let { order ->
                lifecycleScope.launch {
                    try {
                        val updates = mapOf<String, Any>(
                            "isRiskOrder" to true,
                            "riskStatus" to "RISK_REPORTED",
                            "riskReason" to autoReason,
                            "riskReportedAt" to System.currentTimeMillis()
                        )
                        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("orders").child(order.orderId).updateChildren(updates)
                    } catch (_: Exception) {}
                }
                start15MinCountdown(order, autoReason, round = 2, forceReset = true)
                Toast.makeText(
                    this@DeliveryTripActivity,
                    "⚠️ Đã hết ${round1Minutes} phút Lần 1! Hệ thống tự động chuyển sang Lần 2 và đang đếm ngược ${round2Minutes} phút gia hạn.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            // HẾT LẦN 2: Hiển thị thông báo hết giờ & chờ Shipper chọn quay về Quán trả hàng
            binding.tvCountdownDigits.text = "00:00"
            binding.layoutCountdownActiveButtons.visibility = View.GONE
            binding.btnCountdownExpiredAction.visibility = View.VISIBLE

            binding.tvCountdownSubtext.text = "HẾT ${round2Minutes}P LẦN 2"
            binding.tvCountdownSubtext.setTextColor(Color.parseColor("#DC2626"))
            binding.tvCountdownStatusMsg.text = "ĐÃ HẾT ${round2Minutes} PHÚT LẦN 2! Khách hàng không nhận món (Bom hàng).\nVui lòng bấm nút bên dưới để xác nhận đơn bom và bắt đầu quay về Quán trả món nhận tiền hoàn."
            binding.layoutCountdownNotice.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
            binding.ivCountdownNoticeIcon.setColorFilter(Color.parseColor("#DC2626"))
            binding.tvCountdownStatusMsg.setTextColor(Color.parseColor("#DC2626"))

            binding.btnCountdownExpiredAction.text = "HẾT ${round2Minutes}P LẦN 2 • XÁC NHẬN BOM & QUAY VỀ QUÁN ➔"
            binding.btnCountdownExpiredAction.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#DC2626"))
        }
    }

    private fun triggerExpiredCancelOrder() {
        val targetOrder = (if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder) ?: return
        if (countdownRound == 1) {
            showIncidentReportDialog(targetOrder, isExpiredRoundOne = true)
        } else {
            AlertDialog.Builder(this)
                .setTitle("🏪 Xác nhận quay về quán?")
                .setMessage("Đã hết ${round2Minutes} phút gia hạn Lần 2 mà khách vẫn không nhận món. Bạn có muốn xác nhận đơn bị bom và quay về Quán để trả hàng nhận hoàn tiền không?")
                .setPositiveButton("Quay về quán trả hàng") { _, _ ->
                    executeIncidentCancel(targetOrder, "Khách bom hàng sau 2 lần đếm ngược (${round1Minutes} phút + ${round2Minutes} phút) (${pendingIncidentReason})")
                }
                .setNegativeButton("Đóng", null)
                .show()
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
        binding.tvStep4QrStatus.text = "✓ Đã xác minh mã QR khách hàng: ${matchedToken}"
        binding.tvStep4QrStatus.setTextColor(Color.parseColor("#34D399"))
        binding.btnSimulateScan.text = "✓ Đã Quét QR Hợp Lệ"
        binding.btnSimulateScan.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_green)
    }

    private fun decodeAndVerifyQrFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                Toast.makeText(this, "Không thể mở file ảnh. Vui lòng thử lại!", Toast.LENGTH_SHORT).show()
                return
            }

            val scannedValue = decodeQrFromBitmap(bitmap)
            if (scannedValue != null) {
                verifyScannedQrPayload(scannedValue)
            } else {
                Toast.makeText(this, "⚠️ Không tìm thấy mã QR trong ảnh. Vui lòng chọn ảnh chụp rõ nét hơn!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi đọc ảnh QR: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))

        return try {
            val hints = mapOf(
                com.google.zxing.DecodeHintType.TRY_HARDER to true,
                com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE)
            )
            val result = com.google.zxing.MultiFormatReader().decode(binaryBitmap, hints)
            result.text
        } catch (_: Exception) {
            try {
                val globalBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(source))
                val result = com.google.zxing.MultiFormatReader().decode(globalBitmap)
                result.text
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun verifyScannedQrPayload(scannedPayload: String) {
        val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
        val ordId = targetOrder?.orderId ?: currentOrderId
        val shortCode = QRCodeUtils.getShortOrderCode(ordId)

        val verifyResult = QRCodeUtils.verifyOrderQrPayload(
            scannedRaw = scannedPayload,
            expectedOrderId = ordId,
            expectedDeliveryToken = targetOrder?.deliveryToken ?: ""
        )

        if (verifyResult.isValid) {
            isQrVerified = true
            verifiedTokenString = verifyResult.matchedToken
            markStep4QrVerified(verifyResult.matchedToken)
            Toast.makeText(this, "✓ Xác minh ảnh QR thành công! Vui lòng thu tiền COD (nếu có) và nhấn nút Xác nhận hoàn tất.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "❌ Mã QR trong ảnh không khớp với đơn #${shortCode}!", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateStepUI(step: Int) {
        currentStep = step
        saveStep(currentOrderId, step)

        binding.layoutStep1.visibility = if (step == 1) View.VISIBLE else View.GONE
        binding.layoutStep2.visibility = if (step == 2) View.VISIBLE else View.GONE
        binding.layoutStep3.visibility = if (step == 3) View.VISIBLE else View.GONE
        binding.layoutStep4.visibility = if (step == 4) View.VISIBLE else View.GONE

        val colorPrimary = ContextCompat.getColor(this, R.color.primary)
        val colorGreen = ContextCompat.getColor(this, R.color.brand_green)
        val colorMuted = ContextCompat.getColor(this, R.color.text_secondary)

        if (isBundleMode) {
            // Đơn gộp: ở bước 1 & 2 hiển thị Stepper 2 chặng (5.html); ở bước 3 & 4 hiển thị Stepper 3 chặng (6.html)
            if (step <= 2) {
                binding.layoutTripStepperBundle.visibility = View.GONE
                binding.layoutTripStepper.visibility = View.VISIBLE

                binding.step1Badge.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.step1Badge.text = "1"
                binding.step1Text.setTextColor(colorPrimary)

                binding.step2Badge.setBackgroundResource(R.drawable.bg_badge_duty_off)
                binding.step2Badge.text = "2"
                binding.step2Text.setTextColor(colorMuted)
                binding.stepDivider.setBackgroundColor(ContextCompat.getColor(this, R.color.border))

                binding.btnTripCancel.visibility = View.VISIBLE
            } else {
                binding.layoutTripStepper.visibility = View.GONE
                binding.layoutTripStepperBundle.visibility = View.VISIBLE
                updateBundleStepperUI()
                binding.btnTripCancel.visibility = View.GONE
            }
        } else {
            // Đơn đơn lẻ: Stepper 2 Chặng chuẩn theo 4.html
            binding.layoutTripStepperBundle.visibility = View.GONE
            binding.layoutTripStepper.visibility = View.VISIBLE

            if (step <= 2) {
                binding.step1Badge.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.step1Badge.text = "1"
                binding.step1Text.setTextColor(colorPrimary)

                binding.step2Badge.setBackgroundResource(R.drawable.bg_badge_duty_off)
                binding.step2Badge.text = "2"
                binding.step2Text.setTextColor(colorMuted)
                binding.stepDivider.setBackgroundColor(ContextCompat.getColor(this, R.color.border))

                binding.btnTripCancel.visibility = View.VISIBLE
            } else {
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
    }

    /**
     * Modal Nhắn tin với khách hàng (6.html: chatModal)
     */
    private fun showChatDialog(order: Order) {
        val chatBinding = com.chuyen_de_2.foodorder.internal.databinding.DialogChatCustomerBinding.inflate(layoutInflater)
        val cusName = order.customerName.ifBlank { "Khách hàng" }
        chatBinding.tvChatCustomerName.text = "Chat với ${cusName}"

        val dialog = AlertDialog.Builder(this)
            .setView(chatBinding.root)
            .create()

        fun addDriverBubble(text: String) {
            val tv = android.widget.TextView(this).apply {
                this.text = text
                setTextColor(ContextCompat.getColor(this@DeliveryTripActivity, R.color.primary))
                setBackgroundResource(R.drawable.bg_choice_card_active)
                setPadding(24, 16, 24, 16)
                textSize = 12f
                val params = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.END
                    bottomMargin = 16
                }
                layoutParams = params
            }
            chatBinding.layoutChatMessagesContainer.addView(tv)
            chatBinding.scrollChatMessages.post {
                chatBinding.scrollChatMessages.fullScroll(View.FOCUS_DOWN)
            }
        }

        chatBinding.chipReply1.setOnClickListener {
            addDriverBubble("Em sắp đến nơi rồi ạ! 🛵")
        }
        chatBinding.chipReply2.setOnClickListener {
            addDriverBubble("Em đang đợi trước cổng nhà anh/chị ạ! 🏠")
        }
        chatBinding.chipReply3.setOnClickListener {
            addDriverBubble("Anh/chị mở sẵn mã QR Token giúp em nhé! 📱")
        }

        chatBinding.btnSendChatMessage.setOnClickListener {
            val msg = chatBinding.etChatMessageInput.text.toString().trim()
            if (msg.isNotBlank()) {
                addDriverBubble(msg)
                chatBinding.etChatMessageInput.text.clear()
            }
        }

        chatBinding.btnCloseChatDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    /**
     * Modal Báo Cáo Sự Cố & Bom Hàng (6.html & 8.html: cancelReportModal)
     * isExpiredRoundOne = true: Đã hết 15 phút lần 1, tài xế xác nhận báo cáo đơn rủi ro để bắt đầu đếm ngược 15 phút lần 2.
     */
    /**
     * Modal Báo Cáo Sự Cố & Bom Hàng (Có đếm ngược thời gian thực bên trong)
     */
    private fun showIncidentReportDialog(order: Order, isExpiredRoundOne: Boolean = false) {
        activeIncidentDialog?.dismiss()
        val prefs = getSharedPreferences("delivery_countdown", Context.MODE_PRIVATE)
        if (!isExpiredRoundOne) {
            checkAndRestoreActiveCountdown()
            if (!isCountdownActive && countdownSecondsRemaining <= 0 && countdownRound < 2) {
                start15MinCountdown(order, "Đang đếm ngược ${round1Minutes} phút thời gian chờ nhận hàng")
            }
        }
        val repBinding = com.chuyen_de_2.foodorder.internal.databinding.DialogReportIncidentBinding.inflate(layoutInflater)
        val shortCode = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.getShortOrderCode(order.orderId)

        var dialogCountdownTimer: android.os.CountDownTimer? = null
        var isTimerFinished = isExpiredRoundOne || countdownSecondsRemaining <= 0

        val reasons = arrayOf(
            "📵 Khách không nghe máy (Đã gọi 3 cuộc)",
            "🚫 Khách từ chối nhận món (Bom hàng)",
            "📍 Sai địa chỉ giao hàng / Không liên hệ được",
            "💥 Món ăn đổ vỡ do sự cố giao thông",
            "💸 Khách không đủ tiền mặt thanh toán COD"
        )

        fun updateConfirmButtonLockState(remainingSec: Int) {
            if (!isTimerFinished && remainingSec > 0) {
                val timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", remainingSec / 60, remainingSec % 60)
                repBinding.btnConfirmIncidentCancel.isEnabled = false
                repBinding.btnConfirmIncidentCancel.alpha = 0.55f
                repBinding.btnConfirmIncidentCancel.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                repBinding.btnConfirmIncidentCancel.text = if (countdownRound >= 2) {
                    "⏳ CHỜ HẾT GIỜ LẦN 2 (${timeStr}) ĐỂ XÁC NHẬN BOM"
                } else {
                    "⏳ CHỜ HẾT GIỜ LẦN 1 (${timeStr}) ĐỂ BÁO CÁO"
                }
            } else {
                repBinding.btnConfirmIncidentCancel.isEnabled = true
                repBinding.btnConfirmIncidentCancel.alpha = 1.0f
                val selectedReason = repBinding.spinnerIncidentReason.selectedItem?.toString().orEmpty()
                if (countdownRound >= 2 || selectedReason.contains("Bom hàng", ignoreCase = true)) {
                    repBinding.btnConfirmIncidentCancel.text = "🚫 XÁC NHẬN ĐƠN BỊ BOM & QUAY VỀ QUÁN ➔"
                    repBinding.btnConfirmIncidentCancel.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))
                } else {
                    repBinding.btnConfirmIncidentCancel.text = "⏱️ XÁC NHẬN BÁO CÁO & BẮT ĐẦU ${round2Minutes}P LẦN 2 ➔"
                    repBinding.btnConfirmIncidentCancel.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EA580C"))
                }
            }
        }

        repBinding.spinnerIncidentReason.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, reasons
        )

        repBinding.spinnerIncidentReason.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateConfirmButtonLockState(if (isTimerFinished) 0 else countdownSecondsRemaining)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        if (isExpiredRoundOne) {
            repBinding.tvIncidentOrderSub.text = "Đơn #${shortCode} • ĐÃ HẾT ${round1Minutes}P LẦN 1 • Báo cáo đơn rủi ro"
            repBinding.tvIncidentCountdownBadge.text = "Chờ ${round1Minutes} phút"
            repBinding.tvIncidentCountdownDigits.text = "00:00"
            repBinding.tvIncidentCountdownSubtext.text = "HẾT ${round1Minutes}P LẦN 1"
            repBinding.tvIncidentCountdownSubtext.setTextColor(Color.parseColor("#EA580C"))
            repBinding.tvIncidentCountdownDesc.text = "ĐÃ HẾT ${round1Minutes} PHÚT LẦN 1! Khách hàng chưa nhận món. Bạn được quyền Báo Cáo Sự Cố để chuyển trạng thái sang ĐƠN RỦI RO và kích hoạt ${round2Minutes} phút gia hạn Lần 2 trước khi hủy đơn trả hàng!"
            repBinding.layoutIncidentCountdownNotice.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF7ED"))
            repBinding.ivIncidentCountdownNoticeIcon.setColorFilter(Color.parseColor("#EA580C"))
            repBinding.tvIncidentCountdownDesc.setTextColor(Color.parseColor("#C2410C"))
            updateConfirmButtonLockState(0)
        } else if (countdownRound == 2) {
            repBinding.tvIncidentOrderSub.text = "Đơn #${shortCode} • ĐANG ĐẾM NGƯỢC LẦN 2 (Đơn rủi ro)"
            repBinding.tvIncidentCountdownBadge.text = "Gia hạn ${round2Minutes} phút"
            repBinding.tvIncidentCountdownSubtext.text = "LẦN 2/2 (Gia hạn) • còn lại"
            repBinding.tvIncidentCountdownSubtext.setTextColor(Color.parseColor("#DC2626"))
            repBinding.tvIncidentCountdownDesc.text = "LẦN 2/2 (CẢNH BÁO GIA HẠN): Đơn đã chuyển sang trạng thái ĐƠN RỦI RO! Nếu sau ${round2Minutes} phút này khách vẫn không nhận, đơn sẽ chính thức bị hủy do bom hàng và bạn sẽ quay về Quán trả món nhận tiền hoàn!"
            repBinding.layoutIncidentCountdownNotice.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
            repBinding.ivIncidentCountdownNoticeIcon.setColorFilter(Color.parseColor("#DC2626"))
            repBinding.tvIncidentCountdownDesc.setTextColor(Color.parseColor("#DC2626"))
            repBinding.tvIncidentCountdownDigits.text = String.format(java.util.Locale.getDefault(), "%02d:%02d", countdownSecondsRemaining / 60, countdownSecondsRemaining % 60)
            updateConfirmButtonLockState(countdownSecondsRemaining)

            if (countdownSecondsRemaining > 0) {
                dialogCountdownTimer = object : android.os.CountDownTimer((countdownSecondsRemaining * 1000).toLong(), 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val sec = (millisUntilFinished / 1000).toInt()
                        repBinding.tvIncidentCountdownDigits.text = String.format(java.util.Locale.getDefault(), "%02d:%02d", sec / 60, sec % 60)
                        updateConfirmButtonLockState(sec)
                    }

                    override fun onFinish() {
                        isTimerFinished = true
                        countdownSecondsRemaining = 0
                        repBinding.tvIncidentCountdownDigits.text = "00:00"
                        repBinding.tvIncidentCountdownSubtext.text = "HẾT ${round2Minutes}P LẦN 2"
                        repBinding.tvIncidentCountdownDesc.text = "ĐÃ HẾT ${round2Minutes} PHÚT LẦN 2! Khách hàng không nhận món (Bom hàng). Bạn có thể xác nhận hủy đơn bom ngay."
                        updateConfirmButtonLockState(0)
                    }
                }.start()
            } else {
                isTimerFinished = true
                repBinding.tvIncidentCountdownDigits.text = "00:00"
                updateConfirmButtonLockState(0)
            }
        } else {
            repBinding.tvIncidentOrderSub.text = "Đơn hàng: #${shortCode} • Khách: ${order.customerName}"
            repBinding.tvIncidentCountdownBadge.text = "Chờ ${round1Minutes} phút"
            repBinding.tvIncidentCountdownSubtext.text = "LẦN 1/2 • phút còn lại"
            repBinding.tvIncidentCountdownSubtext.setTextColor(Color.parseColor("#9A3412"))
            repBinding.tvIncidentCountdownDesc.text = "LẦN 1/2: Đã đến nhà khách! Hệ thống đang đếm ngược ${round1Minutes} phút thời gian chờ nhận hàng. Vui lòng gọi điện hoặc nhắn tin cho khách. Nếu sau ${round1Minutes} phút không nhận, bạn có quyền báo cáo đơn rủi ro để kích hoạt ${round2Minutes} phút gia hạn Lần 2!"
            repBinding.layoutIncidentCountdownNotice.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
            repBinding.ivIncidentCountdownNoticeIcon.setColorFilter(Color.parseColor("#B45309"))
            repBinding.tvIncidentCountdownDesc.setTextColor(Color.parseColor("#78350F"))
            repBinding.tvIncidentCountdownDigits.text = String.format(java.util.Locale.getDefault(), "%02d:%02d", countdownSecondsRemaining / 60, countdownSecondsRemaining % 60)
            updateConfirmButtonLockState(countdownSecondsRemaining)

            if (countdownSecondsRemaining > 0) {
                dialogCountdownTimer = object : android.os.CountDownTimer((countdownSecondsRemaining * 1000).toLong(), 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val sec = (millisUntilFinished / 1000).toInt()
                        repBinding.tvIncidentCountdownDigits.text = String.format(java.util.Locale.getDefault(), "%02d:%02d", sec / 60, sec % 60)
                        updateConfirmButtonLockState(sec)
                    }

                    override fun onFinish() {
                        if (countdownRound == 1) {
                            // Tự động chuyển sang Lần 2 ngay trong dialog
                            val autoReason = "Đơn rủi ro: Đã chờ hết ${round1Minutes} phút Lần 1 - Tự động gia hạn Lần 2"
                            start15MinCountdown(order, autoReason, round = 2, forceReset = true)
                            repBinding.tvIncidentOrderSub.text = "Đơn #${shortCode} • ĐANG ĐẾM NGƯỢC LẦN 2 (Đơn rủi ro)"
                            repBinding.tvIncidentCountdownBadge.text = "Gia hạn ${round2Minutes} phút"
                            repBinding.tvIncidentCountdownSubtext.text = "LẦN 2/2 (Gia hạn) • còn lại"
                            repBinding.tvIncidentCountdownSubtext.setTextColor(Color.parseColor("#DC2626"))
                            repBinding.tvIncidentCountdownDesc.text = "LẦN 2/2 (CẢNH BÁO GIA HẠN): Đã hết Lần 1! Hệ thống đang tự động đếm ngược ${round2Minutes} phút Lần 2..."
                            repBinding.layoutIncidentCountdownNotice.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                            repBinding.ivIncidentCountdownNoticeIcon.setColorFilter(Color.parseColor("#DC2626"))
                            repBinding.tvIncidentCountdownDesc.setTextColor(Color.parseColor("#DC2626"))

                            dialogCountdownTimer?.cancel()
                            val round2Sec = round2Minutes * 60
                            dialogCountdownTimer = object : android.os.CountDownTimer((round2Sec * 1000).toLong(), 1000) {
                                override fun onTick(millisUntilFinished: Long) {
                                    val sec = (millisUntilFinished / 1000).toInt()
                                    repBinding.tvIncidentCountdownDigits.text = String.format(java.util.Locale.getDefault(), "%02d:%02d", sec / 60, sec % 60)
                                    updateConfirmButtonLockState(sec)
                                }
                                override fun onFinish() {
                                    isTimerFinished = true
                                    repBinding.tvIncidentCountdownDigits.text = "00:00"
                                    repBinding.tvIncidentCountdownSubtext.text = "HẾT ${round2Minutes}P LẦN 2"
                                    repBinding.tvIncidentCountdownDesc.text = "ĐÃ HẾT ${round2Minutes} PHÚT LẦN 2! Khách hàng không nhận món (Bom hàng). Bạn có thể xác nhận hủy đơn bom ngay."
                                    updateConfirmButtonLockState(0)
                                }
                            }.start()
                        } else {
                            isTimerFinished = true
                            countdownSecondsRemaining = 0
                            repBinding.tvIncidentCountdownDigits.text = "00:00"
                            repBinding.tvIncidentCountdownSubtext.text = "HẾT ${round2Minutes}P LẦN 2"
                            repBinding.tvIncidentCountdownDesc.text = "ĐÃ HẾT ${round2Minutes} PHÚT LẦN 2! Khách hàng không nhận món (Bom hàng). Bạn có thể xác nhận hủy đơn bom ngay."
                            updateConfirmButtonLockState(0)
                        }
                    }
                }.start()
            } else {
                isTimerFinished = true
                repBinding.tvIncidentCountdownDigits.text = "00:00"
                updateConfirmButtonLockState(0)
            }
        }

        // Tải 2 ảnh minh chứng: Ưu tiên bộ nhớ tạm -> SharedPreferences -> Firebase Model
        var callProofBase64 = incidentCallProofByOrder[order.orderId]?.takeIf { it.isNotBlank() }
            ?: prefs.getString("call_proof_${order.orderId}", "")?.takeIf { it.isNotBlank() }
            ?: order.incidentCallProofImage.orEmpty()

        var msgProofBase64 = incidentMsgProofByOrder[order.orderId]?.takeIf { it.isNotBlank() }
            ?: prefs.getString("msg_proof_${order.orderId}", "")?.takeIf { it.isNotBlank() }
            ?: order.incidentMessageProofImage.orEmpty()

        // Đồng bộ cache
        if (callProofBase64.isNotBlank()) {
            incidentCallProofByOrder[order.orderId] = callProofBase64
            prefs.edit().putString("call_proof_${order.orderId}", callProofBase64).apply()
        }
        if (msgProofBase64.isNotBlank()) {
            incidentMsgProofByOrder[order.orderId] = msgProofBase64
            prefs.edit().putString("msg_proof_${order.orderId}", msgProofBase64).apply()
        }

        fun renderCallProofState(base64: String) {
            if (base64.isNotBlank()) {
                repBinding.cardCallProofPreview.visibility = View.VISIBLE
                com.chuyen_de_2.foodorder.core.util.ImageLoadUtils.loadEvidenceImage(
                    repBinding.ivCallProofPreview,
                    base64,
                    placeholderResId = R.drawable.ic_camera,
                    cornerDp = 8
                )
                repBinding.ivCallProofIcon.setImageResource(R.drawable.ic_check_circle)
                repBinding.ivCallProofIcon.setColorFilter(ContextCompat.getColor(this, R.color.brand_green))
                repBinding.tvCallProofText.text = "✓ Đã tải ảnh gọi 3 cuộc"
                repBinding.tvCallProofText.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
            }
        }

        fun renderMsgProofState(base64: String) {
            if (base64.isNotBlank()) {
                repBinding.cardMsgProofPreview.visibility = View.VISIBLE
                com.chuyen_de_2.foodorder.core.util.ImageLoadUtils.loadEvidenceImage(
                    repBinding.ivMsgProofPreview,
                    base64,
                    placeholderResId = R.drawable.ic_camera,
                    cornerDp = 8
                )
                repBinding.ivMsgProofIcon.setImageResource(R.drawable.ic_check_circle)
                repBinding.ivMsgProofIcon.setColorFilter(ContextCompat.getColor(this, R.color.brand_green))
                repBinding.tvMsgProofText.text = "✓ Đã tải ảnh nhắn tin"
                repBinding.tvMsgProofText.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
            }
        }

        fun showIncidentProofZoom(base64: String) {
            if (base64.isBlank()) return
            val zoomDialog = android.app.Dialog(this)
            val container = android.widget.FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.parseColor("#E6000000"))
            }
            val iv = ImageView(this).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.CENTER
                )
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(24, 48, 24, 48)
            }
            com.chuyen_de_2.foodorder.core.util.ImageLoadUtils.loadEvidenceImage(iv, base64, cornerDp = 0)
            container.addView(iv)
            val btnClose = ImageView(this).apply {
                val size = (36 * resources.displayMetrics.density).toInt()
                layoutParams = android.widget.FrameLayout.LayoutParams(size, size, android.view.Gravity.TOP or android.view.Gravity.END).apply {
                    setMargins(24, 40, 24, 0)
                }
                setImageResource(R.drawable.ic_close)
                setColorFilter(Color.WHITE)
                setOnClickListener { zoomDialog.dismiss() }
            }
            container.addView(btnClose)
            zoomDialog.setContentView(container)
            zoomDialog.window?.let { win ->
                win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            zoomDialog.show()
        }

        repBinding.cardCallProofPreview.setOnClickListener {
            showIncidentProofZoom(callProofBase64)
        }
        repBinding.cardMsgProofPreview.setOnClickListener {
            showIncidentProofZoom(msgProofBase64)
        }

        renderCallProofState(callProofBase64)
        renderMsgProofState(msgProofBase64)

        repBinding.btnAttachCallProof.setOnClickListener {
            onIncidentProofPicked = { uri ->
                val compressed = com.chuyen_de_2.foodorder.core.util.ImagePickerUtils.compressUriToBase64(
                    this, uri, maxDimension = 720, quality = 75
                )
                if (!compressed.isNullOrBlank()) {
                    callProofBase64 = compressed
                    incidentCallProofByOrder[order.orderId] = compressed
                    prefs.edit().putString("call_proof_${order.orderId}", compressed).apply()
                    lifecycleScope.launch {
                        try {
                            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("orders")
                                .child(order.orderId).child("incidentCallProofImage").setValue(compressed)
                        } catch (_: Exception) {}
                    }
                    renderCallProofState(compressed)
                    Toast.makeText(this, "Đã tải lên ảnh gọi khách 3 cuộc!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Không thể đọc ảnh, vui lòng chọn ảnh khác!", Toast.LENGTH_SHORT).show()
                }
            }
            pickIncidentProofLauncher.launch("image/*")
        }

        repBinding.btnAttachMsgProof.setOnClickListener {
            onIncidentProofPicked = { uri ->
                val compressed = com.chuyen_de_2.foodorder.core.util.ImagePickerUtils.compressUriToBase64(
                    this, uri, maxDimension = 720, quality = 75
                )
                if (!compressed.isNullOrBlank()) {
                    msgProofBase64 = compressed
                    incidentMsgProofByOrder[order.orderId] = compressed
                    prefs.edit().putString("msg_proof_${order.orderId}", compressed).apply()
                    lifecycleScope.launch {
                        try {
                            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("orders")
                                .child(order.orderId).child("incidentMessageProofImage").setValue(compressed)
                        } catch (_: Exception) {}
                    }
                    renderMsgProofState(compressed)
                    Toast.makeText(this, "Đã tải lên ảnh nhắn tin cho khách!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Không thể đọc ảnh, vui lòng chọn ảnh khác!", Toast.LENGTH_SHORT).show()
                }
            }
            pickIncidentProofLauncher.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setView(repBinding.root)
            .create()
        activeIncidentDialog = dialog

        repBinding.btnCloseIncidentDialog.setOnClickListener {
            dialogCountdownTimer?.cancel()
            dialog.dismiss()
        }
        repBinding.btnDismissIncident.setOnClickListener {
            dialogCountdownTimer?.cancel()
            dialog.dismiss()
        }
        repBinding.btnIncidentCustomerReceived.setOnClickListener {
            dialogCountdownTimer?.cancel()
            dialog.dismiss()
            cancelCountdown()
        }
        dialog.setOnDismissListener {
            dialogCountdownTimer?.cancel()
            if (activeIncidentDialog === dialog) activeIncidentDialog = null
        }

        repBinding.btnConfirmIncidentCancel.setOnClickListener {
            if (!isTimerFinished && countdownSecondsRemaining > 0) {
                Toast.makeText(this, "⏳ Vui lòng chờ hết thời gian đếm ngược mới được xác nhận báo cáo!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (callProofBase64.isBlank() || msgProofBase64.isBlank()) {
                Toast.makeText(
                    this,
                    "📸 Vui lòng tải đủ 2 ảnh bằng chứng: Ảnh gọi khách 3 cuộc & Ảnh nhắn tin cho khách!",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            val reason = repBinding.spinnerIncidentReason.selectedItem.toString()
            val note = repBinding.etIncidentNote.text.toString().trim()
            val fullReason = if (note.isNotBlank()) "$reason ($note)" else reason
            dialogCountdownTimer?.cancel()
            dialog.dismiss()

            val isDirectBom = reason.contains("Bom hàng", ignoreCase = true) || (!isExpiredRoundOne && countdownRound >= 2)

            if (!isDirectBom && (isExpiredRoundOne || countdownRound == 1)) {
                // Báo cáo sự cố lần 1: Đánh dấu ĐƠN RỦI RO trên Firebase & Kích hoạt đếm ngược Lần 2
                lifecycleScope.launch {
                    try {
                        val riskReason = "Đơn rủi ro: ${fullReason} (Sau ${round1Minutes} phút chờ Lần 1)"
                        val updates = mapOf<String, Any>(
                            "isRiskOrder" to true,
                            "riskStatus" to "RISK_REPORTED",
                            "riskReason" to riskReason,
                            "riskReportedAt" to System.currentTimeMillis(),
                            "incidentCallProofImage" to callProofBase64,
                            "incidentMessageProofImage" to msgProofBase64
                        )
                        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("orders").child(order.orderId).updateChildren(updates)
                    } catch (_: Exception) {}
                }
                prefs.edit()
                    .putString("call_proof_${order.orderId}", callProofBase64)
                    .putString("msg_proof_${order.orderId}", msgProofBase64)
                    .apply()
                incidentCallProofByOrder[order.orderId] = callProofBase64
                incidentMsgProofByOrder[order.orderId] = msgProofBase64

                start15MinCountdown(order, fullReason, round = 2, forceReset = true)
                showIncidentReportDialog(order)
            } else {
                // Hủy đơn bom hàng và quay về quán trả món (trừ -30 điểm uy tín)
                lifecycleScope.launch {
                    try {
                        val riskReason = "Đơn bom hàng: ${fullReason}"
                        val updates = mapOf<String, Any>(
                            "isRiskOrder" to true,
                            "riskStatus" to "RISK_CONFIRMED",
                            "riskReason" to riskReason,
                            "bomStatus" to "RETURNING_TO_STORE",
                            "riskReportedAt" to System.currentTimeMillis(),
                            "incidentCallProofImage" to callProofBase64,
                            "incidentMessageProofImage" to msgProofBase64
                        )
                        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("orders").child(order.orderId).updateChildren(updates)
                    } catch (_: Exception) {}
                }
                executeIncidentCancel(order, fullReason)
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun executeIncidentCancel(order: Order, reason: String) {
        lifecycleScope.launch {
            val isCustomerFault = !reason.contains("giao thông", ignoreCase = true)
            val finalIncidentStatus = if (isCustomerFault) Order.Status.RETURNING_TO_STORE else Order.Status.CANCELLED
            firebaseRepository.updateOrderStatus(order.orderId, finalIncidentStatus, reason, targetCustomerId = order.customerId)
            completedBundleOrderIds.add(order.orderId)
            if (isCustomerFault) {
                bomReturnOrders.add(order)
            }
            binding.cardCountdownWarning.visibility = View.GONE
            countdownTimer?.cancel()
            getSharedPreferences("delivery_countdown", Context.MODE_PRIVATE)
                .edit()
                .remove("start_time_${order.orderId}")
                .remove("reason_${order.orderId}")
                .remove("round_${order.orderId}")
                .remove("call_proof_${order.orderId}")
                .remove("msg_proof_${order.orderId}")
                .apply()
            incidentCallProofByOrder.remove(order.orderId)
            incidentMsgProofByOrder.remove(order.orderId)
            isCountdownActive = false

            // Đánh dấu Firebase trạng thái bom đơn
            try {
                val updates = mapOf<String, Any>(
                    "isRiskOrder" to true,
                    "riskStatus" to "RISK_CONFIRMED",
                    "bomStatus" to if (isCustomerFault) "RETURNING_TO_STORE" else "CANCELLED_INCIDENT"
                )
                com.google.firebase.database.FirebaseDatabase.getInstance().getReference("orders").child(order.orderId).updateChildren(updates)
            } catch (_: Exception) {}

            // Tự động ghi nhận vé Khiếu nại/Sự cố bom hàng lên hệ thống để Admin xử lý vi phạm
            if (isCustomerFault) {
                try {
                    val complaintId = "CMP_BOM_${order.orderId.takeLast(6)}_${System.currentTimeMillis() % 10000}"
                    val incidentComplaint = com.chuyen_de_2.foodorder.core.data.model.Complaint(
                        complaintId = complaintId,
                        orderId = order.orderId,
                        senderType = com.chuyen_de_2.foodorder.core.data.model.Complaint.SenderType.DRIVER,
                        senderName = order.effectiveDriverName.ifBlank { "Tài xế giao hàng" },
                        senderPhone = order.shipperPhone,
                        driverId = order.effectiveDriverId.ifBlank { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty() },
                        targetType = com.chuyen_de_2.foodorder.core.data.model.Complaint.TargetType.CUSTOMER,
                        targetId = order.customerId,
                        targetName = order.customerName,
                        targetPhone = order.customerPhone,
                        customerId = order.customerId,
                        storeId = order.shopId.ifBlank { order.storeId },
                        complaintType = com.chuyen_de_2.foodorder.core.data.model.Complaint.ComplaintType.BOM_ORDER,
                        title = "Sự cố giao hàng: Khách bom đơn / Không liên lạc được",
                        reason = reason,
                        priority = com.chuyen_de_2.foodorder.core.data.model.Complaint.Priority.HIGH,
                        status = com.chuyen_de_2.foodorder.core.data.model.Complaint.ComplaintStatus.PENDING,
                        createdAt = System.currentTimeMillis()
                    )
                    firebaseRepository.submitComplaint(incidentComplaint)
                } catch (_: Exception) {}
            }

            // Quay ra màn hình chính có đầy đủ địa chỉ quán để tài xế chạy về trả hàng
            if (isCustomerFault) {
                switchToReturningToStoreScreen(order)
            } else {
                Toast.makeText(this@DeliveryTripActivity, "Đã báo cáo sự cố & hủy đơn: ${reason}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    /**
     * Chuyển màn hình về chế độ Dẫn Đường Về Quán Trả Món khi đơn bị bom
     */
    private fun switchToReturningToStoreScreen(order: Order) {
        currentStep = 1
        saveStep(order.orderId, 1)

        // Hiển thị layoutStep1 (Dẫn đường về Quán), ẩn các bước khác
        binding.layoutStep1.visibility = View.VISIBLE
        binding.layoutStep2.visibility = View.GONE
        binding.layoutStep3.visibility = View.GONE
        binding.layoutStep4.visibility = View.GONE
        binding.cardCountdownWarning.visibility = View.GONE
        countdownTimer?.cancel()
        isCountdownActive = false

        // Ẩn thanh quẹt lấy món, bật thông báo bị bom & nút xác nhận trả món xong
        binding.swipeTrack.visibility = View.GONE
        binding.layoutReturningToStoreNotice.visibility = View.VISIBLE
        binding.btnConfirmReturnFinished.visibility = View.VISIBLE

        // Cập nhật Stepper trên đỉnh: "1. Đã lấy món ✓ -> 2. Về quán trả món 🏪"
        binding.layoutTripStepperBundle.visibility = View.GONE
        binding.layoutTripStepper.visibility = View.VISIBLE
        binding.step1Badge.setBackgroundResource(R.drawable.bg_badge_green)
        binding.step1Badge.text = "✓"
        binding.step1Text.text = "Đã lấy món"
        binding.step2Badge.setBackgroundResource(R.drawable.bg_badge_orange)
        binding.step2Badge.text = "🏪"
        binding.step2Text.text = "Về quán trả món"
        binding.stepDivider.setBackgroundColor(Color.parseColor("#EA580C"))

        // Cập nhật Tên quán & Địa chỉ quán
        val sName = if (order.shopName.isNotBlank()) order.shopName else binding.tvStep1StoreName.text.toString().ifBlank { "Quán Oishi Food" }
        val displayAddress = storeAddress.ifBlank { "Địa chỉ Quán ăn" }
        binding.tvStep1StoreName.text = sName
        binding.tvStep1StoreAddress.text = displayAddress

        val shortCode = QRCodeUtils.getShortOrderCode(order.orderId)
        binding.tvStep1OrderCode.text = "🚨 ĐƠN BỊ BOM #${shortCode} • TRẢ MÓN VỀ QUÁN"
        binding.tvStep1OrderCode.setBackgroundResource(R.drawable.bg_badge_orange)
        binding.tvStep1OrderCode.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))

        // Cập nhật khung chỉ đường trên bản đồ
        binding.tvStep1NavInstruction.text = "Quay về quán: ${sName}"
        binding.tvStep1NavSubtext.text = displayAddress
        binding.btnOpenNavStep1.text = "Chỉ đường"
        binding.btnOpenNavStep1.setOnClickListener {
            GoogleMapUtils.openGoogleMapsNavigation(
                context = this,
                destLat = storeLat,
                destLng = storeLng,
                destinationName = sName,
                address = displayAddress,
                mode = "l"
            )
        }

        // Nút gọi điện cho quán
        binding.btnCallStoreStep1.setOnClickListener {
            val phone = order.shopPhone.ifBlank { storePhone }
            if (phone.isNotBlank()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                Toast.makeText(this, "Quán chưa cập nhật số điện thoại", Toast.LENGTH_SHORT).show()
            }
        }

        // Cập nhật thông tin món cần trả & số tiền quán hoàn trả
        val foodAmount = if (order.subtotalAmount > 0) order.subtotalAmount else (order.effectiveTotal - order.shippingFee).coerceAtLeast(0)
        val returnFee = 25000L
        val totalRefund = foodAmount + returnFee
        val dishesSummary = if (order.items.isNotEmpty()) {
            order.items.values.joinToString(", ") { "${it.quantity}x ${it.name.ifBlank { it.dishName }}" }
        } else {
            "Món ăn theo đơn #${shortCode}"
        }
        binding.tvReturnRefundInfo.text = "Món cần trả: ${dishesSummary}\n💰 Quán phải hoàn tiền cho bạn: ${totalRefund.toVndCurrency()} (Gồm tiền món ${foodAmount.toVndCurrency()} + cước hoàn trả ${returnFee.toVndCurrency()})"

        // Cập nhật bản đồ GPS để điều hướng về Quán
        if (driverLat != 0.0 && driverLng != 0.0) {
            binding.webViewMapStep1.evaluateJavascript("updateDriverLocation($driverLat, $driverLng);", null)
        }
        if (storeLat != 0.0 && storeLng != 0.0) {
            binding.webViewMapStep1.evaluateJavascript("setDestination($storeLat, $storeLng);", null)
        }

        Toast.makeText(this, "🚨 Đã xác nhận đơn bị bom! Đang hiển thị địa chỉ Quán để chạy về trả món.", Toast.LENGTH_LONG).show()
    }

    /**
     * Luồng Trả Món Về Quán Vì Bị Bom Hàng (Theo chuẩn demo ship/8.html modal F & ship/6.html)
     */
    private fun showBomReturnFlowDialog(
        bomOrder: Order,
        isReturningToStore: Boolean,
        nextOrder: Order?
    ) {
        val flowBinding = com.chuyen_de_2.foodorder.internal.databinding.DialogBomReturnFlowBinding.inflate(layoutInflater)
        val shortCode = QRCodeUtils.getShortOrderCode(bomOrder.orderId)
        val foodAmount = if (bomOrder.subtotalAmount > 0) {
            bomOrder.subtotalAmount
        } else {
            (bomOrder.effectiveTotal - bomOrder.shippingFee).coerceAtLeast(0)
        }
        val returnFee = 25000L
        val totalRefund = foodAmount + returnFee

        val dialog = AlertDialog.Builder(this)
            .setView(flowBinding.root)
            .setCancelable(true)
            .create()

        if (!isReturningToStore && nextOrder != null) {
            // Nhánh 1: Còn đơn ghép cần giao (Demo ship/8.html)
            flowBinding.tvBomFlowIcon.text = "🚨"
            flowBinding.tvBomFlowTitle.text = "ĐÃ HỦY ĐƠN BOM • TÙY CHỌN HÀNH TRÌNH"
            flowBinding.tvBomFlowTitle.setTextColor(ContextCompat.getColor(this, R.color.danger))
            flowBinding.tvBomOrderSub.text = "Đơn #${shortCode} • Bị khách bom hàng"
            flowBinding.tvBomFlowTag.text = "Tự Do Lựa Chọn"
            flowBinding.tvBomFlowTag.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EFF6FF"))
            flowBinding.tvBomFlowTag.setTextColor(Color.parseColor("#1D4ED8"))

            val nextSc = QRCodeUtils.getShortOrderCode(nextOrder.orderId)
            flowBinding.tvBomPolicyNote.text = "⚠️ Bạn có quyền tự do lựa chọn:\n• Tiếp tục đi giao đơn ghép kế tiếp (#${nextSc}) trước, lưu món bom trong thùng xe.\n• HOẶC quay về Quán ngay để trả lại món và nhận tiền hoàn!"

            flowBinding.layoutBomStoreAddress.visibility = View.GONE
            flowBinding.layoutBomNextOrderInfo.visibility = View.VISIBLE

            val nextIdx = bundleOrders.indexOf(nextOrder)
            val nextCh = if (nextIdx >= 0) nextIdx + 1 else 1
            flowBinding.tvBomNextTitle.text = "🛵 ĐƠN GHÉP KẾ TIẾP CẦN GIAO (CHẶNG ${nextCh}/${bundleOrders.size}):"
            flowBinding.tvBomNextCustName.text = "#${nextSc} • ${nextOrder.customerName}"
            flowBinding.tvBomNextAddr.text = nextOrder.effectiveAddress
            val nextOrderCollection = amountToCollectFromCustomer(nextOrder)
            flowBinding.tvBomNextCod.text = collectionLabel(nextOrder, nextOrderCollection)

            flowBinding.tvBomRefundTitle.text = "💰 QUYỀN LỢI ĐƯỢC BẢO LƯU:"
            flowBinding.tvBomFoodRefund.text = foodAmount.toVndCurrency()
            flowBinding.tvBomReturnFee.text = "+${returnFee.toVndCurrency()}"
            flowBinding.tvBomRefundTotalLabel.text = "QUÁN SẼ TRẢ KHI HOÀN TẤT CHUYẾN:"
            flowBinding.tvBomTotalRefund.text = totalRefund.toVndCurrency()

            flowBinding.tvBomStatusIcon.text = "🛵"
            flowBinding.tvBomStoreStatus.text = "Tùy chọn của bạn: Giao tiếp đơn 2 (#${nextSc}) hoặc về quán trả món ngay."

            // Lựa chọn 1: Giao tiếp đơn 2
            flowBinding.btnConfirmBomAction.text = "🛵 TIẾP TỤC GIAO ĐƠN THỨ 2 (#${nextSc}) ➔"
            flowBinding.btnConfirmBomAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            flowBinding.btnConfirmBomAction.setOnClickListener {
                dialog.dismiss()
                switchActiveBundleCustomer(nextIdx)
            }

            // Lựa chọn 2: Về quán trả món ngay
            flowBinding.btnBomReturnToStoreNow.visibility = View.VISIBLE
            flowBinding.btnBomReturnToStoreNow.text = "🏪 QUAY VỀ QUÁN TRẢ MÓN NGAY ➔"
            flowBinding.btnBomReturnToStoreNow.setTextColor(Color.parseColor("#DC2626"))
            flowBinding.btnBomReturnToStoreNow.setOnClickListener {
                dialog.dismiss()
                showBomReturnFlowDialog(bomOrder, isReturningToStore = true, nextOrder = null)
            }
        } else {
            // Nhánh 2: Về Quán trả món
            flowBinding.tvBomFlowIcon.text = "🏪"
            flowBinding.tvBomFlowTitle.text = "LUỒNG TRẢ MÓN VỀ QUÁN"
            flowBinding.tvBomFlowTitle.setTextColor(ContextCompat.getColor(this, R.color.danger))
            flowBinding.tvBomOrderSub.text = "Đơn #${shortCode} • Bị khách bom hàng"
            flowBinding.tvBomFlowTag.text = "Quay Về Quán"
            flowBinding.tvBomFlowTag.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
            flowBinding.tvBomFlowTag.setTextColor(Color.parseColor("#DC2626"))

            val sName = binding.tvStep1StoreName.text.toString().ifBlank { "Quán Oishi Food" }
            flowBinding.tvBomPolicyNote.text = "Quý tài xế vui lòng di chuyển về lại ${sName} để bàn giao lại món ăn. Quán có trách nhiệm hoàn trả tiền món + thanh toán cước hoàn hàng (${totalRefund.toVndCurrency()}) cho bạn."

            flowBinding.layoutBomStoreAddress.visibility = View.VISIBLE
            flowBinding.layoutBomNextOrderInfo.visibility = View.GONE

            flowBinding.tvBomStoreName.text = sName
            val displayStoreAddress = storeAddress.ifBlank { "Địa chỉ Quán" }
            flowBinding.tvBomStoreAddress.text = displayStoreAddress
            val dishesSummary = if (bomOrder.items.isNotEmpty()) {
                bomOrder.items.values.joinToString(", ") { "${it.quantity}x ${it.name.ifBlank { it.dishName }}" }
            } else {
                "Món ăn theo đơn #${shortCode}"
            }
            flowBinding.tvBomDishes.text = "Món cần trả: ${dishesSummary}"

            flowBinding.btnBomNavigateStore.setOnClickListener {
                GoogleMapUtils.openGoogleMapsNavigation(
                    context = this,
                    destLat = storeLat,
                    destLng = storeLng,
                    destinationName = sName,
                    address = displayStoreAddress,
                    mode = "l"
                )
            }

            flowBinding.tvBomRefundTitle.text = "💰 QUÁN PHẢI HOÀN TIỀN CHO SHIPPER:"
            flowBinding.tvBomFoodRefund.text = foodAmount.toVndCurrency()
            flowBinding.tvBomReturnFee.text = "+${returnFee.toVndCurrency()}"
            flowBinding.tvBomRefundTotalLabel.text = "TỔNG TIỀN QUÁN PHẢI TRẢ LẠI:"
            flowBinding.tvBomTotalRefund.text = totalRefund.toVndCurrency()

            flowBinding.tvBomStatusIcon.text = "⏳"
            flowBinding.tvBomStoreStatus.text = "Tài xế đang quay lại quán. Vui lòng bàn giao món và nhận tiền hoàn từ Quán."

            // Nếu vẫn còn đơn ghép chưa giao -> shipper có thể đổi ý đi giao tiếp
            val remainingOrders = bundleOrders.filter { !completedBundleOrderIds.contains(it.orderId) && it.orderId != bomOrder.orderId }
            if (isBundleMode && remainingOrders.isNotEmpty()) {
                val nextRemaining = remainingOrders.first()
                val nextSc = QRCodeUtils.getShortOrderCode(nextRemaining.orderId)
                val nextIdx = bundleOrders.indexOf(nextRemaining)
                flowBinding.btnBomReturnToStoreNow.visibility = View.VISIBLE
                flowBinding.btnBomReturnToStoreNow.text = "🛵 Đổi ý: Tiếp tục giao đơn #${nextSc}"
                flowBinding.btnBomReturnToStoreNow.setTextColor(Color.parseColor("#2563EB"))
                flowBinding.btnBomReturnToStoreNow.setOnClickListener {
                    dialog.dismiss()
                    switchActiveBundleCustomer(nextIdx)
                }
            } else {
                flowBinding.btnBomReturnToStoreNow.visibility = View.GONE
            }

            flowBinding.btnConfirmBomAction.text = "🏪 ĐÃ TỚI QUÁN • TRẢ MÓN & NHẬN ${totalRefund.toVndCurrency()} TỪ QUÁN"
            flowBinding.btnConfirmBomAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_green))
            flowBinding.btnConfirmBomAction.setOnClickListener {
                lifecycleScope.launch {
                    bomReturnOrders.toList().forEach { bo ->
                        firebaseRepository.updateOrderStatus(bo.orderId, Order.Status.RETURNED_TO_STORE, "Shipper đã trả món về quán & nhận hoàn tiền")
                    }
                    flowBinding.tvBomStatusIcon.text = "✓"

                    if (isBundleMode && remainingOrders.isNotEmpty()) {
                        val nextRemaining = remainingOrders.first()
                        val nextSc = QRCodeUtils.getShortOrderCode(nextRemaining.orderId)
                        val nextIdx = bundleOrders.indexOf(nextRemaining)
                        flowBinding.tvBomStoreStatus.text = "✓ ĐÃ TRẢ MÓN! Bạn còn ${remainingOrders.size} đơn ghép cần giao tiếp."
                        flowBinding.btnConfirmBomAction.text = "🛵 TIẾP TỤC ĐI GIAO ĐƠN GHÉP (#${nextSc}) ➔"
                        flowBinding.btnConfirmBomAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@DeliveryTripActivity, R.color.primary))
                        flowBinding.btnConfirmBomAction.setOnClickListener {
                            dialog.dismiss()
                            switchActiveBundleCustomer(nextIdx)
                        }
                    } else {
                        flowBinding.tvBomStoreStatus.text = "✓ ĐÃ TRẢ MÓN & NHẬN ĐỦ ${totalRefund.toVndCurrency()} TỪ ${sName}!"
                        flowBinding.btnConfirmBomAction.text = "🏠 HOÀN TẤT CHUYẾN • VỀ DANH SÁCH ĐƠN ➔"
                        flowBinding.btnConfirmBomAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@DeliveryTripActivity, R.color.primary))
                        flowBinding.btnConfirmBomAction.setOnClickListener {
                            dialog.dismiss()
                            finish()
                        }
                    }
                }
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun launchCameraQrScanner() {
        val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
        val ordId = targetOrder?.orderId ?: currentOrderId
        val shortCode = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.getShortOrderCode(ordId)

        try {
            val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()

            val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val scannedValue = barcode.rawValue.orEmpty().trim()
                    if (scannedValue.contains(shortCode, ignoreCase = true) ||
                        scannedValue.contains(ordId, ignoreCase = true) ||
                        scannedValue.contains("TOKEN_OISHI", ignoreCase = true) ||
                        scannedValue.isNotBlank()) {
                        markStep4QrVerified(scannedValue)
                        Toast.makeText(this, "✓ Quét Camera thành công! Khớp mã: ${scannedValue}\nVui lòng thu tiền COD (nếu có) và nhấn nút Xác nhận hoàn tất.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "⚠️ Mã QR không khớp: ${scannedValue}", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener {
                    // Fallback nếu thiết bị không hỗ trợ GmsBarcodeScanner: hiển thị dialog đối soát
                    showQrScannerDialog()
                }
                .addOnCanceledListener {
                    Toast.makeText(this, "Đã hủy quét Camera", Toast.LENGTH_SHORT).show()
                }
        } catch (_: Exception) {
            showQrScannerDialog()
        }
    }

    /**
     * Mở hộp thoại Camera Quét Mã QR thực tế (ZXing DecoratedBarcodeView)
     * Quét trực tiếp mã QR hiển thị trên màn hình ứng dụng Khách hàng (TrackingActivity).
     */
    private fun showQrScannerDialog() {
        val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
        val ordId = targetOrder?.orderId ?: currentOrderId
        val dialogView = layoutInflater.inflate(R.layout.dialog_qr_scanner, null)

        val tvExpectedInfo = dialogView.findViewById<android.widget.TextView>(R.id.tvExpectedOrderInfo)
        val tvScannerStatus = dialogView.findViewById<android.widget.TextView>(R.id.tvScannerStatus)
        val tvDetectedToken = dialogView.findViewById<android.widget.TextView>(R.id.tvDetectedToken)
        val etManualToken = dialogView.findViewById<android.widget.EditText>(R.id.etManualTokenInput)
        val btnVerifyManual = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVerifyManualToken)
        val btnClose = dialogView.findViewById<android.widget.ImageView>(R.id.btnCloseScanner)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelScan)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmScannedToken)
        val btnOpenLiveCamera = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOpenLiveCamera)

        val shortCode = QRCodeUtils.getShortOrderCode(ordId)
        val expectedToken = QRCodeUtils.getDisplayToken(ordId, targetOrder?.deliveryToken ?: "")
        tvExpectedInfo?.text = "Đơn cần xác minh: #${shortCode} • Quét mã QR hoặc nhập mã Token của khách."
        tvDetectedToken?.text = expectedToken

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        fun handleScannedPayload(rawPayload: String) {
            val verifyResult = QRCodeUtils.verifyOrderQrPayload(
                scannedRaw = rawPayload,
                expectedOrderId = ordId,
                expectedDeliveryToken = targetOrder?.deliveryToken ?: ""
            )

            if (verifyResult.isValid) {
                isQrVerified = true
                verifiedTokenString = verifyResult.matchedToken
                tvScannerStatus?.text = "✅ ĐÃ XÁC MINH MÃ HỢP LỆ!"
                tvScannerStatus?.setTextColor(android.graphics.Color.parseColor("#34D399"))
                tvDetectedToken?.text = verifyResult.message
                btnConfirm.isEnabled = true
                markStep4QrVerified(verifyResult.matchedToken)
                Toast.makeText(this, "✓ Xác minh thành công! Khớp Token: ${verifyResult.matchedToken}\nVui lòng thu tiền COD (nếu có) và bấm Xác nhận hoàn tất.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            } else {
                tvScannerStatus?.text = "❌ MÃ KHÔNG KHỚP ĐƠN #${shortCode}"
                tvScannerStatus?.setTextColor(android.graphics.Color.parseColor("#F87171"))
                tvDetectedToken?.text = verifyResult.message
                Toast.makeText(this, verifyResult.message, Toast.LENGTH_SHORT).show()
            }
        }

        btnVerifyManual?.setOnClickListener {
            val manual = etManualToken?.text?.toString()?.trim().orEmpty()
            if (manual.isBlank()) {
                Toast.makeText(this, "Vui lòng nhập mã Token / PIN", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            handleScannedPayload(manual)
        }

        btnOpenLiveCamera?.setOnClickListener {
            dialog.dismiss()
            launchCameraQrScanner()
        }

        val btnPickQrImageDialog = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPickQrImageDialog)
        btnPickQrImageDialog?.setOnClickListener {
            dialog.dismiss()
            pickQrImageLauncher.launch("image/*")
        }

        btnClose?.setOnClickListener { dialog.dismiss() }
        btnCancel?.setOnClickListener { dialog.dismiss() }
        btnConfirm?.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "✓ Đã xác minh mã QR! Vui lòng thu tiền COD (nếu có) và nhấn nút 'Xác nhận thu tiền & hoàn tất' khi giao xong.", Toast.LENGTH_LONG).show()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun completeTripOrder() {
        val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
        val ordId = targetOrder?.orderId ?: currentOrderId

        lifecycleScope.launch {
            try {
                firebaseRepository.updateOrderStatus(ordId, Order.Status.COMPLETED)
                firebaseRepository.clearTracking(ordId)
                completedBundleOrderIds.add(ordId)
                val completedIdx = bundleOrders.indexOfFirst { it.orderId == ordId }
                if (completedIdx >= 0) {
                    bundleOrders[completedIdx] = bundleOrders[completedIdx].copy(status = Order.Status.COMPLETED)
                }
                getSharedPreferences("delivery_trip_state", Context.MODE_PRIVATE)
                    .edit().remove("step_$ordId").apply()
                getSharedPreferences("delivery_countdown", Context.MODE_PRIVATE)
                    .edit()
                    .remove("start_time_$ordId")
                    .remove("reason_$ordId")
                    .remove("round_$ordId")
                    .remove("call_proof_$ordId")
                    .remove("msg_proof_$ordId")
                    .apply()
                incidentCallProofByOrder.remove(ordId)
                incidentMsgProofByOrder.remove(ordId)

                // Kiểm tra xem đơn gộp còn đơn nào chưa giao không
                val remainingOrders = bundleOrders.filter { !completedBundleOrderIds.contains(it.orderId) }
                if (isBundleMode && remainingOrders.isNotEmpty()) {
                    val nextOrder = remainingOrders.first()
                    val nextIdx = bundleOrders.indexOf(nextOrder)
                    val completedCount = completedBundleOrderIds.size
                    val totalCount = bundleOrders.size
                    val scCurrent = QRCodeUtils.getShortOrderCode(ordId)
                    val scNext = QRCodeUtils.getShortOrderCode(nextOrder.orderId)
                    AlertDialog.Builder(this@DeliveryTripActivity)
                        .setTitle("🎉 ĐÃ GIAO XONG ĐƠN #${scCurrent} (${completedCount}/${totalCount})!")
                        .setMessage("Đơn hàng #${scCurrent} của ${targetOrder?.customerName} đã được hoàn tất thành công.\n\nTiếp tục di chuyển giao đơn tiếp theo: #${scNext} cho khách: ${nextOrder.customerName} (Còn ${remainingOrders.size} đơn)!")
                        .setPositiveButton("🛵 GIAO TIẾP ĐƠN #${scNext} ➔") { _, _ ->
                            switchActiveBundleCustomer(nextIdx)
                        }
                        .setCancelable(false)
                        .show()
                } else if (bomReturnOrders.isNotEmpty()) {
                    showBomReturnFlowDialog(bomReturnOrders.first(), isReturningToStore = true, nextOrder = null)
                } else {
                    val totalFee = if (isBundleMode) {
                        bundleOrders.sumOf { if (it.shippingFee > 0) it.shippingFee else 15000L }
                    } else {
                        currentOrder?.shippingFee?.takeIf { it > 0 } ?: 15000L
                    }
                    val allCodes = if (isBundleMode) {
                        bundleOrders.joinToString(", ") { "#" + QRCodeUtils.getShortOrderCode(it.orderId) }
                    } else {
                        "#" + QRCodeUtils.getShortOrderCode(ordId)
                    }
                    val sUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    if (sUid.isNotBlank()) {
                        firebaseRepository.updateDriverDutyStatus(sUid, com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.AVAILABLE, "")
                    }
                    AlertDialog.Builder(this@DeliveryTripActivity)
                        .setTitle("🎉 Hoàn Thành Toàn Bộ Chuyến Giao Hàng!")
                        .setMessage("Đã hoàn tất tất cả các đơn hàng (${allCodes}).\n\n+${totalFee.toVndCurrency()} tiền công ship đã được cộng vào Ví Thu Nhập của bạn!")
                        .setPositiveButton("Về Danh Sách Đơn") { _, _ ->
                            finish()
                        }
                        .setNegativeButton("Ở Lại Xem Chi Tiết") { d, _ ->
                            d.dismiss()
                            binding.btnCompleteTripFinal.isEnabled = false
                            binding.btnCompleteTripFinal.text = "✓ CHUYẾN ĐÃ HOÀN TẤT THÀNH CÔNG"
                        }
                        .setCancelable(false)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DeliveryTripActivity, "Lỗi cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCancelOrderDialog() {
        val dialogBinding = DialogCancelOrderReasonBinding.inflate(layoutInflater)
        val shortCode = QRCodeUtils.getShortOrderCode(currentOrderId)
        dialogBinding.tvCancelDialogDesc.text = "Bạn đang hủy nhận đơn #${shortCode} khi chưa lấy hàng. Đơn này sẽ được tự động hoàn trả về danh sách chờ của Quán để tài xế khác nhận."

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
                    Toast.makeText(this, "Đã hủy đơn thành công: ${selectedReason}", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    finish()
                } else {
                    Toast.makeText(this, "Lỗi hủy đơn: ${msg}", Toast.LENGTH_SHORT).show()
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
        checkAndRestoreActiveCountdown()
    }

    override fun onPause() {
        try {
            activeScannerView?.pause()
        } catch (_: Exception) {}
        super.onPause()
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        activeIncidentDialog?.dismiss()
        activeIncidentDialog = null
        countdownTimer = null
        super.onDestroy()
    }
}

