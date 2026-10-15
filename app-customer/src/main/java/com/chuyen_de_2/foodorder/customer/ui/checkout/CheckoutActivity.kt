package com.chuyen_de_2.foodorder.customer.ui.checkout

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.GoogleMapUtils
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.databinding.ActivityCheckoutBinding
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

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

    // Launcher mở màn hình bản đồ Google Maps chọn vị trí
    private val selectLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val street = data.getStringExtra(SelectLocationActivity.EXTRA_STREET_ADDRESS) ?: ""
            val district = data.getStringExtra(SelectLocationActivity.EXTRA_DISTRICT) ?: ""

            if (street.isNotBlank()) {
                binding.etAddress.setText(street)
            }

            // Tự động chọn đúng quận trong Spinner
            if (district.isNotBlank()) {
                val index = districts.indexOfFirst { it.contains(district, ignoreCase = true) }
                if (index != -1) {
                    binding.spDistrict.setSelection(index)
                }
            }

            updateLocationAndFee()
            Toast.makeText(this, "📍 Đã chọn địa chỉ từ Google Maps thành công!", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher xin quyền GPS cho nút lấy vị trí nhanh
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fetchCurrentGpsAndFillAddress()
        } else {
            Toast.makeText(this, "Cần cấp quyền vị trí để định vị GPS", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val targetShopId = intent.getStringExtra(Constants.EXTRA_SHOP_ID)
        if (!targetShopId.isNullOrBlank()) {
            viewModel.loadCarts(targetShopId)
        }

        InsetUtils.setLightStatusBar(this, false)
        InsetUtils.applyToolbarInsets(binding.toolbar)
        InsetUtils.applyNavigationBarPadding(binding.bottomBar)

        setupToolbar()
        setupUI()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        val targetShopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME)
        supportActionBar?.apply {
            title = if (!targetShopName.isNullOrBlank()) targetShopName else "Thanh Toán Đơn Hàng"
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupUI() {
        // Dropdown quận huyện theo b8.html
        val districtAdapter = ArrayAdapter(
            this,
            com.chuyen_de_2.foodorder.customer.R.layout.item_district_spinner,
            districts
        )
        districtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spDistrict.adapter = districtAdapter

        binding.spDistrict.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateLocationAndFee()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Tự động Geocode khi người dùng thay đổi địa chỉ
        binding.etAddress.doAfterTextChanged {
            updateLocationAndFee()
        }

        // 1. NÚT CHỌN ĐỊA CHỈ TRÊN GOOGLE MAPS (Bản đồ trực quan tương tác)
        binding.btnSelectFromMap.setOnClickListener {
            val intent = Intent(this, SelectLocationActivity::class.java)
            selectLocationLauncher.launch(intent)
        }

        // 2. NÚT ĐỊNH VỊ GPS NHANH TẠI CHỖ
        binding.btnQuickGps.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                fetchCurrentGpsAndFillAddress()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        // Bấm vào cước phí để xem trước trên Google Maps ngoài
        binding.tvShipRule.setOnClickListener {
            val streetAddress = binding.etAddress.text.toString().trim()
            val selectedDistrict = binding.spDistrict.selectedItem?.toString() ?: "Quận 1"
            val fullAddress = if (streetAddress.isNotBlank()) "$streetAddress, $selectedDistrict, TP.HCM" else "$selectedDistrict, TP.HCM"
            GoogleMapUtils.openGoogleMapsSearch(this, fullAddress)
        }

        // Mặc định chọn Chuyển khoản ngân hàng (VietQR) theo screenshot
        binding.rbBanking.isChecked = true
        binding.layoutBankingInfo.visibility = View.VISIBLE

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
                binding.etAddress.requestFocus()
                return@setOnClickListener
            }
            binding.tilAddress.error = null

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

    /** Lấy toạ độ GPS hiện tại và tự động điền địa chỉ */
    private fun fetchCurrentGpsAndFillAddress() {
        Toast.makeText(this, "📡 Đang lấy vị trí GPS hiện tại...", Toast.LENGTH_SHORT).show()
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    lifecycleScope.launch {
                        val fullAddr = GoogleMapUtils.reverseGeocode(
                            this@CheckoutActivity,
                            location.latitude,
                            location.longitude
                        )

                        // Trích xuất quận
                        val knownDistricts = listOf(
                            "Quận 1", "Quận 3", "Quận 4", "Quận 5", "Quận 6",
                            "Quận 7", "Quận 8", "Quận 10", "Quận 11", "Quận 12",
                            "Bình Thạnh", "Phú Nhuận", "Tân Bình", "Tân Phú",
                            "Gò Vấp", "Bình Tân", "Thủ Đức", "Bình Chánh",
                            "Hóc Môn", "Nhà Bè"
                        )
                        var matchedDistrict = "Quận 1"
                        for (d in knownDistricts) {
                            if (fullAddr.contains(d, ignoreCase = true)) {
                                matchedDistrict = d
                                break
                            }
                        }

                        val street = fullAddr
                            .replace(", TP. Hồ Chí Minh", "")
                            .replace(", TP.HCM", "")
                            .replace(", $matchedDistrict", "")
                            .trim()

                        binding.etAddress.setText(street)

                        val index = districts.indexOfFirst { it.contains(matchedDistrict, ignoreCase = true) }
                        if (index != -1) {
                            binding.spDistrict.setSelection(index)
                        }

                        updateLocationAndFee()
                        Toast.makeText(this@CheckoutActivity, "🎯 Đã định vị thành công: $fullAddr", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Không lấy được vị trí GPS. Vui lòng bật vị trí thiết bị!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: SecurityException) {}
    }

    private fun updateLocationAndFee() {
        val streetAddress = binding.etAddress.text.toString().trim()
        val selectedDistrict = binding.spDistrict.selectedItem?.toString() ?: "Quận 1 (Nội thành)"
        val fullAddress = if (streetAddress.isNotBlank()) "$streetAddress, $selectedDistrict, TP.HCM" else "$selectedDistrict, TP.HCM"

        lifecycleScope.launch {
            val storeId = intent.getStringExtra(Constants.EXTRA_SHOP_ID) ?: "store_1"
            val storeLatLng = GoogleMapUtils.STORE_COORDINATES[storeId] ?: GoogleMapUtils.DEFAULT_HCMC_LATLNG
            val customerLatLng = GoogleMapUtils.geocodeAddress(this@CheckoutActivity, fullAddress)
            val distanceKm = GoogleMapUtils.calculateDistanceKm(storeLatLng, customerLatLng)

            currentShippingFee = GoogleMapUtils.calculateShippingFeeByDistance(distanceKm)
            val distanceStr = String.format(Locale.US, "%.1f", distanceKm)
            binding.tvShipRule.text = "🛵 Google Maps: Khoảng cách ~$distanceStr km • Cước phí: ${currentShippingFee.toVndCurrency()}"
            recalculateTotal()
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
            val shopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME) ?: carts.firstOrNull()?.shopName
            if (!shopName.isNullOrBlank()) {
                supportActionBar?.title = shopName
            }
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

                val streetAddress = binding.etAddress.text.toString().trim()
                val selectedDistrict = binding.spDistrict.selectedItem?.toString() ?: ""
                val fullAddress = "$streetAddress, $selectedDistrict, TP.HCM"
                val finalTotal = maxOf(0L, currentSubtotal + currentShippingFee - currentDiscount)
                val targetShopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME) ?: supportActionBar?.title?.toString() ?: "Phở Hà Nội 36"

                if (binding.rbBanking.isChecked || binding.rbMoMo.isChecked) {
                    // Chuyển sang màn hình Quét Mã Thanh Toán chuẩn b8_payment.html
                    val channel = if (binding.rbMoMo.isChecked) "momo" else "bank"
                    val intent = Intent(this, com.chuyen_de_2.foodorder.customer.ui.payment.PaymentActivity::class.java).apply {
                        putExtra(Constants.EXTRA_ORDER_ID, orderId)
                        putExtra(Constants.EXTRA_SHOP_NAME, targetShopName)
                        putExtra("extra_address", fullAddress)
                        putExtra("extra_amount", finalTotal)
                        putExtra("extra_subtotal", currentSubtotal)
                        putExtra("extra_shipping_fee", currentShippingFee)
                        putExtra("extra_discount", currentDiscount)
                        putExtra("extra_channel", channel)
                    }
                    startActivity(intent)
                } else {
                    // COD: Chuyển sang màn hình Đặt Đơn Thành Công chuẩn b8_success.html
                    val intent = Intent(this, com.chuyen_de_2.foodorder.customer.ui.order.OrderSuccessActivity::class.java).apply {
                        putExtra(Constants.EXTRA_ORDER_ID, orderId)
                        putExtra(Constants.EXTRA_SHOP_NAME, targetShopName)
                        putExtra("extra_address", fullAddress)
                        putExtra("extra_amount", finalTotal)
                        putExtra("extra_subtotal", currentSubtotal)
                        putExtra("extra_shipping_fee", currentShippingFee)
                        putExtra("extra_discount", currentDiscount)
                        putExtra("extra_payment_method", "Tiền mặt khi nhận hàng (COD)")
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
