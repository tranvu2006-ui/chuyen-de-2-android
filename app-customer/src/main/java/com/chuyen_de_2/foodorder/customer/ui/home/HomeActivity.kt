package com.chuyen_de_2.foodorder.customer.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityHomeBinding
import com.chuyen_de_2.foodorder.customer.databinding.BottomSheetNearbyStoresBinding
import com.chuyen_de_2.foodorder.customer.ui.auth.LoginActivity
import com.chuyen_de_2.foodorder.customer.ui.cart.CartActivity
import com.chuyen_de_2.foodorder.customer.ui.cart.CartViewModel
import com.chuyen_de_2.foodorder.customer.ui.menu.MenuActivity
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.chuyen_de_2.foodorder.core.util.GoogleMapUtils
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Tên chức năng: Trang Chủ Khám Phá & Đặt Món Ăn (Customer Home Marketplace)
 * Cách hoạt động:
 *  - Hiển thị danh mục món ăn (Cơm, Trà sữa, Bún/Phở, Gà rán, Bánh mì, v.v.).
 *  - Cung cấp danh sách Món ăn Hot bán chạy cuộn ngang và Thực đơn món ăn toàn hệ thống cuộn dọc.
 *  - Tìm kiếm món ăn tức thời theo từ khóa (Instant Search Filter).
 *  - Bộ lọc Quán gần bạn (< 1.5 km) hiển thị qua BottomSheetDialog.
 *  - Nút giỏ hàng nổi (Floating Cart Button) cập nhật số lượng món và tổng tiền theo thời gian thực.
 *  - Thanh điều hướng BottomNavigation: Trang chủ, Đơn hàng, Thông báo, Tài khoản.
 * Luồng hướng tới:
 *  - Bấm vào món ăn / quán ăn -> Chuyển sang màn hình thực đơn chi tiết [MenuActivity].
 *  - Bấm nút Giỏ hàng nổi -> Chuyển sang giỏ hàng [CartActivity].
 *  - Bấm tab Đơn hàng -> Chuyển sang lịch sử đơn [OrderHistoryActivity].
 *  - Bấm tab Thông báo -> Chuyển sang [NotificationActivity].
 *  - Bấm tab Tài khoản -> Chuyển sang [ProfileActivity].
 */
