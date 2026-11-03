package com.chuyen_de_2.foodorder.admin.ui.complaint

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
            binding.tvOrderRef.text = "Đơn: #${complaint.orderId.takeLast(6).uppercase()}"
            binding.tvStoreRef.text = "Quán: ${complaint.targetName.ifBlank { complaint.storeId.ifBlank { "N/A" } }}"

            // Phân loại khiếu nại (Complaint Type Badge)
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

            // Tóm tắt các bên
            val senderLabel = when (complaint.senderType) {
                Complaint.SenderType.DRIVER -> "Tài xế (${complaint.senderName.ifBlank { "Shipper" }})"
                Complaint.SenderType.STORE -> "Quán ăn (${complaint.senderName.ifBlank { complaint.storeId }})"
                else -> "Khách (${complaint.senderName.ifBlank { "Khách hàng" }})"
            }
            val targetLabel = when (complaint.targetType) {
                Complaint.TargetType.CUSTOMER -> "Khách (${complaint.targetName.ifBlank { complaint.customerId }})"
                Complaint.TargetType.DRIVER -> "Tài xế (${complaint.targetName.ifBlank { complaint.driverId }})"
                else -> "Quán (${complaint.targetName.ifBlank { complaint.storeId }})"
            }
            binding.tvPartiesSummary.text = "Nguồn: $senderLabel ➔ Đối tượng: $targetLabel"

            binding.tvReason.text = "Nội dung: ${complaint.reason.ifBlank { "Không có mô tả chi tiết" }}"

            // Ảnh bằng chứng (Evidence Image Thumbnail)
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

            // Status Badge
            when (complaint.status.uppercase(Locale.getDefault())) {
                Complaint.ComplaintStatus.RESOLVED -> {
                    binding.tvComplaintStatusBadge.text = "ĐÃ GIẢI QUYẾT"
                    binding.tvComplaintStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
                    binding.tvComplaintStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_green))
                }
                Complaint.ComplaintStatus.REJECTED -> {
                    binding.tvComplaintStatusBadge.text = "ĐÃ BÁC BỎ"
                    binding.tvComplaintStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
                    binding.tvComplaintStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.danger))
                }
                else -> {
                    binding.tvComplaintStatusBadge.text = "CHỜ XỬ LÝ"
                    binding.tvComplaintStatusBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                    binding.tvComplaintStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
                }
            }

            // Resolution info
            val hasPenalty = complaint.penaltyApplied.isNotBlank() && complaint.penaltyApplied != "NONE"
            val hasNote = complaint.adminNote.isNotBlank()
            if (hasPenalty || hasNote) {
                binding.llResolutionResult.visibility = View.VISIBLE
                val penaltyLabel = when (complaint.penaltyApplied) {
                    "WARN" -> "🟡 Mức 1: CẢNH CÁO VI PHẠM (WARN)"
                    "HIDE" -> "🟠 Mức 2: ẨN TẠM THỜI / ĐÌNH CHỈ (HIDE)"
                    "LOCK" -> "🔴 Mức 3: KHÓA TÀI KHOẢN VĨNH VIỄN (LOCK)"
                    else -> "⚪ Không áp dụng chế tài"
                }
                binding.tvAppliedPenalty.text = "Phán quyết: $penaltyLabel"
                binding.tvAdminNote.text = "Phản hồi admin: ${complaint.adminNote.ifBlank { "Không có ghi chú" }}"
            } else {
                binding.llResolutionResult.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onComplaintClick(complaint)
            }
        }
    }

    class ComplaintDiffCallback : DiffUtil.ItemCallback<Complaint>() {
        override fun areItemsTheSame(oldItem: Complaint, newItem: Complaint): Boolean =
            oldItem.complaintId == newItem.complaintId

        override fun areContentsTheSame(oldItem: Complaint, newItem: Complaint): Boolean =
            oldItem == newItem
    }
}
