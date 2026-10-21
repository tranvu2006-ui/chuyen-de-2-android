package com.chuyen_de_2.foodorder.internal.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.internal.ui.auth.StoreVerificationActivity
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.internal.databinding.ActivityDashboardBinding
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.ui.auth.LoginActivity
import com.chuyen_de_2.foodorder.internal.ui.ship.ShipperFragment
import com.chuyen_de_2.foodorder.internal.ui.ship.ShipperIncomeFragment
import com.chuyen_de_2.foodorder.internal.ui.ship.ShipperProfileFragment
import com.chuyen_de_2.foodorder.internal.ui.shop.MenuManageFragment
import com.chuyen_de_2.foodorder.internal.ui.shop.OrderListFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tên chức năng: Bảng điều khiển Trung tâm Phân quyền (Role-Based Dashboard)
 * Cách hoạt động:
 *  - Phân tích quyền tài khoản (RBAC): MERCHANT / MANAGER (Quán ăn) hoặc SHIPPER / DRIVER (Tài xế giao hàng).
 *  - Đối với Quán ăn: Kiểm tra tình trạng xác minh địa chỉ; nếu chưa xong sẽ chuyển hướng sang [StoreVerificationActivity].
 *  - Cung cấp giao diện BottomNavigationView tương ứng riêng biệt cho Quán và Tài xế.
 * Luồng hướng tới:
 *  - MERCHANT: [OrderListFragment] (Quản lý đơn), [MenuManageFragment] (Thực đơn), [DriverListFragment] (Đội ngũ tài xế), [StoreReportFragment] (Báo cáo doanh thu).
 *  - SHIPPER: [ShipperFragment] (Nhận cuốc/GPS), [ShipperIncomeFragment] (Thu nhập/Ví), [ShipperProfileFragment] (Hồ sơ tài xế).
 */
