package com.chuyen_de_2.foodorder.customer.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityHomeBinding
import com.chuyen_de_2.foodorder.customer.ui.auth.LoginActivity
import com.chuyen_de_2.foodorder.customer.ui.cart.CartActivity
import com.chuyen_de_2.foodorder.customer.ui.cart.CartViewModel
import com.chuyen_de_2.foodorder.customer.ui.menu.MenuActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.model.User
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val viewModel: HomeViewModel by viewModels()
    private val cartViewModel: CartViewModel by viewModels()
    private lateinit var adapter: RestaurantAdapter

    private var originalList: List<Store> = emptyList()
    private var selectedCategory: String? = null

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Xử lý Window Insets để tránh bị trùng với Status Bar & Navigation Bar
        InsetUtils.setLightStatusBar(this, false)
        InsetUtils.applyStatusBarPadding(binding.headerBar)
        InsetUtils.applyNavigationBarPadding(binding.bottomNav)

        setupHeader()
        setupCategories()
        setupSearch()
        setupRecyclerView()
        setupSwipeRefresh()
        setupFloatingCart()
        setupBottomNav()
        observeViewModel()
        checkUserRoleForBanner()
    }

    private var selectedDistrict: String? = null

    private fun setupHeader() {
        binding.tvViewAll.setOnClickListener {
            selectedCategory = null
            selectedDistrict = null
            binding.tvLocation.text = getString(R.string.location_default)
            filterRestaurants(binding.etSearch.text.toString())
            Toast.makeText(this, "Hiển thị tất cả quán", Toast.LENGTH_SHORT).show()
        }

        // Chọn Quận / Khu vực TP.HCM theo b1.html
        binding.tvLocation.setOnClickListener {
            showDistrictPicker()
        }

        // Banner click
        binding.bannerPromotion.setOnClickListener {
            Toast.makeText(this, "🎉 Bạn đã lưu voucher FREESHIP ĐƠN 0Đ!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDistrictPicker() {
        val districts = arrayOf("Tất cả TP.HCM", "Quận 1", "Quận 3", "Quận 5", "Quận 10", "Quận Bình Thạnh", "Quận Tân Bình", "TP. Thủ Đức")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("📍 Chọn Khu Vực Giao Hàng TP.HCM")
            .setItems(districts) { _, which ->
                val choice = districts[which]
                if (which == 0) {
                    selectedDistrict = null
                    binding.tvLocation.text = "📍 TP.HCM"
                } else {
                    selectedDistrict = choice
                    binding.tvLocation.text = "📍 $choice"
                }
                filterRestaurants(binding.etSearch.text.toString())
                Toast.makeText(this, "Khu vực: $choice", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun setupCategories() {
        binding.catRice.setOnClickListener { selectCategory("Cơm") }
        binding.catMilkTea.setOnClickListener { selectCategory("Trà Sữa") }
        binding.catNoodle.setOnClickListener { selectCategory("Bún") }
        binding.catChicken.setOnClickListener { selectCategory("Gà") }
        binding.catBread.setOnClickListener { selectCategory("Bánh Mì") }
        binding.catPizza.setOnClickListener { selectCategory("Pizza") }
        binding.catCoffee.setOnClickListener { selectCategory("Cà Phê") }
        binding.catDessert.setOnClickListener { selectCategory("Tráng Miệng") }

        binding.cardFlashSale.setOnClickListener {
            selectCategory("Cơm")
            Toast.makeText(this, "⚡ Đang xem các deal Flash Sale sốc!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectCategory(categoryKeyword: String) {
        selectedCategory = if (selectedCategory == categoryKeyword) null else categoryKeyword
        filterRestaurants(binding.etSearch.text.toString())
        val msg = selectedCategory?.let { "Đang lọc: $it" } ?: "Đã bỏ lọc danh mục"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                filterRestaurants(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterRestaurants(query: String) {
        var filtered = originalList

        if (!selectedDistrict.isNullOrBlank()) {
            filtered = filtered.filter {
                it.district.contains(selectedDistrict!!, ignoreCase = true) ||
                it.address.contains(selectedDistrict!!, ignoreCase = true)
            }
        }

        if (!selectedCategory.isNullOrBlank()) {
            filtered = filtered.filter {
                it.category.contains(selectedCategory!!, ignoreCase = true) ||
                it.name.contains(selectedCategory!!, ignoreCase = true)
            }
        }

        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.address.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
            }
        }

        adapter.submitList(filtered)
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupRecyclerView() {
        adapter = RestaurantAdapter { restaurant ->
            val intent = Intent(this, MenuActivity::class.java).apply {
                putExtra(Constants.EXTRA_SHOP_ID, restaurant.id)
                putExtra(Constants.EXTRA_SHOP_NAME, restaurant.name)
            }
            startActivity(intent)
        }

        binding.rvRestaurants.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = this@HomeActivity.adapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadRestaurants()
        }
    }

    private fun setupFloatingCart() {
        binding.btnCart.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    private fun setupBottomNav() {
        binding.tabHome.setOnClickListener {
            // Already home
        }

        binding.tabOrders.setOnClickListener {
            startActivity(Intent(this, com.chuyen_de_2.foodorder.customer.ui.order.OrderHistoryActivity::class.java))
        }

        binding.tabNotifications.setOnClickListener {
            startActivity(Intent(this, com.chuyen_de_2.foodorder.customer.ui.complaint.ComplaintActivity::class.java))
        }

        binding.tabProfile.setOnClickListener {
            startActivity(Intent(this, com.chuyen_de_2.foodorder.customer.ui.profile.ProfileActivity::class.java))
        }
    }

    private fun observeViewModel() {
        viewModel.stores.observe(this) { stores ->
            originalList = stores
            filterRestaurants(binding.etSearch.text.toString())
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }

        // Realtime cart status trên Floating Button (b4.html: 2 quán • 175.000 đ ➔)
        cartViewModel.carts.observe(this) { carts ->
            val totalItems = carts.sumOf { cart -> cart.items.values.sumOf { it.quantity } }
            if (totalItems > 0) {
                binding.btnCart.visibility = View.VISIBLE
                val shopCount = carts.size
                binding.tvCartBadge.text = "$shopCount quán ($totalItems món)"
            } else {
                binding.btnCart.visibility = View.GONE
            }
        }

        cartViewModel.totalAmount.observe(this) { total ->
            binding.tvCartAmount.text = "${total.toVndCurrency()} ➔"
        }
    }

    private fun checkUserRoleForBanner() {
        lifecycleScope.launch {
            val role = authRepository.getUserRole()
            if (role == User.Role.MERCHANT || role == User.Role.MANAGER || role == User.Role.DRIVER || role == User.Role.SHIPPER) {
                binding.bannerMerchantNotice.visibility = View.VISIBLE
                val isShop = role == User.Role.MERCHANT || role == User.Role.MANAGER
                binding.tvMerchantNoticeText.text = if (isShop) {
                    "🏪 Bạn đang đăng nhập tài khoản Quán ăn"
                } else {
                    "🛵 Bạn đang đăng nhập tài khoản Tài xế"
                }
                binding.btnOpenInternalApp.text = if (isShop) "Mở App Quán ➔" else "Mở App Tài Xế ➔"
                binding.btnOpenInternalApp.setOnClickListener {
                    openInternalApp(isShop)
                }
            } else {
                binding.bannerMerchantNotice.visibility = View.GONE
            }
        }
    }

    private fun openInternalApp(isShop: Boolean) {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.chuyen_de_2.foodorder.internal")
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            val targetName = if (isShop) "Cửa Hàng (Quán Ăn)" else "Tài Xế (Shipper)"
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⚠️ Chưa cài đặt Oishi Internal")
                .setMessage(
                    "Ứng dụng Oishi Internal ($targetName) chưa được cài trên thiết bị.\n\n" +
                    "👉 Hướng dẫn chạy trên Android Studio:\n" +
                    "1. Nhìn lên thanh công cụ cạnh nút Run ▶️ màu xanh.\n" +
                    "2. Nhấn vào danh sách module (đang chọn 'app-customer') và đổi sang 'app-internal'.\n" +
                    "3. Bấm Run (▶️) để cài và mở ứng dụng Cửa hàng / Tài xế!"
                )
                .setPositiveButton("Đã hiểu", null)
                .show()
        }
    }
}
