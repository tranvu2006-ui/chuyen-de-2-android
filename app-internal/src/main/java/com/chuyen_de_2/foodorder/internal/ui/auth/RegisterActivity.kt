package com.chuyen_de_2.foodorder.internal.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.internal.databinding.ActivityRegisterInternalBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Tên chức năng: Màn hình Đăng ký Đối tác Quán (Merchant Registration)
 * Cách hoạt động:
 *  - Thu thập thông tin đối tác gồm: Tên quán/đại diện, Email, Số điện thoại liên hệ, Mật khẩu.
 *  - Gửi dữ liệu tới [AuthViewModel.register] để khởi tạo tài khoản trên Firebase Auth và gán vai trò MERCHANT.
 * Luồng hướng tới:
 *  - Đăng ký thành công -> Bắt buộc điều hướng sang [StoreVerificationActivity] để điền thông tin địa chỉ, GPS và tài khoản nhận tiền.
 *  - Bấm "Đã có tài khoản" -> Đóng màn hình quay về [LoginActivity].
 */
@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterInternalBinding
    private val viewModel: AuthViewModel by viewModels()

    /**
     * Tên chức năng: Khởi tạo giao diện đăng ký đối tác
     * Cách hoạt động: Thiết lập ViewBinding, áp dụng Light Status Bar và kích hoạt lắng nghe sự kiện.
     * Luồng hướng tới: Render giao diện và gắn Observer kết quả.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterInternalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)

        setupUI()
        observeViewModel()
    }

    /**
     * Tên chức năng: Xử lý sự kiện đăng ký và kiểm tra dữ liệu đầu vào (Form Validation)
     * Cách hoạt động:
     *  - Kiểm tra các trường rỗng, tính hợp lệ của số điện thoại và so khớp mật khẩu.
     *  - Mặc định khởi tạo đối tác với quyền [User.Role.MERCHANT].
     * Luồng hướng tới: Gọi [AuthViewModel.register] để gửi yêu cầu lên Firebase.
     */
    private fun setupUI() {
        binding.btnSubmitRegister.setOnClickListener {
            val name = binding.etRegisterName.text.toString().trim()
            val phone = binding.etRegisterPhone.text.toString().trim()
            val email = binding.etRegisterEmail.text.toString().trim()
            val pass = binding.etRegisterPassword.text.toString().trim()
            val confirmPass = binding.etRegisterConfirmPassword.text.toString().trim()

            val role = User.Role.MERCHANT

            if (phone.isBlank()) {
                Toast.makeText(this, "Vui lòng nhập số điện thoại liên hệ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.register(name, email, pass, confirmPass, role, phone)
        }

        binding.tvBackToLogin.setOnClickListener {
            finish()
        }
    }

    /**
     * Tên chức năng: Theo dõi tiến trình đăng ký từ ViewModel
     * Cách hoạt động: Cập nhật ProgressBar và xử lý kết quả đăng ký thành công/thất bại.
     * Luồng hướng tới: Khi thành công, điều hướng sang [StoreVerificationActivity] với cờ CLEAR_TASK.
     */
    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSubmitRegister.isEnabled = !isLoading
        }

        viewModel.registerResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "🎉 Đăng ký tài khoản thành công! Vui lòng hoàn tất phiếu địa chỉ và xác minh thông tin quán.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, StoreVerificationActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "Đăng ký thất bại", Toast.LENGTH_LONG).show()
            }
        }
    }
}
