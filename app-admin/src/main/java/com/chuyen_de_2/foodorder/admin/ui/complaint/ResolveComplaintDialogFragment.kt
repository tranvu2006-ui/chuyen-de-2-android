package com.chuyen_de_2.foodorder.admin.ui.complaint

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.DialogResolveComplaintBinding
import com.chuyen_de_2.foodorder.core.data.model.Complaint
import com.chuyen_de_2.foodorder.core.data.model.Store

class ResolveComplaintDialogFragment(
    private val complaint: Complaint,
    private val onResolve: (complaintId: String, status: String, penaltyTier: String, adminNote: String, storeId: String) -> Unit
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
        binding.tvDialogComplaintRef.text = "Mã đơn: ${complaint.orderId} • Mã quán: ${complaint.storeId}"
        binding.tvDialogReasonContent.text = "Nội dung phản ánh: ${complaint.reason.ifBlank { "Không có nội dung" }}"

        if (complaint.status.equals(Complaint.ComplaintStatus.REJECTED, ignoreCase = true)) {
            binding.rbResolveReject.isChecked = true
        } else {
            binding.rbResolveApprove.isChecked = true
        }

        when (complaint.penaltyApplied) {
            Store.PenaltyTier.WARN -> binding.rbPenaltyWarn.isChecked = true
            Store.PenaltyTier.HIDE -> binding.rbPenaltyHide.isChecked = true
            Store.PenaltyTier.LOCK -> binding.rbPenaltyLock.isChecked = true
            else -> binding.rbPenaltyNone.isChecked = true
        }

        binding.etAdminNote.setText(complaint.adminNote)
    }

    private fun setupActions() {
        binding.btnCancelResolve.setOnClickListener {
            dismiss()
        }

        binding.btnSubmitResolve.setOnClickListener {
            val status = if (binding.rbResolveReject.isChecked) {
                Complaint.ComplaintStatus.REJECTED
            } else {
                Complaint.ComplaintStatus.RESOLVED
            }

            val penaltyTier = when (binding.rgComplaintPenalty.checkedRadioButtonId) {
                R.id.rbPenaltyWarn -> Store.PenaltyTier.WARN
                R.id.rbPenaltyHide -> Store.PenaltyTier.HIDE
                R.id.rbPenaltyLock -> Store.PenaltyTier.LOCK
                else -> Store.PenaltyTier.NONE
            }

            val adminNote = binding.etAdminNote.text?.toString().orEmpty().trim()

            onResolve(
                complaint.complaintId,
                status,
                penaltyTier,
                adminNote,
                complaint.storeId
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ResolveComplaintDialogFragment"
    }
}
