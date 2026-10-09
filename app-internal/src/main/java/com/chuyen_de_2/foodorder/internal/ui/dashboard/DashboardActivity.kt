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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, false)
        InsetUtils.applyToolbarInsets(binding.toolbar)

        setupToolbar()
        loadUserRole()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.btnLogout.setOnClickListener {
            authRepository.logout()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
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
                    binding.toolbar.visibility = android.view.View.VISIBLE
                    binding.bottomNavMerchant.visibility = android.view.View.GONE
                    supportActionBar?.title = "🛵 Shipper (Tài xế quán)"
                    loadFragment(ShipperFragment())
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

    private fun setupMerchantNavigation() {
        binding.toolbar.visibility = android.view.View.GONE
        binding.bottomNavMerchant.visibility = android.view.View.VISIBLE

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
                    Toast.makeText(this, "Tính năng quản lý Đội ngũ Tài xế đang được cập nhật", Toast.LENGTH_SHORT).show()
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
