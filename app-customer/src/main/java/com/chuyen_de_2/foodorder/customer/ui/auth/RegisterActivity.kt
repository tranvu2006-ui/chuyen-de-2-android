package com.chuyen_de_2.foodorder.customer.ui.auth

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Màn hình nhập thông tin đăng ký tài khoản khách hàng.
 * Form đăng ký không còn chứa ô OTP. Sau khi bấm "Đăng ký", hệ thống sẽ chuyển sang
 * màn hình xác minh OTP riêng biệt để hoàn tất việc tạo tài khoản.
 */
@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels()

    private var pendingName = ""
    private var pendingPhone = ""
    private var pendingEmail = ""
    private var pendingPassword = ""
    private var pendingConfirmPassword = ""
    private var pendingRole = User.Role.CUSTOMER

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val otpVerificationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)
        checkNotificationPermission()
        setupUI()
        observeViewModel()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupUI() {
        binding.etName.doAfterTextChanged { binding.tilName.error = null }
        binding.etPhone.doAfterTextChanged { binding.tilPhone.error = null }
        binding.etEmail.doAfterTextChanged { binding.tilEmail.error = null }
        binding.etPassword.doAfterTextChanged { binding.tilPassword.error = null }
        binding.etConfirmPassword.doAfterTextChanged { binding.tilConfirmPassword.error = null }

        // Nút "Đăng ký"
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            val role = when (binding.rgRole.checkedRadioButtonId) {
                R.id.rbMerchant -> User.Role.MERCHANT
                R.id.rbDriver -> User.Role.DRIVER
                else -> User.Role.CUSTOMER
            }

            if (name.isBlank()) {
                binding.tilName.error = "Vui lòng nhập họ tên"
                binding.etName.requestFocus()
                return@setOnClickListener
            }
            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = "Vui lòng nhập email hợp lệ"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }
            if (password.length < 6) {
                binding.tilPassword.error = "Mật khẩu phải từ 6 ký tự trở lên"
                binding.etPassword.requestFocus()
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                binding.tilConfirmPassword.error = "Mật khẩu xác nhận không khớp"
                binding.etConfirmPassword.requestFocus()
                return@setOnClickListener
            }

            pendingName = name
            pendingPhone = phone
            pendingEmail = email
            pendingPassword = password
            pendingConfirmPassword = confirmPassword
            pendingRole = role

            binding.progressBar.visibility = View.VISIBLE
            binding.btnRegister.isEnabled = false
            binding.btnRegister.text = "Đang gửi mã xác thực..."

            // Gửi OTP qua email và kiểm tra tài khoản đã tồn tại hay chưa
            viewModel.requestEmailOtp(email)
        }

        binding.tvLogin.setOnClickListener { finish() }
    }

    private fun observeViewModel() {
        viewModel.otpRequestResult.observe(this) { result ->
            binding.progressBar.visibility = View.GONE
            binding.btnRegister.isEnabled = true
            binding.btnRegister.text = getString(R.string.btn_register)

            result.onSuccess {
                Toast.makeText(this, "Đã gửi mã OTP đến $pendingEmail. Vui lòng xác thực!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, OtpVerificationActivity::class.java).apply {
                    putExtra(OtpVerificationActivity.EXTRA_NAME, pendingName)
                    putExtra(OtpVerificationActivity.EXTRA_EMAIL, pendingEmail)
                    putExtra(OtpVerificationActivity.EXTRA_PASSWORD, pendingPassword)
                    putExtra(OtpVerificationActivity.EXTRA_CONFIRM_PASSWORD, pendingConfirmPassword)
                    putExtra(OtpVerificationActivity.EXTRA_PHONE, pendingPhone)
                    putExtra(OtpVerificationActivity.EXTRA_ROLE, pendingRole)
                }
                otpVerificationLauncher.launch(intent)
            }.onFailure { error ->
                val msg = error.message ?: "Không gửi được mã OTP"
                binding.tilEmail.error = msg
                binding.etEmail.requestFocus()
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
