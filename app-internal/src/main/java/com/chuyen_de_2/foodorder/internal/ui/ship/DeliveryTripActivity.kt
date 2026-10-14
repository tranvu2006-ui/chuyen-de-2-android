package com.chuyen_de_2.foodorder.internal.ui.ship

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.core.util.GoogleMapUtils
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.ActivityDeliveryTripBinding
import com.chuyen_de_2.foodorder.internal.databinding.DialogCancelOrderReasonBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DeliveryTripActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeliveryTripBinding

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private var currentOrderId: String = ""
    private var currentOrder: Order? = null
    private var currentStep = 1 // 1: Đến quán, 2: Lấy hàng, 3: Đến khách, 4: Quét QR xong

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"

        fun start(context: Context, orderId: String) {
            val intent = Intent(context, DeliveryTripActivity::class.java).apply {
                putExtra(EXTRA_ORDER_ID, orderId)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeliveryTripBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentOrderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: ""
        if (currentOrderId.isBlank()) {
            Toast.makeText(this, "Không tìm thấy mã đơn hàng", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupListeners()
        observeOrderDetails()
        updateStepUI(1)
    }

    private fun observeOrderDetails() {
        lifecycleScope.launch {
            firebaseRepository.observeOrder(currentOrderId).collectLatest { order ->
                if (order != null) {
                    currentOrder = order
                    bindOrderData(order)
                }
            }
        }
    }

    private fun bindOrderData(order: Order) {
        val shortCode = if (order.orderId.length > 5) order.orderId.takeLast(6).uppercase() else order.orderId
        binding.tvTripOrderCode.text = "#ORD_OISHI_$shortCode"

        val storeName = order.shopName.ifBlank { "Trà Sữa Ngô Gia (CN Võ Văn Ngân, Thủ Đức)" }
        binding.tvStep1StoreName.text = storeName

        val dishesText = if (order.items.isNotEmpty()) {
            "📦 Món cần lấy:\n" + order.items.values.joinToString("\n") { "• ${it.quantity}x ${it.dishName}" }
        } else {
            "📦 Món cần lấy:\n• 1x Trà Ô Long (Size M)\n• 1x Trà Ngô Gia (Size M)"
        }
        binding.tvStep1DishesList.text = dishesText

        val cusName = order.customerName.ifBlank { "An Nguyễn" }
        binding.tvStep3CustomerName.text = cusName

        val dest = order.address.ifBlank { "104 Hai Bà Trưng, P. Đa Kao, Q.1" }
        binding.tvStep3DeliveryAddress.text = "📍 Địa chỉ: $dest"

        val fee = if (order.shippingFee > 0) order.shippingFee else 25000L
        val totalPayment = if (order.totalPayment > 0) order.totalPayment else (order.total + fee)

        val isCod = order.paymentMethod.equals("COD", ignoreCase = true)
        if (isCod) {
            binding.tvStep3PaymentMethodBadge.text = "💵 Thu tiền mặt COD: ${totalPayment.toVndCurrency()}"
            binding.tvStep4CodCollection.text = "Thu Tiền Mặt COD: ${totalPayment.toVndCurrency()}"
        } else {
            binding.tvStep3PaymentMethodBadge.text = "✓ ĐÃ THANH TOÁN (MOMO/VNPAY) - KHÔNG THU TIỀN"
            binding.tvStep4CodCollection.text = "ĐÃ THANH TOÁN TRƯỚC (COD: 0 đ)"
        }

        binding.tvStep4Summary.text = "Khách hàng: $cusName • 0938 112 233\nĐịa chỉ: $dest"
    }

    private fun setupListeners() {
        binding.btnTripBack.setOnClickListener {
            finish()
        }

        binding.btnTripCancel.setOnClickListener {
            showCancelOrderDialog()
        }

        // Bước 1: Đến quán
        binding.btnOpenNavStep1.setOnClickListener {
            val storeAddress = "120 Võ Văn Ngân, Linh Chiểu, Thủ Đức, TP.HCM"
            GoogleMapUtils.openGoogleMapsSearch(this, storeAddress)
        }

        binding.btnCallStoreStep1.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0908123456"))
            startActivity(intent)
        }

        // Bước 2: Checklist
        binding.btnCheckAllDishes.setOnClickListener {
            binding.cbDish1.isChecked = true
            binding.cbDish2.isChecked = true
            binding.cbSealPackage.isChecked = true
            Toast.makeText(this, "Đã tích chọn tất cả món!", Toast.LENGTH_SHORT).show()
        }

        // Bước 3: Đến khách
        binding.btnOpenNavStep3.setOnClickListener {
            val dest = currentOrder?.address?.ifBlank { "Quận 1, TP.HCM" } ?: "Quận 1, TP.HCM"
            GoogleMapUtils.openGoogleMapsSearch(this, dest)
        }

        binding.btnCallCustomerStep3.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0938112233"))
            startActivity(intent)
        }

        binding.btnMessageCustomerStep3.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:0938112233"))
            startActivity(intent)
        }

        // Bước 4: Quét QR Hoàn tất
        binding.btnSimulateScan.setOnClickListener {
            Toast.makeText(this, "✓ Quét mã QR thành công! Đã khớp mã giao hàng", Toast.LENGTH_SHORT).show()
        }

        // Nút hành động chính dưới đáy màn hình
        binding.btnTripMainAction.setOnClickListener {
            when (currentStep) {
                1 -> {
                    // Đã đến quán -> sang bước 2 (Checklist món)
                    updateStepUI(2)
                }
                2 -> {
                    // Xác nhận đã lấy món -> cập nhật DELIVERING và sang bước 3 (Đến khách)
                    lifecycleScope.launch {
                        firebaseRepository.updateOrderStatus(currentOrderId, Order.Status.DELIVERING)
                        updateStepUI(3)
                    }
                }
                3 -> {
                    // Đã đến khách -> sang bước 4 (Quét QR đối soát)
                    updateStepUI(4)
                }
                4 -> {
                    // Hoàn tất đơn hàng
                    completeTripOrder()
                }
            }
        }
    }

    private fun updateStepUI(step: Int) {
        currentStep = step

        binding.layoutStep1.visibility = if (step == 1) View.VISIBLE else View.GONE
        binding.layoutStep2.visibility = if (step == 2) View.VISIBLE else View.GONE
        binding.layoutStep3.visibility = if (step == 3) View.VISIBLE else View.GONE
        binding.layoutStep4.visibility = if (step == 4) View.VISIBLE else View.GONE

        // Cập nhật Stepper
        val colorPrimary = ContextCompat.getColor(this, R.color.primary)
        val colorGreen = ContextCompat.getColor(this, R.color.brand_green)
        val colorMuted = ContextCompat.getColor(this, R.color.text_secondary)

        // Step 1 badge
        binding.step1Badge.setBackgroundResource(if (step > 1) R.drawable.bg_badge_green else R.drawable.bg_badge_orange)
        binding.step1Badge.text = if (step > 1) "✓" else "1"
        binding.step1Text.setTextColor(if (step >= 1) colorPrimary else colorMuted)

        // Step 2 badge
        binding.step2Badge.setBackgroundResource(
            if (step > 2) R.drawable.bg_badge_green else if (step == 2) R.drawable.bg_badge_orange else R.drawable.bg_badge_duty_off
        )
        binding.step2Badge.text = if (step > 2) "✓" else "2"
        binding.step2Text.setTextColor(if (step >= 2) colorPrimary else colorMuted)

        // Step 3 badge
        binding.step3Badge.setBackgroundResource(
            if (step > 3) R.drawable.bg_badge_green else if (step == 3) R.drawable.bg_badge_orange else R.drawable.bg_badge_duty_off
        )
        binding.step3Badge.text = if (step > 3) "✓" else "3"
        binding.step3Text.setTextColor(if (step >= 3) colorPrimary else colorMuted)

        // Step 4 badge
        binding.step4Badge.setBackgroundResource(
            if (step == 4) R.drawable.bg_badge_orange else R.drawable.bg_badge_duty_off
        )
        binding.step4Text.setTextColor(if (step == 4) colorPrimary else colorMuted)

        // Nút hành động chính
        when (step) {
            1 -> {
                binding.btnTripCancel.visibility = View.VISIBLE
                binding.btnTripMainAction.text = "👉 ĐÃ ĐẾN QUÁN LẤY MÓN ➔"
                binding.btnTripMainAction.setBackgroundColor(colorPrimary)
            }
            2 -> {
                binding.btnTripCancel.visibility = View.VISIBLE
                binding.btnTripMainAction.text = "🛵 XÁC NHẬN ĐÃ LẤY TẤT CẢ MÓN & BẮT ĐẦU GIAO ➔"
                binding.btnTripMainAction.setBackgroundColor(colorPrimary)
            }
            3 -> {
                binding.btnTripCancel.visibility = View.GONE
                binding.btnTripMainAction.text = "👉 ĐÃ ĐẾN ĐIỂM GIAO KHÁCH ➔"
                binding.btnTripMainAction.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_green))
            }
            4 -> {
                binding.btnTripCancel.visibility = View.GONE
                binding.btnTripMainAction.text = "✅ XÁC NHẬN THU TIỀN & HOÀN TẤT ĐƠN HÀNG"
                binding.btnTripMainAction.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_green))
            }
        }
    }

    private fun completeTripOrder() {
        lifecycleScope.launch {
            try {
                firebaseRepository.updateOrderStatus(currentOrderId, Order.Status.COMPLETED)
                firebaseRepository.clearTracking(currentOrderId)

                AlertDialog.Builder(this@DeliveryTripActivity)
                    .setTitle("🎉 Giao Hàng Thành Công!")
                    .setMessage("Đơn hàng đã được hoàn tất thành công.\n\n+25.000 đ tiền công ship đã được cộng vào Ví Thu Nhập của bạn!")
                    .setPositiveButton("Về Trang Chủ") { _, _ ->
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@DeliveryTripActivity, "Lỗi cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCancelOrderDialog() {
        val dialogBinding = DialogCancelOrderReasonBinding.inflate(layoutInflater)
        val reasons = arrayOf(
            "Xe gặp sự cố kỹ thuật dọc đường (Thủng lốp, hỏng xe)",
            "Quán chuẩn bị món quá lâu",
            "Bị trùng tuyến hoặc quá tải đơn ghép",
            "Tài xế có việc gia đình đột xuất"
        )
        dialogBinding.spinnerCancelReason.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, reasons
        )

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelDialogBack.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnCancelDialogConfirm.setOnClickListener {
            val selectedReason = dialogBinding.spinnerCancelReason.selectedItem.toString()
            firebaseRepository.unassignShipper(currentOrderId) { success, msg ->
                if (success) {
                    Toast.makeText(this, "Đã hủy đơn thành công: $selectedReason", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    finish()
                } else {
                    Toast.makeText(this, "Lỗi hủy đơn: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }
}
