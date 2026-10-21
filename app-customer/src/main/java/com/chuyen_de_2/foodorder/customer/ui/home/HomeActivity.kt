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
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val viewModel: HomeViewModel by viewModels()
    private val cartViewModel: CartViewModel by viewModels()

    // Adapter danh sách món ăn chính trên trang chủ (theo chuẩn b1.html)
    private lateinit var dishAdapter: HomeDishAdapter
    private lateinit var hotDishAdapter: HotDishAdapter

    private var selectedCategory: String? = null
    private var isNearbyFiltered: Boolean = false

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
        setupHotDishes()
        setupSearch()
        setupDishRecyclerView()
        setupSwipeRefresh()
        setupFloatingCart()
        setupBottomNav()
        observeViewModel()
        checkUserRoleForBanner()
    }

    override fun onResume() {
        super.onResume()
        checkAccountLock()
    }

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

    private fun setupHeader() {
        // Nút '📍 Quán Gần Đây' ở thanh Header (b1.html: btn-nearby-header)
        binding.btnNearbyStores.setOnClickListener {
            showNearbyStoresBottomSheet()
        }

        // Bấm vào địa chỉ cũng mở danh sách Quán Gần Đây
        binding.tvLocation.setOnClickListener {
            showNearbyStoresBottomSheet()
        }

        // Nút 'Xem tất cả' để reset toàn bộ lọc
        binding.tvViewAll.setOnClickListener {
            selectedCategory = null
            isNearbyFiltered = false
            binding.layoutActiveFilterIndicator.visibility = View.GONE
            viewModel.filterDishes(query = "", category = "ALL", nearbyOnly = false)
            binding.etSearch.setText("")
            Toast.makeText(this, "Hiển thị tất cả món ăn", Toast.LENGTH_SHORT).show()
        }

        // Nút 'Xóa lọc' trên indicator banner
        binding.btnClearFilter.setOnClickListener {
            isNearbyFiltered = false
            binding.layoutActiveFilterIndicator.visibility = View.GONE
            viewModel.filterDishes(nearbyOnly = false)
        }
    }

    /** Hiển thị Bottom Sheet Modal: Các Quán Ăn Gần Bạn Nhất (chuẩn b1.html) */
    private fun showNearbyStoresBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetNearbyStoresBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

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

        val stores = viewModel.nearbyStores.value ?: emptyList()
        nearbyAdapter.submitList(stores)

        // Nút đóng sheet [✕]
        sheetBinding.btnCloseSheet.setOnClickListener {
            dialog.dismiss()
        }

        // Nút: Chỉ Lọc Món Các Quán Gần (< 1.5 km)
        sheetBinding.btnFilterNearbyOnly.setOnClickListener {
            dialog.dismiss()
            isNearbyFiltered = true
            binding.layoutActiveFilterIndicator.visibility = View.VISIBLE
            binding.tvFilterIndicatorSummary.text = "Đang lọc: 📍 Món từ các quán gần (< 1.5 km)"
            viewModel.filterDishes(nearbyOnly = true)
        }

        dialog.show()
    }

    /** Thiết lập danh sách Món Ăn chính của trang chủ */
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

    private fun setupCategories() {
        binding.catRice.setOnClickListener { onCategorySelected("Cơm", "Cơm Trưa") }
        binding.catMilkTea.setOnClickListener { onCategorySelected("Trà", "Trà Sữa") }
        binding.catNoodle.setOnClickListener { onCategorySelected("Bún", "Bún/Phở") }
        binding.catChicken.setOnClickListener { onCategorySelected("Gà", "Gà Rán") }
        binding.catBread.setOnClickListener { onCategorySelected("Bánh mì", "Bánh Mì") }
        binding.catPizza.setOnClickListener { onCategorySelected("Pizza", "Pizza") }
        binding.catCoffee.setOnClickListener { onCategorySelected("Cà phê", "Cà Phê") }
        binding.catDessert.setOnClickListener { onCategorySelected("Tráng miệng", "Ăn Vặt") }
    }

    private fun onCategorySelected(keyword: String, displayName: String) {
        if (selectedCategory == keyword) {
            selectedCategory = null
            viewModel.filterDishes(category = "ALL")
            Toast.makeText(this, "Hiển thị tất cả món ăn", Toast.LENGTH_SHORT).show()
        } else {
            selectedCategory = keyword
            viewModel.filterDishes(category = keyword)
            Toast.makeText(this, "Danh mục: $displayName", Toast.LENGTH_SHORT).show()
        }
    }

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
        // Quan sát danh sách Món Ăn chính
        viewModel.homeDishes.observe(this) { dishes ->
            dishAdapter.submitList(dishes)
            binding.tvDishesCountBadge.text = "(${dishes.size} món)"
            binding.tvEmpty.visibility = if (dishes.isEmpty()) View.VISIBLE else View.GONE
        }

        // Quan sát món hot cuộn ngang
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

        // Realtime cart status trên Floating Button
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
            if (total != null && total > 0L) {
                binding.tvCartAmount.text = "${total.toVndCurrency()} ➔"
            }
        }
    }

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
