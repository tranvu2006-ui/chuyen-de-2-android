package com.chuyen_de_2.foodorder.internal.ui.ship

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
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

    // Native Google Maps instances & components
    private var googleMapStep1: GoogleMap? = null
    private var googleMapStep3: GoogleMap? = null
    private var shipperMarkerStep1: Marker? = null
    private var storeMarkerStep1: Marker? = null
    private var polylineStep1: Polyline? = null

    private var shipperMarkerStep3: Marker? = null
    private var custMarkerStep3: Marker? = null
    private var polylineStep3: Polyline? = null

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

        setupNativeGoogleMaps()
        setupSwipeToConfirm()
        setupListeners()
        observeOrderDetails()
        updateStepUI(1)
        checkAndRequestLocation()
    }

    private fun setupNativeGoogleMaps() {
        val mapFrag1 = supportFragmentManager.findFragmentById(R.id.mapFragmentStep1) as? SupportMapFragment
        mapFrag1?.getMapAsync { map ->
            googleMapStep1 = map
            configureMapUi(map)
            updateStep1NativeMap()
        }

        val mapFrag3 = supportFragmentManager.findFragmentById(R.id.mapFragmentStep3) as? SupportMapFragment
        mapFrag3?.getMapAsync { map ->
            googleMapStep3 = map
            configureMapUi(map)
            updateStep3NativeMap()
        }
    }

    private fun configureMapUi(map: GoogleMap) {
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled = true
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

        // Geocoding địa chỉ khách thực tế để vẽ lộ trình Google Maps Native
        lifecycleScope.launch {
            try {
                val coords = GoogleMapUtils.geocodeAddress(this@DeliveryTripActivity, dest)
                customerLat = coords.latitude
                customerLng = coords.longitude
                updateStep3NativeMap()
            } catch (e: Exception) {
                // Giữ nguyên tọa độ mặc định nếu lỗi mạng
            }
        }

        updateStep1NativeMap()
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
                        updateStep1NativeMap()
                        updateStep3NativeMap()
                    }
                }
                .addOnFailureListener {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) {
                            driverLat = loc.latitude
                            driverLng = loc.longitude
                            updateStep1NativeMap()
                            updateStep3NativeMap()
                        }
                    }
                }
        } catch (e: Exception) {
            // Không làm gián đoạn UI
        }
    }

    private fun updateStep1NativeMap() {
        val map = googleMapStep1 ?: return
        val driverLatLng = LatLng(driverLat, driverLng)
        val targetStoreLatLng = LatLng(storeLat, storeLng)

        // Marker tài xế xe máy native
        val bikeIcon = bitmapDescriptorFromVector(this, R.drawable.ic_shipper_bike)
        if (shipperMarkerStep1 == null) {
            shipperMarkerStep1 = map.addMarker(
                MarkerOptions()
                    .position(driverLatLng)
                    .title("🛵 Vị trí của bạn (Tài xế)")
                    .apply {
                        if (bikeIcon != null) icon(bikeIcon)
                        else icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                    }
            )
        } else {
            shipperMarkerStep1?.position = driverLatLng
        }

        // Marker quán ăn native
        val storeName = currentOrder?.shopName?.ifBlank { "Quán ăn" } ?: "Quán ăn"
        if (storeMarkerStep1 == null) {
            storeMarkerStep1 = map.addMarker(
                MarkerOptions()
                    .position(targetStoreLatLng)
                    .title("🏪 $storeName")
                    .snippet("Điểm nhận món ăn")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        } else {
            storeMarkerStep1?.position = targetStoreLatLng
            storeMarkerStep1?.title = "🏪 $storeName"
        }

        // Tuyến đường native màu xanh Google Maps
        polylineStep1?.remove()
        polylineStep1 = map.addPolyline(
            PolylineOptions()
                .add(driverLatLng, targetStoreLatLng)
                .color(Color.parseColor("#1A73E8"))
                .width(12f)
                .geodesic(true)
        )

        // Căn chỉnh góc nhìn camera
        try {
            val bounds = LatLngBounds.Builder()
                .include(driverLatLng)
                .include(targetStoreLatLng)
                .build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 130))
        } catch (_: Exception) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(driverLatLng, 15f))
        }
    }

    private fun updateStep3NativeMap() {
        val map = googleMapStep3 ?: return
        val driverLatLng = LatLng(driverLat, driverLng)
        val targetCustLatLng = LatLng(customerLat, customerLng)

        // Marker tài xế xe máy native
        val bikeIcon = bitmapDescriptorFromVector(this, R.drawable.ic_shipper_bike)
        if (shipperMarkerStep3 == null) {
            shipperMarkerStep3 = map.addMarker(
                MarkerOptions()
                    .position(driverLatLng)
                    .title("🛵 Vị trí của bạn (Tài xế)")
                    .apply {
                        if (bikeIcon != null) icon(bikeIcon)
                        else icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    }
            )
        } else {
            shipperMarkerStep3?.position = driverLatLng
        }

        // Marker khách hàng native
        val cusName = currentOrder?.customerName?.ifBlank { "Khách hàng" } ?: "Khách hàng"
        if (custMarkerStep3 == null) {
            custMarkerStep3 = map.addMarker(
                MarkerOptions()
                    .position(targetCustLatLng)
                    .title("📍 $cusName")
                    .snippet(currentOrder?.address ?: "Điểm giao khách")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        } else {
            custMarkerStep3?.position = targetCustLatLng
            custMarkerStep3?.title = "📍 $cusName"
        }

        // Tuyến đường native xanh lá chặng 3
        polylineStep3?.remove()
        polylineStep3 = map.addPolyline(
            PolylineOptions()
                .add(driverLatLng, targetCustLatLng)
                .color(Color.parseColor("#059669"))
                .width(12f)
                .geodesic(true)
        )

        try {
            val bounds = LatLngBounds.Builder()
                .include(driverLatLng)
                .include(targetCustLatLng)
                .build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 130))
        } catch (_: Exception) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(driverLatLng, 15f))
        }
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return try {
            val drawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        } catch (e: Exception) {
            null
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
            googleMapStep1?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(storeLat, storeLng), 17f))
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
                updateStep3NativeMap()
            }
        }

        // Bước 3: Dẫn đường đến khách (Mở Google Maps Turn-by-Turn thật)
        binding.btnOpenNavStep3.setOnClickListener {
            val cusName = currentOrder?.customerName ?: "Khách hàng"
            googleMapStep3?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(customerLat, customerLng), 17f))
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
