package com.chuyen_de_2.foodorder.customer.ui.auth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
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
 * Màn hình đăng ký tài khoản khách hàng, tích hợp xác thực mã OTP trực tiếp trên giao diện (inline OTP).
 */
@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels()

    private var otpCountDownTimer: CountDownTimer? = null
    private var pendingRegistration: ((String) -> Unit)? = null
    private var currentOtpEmail = ""

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

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
        binding.etEmail.doAfterTextChanged {
            binding.tilEmail.error = null
            binding.tilOtp.error = null
        }
        binding.etPassword.doAfterTextChanged { binding.tilPassword.error = null }
        binding.etConfirmPassword.doAfterTextChanged { binding.tilConfirmPassword.error = null }
        binding.etOtp.doAfterTextChanged { s ->
            binding.tilOtp.error = null
            val code = s?.toString()?.trim().orEmpty()
            if (code.length == 6 && code.all { it.isDigit() } && viewModel.isLoading.value != true) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(binding.etOtp.windowToken, 0)
                val name = binding.etName.text?.toString()?.trim().orEmpty()
                val email = binding.etEmail.text?.toString()?.trim().orEmpty()
                val password = binding.etPassword.text?.toString().orEmpty()
                val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()
                if (name.isNotBlank() && email.isNotBlank() && password.length >= 6 && password == confirmPassword) {
                    binding.btnRegister.performClick()
                }
            }
        }

        // 1. Nút "Lấy mã" OTP kế bên ô nhập mã
        binding.btnGetOtp.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = "Vui lòng nhập địa chỉ email hợp lệ"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }
            binding.tilEmail.error = null
            binding.tilOtp.error = null
            currentOtpEmail = email
            binding.btnGetOtp.isEnabled = false
            binding.btnGetOtp.text = "Đang gửi..."
            viewModel.requestEmailOtp(email)
        }

        // 2. Nút "Đăng ký"
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            val otpCode = binding.etOtp.text.toString().trim()

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
            if (otpCode.length != 6 || !otpCode.all { it.isDigit() }) {
                binding.tilOtp.error = "Vui lòng bấm 'Lấy mã' và nhập đúng 6 chữ số OTP"
                binding.etOtp.requestFocus()
                return@setOnClickListener
            }

            currentOtpEmail = email
            viewModel.registerWithOtp(
                name = name,
                email = email,
                password = password,
                confirmPassword = confirmPassword,
                role = role,
                phone = phone,
                otpCode = otpCode
            )
        }

        binding.tvLogin.setOnClickListener { finish() }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnRegister.isEnabled = !isLoading
        }

        viewModel.otpRequestResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "Đã gửi mã OTP đến $currentOtpEmail. Vui lòng kiểm tra email!", Toast.LENGTH_LONG).show()
                startOtpCountdown()
                binding.etOtp.requestFocus()
            }.onFailure { error ->
                binding.btnGetOtp.isEnabled = true
                binding.btnGetOtp.text = "Lấy mã"
                val msg = error.message ?: "Không gửi được mã OTP"
                binding.tilEmail.error = msg
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.otpVerifyResult.observe(this) { result ->
            result.onSuccess { verifiedValue ->
                binding.tilOtp.error = null
                pendingRegistration?.invoke(verifiedValue)
            }.onFailure { error ->
                val message = error.message.orEmpty()
                val userMessage = if (
                    message.contains("expired", ignoreCase = true) ||
                    message.contains("invalid", ignoreCase = true) ||
                    message.contains("token", ignoreCase = true)
                ) {
                    "Mã OTP không đúng hoặc đã hết hạn. Vui lòng bấm 'Lấy mã' để nhận mã mới."
                } else {
                    message.ifBlank { "Mã OTP không hợp lệ" }
                }
                binding.tilOtp.error = userMessage
                binding.etOtp.requestFocus()
                Toast.makeText(this, userMessage, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.registerResult.observe(this) { result ->
            result.onSuccess {
                otpCountDownTimer?.cancel()
                Toast.makeText(
                    this,
                    "Đăng ký và xác thực thành công! Bạn có thể đăng nhập ngay.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "Đăng ký thất bại", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startOtpCountdown() {
        otpCountDownTimer?.cancel()
        binding.btnGetOtp.isEnabled = false
        otpCountDownTimer = object : CountDownTimer(60_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                binding.btnGetOtp.text = "${sec}s"
            }

            override fun onFinish() {
                binding.btnGetOtp.isEnabled = true
                binding.btnGetOtp.text = "Gửi lại"
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        otpCountDownTimer?.cancel()
    }
}
