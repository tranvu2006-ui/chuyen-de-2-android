package com.chuyen_de_2.foodorder.customer.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.EmailOtpRequiredException
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.databinding.ActivityLoginBinding
import com.chuyen_de_2.foodorder.customer.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Màn hình đăng nhập khách hàng, tích hợp lấy và nhập mã xác thực OTP trực tiếp (inline OTP).
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    private var otpCountDownTimer: CountDownTimer? = null

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

    private fun setupUI() {
        binding.etEmail.doAfterTextChanged {
            binding.tilEmail.error = null
            binding.tilOtp.error = null
        }
        binding.etPassword.doAfterTextChanged {
            binding.tilPassword.error = null
        }
        binding.etOtp.doAfterTextChanged {
            binding.tilOtp.error = null
        }

        // Nút "Lấy mã" kế bên ô nhập OTP
        binding.btnGetOtp.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = "Vui lòng nhập địa chỉ email hợp lệ"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }
            binding.tilEmail.error = null
            binding.tilOtp.error = null
            binding.btnGetOtp.isEnabled = false
            binding.btnGetOtp.text = "Đang gửi..."
            viewModel.requestEmailOtp(email)
        }

        // Nút "Đăng nhập"
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val otp = binding.etOtp.text.toString().trim()

            if (email.isBlank()) {
                binding.tilEmail.error = "Vui lòng nhập email"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }
            if (password.isBlank()) {
                binding.tilPassword.error = "Vui lòng nhập mật khẩu"
                binding.etPassword.requestFocus()
                return@setOnClickListener
            }

            // Nếu người dùng đã nhập mã OTP -> xác thực OTP và đăng nhập
            if (otp.isNotBlank()) {
                if (otp.length != 6 || !otp.all { it.isDigit() }) {
                    binding.tilOtp.error = "Mã OTP phải gồm đúng 6 chữ số"
                    binding.etOtp.requestFocus()
                    return@setOnClickListener
                }
                viewModel.verifyLoginOtp(email, password, otp)
            } else {
                // Đăng nhập bình thường
                viewModel.login(email, password)
            }
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
                if (error is EmailOtpRequiredException) {
                    val email = binding.etEmail.text.toString().trim()
                    binding.tilOtp.error = "Tài khoản cần xác minh. Vui lòng bấm 'Lấy mã' và nhập mã OTP."
                    binding.etOtp.requestFocus()
                    if (binding.btnGetOtp.isEnabled) {
                        viewModel.requestEmailOtp(email)
                        startOtpCountdown()
                    }
                } else {
                    Toast.makeText(this, error.message ?: "Đăng nhập thất bại", Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.otpRequestResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "Đã gửi mã OTP đến email. Vui lòng kiểm tra hộp thư!", Toast.LENGTH_LONG).show()
                startOtpCountdown()
                binding.etOtp.requestFocus()
            }.onFailure { error ->
                binding.btnGetOtp.isEnabled = true
                binding.btnGetOtp.text = "Lấy mã"
                Toast.makeText(this, error.message ?: "Không gửi được mã OTP.", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.loginOtpResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "Xác minh email và đăng nhập thành công.", Toast.LENGTH_SHORT).show()
                checkRoleAndRoute()
            }.onFailure { error ->
                val userMsg = error.message ?: "Mã OTP không hợp lệ."
                binding.tilOtp.error = userMsg
                binding.etOtp.requestFocus()
                Toast.makeText(this, userMsg, Toast.LENGTH_LONG).show()
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

    private fun checkRoleAndRoute() {
        lifecycleScope.launch {
            val role = viewModel.getUserRole()
            if (role == User.Role.CUSTOMER || role.isNullOrBlank()) {
                val intent = Intent(this@LoginActivity, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this@LoginActivity, "Tài khoản đối tác vui lòng dùng App Quán/Tài xế.", Toast.LENGTH_LONG).show()
                viewModel.logout()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        otpCountDownTimer?.cancel()
    }
}
