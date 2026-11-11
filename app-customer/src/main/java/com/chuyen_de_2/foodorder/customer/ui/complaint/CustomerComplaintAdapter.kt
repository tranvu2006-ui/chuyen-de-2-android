package com.chuyen_de_2.foodorder.customer.ui.complaint

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Complaint
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.util.ImageLoadUtils
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ItemCustomerComplaintBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomerComplaintAdapter(
    private val onItemClick: (Complaint) -> Unit
) : ListAdapter<Complaint, CustomerComplaintAdapter.ComplaintViewHolder>(ComplaintDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComplaintViewHolder {
        val binding = ItemCustomerComplaintBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ComplaintViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ComplaintViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ComplaintViewHolder(
        private val binding: ItemCustomerComplaintBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(complaint: Complaint) {
            val context = itemView.context

            // 1. Mã hồ sơ & Thời gian
            val shortCode = if (complaint.complaintId.isNotBlank()) {
                "#" + complaint.complaintId.takeLast(6).uppercase()
            } else {
                "#KN-${complaint.createdAt % 100000}"
            }
            binding.tvComplaintCode.text = shortCode

            val timeStr = if (complaint.createdAt > 0) {
                dateFormat.format(Date(complaint.createdAt))
            } else {
                "Vừa gửi"
            }
            binding.tvComplaintTime.text = timeStr

            // 2. Đối tượng bị khiếu nại & Mã đơn hàng
            val isDriver = complaint.targetType == Complaint.TargetType.DRIVER
            binding.ivTargetTypeIcon.setImageResource(if (isDriver) R.drawable.ic_bike else R.drawable.ic_store)

            val targetPrefix = if (isDriver) "Tài xế" else "Quán"
            val targetName = complaint.targetName.ifBlank {
                if (isDriver) "Tài xế giao đơn" else "Quán ăn đối tác"
            }
            binding.tvTargetName.text = "$targetPrefix: $targetName"

            val orderCode = if (complaint.orderId.isNotBlank()) {
                com.chuyen_de_2.foodorder.core.util.QRCodeUtils.formatOrderCode(complaint.orderId)
            } else {
                "Không rõ đơn"
            }
            binding.tvOrderId.text = "Đơn: $orderCode"

            // 3. Phân loại & Nội dung
            val categoryTitle = when (complaint.complaintType) {
                Complaint.ComplaintType.ATTP -> "🍲 Vệ sinh ATTP"
                Complaint.ComplaintType.WRONG_ITEM -> "🍱 Giao sai / thiếu món"
                Complaint.ComplaintType.ATTITUDE -> "🛵 Thái độ phục vụ"
                Complaint.ComplaintType.DELIVERY_DELAY -> "⏱️ Giao hàng trễ"
                Complaint.ComplaintType.FRAUD -> "💸 Gian lận / Sai tiền"
                Complaint.ComplaintType.BOM_ORDER -> "📦 Sự cố giao nhận"
                else -> "❓ Vấn đề khác"
            }
            binding.tvComplaintCategoryBadge.text = categoryTitle
            binding.tvReasonContent.text = complaint.reason.ifBlank { "Không có mô tả chi tiết." }

            // 4. Ảnh bằng chứng thu nhỏ
            if (complaint.evidenceImage.isNotBlank()) {
                binding.layoutEvidenceThumb.visibility = View.VISIBLE
                ImageLoadUtils.loadEvidenceImage(
                    imageView = binding.ivEvidenceThumb,
                    imageUrl = complaint.evidenceImage,
                    cornerDp = 6
                )
            } else {
                binding.layoutEvidenceThumb.visibility = View.GONE
            }

            // 5. Trạng thái & Phán quyết (Đặc biệt xử lý PHẠT NGƯỢC người dùng)
            val isCounterPenalized = complaint.isComplainantPenalized
            val isResolved = complaint.status.equals(Complaint.ComplaintStatus.RESOLVED, ignoreCase = true)
            val isRejected = complaint.status.equals(Complaint.ComplaintStatus.REJECTED, ignoreCase = true)

            when {
                // TRƯỜNG HỢP: KHÁCH HÀNG BỊ ADMIN PHẠT NGƯỢC
                isCounterPenalized -> {
                    binding.tvStatusBadge.text = "BỊ PHẠT NGƯỢC"
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#DC2626"))

                    binding.layoutCounterPenaltyBox.visibility = View.VISIBLE
                    binding.layoutResolvedBox.visibility = View.GONE

                    val scoreText = if (complaint.trustScoreDeducted > 0) {
                        "-${complaint.trustScoreDeducted} điểm uy tín"
                    } else {
                        when (complaint.counterPenaltyApplied) {
                            Store.PenaltyTier.WARN -> "-10 điểm uy tín"
                            Store.PenaltyTier.HIDE -> "-30 điểm uy tín & cấm COD"
                            Store.PenaltyTier.LOCK -> "-50 điểm uy tín & KHÓA TÀI KHOẢN"
                            else -> "Cảnh cáo vi phạm"
                        }
                    }

                    binding.tvCounterPenaltyTitle.text = "🚨 BỊ PHẠT NGƯỢC: $scoreText"
                    val reason = complaint.counterPenaltyReason.ifBlank { complaint.adminNote }.ifBlank {
                        "Admin xác minh báo cáo sai sự thật / vu khống."
                    }
                    binding.tvCounterPenaltyDetails.text = "Căn cứ xử lý: $reason"
                }

                // TRƯỜNG HỢP: KHIẾU NẠI HỢP LỆ VÀ ĐÃ XỬ PHẠT ĐỐI TƯỢNG
                isResolved -> {
                    binding.tvStatusBadge.text = "ĐÃ GIẢI QUYẾT"
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#059669"))

                    binding.layoutCounterPenaltyBox.visibility = View.GONE
                    binding.layoutResolvedBox.visibility = View.VISIBLE

                    val penaltyName = when (complaint.penaltyApplied) {
                        Store.PenaltyTier.WARN -> "Cảnh cáo đối tượng vi phạm"
                        Store.PenaltyTier.HIDE -> "Đình chỉ hoạt động / Cấm COD"
                        Store.PenaltyTier.LOCK -> "Khóa tài khoản bên vi phạm"
                        else -> "Đã giải quyết theo quy định"
                    }
                    binding.tvResolvedTitle.text = "✓ $penaltyName"
                    binding.tvResolvedDetails.text = "Biên bản Admin: ${complaint.adminNote.ifBlank { "Khiếu nại hợp lệ, đã giải quyết xong." }}"
                }

                // TRƯỜNG HỢP: BÁC BỎ BÌNH THƯỜNG (KHÔNG PHẠT)
                isRejected -> {
                    binding.tvStatusBadge.text = "ĐÃ BÁC BỎ"
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_gray)
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#64748B"))

                    binding.layoutCounterPenaltyBox.visibility = View.GONE
                    binding.layoutResolvedBox.visibility = View.GONE
                }

                // TRƯỜNG HỢP: CHỜ THẨM ĐỊNH (PENDING)
                else -> {
                    binding.tvStatusBadge.text = "CHỜ THẨM ĐỊNH"
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#D97706"))

                    binding.layoutCounterPenaltyBox.visibility = View.GONE
                    binding.layoutResolvedBox.visibility = View.GONE
                }
            }

            itemView.setOnClickListener {
                onItemClick(complaint)
            }
        }
    }

    private class ComplaintDiffCallback : DiffUtil.ItemCallback<Complaint>() {
        override fun areItemsTheSame(oldItem: Complaint, newItem: Complaint): Boolean {
            return oldItem.complaintId == newItem.complaintId
        }

        override fun areContentsTheSame(oldItem: Complaint, newItem: Complaint): Boolean {
            return oldItem == newItem
        }
    }
}
