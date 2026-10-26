package com.chuyen_de_2.foodorder.admin.ui.monitor

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.chuyen_de_2.foodorder.admin.databinding.DialogEditOrderBinding
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EditOrderDialogFragment(
    private val order: Order,
    private val onSave: (Order) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogEditOrderBinding? = null
    private val binding get() = _binding!!

    private val statusOptions = listOf(
        Order.OrderStatus.PENDING,
        Order.OrderStatus.PREPARING,
        Order.OrderStatus.READY,
        Order.OrderStatus.ASSIGNED,
        Order.OrderStatus.DELIVERING,
        Order.OrderStatus.COMPLETED,
        Order.OrderStatus.BOOM,
        Order.OrderStatus.CANCELLED
    )

    private val paymentOptions = listOf(
        "COD",
        "MOMO",
        "VN_PAY",
        "ZALO_PAY",
        "WALLET"
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()
        populateCurrentData()
        setupListeners()
    }

    private fun setupSpinners() {
        val context = requireContext()

        val statusLabels = listOf(
            "🟡 Đang chờ xác nhận (PENDING)",
            "🟠 Đang chuẩn bị món (PREPARING)",
            "👨‍🍳 Quán làm xong (READY)",
            "🛵 Đã điều tài xế (ASSIGNED)",
            "🚚 Đang giao hàng (DELIVERING)",
            "🟢 Giao thành công (COMPLETED)",
            "⚠️ Khách boom hàng (BOOM)",
            "🔴 Đã hủy đơn (CANCELLED)"
        )
        val statusAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, statusLabels)
        binding.spnOrderStatus.adapter = statusAdapter

        val paymentLabels = listOf(
            "💵 Tiền mặt khi nhận (COD)",
            "🟣 Ví điện tử MoMo",
            "💳 Cổng VNPAY QR",
            "🔵 Ví ZaloPay",
            "👛 Ví số Oishi Food"
        )
        val paymentAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, paymentLabels)
        binding.spnPaymentMethod.adapter = paymentAdapter
    }

    private fun populateCurrentData() {
        binding.tvOrderIdSubtitle.text = "Mã đơn: ${order.orderId}"

        val currentStatus = order.orderStatus.ifBlank { order.status }.uppercase()
        val statusIdx = statusOptions.indexOfFirst { it.equals(currentStatus, ignoreCase = true) }.coerceAtLeast(0)
        binding.spnOrderStatus.setSelection(statusIdx)

        val currentPayment = order.paymentMethod.uppercase()
        val paymentIdx = paymentOptions.indexOfFirst { currentPayment.contains(it) }.coerceAtLeast(0)
        binding.spnPaymentMethod.setSelection(paymentIdx)

        binding.etOrderAddress.setText(order.address.ifBlank { order.deliveryAddress })
        binding.etOrderNote.setText(order.note)
        binding.etShippingFee.setText(order.shippingFee.toString())
        binding.etTotalPayment.setText(order.totalPayment.toString())
        binding.etShipperName.setText(order.shipperName)
        binding.etShipperPhone.setText(order.shipperPhone)
    }

    private fun setupListeners() {
        binding.btnCloseOrderDialog.setOnClickListener { dismiss() }
        binding.btnCancelEditOrder.setOnClickListener { dismiss() }

        binding.btnSaveEditOrder.setOnClickListener {
            val selectedStatus = statusOptions.getOrElse(binding.spnOrderStatus.selectedItemPosition) { Order.OrderStatus.PENDING }
            val selectedPayment = paymentOptions.getOrElse(binding.spnPaymentMethod.selectedItemPosition) { "COD" }
            val address = binding.etOrderAddress.text?.toString().orEmpty().trim()
            val note = binding.etOrderNote.text?.toString().orEmpty().trim()
            val shippingFee = binding.etShippingFee.text?.toString().orEmpty().toLongOrNull() ?: order.shippingFee
            val totalPayment = binding.etTotalPayment.text?.toString().orEmpty().toLongOrNull() ?: order.totalPayment
            val shipperName = binding.etShipperName.text?.toString().orEmpty().trim()
            val shipperPhone = binding.etShipperPhone.text?.toString().orEmpty().trim()

            val updatedOrder = order.copy(
                orderStatus = selectedStatus,
                status = selectedStatus,
                paymentMethod = selectedPayment,
                address = address,
                deliveryAddress = address,
                note = note,
                shippingFee = shippingFee,
                totalPayment = totalPayment,
                shipperName = shipperName,
                shipperPhone = shipperPhone
            )

            onSave(updatedOrder)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditOrderDialogFragment"
    }
}
