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
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Tính chiều cao status bar dự phòng ban đầu
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        statusBarHeight = if (resId > 0) {
            resources.getDimensionPixelSize(resId)
        } else {
            (28 * resources.displayMetrics.density).toInt()
        }

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
        if (isMerchantMode) {
            // Chế độ Quán: Toàn bộ màn hình lùi xuống dưới thanh status bar để không bị cấn tai thỏ/camera
            InsetUtils.setLightStatusBar(this, true)
            binding.root.setBackgroundColor(android.graphics.Color.WHITE)
            androidx.core.view.ViewCompat.getRootWindowInsets(binding.root)
            binding.fragmentContainer.setPadding(0, statusBarHeight, 0, 0)
            binding.bottomNavMerchant.setPadding(0, 0, 0, navigationBarHeight)
        } else {
            // Chế độ Shipper: Tránh cấn tai thỏ và thanh điều hướng
            InsetUtils.setLightStatusBar(this, true)
            binding.root.setBackgroundColor(android.graphics.Color.WHITE)
            binding.fragmentContainer.setPadding(0, statusBarHeight, 0, 0)
            binding.bottomNavShipper.setPadding(0, 0, 0, navigationBarHeight)
        }
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
                    })
                    finish()
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
                    androidx.appcompat.app.AlertDialog.Builder(this@DashboardActivity)
                        .setTitle("⚠️ Tài khoản Khách Hàng")
                        .setMessage("Tài khoản của bạn là Khách hàng (Customer).\n\nỨng dụng này chỉ dành cho Cửa Hàng và Tài Xế. Vui lòng mở ứng dụng 'Oishi Customer' để đặt món!")
                        .setPositiveButton("Mở Oishi Customer") { _, _ ->
                            authRepository.logout()
                            val launchIntent = packageManager.getLaunchIntentForPackage("com.chuyen_de_2.foodorder.customer")
                            if (launchIntent != null) {
                                startActivity(launchIntent)
                            }
                            finish()
                        }
                        .setNegativeButton("Đăng xuất") { _, _ ->
                            authRepository.logout()
                            startActivity(Intent(this@DashboardActivity, LoginActivity::class.java))
                            finish()
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
