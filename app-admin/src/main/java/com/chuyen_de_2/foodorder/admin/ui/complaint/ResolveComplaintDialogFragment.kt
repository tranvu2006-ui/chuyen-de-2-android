package com.chuyen_de_2.foodorder.admin.ui.complaint

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.DialogResolveComplaintBinding
import com.chuyen_de_2.foodorder.core.data.model.Complaint
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.util.ImageLoadUtils

class ResolveComplaintDialogFragment(
    private val complaint: Complaint,
    private val onResolve: (complaint: Complaint, status: String, penaltyTier: String, adminNote: String) -> Unit
) : DialogFragment() {

    private var _binding: DialogResolveComplaintBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogResolveComplaintBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateData()
        setupActions()
    }

    private fun populateData() {
        val shortCompId = if (complaint.complaintId.length > 8) complaint.complaintId.takeLast(8).uppercase() else complaint.complaintId
        val shortOrderCode = if (complaint.orderId.length > 6) complaint.orderId.takeLast(6).uppercase() else complaint.orderId
        binding.tvDialogComplaintRef.text = "#$shortCompId • Đơn: #ORD-$shortOrderCode"

        // Nguồn khiếu nại (Sender)
        val senderLabel = when (complaint.senderType) {
            Complaint.SenderType.DRIVER -> "Tài xế giao hàng"
            Complaint.SenderType.STORE -> "Quán ăn đối tác"
            else -> "Khách hàng"
        }
        val senderContact = if (complaint.senderPhone.isNotBlank()) " (${complaint.senderPhone})" else ""
        binding.tvDialogSenderInfo.text = "$senderLabel: ${complaint.senderName.ifBlank { complaint.customerId }}$senderContact"

        // Đối tượng bị khiếu nại (Target)
        val targetLabel = when (complaint.targetType) {
            Complaint.TargetType.CUSTOMER -> "Khách hàng"
            Complaint.TargetType.DRIVER -> "Tài xế shipper"
            else -> "Cửa hàng / Quán"
        }
        val targetContact = if (complaint.targetPhone.isNotBlank()) " (${complaint.targetPhone})" else ""
        binding.tvDialogTargetInfo.text = "$targetLabel: ${complaint.targetName.ifBlank { complaint.targetId.ifBlank { complaint.storeId } }}$targetContact"

        // Chủ đề & Phân loại vi phạm
        val typeTitle = when (complaint.complaintType) {
            Complaint.ComplaintType.ATTP -> "Vệ sinh An toàn Thực phẩm (ATTP)"
            Complaint.ComplaintType.WRONG_ITEM -> "Giao sai món / Thiếu món trong đơn"
            Complaint.ComplaintType.ATTITUDE -> "Thái độ phục vụ không phù hợp"
            Complaint.ComplaintType.DELIVERY_DELAY -> "Giao hàng quá trễ so với cam kết"
            Complaint.ComplaintType.BOM_ORDER -> "Khách bom đơn / Không liên lạc được"
            Complaint.ComplaintType.FRAUD -> "Gian lận / Tính sai tiền"
            else -> if (complaint.title.isNotBlank()) complaint.title else "Vấn đề sự cố khác"
        }
        binding.tvDialogComplaintType.text = "Chủ đề: $typeTitle • Ưu tiên: ${complaint.priority}"

        binding.tvDialogReasonContent.text = "Nội dung phản ánh: ${complaint.reason.ifBlank { "Không có mô tả chi tiết" }}"

        // Ảnh bằng chứng minh chứng
        if (complaint.evidenceImage.isNotBlank()) {
            binding.llDialogEvidence.visibility = View.VISIBLE
            ImageLoadUtils.loadStoreImage(
                imageView = binding.ivDialogEvidence,
                imageUrl = complaint.evidenceImage,
                cornerDp = 12
            )
            binding.cardEvidencePreview.setOnClickListener {
                showFullEvidenceDialog(complaint.evidenceImage)
            }
        } else {
            binding.llDialogEvidence.visibility = View.GONE
        }

        // Quyết định chấp thuận / Bác bỏ
        if (complaint.status.equals(Complaint.ComplaintStatus.REJECTED, ignoreCase = true)) {
            binding.rbResolveReject.isChecked = true
        } else {
            binding.rbResolveApprove.isChecked = true
        }

        // Mức phạt
        when (complaint.penaltyApplied) {
            Store.PenaltyTier.WARN -> binding.rbPenaltyWarn.isChecked = true
            Store.PenaltyTier.HIDE -> binding.rbPenaltyHide.isChecked = true
            Store.PenaltyTier.LOCK -> binding.rbPenaltyLock.isChecked = true
            else -> binding.rbPenaltyNone.isChecked = true
        }
        updatePenaltyCallout()

        binding.etAdminNote.setText(complaint.adminNote)
    }

    private fun updatePenaltyCallout() {
        if (binding.rbResolveReject.isChecked) {
            binding.tvPenaltyCallout.text = "❌ Bác bỏ khiếu nại: Hồ sơ không đủ căn cứ xử lý. Giữ nguyên trạng thái tài khoản đối tượng, không áp dụng chế tài."
            binding.tvPenaltyCallout.setBackgroundColor(0xFFFEF2F2.toInt())
            binding.tvPenaltyCallout.setTextColor(0xFF991B1B.toInt())
            return
        }

        binding.tvPenaltyCallout.setBackgroundColor(0xFFFFFBEB.toInt())
        binding.tvPenaltyCallout.setTextColor(0xFF92400E.toInt())

        val targetDesc = when (complaint.targetType) {
            Complaint.TargetType.CUSTOMER -> "khách hàng (Trừ 10 điểm uy tín)"
            Complaint.TargetType.DRIVER -> "tài xế (Ghi nhận biên bản cảnh cáo)"
            else -> "cửa hàng (Ghi nhận vi phạm 1 lần)"
        }

        when (binding.rgComplaintPenalty.checkedRadioButtonId) {
            R.id.rbPenaltyWarn -> {
                binding.tvPenaltyCallout.text = "🟡 Mức 1 - Cảnh báo: Gửi biên bản cảnh cáo chính thức tới $targetDesc. Tài khoản vẫn được phép tiếp tục hoạt động."
            }
            R.id.rbPenaltyHide -> {
                val hideDesc = when (complaint.targetType) {
                    Complaint.TargetType.CUSTOMER -> "Tạm đình chỉ quyền đặt COD của khách (hạ điểm uy tín về 30)"
                    Complaint.TargetType.DRIVER -> "Tạm đình chỉ hoạt động nhận đơn của tài xế"
                    else -> "Tạm ẩn quán khỏi danh sách đề xuất trên ứng dụng"
                }
                binding.tvPenaltyCallout.text = "🟠 Mức 2 - Ẩn tạm thời: $hideDesc."
            }
            R.id.rbPenaltyLock -> {
                binding.tvPenaltyCallout.text = "🔴 Mức 3 - Khóa tài khoản: Khóa tài khoản vĩnh viễn, cấm hoạt động trên toàn hệ thống Oishi Food."
            }
            else -> {
                binding.tvPenaltyCallout.text = "⚪ Không áp dụng chế tài: Xác nhận giải quyết khiếu nại, nhắc nhở chung và không xử phạt đối tượng."
            }
        }
    }

    private fun setupActions() {
        binding.btnCancelResolve.setOnClickListener {
            dismiss()
        }

        binding.rgComplaintDecision.setOnCheckedChangeListener { _, _ ->
            updatePenaltyCallout()
        }

        binding.rgComplaintPenalty.setOnCheckedChangeListener { _, _ ->
            updatePenaltyCallout()
        }

        binding.btnQuickReject.setOnClickListener {
            binding.rbResolveReject.isChecked = true
            binding.rbPenaltyNone.isChecked = true
            if (binding.etAdminNote.text.isNullOrBlank()) {
                binding.etAdminNote.setText("Bác bỏ khiếu nại do không đủ chứng cứ vi phạm hợp lệ.")
            }
            submitResolve(Complaint.ComplaintStatus.REJECTED, Store.PenaltyTier.NONE)
        }

        binding.btnSubmitResolve.setOnClickListener {
            val status = if (binding.rbResolveReject.isChecked) {
                Complaint.ComplaintStatus.REJECTED
            } else {
                Complaint.ComplaintStatus.RESOLVED
            }

            val penaltyTier = if (status == Complaint.ComplaintStatus.REJECTED) {
                Store.PenaltyTier.NONE
            } else {
                when (binding.rgComplaintPenalty.checkedRadioButtonId) {
                    R.id.rbPenaltyWarn -> Store.PenaltyTier.WARN
                    R.id.rbPenaltyHide -> Store.PenaltyTier.HIDE
                    R.id.rbPenaltyLock -> Store.PenaltyTier.LOCK
                    else -> Store.PenaltyTier.NONE
                }
            }

            submitResolve(status, penaltyTier)
        }
    }

    private fun submitResolve(status: String, penaltyTier: String) {
        val note = binding.etAdminNote.text?.toString().orEmpty().trim()
        if (note.isBlank()) {
            Toast.makeText(requireContext(), "Vui lòng nhập lý do / phản hồi chính thức của Admin", Toast.LENGTH_SHORT).show()
            binding.etAdminNote.requestFocus()
            return
        }

        onResolve(complaint, status, penaltyTier, note)
        dismiss()
    }

    private fun showFullEvidenceDialog(evidenceImage: String) {
        val dialog = Dialog(requireContext())
        val iv = ImageView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        ImageLoadUtils.loadStoreImage(iv, evidenceImage)
        dialog.setContentView(iv)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ResolveComplaintDialogFragment"
    }
}
