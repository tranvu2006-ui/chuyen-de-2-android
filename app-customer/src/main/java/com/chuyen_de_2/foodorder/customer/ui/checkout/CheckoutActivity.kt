package com.chuyen_de_2.foodorder.customer.ui.checkout

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.databinding.ActivityCheckoutBinding
import com.chuyen_de_2.foodorder.customer.ui.tracking.TrackingActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CheckoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private val viewModel: CheckoutViewModel by viewModels()

    private var currentSubtotal = 0L
    private var currentShippingFee = 15000L
    private var currentDiscount = 0L

    private val districts = arrayOf(
        "Quận 1 (Nội thành)",
        "Quận 3 (Nội thành)",
        "Quận 5 (Nội thành)",
        "Quận 10 (Nội thành)",
        "Quận Phú Nhuận (Nội thành)",
        "Quận Bình Thạnh (Nội thành)",
        "Quận Tân Bình (Nội thành)",
        "TP. Thủ Đức (Ngoại thành)",
        "Quận 12 (Ngoại thành)",
        "Quận Bình Tân (Ngoại thành)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, false)
        InsetUtils.applyToolbarInsets(binding.toolbar)
        InsetUtils.applyNavigationBarPadding(binding.bottomBar)

        setupToolbar()
        setupUI()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Xác Nhận & Thanh Toán"
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupUI() {
        // Dropdown quận huyện theo b8.html
        val districtAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            districts
        )
        binding.spDistrict.adapter = districtAdapter

        binding.spDistrict.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = districts[position]
                if (selected.contains("Nội thành")) {
                    currentShippingFee = 15000L
                    binding.tvShipRule.text = "🛵 Cước phí giao hàng đồng giá 15.000 đ cho nội thành TP.HCM"
                } else {
                    currentShippingFee = 25000L
                    binding.tvShipRule.text = "🛵 Cước phí giao hàng 25.000 đ cho khu vực ngoại thành TP.HCM"
                }
                recalculateTotal()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Voucher Coupon (b8.html)
        binding.btnApplyVoucher.setOnClickListener {
            val code = binding.etVoucherCode.text.toString().trim()
            if (code.equals("OISHI15K", ignoreCase = true)) {
                currentDiscount = 15000L
                binding.layoutVoucherDiscount.visibility = View.VISIBLE
                binding.tvDiscount.text = "-15.000 đ"
                binding.tvVoucherMessage.text = "✔ Đã áp dụng mã OISHI15K: Giảm 15.000 đ"
                binding.tvVoucherMessage.setTextColor(getColor(android.R.color.holo_green_dark))
                recalculateTotal()
                Toast.makeText(this, "🎉 Áp dụng mã OISHI15K thành công!", Toast.LENGTH_SHORT).show()
            } else if (code.isBlank()) {
                Toast.makeText(this, "Vui lòng nhập mã voucher", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Mã voucher không hợp lệ hoặc đã hết hạn", Toast.LENGTH_SHORT).show()
            }
        }

        // Chọn phương thức thanh toán
        binding.rgPayment.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutBankingInfo.visibility = if (checkedId == binding.rbBanking.id) View.VISIBLE else View.GONE
        }

        // Đặt hàng
        binding.btnPlaceOrder.setOnClickListener {
            val streetAddress = binding.etAddress.text.toString().trim()
            val note = binding.etNote.text.toString().trim()

            if (streetAddress.isBlank()) {
                binding.tilAddress.error = "Vui lòng nhập địa chỉ giao hàng"
                return@setOnClickListener
            }

            val selectedDistrict = binding.spDistrict.selectedItem?.toString() ?: ""
            val fullAddress = "$streetAddress, $selectedDistrict, TP.HCM"

            val paymentMethod = when {
                binding.rbMoMo.isChecked -> Order.PaymentMethod.MOMO_QR
                binding.rbBanking.isChecked -> Order.PaymentMethod.BANKING
                else -> Order.PaymentMethod.COD
            }

            viewModel.placeOrder(
                address = fullAddress,
                note = note,
                shippingFee = currentShippingFee,
                discount = currentDiscount,
                paymentMethod = paymentMethod
            )
        }
    }

    private fun recalculateTotal() {
        binding.tvShippingFee.text = currentShippingFee.toVndCurrency()
        val finalTotal = maxOf(0L, currentSubtotal + currentShippingFee - currentDiscount)
        binding.tvTotal.text = finalTotal.toVndCurrency()
        binding.tvBottomTotal.text = finalTotal.toVndCurrency()
    }

    private fun observeViewModel() {
        viewModel.carts.observe(this) { carts ->
            currentSubtotal = carts.sumOf { it.totalAmount }
            binding.tvSubtotal.text = currentSubtotal.toVndCurrency()
            binding.tvItemCount.text = "${carts.sumOf { it.items.size }} món"

            recalculateTotal()

            // Hiển thị danh sách tóm tắt
            val summary = carts.flatMap { cart ->
                cart.items.map { (_, item) ->
                    val opt = if (item.options.isNotBlank()) " (${item.options})" else ""
                    "• ${item.name}$opt x${item.quantity} — ${(item.price * item.quantity).toVndCurrency()}"
                }
            }.joinToString("\n")
            binding.tvOrderSummary.text = summary
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnPlaceOrder.isEnabled = !isLoading
        }

        viewModel.orderResult.observe(this) { result ->
            result.onSuccess { orderId ->
                Toast.makeText(this, "🎉 Đặt hàng thành công!", Toast.LENGTH_SHORT).show()

                // Mở màn hình Theo dõi đơn hàng theo chuẩn b9.html
                if (orderId.isNotBlank() && orderId != "OK") {
                    val intent = Intent(this, TrackingActivity::class.java).apply {
                        putExtra(Constants.EXTRA_ORDER_ID, orderId)
                    }
                    startActivity(intent)
                }
                finish()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "Lỗi đặt đơn", Toast.LENGTH_LONG).show()
            }
        }
    }
}
