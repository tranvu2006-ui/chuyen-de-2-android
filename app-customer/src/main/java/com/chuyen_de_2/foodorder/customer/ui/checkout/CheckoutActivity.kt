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
import android.graphics.Color
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.customer.R
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
    private var selectedPaymentMode: String = Order.PaymentMode.FULL
    private var currentCustomerTrustScore: Int = 100

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

        // Khởi tạo các phương thức thanh toán chuẩn b8.html
        binding.layoutPayFull.setOnClickListener { selectPaymentMode(Order.PaymentMode.FULL) }
        binding.layoutPayDeposit.setOnClickListener { selectPaymentMode(Order.PaymentMode.DEPOSIT) }
        binding.layoutPayCod.setOnClickListener {
            if (currentCustomerTrustScore < 100) {
                Toast.makeText(
                    this,
                    "Chỉ khách hàng đạt 100 điểm uy tín mới được đặt đơn COD (Điểm hiện tại: $currentCustomerTrustScore/100). Vui lòng chọn Đặt cọc 20% hoặc Trả thẳng!",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            selectPaymentMode(Order.PaymentMode.COD)
        }

        selectPaymentMode(Order.PaymentMode.FULL)

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

            if (selectedPaymentMode == Order.PaymentMode.COD && currentCustomerTrustScore < 100) {
                Toast.makeText(
                    this,
                    "Bạn cần đạt 100 điểm uy tín để đặt đơn COD. Vui lòng chọn Đặt cọc 20% hoặc Trả thẳng!",
                    Toast.LENGTH_LONG
                ).show()
                selectPaymentMode(Order.PaymentMode.DEPOSIT)
                return@setOnClickListener
            }

            val finalTotal = maxOf(0L, currentSubtotal + currentShippingFee - currentDiscount)
            val depositAmount = ((finalTotal * 0.2) / 1000).toLong() * 1000
            val remainAmount = finalTotal - depositAmount

            val paymentMethod = if (selectedPaymentMode == Order.PaymentMode.COD) {
                Order.PaymentMethod.COD
            } else {
                Order.PaymentMethod.BANKING
            }

            viewModel.placeOrder(
                address = fullAddress,
                note = note,
                shippingFee = currentShippingFee,
                discount = currentDiscount,
                paymentMethod = paymentMethod,
                paymentMode = selectedPaymentMode,
                depositAmount = if (selectedPaymentMode == Order.PaymentMode.DEPOSIT) depositAmount else 0L,
                remainAmount = if (selectedPaymentMode == Order.PaymentMode.DEPOSIT) remainAmount else if (selectedPaymentMode == Order.PaymentMode.COD) finalTotal else 0L,
                customerPhone = phone
            )
        }
    }

    private fun selectPaymentMode(mode: String) {
        selectedPaymentMode = mode
        binding.rbPayFull.isChecked = (mode == Order.PaymentMode.FULL)
        binding.rbPayDeposit.isChecked = (mode == Order.PaymentMode.DEPOSIT)
        binding.rbPayCod.isChecked = (mode == Order.PaymentMode.COD)

        binding.layoutPayFull.setBackgroundResource(
            if (mode == Order.PaymentMode.FULL) R.drawable.bg_payment_card_selected else R.drawable.bg_payment_card_normal
        )
        binding.layoutPayDeposit.setBackgroundResource(
            if (mode == Order.PaymentMode.DEPOSIT) R.drawable.bg_payment_card_selected else R.drawable.bg_payment_card_normal
        )
        binding.layoutPayCod.setBackgroundResource(
            if (mode == Order.PaymentMode.COD) R.drawable.bg_payment_card_selected else R.drawable.bg_payment_card_normal
        )

        if (mode == Order.PaymentMode.COD) {
            binding.layoutChannelWrap.visibility = View.GONE
            binding.layoutBankingInfo.visibility = View.GONE
        } else {
            binding.layoutChannelWrap.visibility = View.VISIBLE
            binding.layoutBankingInfo.visibility = View.VISIBLE
            binding.tvChannelTitle.text = if (mode == Order.PaymentMode.FULL) {
                "Kênh quét mã thanh toán 100%:"
            } else {
                "Kênh quét mã thanh toán cọc 20%:"
            }
        }

        recalculateTotal()
    }

    /** Nạp danh sách địa chỉ lưu sẵn & thông tin liên hệ của khách hàng */
    private fun setupSavedAddresses() {
        val uid = firebaseAuth.currentUser?.uid ?: return

        // 1. Tự động điền SĐT & kiểm tra Điểm Uy Tín (TrustScore) theo thời gian thực
        lifecycleScope.launch {
            firebaseRepository.observeCustomerProfile(uid).collectLatest { cust ->
                currentCustomerTrustScore = cust.trustScore
                if (cust.trustScore >= 95 && currentDiscount < 5000L) {
                    currentDiscount = 5000L
                    recalculateTotal()
                }

                if (cust.trustScore >= 100) {
                    // Đạt 100 điểm uy tín:
                    // 1. Mới cho đặt đơn COD
                    binding.layoutPayCod.isEnabled = true
                    binding.layoutPayCod.alpha = 1.0f
                    binding.tvCodTrustPill.text = "⭐ Điểm uy tín: ${cust.trustScore}/100 (💎 Đạt 100 điểm: Đủ điều kiện thanh toán COD)"
                    binding.tvCodTrustPill.setTextColor(Color.parseColor("#059669"))

                    // 2. Ẩn phần đặt cọc
                    binding.layoutPayDeposit.visibility = View.GONE

                    // Nếu trước đó đang chọn Cọc thì chuyển sang COD
                    if (selectedPaymentMode == Order.PaymentMode.DEPOSIT) {
                        selectPaymentMode(Order.PaymentMode.COD)
                    }
                } else {
                    // Dưới 100 điểm uy tín:
                    // 1. Mới có phần đặt cọc 20%
                    binding.layoutPayDeposit.visibility = View.VISIBLE

                    // 2. Khóa hình thức COD (chỉ khách hàng đạt 100 điểm mới được đặt COD)
                    binding.layoutPayCod.isEnabled = false
                    binding.layoutPayCod.alpha = 0.55f
                    binding.tvCodTrustPill.text = "🔒 Cần đạt 100 điểm uy tín để mở khóa COD (Điểm hiện tại: ${cust.trustScore}/100)"
                    binding.tvCodTrustPill.setTextColor(Color.parseColor("#DC2626"))

                    // Nếu đang chọn COD thì chuyển sang Đặt Cọc 20%
                    if (selectedPaymentMode == Order.PaymentMode.COD) {
                        selectPaymentMode(Order.PaymentMode.DEPOSIT)
                    }
                }
            }
        }
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

            currentShippingFee = GoogleMapUtils.calculateShippingFeeByDistance(distanceKm)
            val distanceStr = String.format(Locale.US, "%.1f", distanceKm)
            binding.tvShipRule.text = "🛵 Google Maps: Khoảng cách ~$distanceStr km • Cước phí: ${currentShippingFee.toVndCurrency()}"
            recalculateTotal()
        }
    }

    private fun recalculateTotal() {
        binding.tvShippingFee.text = currentShippingFee.toVndCurrency()
        val finalTotal = maxOf(0L, currentSubtotal + currentShippingFee - currentDiscount)
        val depositAmount = ((finalTotal * 0.2) / 1000).toLong() * 1000
        val remainAmount = finalTotal - depositAmount

        binding.tvTotal.text = finalTotal.toVndCurrency()

        // Cập nhật nhãn số tiền trên 3 Option Cards (chuẩn b8.html)
        binding.tvPayFullAmt.text = "Thanh toán ngay: ${finalTotal.toVndCurrency()}"
        binding.tvDepositNow.text = "Cọc ngay (20%): ${depositAmount.toVndCurrency()}"
        binding.tvDepositRemain.text = "Thu khi nhận: ${remainAmount.toVndCurrency()}"
        binding.tvPayCodAmt.text = "Thanh toán khi nhận: ${finalTotal.toVndCurrency()} (Trả trước: 0 đ)"

        when (selectedPaymentMode) {
            Order.PaymentMode.FULL -> {
                binding.txtSelectedMode.text = "Trả thẳng 100% (VietQR Ngân Hàng)"
                binding.txtSelectedMode.setTextColor(ContextCompat.getColor(this, R.color.primary))
                binding.lblPayNowTitle.text = "👉 Cần thanh toán ngay (100%):"
                binding.lblPayNowTitle.setTextColor(ContextCompat.getColor(this, R.color.primary))
                binding.txtPayNowAmount.text = finalTotal.toVndCurrency()
                binding.txtPayNowAmount.setTextColor(ContextCompat.getColor(this, R.color.primary))
                binding.rowRemainNotice.visibility = View.GONE

                binding.lblBottomTitle.text = "Thanh toán ngay (100%):"
                binding.tvBottomTotal.text = finalTotal.toVndCurrency()
                binding.lblBottomSubNotice.visibility = View.GONE
                binding.btnPlaceOrder.text = "Thanh Toán Đủ (Quét Mã QR) ➔"
            }
            Order.PaymentMode.DEPOSIT -> {
                binding.txtSelectedMode.text = "Đặt cọc 20% (VietQR Ngân Hàng)"
                binding.txtSelectedMode.setTextColor(Color.parseColor("#D97706"))
                binding.lblPayNowTitle.text = "👉 Cần thanh toán cọc ngay (20%):"
                binding.lblPayNowTitle.setTextColor(Color.parseColor("#D97706"))
                binding.txtPayNowAmount.text = depositAmount.toVndCurrency()
                binding.txtPayNowAmount.setTextColor(Color.parseColor("#D97706"))
                binding.rowRemainNotice.visibility = View.VISIBLE
                binding.lblPayRemainTitle.text = "Thu khi nhận món:"
                binding.txtPayRemainAmount.text = "${remainAmount.toVndCurrency()} (Thu tiền mặt)"

                binding.lblBottomTitle.text = "Thanh toán cọc (20%):"
                binding.tvBottomTotal.text = depositAmount.toVndCurrency()
                binding.lblBottomSubNotice.visibility = View.VISIBLE
                binding.lblBottomSubNotice.text = "Còn lại ${remainAmount.toVndCurrency()} thu khi nhận"
                binding.btnPlaceOrder.text = "Thanh Toán Cọc (Quét Mã QR) ➔"
            }
            Order.PaymentMode.COD -> {
                binding.txtSelectedMode.text = "Thanh toán khi nhận hàng (COD - Uy tín cao)"
                binding.txtSelectedMode.setTextColor(Color.parseColor("#059669"))
                binding.lblPayNowTitle.text = "👉 Cần thanh toán ngay:"
                binding.lblPayNowTitle.setTextColor(Color.parseColor("#059669"))
                binding.txtPayNowAmount.text = "0 đ"
                binding.txtPayNowAmount.setTextColor(Color.parseColor("#059669"))
                binding.rowRemainNotice.visibility = View.VISIBLE
                binding.lblPayRemainTitle.text = "Thu khi nhận món:"
                binding.txtPayRemainAmount.text = "${finalTotal.toVndCurrency()} (Shipper thu khi giao món)"

                binding.lblBottomTitle.text = "Cần thanh toán ngay:"
                binding.tvBottomTotal.text = "0 đ"
                binding.lblBottomSubNotice.visibility = View.VISIBLE
                binding.lblBottomSubNotice.text = "Thu ${finalTotal.toVndCurrency()} khi nhận món"
                binding.btnPlaceOrder.text = "Đặt Đơn COD (0đ Trả Trước) ➔"
            }
        }
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
                val depositAmount = ((finalTotal * 0.2) / 1000).toLong() * 1000
                val remainAmount = finalTotal - depositAmount
                val targetShopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME) ?: currentStore?.name ?: supportActionBar?.title?.toString() ?: "Quán Ăn"

                when (selectedPaymentMode) {
                    Order.PaymentMode.COD -> {
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
                            putExtra("extra_payment_mode", Order.PaymentMode.COD)
                            putExtra("extra_deposit", 0L)
                            putExtra("extra_remain", finalTotal)
                        }
                        startActivity(intent)
                    }
                    Order.PaymentMode.DEPOSIT -> {
                        // Cọc 20%: Chuyển sang PaymentActivity quét mã QR với số tiền cọc 20%
                        val intent = Intent(this, com.chuyen_de_2.foodorder.customer.ui.payment.PaymentActivity::class.java).apply {
                            putExtra(Constants.EXTRA_ORDER_ID, orderId)
                            putExtra(Constants.EXTRA_SHOP_ID, currentTargetShopId)
                            putExtra(Constants.EXTRA_SHOP_NAME, targetShopName)
                            putExtra("extra_address", fullAddress)
                            putExtra("extra_amount", depositAmount) // Quét QR đúng số tiền cọc
                            putExtra("extra_deposit", depositAmount)
                            putExtra("extra_remain", remainAmount)
                            putExtra("extra_total", finalTotal)
                            putExtra("extra_subtotal", currentSubtotal)
                            putExtra("extra_shipping_fee", currentShippingFee)
                            putExtra("extra_discount", currentDiscount)
                            putExtra("extra_payment_mode", Order.PaymentMode.DEPOSIT)
                            putExtra("extra_channel", "bank")
                            putExtra("extra_bank_name", currentStore?.bankName.orEmpty())
                            putExtra("extra_bank_number", currentStore?.bankAccountNumber.orEmpty())
                            putExtra("extra_bank_holder", currentStore?.bankAccountName.orEmpty())
                            putExtra("extra_bank_bin", currentStore?.bankBin.orEmpty())
                            putExtra("extra_created_at", System.currentTimeMillis())
                        }
                        startActivity(intent)
                    }
                    Order.PaymentMode.FULL -> {
                        // Trả thẳng 100%: Quét QR toàn bộ số tiền
                        val intent = Intent(this, com.chuyen_de_2.foodorder.customer.ui.payment.PaymentActivity::class.java).apply {
                            putExtra(Constants.EXTRA_ORDER_ID, orderId)
                            putExtra(Constants.EXTRA_SHOP_ID, currentTargetShopId)
                            putExtra(Constants.EXTRA_SHOP_NAME, targetShopName)
                            putExtra("extra_address", fullAddress)
                            putExtra("extra_amount", finalTotal)
                            putExtra("extra_deposit", 0L)
                            putExtra("extra_remain", 0L)
                            putExtra("extra_total", finalTotal)
                            putExtra("extra_subtotal", currentSubtotal)
                            putExtra("extra_shipping_fee", currentShippingFee)
                            putExtra("extra_discount", currentDiscount)
                            putExtra("extra_payment_mode", Order.PaymentMode.FULL)
                            putExtra("extra_channel", "bank")
                            putExtra("extra_bank_name", currentStore?.bankName.orEmpty())
                            putExtra("extra_bank_number", currentStore?.bankAccountNumber.orEmpty())
                            putExtra("extra_bank_holder", currentStore?.bankAccountName.orEmpty())
                            putExtra("extra_bank_bin", currentStore?.bankBin.orEmpty())
                            putExtra("extra_created_at", System.currentTimeMillis())
                        }
                        startActivity(intent)
                    }
                }
                finish()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "Lỗi đặt đơn", Toast.LENGTH_LONG).show()
            }
        }
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
                        binding.tvBankingDetails.text = "• Ngân hàng: $bankDisplay\n• Số tài khoản: ${store.bankAccountNumber}\n• Chủ tài khoản: $ownerDisplay\n• Cú pháp CK: OISHI [Mã đơn hàng]"
                    }
                }
            }
        }
    }
}
