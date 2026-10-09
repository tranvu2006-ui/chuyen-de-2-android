package com.chuyen_de_2.foodorder.customer.ui.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chuyen_de_2.foodorder.customer.databinding.DialogIncidentProofBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class IncidentProofBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogIncidentProofBinding? = null
    private val binding get() = _binding!!

    private var orderId: String = ""
    private var driverName: String = ""
    private var reason: String = ""
    private var notes: String = ""

    companion object {
        private const val ARG_ORDER_ID = "order_id"
        private const val ARG_DRIVER_NAME = "driver_name"
        private const val ARG_REASON = "reason"
        private const val ARG_NOTES = "notes"

        fun newInstance(orderId: String, driverName: String, reason: String, notes: String): IncidentProofBottomSheetDialogFragment {
            return IncidentProofBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ORDER_ID, orderId)
                    putString(ARG_DRIVER_NAME, driverName)
                    putString(ARG_REASON, reason)
                    putString(ARG_NOTES, notes)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orderId = arguments?.getString(ARG_ORDER_ID) ?: ""
        driverName = arguments?.getString(ARG_DRIVER_NAME) ?: "Tài xế Oishi Food"
        reason = arguments?.getString(ARG_REASON) ?: "Khách không nghe máy sau 15 phút đếm ngược (Bom hàng)"
        notes = arguments?.getString(ARG_NOTES) ?: "Tài xế đã có mặt tại điểm giao nhưng không liên lạc được."
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogIncidentProofBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvModalOrderCode.text = "#ORD_OISHI_${orderId.takeLast(6).uppercase()} • Ghi nhận hệ thống"
        binding.tvModalReason.text = reason
        binding.tvModalDriverInfo.text = driverName
        binding.tvModalDriverNotes.text = "\"$notes\""

        binding.btnModalClose.setOnClickListener { dismiss() }
        binding.btnModalDismiss.setOnClickListener { dismiss() }
        binding.btnModalTakeAction.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
