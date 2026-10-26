package com.chuyen_de_2.foodorder.admin.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.ActivityAdminMainBinding
import com.chuyen_de_2.foodorder.admin.ui.auth.AdminAuthState
import com.chuyen_de_2.foodorder.admin.ui.auth.AdminAuthViewModel
import com.chuyen_de_2.foodorder.admin.ui.auth.AdminLoginActivity
import com.chuyen_de_2.foodorder.admin.ui.category.AdminCategoryListFragment
import com.chuyen_de_2.foodorder.admin.ui.complaint.AdminComplaintListFragment
import com.chuyen_de_2.foodorder.admin.ui.dashboard.AdminDashboardFragment
import com.chuyen_de_2.foodorder.admin.ui.monitor.AdminMonitorFragment
import com.chuyen_de_2.foodorder.admin.ui.store.AdminStoreListFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminMainBinding
    private val authViewModel: AdminAuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupTopBar()
        setupBottomNavigation()
        setupBackNavigation()
        observeAuth()

        if (savedInstanceState == null) {
            replaceFragment(AdminDashboardFragment())
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Đặt chiều cao spacer chính xác theo thanh trạng thái
            binding.viewStatusBar.updateLayoutParams {
                height = insets.top
            }

            // Đặt padding dưới cho Bottom Navigation
            binding.bottomNavAdmin.updatePadding(
                bottom = insets.bottom
            )

            windowInsets
        }
    }

    private fun setupTopBar() {
        binding.btnAdminLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Đăng xuất quản trị")
                .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi Cổng Quản Trị Hệ Thống?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Đăng xuất") { _, _ ->
                    authViewModel.logout()
                    val intent = Intent(this, AdminLoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                .show()
        }

        binding.btnQuickUrgentAlert.setOnClickListener {
            // Chuyển nhanh sang tab Khiếu nại hoặc Quán ăn
            selectNavigationTab(R.id.menu_complaints)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavAdmin.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.menu_dashboard -> AdminDashboardFragment()
                R.id.menu_stores -> AdminStoreListFragment()
                R.id.menu_categories -> AdminCategoryListFragment()
                R.id.menu_complaints -> AdminComplaintListFragment()
                R.id.menu_monitor -> AdminMonitorFragment()
                else -> AdminDashboardFragment()
            }
            replaceFragment(fragment)
            true
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.bottomNavAdmin.selectedItemId != R.id.menu_dashboard) {
                    binding.bottomNavAdmin.selectedItemId = R.id.menu_dashboard
                } else {
                    finish()
                }
            }
        })
    }

    private fun observeAuth() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.authState.collect { state ->
                    if (state is AdminAuthState.Success) {
                        val name = state.user.name.ifBlank { state.user.email }
                        binding.tvTopBarAdminName.text = "Quản trị viên: $name"
                    }
                }
            }
        }
    }

    fun updateAlertBadge(pendingTotal: Int) {
        if (pendingTotal > 0) {
            binding.tvAlertBadgeCount.visibility = View.VISIBLE
            binding.tvAlertBadgeCount.text = if (pendingTotal > 9) "9+" else pendingTotal.toString()
        } else {
            binding.tvAlertBadgeCount.visibility = View.GONE
        }
    }

    fun selectNavigationTab(menuItemId: Int) {
        binding.bottomNavAdmin.selectedItemId = menuItemId
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.navHostAdmin, fragment)
            .commit()
    }
}
