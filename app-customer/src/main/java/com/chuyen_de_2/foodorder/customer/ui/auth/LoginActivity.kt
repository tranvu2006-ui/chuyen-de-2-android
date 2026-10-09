package com.chuyen_de_2.foodorder.customer.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.databinding.ActivityLoginBinding
import com.chuyen_de_2.foodorder.customer.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)

        setupUI()
        observeViewModel()

        // Nếu đã đăng nhập → kiểm tra role và điều hướng
        if (viewModel.isLoggedIn) {
            checkRoleAndRoute()
        }
    }

    private fun setupUI() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            viewModel.login(email, password)
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnLogin.isEnabled = !isLoading
        }

        viewModel.loginResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                checkRoleAndRoute()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "Đăng nhập thất bại", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkRoleAndRoute() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val role = viewModel.getUserRole()?.trim()?.uppercase()
            binding.progressBar.visibility = View.GONE

            when (role) {
                User.Role.MERCHANT, User.Role.MANAGER, "SHOP", "STORE" -> {
                    showInternalRoleDialog("Cửa Hàng (Quán Ăn)", isShop = true)
                }
                User.Role.DRIVER, User.Role.SHIPPER -> {
                    showInternalRoleDialog("Tài Xế (Shipper)", isShop = false)
                }
                else -> {
                    navigateToHome()
                }
            }
        }
    }

    private fun showInternalRoleDialog(roleName: String, isShop: Boolean) {
        val appName = if (isShop) "Quán Ăn (Merchant)" else "Tài Xế (Shipper)"
        val featureDesc = if (isShop) "Quản lý Đơn hàng & Thực đơn" else "Nhận cuốc giao hàng & Định vị GPS"

        AlertDialog.Builder(this)
            .setTitle("🏪 Tài khoản $roleName")
            .setMessage(
                "Bạn đang đăng nhập bằng tài khoản $roleName trên ứng dụng Khách hàng (Oishi Customer).\n\n" +
                "Giao diện $featureDesc nằm ở ứng dụng Cửa hàng & Tài xế (Oishi Internal).\n\n" +
                "Bạn có muốn chuyển sang ứng dụng Oishi Internal ngay bây giờ không?"
            )
            .setPositiveButton("Mở App Quán (Oishi Internal)") { _, _ ->
                val launchIntent = packageManager.getLaunchIntentForPackage("com.chuyen_de_2.foodorder.internal")
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    finish()
                } else {
                    showAppNotInstalledDialog(appName)
                }
            }
            .setNegativeButton("Xem như Khách") { _, _ ->
                navigateToHome()
            }
            .setNeutralButton("Đăng xuất") { _, _ ->
                viewModel.logout()
            }
            .setCancelable(false)
            .show()
    }

    private fun showAppNotInstalledDialog(appName: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Chưa cài đặt Oishi Internal")
            .setMessage(
                "Ứng dụng Oishi Internal ($appName) chưa được cài đặt trên thiết bị.\n\n" +
                "👉 Hướng dẫn mở giao diện Cửa hàng trên Android Studio:\n" +
                "1. Nhìn lên thanh công cụ phía trên của Android Studio (cạnh nút Run ▶️ màu xanh).\n" +
                "2. Nhấn vào danh sách module (đang chọn 'app-customer') và đổi sang 'app-internal'.\n" +
                "3. Bấm Run (▶️) để cài đặt và mở ứng dụng Quán / Shipper!"
            )
            .setPositiveButton("Tiếp tục xem như Khách") { _, _ ->
                navigateToHome()
            }
            .setNegativeButton("Đăng xuất") { _, _ ->
                viewModel.logout()
            }
            .show()
    }

    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
