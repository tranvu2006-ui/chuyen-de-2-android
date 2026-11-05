package com.chuyen_de_2.foodorder.customer.ui.complaint

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Complaint
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.ImageLoadUtils
import com.chuyen_de_2.foodorder.core.util.ImagePickerUtils
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityComplaintBinding
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Trung tâm gửi khiếu nại đơn hàng chuẩn b12.html
 * - Chọn đơn hàng từ danh sách đơn đã đặt hoặc nhập mã đơn tùy chỉnh.
 * - Xem trước chi tiết đơn hàng (Quán ăn, danh sách món, Shipper, tổng tiền).
 * - Phân loại nguyên nhân (ATTP, Sai món, Thái độ, Trễ hạn, Sai tiền, Khác) kèm huy hiệu ưu tiên.
 * - Hộp tải ảnh minh chứng (Upload Box) chuẩn b12.html với xem trước ảnh lớn và gỡ ảnh linh hoạt.
 * - Thông báo quy trình xử phạt 3 cấp độ của Admin.
 */
@AndroidEntryPoint
class ComplaintActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComplaintBinding

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private var selectedEvidenceBase64: String = ""
    private var customerOrders: List<Order> = emptyList()
    private var activeOrder: Order? = null
    private var manualOrderIdSearchJob: Job? = null

    private val pickEvidenceLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val base64 = ImagePickerUtils.compressUriToBase64(this, uri, maxDimension = 720, quality = 80)
        if (!base64.isNullOrBlank()) {
            selectedEvidenceBase64 = base64
            binding.layoutUploadBoxPlaceholder.visibility = View.GONE
            binding.layoutEvidencePreviewContainer.visibility = View.VISIBLE
            ImageLoadUtils.loadEvidenceImage(binding.ivEvidencePreview, base64, cornerDp = 12)
            Toast.makeText(this, "✓ Đã đính kèm ảnh minh chứng thành công!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Không thể đọc ảnh đã chọn. Vui lòng thử ảnh khác!", Toast.LENGTH_SHORT).show()
        }
    }

    private val reasonEntries = listOf(
        ReasonItem("🍲 Vệ sinh an toàn thực phẩm (Có mùi lạ, thức ăn hư hỏng / nguội ngắt)", Complaint.ComplaintType.ATTP, Complaint.Priority.HIGH),
        ReasonItem("🍱 Giao sai món ăn hoặc thiếu món trong bill", Complaint.ComplaintType.WRONG_ITEM, Complaint.Priority.MEDIUM),
        ReasonItem("🛵 Thái độ phục vụ của tài xế hoặc quán ăn không đúng chuẩn", Complaint.ComplaintType.ATTITUDE, Complaint.Priority.MEDIUM),
        ReasonItem("⏱️ Giao hàng quá chậm trễ so với cam kết (>45 phút)", Complaint.ComplaintType.DELIVERY_DELAY, Complaint.Priority.MEDIUM),
        ReasonItem("💸 Sai tiền hoặc không áp dụng đúng khuyến mãi / mã giảm giá", Complaint.ComplaintType.FRAUD, Complaint.Priority.MEDIUM),
        ReasonItem("❓ Lý do tranh chấp khác", Complaint.ComplaintType.OTHER, Complaint.Priority.LOW)
    )

    data class ReasonItem(
        val title: String,
        val type: String,
        val priority: String
    ) {
        override fun toString(): String = title
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComplaintBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
        setupWindowInsets()

        setupToolbar()
        setupReasonSpinner()
        setupOrderSelection()
        setupUploadBox()
        setupSubmitButton()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val topInset = if (insets.top > 0) insets.top else InsetUtils.getFallbackStatusBarHeight(this)

            binding.viewStatusBar.updateLayoutParams {
                height = topInset
            }
            binding.root.updatePadding(bottom = navInsets.bottom)
            windowInsets
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupReasonSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            reasonEntries
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerReason.adapter = adapter

        binding.spinnerReason.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = reasonEntries.getOrNull(position) ?: return
                when (item.priority) {
                    Complaint.Priority.HIGH -> {
                        binding.tvPriorityBadge.text = "Ưu tiên: Cao 🔴"
                        binding.tvPriorityBadge.setBackgroundResource(R.drawable.bg_badge_red)
                        binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(this@ComplaintActivity, R.color.danger))
                    }
                    Complaint.Priority.MEDIUM -> {
                        binding.tvPriorityBadge.text = "Ưu tiên: Bình thường 🟠"
                        binding.tvPriorityBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                        binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(this@ComplaintActivity, R.color.brand_orange))
                    }
                    else -> {
                        binding.tvPriorityBadge.text = "Ưu tiên: Thấp ⚪"
                        binding.tvPriorityBadge.setBackgroundResource(R.drawable.bg_badge_gray)
                        binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(this@ComplaintActivity, R.color.text_secondary))
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupOrderSelection() {
        val intentOrderId = intent.getStringExtra(Constants.EXTRA_ORDER_ID).orEmpty()
        val currentUid = firebaseAuth.currentUser?.uid.orEmpty()

        binding.btnToggleManualInput.setOnClickListener {
            val isCurrentlyVisible = binding.tilManualOrderId.visibility == View.VISIBLE
            if (isCurrentlyVisible) {
                binding.tilManualOrderId.visibility = View.GONE
                binding.spinnerOrders.visibility = View.VISIBLE
                binding.btnToggleManualInput.text = "Nhập mã đơn"
            } else {
                binding.tilManualOrderId.visibility = View.VISIBLE
                binding.spinnerOrders.visibility = View.GONE
                binding.btnToggleManualInput.text = "Chọn từ danh sách"
            }
        }

        binding.etOrderId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.trim().orEmpty()
                manualOrderIdSearchJob?.cancel()
                if (input.isNotBlank()) {
                    manualOrderIdSearchJob = lifecycleScope.launch {
                        delay(500)
                        val order = firebaseRepository.getOrder(input)
                        if (order != null) {
                            activeOrder = order
                            bindOrderPreview(order)
                        }
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Lắng nghe danh sách đơn hàng của khách hàng
        lifecycleScope.launch {
            if (currentUid.isNotBlank()) {
                firebaseRepository.observeOrdersByCustomer(currentUid).collectLatest { orders ->
                    customerOrders = orders
                    populateOrderSpinner(intentOrderId)
                }
            } else {
                populateOrderSpinner(intentOrderId)
            }
        }
    }

    private fun populateOrderSpinner(presetOrderId: String) {
        val displayList = mutableListOf<String>()

        if (customerOrders.isEmpty()) {
            if (presetOrderId.isNotBlank()) {
                displayList.add("#${presetOrderId.takeLast(8)} (Đơn hàng từ chi tiết)")
            } else {
                displayList.add("Chưa có đơn hàng nào trong tài khoản")
            }
        } else {
            customerOrders.forEach { order ->
                val shortId = order.orderId.takeLast(6)
                val shop = order.shopName.ifBlank { "Quán ăn" }
                val amount = String.format(Locale.getDefault(), "%,d đ", order.effectiveTotal)
                displayList.add("#$shortId - $shop ($amount)")
            }
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            displayList
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerOrders.adapter = adapter

        // Tự động chọn đơn hàng nếu có mã đơn truyền từ Intent
        if (presetOrderId.isNotBlank()) {
            val idx = customerOrders.indexOfFirst { it.orderId == presetOrderId }
            if (idx != -1) {
                binding.spinnerOrders.setSelection(idx)
            } else {
                // Fetch trực tiếp đơn hàng này từ Firebase
                lifecycleScope.launch {
                    val directOrder = firebaseRepository.getOrder(presetOrderId)
                    if (directOrder != null) {
                        activeOrder = directOrder
                        bindOrderPreview(directOrder)
                    }
                }
            }
            binding.etOrderId.setText(presetOrderId)
        }

        binding.spinnerOrders.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val order = customerOrders.getOrNull(position)
                if (order != null) {
                    activeOrder = order
                    binding.etOrderId.setText(order.orderId)
                    bindOrderPreview(order)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun bindOrderPreview(order: Order) {
        binding.orderPreviewBox.visibility = View.VISIBLE
        binding.tvPreviewStoreName.text = "Quán bị khiếu nại: ${order.shopName.ifBlank { "Quán ăn #${order.storeId.takeLast(6)}" }}"

        val itemsSummary = if (order.items.isNotEmpty()) {
            order.items.values.joinToString(", ") { "${it.quantity}x ${it.name}" }
        } else {
            "Chi tiết các món ăn theo đơn hàng"
        }
        binding.tvPreviewOrderItems.text = "🍲 Món đặt: $itemsSummary"

        val driverName = order.effectiveDriverName.ifBlank { "Chưa phân công shipper" }
        val driverPhone = order.shipperPhone.ifBlank { "" }
        binding.tvPreviewDriverInfo.text = "🛵 Shipper phụ trách: $driverName ${if (driverPhone.isNotBlank()) "($driverPhone)" else ""}"

        binding.tvPreviewTotalAmount.text = "Tổng tiền: " + String.format(Locale.getDefault(), "%,d đ", order.effectiveTotal)
        binding.tvPreviewOrderStatus.text = order.orderStatus.ifBlank { "HOÀN TẤT" }
    }

    private fun setupUploadBox() {
        binding.layoutUploadBoxPlaceholder.setOnClickListener {
            pickEvidenceLauncher.launch("image/*")
        }

        binding.ivEvidencePreview.setOnClickListener {
            if (selectedEvidenceBase64.isNotBlank()) {
                showFullEvidenceDialog(selectedEvidenceBase64)
            }
        }

        binding.btnChangeEvidenceImage.setOnClickListener {
            pickEvidenceLauncher.launch("image/*")
        }

        binding.btnClearEvidenceImage.setOnClickListener {
            selectedEvidenceBase64 = ""
            binding.layoutUploadBoxPlaceholder.visibility = View.VISIBLE
            binding.layoutEvidencePreviewContainer.visibility = View.GONE
            try {
                Glide.with(this).clear(binding.ivEvidencePreview)
            } catch (_: Exception) {}
            binding.ivEvidencePreview.setImageDrawable(null)
            Toast.makeText(this, "Đã gỡ bỏ ảnh minh chứng", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFullEvidenceDialog(evidenceImage: String) {
        val dialog = android.app.Dialog(this)
        val iv = android.widget.ImageView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setPadding(16, 16, 16, 16)
        }
        ImageLoadUtils.loadEvidenceImage(iv, evidenceImage, cornerDp = 0)
        dialog.setContentView(iv)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun setupSubmitButton() {
        binding.btnSubmitComplaint.setOnClickListener {
            val resolvedOrderId = if (binding.tilManualOrderId.visibility == View.VISIBLE) {
                binding.etOrderId.text?.toString().orEmpty().trim()
            } else {
                activeOrder?.orderId ?: binding.etOrderId.text?.toString().orEmpty().trim()
            }

            val description = binding.etDescription.text?.toString().orEmpty().trim()
            val selectedReasonItem = binding.spinnerReason.selectedItem as? ReasonItem ?: reasonEntries[0]

            if (resolvedOrderId.isBlank()) {
                Toast.makeText(this, "⚠️ Vui lòng chọn hoặc nhập mã đơn hàng cần khiếu nại", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (description.length < 5) {
                Toast.makeText(this, "⚠️ Vui lòng mô tả chi tiết sự việc (tối thiểu 5 ký tự)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val customerId = firebaseAuth.currentUser?.uid ?: "anonymous_customer"
            val storeIdIntent = intent.getStringExtra("EXTRA_STORE_ID").orEmpty()

            binding.btnSubmitComplaint.isEnabled = false
            binding.btnSubmitComplaint.text = "Đang gửi hồ sơ lên Hội đồng Admin..."

            lifecycleScope.launch {
                val order = activeOrder ?: firebaseRepository.getOrder(resolvedOrderId)
                val resolvedStoreId = storeIdIntent.ifBlank {
                    order?.shopId?.ifBlank { order?.storeId }.orEmpty()
                }

                val isDriverFault = selectedReasonItem.type == Complaint.ComplaintType.ATTITUDE ||
                    selectedReasonItem.title.contains("tài xế", ignoreCase = true)

                val targetType = if (isDriverFault) {
                    Complaint.TargetType.DRIVER
                } else {
                    Complaint.TargetType.STORE
                }

                val targetId = if (isDriverFault) order?.effectiveDriverId.orEmpty() else resolvedStoreId
                val targetName = if (isDriverFault) order?.effectiveDriverName.orEmpty() else order?.shopName.orEmpty()
                val targetPhone = if (isDriverFault) order?.shipperPhone.orEmpty() else order?.shopPhone.orEmpty()

                val complaintCode = "KN-${System.currentTimeMillis() % 100000}"
                val complaintId = "CMP_${resolvedOrderId.takeLast(6)}_${System.currentTimeMillis() % 10000}"

                val complaint = Complaint(
                    complaintId = complaintId,
                    orderId = resolvedOrderId,
                    senderType = Complaint.SenderType.CUSTOMER,
                    customerId = customerId,
                    senderName = order?.customerName.orEmpty(),
                    senderPhone = order?.customerPhone.orEmpty(),
                    targetType = targetType,
                    targetId = targetId,
                    targetName = targetName,
                    targetPhone = targetPhone,
                    storeId = resolvedStoreId,
                    driverId = order?.effectiveDriverId.orEmpty(),
                    complaintType = selectedReasonItem.type,
                    title = selectedReasonItem.title,
                    reason = description,
                    evidenceImage = selectedEvidenceBase64,
                    priority = selectedReasonItem.priority,
                    status = Complaint.ComplaintStatus.PENDING,
                    createdAt = System.currentTimeMillis()
                )

                val result = firebaseRepository.submitComplaint(complaint)
                if (result.isSuccess) {
                    AlertDialog.Builder(this@ComplaintActivity)
                        .setTitle("🎉 Gửi Khiếu Nại Thành Công!")
                        .setMessage("Mã hồ sơ tiếp nhận: #$complaintCode\n\nBan Quản Trị Oishi Food sẽ tiến hành thẩm định và áp dụng chế tài xử phạt đối với ${if (isDriverFault) "tài xế" else "quán ăn"} trong vòng 2 giờ.")
                        .setCancelable(false)
                        .setPositiveButton("Đã hiểu") { _, _ ->
                            finish()
                        }
                        .show()
                } else {
                    binding.btnSubmitComplaint.isEnabled = true
                    binding.btnSubmitComplaint.text = "Gửi Khiếu Nại Lên Ban Quản Trị ⚖️"
                    Toast.makeText(
                        this@ComplaintActivity,
                        "Lỗi khi gửi: ${result.exceptionOrNull()?.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