@AndroidEntryPoint
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private var statusBarHeight: Int = 0
    private var navigationBarHeight: Int = 0
    private var isMerchantMode = false

    /**
     * Tên chức năng: Khởi tạo Dashboard và xử lý Insets tràn viền (Edge-to-Edge)
     * Cách hoạt động: Lắng nghe WindowInsets để tính toán khoảng đệm thanh trạng thái và thanh điều hướng ảo, sau đó gọi loadUserRole().
     * Luồng hướng tới: Bắt đầu kiểm tra vai trò người dùng tại [loadUserRole].
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InsetUtils.initEdgeToEdge(this, true)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Tính chiều cao status bar dự phòng ban đầu
        statusBarHeight = InsetUtils.getFallbackStatusBarHeight(this)

        // Lắng nghe Window Insets (Status bar, Tai thỏ / Notch, Navigation bar)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusInsets = windowInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            val navInsets = windowInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.navigationBars()
            )

            if (statusInsets.top > 0) {
                statusBarHeight = statusInsets.top
            }
            if (navInsets.bottom > 0) {
                navigationBarHeight = navInsets.bottom
            }

            applyWindowPadding()
            windowInsets
        }

        setupToolbar()
        loadUserRole()
    }

    /**
     * Tên chức năng: Áp dụng khoảng đệm an toàn màn hình (Window Padding)
     * Cách hoạt động: Thêm padding top cho container và padding bottom cho bottom navigation để không bị che khuất bởi phím điều hướng hệ thống.
     * Luồng hướng tới: Hiển thị nội dung hoàn chỉnh trong vùng an toàn (Safe Area).
     */
    private fun applyWindowPadding() {
        InsetUtils.setLightStatusBar(this, true)
        binding.root.setBackgroundColor(android.graphics.Color.WHITE)
        val density = resources.displayMetrics.density
        val extraSpacing = (10 * density).toInt()
        val topPadding = (if (statusBarHeight > 0) statusBarHeight else InsetUtils.getFallbackStatusBarHeight(this)) + extraSpacing
        binding.fragmentContainer.setPadding(0, topPadding, 0, 0)
        binding.bottomNavMerchant.setPadding(0, 0, 0, navigationBarHeight)
        binding.bottomNavShipper.setPadding(0, 0, 0, navigationBarHeight)
    }

    /**
     * Tên chức năng: Thiết lập thanh công cụ Toolbar và sự kiện Đăng xuất
     * Cách hoạt động: Gắn Toolbar và xử lý hộp thoại xác nhận khi người dùng muốn đăng xuất.
     * Luồng hướng tới: Chuyển về [LoginActivity] và xóa cờ phiên làm việc.
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.btnLogout.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🚪 Đăng Xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất không?")
                .setPositiveButton("Đăng xuất") { _, _ ->
                    authRepository.logout()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("LOGOUT", true)
                    })
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    /**
     * Tên chức năng: Tải và đối soát vai trò tài khoản (Role Dispatcher)
     * Cách hoạt động:
     *  - Truy vấn Firestore lấy role hiện tại của user.
     *  - Nếu là Quán ăn: Kiểm tra xác minh hồ sơ quán.
     *  - Nếu là Tài xế: Thiết lập menu shipper.
     *  - Nếu tài khoản mới chưa có role: Hiển thị Dialog kích hoạt vai trò tương ứng ngay lập tức.
     * Luồng hướng tới: [checkMerchantVerificationAndProceed] hoặc [setupShipperNavigation].
     */
    private fun loadUserRole() {
        lifecycleScope.launch {
            val role = authRepository.getUserRole()?.trim()?.uppercase()

            when (role) {
                User.Role.MANAGER, User.Role.MERCHANT, "SHOP", "STORE" -> {
                    checkMerchantVerificationAndProceed()
                }
                User.Role.SHIPPER, User.Role.DRIVER -> {
                    setupShipperNavigation()
                }
                else -> {
                    // Nếu tài khoản chưa có vai trò đối tác, cho phép người dùng chọn Quán ăn hoặc Tài xế để kích hoạt ngay
                    androidx.appcompat.app.AlertDialog.Builder(this@DashboardActivity)
                        .setTitle("🏪 Kích Hoạt Vai Trò Đối Tác")
                        .setMessage("Tài khoản của bạn hiện tại chưa được kích hoạt vai trò Đối Tác trên ứng dụng Oishi Internal.\n\nBạn muốn sử dụng tài khoản này với vai trò gì?")
                        .setPositiveButton("🏪 Tôi là Quán Ăn") { _, _ ->
                            lifecycleScope.launch {
                                authRepository.setUserRole(User.Role.MERCHANT)
                                Toast.makeText(this@DashboardActivity, "✓ Đã kích hoạt vai trò Quán Ăn thành công!", Toast.LENGTH_SHORT).show()
                                checkMerchantVerificationAndProceed()
                            }
                        }
                        .setNegativeButton("🛵 Tôi là Tài Xế") { _, _ ->
                            lifecycleScope.launch {
                                authRepository.setUserRole(User.Role.DRIVER)
                                Toast.makeText(this@DashboardActivity, "✓ Đã kích hoạt vai trò Tài Xế thành công!", Toast.LENGTH_SHORT).show()
                                setupShipperNavigation()
                            }
                        }
                        .setNeutralButton("🚪 Đăng xuất") { _, _ ->
                            authRepository.logout()
                            startActivity(Intent(this@DashboardActivity, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                putExtra("LOGOUT", true)
                            })
                        }
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }

    /**
     * Tên chức năng: Đối soát hồ sơ quán & xác minh thông tin bắt buộc
     * Cách hoạt động: Kiểm tra tài liệu quán trên Firestore; nếu chưa có địa chỉ hoặc chưa xác minh thì chặn vào Dashboard.
     * Luồng hướng tới: Chưa hoàn tất -> [StoreVerificationActivity]; Đã hoàn tất -> [setupMerchantNavigation].
     */
    private fun checkMerchantVerificationAndProceed() {
        lifecycleScope.launch {
            val uid = authRepository.currentUser?.uid.orEmpty()
            if (uid.isNotBlank()) {
                val storeId = firebaseRepository.resolveStoreIdForUser(uid)
                if (storeId.isNotBlank()) {
                    val store = firebaseRepository.getStoreOnce(storeId)
                    val isComplete = store != null && (store.isVerified || (store.address.isNotBlank() && store.district.isNotBlank() && store.cccdNumber.isNotBlank()))
                    if (store == null || !isComplete || store.address.isBlank()) {
                        Toast.makeText(
                            this@DashboardActivity,
                            "⚠️ Vui lòng hoàn tất phiếu địa chỉ và xác minh thông tin quán trước khi nhận đơn!",
                            Toast.LENGTH_LONG
                        ).show()
                        val intent = Intent(this@DashboardActivity, StoreVerificationActivity::class.java).apply {
                            putExtra("STORE_ID", storeId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                        return@launch
                    }
                }
            }
            setupMerchantNavigation()
        }
    }

    /**
     * Tên chức năng: Thiết lập giao diện và thanh điều hướng cho Tài xế (Shipper Navigation)
     * Cách hoạt động: Ẩn thanh công cụ shop, hiện BottomNav Shipper gồm: Nhận đơn, Thu nhập và Hồ sơ tài xế.
     * Luồng hướng tới: Chuyển đổi giữa [ShipperFragment], [ShipperIncomeFragment], [ShipperProfileFragment].
     */
    private fun setupShipperNavigation() {
        isMerchantMode = false
        binding.toolbar.visibility = android.view.View.GONE
        binding.bottomNavMerchant.visibility = android.view.View.GONE
        binding.bottomNavShipper.visibility = android.view.View.VISIBLE
        applyWindowPadding()
        androidx.core.view.ViewCompat.requestApplyInsets(binding.root)

        binding.bottomNavShipper.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_shipper_orders -> {
                    loadFragment(ShipperFragment())
                    true
                }
                R.id.nav_shipper_income -> {
                    loadFragment(ShipperIncomeFragment())
                    true
                }
                R.id.nav_shipper_profile -> {
                    loadFragment(ShipperProfileFragment())
                    true
                }
                else -> false
            }
        }

        binding.bottomNavShipper.selectedItemId = R.id.nav_shipper_orders
    }

    /**
     * Tên chức năng: Thiết lập giao diện và thanh điều hướng cho Quán ăn (Merchant Navigation)
     * Cách hoạt động: Ẩn thanh công cụ shipper, hiện BottomNav Quán gồm: Đơn hàng, Thực đơn, Tài xế và Doanh thu.
     * Luồng hướng tới: Chuyển đổi giữa [OrderListFragment], [MenuManageFragment], [DriverListFragment], [StoreReportFragment].
     */
    private fun setupMerchantNavigation() {
        isMerchantMode = true
        binding.toolbar.visibility = android.view.View.GONE
        binding.bottomNavMerchant.visibility = android.view.View.VISIBLE
        applyWindowPadding()
        androidx.core.view.ViewCompat.requestApplyInsets(binding.root)

        binding.bottomNavMerchant.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_orders -> {
                    loadFragment(OrderListFragment())
                    true
                }
                R.id.nav_menu -> {
                    val menuFragment = MenuManageFragment().apply {
                        onNavigateToOrdersListener = {
                            binding.bottomNavMerchant.selectedItemId = R.id.nav_orders
                        }
                    }
                    loadFragment(menuFragment)
                    true
                }
                R.id.nav_drivers -> {
                    loadFragment(com.chuyen_de_2.foodorder.internal.ui.driver.DriverListFragment())
                    true
                }
                R.id.nav_reports -> {
                    loadFragment(com.chuyen_de_2.foodorder.internal.ui.shop.StoreReportFragment())
                    true
                }
                else -> false
            }
        }

        // Mặc định tải tab Thực đơn hoặc Đơn hàng
        if (binding.bottomNavMerchant.selectedItemId == R.id.nav_menu) {
            val menuFragment = MenuManageFragment().apply {
                onNavigateToOrdersListener = {
                    binding.bottomNavMerchant.selectedItemId = R.id.nav_orders
                }
            }
            loadFragment(menuFragment)
        } else {
            loadFragment(OrderListFragment())
        }
    }

    /**
     * Tên chức năng: Thay thế Fragment nội dung trong container
     * Cách hoạt động: Dùng FragmentManager để commit giao dịch replace fragment tương ứng vào framelayout container.
     * Luồng hướng tới: Cập nhật màn hình con tương ứng trong Dashboard.
     */
    private fun loadFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()
    }
}
