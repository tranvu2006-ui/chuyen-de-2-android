package com.chuyen_de_2.foodorder.customer.ui.tracking

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.core.data.model.DishReview
import com.chuyen_de_2.foodorder.core.data.model.Order
import java.io.ByteArrayOutputStream
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.GoogleMapUtils
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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

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
/**
 * Tên chức năng: Màn Hình Theo Dõi Đơn Hàng & Định Vị Tài Xế Realtime (Order Tracking & Live Map)
 * Cách hoạt động:
 *  - Lắng nghe tiến trình trạng thái đơn hàng (PENDING -> PREPARING -> READY -> ASSIGNED -> DELIVERING -> COMPLETED).
 *  - Vẽ bản đồ Google Maps và hiển thị marker Quán ăn, Khách hàng cùng marker Xe máy của Tài xế di chuyển theo tọa độ GPS thời gian thực.
 *  - Hiển thị mã QR nhận hàng bảo mật (Delivery Token) để tài xế quét xác thực khi bàn giao món.
 *  - Cung cấp liên hệ khẩn cấp: Gọi điện thoại cho Quán, nhắn tin trao đổi với Quán, gọi điện cho Tài xế.
 *  - Tiếp nhận bằng chứng hình ảnh khi có sự cố đơn hàng (Incident Proof).
 *  - Đánh giá sao ⭐ và viết nhận xét sau khi nhận món thành công.
 * Luồng hướng tới:
 *  - Bấm Back -> Trở về trang chủ [HomeActivity] hoặc [OrderHistoryActivity].
 *  - Bấm Nhắn tin quán -> Mở BottomSheet [ChatStoreBottomSheetDialogFragment].
 *  - Bấm Khiếu nại -> Mở màn hình [ComplaintActivity].
 */
