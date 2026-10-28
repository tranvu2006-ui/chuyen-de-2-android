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
                                lifecycleScope.launch {
                                    if (isBundleMode && bundleOrders.isNotEmpty()) {
                                        bundleOrders.toList().forEach { o ->
                                            firebaseRepository.updateOrderStatus(o.orderId, Order.Status.DELIVERING)
                                        }
                                        Toast.makeText(this@DeliveryTripActivity, "🛵 Đã lấy đủ tất cả ${bundleOrders.size} đơn gộp! Bắt đầu giao chặng 1", Toast.LENGTH_SHORT).show()
                                    } else {
                                        firebaseRepository.updateOrderStatus(currentOrderId, Order.Status.DELIVERING)
                                        Toast.makeText(this@DeliveryTripActivity, "🛵 Đã lấy hàng thành công! Xuất phát giao hàng", Toast.LENGTH_SHORT).show()
                                    }
                                    updateStepUI(3)
                                    fetchRealShipperLocation()
                                    thumb.translationX = 0f
                                    label.alpha = 1f
                                    isConfirmed = false
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
                                updateStepUI(4)
                                val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
                                targetOrder?.let {
                                    lifecycleScope.launch {
                                        firebaseRepository.updateOrderStatus(it.orderId, Order.Status.ARRIVED_AT_CUSTOMER)
                                        Toast.makeText(this@DeliveryTripActivity, "📍 Đã đến nhà khách! Chờ khách đối soát mã nhận món", Toast.LENGTH_SHORT).show()
                                    }
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

    private fun syncStepFromOrderStatus(status: String) {
        val targetStep = when (status) {
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

    private fun observeOrderDetails() {
        lifecycleScope.launch {
            firebaseRepository.observeOrder(currentOrderId).collectLatest { order ->
                if (order != null) {
                    val isFirstLoad = (currentOrder == null)
                    currentOrder = order
                    if (!isBundleMode) {
                        bindOrderData(order)
                    }
                    syncStepFromOrderStatus(order.status)
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
                     o.status == Order.Status.ARRIVED_AT_CUSTOMER) &&
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
            binding.tvTripOrderCode.text = "Đơn #ORD-$shortCode"
            binding.tvTripBundleBadge.visibility = View.GONE
            binding.tvStep1OrderCode.text = "ĐƠN CẦN LẤY: #ORD-$shortCode"
            binding.tvStep2SingleOrderCode.text = "MÃ ĐƠN: #ORD-$shortCode"
        } else {
            val idx = bundleOrders.indexOfFirst { it.orderId == order.orderId }
            val ch = if (idx >= 0) idx + 1 else 1
            binding.tvTripOrderCode.text = "#ORD-$shortCode (Chặng $ch/${bundleOrders.size})"
            binding.tvTripBundleBadge.visibility = View.VISIBLE
            binding.tvTripBundleBadge.text = "Ghép ${bundleOrders.size} đơn"

            val allCodes = bundleOrders.joinToString(", ") { "#ORD-" + QRCodeUtils.getShortOrderCode(it.orderId) }
            binding.tvStep1OrderCode.text = "CẦN LẤY ${bundleOrders.size} ĐƠN: $allCodes"
            binding.tvStep2SingleOrderCode.text = "MÃ ĐƠN: #ORD-$shortCode"
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

        binding.tvStep3OrderCode.text = "ĐANG GIAO: #ORD-$shortCode"
        binding.tvStep4OrderCode.text = "XÁC MINH ĐƠN HÀNG: #ORD-$shortCode"

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
        binding.tvStep3DeliveryAddress.text = "📍 Địa chỉ: $dest"

        if (order.note.isNotBlank()) {
            binding.tvStep3CustomerNote.visibility = View.VISIBLE
            binding.tvStep3CustomerNote.text = "📝 Ghi chú: \"${order.note}\""
        } else {
            binding.tvStep3CustomerNote.visibility = View.GONE
        }

        val fee = order.shippingFee
        val totalPayment = if (order.totalPayment > 0) order.totalPayment else (order.total + fee)

        val isCod = order.paymentMethod.equals("COD", ignoreCase = true)
        if (isCod) {
            binding.tvStep3PaymentMethodBadge.text = "💵 Thu tiền mặt COD: ${totalPayment.toVndCurrency()}"
            binding.tvStep4CodCollection.text = "Thu Tiền Mặt COD: ${totalPayment.toVndCurrency()}"
        } else {
            binding.tvStep3PaymentMethodBadge.text = "✓ ĐÃ THANH TOÁN ONLINE (VIETQR/MOMO) - KHÔNG THU TIỀN"
            binding.tvStep4CodCollection.text = "ĐÃ THANH TOÁN TRƯỚC (COD: 0 đ)"
        }

        val phoneDisplay = if (order.customerPhone.isNotBlank()) order.customerPhone else "Khách hàng"
        binding.tvStep4Summary.text = "Đơn #ORD-$shortCode\nKhách: $cusName • $phoneDisplay\nĐịa chỉ: $dest"

        binding.tvStep3NavInstruction.text = "Điểm đến: $dest"
        binding.tvStep3NavSubtext.text = "Giao cho: $cusName (#ORD-$shortCode)"

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
        val totalCod = bundleOrders.filter { it.paymentMethod.equals("COD", true) }
            .sumOf { if (it.totalPayment > 0) it.totalPayment else (it.total + it.shippingFee) }

        binding.tvBundleTotalFee.text = "+${totalFee.toVndCurrency()}"
        binding.tvBundleOrderCount.text = "${bundleOrders.size} đơn"
        binding.tvBundleTotalCod.text = totalCod.toVndCurrency()

        // Xóa danh sách cũ và render động từng card đơn
        binding.layoutBundleCardsContainer.removeAllViews()
        bundleOrders.forEachIndexed { index, order ->
            val cardBinding = com.chuyen_de_2.foodorder.internal.databinding.ItemBundleOrderCardBinding.inflate(
                layoutInflater, binding.layoutBundleCardsContainer, false
            )

            val shortCode = if (order.orderId.length > 5) order.orderId.takeLast(6).uppercase() else order.orderId
            cardBinding.tvBundleOrderCode.text = "#ORD-$shortCode"
            cardBinding.tvBundleSequenceTag.text = "Chặng ${index + 1}"

            val isCod = order.paymentMethod.equals("COD", true)
            val fee = if (order.shippingFee > 0) order.shippingFee else 25000L
            val totalPayment = if (order.totalPayment > 0) order.totalPayment else (order.total + fee)

            if (isCod) {
                cardBinding.tvBundlePaymentBadge.text = "💵 COD: ${totalPayment.toVndCurrency()}"
                cardBinding.tvBundlePaymentBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                cardBinding.tvBundlePaymentBadge.setTextColor(ContextCompat.getColor(this, R.color.primary))
            } else {
                cardBinding.tvBundlePaymentBadge.text = "✓ ĐÃ THANH TOÁN (VIETQR)"
                cardBinding.tvBundlePaymentBadge.setBackgroundResource(R.drawable.bg_badge_green)
                cardBinding.tvBundlePaymentBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
            }

            cardBinding.tvBundleOrderFee.text = "+${fee.toVndCurrency()}"

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
            cardBinding.tvBundleCustomerName.text = "Khách: $cusName"
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
                    Toast.makeText(this, "✓ Đã kiểm đủ món đơn #ORD-$shortCode", Toast.LENGTH_SHORT).show()
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
            binding.choiceName1.text = "1. ${order1.customerName.ifBlank { "Khách 1" }} (#ORD-$sc1)"
            binding.choiceAddr1.text = order1.address.ifBlank { "Địa chỉ 1" }
            val isCod1 = order1.paymentMethod.equals("COD", true)
            binding.choicePayment1.text = if (isCod1) "COD ${(if (order1.totalPayment > 0) order1.totalPayment else (order1.total + order1.shippingFee)).toVndCurrency()}" else "0 đ (MoMo)"
            binding.choicePayment1.setTextColor(if (isCod1) ContextCompat.getColor(this, R.color.primary) else ContextCompat.getColor(this, R.color.brand_green))

            val isDone1 = completedBundleOrderIds.contains(order1.orderId)
            if (isDone1) {
                binding.choiceBadge1.text = "✓ Đã giao"
                binding.choiceBadge1.setBackgroundResource(R.drawable.bg_badge_green)
                binding.btnChoiceCust1.setBackgroundResource(R.drawable.bg_choice_card_idle)
            } else if (activeBundleIndex == 0) {
                binding.choiceBadge1.text = "⚡ Đang giao"
                binding.choiceBadge1.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.btnChoiceCust1.setBackgroundResource(R.drawable.bg_choice_card_active)
            } else {
                binding.choiceBadge1.text = "Giao sau"
                binding.choiceBadge1.setBackgroundResource(R.drawable.bg_badge_duty_off)
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
            binding.choiceName2.text = "2. ${order2.customerName.ifBlank { "Khách 2" }} (#ORD-$sc2)"
            binding.choiceAddr2.text = order2.address.ifBlank { "Địa chỉ 2" }
            val isCod2 = order2.paymentMethod.equals("COD", true)
            binding.choicePayment2.text = if (isCod2) "COD ${(if (order2.totalPayment > 0) order2.totalPayment else (order2.total + order2.shippingFee)).toVndCurrency()}" else "0 đ (MoMo)"
            binding.choicePayment2.setTextColor(if (isCod2) ContextCompat.getColor(this, R.color.primary) else ContextCompat.getColor(this, R.color.brand_green))

            val isDone2 = completedBundleOrderIds.contains(order2.orderId)
            if (isDone2) {
                binding.choiceBadge2.text = "✓ Đã giao"
                binding.choiceBadge2.setBackgroundResource(R.drawable.bg_badge_green)
                binding.btnChoiceCust2.setBackgroundResource(R.drawable.bg_choice_card_idle)
            } else if (activeBundleIndex == 1) {
                binding.choiceBadge2.text = "⚡ Đang giao"
                binding.choiceBadge2.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.btnChoiceCust2.setBackgroundResource(R.drawable.bg_choice_card_active)
            } else {
                binding.choiceBadge2.text = "Giao sau"
                binding.choiceBadge2.setBackgroundResource(R.drawable.bg_badge_duty_off)
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
            binding.choiceName3.text = "3. ${order3.customerName.ifBlank { "Khách 3" }} (#ORD-$sc3)"
            binding.choiceAddr3.text = order3.address.ifBlank { "Địa chỉ 3" }
            val isCod3 = order3.paymentMethod.equals("COD", true)
            binding.choicePayment3.text = if (isCod3) "COD ${(if (order3.totalPayment > 0) order3.totalPayment else (order3.total + order3.shippingFee)).toVndCurrency()}" else "0 đ (MoMo)"
            binding.choicePayment3.setTextColor(if (isCod3) ContextCompat.getColor(this, R.color.primary) else ContextCompat.getColor(this, R.color.brand_green))

            val isDone3 = completedBundleOrderIds.contains(order3.orderId)
            if (isDone3) {
                binding.choiceBadge3.text = "✓ Đã giao"
                binding.choiceBadge3.setBackgroundResource(R.drawable.bg_badge_green)
                binding.btnChoiceCust3.setBackgroundResource(R.drawable.bg_choice_card_idle)
            } else if (activeBundleIndex == 2) {
                binding.choiceBadge3.text = "⚡ Đang giao"
                binding.choiceBadge3.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.btnChoiceCust3.setBackgroundResource(R.drawable.bg_choice_card_active)
            } else {
                binding.choiceBadge3.text = "Giao sau"
                binding.choiceBadge3.setBackgroundResource(R.drawable.bg_badge_duty_off)
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
        binding.stepBundleCust1Text.text = if (order1 != null) "${order1.customerName.take(6).ifBlank { "Khách 1" }} (#$sc1)" else "Khách 1"
        binding.stepBundleCust2Text.text = if (order2 != null) "${order2.customerName.take(6).ifBlank { "Khách 2" }} (#$sc2)" else "Khách 2"

        val isDone1 = order1 != null && completedBundleOrderIds.contains(order1.orderId)
        val isDone2 = order2 != null && completedBundleOrderIds.contains(order2.orderId)

        // Trạng thái Khách 1
        if (isDone1) {
            binding.stepBundleCust1Badge.setBackgroundResource(R.drawable.bg_badge_green)
            binding.stepBundleCust1Badge.text = "✓"
            binding.stepBundleCust1Text.setTextColor(colorGreen)
            binding.stepBundleDivider1.setBackgroundColor(colorGreen)
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
            binding.stepBundleCust3Text.text = "${order3.customerName.take(6).ifBlank { "Khách 3" }} (#$sc3)"

            val isDone3 = completedBundleOrderIds.contains(order3.orderId)
            if (isDone3) {
                binding.stepBundleCust3Badge.setBackgroundResource(R.drawable.bg_badge_green)
                binding.stepBundleCust3Badge.text = "✓"
                binding.stepBundleCust3Text.setTextColor(colorGreen)
                binding.stepBundleDivider3.setBackgroundColor(colorGreen)
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

        activeBundleIndex = index
        currentOrderId = targetOrder.orderId
        currentOrder = targetOrder
        resetStep4QrUI()
        bindCustomerQuickSwitchStep3()
        bindOrderData(targetOrder)

        if (currentStep >= 3) {
            val targetStep = if (targetOrder.status == Order.Status.ARRIVED_AT_CUSTOMER) 4 else 3
            updateStepUI(targetStep)
        }

        val shortCode = QRCodeUtils.getShortOrderCode(targetOrder.orderId)
        // Toast nổi thông báo đổi tuyến theo 6.html
        binding.tvRouteToastText.text = "🛵 Đang giao đơn #ORD-$shortCode (${targetOrder.customerName})!"
        binding.layoutRouteToast.visibility = View.VISIBLE
        binding.layoutRouteToast.postDelayed({
            binding.layoutRouteToast.visibility = View.GONE
        }, 2200)
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
            finish()
        }

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
            lifecycleScope.launch {
                if (isBundleMode && bundleOrders.isNotEmpty()) {
                    bundleOrders.toList().forEach { o ->
                        firebaseRepository.updateOrderStatus(o.orderId, Order.Status.DELIVERING)
                    }
                    Toast.makeText(this@DeliveryTripActivity, "🛵 Đã lấy đủ tất cả ${bundleOrders.size} đơn gộp! Bắt đầu giao chặng 1", Toast.LENGTH_SHORT).show()
                } else {
                    firebaseRepository.updateOrderStatus(currentOrderId, Order.Status.DELIVERING)
                    Toast.makeText(this@DeliveryTripActivity, "🛵 Xuất phát đi giao hàng!", Toast.LENGTH_SHORT).show()
                }
                updateStepUI(3)
                fetchRealShipperLocation()
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

        // Bước 3: Nút "Đã đến điểm giao khách" -> sang Bước 4 & mở Camera quét QR
        binding.btnArrivedAtCustomer.setOnClickListener {
            updateStepUI(4)
            val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
            targetOrder?.let {
                lifecycleScope.launch {
                    firebaseRepository.updateOrderStatus(it.orderId, Order.Status.ARRIVED_AT_CUSTOMER)
                    Toast.makeText(this@DeliveryTripActivity, "📍 Đã đến nhà khách! Chờ khách đối soát mã nhận món", Toast.LENGTH_SHORT).show()
                }
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
            Toast.makeText(this, "✓ Xác minh ảnh QR thành công! Khớp Token: ${verifyResult.matchedToken}", Toast.LENGTH_SHORT).show()
            completeTripOrder()
        } else {
            Toast.makeText(this, "❌ Mã QR trong ảnh không khớp với đơn #ORD-$shortCode!", Toast.LENGTH_LONG).show()
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
        chatBinding.tvChatCustomerName.text = "Chat với $cusName"

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
     * Modal Báo Cáo Sự Cố & Bom Hàng (6.html: cancelReportModal)
     */
    private fun showIncidentReportDialog(order: Order) {
        val repBinding = com.chuyen_de_2.foodorder.internal.databinding.DialogReportIncidentBinding.inflate(layoutInflater)
        val shortCode = if (order.orderId.length > 5) order.orderId.takeLast(6).uppercase() else order.orderId
        repBinding.tvIncidentOrderSub.text = "Đơn hàng: #ORD-$shortCode • Khách: ${order.customerName}"

        val reasons = arrayOf(
            "📵 Khách không nghe máy (Đã gọi 3 cuộc)",
            "🚫 Khách từ chối nhận món (Bom hàng)",
            "📍 Sai địa chỉ giao hàng / Không liên hệ được",
            "💥 Món ăn đổ vỡ do sự cố giao thông",
            "💸 Khách không đủ tiền mặt thanh toán COD"
        )
        repBinding.spinnerIncidentReason.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, reasons
        )

        var hasProof = false
        repBinding.btnAttachProof.setOnClickListener {
            hasProof = true
            repBinding.tvProofIcon.text = "✓"
            repBinding.tvProofText.text = "✓ Đã đính kèm ảnh bằng chứng hiện trường"
            repBinding.tvProofText.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
            Toast.makeText(this, "Đã chụp và đính kèm bằng chứng sự cố thành công!", Toast.LENGTH_SHORT).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(repBinding.root)
            .create()

        repBinding.btnCloseIncidentDialog.setOnClickListener { dialog.dismiss() }
        repBinding.btnDismissIncident.setOnClickListener { dialog.dismiss() }

        repBinding.btnConfirmIncidentCancel.setOnClickListener {
            val reason = repBinding.spinnerIncidentReason.selectedItem.toString()
            lifecycleScope.launch {
                val isCustomerFault = !reason.contains("giao thông", ignoreCase = true)
                val finalIncidentStatus = if (isCustomerFault) Order.Status.RETURNING_TO_STORE else Order.Status.CANCELLED
                firebaseRepository.updateOrderStatus(order.orderId, finalIncidentStatus, reason)
                completedBundleOrderIds.add(order.orderId)
                if (isCustomerFault) {
                    bomReturnOrders.add(order)
                }
                dialog.dismiss()

                // Nếu là đơn gộp và còn đơn chưa giao -> Hiện modal điều hướng theo 8.html & 6.html (bomReturnFlowModal)
                val remainingOrders = bundleOrders.filter { !completedBundleOrderIds.contains(it.orderId) }
                if (isBundleMode && remainingOrders.isNotEmpty()) {
                    showBomReturnFlowDialog(order, isReturningToStore = false, nextOrder = remainingOrders.first())
                } else if (isCustomerFault) {
                    showBomReturnFlowDialog(order, isReturningToStore = true, nextOrder = null)
                } else {
                    Toast.makeText(this@DeliveryTripActivity, "Đã báo cáo sự cố & hủy đơn: $reason", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
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
            .setCancelable(false)
            .create()

        if (!isReturningToStore && nextOrder != null) {
            // Nhánh 1: Còn đơn ghép cần giao (Demo ship/8.html)
            flowBinding.tvBomFlowIcon.text = "🚨"
            flowBinding.tvBomFlowTitle.text = "ĐÃ HỦY ĐƠN BOM • TIẾP TỤC ĐƠN GHÉP"
            flowBinding.tvBomFlowTitle.setTextColor(ContextCompat.getColor(this, R.color.danger))
            flowBinding.tvBomOrderSub.text = "Đơn #ORD-$shortCode • Bị khách bom hàng"
            flowBinding.tvBomFlowTag.text = "Giữ Món Trên Xe"
            flowBinding.tvBomFlowTag.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EFF6FF"))
            flowBinding.tvBomFlowTag.setTextColor(Color.parseColor("#1D4ED8"))

            val nextSc = QRCodeUtils.getShortOrderCode(nextOrder.orderId)
            flowBinding.tvBomPolicyNote.text = "⚠️ Quy trình đơn ghép: Món ăn được lưu giữ nguyên niêm phong trong thùng xe. Quý tài xế vui lòng tiếp tục đi giao đơn ghép kế tiếp (#ORD-$nextSc) trước. Sau khi hoàn tất, hệ thống sẽ điều hướng bạn quay về Quán để trả lại món và nhận tiền hoàn!"

            flowBinding.layoutBomStoreAddress.visibility = View.GONE
            flowBinding.layoutBomNextOrderInfo.visibility = View.VISIBLE

            val nextIdx = bundleOrders.indexOf(nextOrder)
            val nextCh = if (nextIdx >= 0) nextIdx + 1 else 1
            flowBinding.tvBomNextTitle.text = "🛵 ĐƠN GHÉP KẾ TIẾP CẦN GIAO NGAY (CHẶNG $nextCh/${bundleOrders.size}):"
            flowBinding.tvBomNextCustName.text = "#ORD-$nextSc • ${nextOrder.customerName}"
            flowBinding.tvBomNextAddr.text = nextOrder.effectiveAddress
            flowBinding.tvBomNextCod.text = if (nextOrder.paymentMethod.equals(Order.PaymentMethod.COD, ignoreCase = true)) {
                "${nextOrder.effectiveTotal.toVndCurrency()} (Tiền mặt)"
            } else {
                "0 đ (Đã thanh toán Online)"
            }

            flowBinding.tvBomRefundTitle.text = "💰 QUYỀN LỢI ĐƯỢC BẢO LƯU:"
            flowBinding.tvBomFoodRefund.text = foodAmount.toVndCurrency()
            flowBinding.tvBomReturnFee.text = "+${returnFee.toVndCurrency()}"
            flowBinding.tvBomRefundTotalLabel.text = "QUÁN SẼ TRẢ KHI HOÀN TẤT CHUYẾN:"
            flowBinding.tvBomTotalRefund.text = totalRefund.toVndCurrency()

            flowBinding.tvBomStatusIcon.text = "🛵"
            flowBinding.tvBomStoreStatus.text = "Đơn ghép kế tiếp đang chờ: #ORD-$nextSc (${nextOrder.customerName})"

            flowBinding.btnConfirmBomAction.text = "🛵 TIẾP TỤC GIAO ĐƠN TIẾP THEO (#ORD-$nextSc) ➔"
            flowBinding.btnConfirmBomAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            flowBinding.btnConfirmBomAction.setOnClickListener {
                dialog.dismiss()
                switchActiveBundleCustomer(nextIdx)
                updateStepUI(3)
            }
        } else {
            // Nhánh 2: Đã xong hết các đơn ghép hoặc đơn đơn lẻ -> Về Quán trả món
            flowBinding.tvBomFlowIcon.text = "🏪"
            flowBinding.tvBomFlowTitle.text = "LUỒNG TRẢ MÓN VỀ QUÁN"
            flowBinding.tvBomFlowTitle.setTextColor(ContextCompat.getColor(this, R.color.danger))
            flowBinding.tvBomOrderSub.text = "Đơn #ORD-$shortCode • Bị khách bom hàng"
            flowBinding.tvBomFlowTag.text = "Quay Về Quán"
            flowBinding.tvBomFlowTag.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
            flowBinding.tvBomFlowTag.setTextColor(Color.parseColor("#DC2626"))

            val sName = binding.tvStep1StoreName.text.toString().ifBlank { "Quán Oishi Food" }
            flowBinding.tvBomPolicyNote.text = "⚠️ Toàn bộ đơn ghép đã hoàn tất! Quý tài xế vui lòng di chuyển về lại $sName để bàn giao lại món ăn. Quán có trách nhiệm hoàn trả tiền món + thanh toán cước hoàn hàng (${totalRefund.toVndCurrency()}) cho bạn."

            flowBinding.layoutBomStoreAddress.visibility = View.VISIBLE
            flowBinding.layoutBomNextOrderInfo.visibility = View.GONE

            flowBinding.tvBomStoreName.text = sName
            val displayStoreAddress = storeAddress.ifBlank { "Địa chỉ Quán" }
            flowBinding.tvBomStoreAddress.text = displayStoreAddress
            val dishesSummary = if (bomOrder.items.isNotEmpty()) {
                bomOrder.items.values.joinToString(", ") { "${it.quantity}x ${it.name.ifBlank { it.dishName }}" }
            } else {
                "Món ăn theo đơn #ORD-$shortCode"
            }
            flowBinding.tvBomDishes.text = "Món cần trả: $dishesSummary"

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

            flowBinding.btnConfirmBomAction.text = "🏪 ĐÃ TỚI QUÁN • TRẢ MÓN & NHẬN ${totalRefund.toVndCurrency()} TỪ QUÁN"
            flowBinding.btnConfirmBomAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_green))
            flowBinding.btnConfirmBomAction.setOnClickListener {
                lifecycleScope.launch {
                    bomReturnOrders.toList().forEach { bo ->
                        firebaseRepository.updateOrderStatus(bo.orderId, Order.Status.RETURNED_TO_STORE, "Shipper đã trả món về quán & nhận hoàn tiền")
                    }
                    flowBinding.tvBomStatusIcon.text = "✓"
                    flowBinding.tvBomStoreStatus.text = "✓ ĐÃ TRẢ MÓN & NHẬN ĐỦ ${totalRefund.toVndCurrency()} TỪ $sName!"
                    flowBinding.btnConfirmBomAction.text = "🏠 HOÀN TẤT CHUYẾN • VỀ DANH SÁCH ĐƠN ➔"
                    flowBinding.btnConfirmBomAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@DeliveryTripActivity, R.color.primary))
                    flowBinding.btnConfirmBomAction.setOnClickListener {
                        dialog.dismiss()
                        finish()
                    }
                }
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun launchCameraQrScanner() {
        val targetOrder = if (isBundleMode) bundleOrders.getOrNull(activeBundleIndex) ?: currentOrder else currentOrder
        val ordId = targetOrder?.orderId ?: currentOrderId
        val shortCode = if (ordId.length > 5) ordId.takeLast(6).uppercase() else ordId

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
                        Toast.makeText(this, "✓ Quét Camera thành công! Khớp mã: $scannedValue", Toast.LENGTH_SHORT).show()
                        completeTripOrder()
                    } else {
                        Toast.makeText(this, "⚠️ Mã QR không khớp: $scannedValue", Toast.LENGTH_LONG).show()
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
        tvExpectedInfo?.text = "Đơn cần xác minh: #ORD-$shortCode • Quét mã QR hoặc nhập mã Token của khách."
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
                Toast.makeText(this, "✓ Xác minh thành công! Khớp Token: ${verifyResult.matchedToken}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                completeTripOrder()
            } else {
                tvScannerStatus?.text = "❌ MÃ KHÔNG KHỚP ĐƠN #ORD-$shortCode"
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
            completeTripOrder()
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
                getSharedPreferences("delivery_trip_state", Context.MODE_PRIVATE)
                    .edit().remove("step_$ordId").apply()

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
                        .setTitle("🎉 ĐÃ GIAO XONG ĐƠN #ORD-$scCurrent ($completedCount/$totalCount)!")
                        .setMessage("Đơn hàng #ORD-$scCurrent của ${targetOrder?.customerName} đã được hoàn tất thành công.\n\nTiếp tục di chuyển giao đơn tiếp theo: #ORD-$scNext cho khách: ${nextOrder.customerName} (Còn ${remainingOrders.size} đơn)!")
                        .setPositiveButton("🛵 GIAO TIẾP ĐƠN #ORD-$scNext ➔") { _, _ ->
                            switchActiveBundleCustomer(nextIdx)
                            updateStepUI(3)
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
                        bundleOrders.joinToString(", ") { "#ORD-" + QRCodeUtils.getShortOrderCode(it.orderId) }
                    } else {
                        "#ORD-" + QRCodeUtils.getShortOrderCode(ordId)
                    }
                    AlertDialog.Builder(this@DeliveryTripActivity)
                        .setTitle("🎉 Hoàn Thành Toàn Bộ Chuyến Giao Hàng!")
                        .setMessage("Đã hoàn tất tất cả các đơn hàng ($allCodes).\n\n+${totalFee.toVndCurrency()} tiền công ship đã được cộng vào Ví Thu Nhập của bạn!")
                        .setPositiveButton("Về Danh Sách Đơn") { _, _ ->
                            finish()
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

