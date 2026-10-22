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

    private var selectedCategory: String? = null
    private var isNearbyFiltered: Boolean = false

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
            isNearbyFiltered = false
            clearCategoryHighlight()
            binding.layoutActiveFilterIndicator.visibility = View.GONE
            binding.tvSectionTitle.text = "Món ngon đề xuất cho bạn"
            viewModel.filterDishes(query = "", category = "ALL", nearbyOnly = false)
            binding.etSearch.setText("")
            Toast.makeText(this, "Hiển thị tất cả món ăn", Toast.LENGTH_SHORT).show()
        }

        binding.btnClearFilter.setOnClickListener {
            selectedCategory = null
            isNearbyFiltered = false
            clearCategoryHighlight()
            binding.layoutActiveFilterIndicator.visibility = View.GONE
            binding.tvSectionTitle.text = "Món ngon đề xuất cho bạn"
            viewModel.filterDishes(category = "ALL", nearbyOnly = false)
        }
    }

    /**
     * Tên chức năng: Hiển thị Bottom Sheet Modal danh sách Quán Ăn Gần Bạn Nhất
     * Cách hoạt động: Mở bảng trượt dưới lên hiển thị danh sách quán ăn theo bán kính khoảng cách GPS, hỗ trợ lọc món theo quán gần (< 1.5 km).
     * Luồng hướng tới: Bấm chọn quán -> Mở [MenuActivity] của quán đó.
     */
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

        sheetBinding.btnCloseSheet.setOnClickListener {
            dialog.dismiss()
        }

        sheetBinding.btnFilterNearbyOnly.setOnClickListener {
            dialog.dismiss()
            isNearbyFiltered = true
            binding.layoutActiveFilterIndicator.visibility = View.VISIBLE
            binding.tvFilterIndicatorSummary.text = "Đang lọc: 📍 Món từ các quán gần (< 1.5 km)"
            viewModel.filterDishes(nearbyOnly = true)
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
        binding.catRice.setOnClickListener { onCategorySelected("Cơm", "Cơm Trưa", binding.catRice) }
        binding.catMilkTea.setOnClickListener { onCategorySelected("Trà", "Trà Sữa", binding.catMilkTea) }
        binding.catNoodle.setOnClickListener { onCategorySelected("Bún", "Bún/Phở", binding.catNoodle) }
        binding.catChicken.setOnClickListener { onCategorySelected("Gà", "Gà Rán", binding.catChicken) }
        binding.catBread.setOnClickListener { onCategorySelected("Bánh mì", "Bánh Mì", binding.catBread) }
        binding.catPizza.setOnClickListener { onCategorySelected("Pizza", "Pizza", binding.catPizza) }
        binding.catCoffee.setOnClickListener { onCategorySelected("Cà phê", "Cà Phê", binding.catCoffee) }
        binding.catDessert.setOnClickListener { onCategorySelected("Tráng miệng", "Ăn Vặt", binding.catDessert) }
    }

    private fun onCategorySelected(keyword: String, displayName: String, view: View) {
        if (selectedCategory == keyword) {
            selectedCategory = null
            clearCategoryHighlight()
            viewModel.filterDishes(category = "ALL")
            binding.tvSectionTitle.text = "Món ngon đề xuất cho bạn"
            if (!isNearbyFiltered) {
                binding.layoutActiveFilterIndicator.visibility = View.GONE
            } else {
                binding.tvFilterIndicatorSummary.text = "Đang lọc: 📍 Các quán gần nhất (< 1.5 km)"
            }
            Toast.makeText(this, "Hiển thị tất cả món ăn", Toast.LENGTH_SHORT).show()
        } else {
            selectedCategory = keyword
            highlightCategory(view)
            viewModel.filterDishes(category = keyword)
            binding.tvSectionTitle.text = "Món ngon: $displayName"
            binding.layoutActiveFilterIndicator.visibility = View.VISIBLE
            val filterText = if (isNearbyFiltered) {
                "Đang lọc: $displayName & 📍 Quán gần (< 1.5 km)"
            } else {
                "Đang lọc danh mục: $displayName"
            }
            binding.tvFilterIndicatorSummary.text = filterText
            Toast.makeText(this, "Danh mục: $displayName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun highlightCategory(selectedView: View) {
        val categoryViews = listOf(
            binding.catRice, binding.catMilkTea, binding.catNoodle, binding.catChicken,
            binding.catBread, binding.catPizza, binding.catCoffee, binding.catDessert
        )
        categoryViews.forEach { v ->
            if (v == selectedView) {
                v.setBackgroundResource(R.drawable.bg_chip_selected)
            } else {
                v.background = null
            }
        }
    }

    private fun clearCategoryHighlight() {
        val categoryViews = listOf(
            binding.catRice, binding.catMilkTea, binding.catNoodle, binding.catChicken,
            binding.catBread, binding.catPizza, binding.catCoffee, binding.catDessert
        )
        categoryViews.forEach { it.background = null }
    }

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
