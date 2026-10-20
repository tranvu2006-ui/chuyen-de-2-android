package com.chuyen_de_2.foodorder.internal.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
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
 * Dashboard Activity — Phân quyền RBAC (Section 2 - doc-adr)
 *
 * Kiểm tra role của user đã đăng nhập:
 * - MANAGER → hiển thị OrderListFragment (Kanban quản lý đơn)
 * - SHIPPER → hiển thị ShipperFragment (nhận cuốc + GPS)
 */
@AndroidEntryPoint
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    @Inject
    lateinit var authRepository: AuthRepository

    private var statusBarHeight: Int = 0
    private var navigationBarHeight: Int = 0
    private var isMerchantMode = false

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

    private fun applyWindowPadding() {
        InsetUtils.setLightStatusBar(this, true)
        binding.root.setBackgroundColor(android.graphics.Color.WHITE)
        val density = resources.displayMetrics.density
        val extraSpacing = (10 * density).toInt() // Cách trên 1 xíu để tránh thanh pin và giờ
        val topPadding = (if (statusBarHeight > 0) statusBarHeight else InsetUtils.getFallbackStatusBarHeight(this)) + extraSpacing
        binding.fragmentContainer.setPadding(0, topPadding, 0, 0)
        binding.bottomNavMerchant.setPadding(0, 0, 0, navigationBarHeight)
        binding.bottomNavShipper.setPadding(0, 0, 0, navigationBarHeight)
    }

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

    private fun loadUserRole() {
        lifecycleScope.launch {
            val role = authRepository.getUserRole()?.trim()?.uppercase()

            when (role) {
                User.Role.MANAGER, User.Role.MERCHANT, "SHOP", "STORE" -> {
                    setupMerchantNavigation()
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
                                setupMerchantNavigation()
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
                    Toast.makeText(this, "Tính năng Báo cáo Doanh thu & Đơn hàng", Toast.LENGTH_SHORT).show()
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

    private fun loadFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()
    }
}