@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val viewModel: HomeViewModel by viewModels()
    private val cartViewModel: CartViewModel by viewModels()

    private lateinit var dishAdapter: HomeDishAdapter
    private lateinit var hotDishAdapter: HotDishAdapter
    private lateinit var categoryAdapter: HomeCategoryAdapter

    private var selectedCategory: String? = null
    private var selectedPriceLabel: String? = null
    private var isNearbyFiltered: Boolean = false
    private var selectedNearbyDistanceKm: Double = 3.0
    private var selectedNearbyDistanceLabel: String = "< 3 km"
    private var currentLocationName: String = "Đang xác định vị trí..."

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            requestFreshGpsLocation()
        }
    }

    @Inject
    lateinit var authRepository: AuthRepository

    /**
     * Tên chức năng: Khởi tạo Trang chủ Khách hàng
     * Cách hoạt động: Cấu hình Insets Edge-to-Edge, khởi tạo các thành phần giao diện, adapter, bộ lọc và lắng nghe ViewModel.
     * Luồng hướng tới: Chuẩn bị sàn thương mại món ăn cho khách đặt.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, false)
        InsetUtils.applyStatusBarPadding(binding.headerBar, 8)
        InsetUtils.applyNavigationBarPadding(binding.bottomNav)

        setupHeader()
        setupCategories()
        setupPriceFilter()
        setupHotDishes()
        setupSearch()
        setupDishRecyclerView()
        setupSwipeRefresh()
        setupFloatingCart()
        setupBottomNav()
        observeViewModel()
        checkUserRoleForBanner()
        detectUserLocation()
    }

    override fun onResume() {
        super.onResume()
        checkAccountLock()
    }

    /**
     * Tên chức năng: Kiểm tra trạng thái tài khoản khi người dùng quay lại màn hình
     * Cách hoạt động: Kiểm tra cờ `isActive` trên Firestore; nếu bị khóa, tự động đăng xuất và thông báo lỗi.
     * Luồng hướng tới: Điều hướng về [LoginActivity] nếu tài khoản bị khóa.
     */
    private fun checkAccountLock() {
        lifecycleScope.launch {
            val statusCheck = authRepository.checkAccountActiveStatus()
            if (statusCheck.isFailure) {
                val reason = statusCheck.exceptionOrNull()?.message ?: "Tài khoản của bạn đã bị khóa bởi Quản trị viên."
                androidx.appcompat.app.AlertDialog.Builder(this@HomeActivity)
                    .setTitle("🔒 Tài khoản bị khóa")
                    .setMessage(reason)
                    .setPositiveButton("Đóng") { _, _ ->
                        val intent = Intent(this@HomeActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("LOGOUT", true)
                        }
                        startActivity(intent)
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    /**
     * Tên chức năng: Thiết lập thanh tiêu đề Header và bộ lọc quán gần
     * Cách hoạt động: Bắt sự kiện Click nút "Quán gần đây", chọn địa chỉ, nút "Xem tất cả" và "Xóa lọc".
     * Luồng hướng tới: Gọi [showNearbyStoresBottomSheet] hoặc reset bộ lọc.
     */
    private fun setupHeader() {
        binding.btnNearbyStores.setOnClickListener {
            showNearbyStoresBottomSheet()
        }

        binding.tvLocation.setOnClickListener {
            showNearbyStoresBottomSheet()
        }

        binding.tvViewAll.setOnClickListener {
            selectedCategory = null
            selectedPriceLabel = null
            isNearbyFiltered = false
            selectedNearbyDistanceKm = 3.0
            selectedNearbyDistanceLabel = "< 3 km"
            clearCategoryHighlight()
            binding.chipPriceAll.isChecked = true
            updateFilterIndicator()
            binding.tvSectionTitle.text = "Món ngon đề xuất cho bạn"
            viewModel.filterDishes(query = "", category = "ALL", nearbyOnly = false, maxDistanceKm = 3.0, resetPrice = true)
            binding.etSearch.setText("")
            Toast.makeText(this, "Hiển thị tất cả món ăn", Toast.LENGTH_SHORT).show()
        }

        binding.btnClearFilter.setOnClickListener {
            selectedCategory = null
            selectedPriceLabel = null
            isNearbyFiltered = false
            selectedNearbyDistanceKm = 3.0
            selectedNearbyDistanceLabel = "< 3 km"
            clearCategoryHighlight()
            binding.chipPriceAll.isChecked = true
            updateFilterIndicator()
            binding.tvSectionTitle.text = "Món ngon đề xuất cho bạn"
            viewModel.filterDishes(category = "ALL", nearbyOnly = false, maxDistanceKm = 3.0, resetPrice = true)
        }

        binding.layoutActiveFilterIndicator.setOnClickListener {
            showNearbyStoresBottomSheet()
        }
    }

    /**
     * Tên chức năng: Phát hiện vị trí người dùng (từ địa chỉ đã lưu hoặc GPS)
     */
    private fun detectUserLocation() {
        lifecycleScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (!uid.isNullOrBlank()) {
                    val userSnap = FirebaseDatabase.getInstance().getReference("users").child(uid).get().await()
                    val addr = userSnap.child("address").getValue(String::class.java).orEmpty().trim()
                    if (addr.isNotBlank()) {
                        currentLocationName = addr
                        val shortDistrict = addr.split(",")
                            .map { it.trim() }
                            .firstOrNull { it.contains("Quận", true) || it.contains("Thủ Đức", true) || it.contains("Bình Thạnh", true) || it.contains("Gò Vấp", true) || it.contains("Tân Bình", true) || it.contains("Phú Nhuận", true) }
                            ?: "TP.HCM"
                        binding.tvLocation.text = "📍 $shortDistrict ▾"

                        val coords = GoogleMapUtils.geocodeAddress(this@HomeActivity, addr)
                        if (coords.latitude != 0.0 && coords.longitude != 0.0) {
                            viewModel.setUserLocation(coords.latitude, coords.longitude)
                        }
                    }
                }
            } catch (_: Exception) {}

            checkGpsPermissionAndFetch()
        }
    }

    private fun checkGpsPermissionAndFetch(onComplete: (() -> Unit)? = null) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            requestFreshGpsLocation(onComplete)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshGpsLocation(onComplete: (() -> Unit)? = null) {
        try {
            val fused = LocationServices.getFusedLocationProviderClient(this)
            val priority = Priority.PRIORITY_HIGH_ACCURACY
            fused.getCurrentLocation(priority, null).addOnSuccessListener { loc ->
                if (loc != null) {
                    onGpsLocationUpdated(loc.latitude, loc.longitude, onComplete)
                } else {
                    fused.lastLocation.addOnSuccessListener { fallbackLoc ->
                        if (fallbackLoc != null) {
                            onGpsLocationUpdated(fallbackLoc.latitude, fallbackLoc.longitude, onComplete)
                        } else {
                            onComplete?.invoke()
                        }
                    }
                }
            }.addOnFailureListener {
                fused.lastLocation.addOnSuccessListener { fallbackLoc ->
                    if (fallbackLoc != null) {
                        onGpsLocationUpdated(fallbackLoc.latitude, fallbackLoc.longitude, onComplete)
                    } else {
                        onComplete?.invoke()
                    }
                }
            }
        } catch (_: SecurityException) {
            onComplete?.invoke()
        }
    }

    private fun onGpsLocationUpdated(lat: Double, lng: Double, onComplete: (() -> Unit)? = null) {
        viewModel.setUserLocation(lat, lng)
        lifecycleScope.launch {
            val nearestDistrict = GoogleMapUtils.findNearestDistrict(lat, lng)
            binding.tvLocation.text = "📍 $nearestDistrict ▾"
            val reverseAddr = GoogleMapUtils.reverseGeocode(this@HomeActivity, lat, lng)
            currentLocationName = if (reverseAddr.isNotBlank() && reverseAddr != "TP. Hồ Chí Minh") {
                reverseAddr
            } else {
                "$nearestDistrict, TP.HCM"
            }
            onComplete?.invoke()
        }
    }

    /**
     * Tên chức năng: Hiển thị Bottom Sheet Modal danh sách Quán Ăn Gần Bạn Nhất
     * Cách hoạt động: Mở bảng trượt dưới lên hiển thị danh sách quán ăn theo bán kính khoảng cách GPS, hỗ trợ lọc món theo quán gần.
     * Luồng hướng tới: Bấm chọn quán -> Mở [MenuActivity] của quán đó.
     */
    private fun showNearbyStoresBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetNearbyStoresBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val updateSheetLocation = {
            sheetBinding.tvSheetCurrentLocation.text = "Vị trí: $currentLocationName"
        }
        updateSheetLocation()

        if (currentLocationName.contains("Đang") || currentLocationName == "TP.HCM") {
            checkGpsPermissionAndFetch {
                updateSheetLocation()
            }
        }

        var currentRadius = selectedNearbyDistanceKm
        var currentRadiusLabel = selectedNearbyDistanceLabel

        val nearbyAdapter = NearbyStoreAdapter { storeItem ->
            dialog.dismiss()
            val intent = Intent(this, MenuActivity::class.java).apply {
                putExtra(Constants.EXTRA_SHOP_ID, storeItem.id)
                putExtra(Constants.EXTRA_SHOP_NAME, storeItem.name)
            }
            startActivity(intent)
        }

        sheetBinding.rvNearbyStores.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = nearbyAdapter
        }

        fun filterAndDisplayStores(allStores: List<NearbyStoreItem>) {
            val filteredStores = if (currentRadius >= 999.0) {
                allStores
            } else {
                allStores.filter { it.distanceKm in 0.001..currentRadius }
            }
            nearbyAdapter.submitList(filteredStores)

            if (filteredStores.isEmpty()) {
                sheetBinding.tvEmptyNearby.text = "Không có quán nào trong phạm vi $currentRadiusLabel.\nHãy thử chọn bán kính 5 km hoặc 10 km!"
                sheetBinding.tvEmptyNearby.visibility = View.VISIBLE
                sheetBinding.tvNearbyStoreCountSummary.visibility = View.GONE
            } else {
                sheetBinding.tvEmptyNearby.visibility = View.GONE
                sheetBinding.tvNearbyStoreCountSummary.text = "Tìm thấy ${filteredStores.size} quán trong bán kính $currentRadiusLabel"
                sheetBinding.tvNearbyStoreCountSummary.visibility = View.VISIBLE
            }

            sheetBinding.btnFilterNearbyOnly.text = if (currentRadius >= 999.0) {
                "Áp Dụng Lọc Món Quán Gần (Tất cả)"
            } else {
                "Áp Dụng Lọc Món Quán Gần ($currentRadiusLabel)"
            }
        }

        // Chọn sẵn Chip khoảng cách hiện tại
        when (currentRadius) {
            1.5 -> sheetBinding.chipDist1.isChecked = true
            3.0 -> sheetBinding.chipDist3.isChecked = true
            5.0 -> sheetBinding.chipDist5.isChecked = true
            10.0 -> sheetBinding.chipDist10.isChecked = true
            else -> sheetBinding.chipDistAll.isChecked = true
        }

        filterAndDisplayStores(viewModel.nearbyStores.value ?: emptyList())

        sheetBinding.chipGroupDistance.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipDist1 -> {
                    currentRadius = 1.5
                    currentRadiusLabel = "< 1.5 km"
                }
                R.id.chipDist3 -> {
                    currentRadius = 3.0
                    currentRadiusLabel = "< 3 km"
                }
                R.id.chipDist5 -> {
                    currentRadius = 5.0
                    currentRadiusLabel = "< 5 km"
                }
                R.id.chipDist10 -> {
                    currentRadius = 10.0
                    currentRadiusLabel = "< 10 km"
                }
                R.id.chipDistAll -> {
                    currentRadius = 999.0
                    currentRadiusLabel = "Tất cả"
                }
            }
            filterAndDisplayStores(viewModel.nearbyStores.value ?: emptyList())
        }

        // Quan sát Realtime để cập nhật khi dữ liệu quán tải xong
        val nearbyObserver = androidx.lifecycle.Observer<List<NearbyStoreItem>> { updatedStores ->
            filterAndDisplayStores(updatedStores)
        }
        viewModel.nearbyStores.observe(this, nearbyObserver)
        dialog.setOnDismissListener {
            viewModel.nearbyStores.removeObserver(nearbyObserver)
        }

        sheetBinding.btnCloseSheet.setOnClickListener {
            dialog.dismiss()
        }

        sheetBinding.btnFilterNearbyOnly.setOnClickListener {
            dialog.dismiss()
            selectedNearbyDistanceKm = currentRadius
            selectedNearbyDistanceLabel = currentRadiusLabel
            isNearbyFiltered = true
            updateFilterIndicator()
            viewModel.filterDishes(nearbyOnly = true, maxDistanceKm = selectedNearbyDistanceKm)
        }

        dialog.show()
    }

    /**
     * Tên chức năng: Thiết lập RecyclerView danh sách Món Ăn chính
     * Cách hoạt động: Khởi tạo [HomeDishAdapter] và xử lý callback mở thực đơn quán chứa món ăn đó.
     * Luồng hướng tới: Điều hướng sang [MenuActivity].
     */
    private fun setupDishRecyclerView() {
        dishAdapter = HomeDishAdapter(
            onDishClick = { dish ->
                val intent = Intent(this, MenuActivity::class.java).apply {
                    putExtra(Constants.EXTRA_SHOP_ID, dish.storeId)
                    putExtra(Constants.EXTRA_SHOP_NAME, dish.storeName.removePrefix("🏪 ").trim())
                    putExtra(Constants.EXTRA_DISH_ID, dish.id)
                }
                startActivity(intent)
            },
            onAddClick = { dish ->
                val intent = Intent(this, MenuActivity::class.java).apply {
                    putExtra(Constants.EXTRA_SHOP_ID, dish.storeId)
                    putExtra(Constants.EXTRA_SHOP_NAME, dish.storeName.removePrefix("🏪 ").trim())
                    putExtra(Constants.EXTRA_DISH_ID, dish.id)
                }
                startActivity(intent)
            }
        )

        binding.rvDishes.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = dishAdapter
        }
    }

    /**
     * Tên chức năng: Cấu hình danh sách Món Ăn Hot (Thịnh hành)
     * Cách hoạt động: Gắn [HotDishAdapter] với chiều ngang (HORIZONTAL).
     * Luồng hướng tới: Chọn món hot -> Mở [MenuActivity].
     */
    private fun setupHotDishes() {
        hotDishAdapter = HotDishAdapter { hotDish ->
            val intent = Intent(this, MenuActivity::class.java).apply {
                putExtra(Constants.EXTRA_SHOP_ID, hotDish.storeId)
                putExtra(Constants.EXTRA_SHOP_NAME, hotDish.storeName.removePrefix("🏪 ").trim())
                putExtra(Constants.EXTRA_DISH_ID, hotDish.id)
            }
            startActivity(intent)
        }

        binding.rvHotDishes.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = hotDishAdapter
        }
    }

    /**
     * Tên chức năng: Thiết lập các nút chọn Danh mục món ăn
     * Cách hoạt động: Gán sự kiện click cho từng danh mục: Cơm, Trà sữa, Bún/Phở, Gà rán, Bánh mì, Pizza, Cà phê, Tráng miệng.
     * Luồng hướng tới: Gọi [onCategorySelected].
     */
    private fun setupCategories() {
        categoryAdapter = HomeCategoryAdapter { category ->
            if (category == null) {
                selectedCategory = null
                viewModel.filterDishes(category = "ALL")
                binding.tvSectionTitle.text = "Món ngon đề xuất cho bạn"
                updateFilterIndicator()
                Toast.makeText(this, "Hiển thị tất cả món ăn", Toast.LENGTH_SHORT).show()
            } else {
                selectedCategory = category.name
                viewModel.filterDishes(category = category.name)
                binding.tvSectionTitle.text = "Món ngon: ${category.name}"
                updateFilterIndicator()
                Toast.makeText(this, "Danh mục: ${category.name}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvHomeCategories.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
    }

    private fun clearCategoryHighlight() {
        if (::categoryAdapter.isInitialized) {
            categoryAdapter.setSelected(null)
        }
    }

    /**
     * Tên chức năng: Thiết lập thanh lọc theo khoảng giá tiền & sắp xếp giá
     * Cách hoạt động: Lắng nghe sự kiện chọn Chip lọc giá, áp dụng lọc lên ViewModel và cập nhật banner trạng thái.
     */
    private fun setupPriceFilter() {
        binding.chipGroupPrice.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipPriceAll
            when (checkedId) {
                R.id.chipPriceUnder30k -> {
                    selectedPriceLabel = "< 30k"
                    viewModel.filterDishes(minPrice = 0L, maxPrice = 30000L, priceSort = HomeViewModel.PriceSort.NONE)
                }
                R.id.chipPrice30kTo60k -> {
                    selectedPriceLabel = "30k - 60k"
                    viewModel.filterDishes(minPrice = 30000L, maxPrice = 60000L, priceSort = HomeViewModel.PriceSort.NONE)
                }
                R.id.chipPrice60kTo100k -> {
                    selectedPriceLabel = "60k - 100k"
                    viewModel.filterDishes(minPrice = 60000L, maxPrice = 100000L, priceSort = HomeViewModel.PriceSort.NONE)
                }
                R.id.chipPriceOver100k -> {
                    selectedPriceLabel = "> 100k"
                    viewModel.filterDishes(minPrice = 100000L, maxPrice = Long.MAX_VALUE, priceSort = HomeViewModel.PriceSort.NONE)
                }
                R.id.chipPriceAsc -> {
                    selectedPriceLabel = "Giá tăng dần ⬆"
                    viewModel.filterDishes(resetPriceRange = true, priceSort = HomeViewModel.PriceSort.ASC)
                }
                R.id.chipPriceDesc -> {
                    selectedPriceLabel = "Giá giảm dần ⬇"
                    viewModel.filterDishes(resetPriceRange = true, priceSort = HomeViewModel.PriceSort.DESC)
                }
                else -> {
                    selectedPriceLabel = null
                    viewModel.filterDishes(resetPrice = true)
                }
            }
            updateFilterIndicator()
        }
    }

    private fun updateFilterIndicator() {
        val activeFilters = mutableListOf<String>()
        if (!selectedCategory.isNullOrBlank()) {
            activeFilters.add(selectedCategory!!)
        }
        if (!selectedPriceLabel.isNullOrBlank()) {
            activeFilters.add("Giá: $selectedPriceLabel")
        }
        if (isNearbyFiltered) {
            activeFilters.add("📍 $selectedNearbyDistanceLabel")
        }

        if (activeFilters.isNotEmpty()) {
            binding.layoutActiveFilterIndicator.visibility = View.VISIBLE
            binding.tvFilterIndicatorSummary.text = "Đang lọc: ${activeFilters.joinToString(" • ")}"
        } else {
            binding.layoutActiveFilterIndicator.visibility = View.GONE
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    /**
     * Tên chức năng: Tìm kiếm món ăn theo từ khóa nhập liệu
     * Cách hoạt động: Lắng nghe TextWatcher trên ô tìm kiếm, gọi [HomeViewModel.filterDishes] cập nhật danh sách hiển thị tức thời.
     * Luồng hướng tới: Lọc kết quả tìm kiếm.
     */
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.filterDishes(query = s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadRestaurants()
        }
    }

    /**
     * Tên chức năng: Nút Giỏ Hàng Nổi (Floating Cart)
     * Cách hoạt động: Hiển thị nổi trên màn hình khi có món trong giỏ, click vào để điều hướng sang màn hình xem giỏ hàng.
     * Luồng hướng tới: [CartActivity].
     */
    private fun setupFloatingCart() {
        binding.btnCart.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    /**
     * Tên chức năng: Cấu hình điều hướng thanh Bottom Navigation
     * Cách hoạt động: Bắt sự kiện chuyển hướng giữa các tab chính: Trang chủ, Đơn hàng, Thông báo, Hồ sơ cá nhân.
     * Luồng hướng tới: [HomeActivity], [OrderHistoryActivity], [NotificationActivity], [ProfileActivity].
     */
    private fun setupBottomNav() {
        binding.tabHome.setOnClickListener {
            // Already home
        }

        binding.tabOrders.setOnClickListener {
            startActivity(Intent(this, com.chuyen_de_2.foodorder.customer.ui.order.OrderHistoryActivity::class.java))
        }

        binding.tabNotifications.setOnClickListener {
            startActivity(Intent(this, com.chuyen_de_2.foodorder.customer.ui.notification.NotificationActivity::class.java))
        }

        binding.tabProfile.setOnClickListener {
            startActivity(Intent(this, com.chuyen_de_2.foodorder.customer.ui.profile.ProfileActivity::class.java))
        }
    }

    /**
     * Tên chức năng: Lắng nghe trạng thái dữ liệu từ HomeViewModel & CartViewModel
     * Cách hoạt động: Cập nhật danh sách món ăn, món hot, trạng thái loading/error, và cập nhật số lượng/tổng tiền hiển thị trên nút Giỏ hàng nổi.
     * Luồng hướng tới: Đồng bộ hiển thị Realtime.
     */
    private fun observeViewModel() {
        viewModel.platformCategories.observe(this) { categories ->
            if (::categoryAdapter.isInitialized) {
                categoryAdapter.submitCategories(categories)
            }
        }

        viewModel.homeDishes.observe(this) { dishes ->
            dishAdapter.submitList(dishes)
            binding.tvDishesCountBadge.text = "(${dishes.size} món)"
            binding.tvEmpty.visibility = if (dishes.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.hotDishes.observe(this) { hotDishes ->
            hotDishAdapter.submitList(hotDishes)
            val hasHot = hotDishes.isNotEmpty()
            binding.layoutHotDishesHeader.visibility = if (hasHot) View.VISIBLE else View.GONE
            binding.rvHotDishes.visibility = if (hasHot) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }

        cartViewModel.carts.observe(this) { carts ->
            val totalItems = carts.sumOf { cart -> cart.items.values.sumOf { it.quantity } }
            if (totalItems > 0) {
                binding.btnCart.visibility = View.VISIBLE
                val shopCount = carts.size
                binding.tvCartBadge.text = if (shopCount > 1) "$shopCount quán" else "$totalItems món"
            } else {
                binding.btnCart.visibility = View.GONE
            }
        }

        cartViewModel.totalAmount.observe(this) { total ->
            if (total != null && total > 0L) {
                binding.tvCartAmount.text = "${total.toVndCurrency()} ➔"
            }
        }
    }

    /**
     * Tên chức năng: Hiển thị Banner nhắc nhở mở App Nội bộ nếu tài khoản là Đối tác
     * Cách hoạt động: Kiểm tra role của user; nếu là Quán hoặc Tài xế, hiển thị banner đầu trang hỗ trợ 1 chạm mở app Oishi Internal.
     * Luồng hướng tới: Mở Package `com.chuyen_de_2.foodorder.internal`.
     */
    private fun checkUserRoleForBanner() {
        lifecycleScope.launch {
            val role = authRepository.getUserRole()?.trim()?.uppercase()
            if (role == User.Role.MERCHANT || role == User.Role.MANAGER || role == "SHOP" || role == "STORE" || role == User.Role.DRIVER || role == User.Role.SHIPPER) {
                binding.bannerMerchantNotice.visibility = View.VISIBLE
                val roleName = if (role == User.Role.DRIVER || role == User.Role.SHIPPER) "Tài Xế (Driver)" else "Cửa Hàng (Merchant)"
                binding.tvMerchantNoticeText.text = "Bạn đang đăng nhập bằng tài khoản $roleName."

                binding.btnOpenInternalApp.setOnClickListener {
                    val launchIntent = packageManager.getLaunchIntentForPackage("com.chuyen_de_2.foodorder.internal")
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    } else {
                        Toast.makeText(this@HomeActivity, "Ứng dụng Quán/Shipper (Oishi Internal) chưa cài đặt!", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                binding.bannerMerchantNotice.visibility = View.GONE
            }
        }
    }
}
