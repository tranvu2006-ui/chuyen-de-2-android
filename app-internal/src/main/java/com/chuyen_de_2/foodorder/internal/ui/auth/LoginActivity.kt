package com.chuyen_de_2.foodorder.internal.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.internal.databinding.ActivityLoginInternalBinding
import com.chuyen_de_2.foodorder.internal.ui.dashboard.DashboardActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Tên chức năng: Màn hình Đăng nhập Hệ thống Nội bộ (Merchant / Shipper)
 * Cách hoạt động:
 *  - Tiếp nhận thông tin tài khoản (Email & Mật khẩu).
 *  - Xác thực qua Firebase Authentication và kiểm tra quyền truy cập (Role) cũng như trạng thái tài khoản (ACTIVE / LOCKED).
 *  - Nếu đã đăng nhập trước đó và tài khoản hợp lệ, tự động điều hướng vào Dashboard.
 * Luồng hướng tới:
 *  - Đăng nhập thành công -> Điều hướng sang [DashboardActivity].
 *  - Bấm "Đăng ký" -> Chuyển sang [RegisterActivity].
 *  - Tài khoản bị khóa -> Hiển thị Dialog cảnh báo khóa tài khoản.
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginInternalBinding

    @Inject
    lateinit var authRepository: AuthRepository

    private val viewModel: AuthViewModel by viewModels()

    /**
     * Tên chức năng: Khởi tạo màn hình và kiểm tra phiên làm việc (Session Check)
     * Cách hoạt động:
     *  - Kiểm tra Intent cờ LOGOUT để đăng xuất nếu có yêu cầu.
     *  - Nếu đã đăng nhập, gọi checkAccountActiveStatus() trên Firebase để đảm bảo tài khoản chưa bị Admin khóa.
     * Luồng hướng tới: Vào [DashboardActivity] nếu hợp lệ hoặc hiển thị Dialog nếu bị khóa.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isLogout = intent.getBooleanExtra("LOGOUT", false)
        if (isLogout) {
            authRepository.logout()
        } else if (authRepository.isLoggedIn) {
            lifecycleScope.launch {
                val statusCheck = authRepository.checkAccountActiveStatus()
                if (statusCheck.isFailure) {
                    val reason = statusCheck.exceptionOrNull()?.message ?: "Tài khoản của bạn đã bị khóa bởi Quản trị viên."
                    androidx.appcompat.app.AlertDialog.Builder(this@LoginActivity)
                        .setTitle("🔒 Tài khoản bị khóa")
                        .setMessage(reason)
                        .setPositiveButton("Đóng", null)
                        .show()
                } else {
                    navigateToDashboard()
                }
            }
        }

        binding = ActivityLoginInternalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)

        setupUI()
        observeViewModel()
    }

    /**
     * Tên chức năng: Thiết lập sự kiện giao diện Đăng nhập
     * Cách hoạt động: Lấy thông tin email, password từ EditText và kích hoạt luồng xử lý đăng nhập tại [AuthViewModel].
     * Luồng hướng tới: Gửi dữ liệu tới [AuthViewModel.login].
     */
    private fun setupUI() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            viewModel.login(email, password)
        }

        binding.tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    /**
     * Tên chức năng: Lắng nghe trạng thái từ ViewModel (State Observer)
     * Cách hoạt động: Cập nhật ProgressBar và xử lý kết quả đăng nhập trả về từ ViewModel.
     * Luồng hướng tới: Điều hướng tới [navigateToDashboard] khi thành công hoặc hiện Toast báo lỗi khi thất bại.
     */
    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnLogin.isEnabled = !isLoading
        }

        viewModel.loginResult.observe(this) { result ->
            result.onSuccess {
                navigateToDashboard()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "Đăng nhập thất bại", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Tên chức năng: Điều hướng vào Dashboard
     * Cách hoạt động: Mở màn hình chính của ứng dụng nội bộ và đóng màn hình Login.
     * Luồng hướng tới: [DashboardActivity].
     */
    private fun navigateToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    /**
     * Tên chức năng: Xử lý Intent mới khi Activity đang ở backstack
     * Cách hoạt động: Nhận tín hiệu đăng xuất từ các màn hình khác chuyển về (FLAG_ACTIVITY_CLEAR_TOP).
     * Luồng hướng tới: Gọi authRepository.logout() để giải phóng phiên làm việc.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("LOGOUT", false)) {
            authRepository.logout()
        }
    }
}
