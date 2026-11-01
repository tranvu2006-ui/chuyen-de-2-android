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

            binding.tvComplaintId.text = "Khiếu nại #${complaint.complaintId.takeLast(8)}"
            val timeStr = if (complaint.createdAt > 0) dateFormat.format(Date(complaint.createdAt)) else "Vừa xong"
            binding.tvComplaintTime.text = "Thời gian gửi: $timeStr"
            binding.tvOrderRef.text = "Đơn: ${complaint.orderId.ifBlank { "N/A" }}"
            binding.tvStoreRef.text = "Quán: ${complaint.storeId.ifBlank { "N/A" }}"
            binding.tvReason.text = "Nội dung: ${complaint.reason.ifBlank { "Không có mô tả chi tiết" }}"

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
                binding.tvAppliedPenalty.text = "Mức phạt áp dụng: ${if (hasPenalty) complaint.penaltyApplied else "Không phạt"}"
                binding.tvAdminNote.text = "Ghi chú admin: ${complaint.adminNote.ifBlank { "Không có" }}"
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
