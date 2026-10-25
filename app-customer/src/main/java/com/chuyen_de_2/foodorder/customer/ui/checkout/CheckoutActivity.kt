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
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.CustomerAddress
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.GoogleMapUtils
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.databinding.ActivityCheckoutBinding
import com.chuyen_de_2.foodorder.customer.databinding.DialogAddressBookBinding
import com.chuyen_de_2.foodorder.customer.ui.profile.AddressBookAdapter
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Tên chức năng: Màn Hình Xác Nhận Thanh Toán & Đặt Hàng (Checkout & Order Placement Screen)
 * Cách hoạt động:
 *  - Thu thập thông tin giao hàng: Họ tên người nhận, SĐT, Địa chỉ chi tiết, Quận/Huyện.
 *  - Hỗ trợ chọn địa chỉ từ Sổ địa chỉ đã lưu hoặc ghim vị trí trực tiếp trên bản đồ qua [SelectLocationActivity].
 *  - Tự động tính toán phí vận chuyển (Shipping Fee) dựa trên khoảng cách giữa tọa độ Quán và Khách hàng.
 *  - Áp dụng mã giảm giá voucher khuyến mãi (nếu có).
 *  - Hỗ trợ các phương thức thanh toán: Tiền mặt (COD) hoặc Chuyển khoản Ngân hàng (VietQR Napas).
 *  - Ghi nhận đơn hàng mới (Status: PENDING) lên Firestore.
 * Luồng hướng tới:
 *  - Thanh toán COD -> Chuyển sang [OrderSuccessActivity].
 *  - Thanh toán VietQR -> Chuyển sang [PaymentActivity] để quét mã chuyển khoản.
 *  - Bấm chọn vị trí bản đồ -> Mở [SelectLocationActivity].
 */
