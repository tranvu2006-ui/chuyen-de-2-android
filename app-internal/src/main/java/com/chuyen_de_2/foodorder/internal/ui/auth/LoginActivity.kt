package com.chuyen_de_2.foodorder.internal.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.KeyboardUtils
import com.chuyen_de_2.foodorder.core.util.OtpSessionManager
import com.chuyen_de_2.foodorder.core.util.RegistrationOtpManager
import com.chuyen_de_2.foodorder.internal.databinding.ActivityLoginInternalBinding
import com.chuyen_de_2.foodorder.internal.ui.dashboard.DashboardActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Màn hình đăng nhập đối tác nội bộ (Chủ quán/Tài xế).
 * Nếu tài khoản chưa xác thực OTP hoặc phiên 24h đã hết hạn, hệ thống sẽ chuyển sang
 * màn hình OtpVerificationActivity riêng biệt (tương tự như luồng Đăng ký).
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginInternalBinding

    @Inject
    lateinit var authRepository: AuthRepository

    private val viewModel: AuthViewModel by viewModels()

    private var pendingEmail: String = ""
    private var pendingPassword: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isLogout = intent.getBooleanExtra("LOGOUT", false)
        if (isLogout) {
            authRepository.logout()
        } else if (authRepository.isLoggedIn) {
            checkActiveStatusAndNavigate()
        }

        binding = ActivityLoginInternalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
        InsetUtils.applyImeAndSystemInsets(binding.root)

        setupUI()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        if (authRepository.isLoggedIn) {
            checkActiveStatusAndNavigate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("LOGOUT", false)) {
            authRepository.logout()
        }
    }

    private fun setupUI() {
        binding.etEmail.doAfterTextChanged {
            binding.tilEmail.error = null
        }
        binding.etPassword.doAfterTextChanged {
            binding.tilPassword.error = null
        }

        // Nút "Đăng nhập"
        binding.btnLogin.setOnClickListener {
            if (viewModel.isLoading.value == true) return@setOnClickListener
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = "Vui lòng nhập địa chỉ email hợp lệ"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }
            if (password.isBlank()) {
                binding.tilPassword.error = "Vui lòng nhập mật khẩu"
                binding.etPassword.requestFocus()
                return@setOnClickListener
            }

            binding.tilEmail.error = null
            binding.tilPassword.error = null

            val isAdmin = email.lowercase() == "admin@gmail.com"
            val hasSession = isAdmin || OtpSessionManager.isSessionValid(this, email)

            if (hasSession) {
                // Đã có phiên xác minh hợp lệ trong 24h hoặc là admin -> Đăng nhập trực tiếp
                viewModel.login(email, password)
            } else {
                // Chưa xác minh hoặc hết hạn 24h -> Chuyển sang OtpVerificationActivity
                pendingEmail = email
                pendingPassword = password

                val remainingCooldown = RegistrationOtpManager.getRemainingCooldownMs(this, email)
                if (remainingCooldown > 0L) {
                    // Mã OTP vừa gửi vẫn còn hạn/cooldown -> Chuyển thẳng sang OtpVerificationActivity
                    openOtpVerificationActivity(email, password)
                } else {
                    // Gửi mã OTP mới đến email
                    binding.btnLogin.isEnabled = false
                    viewModel.requestLoginOtp(email)
                }
            }
        }

        // Chuyển sang màn hình Đăng ký đối tác
        binding.tvGoToRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnLogin.isEnabled = !isLoading
        }

        viewModel.otpRequestResult.observe(this) { result ->
            binding.btnLogin.isEnabled = true
            result.onSuccess {
                RegistrationOtpManager.recordOtpSent(this, pendingEmail)
                Toast.makeText(this, "Đã gửi mã OTP đến $pendingEmail. Vui lòng xác thực!", Toast.LENGTH_SHORT).show()
                openOtpVerificationActivity(pendingEmail, pendingPassword)
            }.onFailure { error ->
                val rawMsg = error.message.orEmpty()
                if (RegistrationOtpManager.isRateLimitError(rawMsg)) {
                    RegistrationOtpManager.recordOtpSent(this, pendingEmail)
                    Toast.makeText(this, "Mã OTP đã được gửi đến $pendingEmail và vẫn còn hiệu lực. Vui lòng xác thực!", Toast.LENGTH_SHORT).show()
                    openOtpVerificationActivity(pendingEmail, pendingPassword)
                } else {
                    val msg = error.message ?: "Không gửi được mã OTP"
                    if (msg.contains("chưa được đăng ký", ignoreCase = true)) {
                        binding.tilEmail.error = msg
                        binding.etEmail.requestFocus()
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.loginResult.observe(this) { result ->
            result.onSuccess {
                val email = binding.etEmail.text.toString().trim()
                OtpSessionManager.markEmailVerified(this, email)
                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                navigateToDashboard()
            }.onFailure { error ->
                if (error is com.chuyen_de_2.foodorder.core.data.firebase.EmailOtpRequiredException) {
                    val email = binding.etEmail.text.toString().trim()
                    val password = binding.etPassword.text.toString()
                    pendingEmail = email
                    pendingPassword = password
                    viewModel.requestLoginOtp(email)
                } else {
                    val msg = error.message.orEmpty()
                    val userMsg = if (
                        msg.contains("password", ignoreCase = true) ||
                        msg.contains("credential", ignoreCase = true) ||
                        msg.contains("mật khẩu", ignoreCase = true)
                    ) {
                        binding.tilPassword.error = "Mật khẩu không chính xác"
                        "Mật khẩu không chính xác"
                    } else if (msg.contains("no user", ignoreCase = true) || msg.contains("user-not-found", ignoreCase = true)) {
                        binding.tilEmail.error = "Không tìm thấy tài khoản với email này"
                        "Không tìm thấy tài khoản với email này"
                    } else {
                        msg.ifBlank { "Đăng nhập thất bại" }
                    }
                    Toast.makeText(this, userMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openOtpVerificationActivity(email: String, password: String) {
        val intent = Intent(this, OtpVerificationActivity::class.java).apply {
            putExtra(OtpVerificationActivity.EXTRA_FLOW, OtpVerificationActivity.FLOW_LOGIN)
            putExtra(OtpVerificationActivity.EXTRA_EMAIL, email)
            putExtra(OtpVerificationActivity.EXTRA_PASSWORD, password)
        }
        startActivity(intent)
    }

    private fun checkActiveStatusAndNavigate() {
        lifecycleScope.launch {
            try {
                val statusCheck = authRepository.checkAccountActiveStatus()
                if (isFinishing || isDestroyed) return@launch
                if (statusCheck.isFailure) {
                    val reason = statusCheck.exceptionOrNull()?.message ?: "Tài khoản của bạn đã bị khóa bởi Quản trị viên."
                    AlertDialog.Builder(this@LoginActivity)
                        .setTitle("Tài khoản bị khóa")
                        .setMessage(reason)
                        .setPositiveButton("Đóng", null)
                        .show()
                } else {
                    navigateToDashboard()
                }
            } catch (_: Exception) {}
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        KeyboardUtils.handleTouchOutsideToHideKeyboard(this, ev)
        return super.dispatchTouchEvent(ev)
    }
}
