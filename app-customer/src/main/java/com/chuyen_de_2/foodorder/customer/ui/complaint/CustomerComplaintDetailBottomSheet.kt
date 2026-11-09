package com.chuyen_de_2.foodorder.customer.ui.complaint

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.chuyen_de_2.foodorder.core.data.model.Complaint
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.util.ImageLoadUtils
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.BottomSheetCustomerComplaintDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomerComplaintDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCustomerComplaintDetailBinding? = null
    private val binding get() = _binding!!

    private var complaint: Complaint? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = arguments?.getString(ARG_COMPLAINT_JSON)
        if (!json.isNullOrBlank()) {
            complaint = try {
                Gson().fromJson(json, Complaint::class.java)
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCustomerComplaintDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnDone.setOnClickListener { dismiss() }

        val c = complaint ?: return
        bindComplaintData(c)
    }

    private fun bindComplaintData(c: Complaint) {
        val shortCode = if (c.complaintId.isNotBlank()) {
            "#" + c.complaintId.takeLast(6).uppercase()
        } else {
            "#KN-${c.createdAt % 100000}"
        }
        binding.tvDetailTitle.text = "Hồ Sơ Khiếu Nại $shortCode"

        val timeStr = if (c.createdAt > 0) dateFormat.format(Date(c.createdAt)) else "Vừa gửi"
        binding.tvDetailTime.text = "Gửi lúc: $timeStr"

        val orderCode = if (c.orderId.isNotBlank()) "#ORD-${c.orderId.takeLast(6).uppercase()}" else "Không rõ"
        binding.tvDetailOrderId.text = orderCode

        val isDriver = c.targetType == Complaint.TargetType.DRIVER
        val targetPrefix = if (isDriver) "Tài xế" else "Quán"
        val targetName = c.targetName.ifBlank { if (isDriver) "Tài xế giao đơn" else "Quán ăn đối tác" }
        binding.tvDetailTarget.text = "$targetPrefix: $targetName"

        val categoryTitle = when (c.complaintType) {
            Complaint.ComplaintType.ATTP -> "🍲 Vệ sinh ATTP"
            Complaint.ComplaintType.WRONG_ITEM -> "🍱 Giao sai / thiếu món"
            Complaint.ComplaintType.ATTITUDE -> "🛵 Thái độ phục vụ"
            Complaint.ComplaintType.DELIVERY_DELAY -> "⏱️ Giao hàng trễ"
            Complaint.ComplaintType.FRAUD -> "💸 Gian lận / Sai tiền"
            Complaint.ComplaintType.BOM_ORDER -> "📦 Sự cố giao nhận"
            else -> "❓ Vấn đề khác"
        }
        binding.tvDetailCategory.text = categoryTitle
        binding.tvDetailReason.text = c.reason.ifBlank { "Không có mô tả chi tiết." }

        // Ảnh bằng chứng
        if (c.evidenceImage.isNotBlank()) {
            binding.layoutDetailEvidence.visibility = View.VISIBLE
            ImageLoadUtils.loadEvidenceImage(
                imageView = binding.ivDetailEvidence,
                imageUrl = c.evidenceImage,
                cornerDp = 10
            )
            binding.cardEvidenceZoom.setOnClickListener {
                showFullEvidenceDialog(c.evidenceImage)
            }
        } else {
            binding.layoutDetailEvidence.visibility = View.GONE
        }

        // Trạng thái tổng thể
        val isCounterPenalized = c.isComplainantPenalized
        val isResolved = c.status.equals(Complaint.ComplaintStatus.RESOLVED, ignoreCase = true)
        val isRejected = c.status.equals(Complaint.ComplaintStatus.REJECTED, ignoreCase = true)

        when {
            // 1. Phạt ngược khách hàng
            isCounterPenalized -> {
                binding.cardStatusBanner.setCardBackgroundColor(Color.parseColor("#FEF2F2"))
                binding.cardStatusBanner.strokeColor = Color.parseColor("#FCA5A5")
                binding.ivStatusBannerIcon.setImageResource(R.drawable.ic_shield_alert)
                binding.ivStatusBannerIcon.setColorFilter(Color.parseColor("#DC2626"))
                binding.tvStatusBannerTitle.text = "ĐÃ BÁC BỎ & PHẠT NGƯỢC NGƯỜI KHIẾU NẠI"
                binding.tvStatusBannerTitle.setTextColor(Color.parseColor("#991B1B"))
                binding.tvStatusBannerDesc.text = "Ban Quản Trị đã phát hiện hành vi báo cáo sai sự thật hoặc vu khống vi phạm quy chế sàn."
                binding.tvStatusBannerDesc.setTextColor(Color.parseColor("#B91C1C"))

                binding.cardCounterPenaltyAlert.visibility = View.VISIBLE
                val scoreText = if (c.trustScoreDeducted > 0) {
                    "-${c.trustScoreDeducted} điểm uy tín"
                } else {
                    when (c.counterPenaltyApplied) {
                        Store.PenaltyTier.WARN -> "-10 điểm uy tín"
                        Store.PenaltyTier.HIDE -> "-30 điểm uy tín & tước quyền COD"
                        Store.PenaltyTier.LOCK -> "-50 điểm uy tín & KHÓA TÀI KHOẢN"
                        else -> "Cảnh cáo vi phạm"
                    }
                }
                binding.tvCounterPenaltyVerdict.text = "• Mức chế tài: $scoreText\n• Hậu quả: Điểm uy tín của bạn đã bị khấu trừ. Nếu tiếp tục vi phạm, tài khoản sẽ bị khóa vĩnh viễn."
                binding.tvCounterPenaltyAdminReason.text = "Căn cứ phán quyết từ Admin: ${c.counterPenaltyReason.ifBlank { c.adminNote }.ifBlank { "Không đủ chứng cứ xác thực." }}"

                binding.layoutAdminResolution.visibility = View.GONE
            }

            // 2. Khiếu nại được giải quyết (phạt quán/tài xế)
            isResolved -> {
                binding.cardStatusBanner.setCardBackgroundColor(Color.parseColor("#ECFDF5"))
                binding.cardStatusBanner.strokeColor = Color.parseColor("#A7F3D0")
                binding.ivStatusBannerIcon.setImageResource(R.drawable.ic_badge_check)
                binding.ivStatusBannerIcon.setColorFilter(Color.parseColor("#059669"))
                binding.tvStatusBannerTitle.text = "KHIẾU NẠI HỢP LỆ — ĐÃ BAN HÀNH XỬ PHẠT"
                binding.tvStatusBannerTitle.setTextColor(Color.parseColor("#065F46"))
                binding.tvStatusBannerDesc.text = "Ban Quản Trị đã tiến hành xác minh và áp dụng chế tài nghiêm khắc đối với bên vi phạm."
                binding.tvStatusBannerDesc.setTextColor(Color.parseColor("#047857"))

                binding.cardCounterPenaltyAlert.visibility = View.GONE
                binding.layoutAdminResolution.visibility = View.VISIBLE

                val penaltyText = when (c.penaltyApplied) {
                    Store.PenaltyTier.WARN -> "Cảnh cáo đối tượng vi phạm"
                    Store.PenaltyTier.HIDE -> "Tạm ẩn gian hàng / Đình chỉ 3 ngày"
                    Store.PenaltyTier.LOCK -> "Khóa tài khoản bên vi phạm vĩnh viễn"
                    else -> "Đã giải quyết thỏa đáng"
                }
                binding.tvAdminResolutionPenalty.text = "Chế tài: $penaltyText"
                binding.tvAdminResolutionNote.text = "Biên bản giải quyết: ${c.adminNote.ifBlank { "Hồ sơ đã được thụ lý hoàn tất." }}"
            }

            // 3. Bác bỏ khiếu nại (bình thường, không phạt)
            isRejected -> {
                binding.cardStatusBanner.setCardBackgroundColor(Color.parseColor("#F1F5F9"))
                binding.cardStatusBanner.strokeColor = Color.parseColor("#CBD5E1")
                binding.ivStatusBannerIcon.setImageResource(R.drawable.ic_close)
                binding.ivStatusBannerIcon.setColorFilter(Color.parseColor("#64748B"))
                binding.tvStatusBannerTitle.text = "ĐÃ ĐÓNG HỒ SƠ (BÁC BỎ)"
                binding.tvStatusBannerTitle.setTextColor(Color.parseColor("#334155"))
                binding.tvStatusBannerDesc.text = "Không đủ chứng cứ xác thực vi phạm từ bên bị khiếu nại. Hồ sơ đã được đóng lại."
                binding.tvStatusBannerDesc.setTextColor(Color.parseColor("#475569"))

                binding.cardCounterPenaltyAlert.visibility = View.GONE
                binding.layoutAdminResolution.visibility = View.VISIBLE
                binding.tvAdminResolutionPenalty.text = "Kết quả: Bác bỏ khiếu nại"
                binding.tvAdminResolutionNote.text = "Lý do: ${c.adminNote.ifBlank { "Không phát hiện vi phạm quy chế." }}"
            }

            // 4. Chờ thẩm định
            else -> {
                binding.cardStatusBanner.setCardBackgroundColor(Color.parseColor("#FFFBEB"))
                binding.cardStatusBanner.strokeColor = Color.parseColor("#FDE68A")
                binding.ivStatusBannerIcon.setImageResource(R.drawable.ic_clock)
                binding.ivStatusBannerIcon.setColorFilter(Color.parseColor("#D97706"))
                binding.tvStatusBannerTitle.text = "ĐANG THẨM ĐỊNH HỒ SƠ"
                binding.tvStatusBannerTitle.setTextColor(Color.parseColor("#92400E"))
                binding.tvStatusBannerDesc.text = "Ban Quản Trị đang liên hệ các bên để xác minh sự việc trong vòng 2 giờ."
                binding.tvStatusBannerDesc.setTextColor(Color.parseColor("#B45309"))

                binding.cardCounterPenaltyAlert.visibility = View.GONE
                binding.layoutAdminResolution.visibility = View.GONE
            }
        }
    }

    private fun showFullEvidenceDialog(evidenceImage: String) {
        val dialog = Dialog(requireContext())
        val container = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#E6000000"))
        }

        val iv = ImageView(requireContext()).apply {
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

        val btnClose = ImageView(requireContext()).apply {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_COMPLAINT_JSON = "arg_complaint_json"

        fun newInstance(complaint: Complaint): CustomerComplaintDetailBottomSheet {
            return CustomerComplaintDetailBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_COMPLAINT_JSON, Gson().toJson(complaint))
                }
            }
        }
    }
}
