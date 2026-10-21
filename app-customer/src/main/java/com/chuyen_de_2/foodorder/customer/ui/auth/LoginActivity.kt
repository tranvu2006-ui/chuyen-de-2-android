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

/**
 * Tên chức năng: Màn Hình Đăng Nhập Khách Hàng (Customer Login Screen)
 * Cách hoạt động:
 *  - Thu thập Email và Mật khẩu khách hàng, thực hiện xác thực qua Firebase Authentication.
 *  - Kiểm tra trạng thái tài khoản (ACTIVE / LOCKED); nếu bị Admin khóa sẽ hiển thị hộp thoại cảnh báo.
 *  - Đối soát vai trò (Role): Nếu tài khoản có vai trò Quán (MERCHANT) hoặc Tài xế (DRIVER), gợi ý mở ứng dụng `com.chuyen_de_2.foodorder.internal`.
 *  - Nếu là tài khoản Khách hàng thông thường, điều hướng vào màn hình trang chủ [HomeActivity].
 * Luồng hướng tới:
 *  - Đăng nhập thành công -> Chuyển vào [HomeActivity].
 *  - Bấm "Đăng ký" -> Chuyển sang [RegisterActivity].
 *  - Tài khoản Quán/Shipper -> Điều hướng mở ứng dụng đối tác nội bộ qua Package Manager.
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    /**
     * Tên chức năng: Khởi tạo màn hình và kiểm tra phiên đăng nhập
     * Cách hoạt động: Thiết lập giao diện, bắt sự kiện và kiểm tra trạng thái đăng nhập sẵn có.
     * Luồng hướng tới: Tự động vào [HomeActivity] nếu phiên còn hiệu lực.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)

        setupUI()
        observeViewModel()

        val isLogout = intent.getBooleanExtra("LOGOUT", false)
        if (isLogout) {
            viewModel.logout()
        } else if (viewModel.isLoggedIn) {
            checkRoleAndRoute()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("LOGOUT", false)) {
            viewModel.logout()
        }
    }

    /**
     * Tên chức năng: Cấu hình sự kiện nút Đăng nhập và Đăng ký
     * Cách hoạt động: Lấy thông tin email/mật khẩu và kích hoạt [AuthViewModel.login].
     * Luồng hướng tới: Gửi dữ liệu xác thực tới Firebase.
     */
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

    /**
     * Tên chức năng: Lắng nghe trạng thái kết quả đăng nhập từ ViewModel
     * Cách hoạt động: Hiển thị thanh tiến trình ProgressBar và xử lý thành công / thất bại.
     * Luồng hướng tới: Khi thành công, gọi [checkRoleAndRoute].
     */
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

    /**
     * Tên chức năng: Kiểm tra trạng thái kích hoạt tài khoản và điều hướng vai trò
     * Cách hoạt động:
     *  - Kiểm tra xem tài khoản có bị khóa không via `checkAccountActiveStatus()`.
     *  - Đọc quyền (Role) của người dùng để điều hướng ứng dụng phù hợp (Customer -> Home; Merchant/Shipper -> Gợi ý mở App Internal).
     * Luồng hướng tới: [navigateToHome] hoặc [showInternalRoleDialog].
     */
    private fun checkRoleAndRoute() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val statusCheck = viewModel.checkAccountActiveStatus()
            if (statusCheck.isFailure) {
                binding.progressBar.visibility = View.GONE
                val reason = statusCheck.exceptionOrNull()?.message ?: "Tài khoản của bạn đã bị khóa bởi Quản trị viên."
                AlertDialog.Builder(this@LoginActivity)
                    .setTitle("🔒 Tài khoản bị khóa")
                    .setMessage(reason)
                    .setPositiveButton("Đóng", null)
                    .show()
                return@launch
            }

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

    /**
     * Tên chức năng: Hộp thoại gợi ý chuyển đổi ứng dụng cho Đối tác
     * Cách hoạt động: Khi phát hiện tài khoản đối tác mở nhầm app khách hàng, hiển thị Dialog hỗ trợ mở nhanh app Oishi Internal.
     * Luồng hướng tới: Mở Package `com.chuyen_de_2.foodorder.internal` hoặc tiếp tục xem với vai trò Khách.
     */
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

    /**
     * Tên chức năng: Điều hướng vào Trang chủ Khách hàng
     * Cách hoạt động: Khởi chạy [HomeActivity] và đóng màn hình Đăng nhập.
     * Luồng hướng tới: [HomeActivity].
     */
    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
