package com.chuyen_de_2.foodorder.admin.ui.complaint

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.ActivityAdminComplaintDetailBinding
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Complaint
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.util.ImageLoadUtils
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AdminComplaintDetailActivity : AppCompatActivity() {

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private lateinit var binding: ActivityAdminComplaintDetailBinding
    private var complaintId: String = ""
    private var currentComplaint: Complaint? = null
    private var currentOrder: Order? = null

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminComplaintDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        complaintId = intent.getStringExtra(EXTRA_COMPLAINT_ID).orEmpty()
        if (complaintId.isBlank()) {
            Toast.makeText(this, "Không tìm thấy mã khiếu nại", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupWindowInsets()
        setupViews()
        observeComplaintData()
    }

    private fun setupWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        InsetUtils.setLightStatusBar(this, false)

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

    private fun setupViews() {
        // Nút Back
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Quyết định Radio Change (Chấp thuận / Bác bỏ)
        binding.rgComplaintDecision.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbResolveReject) {
                binding.rbPenaltyNone.isChecked = true
                for (i in 0 until binding.rgComplaintPenalty.childCount) {
                    val child = binding.rgComplaintPenalty.getChildAt(i)
                    child.isEnabled = (child.id == R.id.rbPenaltyNone)
                }
            } else {
                for (i in 0 until binding.rgComplaintPenalty.childCount) {
                    binding.rgComplaintPenalty.getChildAt(i).isEnabled = true
                }
            }
            updatePenaltyImpactCallout()
        }

        // Mức phạt Radio Change
        binding.rgComplaintPenalty.setOnCheckedChangeListener { _, _ ->
            updatePenaltyImpactCallout()
        }

        // Quick Note Chips
        binding.chipNoteVerified.setOnClickListener {
            binding.etAdminVerdictNote.setText("Đã xác minh bằng chứng vi phạm chính xác, tiến hành thi hành chế tài theo quy chế sàn.")
            binding.rbResolveApprove.isChecked = true
            if (binding.rbPenaltyNone.isChecked) {
                binding.rbPenaltyWarn.isChecked = true
            }
        }
        binding.chipNoteInsufficient.setOnClickListener {
            binding.etAdminVerdictNote.setText("Bác bỏ khiếu nại do không đủ căn cứ xác thực vi phạm hoặc hình ảnh hiện trường không hợp lệ.")
            binding.rbResolveReject.isChecked = true
            binding.rbPenaltyNone.isChecked = true
        }
        binding.chipNoteMediated.setOnClickListener {
            binding.etAdminVerdictNote.setText("Hai bên đã tự thương lượng và bồi hoàn thỏa đáng, đóng hồ sơ khiếu nại.")
            binding.rbResolveApprove.isChecked = true
            binding.rbPenaltyNone.isChecked = true
        }
        binding.chipNoteFalseReport.setOnClickListener {
            binding.rbResolveCounterPenalty.isChecked = true
            binding.rbPenaltyWarn.isChecked = true
            binding.etAdminVerdictNote.setText("Bác bỏ khiếu nại & phạt người gửi do báo cáo sai sự thật, vu khống đối tượng.")
        }
        binding.chipNoteAbuse.setOnClickListener {
            binding.rbResolveCounterPenalty.isChecked = true
            binding.rbPenaltyHide.isChecked = true
            binding.etAdminVerdictNote.setText("Bác bỏ hồ sơ & áp dụng chế tài do lạm dụng khiếu nại nhiều lần không căn cứ.")
        }

        // Nút Bác bỏ nhanh
        binding.btnQuickReject.setOnClickListener {
            binding.rbResolveReject.isChecked = true
            binding.rbPenaltyNone.isChecked = true
            if (binding.etAdminVerdictNote.text.isNullOrBlank()) {
                binding.etAdminVerdictNote.setText("Bác bỏ khiếu nại do không đủ căn cứ vi phạm hợp lệ.")
            }
            submitVerdict(Complaint.ComplaintStatus.REJECTED, Store.PenaltyTier.NONE)
        }

        // Nút Xác nhận phán quyết
        binding.btnSubmitVerdict.setOnClickListener {
            val isCounterPenalty = binding.rbResolveCounterPenalty.isChecked
            val status = if (binding.rbResolveReject.isChecked || isCounterPenalty) {
                Complaint.ComplaintStatus.REJECTED
            } else {
                Complaint.ComplaintStatus.RESOLVED
            }

            val penaltyTier = when {
                binding.rbResolveReject.isChecked -> Store.PenaltyTier.NONE
                else -> when (binding.rgComplaintPenalty.checkedRadioButtonId) {
                    R.id.rbPenaltyWarn -> Store.PenaltyTier.WARN
                    R.id.rbPenaltyHide -> Store.PenaltyTier.HIDE
                    R.id.rbPenaltyLock -> Store.PenaltyTier.LOCK
                    else -> if (isCounterPenalty) Store.PenaltyTier.WARN else Store.PenaltyTier.NONE
                }
            }

            submitVerdict(status, penaltyTier, isCounterPenalty)
        }

        // Chạm ảnh để xem toàn màn hình
        binding.ivEvidencePhoto.setOnClickListener {
            currentComplaint?.evidenceImage?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                showFullEvidenceDialog(imageUrl)
            }
        }
    }

    private fun observeComplaintData() {
        lifecycleScope.launch {
            firebaseRepository.observeComplaint(complaintId).collectLatest { complaint ->
                if (complaint == null) {
                    // Nếu chưa tải được hoặc không tồn tại
                    return@collectLatest
                }
                currentComplaint = complaint
                bindComplaintData(complaint)

                // Lấy đơn hàng liên quan nếu có
                if (complaint.orderId.isNotBlank()) {
                    loadLinkedOrder(complaint.orderId)
                } else {
                    binding.cardLinkedOrder.visibility = View.GONE
                }
            }
        }
    }

    private fun bindComplaintData(complaint: Complaint) {
        val shortCompId = complaint.complaintId.takeLast(8).uppercase()
        binding.tvHeaderComplaintCode.text = "#CMP_$shortCompId"
        binding.tvComplaintFullId.text = "Mã hồ sơ: #CMP_${complaint.complaintId.uppercase()}"

        // Thời gian gửi
        val timeStr = if (complaint.createdAt > 0) dateFormat.format(Date(complaint.createdAt)) else "Vừa xong"
        binding.tvComplaintTime.text = "Thời gian gửi: $timeStr"

        // Trạng thái hồ sơ
        val isResolved = complaint.status.equals(Complaint.ComplaintStatus.RESOLVED, ignoreCase = true)
        val isRejected = complaint.status.equals(Complaint.ComplaintStatus.REJECTED, ignoreCase = true)

        when {
            isResolved -> {
                binding.tvHeaderStatusBadge.text = "ĐÃ GIẢI QUYẾT"
                binding.tvHeaderStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
                binding.tvHeaderStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
            }
            isRejected -> {
                binding.tvHeaderStatusBadge.text = "ĐÃ BÁC BỎ"
                binding.tvHeaderStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
                binding.tvHeaderStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.danger))
            }
            else -> {
                binding.tvHeaderStatusBadge.text = "CHỜ XỬ LÝ"
                binding.tvHeaderStatusBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.tvHeaderStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_orange))
            }
        }

        // Phân loại vấn đề sự cố
        val typeTitle = when (complaint.complaintType) {
            Complaint.ComplaintType.ATTP -> "⚠️ Vệ sinh An toàn Thực phẩm (ATTP)"
            Complaint.ComplaintType.WRONG_ITEM -> "📦 Sai / thiếu món trong đơn"
            Complaint.ComplaintType.ATTITUDE -> "👤 Thái độ phục vụ không phù hợp"
            Complaint.ComplaintType.DELIVERY_DELAY -> "⏱️ Giao trễ so với cam kết"
            Complaint.ComplaintType.BOM_ORDER -> "💣 Khách bom hàng / Không liên lạc được"
            Complaint.ComplaintType.FRAUD -> "💳 Gian lận / Tính sai tiền"
            else -> if (complaint.title.isNotBlank()) complaint.title else "Vấn đề sự cố khác"
        }
        binding.tvComplaintTypeBadge.text = typeTitle

        // Mức độ ưu tiên
        when (complaint.priority.uppercase(Locale.getDefault())) {
            Complaint.Priority.HIGH -> {
                binding.tvPriorityBadge.text = "🔴 ƯU TIÊN CAO"
                binding.tvPriorityBadge.setBackgroundResource(R.drawable.bg_badge_red)
                binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(this, R.color.danger))
            }
            Complaint.Priority.LOW -> {
                binding.tvPriorityBadge.text = "⚪ THẤP"
                binding.tvPriorityBadge.setBackgroundResource(R.drawable.bg_badge_blue)
                binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_blue))
            }
            else -> {
                binding.tvPriorityBadge.text = "🟠 TRUNG BÌNH"
                binding.tvPriorityBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_orange))
            }
        }

        // 1. Nguồn Gửi Khiếu Nại (Sender)
        val senderRoleStr = when (complaint.senderType) {
            Complaint.SenderType.DRIVER -> "Tài xế giao hàng"
            Complaint.SenderType.STORE -> "Quán ăn đối tác"
            else -> "Khách hàng đặt món"
        }
        binding.tvSenderRoleBadge.text = senderRoleStr
        binding.tvSenderName.text = complaint.senderName.ifBlank { "Người dùng #${complaint.customerId.takeLast(6)}" }
        val senderPhone = complaint.senderPhone.ifBlank { "Chưa cập nhật SĐT" }
        binding.tvSenderPhone.text = senderPhone

        binding.btnCopySenderPhone.setOnClickListener {
            copyToClipboard("SĐT Nguồn khiếu nại", senderPhone)
        }
        binding.btnCallSender.setOnClickListener {
            dialPhoneNumber(senderPhone)
        }

        // 2. Đối Tượng Bị Khiếu Nại (Target)
        val targetRoleStr = when (complaint.targetType) {
            Complaint.TargetType.CUSTOMER -> "Khách hàng"
            Complaint.TargetType.DRIVER -> "Tài xế giao hàng"
            else -> "Cửa hàng / Quán ăn"
        }
        binding.tvTargetRoleBadge.text = targetRoleStr
        val targetName = complaint.targetName.ifBlank {
            if (complaint.targetType == Complaint.TargetType.STORE) complaint.storeId.ifBlank { "Quán ăn đối tác" }
            else "Tài khoản #${complaint.targetId.takeLast(6)}"
        }
        binding.tvTargetName.text = targetName
        val targetPhone = complaint.targetPhone.ifBlank { "Chưa cập nhật SĐT" }
        binding.tvTargetPhone.text = targetPhone

        binding.btnCopyTargetPhone.setOnClickListener {
            copyToClipboard("SĐT Đối tượng vi phạm", targetPhone)
        }
        binding.btnCallTarget.setOnClickListener {
            dialPhoneNumber(targetPhone)
        }

        // 3. Nội dung phản ánh
        binding.tvComplaintReasonContent.text = complaint.reason.ifBlank { "Không có mô tả chi tiết từ người gửi." }

        // 4. Bằng chứng hình ảnh
        if (complaint.evidenceImage.isNotBlank()) {
            binding.llEvidenceContainer.visibility = View.VISIBLE
            binding.llNoEvidence.visibility = View.GONE
            ImageLoadUtils.loadEvidenceImage(
                imageView = binding.ivEvidencePhoto,
                imageUrl = complaint.evidenceImage,
                cornerDp = 10
            )
        } else {
            binding.llEvidenceContainer.visibility = View.GONE
            binding.llNoEvidence.visibility = View.VISIBLE
        }

        // 5. Kết quả đã giải quyết trước đó (nếu có)
        val hasPenalty = complaint.penaltyApplied.isNotBlank() && complaint.penaltyApplied != "NONE"
        val hasNote = complaint.adminNote.isNotBlank()
        if (isResolved || isRejected || hasPenalty || hasNote) {
            binding.cardExistingResolution.visibility = View.VISIBLE
            val resolvedTime = if (complaint.resolvedAt > 0) dateFormat.format(Date(complaint.resolvedAt)) else timeStr
            binding.tvResolvedTime.text = resolvedTime

            val penaltyDesc = when {
                complaint.isComplainantPenalized -> {
                    when (complaint.counterPenaltyApplied) {
                        Store.PenaltyTier.WARN -> "⚠️ PHẠT NGƯỢC KHÁCH HÀNG: Cảnh cáo & Trừ 10 điểm uy tín"
                        Store.PenaltyTier.HIDE -> "🚫 PHẠT NGƯỢC KHÁCH HÀNG: Trừ nặng 30 điểm uy tín & Tước quyền COD"
                        Store.PenaltyTier.LOCK -> "🔒 PHẠT NGƯỢC KHÁCH HÀNG: Khóa tài khoản vĩnh viễn (Trừ 50đ)"
                        else -> "⚠️ Đã phạt ngược người khiếu nại"
                    }
                }
                complaint.penaltyApplied == Store.PenaltyTier.WARN -> "🟡 Mức 1 - Cảnh Báo Vi Phạm (WARN)"
                complaint.penaltyApplied == Store.PenaltyTier.HIDE -> "🟠 Mức 2 - Ẩn Tạm Thời / Đình Chỉ Hoạt Động (HIDE)"
                complaint.penaltyApplied == Store.PenaltyTier.LOCK -> "🔴 Mức 3 - Khóa Tài Khoản Vĩnh Viễn (LOCK)"
                isRejected -> "❌ Bác Bỏ Khiếu Nại (Không Vi Phạm)"
                else -> "⚪ Không áp dụng chế tài"
            }
            binding.tvExistingPenaltyApplied.text = "Chế tài: $penaltyDesc"
            binding.tvExistingAdminNote.text = "Biên bản Admin: ${complaint.adminNote.ifBlank { "Đã thụ lý theo quy chế sàn." }}"

            // Điền sẵn form nếu admin muốn điều chỉnh lại
            if (binding.etAdminVerdictNote.text.isNullOrBlank()) {
                binding.etAdminVerdictNote.setText(complaint.adminNote)
            }
            if (complaint.isComplainantPenalized) {
                binding.rbResolveCounterPenalty.isChecked = true
                when (complaint.counterPenaltyApplied) {
                    Store.PenaltyTier.WARN -> binding.rbPenaltyWarn.isChecked = true
                    Store.PenaltyTier.HIDE -> binding.rbPenaltyHide.isChecked = true
                    Store.PenaltyTier.LOCK -> binding.rbPenaltyLock.isChecked = true
                    else -> binding.rbPenaltyNone.isChecked = true
                }
            } else if (isRejected) {
                binding.rbResolveReject.isChecked = true
                binding.rbPenaltyNone.isChecked = true
            } else {
                binding.rbResolveApprove.isChecked = true
                when (complaint.penaltyApplied) {
                    Store.PenaltyTier.WARN -> binding.rbPenaltyWarn.isChecked = true
                    Store.PenaltyTier.HIDE -> binding.rbPenaltyHide.isChecked = true
                    Store.PenaltyTier.LOCK -> binding.rbPenaltyLock.isChecked = true
                    else -> binding.rbPenaltyNone.isChecked = true
                }
            }
        } else {
            binding.cardExistingResolution.visibility = View.GONE
        }

        updatePenaltyImpactCallout()
    }

    private suspend fun loadLinkedOrder(orderId: String) {
        val order = firebaseRepository.getOrder(orderId)
        currentOrder = order
        if (order == null) {
            binding.cardLinkedOrder.visibility = View.GONE
            return
        }

        binding.cardLinkedOrder.visibility = View.VISIBLE
        val shortOrderCode = order.orderId.takeLast(6).uppercase()
        binding.tvOrderTitle.text = "Đơn hàng: #ORD-$shortOrderCode"
        binding.tvOrderShopName.text = order.effectiveShopName.ifBlank { "Quán đối tác" }

        val orderTimeStr = if (order.createdAt > 0) dateFormat.format(Date(order.createdAt)) else ""
        binding.tvOrderTime.text = orderTimeStr

        // Danh sách món
        val itemsSummary = if (order.items.isNotEmpty()) {
            order.items.values.joinToString("\n") { item ->
                "• ${item.quantity}x ${item.name} (${currencyFormat.format(item.price)})"
            }
        } else {
            "Không có thông tin chi tiết món ăn"
        }
        binding.tvOrderItemsSummary.text = itemsSummary

        // Tài xế & Tổng tiền
        val driverName = order.effectiveDriverName.ifBlank { "Chưa nhận tài xế" }
        val driverPhone = order.shipperPhone.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        binding.tvOrderDriverInfo.text = "Tài xế: $driverName$driverPhone"
        binding.tvOrderTotalAmount.text = currencyFormat.format(order.effectiveTotal)

        // Trạng thái đơn
        val statusText = when (order.effectiveStatus.uppercase(Locale.getDefault())) {
            Order.Status.COMPLETED -> "HOÀN TẤT"
            Order.Status.CANCELLED -> "ĐÃ HỦY"
            Order.Status.BOOM -> "BOM HÀNG"
            Order.Status.DELIVERING -> "ĐANG GIAO"
            Order.Status.PREPARING -> "ĐANG LÀM MÓN"
            else -> "ĐANG XỬ LÝ"
        }
        binding.tvOrderStatusBadge.text = statusText
    }

    private fun updatePenaltyImpactCallout() {
        val complaint = currentComplaint

        // Nếu chọn Bác bỏ hồ sơ (không phạt)
        if (binding.rbResolveReject.isChecked) {
            binding.tvPenaltySectionTitle.text = "2. Biện pháp chế tài:"
            binding.tvPenaltyImpactCallout.text = "⚪ Bác bỏ bình thường: Không đủ chứng cứ vi phạm rõ ràng. Hồ sơ sẽ được đóng lại và không áp dụng chế tài lên bên nào."
            binding.tvPenaltyImpactCallout.setBackgroundColor(0xFFF3F4F6.toInt())
            binding.tvPenaltyImpactCallout.setTextColor(0xFF4B5563.toInt())
            return
        }

        // Nếu chọn Phạt ngược người khiếu nại (Khách hàng)
        if (binding.rbResolveCounterPenalty.isChecked) {
            val customerName = complaint?.senderName.orEmpty().ifBlank { "Khách hàng" }
            binding.tvPenaltySectionTitle.text = "2. Biện pháp PHẠT NGƯỢC khách hàng ($customerName):"
            binding.tvPenaltyImpactCallout.setBackgroundColor(0xFFFEF2F2.toInt())
            binding.tvPenaltyImpactCallout.setTextColor(0xFF991B1B.toInt())
            when (binding.rgComplaintPenalty.checkedRadioButtonId) {
                R.id.rbPenaltyWarn -> {
                    binding.tvPenaltyImpactCallout.text = "🟡 Phạt ngược Mức 1 - Cảnh cáo & Trừ 10 điểm uy tín: Ghi nhận vi phạm báo cáo sai sự thật. Điểm uy tín của $customerName bị trừ 10 điểm và lưu log kiểm tra."
                }
                R.id.rbPenaltyHide -> {
                    binding.tvPenaltyImpactCallout.text = "🟠 Phạt ngược Mức 2 - Trừ nặng 30 điểm uy tín & Khóa quyền COD: Điểm uy tín bị giảm 30 điểm. Nếu điểm rơi xuống dưới 90 điểm, khách hàng sẽ bị cấm vĩnh viễn thanh toán COD!"
                }
                R.id.rbPenaltyLock -> {
                    binding.tvPenaltyImpactCallout.text = "🔴 Phạt ngược Mức 3 - Khóa tài khoản vĩnh viễn: Trừ 50 điểm uy tín và KHÓA TÀI KHOẢN người dùng (LOCKED). Khách hàng không thể đăng nhập vào ứng dụng."
                }
                else -> {
                    binding.tvPenaltyImpactCallout.text = "⚠️ Vui lòng chọn 1 trong 3 mức chế tài phạt ngược để áp dụng lên khách hàng vi phạm."
                }
            }
            return
        }

        // Chấp thuận khiếu nại: phạt bên bị khiếu nại
        binding.tvPenaltySectionTitle.text = "2. Biện pháp chế tài đối tượng vi phạm (3 Mức):"
        binding.tvPenaltyImpactCallout.setBackgroundColor(0xFFFFFBEB.toInt())
        binding.tvPenaltyImpactCallout.setTextColor(0xFF92400E.toInt())

        val targetType = complaint?.targetType ?: Complaint.TargetType.STORE
        val targetDesc = when (targetType) {
            Complaint.TargetType.CUSTOMER -> "khách hàng (Trừ 10 điểm uy tín)"
            Complaint.TargetType.DRIVER -> "tài xế (Ghi nhận biên bản cảnh cáo)"
            else -> "cửa hàng (Ghi nhận vi phạm 1 lần)"
        }

        when (binding.rgComplaintPenalty.checkedRadioButtonId) {
            R.id.rbPenaltyWarn -> {
                binding.tvPenaltyImpactCallout.text = "🟡 Mức 1 - Cảnh báo: Gửi biên bản cảnh cáo chính thức tới $targetDesc. Tài khoản vẫn được phép tiếp tục hoạt động."
            }
            R.id.rbPenaltyHide -> {
                val hideDesc = when (targetType) {
                    Complaint.TargetType.CUSTOMER -> "Tạm đình chỉ quyền đặt COD của khách (hạ điểm uy tín về 30)"
                    Complaint.TargetType.DRIVER -> "Tạm đình chỉ hoạt động nhận đơn và chuyển trạng thái Tắt nhận cuốc"
                    else -> "Tạm ẩn quán khỏi danh sách đề xuất và tạm đóng cửa gian hàng trên ứng dụng"
                }
                binding.tvPenaltyImpactCallout.text = "🟠 Mức 2 - Ẩn tạm thời: $hideDesc."
            }
            R.id.rbPenaltyLock -> {
                binding.tvPenaltyImpactCallout.text = "🔴 Mức 3 - Khóa tài khoản: Khóa tài khoản vĩnh viễn, cấm hoạt động trên toàn hệ thống Oishi Food."
            }
            else -> {
                binding.tvPenaltyImpactCallout.text = "⚪ Không áp dụng chế tài: Xác nhận giải quyết khiếu nại, nhắc nhở chung và không xử phạt đối tượng."
            }
        }
    }

    private fun submitVerdict(status: String, penaltyTier: String, isCounterPenalty: Boolean = false) {
        val note = binding.etAdminVerdictNote.text?.toString().orEmpty().trim()
        if (note.isBlank()) {
            Toast.makeText(this, "Vui lòng nhập lý do / phản hồi chính thức của Admin", Toast.LENGTH_SHORT).show()
            binding.etAdminVerdictNote.requestFocus()
            return
        }

        val complaint = currentComplaint ?: return

        binding.progressBarSubmitting.visibility = View.VISIBLE
        binding.btnSubmitVerdict.isEnabled = false
        binding.btnQuickReject.isEnabled = false

        lifecycleScope.launch {
            val result = firebaseRepository.resolveComplaint(
                complaintId = complaint.complaintId,
                status = status,
                penaltyApplied = penaltyTier,
                adminNote = note,
                storeId = complaint.storeId,
                targetType = complaint.targetType,
                targetId = complaint.targetId,
                customerId = complaint.customerId,
                driverId = complaint.driverId,
                orderId = complaint.orderId,
                isCounterPenalty = isCounterPenalty,
                counterPenaltyTier = penaltyTier
            )

            binding.progressBarSubmitting.visibility = View.GONE
            binding.btnSubmitVerdict.isEnabled = true
            binding.btnQuickReject.isEnabled = true

            if (result.isSuccess) {
                Toast.makeText(this@AdminComplaintDetailActivity, "Ban hành phán quyết thành công!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Có lỗi xảy ra khi cập nhật hồ sơ"
                Toast.makeText(this@AdminComplaintDetailActivity, "Lỗi: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showFullEvidenceDialog(evidenceImage: String) {
        val dialog = Dialog(this)
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#E6000000"))
        }

        val iv = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(24, 48, 24, 48)
        }
        ImageLoadUtils.loadEvidenceImage(iv, evidenceImage, cornerDp = 0)
        container.addView(iv)

        val btnClose = ImageView(this).apply {
            val size = (36 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                setMargins(24, 40, 24, 0)
            }
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.WHITE)
            setOnClickListener { dialog.dismiss() }
        }
        container.addView(btnClose)

        dialog.setContentView(container)
        dialog.window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        dialog.show()
    }

    private fun copyToClipboard(label: String, text: String) {
        if (text.isBlank() || text.contains("Chưa cập nhật") || text.contains("Không có")) {
            Toast.makeText(this, "Không có dữ liệu để sao chép", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Đã sao chép: $text", Toast.LENGTH_SHORT).show()
    }

    private fun dialPhoneNumber(phoneNumber: String) {
        if (phoneNumber.isBlank() || phoneNumber.contains("Chưa cập nhật") || phoneNumber.contains("Không có")) {
            Toast.makeText(this, "Số điện thoại không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở ứng dụng cuộc gọi", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_COMPLAINT_ID = "extra_complaint_id"

        fun newIntent(context: Context, complaintId: String): Intent {
            return Intent(context, AdminComplaintDetailActivity::class.java).apply {
                putExtra(EXTRA_COMPLAINT_ID, complaintId)
            }
        }
    }
}
