package com.chuyen_de_2.foodorder.customer.ui.tracking

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityTrackingBinding
import com.chuyen_de_2.foodorder.customer.ui.order.IncidentProofBottomSheetDialogFragment
import com.chuyen_de_2.foodorder.customer.ui.order.OrderHistoryActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import dagger.hilt.android.AndroidEntryPoint

/**
 * Màn hình Theo Dõi Đơn Hàng (TrackingActivity)
 * Thiết kế đồng bộ 100% với khách/b9.html:
 * - Stepper 4 bước chuẩn Oishi Food (Chờ xác nhận -> Quán đang làm -> Đang giao -> Đã nhận)
 * - Nút gọi quán & Modal nhắn tin trực tiếp với quán
 * - Hộp cảnh báo mất đơn & Cảnh báo khẩn cấp
 * - Mã QR Delivery Token & Box chúc mừng giao thành công
 * - Thẻ tài xế và Google Maps GPS realtime
 * - Đánh giá 5 sao & Tóm tắt chi tiết giỏ hàng và thanh toán
 */
@AndroidEntryPoint
class TrackingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityTrackingBinding
    private val viewModel: TrackingViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private var shipperMarker: Marker? = null
    private var isFirstUpdate = true

    private var currentOrderId: String = ""
    private var currentStoreName: String = "Quán ăn"
    private var currentStorePhone: String = "02838229999"
    private var currentShipperName: String = "Trần Văn Nam"
    private var currentShipperPhone: String = "0901234567"
    private var currentRating: Int = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)
        InsetUtils.applyToolbarInsets(binding.topHeader)

        currentOrderId = intent.getStringExtra(Constants.EXTRA_ORDER_ID) ?: ""
        if (currentOrderId.isBlank()) {
            Toast.makeText(this, "Lỗi: Không tìm thấy mã đơn hàng", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        setupMap()
        setupListeners()
        setupRatingComponent()

        viewModel.startTracking(currentOrderId)
        observeViewModel()
    }

    private fun setupUI() {
        val shortCode = currentOrderId.takeLast(6).uppercase()
        binding.tvHeaderOrderCode.text = "#ORD_$shortCode"
        binding.lblStatusSub.text = "Mã đơn: #ORD_$shortCode • Quán đang kiểm tra món ăn"
        binding.tokenPill.text = "TOKEN_OISHI_$shortCode"
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Gọi cho quán
        binding.btnCallShop.setOnClickListener {
            dialPhone(currentStorePhone)
        }

        // Nhắn tin cho quán (Bottom Sheet b9.html)
        binding.btnChatShop.setOnClickListener {
            val modal = ChatStoreBottomSheetDialogFragment.newInstance(currentStoreName, currentOrderId)
            modal.show(supportFragmentManager, "ChatStoreBottomSheet")
        }

        // Gọi cho shipper
        binding.btnCallShipper.setOnClickListener {
            dialPhone(currentShipperPhone)
        }

        // Cuộn xuống mã QR
        binding.btnScrollToQr.setOnClickListener {
            binding.scrollViewContent.smoothScrollTo(0, binding.qrCardSection.top)
        }

        // Nút Cảnh Báo khẩn cấp trên Header hoặc trong Banner
        val openIncidentAction = View.OnClickListener {
            val modal = IncidentProofBottomSheetDialogFragment.newInstance(
                orderId = currentOrderId,
                driverName = currentShipperName,
                reason = "Khách không nghe máy sau 15 phút đếm ngược (Mô phỏng sự cố)",
                notes = "Tài xế đã có mặt tại địa chỉ giao hàng và liên hệ 3 cuộc gọi nhưng không có phản hồi."
            )
            modal.show(supportFragmentManager, "IncidentProofModal")
        }
        binding.btnHeaderWarn.setOnClickListener(openIncidentAction)
        binding.btnViewIncidentDetailInline.setOnClickListener(openIncidentAction)

        // Banner huỷ đơn
        binding.btnCloseCancelledNotice.setOnClickListener {
            binding.boxCustomerCancelledNotice.isVisible = false
        }
        binding.btnViewCancelledDetail.setOnClickListener {
            val intent = Intent(this, OrderHistoryActivity::class.java).apply {
                putExtra("selected_tab", "CANCELLED")
            }
            startActivity(intent)
        }
    }

    private fun setupRatingComponent() {
        val stars = listOf(binding.star1, binding.star2, binding.star3, binding.star4, binding.star5)
        stars.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                setRating(index + 1)
            }
        }

        binding.btnSubmitReview.setOnClickListener {
            Toast.makeText(
                this,
                "✓ Đã gửi đánh giá $currentRating sao! Bạn nhận được +50 điểm Oishi Rewards",
                Toast.LENGTH_LONG
            ).show()
            binding.btnSubmitReview.isEnabled = false
            binding.btnSubmitReview.text = "✓ Đã Gửi Đánh Giá"
            binding.btnSubmitReview.setBackgroundColor(ContextCompat.getColor(this, R.color.text_muted))
        }
    }

    private fun setRating(starCount: Int) {
        currentRating = starCount
        val stars = listOf(binding.star1, binding.star2, binding.star3, binding.star4, binding.star5)
        val activeColor = ContextCompat.getColor(this, R.color.rating_star)
        val inactiveColor = ContextCompat.getColor(this, R.color.divider)

        stars.forEachIndexed { idx, tv ->
            if (idx < starCount) {
                tv.setTextColor(activeColor)
            } else {
                tv.setTextColor(inactiveColor)
            }
        }

        binding.ratingHintText.text = when (starCount) {
            1 -> "Rất tệ! Quán phục vụ chưa đạt yêu cầu."
            2 -> "Chưa hài lòng! Mong quán cải thiện."
            3 -> "Bình thường! Tạm ổn."
            4 -> "Hài lòng! Món ngon, đóng gói cẩn thận."
            else -> "Cực kỳ hài lòng! Món ngon, giao nhanh đúng giờ."
        }
    }

    private fun dialPhone(phone: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể gọi tới số $phone", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
        }
        // Vị trí mặc định trung tâm TP.HCM
        val hcmCenter = LatLng(10.7769, 106.7009)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(hcmCenter, 14f))
    }

    private fun observeViewModel() {
        viewModel.location.observe(this) { tracking ->
            tracking?.let {
                val latLng = LatLng(it.lat, it.lng)
                updateShipperMarker(latLng)
            }
        }

        viewModel.order.observe(this) { order ->
            order?.let { updateOrderUI(it) }
        }
    }

    private fun updateOrderUI(order: Order) {
        // Cập nhật tên quán & số điện thoại
        if (order.shopName.isNotBlank()) {
            currentStoreName = order.shopName
            binding.btnCallShop.text = "📞 Gọi Cho Quán ($currentStoreName)"
            binding.shipperRole.text = "Nhân viên giao hàng • $currentStoreName"
        }

        // Cập nhật tài xế
        if (order.shipperName.isNotBlank()) {
            currentShipperName = order.shipperName
            binding.shipperName.text = currentShipperName
        }

        // Cập nhật Delivery Token
        if (order.deliveryToken.isNotBlank()) {
            binding.tokenPill.text = order.deliveryToken
        }

        // Cập nhật Stepper 4 bước
        updateStatusStep(order.status)

        // Cập nhật chi tiết các món ăn & thanh toán
        bindOrderItems(order)
    }

    private fun updateStatusStep(status: String) {
        val shortCode = currentOrderId.takeLast(6).uppercase()
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val mutedColor = ContextCompat.getColor(this, R.color.text_secondary)
        val greenColor = ContextCompat.getColor(this, R.color.status_completed)

        when (status) {
            Order.Status.PENDING -> {
                setTimelineProgress(0)
                setNodeActive(1)
                binding.lblStatusIcon.text = "⏳"
                binding.lblStatusHeadline.text = "Đã gửi đơn, chờ quán xác nhận"
                binding.lblStatusSub.text = "Mã đơn: #ORD_$shortCode • Quán đang kiểm tra món ăn"
                binding.lblDetailIcon.text = "⏳"
                binding.lblDetailDesc.text =
                    "Hệ thống đã gửi thông báo đơn tới $currentStoreName. Quán sẽ kiểm tra và xác nhận trong vòng 1 - 3 phút."
                binding.boxCustomerCancelledNotice.isVisible = false
                binding.boxWarningActiveOnOrder.isVisible = false
                binding.btnHeaderWarn.isVisible = false
                binding.ratingCard.isVisible = false
                binding.qrCompletedBox.isVisible = false
                binding.qrCodeWrapper.isVisible = true
                binding.tokenPill.isVisible = true
            }

            Order.Status.PREPARING, Order.Status.READY -> {
                setTimelineProgress(33)
                setNodeDone(1)
                setNodeActive(2)
                binding.lblStatusIcon.text = "🍳"
                binding.lblStatusHeadline.text = "Quán đang chuẩn bị món ăn"
                binding.lblStatusSub.text = "Món ăn đang được chuẩn bị nóng hổi"
                binding.lblDetailIcon.text = "🍳"
                binding.lblDetailDesc.text =
                    "$currentStoreName đang chế biến món ăn tươi ngon. Tài xế sẽ tới lấy món ngay khi sẵn sàng."
                binding.boxCustomerCancelledNotice.isVisible = false
                binding.boxWarningActiveOnOrder.isVisible = false
                binding.btnHeaderWarn.isVisible = false
                binding.ratingCard.isVisible = false
                binding.qrCompletedBox.isVisible = false
                binding.qrCodeWrapper.isVisible = true
                binding.tokenPill.isVisible = true
            }

            Order.Status.ASSIGNED, Order.Status.DELIVERING -> {
                setTimelineProgress(66)
                setNodeDone(1)
                setNodeDone(2)
                setNodeActive(3)
                binding.lblStatusIcon.text = "🛵"
                binding.lblStatusHeadline.text = "Tài xế đang giao hàng tới bạn"
                binding.lblStatusSub.text = "Vui lòng để ý điện thoại để nhận món ăn"
                binding.lblDetailIcon.text = "🛵"
                binding.lblDetailDesc.text =
                    "Tài xế $currentShipperName đang di chuyển tới địa chỉ giao hàng của bạn. Bạn có thể xem lộ trình trên bản đồ."
                binding.boxCustomerCancelledNotice.isVisible = false
                binding.boxWarningActiveOnOrder.isVisible = false
                binding.btnHeaderWarn.isVisible = false
                binding.ratingCard.isVisible = false
                binding.qrCompletedBox.isVisible = false
                binding.qrCodeWrapper.isVisible = true
                binding.tokenPill.isVisible = true
            }

            Order.Status.COMPLETED -> {
                setTimelineProgress(100)
                setNodeDone(1)
                setNodeDone(2)
                setNodeDone(3)
                setNodeCompleted(4)
                binding.lblStatusIcon.text = "🎉"
                binding.lblStatusHeadline.text = "Giao hàng thành công!"
                binding.lblStatusHeadline.setTextColor(greenColor)
                binding.lblStatusSub.text = "Đơn hàng đã được đối soát hoàn tất"
                binding.lblDetailIcon.text = "🎉"
                binding.lblDetailDesc.text =
                    "Đơn hàng đã được tài xế quét đối soát xác nhận hoàn tất thành công. Chúc bạn có bữa ăn ngon miệng!"
                binding.boxCustomerCancelledNotice.isVisible = false
                binding.boxWarningActiveOnOrder.isVisible = false
                binding.btnHeaderWarn.isVisible = false

                // Bước 4: Ẩn QR code visual, Hiện Box Chúc mừng & Khung Đánh giá 5 sao
                binding.qrCodeWrapper.isVisible = false
                binding.tokenPill.isVisible = false
                binding.qrCompletedBox.isVisible = true
                binding.ratingCard.isVisible = true
            }

            Order.Status.CANCELLED, Order.Status.BOOM -> {
                binding.lblStatusIcon.text = "🚫"
                binding.lblStatusHeadline.text = "Đơn hàng đã bị hủy"
                binding.lblStatusHeadline.setTextColor(ContextCompat.getColor(this, R.color.error))
                binding.lblStatusSub.text = "Đơn hàng gặp sự cố giao hàng hoặc bom hàng"
                binding.lblDetailIcon.text = "⚠️"
                binding.lblDetailDesc.text =
                    "Đơn hàng đã chính thức bị hủy. Tiền món đang được xử lý hoàn trả theo chính sách Oishi Food."
                binding.boxCustomerCancelledNotice.isVisible = true
                binding.boxWarningActiveOnOrder.isVisible = false
                binding.btnHeaderWarn.isVisible = true
                binding.ratingCard.isVisible = false
            }
        }
    }

    private fun setTimelineProgress(progress: Int) {
        binding.timelineProgress.progress = progress
    }

    private fun setNodeActive(step: Int) {
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        when (step) {
            1 -> {
                binding.dotStep1.apply {
                    text = "1"
                    setBackgroundResource(R.drawable.bg_badge_orange)
                    backgroundTintList = ContextCompat.getColorStateList(this@TrackingActivity, R.color.primary)
                    setTextColor(ContextCompat.getColor(this@TrackingActivity, R.color.white))
                }
                binding.labelStep1.apply {
                    setTextColor(primaryColor)
                    paint.isFakeBoldText = true
                }
            }
            2 -> {
                binding.dotStep2.apply {
                    text = "2"
                    setBackgroundResource(R.drawable.bg_badge_orange)
                    backgroundTintList = ContextCompat.getColorStateList(this@TrackingActivity, R.color.primary)
                    setTextColor(ContextCompat.getColor(this@TrackingActivity, R.color.white))
                }
                binding.labelStep2.apply {
                    setTextColor(primaryColor)
                    paint.isFakeBoldText = true
                }
            }
            3 -> {
                binding.dotStep3.apply {
                    text = "3"
                    setBackgroundResource(R.drawable.bg_badge_orange)
                    backgroundTintList = ContextCompat.getColorStateList(this@TrackingActivity, R.color.primary)
                    setTextColor(ContextCompat.getColor(this@TrackingActivity, R.color.white))
                }
                binding.labelStep3.apply {
                    setTextColor(primaryColor)
                    paint.isFakeBoldText = true
                }
            }
        }
    }

    private fun setNodeDone(step: Int) {
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val textPrimary = ContextCompat.getColor(this, R.color.text_primary)
        val targetDot = when (step) {
            1 -> binding.dotStep1
            2 -> binding.dotStep2
            3 -> binding.dotStep3
            else -> return
        }
        val targetLabel = when (step) {
            1 -> binding.labelStep1
            2 -> binding.labelStep2
            3 -> binding.labelStep3
            else -> return
        }

        targetDot.apply {
            text = "✓"
            setBackgroundResource(R.drawable.bg_badge_orange)
            backgroundTintList = ContextCompat.getColorStateList(this@TrackingActivity, R.color.primary)
            setTextColor(ContextCompat.getColor(this@TrackingActivity, R.color.white))
        }
        targetLabel.apply {
            setTextColor(textPrimary)
            paint.isFakeBoldText = true
        }
    }

    private fun setNodeCompleted(step: Int) {
        val greenColor = ContextCompat.getColor(this, R.color.status_completed)
        if (step == 4) {
            binding.dotStep4.apply {
                text = "✓"
                setBackgroundResource(R.drawable.bg_badge_orange)
                backgroundTintList = ContextCompat.getColorStateList(this@TrackingActivity, R.color.status_completed)
                setTextColor(ContextCompat.getColor(this@TrackingActivity, R.color.white))
            }
            binding.labelStep4.apply {
                setTextColor(greenColor)
                paint.isFakeBoldText = true
            }
        }
    }

    private fun bindOrderItems(order: Order) {
        val container = binding.llOrderItemsContainer
        container.removeAllViews()

        if (order.items.isNotEmpty()) {
            for ((_, item) in order.items) {
                val rowView = LayoutInflater.from(this).inflate(
                    android.R.layout.simple_list_item_2,
                    container,
                    false
                )
                val text1 = rowView.findViewById<TextView>(android.R.id.text1)
                val text2 = rowView.findViewById<TextView>(android.R.id.text2)

                text1.text = "${item.quantity}x ${item.name}"
                text1.textSize = 12f
                text1.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

                val subtotal = if (item.itemSubtotal > 0) item.itemSubtotal else item.price * item.quantity
                text2.text = subtotal.toVndCurrency()
                text2.textSize = 12f
                text2.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

                container.addView(rowView)
            }
        } else {
            // Hiển thị fallback nếu đơn không có item danh sách con
            val fallbackTv = TextView(this).apply {
                text = "1x Đơn món ăn #${currentOrderId.takeLast(6).uppercase()}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }
            container.addView(fallbackTv)
        }

        // Phí giao hàng
        val shippingFee = if (order.shippingFee > 0) order.shippingFee else 15000L
        binding.txtShippingFee.text = shippingFee.toVndCurrency()

        // Tổng tiền
        val totalAmount = if (order.totalPayment > 0) order.totalPayment else if (order.total > 0) order.total else 50000L
        binding.txtB9Total.text = totalAmount.toVndCurrency()

        // Hình thức thanh toán
        val payMethodStr = when (order.paymentMethod) {
            Order.PaymentMethod.MOMO_QR -> "Ví MoMo (Đã thanh toán)"
            Order.PaymentMethod.BANKING -> "Chuyển khoản Ngân hàng (Đã thanh toán)"
            else -> "Tiền mặt khi nhận món (COD)"
        }
        binding.txtB9PayMode.text = "💳 Hình thức: $payMethodStr"
    }

    private fun updateShipperMarker(latLng: LatLng) {
        val map = googleMap ?: return

        if (shipperMarker == null) {
            shipperMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Tài xế: $currentShipperName")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        } else {
            shipperMarker?.position = latLng
        }

        if (isFirstUpdate) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            isFirstUpdate = false
        }
    }
}
