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
import android.content.res.ColorStateList
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
import com.google.firebase.database.FirebaseDatabase
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
    private var selectedChannel: String = "bank"
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
        setupVoucherComponent()
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
                val chosenDistrict = districts.getOrNull(position).orEmpty()
                val normChosen = GoogleMapUtils.normalizeDistrictName(chosenDistrict)
                if (selectedCustomerLat != 0.0 && selectedCustomerLng != 0.0) {
                    val currentPinDistrict = GoogleMapUtils.findNearestDistrict(selectedCustomerLat, selectedCustomerLng)
                    if (!currentPinDistrict.equals(normChosen, ignoreCase = true)) {
                        selectedCustomerLat = 0.0
                        selectedCustomerLng = 0.0
                    }
                }
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
            val orderTotal = maxOf(0L, currentSubtotal + currentShippingFee - currentDiscount)
            if (currentCustomerTrustScore < 50) {
                Toast.makeText(
                    this,
                    "Điểm uy tín của bạn là $currentCustomerTrustScore/100 (< 50 điểm). Hình thức COD bị khóa, vui lòng chọn Đặt cọc 20% hoặc Trả thẳng!",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            if (currentCustomerTrustScore < 80 && orderTotal > 150_000L) {
                Toast.makeText(
                    this,
                    "Điểm uy tín Hạng Bạc ($currentCustomerTrustScore/100) chỉ hỗ trợ COD tối đa 150.000đ. Đơn này giá trị ${orderTotal.toVndCurrency()}, vui lòng chọn Đặt cọc 20% hoặc Trả thẳng!",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            if (currentCustomerTrustScore < 95 && orderTotal > 500_000L) {
                Toast.makeText(
                    this,
                    "Điểm uy tín Hạng Vàng ($currentCustomerTrustScore/100) hỗ trợ COD tối đa 500.000đ. Đơn này giá trị ${orderTotal.toVndCurrency()}, vui lòng chọn Đặt cọc 20% hoặc Trả thẳng!",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            selectPaymentMode(Order.PaymentMode.COD)
        }

        binding.cardChannelBank.setOnClickListener { selectOnlineChannel("bank") }
        binding.cardChannelMomo.setOnClickListener { selectOnlineChannel("momo") }

        selectPaymentMode(Order.PaymentMode.FULL)

        // Đặt hàng
        binding.btnPlaceOrder.setOnClickListener {
            val streetAddress = binding.etAddress.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val note = binding.etNote.text.toString().trim()

            if (streetAddress.isBlank()) {
                binding.tilAddress.error = "Vui lòng nhập địa chỉ giao hàng"
                binding.nestedScrollView.smoothScrollTo(0, 0)
                binding.etAddress.requestFocus()
                return@setOnClickListener
            }
            binding.tilAddress.error = null

            if (phone.isBlank()) {
                binding.tilPhone.error = "Vui lòng nhập số điện thoại người nhận"
                binding.nestedScrollView.smoothScrollTo(0, 0)
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

            val paymentMethod = when (selectedPaymentMode) {
                Order.PaymentMode.COD -> Order.PaymentMethod.COD
                else -> if (selectedChannel == "momo") Order.PaymentMethod.MOMO else Order.PaymentMethod.BANKING
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
                    binding.layoutVoucherDiscount.visibility = View.VISIBLE
                    binding.tvDiscount.text = "-${currentDiscount.toVndCurrency()}"
                    binding.tvVoucherAppliedMsg.visibility = View.VISIBLE
                    binding.tvVoucherAppliedMsg.text = "⭐ Điểm Uy Tín ${cust.trustScore}/100: Ưu đãi giảm 5.000 đ"
                    recalculateTotal()
                }

                val orderTotal = maxOf(0L, currentSubtotal + currentShippingFee - currentDiscount)
                binding.tvTrustScoreTitle.text = "Điểm Uy Tín: ${cust.trustScore}/100 (${cust.tierBadge})"

                if (cust.trustScore >= 80) {
                    // Hạng Kim Cương hoặc Hạng Vàng: Mở khóa hoàn toàn COD
                    binding.layoutPayCod.isEnabled = true
                    binding.layoutPayCod.alpha = 1.0f
                    binding.tvCodTrustPill.text = "Điểm uy tín: ${cust.trustScore}/100 (${cust.tierBadge}) • Đủ điều kiện nhận hàng trả sau (COD)"
                    binding.tvCodTrustPill.setTextColor(Color.parseColor("#059669"))
                    binding.tvCodTrustPill.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0)
                    binding.tvCodTrustPill.compoundDrawableTintList = ColorStateList.valueOf(Color.parseColor("#059669"))
                } else if (cust.trustScore >= 50) {
                    // Hạng Bạc (50 - 79 điểm): Giới hạn COD 150.000đ
                    if (orderTotal <= 150_000L) {
                        binding.layoutPayCod.isEnabled = true
                        binding.layoutPayCod.alpha = 1.0f
                        binding.tvCodTrustPill.text = "Điểm uy tín Hạng Bạc (${cust.trustScore}/100) • Đơn dưới 150k được thanh toán COD"
                        binding.tvCodTrustPill.setTextColor(Color.parseColor("#D97706"))
                        binding.tvCodTrustPill.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_shield, 0, 0, 0)
                        binding.tvCodTrustPill.compoundDrawableTintList = ColorStateList.valueOf(Color.parseColor("#D97706"))
                    } else {
                        binding.layoutPayCod.isEnabled = false
                        binding.layoutPayCod.alpha = 0.55f
                        binding.tvCodTrustPill.text = "Hạng Bạc (${cust.trustScore}/100): Đơn > 150k vui lòng chọn Đặt cọc 20% hoặc Trả thẳng!"
                        binding.tvCodTrustPill.setTextColor(Color.parseColor("#DC2626"))
                        binding.tvCodTrustPill.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_alert_triangle, 0, 0, 0)
                        binding.tvCodTrustPill.compoundDrawableTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))
                        if (selectedPaymentMode == Order.PaymentMode.COD) {
                            selectPaymentMode(Order.PaymentMode.DEPOSIT)
                        }
                    }
                } else {
                    // Dưới 50 điểm: Khóa hoàn toàn COD
                    binding.layoutPayCod.isEnabled = false
                    binding.layoutPayCod.alpha = 0.55f
                    binding.tvCodTrustPill.text = "Điểm uy tín thấp (${cust.trustScore}/100 < 50): Đã khóa COD. Vui lòng chọn Đặt cọc 20% hoặc Trả thẳng!"
                    binding.tvCodTrustPill.setTextColor(Color.parseColor("#DC2626"))
                    binding.tvCodTrustPill.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock, 0, 0, 0)
                    binding.tvCodTrustPill.compoundDrawableTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))
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
                        selectedCustomerLat = location.latitude
                        selectedCustomerLng = location.longitude

                        val fullAddr = GoogleMapUtils.reverseGeocode(
                            this@CheckoutActivity,
                            location.latitude,
                            location.longitude
                        )

                        val matchedDistrict = GoogleMapUtils.normalizeDistrictName(fullAddr)

                        val street = fullAddr
                            .replace(", TP. Hồ Chí Minh", "")
                            .replace(", TP.HCM", "")
                            .replace(Regex(",\\s*$matchedDistrict.*", RegexOption.IGNORE_CASE), "")
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
        // Làm sạch "(Nội thành)", "(Ngoại thành)" khỏi tên quận trước khi geocode và tính phí
        val cleanDistrict = selectedDistrict.replace(Regex("\\s*\\(.*?\\)"), "").trim()
        val fullAddress = if (streetAddress.isNotBlank()) "$streetAddress, $cleanDistrict, TP.HCM" else "$cleanDistrict, TP.HCM"

        lifecycleScope.launch {
            val storeId = intent.getStringExtra(Constants.EXTRA_SHOP_ID) ?: currentTargetShopId
            val store = currentStore ?: if (storeId.isNotBlank()) firebaseRepository.getStoreOnce(storeId) else null
            val storeLatLng = if (store != null && store.latitude != 0.0 && store.longitude != 0.0) {
                com.google.android.gms.maps.model.LatLng(store.latitude, store.longitude)
            } else if (store != null && store.address.isNotBlank()) {
                GoogleMapUtils.geocodeAddress(this@CheckoutActivity, "${store.address}, TP.HCM")
            } else if (store != null && store.district.isNotBlank()) {
                // Fallback: dùng toạ độ chuẩn của quận store thay vì DEFAULT (Q.1)
                GoogleMapUtils.DISTRICT_COORDINATES[GoogleMapUtils.normalizeDistrictName(store.district)]
                    ?: GoogleMapUtils.DEFAULT_HCMC_LATLNG
            } else {
                GoogleMapUtils.DEFAULT_HCMC_LATLNG
            }
            val customerLatLng = if (selectedCustomerLat != 0.0 && selectedCustomerLng != 0.0) {
                com.google.android.gms.maps.model.LatLng(selectedCustomerLat, selectedCustomerLng)
            } else {
                GoogleMapUtils.geocodeAddress(this@CheckoutActivity, fullAddress)
            }
            var distanceKm = GoogleMapUtils.calculateDistanceKm(storeLatLng, customerLatLng)
            val storeDistrict = store?.district?.takeIf { it.isNotBlank() }
                ?: GoogleMapUtils.normalizeDistrictName(store?.address.orEmpty()).takeIf { it != store?.address.orEmpty() }
                ?: GoogleMapUtils.findNearestDistrict(storeLatLng.latitude, storeLatLng.longitude)

            val normStoreDistrict = GoogleMapUtils.normalizeDistrictName(storeDistrict)
            val normCustomerDistrict = GoogleMapUtils.normalizeDistrictName(cleanDistrict)
            val isSame = normStoreDistrict.isNotBlank() && normCustomerDistrict.isNotBlank() && normStoreDistrict.equals(normCustomerDistrict, ignoreCase = true)

            // Nếu cùng quận mà khách chỉ chọn quận trên spinner (chưa ghim toạ độ cụ thể ngoài đời) hoặc toạ độ geocode bị lệch tâm quận
            if (isSame && (selectedCustomerLat == 0.0 || distanceKm > 3.0f)) {
                distanceKm = minOf(distanceKm, 1.2f)
            }

            currentShippingFee = GoogleMapUtils.calculateShippingFee(distanceKm, normStoreDistrict, normCustomerDistrict)
            val distanceStr = String.format(Locale.US, "%.1f", distanceKm)

            // DỰ ĐOÁN THỜI GIAN GIAO HÀNG (ESTIMATED DELIVERY TIME)
            // Công thức: 15 phút quán chuẩn bị + 3.5 phút/km shipper di chuyển
            val estMinutes = (15 + (distanceKm * 3.5).toInt()).coerceIn(20, 50)
            val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, estMinutes) }
            val etaTimeStr = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)

            val feeDesc = when {
                currentShippingFee == 0L -> "0 đ (Miễn phí ship)"
                currentShippingFee <= 10000L -> "${currentShippingFee.toVndCurrency()} (Rẻ - Cùng quận / Kế bên)"
                currentShippingFee <= 14000L -> "${currentShippingFee.toVndCurrency()} (Quận lân cận)"
                else -> "${currentShippingFee.toVndCurrency()} (Cự ly xa)"
            }

            binding.tvShipRule.text = "🛵 Khoảng cách: ~$distanceStr km • Cước phí: $feeDesc\n⏱️ Dự kiến giao lúc: $etaTimeStr (khoảng $estMinutes phút)"
            recalculateTotal()
        }
    }

    private fun recalculateTotal() {
        binding.tvShippingFee.text = if (currentShippingFee == 0L) "Miễn phí" else currentShippingFee.toVndCurrency()
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

                binding.lblBottomTitle.text = "Tổng thanh toán:"
                binding.tvBottomTotal.text = finalTotal.toVndCurrency()
                binding.lblBottomSubNotice.visibility = View.GONE
                binding.btnPlaceOrder.text = "Thanh toán ngay ➔"
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

                binding.lblBottomTitle.text = "Tiền cọc (20%):"
                binding.tvBottomTotal.text = depositAmount.toVndCurrency()
                binding.lblBottomSubNotice.visibility = View.VISIBLE
                binding.lblBottomSubNotice.text = "💡 Còn lại ${remainAmount.toVndCurrency()} thu khi nhận hàng"
                binding.btnPlaceOrder.text = "Thanh toán cọc ➔"
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

                binding.lblBottomTitle.text = "Cần trả trước:"
                binding.tvBottomTotal.text = "0 đ"
                binding.lblBottomSubNotice.visibility = View.VISIBLE
                binding.lblBottomSubNotice.text = "💡 Shipper thu ${finalTotal.toVndCurrency()} khi giao món"
                binding.btnPlaceOrder.text = "Đặt hàng COD ➔"
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
                listenStoreVouchers(currentTargetShopId)
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
                        val isMomo = (selectedChannel == "momo")
                        val intent = Intent(this, com.chuyen_de_2.foodorder.customer.ui.payment.PaymentActivity::class.java).apply {
                            putExtra(Constants.EXTRA_ORDER_ID, orderId)
                            putExtra(Constants.EXTRA_SHOP_ID, currentTargetShopId)
                            putExtra(Constants.EXTRA_SHOP_NAME, targetShopName)
                            putExtra("extra_address", fullAddress)
                            putExtra("extra_amount", depositAmount)
                            putExtra("extra_total", finalTotal)
                            putExtra("extra_subtotal", currentSubtotal)
                            putExtra("extra_shipping_fee", currentShippingFee)
                            putExtra("extra_discount", currentDiscount)
                            putExtra("extra_payment_mode", Order.PaymentMode.DEPOSIT)
                            putExtra("extra_deposit", depositAmount)
                            putExtra("extra_remain", remainAmount)
                            putExtra("extra_channel", if (isMomo) "momo" else "bank")
                            putExtra("extra_shop_phone", currentStore?.momoPhone?.ifBlank { currentStore?.phone }.orEmpty())
                            putExtra("extra_momo_holder", currentStore?.momoAccountName?.ifBlank { currentStore?.bankAccountName?.ifBlank { currentStore?.ownerName } }.orEmpty())
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
                        val isMomo = (selectedChannel == "momo")
                        val intent = Intent(this, com.chuyen_de_2.foodorder.customer.ui.payment.PaymentActivity::class.java).apply {
                            putExtra(Constants.EXTRA_ORDER_ID, orderId)
                            putExtra(Constants.EXTRA_SHOP_ID, currentTargetShopId)
                            putExtra(Constants.EXTRA_SHOP_NAME, targetShopName)
                            putExtra("extra_address", fullAddress)
                            putExtra("extra_amount", finalTotal)
                            putExtra("extra_total", finalTotal)
                            putExtra("extra_subtotal", currentSubtotal)
                            putExtra("extra_shipping_fee", currentShippingFee)
                            putExtra("extra_discount", currentDiscount)
                            putExtra("extra_payment_mode", Order.PaymentMode.FULL)
                            putExtra("extra_deposit", 0L)
                            putExtra("extra_remain", 0L)
                            putExtra("extra_channel", if (isMomo) "momo" else "bank")
                            putExtra("extra_shop_phone", currentStore?.momoPhone?.ifBlank { currentStore?.phone }.orEmpty())
                            putExtra("extra_momo_holder", currentStore?.momoAccountName?.ifBlank { currentStore?.bankAccountName?.ifBlank { currentStore?.ownerName } }.orEmpty())
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

    /** Lắng nghe thông tin tài khoản ngân hàng / ví MoMo của quán để hiển thị cho khách xem trước */
    private fun observeShopInfo(shopId: String) {
        if (shopId.isBlank()) return
        lifecycleScope.launch {
            firebaseRepository.observeStore(shopId).collectLatest { store ->
                if (store != null) {
                    currentStore = store
                    updatePaymentPreviewBox(isMomo = binding.rbMomo.isChecked)
                    updateLocationAndFee()
                }
            }
        }
    }

    private fun updatePaymentPreviewBox(isMomo: Boolean) {
        val store = currentStore
        if (isMomo) {
            binding.tvBankingTitle.text = "THÔNG TIN TÀI KHOẢN VÍ MOMO"
            binding.tvBankingTitle.setTextColor(android.graphics.Color.parseColor("#A50064"))
            val momoPhone = store?.momoPhone?.ifBlank { store.phone.ifBlank { store.bankAccountNumber.ifBlank { "0988 888 888" } } } ?: "0988 888 888"
            val momoOwner = store?.momoAccountName?.ifBlank { store.bankAccountName.ifBlank { store.ownerName.ifBlank { store.name } } } ?: "OISHI FOOD VIETNAM"
            binding.tvBankingDetails.text = "• Ví điện tử: MoMo (Chuyển tiền 24/7)\n• Số điện thoại Ví: $momoPhone\n• Chủ Ví: $momoOwner\n• Nội dung chuyển: OISHI [Mã đơn hàng]"
        } else {
            binding.tvBankingTitle.text = "THÔNG TIN TÀI KHOẢN NGÂN HÀNG"
            binding.tvBankingTitle.setTextColor(android.graphics.Color.parseColor("#0284C7"))
            val bankDisplay = store?.bankName?.ifBlank { "MB Bank (Quân Đội)" } ?: "MB Bank (Quân Đội)"
            val accNum = store?.bankAccountNumber?.ifBlank { "0988 888 888" } ?: "0988 888 888"
            val ownerDisplay = store?.bankAccountName?.ifBlank { store.name } ?: "OISHI FOOD VIETNAM"
            binding.tvBankingDetails.text = "• Ngân hàng: $bankDisplay\n• Số tài khoản: $accNum\n• Chủ tài khoản: $ownerDisplay\n• Cú pháp CK: OISHI [Mã đơn hàng]"
        }
    }

    private fun selectOnlineChannel(channel: String) {
        selectedChannel = channel
        val isMomo = (channel == "momo")
        binding.rbBanking.isChecked = !isMomo
        binding.rbMomo.isChecked = isMomo
        binding.cardChannelBank.setBackgroundResource(if (!isMomo) com.chuyen_de_2.foodorder.customer.R.drawable.bg_payment_card_selected else com.chuyen_de_2.foodorder.customer.R.drawable.bg_payment_card_normal)
        binding.cardChannelMomo.setBackgroundResource(if (isMomo) com.chuyen_de_2.foodorder.customer.R.drawable.bg_payment_card_selected else com.chuyen_de_2.foodorder.customer.R.drawable.bg_payment_card_normal)
        binding.ivCheckBank.visibility = if (!isMomo) View.VISIBLE else View.GONE
        binding.ivCheckMomo.visibility = if (isMomo) View.VISIBLE else View.GONE
        updatePaymentPreviewBox(isMomo)
    }

    // =========================================================================
    // CHỨC NĂNG: ÁP DỤNG MÃ GIẢM GIÁ / VOUCHER (ĐỒNG BỘ TỪ QUÁN ĂN VÀ HỆ THỐNG)
    // Mục đích: Khách hàng áp dụng mã giảm giá do chính Quán ăn tạo trên app-internal
    // hoặc các mã toàn sàn (OISHI10, FREESHIP, OISHI20, GIAM50K)
    // =========================================================================
    private val storeVouchers = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.Voucher>()
    private var vouchersListener: com.google.firebase.database.ValueEventListener? = null
    private var listeningShopId: String = ""

    private fun listenStoreVouchers(shopId: String) {
        if (shopId.isBlank() || shopId == listeningShopId) return
        listeningShopId = shopId
        vouchersListener?.let {
            FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL)
                .getReference("vouchers")
                .removeEventListener(it)
        }
        val ref = FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL)
            .getReference("vouchers")
            .child(shopId)
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                storeVouchers.clear()
                for (child in snapshot.children) {
                    val v = child.getValue(com.chuyen_de_2.foodorder.core.data.model.Voucher::class.java)
                    if (v != null && v.isActive) {
                        storeVouchers.add(v)
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        vouchersListener = listener
        ref.addValueEventListener(listener)
    }

    private fun setupVoucherComponent() {
        // Lắng nghe danh sách mã giảm giá do Quán này tạo trên Firebase Realtime Database
        if (currentTargetShopId.isNotBlank()) {
            listenStoreVouchers(currentTargetShopId)
        }

        fun applyCode(rawCode: String) {
            val code = rawCode.trim().uppercase()
            if (code.isBlank()) {
                Toast.makeText(this, "Vui lòng nhập mã giảm giá", Toast.LENGTH_SHORT).show()
                return
            }

            var discountAmt = 0L
            var msg = ""

            // 1. ƯU TIÊN KIỂM TRA MÃ CỦA QUÁN ĂN NÀY ĐÃ TẠO TRÊN APP-INTERNAL
            val matchedStoreVoucher = storeVouchers.firstOrNull { it.code.equals(code, ignoreCase = true) }
            if (matchedStoreVoucher != null) {
                if (currentSubtotal < matchedStoreVoucher.minOrderAmount) {
                    Toast.makeText(
                        this,
                        "Mã ${matchedStoreVoucher.code} của Quán chỉ áp dụng cho đơn từ ${matchedStoreVoucher.minOrderAmount.toVndCurrency()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                discountAmt = matchedStoreVoucher.discountAmount
                msg = "Áp dụng mã Quán [${matchedStoreVoucher.code}]: Giảm ${discountAmt.toVndCurrency()}"
            } else {
                // 2. NẾU KHÔNG CÓ TRÊN QUÁN, KIỂM TRA MÃ TOÀN SÀN HỆ THỐNG
                when (code) {
                    "OISHI10" -> {
                        discountAmt = 10000L
                        msg = "Áp dụng mã OISHI10: Giảm 10.000 đ"
                    }
                    "FREESHIP" -> {
                        discountAmt = if (currentShippingFee > 0) currentShippingFee else 15000L
                        msg = "Áp dụng mã FREESHIP: Miễn phí vận chuyển"
                    }
                    "OISHI20" -> {
                        if (currentSubtotal < 50000L) {
                            Toast.makeText(this, "Mã OISHI20 chỉ áp dụng cho đơn từ 50.000 đ", Toast.LENGTH_SHORT).show()
                            return
                        }
                        discountAmt = 20000L
                        msg = "Áp dụng mã OISHI20: Giảm 20.000 đ"
                    }
                    "GIAM50K" -> {
                        if (currentSubtotal < 100000L) {
                            Toast.makeText(this, "Mã GIAM50K chỉ áp dụng cho đơn từ 100.000 đ", Toast.LENGTH_SHORT).show()
                            return
                        }
                        discountAmt = 50000L
                        msg = "Áp dụng mã VIP GIAM50K: Giảm 50.000 đ"
                    }
                    else -> {
                        Toast.makeText(this, "Mã \"$code\" không hợp lệ hoặc quán chưa áp dụng mã này", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }

            // Cập nhật số tiền giảm
            currentDiscount = discountAmt
            binding.etVoucherCode.setText(code)

            // Hiển thị dòng giảm giá trong bảng kê tóm tắt tiền
            binding.layoutVoucherDiscount.visibility = View.VISIBLE
            binding.tvDiscount.text = "-${currentDiscount.toVndCurrency()}"

            // Hiển thị thông báo xanh xác nhận
            binding.tvVoucherAppliedMsg.visibility = View.VISIBLE
            binding.tvVoucherAppliedMsg.text = "✓ $msg (-${currentDiscount.toVndCurrency()})"

            // Tính toán lại toàn bộ đơn hàng
            recalculateTotal()

            Toast.makeText(this, "🎉 $msg!", Toast.LENGTH_SHORT).show()
        }

        // Bấm nút Áp dụng
        binding.btnApplyVoucher.setOnClickListener {
            val code = binding.etVoucherCode.text.toString()
            applyCode(code)
        }

        // Bấm chọn nhanh chip OISHI10
        binding.chipVoucherOishi10.setOnClickListener {
            applyCode("OISHI10")
        }

        // Bấm chọn nhanh chip FREESHIP
        binding.chipVoucherFreeShip.setOnClickListener {
            applyCode("FREESHIP")
        }

        // Bấm chọn nhanh chip OISHI20
        binding.chipVoucherOishi20.setOnClickListener {
            applyCode("OISHI20")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vouchersListener?.let {
            if (listeningShopId.isNotBlank()) {
                FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL)
                    .getReference("vouchers")
                    .child(listeningShopId)
                    .removeEventListener(it)
            }
        }
    }
}
