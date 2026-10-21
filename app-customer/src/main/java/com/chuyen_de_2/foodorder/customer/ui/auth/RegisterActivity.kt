package com.chuyen_de_2.foodorder.customer.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.databinding.ActivityRegisterBinding
import com.chuyen_de_2.foodorder.customer.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Tên chức năng: Màn Hình Đăng Ký Tài Khoản Khách Hàng (Customer Registration)
 * Cách hoạt động:
 *  - Thu thập thông tin cá nhân: Họ tên, Số điện thoại, Email, Mật khẩu và xác nhận mật khẩu.
 *  - Cho phép chọn vai trò đăng ký (Khách hàng, Đối tác quán, Tài xế).
 *  - Tạo tài khoản trên Firebase Authentication và ghi nhận thông tin vào Firestore collection `users`.
 * Luồng hướng tới:
 *  - Đăng ký thành công -> Điều hướng ngay vào màn hình chính [HomeActivity] (xóa stack).
 *  - Bấm "Đã có tài khoản" -> Đóng màn hình quay về [LoginActivity].
 */
@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels()

    /**
     * Tên chức năng: Khởi tạo View đăng ký
     * Cách hoạt động: Áp dụng Insets, thiết lập lắng nghe sự kiện nút bấm và ViewModel.
     * Luồng hướng tới: Hiển thị form đăng ký.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)

        setupUI()
        observeViewModel()
    }

    /**
     * Tên chức năng: Xử lý sự kiện gửi form đăng ký
     * Cách hoạt động: Lấy dữ liệu từ các trường EditText và RadioGroup chọn Role, gửi sang [AuthViewModel.register].
     * Luồng hướng tới: Kích hoạt xử lý trên Firebase Auth.
     */
    private fun setupUI() {
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()
            val role = when (binding.rgRole.checkedRadioButtonId) {
                com.chuyen_de_2.foodorder.customer.R.id.rbMerchant -> com.chuyen_de_2.foodorder.core.data.model.User.Role.MERCHANT
                com.chuyen_de_2.foodorder.customer.R.id.rbDriver -> com.chuyen_de_2.foodorder.core.data.model.User.Role.DRIVER
                else -> com.chuyen_de_2.foodorder.core.data.model.User.Role.CUSTOMER
            }
            viewModel.register(name, email, password, confirmPassword, role, phone)
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    /**
     * Tên chức năng: Lắng nghe kết quả đăng ký từ ViewModel
     * Cách hoạt động: Hiển thị trạng thái tải và điều hướng sang [HomeActivity] khi thành công.
     * Luồng hướng tới: [HomeActivity].
     */
    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnRegister.isEnabled = !isLoading
        }

        viewModel.registerResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "Đăng ký thất bại", Toast.LENGTH_LONG).show()
            }
        }
    }
}
