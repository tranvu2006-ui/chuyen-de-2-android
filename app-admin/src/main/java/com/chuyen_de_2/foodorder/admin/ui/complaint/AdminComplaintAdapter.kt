package com.chuyen_de_2.foodorder.admin.ui.complaint

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.ItemAdminComplaintBinding
import com.chuyen_de_2.foodorder.core.data.model.Complaint
import com.chuyen_de_2.foodorder.core.util.ImageLoadUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminComplaintAdapter(
    private val onComplaintClick: (Complaint) -> Unit
) : ListAdapter<Complaint, AdminComplaintAdapter.ComplaintViewHolder>(ComplaintDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComplaintViewHolder {
        val binding = ItemAdminComplaintBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ComplaintViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ComplaintViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ComplaintViewHolder(private val binding: ItemAdminComplaintBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(complaint: Complaint) {
            val context = itemView.context

            binding.tvComplaintId.text = "Khiếu nại #${complaint.complaintId.takeLast(8).uppercase()}"
            val timeStr = if (complaint.createdAt > 0) dateFormat.format(Date(complaint.createdAt)) else "Vừa xong"
            binding.tvComplaintTime.text = "Thời gian gửi: $timeStr"
            binding.tvOrderRef.text = "📦 Đơn: #${complaint.orderId.takeLast(6).uppercase()}"

            // 1. Phân loại sự cố (Complaint Type Badge)
            val typeTitle = when (complaint.complaintType) {
                Complaint.ComplaintType.ATTP -> "⚠️ Vệ sinh ATTP"
                Complaint.ComplaintType.WRONG_ITEM -> "📦 Sai / thiếu món"
                Complaint.ComplaintType.ATTITUDE -> "👤 Thái độ phục vụ"
                Complaint.ComplaintType.DELIVERY_DELAY -> "⏱️ Giao trễ"
                Complaint.ComplaintType.BOM_ORDER -> "💣 Khách bom hàng"
                Complaint.ComplaintType.FRAUD -> "💳 Sai tiền / Ưu đãi"
                else -> if (complaint.title.isNotBlank()) complaint.title else "Vấn đề khác"
            }
            binding.tvComplaintTypeBadge.text = typeTitle

            // 2. Mức độ ưu tiên (Priority Badge)
            when (complaint.priority.uppercase(Locale.getDefault())) {
                Complaint.Priority.HIGH -> {
                    binding.tvPriorityBadge.text = "🔴 ƯU TIÊN CAO"
                    binding.tvPriorityBadge.setBackgroundResource(R.drawable.bg_badge_red)
                    binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.danger))
                }
                Complaint.Priority.LOW -> {
                    binding.tvPriorityBadge.text = "⚪ THẤP"
                    binding.tvPriorityBadge.setBackgroundResource(R.drawable.bg_badge_blue)
                    binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_blue))
                }
                else -> {
                    binding.tvPriorityBadge.text = "🟠 TRUNG BÌNH"
                    binding.tvPriorityBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                    binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
                }
            }

            // 3. Thông tin 2 bên liên quan (Nguồn khiếu nại vs Đối tượng vi phạm)
            val senderRoleStr = when (complaint.senderType) {
                Complaint.SenderType.DRIVER -> "Tài xế"
                Complaint.SenderType.STORE -> "Quán ăn"
                else -> "Khách hàng"
            }
            val senderName = complaint.senderName.ifBlank { "Người dùng #${complaint.customerId.takeLast(5)}" }
            binding.tvSenderInfo.text = "$senderRoleStr: $senderName"
            binding.tvSenderContact.text = "SĐT: ${complaint.senderPhone.ifBlank { "Không có SĐT" }}"

            val targetRoleStr = when (complaint.targetType) {
                Complaint.TargetType.CUSTOMER -> "Khách hàng"
                Complaint.TargetType.DRIVER -> "Tài xế"
                else -> "Quán ăn"
            }
            val targetName = complaint.targetName.ifBlank {
                if (complaint.targetType == Complaint.TargetType.STORE) complaint.storeId.ifBlank { "Quán ăn" }
                else "Tài khoản #${complaint.targetId.takeLast(5)}"
            }
            binding.tvTargetInfo.text = "$targetRoleStr: $targetName"
            binding.tvTargetContact.text = "SĐT: ${complaint.targetPhone.ifBlank { "Chưa cập nhật" }}"

            // 4. Nội dung chi tiết
            binding.tvReason.text = "Nội dung phản ánh: ${complaint.reason.ifBlank { "Không có mô tả chi tiết" }}"

            // 5. Ảnh bằng chứng hiện trường
            if (complaint.evidenceImage.isNotBlank()) {
                binding.llEvidenceThumbnail.visibility = View.VISIBLE
                ImageLoadUtils.loadStoreImage(
                    imageView = binding.ivComplaintEvidenceThumb,
                    imageUrl = complaint.evidenceImage,
                    cornerDp = 8
                )
            } else {
                binding.llEvidenceThumbnail.visibility = View.GONE
            }

            // 6. Trạng thái hồ sơ
            val isResolved = complaint.status.equals(Complaint.ComplaintStatus.RESOLVED, ignoreCase = true)
            val isRejected = complaint.status.equals(Complaint.ComplaintStatus.REJECTED, ignoreCase = true)

            when {
                isResolved -> {
                    binding.tvComplaintStatusBadge.text = "ĐÃ GIẢI QUYẾT"
                    binding.tvComplaintStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
                    binding.tvComplaintStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_green))
                    binding.btnResolveAction.text = "🔍 Xem Biên Bản Phán Quyết"
                    binding.btnResolveAction.setTextColor(ContextCompat.getColor(context, R.color.brand_green))
                    binding.btnResolveAction.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_green_bg))
                }
                isRejected -> {
                    binding.tvComplaintStatusBadge.text = "ĐÃ BÁC BỎ"
                    binding.tvComplaintStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
                    binding.tvComplaintStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.danger))
                    binding.btnResolveAction.text = "🔍 Xem Lý Do Bác Bỏ"
                    binding.btnResolveAction.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    binding.btnResolveAction.setBackgroundColor(ContextCompat.getColor(context, R.color.background))
                }
                else -> {
                    binding.tvComplaintStatusBadge.text = "CHỜ XỬ LÝ"
                    binding.tvComplaintStatusBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                    binding.tvComplaintStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
                    binding.btnResolveAction.text = "⚖️ Thẩm Định & Phán Quyết Hồ Sơ"
                    binding.btnResolveAction.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    binding.btnResolveAction.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_light))
                }
            }

            // 7. Thông tin chế tài đã thi hành
            val hasPenalty = complaint.penaltyApplied.isNotBlank() && complaint.penaltyApplied != "NONE"
            val hasNote = complaint.adminNote.isNotBlank()
            if (isResolved || isRejected || hasPenalty || hasNote) {
                binding.llResolutionResult.visibility = View.VISIBLE
                val penaltyLabel = when (complaint.penaltyApplied) {
                    "WARN" -> "🟡 Mức 1: CẢNH CÁO VI PHẠM (WARN)"
                    "HIDE" -> "🟠 Mức 2: ẨN TẠM THỜI / ĐÌNH CHỈ (HIDE)"
                    "LOCK" -> "🔴 Mức 3: KHÓA TÀI KHOẢN VĨNH VIỄN (LOCK)"
                    else -> "⚪ Không áp dụng chế tài"
                }
                binding.tvAppliedPenalty.text = "Phán quyết: $penaltyLabel"
                binding.tvAdminNote.text = "Biên bản Admin: ${complaint.adminNote.ifBlank { "Đã thụ lý theo quy chế sàn" }}"
            } else {
                binding.llResolutionResult.visibility = View.GONE
            }

            // Click listener
            binding.root.setOnClickListener { onComplaintClick(complaint) }
            binding.btnResolveAction.setOnClickListener { onComplaintClick(complaint) }
            binding.llEvidenceThumbnail.setOnClickListener { onComplaintClick(complaint) }
        }
    }

    class ComplaintDiffCallback : DiffUtil.ItemCallback<Complaint>() {
        override fun areItemsTheSame(oldItem: Complaint, newItem: Complaint): Boolean =
            oldItem.complaintId == newItem.complaintId

        override fun areContentsTheSame(oldItem: Complaint, newItem: Complaint): Boolean =
            oldItem == newItem
    }
}