@AndroidEntryPoint
class TrackingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityTrackingBinding
    private val viewModel: TrackingViewModel by viewModels()

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private var googleMap: GoogleMap? = null
    private var shipperMarker: Marker? = null
    private var storeMarker: Marker? = null
    private var customerMarker: Marker? = null
    private var routePolyline: Polyline? = null
    private var customerLatLng: LatLng? = null
    private var storeLatLng: LatLng? = null
    private var isFirstUpdate = true

    private var currentOrderId: String = ""
    private var currentStoreName: String = ""
    private var currentStorePhone: String = ""
    private var currentShipperName: String = "Tài xế giao hàng"
    private var currentShipperPhone: String = ""
    private var currentOrder: Order? = null

    // Quản lý trạng thái đánh giá riêng cho từng món trong đơn (Cách 2)
    private data class DishRatingState(
        val dishId: String,
        val dishName: String,
        val specText: String,
        var rating: Int = 5,
        var commentEt: EditText? = null,
        var starLabelTv: TextView? = null,
        var starTvs: List<TextView> = emptyList(),
        var imageBase64: String? = null,
        var layoutPreview: View? = null,
        var ivPreview: ImageView? = null
    )
    private val dishRatingStates = mutableListOf<DishRatingState>()
    private var currentPickingDishState: DishRatingState? = null

    // Launcher chọn ảnh từ thư viện
    private val pickReviewImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processAndPreviewImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
        InsetUtils.applyToolbarInsets(binding.topHeader, 8)

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
        val displayOrderCode = if (currentOrderId.startsWith("OISHI", ignoreCase = true)) currentOrderId else if (currentOrderId.length > 5) "#ORD_${currentOrderId.takeLast(6).uppercase()}" else currentOrderId
        val shortCode = if (currentOrderId.startsWith("OISHI", ignoreCase = true)) currentOrderId else if (currentOrderId.length > 5) currentOrderId.takeLast(6).uppercase() else currentOrderId
        binding.tvHeaderOrderCode.text = displayOrderCode
        binding.lblStatusSub.text = "Mã đơn: $displayOrderCode • Quán đang kiểm tra món ăn"
        val tokenText = if (currentOrderId.startsWith("OISHI", ignoreCase = true)) "TOKEN_$currentOrderId" else "TOKEN_OISHI_$shortCode"
        binding.tokenPill.text = tokenText

        // Sinh và hiển thị mã QR thực tế sắc nét bằng QRCodeUtils
        val qrPayload = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.buildOrderQrPayload(currentOrderId)
        val qrBitmap = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.generateQRCodeBitmap(qrPayload, 600, 600)
        binding.qrCodeWrapper.setOnClickListener {
            showFullscreenQrDialog(qrPayload, tokenText)
        }
        binding.ivQrCode.setImageBitmap(qrBitmap)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Bấm vào bản đồ mở ứng dụng Google Maps dẫn đường trực tiếp
        binding.mapFragment.setOnClickListener {
            customerLatLng?.let { dest ->
                GoogleMapUtils.openGoogleMapsNavigation(this, dest.latitude, dest.longitude, "Điểm giao hàng")
            }
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

        // Nút hủy đơn / gửi yêu cầu hủy đơn
        binding.btnCancelOrder.setOnClickListener {
            handleCancelOrderClick()
        }

        // Nút rút lại yêu cầu hủy đơn
        binding.btnWithdrawCancelRequest.setOnClickListener {
            handleWithdrawCancelClick()
        }
    }

    private fun handleCancelOrderClick() {
        val order = currentOrder ?: return
        val status = order.effectiveStatus.uppercase()
        if (status == Order.Status.PENDING || status == Order.Status.PAID || status == Order.Status.AWAITING_PAYMENT) {
            showInstantCancelDialog(order)
        } else if (status == Order.Status.PREPARING || status == Order.Status.READY) {
            showRequestCancelDialog(order)
        }
    }

    private fun showInstantCancelDialog(order: Order) {
        val reasons = arrayOf(
            "Đặt nhầm món / trùng đơn",
            "Đặt nhầm địa chỉ giao hàng",
            "Đổi ý, không muốn mua nữa",
            "Quán lâu xác nhận",
            "Lý do khác..."
        )
        var selectedIndex = 0
        val input = EditText(this).apply {
            hint = "Nhập lý do cụ thể..."
            visibility = View.GONE
            setPadding(40, 20, 40, 20)
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(input)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("✕ Hủy Đơn Hàng")
            .setMessage("Do quán chưa xác nhận làm món, bạn có thể hủy đơn ngay lập tức mà không bị ảnh hưởng đến điểm uy tín.\n\nVui lòng chọn lý do hủy:")
            .setSingleChoiceItems(reasons, 0) { _, which ->
                selectedIndex = which
                input.visibility = if (which == reasons.size - 1) View.VISIBLE else View.GONE
            }
            .setView(container)
            .setPositiveButton("Xác Nhận Hủy") { _, _ ->
                val reasonText = if (selectedIndex == reasons.size - 1) {
                    input.text.toString().trim().ifBlank { "Khách hủy do đặt nhầm" }
                } else {
                    reasons[selectedIndex]
                }
                viewModel.cancelOrderImmediately(order.orderId, reasonText)
                Toast.makeText(this, "✓ Đã hủy đơn hàng thành công!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Giữ Lại Đơn", null)
            .show()
    }

    private fun showRequestCancelDialog(order: Order) {
        val reasons = arrayOf(
            "Đặt nhầm món, cần đặt lại",
            "Đặt nhầm địa chỉ nhận hàng",
            "Chờ quán chuẩn bị quá lâu",
            "Có việc bận đột xuất",
            "Lý do khác..."
        )
        var selectedIndex = 0
        val input = EditText(this).apply {
            hint = "Nhập lý do cụ thể gửi quán..."
            visibility = View.GONE
            setPadding(40, 20, 40, 20)
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(input)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ Yêu Cầu Hủy Đơn")
            .setMessage("Quán đã tiếp nhận và đang chế biến món ăn. Để hủy đơn, yêu cầu của bạn sẽ được gửi tới quán để quán xác nhận hủy.\n\nVui lòng chọn lý do:")
            .setSingleChoiceItems(reasons, 0) { _, which ->
                selectedIndex = which
                input.visibility = if (which == reasons.size - 1) View.VISIBLE else View.GONE
            }
            .setView(container)
            .setPositiveButton("Gửi Yêu Cầu Hủy") { _, _ ->
                val reasonText = if (selectedIndex == reasons.size - 1) {
                    input.text.toString().trim().ifBlank { "Khách yêu cầu hủy do đặt nhầm" }
                } else {
                    reasons[selectedIndex]
                }
                viewModel.requestCancelOrder(order.orderId, reasonText)
                Toast.makeText(this, "✓ Đã gửi yêu cầu hủy đơn đến quán! Vui lòng chờ phản hồi.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Quay Lại", null)
            .show()
    }

    private fun handleWithdrawCancelClick() {
        val order = currentOrder ?: return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Rút lại yêu cầu hủy đơn?")
            .setMessage("Bạn có muốn tiếp tục nhận đơn hàng này và rút lại yêu cầu hủy gửi quán không?")
            .setPositiveButton("Rút Lại Yêu Cầu") { _, _ ->
                viewModel.withdrawCancelRequest(order.orderId)
                Toast.makeText(this, "✓ Đã rút lại yêu cầu hủy đơn", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun setupRatingComponent() {
        binding.btnSubmitReview.setOnClickListener {
            handleSubmitReview()
        }
    }

    private fun setupDishRatingList(order: Order) {
        if (dishRatingStates.isNotEmpty()) return
        val container = binding.llReviewDishesContainer
        container.removeAllViews()

        val itemsList = order.items.values.toList()
        if (itemsList.isEmpty()) {
            val state = DishRatingState(
                dishId = "general_${currentOrderId}",
                dishName = "Đơn món ăn #${currentOrderId.takeLast(6).uppercase()}",
                specText = "Đã giao thành công",
                rating = 5
            )
            dishRatingStates.add(state)
            val itemView = createDishRatingView(state, null)
            container.addView(itemView)
            return
        }

        for (item in itemsList) {
            val resolvedDishId = if (item.dishId.isNotBlank()) item.dishId else item.itemId
            val resolvedDishName = if (item.dishName.isNotBlank()) item.dishName else item.name
            val state = DishRatingState(
                dishId = resolvedDishId,
                dishName = resolvedDishName,
                specText = "Số lượng: ${item.quantity} phần",
                rating = 5
            )
            dishRatingStates.add(state)
            val itemView = createDishRatingView(state, item.effectiveImageUrl)
            container.addView(itemView)
        }
    }

    private fun createDishRatingView(state: DishRatingState, imageUrl: String?): View {
        val rowView = LayoutInflater.from(this).inflate(R.layout.item_order_dish_rating, binding.llReviewDishesContainer, false)
        val tvDishName = rowView.findViewById<TextView>(R.id.tvDishName)
        val tvDishSpec = rowView.findViewById<TextView>(R.id.tvDishSpec)
        val ivDishThumb = rowView.findViewById<ImageView>(R.id.ivDishThumb)
        val tvDishStarLabel = rowView.findViewById<TextView>(R.id.tvDishStarLabel)
        val etDishReviewComment = rowView.findViewById<EditText>(R.id.etDishReviewComment)
        val btnDishPickImage = rowView.findViewById<View>(R.id.btnDishPickImage)
        val layoutDishImagePreview = rowView.findViewById<View>(R.id.layoutDishImagePreview)
        val ivDishReviewPreview = rowView.findViewById<ImageView>(R.id.ivDishReviewPreview)
        val btnDishRemoveImage = rowView.findViewById<View>(R.id.btnDishRemoveImage)

        state.layoutPreview = layoutDishImagePreview
        state.ivPreview = ivDishReviewPreview

        val starTvs = listOf<TextView>(
            rowView.findViewById(R.id.tvStar1),
            rowView.findViewById(R.id.tvStar2),
            rowView.findViewById(R.id.tvStar3),
            rowView.findViewById(R.id.tvStar4),
            rowView.findViewById(R.id.tvStar5)
        )

        tvDishName.text = state.dishName
        tvDishSpec.text = state.specText

        if (!imageUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.placeholder_food)
                .error(R.drawable.placeholder_food)
                .into(ivDishThumb)
        }

        val activeColor = ContextCompat.getColor(this, R.color.rating_star)
        val inactiveColor = ContextCompat.getColor(this, R.color.divider)

        fun updateStarUI(starCount: Int) {
            state.rating = starCount
            starTvs.forEachIndexed { idx, tv ->
                tv.setTextColor(if (idx < starCount) activeColor else inactiveColor)
            }
            tvDishStarLabel.text = when (starCount) {
                1 -> "1★ Rất tệ"
                2 -> "2★ Chưa hài lòng"
                3 -> "3★ Tạm ổn"
                4 -> "4★ Hài lòng, ngon"
                else -> "5★ Tuyệt vời!"
            }
        }

        starTvs.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                updateStarUI(index + 1)
            }
        }
        updateStarUI(5)

        btnDishPickImage.setOnClickListener {
            currentPickingDishState = state
            pickReviewImageLauncher.launch("image/*")
        }

        btnDishRemoveImage.setOnClickListener {
            state.imageBase64 = null
            layoutDishImagePreview.visibility = View.GONE
        }

        state.commentEt = etDishReviewComment
        state.starLabelTv = tvDishStarLabel
        state.starTvs = starTvs

        return rowView
    }

    private fun processAndPreviewImage(uri: Uri) {
        try {
            val base64String = com.chuyen_de_2.foodorder.core.util.ImagePickerUtils.compressUriToBase64(
                this,
                uri,
                maxDimension = 480,
                quality = 75
            )

            if (!base64String.isNullOrBlank()) {
                val targetState = currentPickingDishState
                if (targetState != null) {
                    targetState.imageBase64 = base64String
                    targetState.layoutPreview?.visibility = View.VISIBLE
                    targetState.ivPreview?.let { iv ->
                        Glide.with(this)
                            .load(base64String)
                            .into(iv)
                    }
                    Toast.makeText(this, "📷 Đã thêm ảnh cho món ${targetState.dishName}!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Không thể đọc ảnh, vui lòng thử lại!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể tải ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSubmitReview() {
        if (dishRatingStates.isEmpty()) return
        val order = currentOrder
        val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val customerId = authUser?.uid ?: order?.customerId ?: "user_${System.currentTimeMillis()}"
        val customerName = authUser?.displayName?.ifBlank { null }
            ?: authUser?.email?.substringBefore("@")
            ?: order?.customerName?.ifBlank { null }
            ?: "Bạn (Khách hàng)"

        val colors = listOf("#EE4D2D", "#10B981", "#3B82F6", "#8B5CF6", "#F59E0B", "#EC4899")
        val avatarColor = colors[kotlin.math.abs(customerId.hashCode()) % colors.size]
        val storeId = order?.storeId?.ifBlank { order.shopId }.orEmpty()

        lifecycleScope.launch {
            binding.btnSubmitReview.isEnabled = false
            binding.btnSubmitReview.text = "Đang gửi đánh giá..."

            for (state in dishRatingStates) {
                val comment = state.commentEt?.text?.toString()?.trim().orEmpty().ifBlank {
                    state.starLabelTv?.text?.toString() ?: "Món ăn ngon, rất hài lòng!"
                }
                val dishImage = state.imageBase64.orEmpty()
                val review = DishReview(
                    reviewId = "rev_${System.currentTimeMillis()}_${(1000..9999).random()}",
                    orderId = currentOrderId,
                    dishId = state.dishId,
                    dishName = state.dishName,
                    storeId = storeId,
                    customerId = customerId,
                    customerName = customerName,
                    rating = state.rating,
                    comment = comment,
                    imageUrl = dishImage,
                    specText = state.specText,
                    avatarColorHex = avatarColor,
                    createdAt = System.currentTimeMillis()
                )
                firebaseRepository.submitDishReview(review)
            }

            Toast.makeText(
                this@TrackingActivity,
                "✓ Cảm ơn bạn đã gửi đánh giá các món ăn! Bạn nhận được +50 điểm Oishi Rewards",
                Toast.LENGTH_LONG
            ).show()

            binding.btnSubmitReview.isEnabled = false
            binding.btnSubmitReview.text = "✓ Đã Gửi Đánh Giá"
            binding.btnSubmitReview.setBackgroundColor(ContextCompat.getColor(this@TrackingActivity, R.color.text_muted))
            binding.llReviewDishesContainer.visibility = View.GONE
            binding.tvRatingSub.text = "Cảm ơn bạn đã gửi đánh giá cho các món ăn trong đơn hàng!"
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

    private var lastGeocodedAddress: String = ""
    private var lastGeneratedQrPayload: String = ""
    private var hasBoundOrderItems: Boolean = false

    private fun updateOrderUI(order: Order) {
        currentOrder = order

        // Cập nhật trạng thái nút hủy đơn và banner yêu cầu hủy đơn
        val statusUpper = order.effectiveStatus.uppercase()
        when {
            statusUpper == Order.Status.CANCELLED || statusUpper == Order.Status.BOOM ||
            statusUpper == Order.Status.COMPLETED || statusUpper == Order.Status.RETURNING_TO_STORE ||
            statusUpper == Order.Status.RETURNED_TO_STORE || statusUpper == Order.Status.DELIVERING ||
            statusUpper == Order.Status.ARRIVED_AT_CUSTOMER || statusUpper == Order.Status.ARRIVED_AT_SHOP ||
            statusUpper == Order.Status.ASSIGNED -> {
                binding.btnCancelOrder.isVisible = false
                binding.boxCancelRequestStatus.isVisible = false
            }
            order.cancelRequested && order.cancelRequestStatus == Order.CancelRequestStatus.PENDING -> {
                binding.btnCancelOrder.isVisible = false
                binding.boxCancelRequestStatus.isVisible = true
                binding.tvCancelRequestIcon.text = "⏳"
                binding.tvCancelRequestTitle.text = "Đang chờ quán duyệt yêu cầu hủy đơn"
                binding.tvCancelRequestDesc.text = "Lý do: ${order.cancelRequestReason.ifBlank { "Đặt nhầm món" }}\nQuán đang xem xét yêu cầu hủy của bạn."
                binding.btnWithdrawCancelRequest.isVisible = true
            }
            !order.cancelRequested && order.cancelRequestStatus == Order.CancelRequestStatus.REJECTED &&
            (statusUpper == Order.Status.PREPARING || statusUpper == Order.Status.READY) -> {
                binding.btnCancelOrder.isVisible = false
                binding.boxCancelRequestStatus.isVisible = true
                binding.tvCancelRequestIcon.text = "❌"
                binding.tvCancelRequestTitle.text = "Quán không thể hủy đơn"
                binding.tvCancelRequestDesc.text = "Lý do: ${order.cancelRejectReason.ifBlank { "Quán đã nấu xong món ăn, không thể hủy đơn." }}\nĐơn hàng vẫn tiếp tục được thực hiện."
                binding.btnWithdrawCancelRequest.isVisible = false
            }
            statusUpper == Order.Status.PENDING || statusUpper == Order.Status.PAID || statusUpper == Order.Status.AWAITING_PAYMENT -> {
                binding.boxCancelRequestStatus.isVisible = false
                binding.btnCancelOrder.isVisible = true
                binding.btnCancelOrder.text = "✕ Hủy Đơn Hàng (Đặt Nhầm)"
            }
            statusUpper == Order.Status.PREPARING || statusUpper == Order.Status.READY -> {
                binding.boxCancelRequestStatus.isVisible = false
                binding.btnCancelOrder.isVisible = true
                binding.btnCancelOrder.text = "✕ Gửi Yêu Cầu Hủy Đơn"
            }
            else -> {
                binding.btnCancelOrder.isVisible = false
                binding.boxCancelRequestStatus.isVisible = false
            }
        }

        // Cập nhật tên quán & số điện thoại
        if (order.shopName.isNotBlank()) {
            currentStoreName = order.shopName
            binding.btnCallShop.text = "📞 Gọi Cho Quán ($currentStoreName)"
            binding.shipperRole.text = "Nhân viên giao hàng • $currentStoreName"
        }
        currentStorePhone = order.shopPhone
        currentShipperPhone = order.shipperPhone

        // Cập nhật tài xế
        if (order.shipperName.isNotBlank()) {
            currentShipperName = order.shipperName
            binding.shipperName.text = currentShipperName
        }

        // Cập nhật Delivery Token (chỉ tạo lại QR Bitmap khi payload thay đổi để tránh khựng UI khi đổi trạng thái)
        if (order.deliveryToken.isNotBlank()) {
            val resolvedOrderId = order.orderId.ifBlank { currentOrderId }
            val displayToken = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.getDisplayToken(resolvedOrderId, order.deliveryToken)
            val qrPayload = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.buildOrderQrPayload(resolvedOrderId, order.deliveryToken)
            binding.tokenPill.text = if (!order.deliveryToken.startsWith("TOKEN_OISHI_", ignoreCase = true)) {
                "$displayToken • PIN: ${order.deliveryToken}"
            } else {
                displayToken
            }
            if (qrPayload != lastGeneratedQrPayload) {
                lastGeneratedQrPayload = qrPayload
                binding.ivQrCode.setImageBitmap(
                    com.chuyen_de_2.foodorder.core.util.QRCodeUtils.generateQRCodeBitmap(qrPayload, 600, 600)
                )
                binding.qrCodeWrapper.setOnClickListener {
                    showFullscreenQrDialog(qrPayload, binding.tokenPill.text.toString())
                }
            }
        }

        // Cập nhật Stepper 4 bước
        updateStatusStep(order.status)
        if (order.status == Order.Status.COMPLETED) {
            setupDishRatingList(order)
        }

        // Hiển thị cảnh báo mất đơn theo thời gian thực nếu shipper kích hoạt đếm ngược
        if (order.hasIncidentWarning && order.status != Order.Status.COMPLETED &&
            order.status != Order.Status.CANCELLED && order.status != Order.Status.BOOM &&
            order.status != Order.Status.RETURNING_TO_STORE && order.status != Order.Status.RETURNED_TO_STORE) {
            binding.boxWarningActiveOnOrder.isVisible = true
            binding.btnHeaderWarn.isVisible = true
            val warnMsg = order.incidentWarningText.ifBlank {
                "Tài xế đã có mặt tại địa chỉ và đang chờ bạn nhận món. Vui lòng chuẩn bị mã QR bên dưới để đối soát ngay!"
            }
            binding.lblWarningInlineDesc.text = warnMsg
            binding.btnScrollToQr.setOnClickListener {
                binding.scrollViewContent.smoothScrollTo(0, binding.qrCodeWrapper.top)
            }
        }

        // Cập nhật chi tiết các món ăn & thanh toán (chỉ render lần đầu để chuyển trạng thái mượt 0ms)
        if (!hasBoundOrderItems && order.items.isNotEmpty()) {
            hasBoundOrderItems = true
            bindOrderItems(order)
        }

        // Geocode địa chỉ khách hàng & quán ăn lên bản đồ Google Maps (chỉ chạy 1 lần khi địa chỉ mới)
        if (customerLatLng == null || lastGeocodedAddress != order.address) {
            lastGeocodedAddress = order.address
            lifecycleScope.launch {
                val map = googleMap ?: return@launch
                val cLatLng = GoogleMapUtils.geocodeAddress(this@TrackingActivity, order.address)
                customerLatLng = cLatLng

                val store = firebaseRepository.getStoreOnce(order.storeId.ifBlank { order.shopId })
                if (currentStorePhone.isBlank() && store != null && store.phone.isNotBlank()) {
                    currentStorePhone = store.phone
                }

                val sLatLng = if (store != null && store.latitude != 0.0 && store.longitude != 0.0) {
                    LatLng(store.latitude, store.longitude)
                } else if (store != null && store.address.isNotBlank()) {
                    GoogleMapUtils.geocodeAddress(this@TrackingActivity, "${store.address}, TP.HCM")
                } else {
                    GoogleMapUtils.findFallbackDistrict(order.shopName)
                }
                storeLatLng = sLatLng

                if (storeMarker == null) {
                    storeMarker = map.addMarker(
                        MarkerOptions()
                            .position(sLatLng)
                            .title("Quán ăn: ${order.shopName}")
                            .snippet("Điểm nhận món")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                    )
                } else {
                    storeMarker?.position = sLatLng
                }

                if (customerMarker == null) {
                    customerMarker = map.addMarker(
                        MarkerOptions()
                            .position(cLatLng)
                            .title("Điểm giao hàng")
                            .snippet(order.address)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
                } else {
                    customerMarker?.position = cLatLng
                }

                updateRouteAndCamera()
            }
        }
    }

    private fun updateStatusStep(status: String) {
        val displayOrderCode = if (currentOrderId.startsWith("OISHI", ignoreCase = true)) currentOrderId else "#ORD_${currentOrderId.takeLast(6).uppercase()}"
        val shortCode = if (currentOrderId.startsWith("OISHI", ignoreCase = true)) currentOrderId else currentOrderId.takeLast(6).uppercase()
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val mutedColor = ContextCompat.getColor(this, R.color.text_secondary)
        val greenColor = ContextCompat.getColor(this, R.color.status_completed)

        when (status) {
            Order.Status.PAID -> {
                setTimelineProgress(0)
                setNodeActive(1)
                binding.lblStatusIcon.text = "💰"
                binding.lblStatusHeadline.text = "Đã thanh toán thành công"
                binding.lblStatusSub.text = "Mã đơn: $displayOrderCode • Quán đã nhận tiền và đang chuẩn bị món"
                binding.lblDetailIcon.text = "✅"
                binding.lblDetailDesc.text =
                    "Thanh toán chuyển khoản VietQR thành công. $currentStoreName đã tiếp nhận đơn và đang chuẩn bị nấu món."
                binding.boxCustomerCancelledNotice.isVisible = false
                binding.boxWarningActiveOnOrder.isVisible = false
                binding.btnHeaderWarn.isVisible = false
                binding.ratingCard.isVisible = false
                binding.qrCompletedBox.isVisible = false
                binding.qrCodeWrapper.isVisible = true
                binding.tokenPill.isVisible = true
            }

            Order.Status.PENDING -> {
                setTimelineProgress(0)
                setNodeActive(1)
                binding.lblStatusIcon.text = "⏳"
                binding.lblStatusHeadline.text = "Đã gửi đơn, chờ quán xác nhận"
                binding.lblStatusSub.text = "Mã đơn: $displayOrderCode • Quán đang kiểm tra món ăn"
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

            Order.Status.ASSIGNED -> {
                setTimelineProgress(50)
                setNodeDone(1)
                setNodeDone(2)
                setNodeActive(3)
                binding.lblStatusIcon.text = "🛵"
                binding.lblStatusHeadline.text = "Tài xế đang đến quán lấy món"
                binding.lblStatusSub.text = "Mã đơn: #ORD_$shortCode • Tài xế đang di chuyển tới $currentStoreName"
                binding.lblDetailIcon.text = "🛵"
                binding.lblDetailDesc.text =
                    "Tài xế $currentShipperName đã nhận đơn và đang di chuyển tới $currentStoreName để lấy món."
                binding.boxCustomerCancelledNotice.isVisible = false
                binding.boxWarningActiveOnOrder.isVisible = false
                binding.btnHeaderWarn.isVisible = false
                binding.ratingCard.isVisible = false
                binding.qrCompletedBox.isVisible = false
                binding.qrCodeWrapper.isVisible = true
                binding.tokenPill.isVisible = true
            }

            Order.Status.ARRIVED_AT_SHOP -> {
                setTimelineProgress(60)
                setNodeDone(1)
                setNodeDone(2)
                setNodeActive(3)
                binding.lblStatusIcon.text = "🏪"
                binding.lblStatusHeadline.text = "Tài xế đã đến quán ăn"
                binding.lblStatusSub.text = "Mã đơn: #ORD_$shortCode • Đang kiểm tra món tại quầy $currentStoreName"
                binding.lblDetailIcon.text = "🏪"
                binding.lblDetailDesc.text =
                    "Tài xế $currentShipperName đã có mặt tại $currentStoreName và đang kiểm tra đóng gói túi món ăn."
                binding.boxCustomerCancelledNotice.isVisible = false
                binding.boxWarningActiveOnOrder.isVisible = false
                binding.btnHeaderWarn.isVisible = false
                binding.ratingCard.isVisible = false
                binding.qrCompletedBox.isVisible = false
                binding.qrCodeWrapper.isVisible = true
                binding.tokenPill.isVisible = true
            }

            Order.Status.DELIVERING -> {
                setTimelineProgress(75)
                setNodeDone(1)
                setNodeDone(2)
                setNodeActive(3)
                binding.lblStatusIcon.text = "🛵"
                binding.lblStatusHeadline.text = "Tài xế đã lấy hàng, đang giao tới bạn"
                binding.lblStatusSub.text = "Mã đơn: #ORD_$shortCode • Vui lòng để ý điện thoại"
                binding.lblDetailIcon.text = "🛵"
                binding.lblDetailDesc.text =
                    "Tài xế $currentShipperName đã lấy món và đang di chuyển trên lộ trình tới nhà bạn. Bạn có thể xem vị trí trên bản đồ."
                binding.boxCustomerCancelledNotice.isVisible = false
                binding.boxWarningActiveOnOrder.isVisible = false
                binding.btnHeaderWarn.isVisible = false
                binding.ratingCard.isVisible = false
                binding.qrCompletedBox.isVisible = false
                binding.qrCodeWrapper.isVisible = true
                binding.tokenPill.isVisible = true
            }

            Order.Status.ARRIVED_AT_CUSTOMER -> {
                setTimelineProgress(92)
                setNodeDone(1)
                setNodeDone(2)
                setNodeDone(3)
                setNodeActive(4)
                binding.lblStatusIcon.text = "📍"
                binding.lblStatusHeadline.text = "🔔 Tài xế đã đến nơi, vui lòng nhận món!"
                binding.lblStatusHeadline.setTextColor(primaryColor)
                binding.lblStatusSub.text = "Mã đơn: #ORD_$shortCode • Tài xế đang chờ trước cửa"
                binding.lblDetailIcon.text = "📍"
                binding.lblDetailDesc.text =
                    "Tài xế $currentShipperName đã đến điểm giao hàng. Vui lòng chuẩn bị mã QR Token bên dưới để đối soát nhận món ăn!"
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

            Order.Status.RETURNING_TO_STORE, Order.Status.RETURNED_TO_STORE -> {
                binding.lblStatusIcon.text = "🚨"
                binding.lblStatusHeadline.text = if (status == Order.Status.RETURNED_TO_STORE) {
                    "Đơn hàng đã được trả về quán"
                } else {
                    "Đang chuyển trả món về quán"
                }
                binding.lblStatusHeadline.setTextColor(ContextCompat.getColor(this, R.color.error))
                binding.lblStatusSub.text = "Đơn hàng bị hủy do không liên lạc được sau thời gian chờ"
                binding.lblDetailIcon.text = "⚠️"
                val reasonText = currentOrder?.cancelReason?.ifBlank { null }
                    ?: currentOrder?.riskReason?.ifBlank { null }
                    ?: "Khách không nhận món sau các lần đếm ngược chờ nhận hàng (Bom hàng)."
                binding.lblDetailDesc.text =
                    "Đơn hàng đã bị hủy và tài xế đang hoàn trả món về quán. Lý do: $reasonText"
                binding.boxCustomerCancelledNotice.isVisible = true
                binding.lblCancelReason.text = "Lý do: $reasonText\nTài xế đang hoàn trả lại đồ ăn cho $currentStoreName."
                binding.boxWarningActiveOnOrder.isVisible = false
                binding.btnHeaderWarn.isVisible = true
                binding.ratingCard.isVisible = false
                binding.qrCodeWrapper.isVisible = false
                binding.tokenPill.isVisible = false
                binding.qrCompletedBox.isVisible = false
            }

            Order.Status.CANCELLED, Order.Status.BOOM -> {
                binding.lblStatusIcon.text = "🚫"
                binding.lblStatusHeadline.text = "Đơn hàng đã bị hủy"
                binding.lblStatusHeadline.setTextColor(ContextCompat.getColor(this, R.color.error))
                binding.lblStatusSub.text = "Đơn hàng gặp sự cố giao hàng hoặc bom hàng"
                binding.lblDetailIcon.text = "⚠️"
                val reasonText = currentOrder?.cancelReason?.ifBlank { null }
                    ?: currentOrder?.riskReason?.ifBlank { null }
                    ?: "Đơn hàng đã chính thức bị hủy. Tiền món đang được xử lý hoàn trả theo chính sách Oishi Food."
                binding.lblDetailDesc.text = reasonText
                binding.boxCustomerCancelledNotice.isVisible = true
                binding.lblCancelReason.text = "Lý do: $reasonText"
                binding.boxWarningActiveOnOrder.isVisible = false
                binding.btnHeaderWarn.isVisible = true
                binding.ratingCard.isVisible = false
                binding.qrCodeWrapper.isVisible = false
                binding.tokenPill.isVisible = false
                binding.qrCompletedBox.isVisible = false
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
        val isPaid = order.paymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true)
        val payMethodStr = when (order.paymentMethod.uppercase()) {
            Order.PaymentMethod.MOMO -> if (isPaid) "Ví điện tử MoMo (Đã chuyển khoản)" else "Ví điện tử MoMo (Chờ chuyển khoản)"
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
                    .title("🛵 Tài xế: $currentShipperName")
                    .snippet("Đang di chuyển giao đơn")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        } else {
            shipperMarker?.position = latLng
        }

        updateRouteAndCamera()
    }

    private fun updateRouteAndCamera() {
        val map = googleMap ?: return
        val sLatLng = storeLatLng ?: return
        val cLatLng = customerLatLng ?: return
        val shipLatLng = shipperMarker?.position ?: sLatLng

        // Vẽ đường nối lộ trình di chuyển Google Maps
        routePolyline?.remove()
        routePolyline = map.addPolyline(
            PolylineOptions()
                .add(sLatLng, shipLatLng, cLatLng)
                .color(ContextCompat.getColor(this, R.color.primary))
                .width(8f)
                .geodesic(true)
        )

        try {
            val boundsBuilder = LatLngBounds.Builder()
            boundsBuilder.include(sLatLng)
            boundsBuilder.include(cLatLng)
            boundsBuilder.include(shipLatLng)
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
        } catch (_: Exception) {}
    }

    private fun showFullscreenQrDialog(qrPayload: String, tokenLabel: String) {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(48, 48, 48, 36)
        }
        val ivLargeQr = android.widget.ImageView(this).apply {
            val sizePx = (resources.displayMetrics.density * 260).toInt()
            layoutParams = android.widget.LinearLayout.LayoutParams(sizePx, sizePx)
            setImageBitmap(com.chuyen_de_2.foodorder.core.util.QRCodeUtils.generateQRCodeBitmap(qrPayload, 800, 800))
        }
        val tvToken = TextView(this).apply {
            text = tokenLabel
            textSize = 14f
            paint.isFakeBoldText = true
            gravity = android.view.Gravity.CENTER
            setPadding(0, 24, 0, 8)
            setTextColor(ContextCompat.getColor(this@TrackingActivity, R.color.text_primary))
        }
        val tvHint = TextView(this).apply {
            text = "Đưa mã QR này cho tài xế Shipper quét để xác nhận hoàn tất giao hàng"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@TrackingActivity, R.color.text_secondary))
        }
        container.addView(ivLargeQr)
        container.addView(tvToken)
        container.addView(tvHint)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("📱 Mã QR Nhận Đơn Hàng")
            .setView(container)
            .setPositiveButton("Đóng", null)
            .show()
    }
}