@AndroidEntryPoint
class CheckoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private val viewModel: CheckoutViewModel by viewModels()

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private var savedAddressList: List<CustomerAddress> = emptyList()
    private var hasAutoFilledDefaultAddress = false
    private var currentTargetShopId: String = ""
    private var currentStore: com.chuyen_de_2.foodorder.core.data.model.Store? = null

    private var currentSubtotal = 0L
    private var currentShippingFee = 15000L
    private var currentDiscount = 0L
    private var selectedCustomerLat = 0.0
    private var selectedCustomerLng = 0.0

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
            selectedCustomerLat = data.getDoubleExtra(SelectLocationActivity.EXTRA_LAT, 0.0)
            selectedCustomerLng = data.getDoubleExtra(SelectLocationActivity.EXTRA_LNG, 0.0)

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

        val targetShopId = intent.getStringExtra(Constants.EXTRA_SHOP_ID).orEmpty()
        currentTargetShopId = targetShopId
        if (currentTargetShopId.isNotBlank()) {
            viewModel.loadCarts(currentTargetShopId)
            observeShopInfo(currentTargetShopId)
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
        // Thiết lập sổ địa chỉ lưu sẵn & chọn nhanh
        setupSavedAddresses()

        binding.btnOpenSavedAddresses.setOnClickListener {
            showSelectAddressBottomSheet()
        }

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

        // Mặc định chọn VNPay sandbox
        binding.rbVnPay.isChecked = true
        binding.layoutBankingInfo.visibility = View.GONE

        // Chọn phương thức thanh toán
        binding.rgPayment.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutBankingInfo.visibility = if (checkedId == binding.rbBanking.id) View.VISIBLE else View.GONE
        }

        // Đặt hàng
        binding.btnPlaceOrder.setOnClickListener {
            val streetAddress = binding.etAddress.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val note = binding.etNote.text.toString().trim()

            if (streetAddress.isBlank()) {
                binding.tilAddress.error = "Vui lòng nhập địa chỉ giao hàng"
                binding.etAddress.requestFocus()
                return@setOnClickListener
            }
            binding.tilAddress.error = null

            if (phone.isBlank()) {
                binding.tilPhone.error = "Vui lòng nhập số điện thoại người nhận"
                binding.etPhone.requestFocus()
                return@setOnClickListener
            }
            binding.tilPhone.error = null

            val selectedDistrict = binding.spDistrict.selectedItem?.toString() ?: ""
            val fullAddress = "$streetAddress, $selectedDistrict, TP.HCM"

            val paymentMethod = when {
                binding.rbVnPay.isChecked -> Order.PaymentMethod.VNPAY
                binding.rbBanking.isChecked -> Order.PaymentMethod.BANKING
                else -> Order.PaymentMethod.COD
            }

            viewModel.placeOrder(
                address = fullAddress,
                note = note,
                shippingFee = currentShippingFee,
                discount = currentDiscount,
                paymentMethod = paymentMethod,
                customerPhone = phone
            )
        }
    }

    /** Nạp danh sách địa chỉ lưu sẵn & thông tin liên hệ của khách hàng */
    private fun setupSavedAddresses() {
        val uid = firebaseAuth.currentUser?.uid ?: return

        // 1. Tự động điền SĐT nếu tài khoản đã có
        lifecycleScope.launch {
            val userResult = authRepository.getCurrentUserData()
            userResult.onSuccess { user ->
                if (user.phone.isNotBlank() && binding.etPhone.text.isNullOrBlank()) {
                    binding.etPhone.setText(user.phone)
                }
            }
        }

        // 2. Lắng nghe danh sách địa chỉ đã lưu
        lifecycleScope.launch {
            firebaseRepository.observeCustomerAddresses(uid).collectLatest { list ->
                savedAddressList = list
                binding.cgSavedAddresses.removeAllViews()

                if (list.isEmpty()) {
                    binding.layoutSavedAddressBar.visibility = View.GONE
                    return@collectLatest
                }

                binding.layoutSavedAddressBar.visibility = View.VISIBLE

                for (addr in list) {
                    val chip = Chip(this@CheckoutActivity).apply {
                        text = if (addr.isDefault) "⭐ ${addr.label}" else addr.label
                        isCheckable = false
                        isClickable = true
                        setOnClickListener {
                            applyAddress(addr)
                        }
                    }
                    binding.cgSavedAddresses.addView(chip)
                }

                // Tự động điền địa chỉ mặc định lần đầu nếu người dùng chưa nhập gì
                if (!hasAutoFilledDefaultAddress && binding.etAddress.text.isNullOrBlank()) {
                    val defaultAddr = list.firstOrNull { it.isDefault } ?: list.firstOrNull()
                    if (defaultAddr != null) {
                        applyAddress(defaultAddr)
                        hasAutoFilledDefaultAddress = true
                    }
                }
            }
        }
    }

    /** Áp dụng địa chỉ đã lưu vào form checkout và tính cước phí */
    private fun applyAddress(addr: CustomerAddress) {
        if (addr.streetAddress.isNotBlank()) {
            binding.etAddress.setText(addr.streetAddress)
        }

        val district = addr.district
        if (district.isNotBlank()) {
            val index = districts.indexOfFirst { it.contains(district, ignoreCase = true) }
            if (index != -1) {
                binding.spDistrict.setSelection(index)
            }
        }

        if (addr.receiverPhone.isNotBlank()) {
            binding.etPhone.setText(addr.receiverPhone)
        }

        updateLocationAndFee()
        Toast.makeText(this, "📍 Đã chọn: ${addr.label} (${addr.fullAddress()})", Toast.LENGTH_SHORT).show()
    }

    /** Mở BottomSheet chọn từ Sổ Địa Chỉ đã lưu */
    private fun showSelectAddressBottomSheet() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (savedAddressList.isEmpty()) {
            Toast.makeText(this, "Bạn chưa có địa chỉ lưu sẵn nào. Hãy vào Hồ Sơ để thêm nhé!", Toast.LENGTH_LONG).show()
            return
        }

        val sheetBinding = DialogAddressBookBinding.inflate(LayoutInflater.from(this))
        val bottomSheet = BottomSheetDialog(this)
        bottomSheet.setContentView(sheetBinding.root)

        val adapter = AddressBookAdapter(
            onSelectClick = { selectedAddr ->
                applyAddress(selectedAddr)
                bottomSheet.dismiss()
            },
            onSetDefaultClick = { selectedAddr ->
                lifecycleScope.launch {
                    firebaseRepository.setDefaultAddress(uid, selectedAddr.addressId)
                }
            },
            onDeleteClick = { selectedAddr ->
                lifecycleScope.launch {
                    firebaseRepository.deleteCustomerAddress(uid, selectedAddr.addressId)
                }
            }
        )

        sheetBinding.rvAddressBook.apply {
            layoutManager = LinearLayoutManager(this@CheckoutActivity)
            this.adapter = adapter
        }

        adapter.submitList(savedAddressList)
        sheetBinding.btnAddNewAddress.visibility = View.GONE // Trong checkout chỉ tập trung chọn nhanh
        sheetBinding.btnCloseAddressBook.setOnClickListener { bottomSheet.dismiss() }

        bottomSheet.show()
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
        val hasPaymentTestItem = hasPaymentTestItem()
        if (hasPaymentTestItem) {
            currentShippingFee = 0L
            binding.tvShipRule.text = "🧪 Đơn thử nghiệm • Miễn phí giao hàng"
            recalculateTotal()
            return
        }

        currentShippingFee = DEFAULT_SHIPPING_FEE
        val streetAddress = binding.etAddress.text.toString().trim()
        val selectedDistrict = binding.spDistrict.selectedItem?.toString() ?: "Quận 1 (Nội thành)"
        val fullAddress = if (streetAddress.isNotBlank()) "$streetAddress, $selectedDistrict, TP.HCM" else "$selectedDistrict, TP.HCM"

        lifecycleScope.launch {
            val storeId = intent.getStringExtra(Constants.EXTRA_SHOP_ID) ?: currentTargetShopId
            val store = currentStore ?: if (storeId.isNotBlank()) firebaseRepository.getStoreOnce(storeId) else null
            val storeLatLng = if (store != null && store.latitude != 0.0 && store.longitude != 0.0) {
                com.google.android.gms.maps.model.LatLng(store.latitude, store.longitude)
            } else if (store != null && store.address.isNotBlank()) {
                GoogleMapUtils.geocodeAddress(this@CheckoutActivity, "${store.address}, TP.HCM")
            } else {
                GoogleMapUtils.DEFAULT_HCMC_LATLNG
            }
            val customerLatLng = if (selectedCustomerLat != 0.0 && selectedCustomerLng != 0.0) {
                com.google.android.gms.maps.model.LatLng(selectedCustomerLat, selectedCustomerLng)
            } else {
                GoogleMapUtils.geocodeAddress(this@CheckoutActivity, fullAddress)
            }
            val distanceKm = GoogleMapUtils.calculateDistanceKm(storeLatLng, customerLatLng)

            if (hasPaymentTestItem()) {
                currentShippingFee = 0L
                binding.tvShipRule.text = "🧪 Đơn thử nghiệm • Miễn phí giao hàng"
                recalculateTotal()
                return@launch
            }

            currentShippingFee = GoogleMapUtils.calculateShippingFeeByDistance(distanceKm)
            val distanceStr = String.format(Locale.US, "%.1f", distanceKm)
            binding.tvShipRule.text = "🛵 Google Maps: Khoảng cách ~$distanceStr km • Cước phí: ${currentShippingFee.toVndCurrency()}"
            recalculateTotal()
        }
    }

    private fun hasPaymentTestItem(): Boolean {
        return viewModel.carts.value.orEmpty().any { cart ->
            cart.items.values.any { it.price == PAYMENT_TEST_ITEM_PRICE && it.quantity > 0 }
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
            if (currentTargetShopId.isBlank() && carts.isNotEmpty()) {
                currentTargetShopId = carts[0].shopId
                observeShopInfo(currentTargetShopId)
            }

            currentSubtotal = carts.sumOf { it.totalAmount }
            binding.tvSubtotal.text = currentSubtotal.toVndCurrency()
            binding.tvItemCount.text = "${carts.sumOf { it.items.size }} món"

            updateLocationAndFee()
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
                val targetShopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME) ?: currentStore?.name ?: supportActionBar?.title?.toString() ?: "Quán Ăn"

                if (binding.rbVnPay.isChecked) {
                    startActivity(
                        com.chuyen_de_2.foodorder.customer.ui.payment.VnPayActivity.createIntent(
                            context = this,
                            orderId = orderId,
                            shopName = targetShopName,
                            address = fullAddress,
                            amount = finalTotal,
                            subtotal = currentSubtotal,
                            shippingFee = currentShippingFee,
                            discount = currentDiscount,
                            orderItems = buildPaymentItemSummary()
                        )
                    )
                } else if (binding.rbBanking.isChecked) {
                    // Chuyển sang màn hình Quét Mã VietQR Ngân Hàng (với thông tin tài khoản của Quán)
                    val intent = Intent(this, com.chuyen_de_2.foodorder.customer.ui.payment.PaymentActivity::class.java).apply {
                        putExtra(Constants.EXTRA_ORDER_ID, orderId)
                        putExtra(Constants.EXTRA_SHOP_ID, currentTargetShopId)
                        putExtra(Constants.EXTRA_SHOP_NAME, targetShopName)
                        putExtra("extra_address", fullAddress)
                        putExtra("extra_amount", finalTotal)
                        putExtra("extra_subtotal", currentSubtotal)
                        putExtra("extra_shipping_fee", currentShippingFee)
                        putExtra("extra_discount", currentDiscount)
                        putExtra("extra_order_items", buildPaymentItemSummary())
                        putExtra("extra_channel", "bank")
                        putExtra("extra_bank_name", currentStore?.bankName.orEmpty())
                        putExtra("extra_bank_number", currentStore?.bankAccountNumber.orEmpty())
                        putExtra("extra_bank_holder", currentStore?.bankAccountName.orEmpty())
                        putExtra("extra_bank_bin", currentStore?.bankBin.orEmpty())
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

    private fun buildPaymentItemSummary(): String {
        return viewModel.carts.value.orEmpty().flatMap { cart ->
            cart.items.values.map { item ->
                "${item.quantity}x ${item.name}${if (item.options.isNotBlank()) " (${item.options})" else ""} — ${(item.price * item.quantity).toVndCurrency()}"
            }
        }.joinToString("\n")
    }

    private companion object {
        const val PAYMENT_TEST_ITEM_PRICE = 333L
        const val DEFAULT_SHIPPING_FEE = 15000L
    }

    /** Lắng nghe thông tin tài khoản ngân hàng của quán để hiển thị cho khách xem trước */
    private fun observeShopInfo(shopId: String) {
        if (shopId.isBlank()) return
        lifecycleScope.launch {
            firebaseRepository.observeStore(shopId).collectLatest { store ->
                if (store != null) {
                    currentStore = store
                    if (store.bankAccountNumber.isNotBlank()) {
                        val bankDisplay = store.bankName.ifBlank { "MB Bank (Quân Đội)" }
                        val ownerDisplay = store.bankAccountName.ifBlank { store.name }
                        binding.tvBankingDetails.text = "• Ngân hàng: $bankDisplay\n• Số tài khoản: ${store.bankAccountNumber}\n• Chủ tài khoản: $ownerDisplay\n• Nội dung CK: ORD_OISHI_[MÃ ĐƠN]"
                    }
                }
            }
        }
    }
}
