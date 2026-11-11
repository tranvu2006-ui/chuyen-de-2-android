package com.chuyen_de_2.foodorder.internal.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import android.view.MotionEvent
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.KeyboardUtils
import com.chuyen_de_2.foodorder.core.util.OtpSessionManager
import com.chuyen_de_2.foodorder.internal.databinding.ActivityLoginInternalBinding
import com.chuyen_de_2.foodorder.internal.ui.dashboard.DashboardActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Màn hình đăng nhập đối tác nội bộ, bắt buộc xác thực mã OTP qua Email trực tiếp trên form (inline OTP).
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginInternalBinding
    private var otpCountDownTimer: CountDownTimer? = null

    @Inject
    lateinit var authRepository: AuthRepository

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isLogout = intent.getBooleanExtra("LOGOUT", false)
        if (isLogout) {
            authRepository.logout()
        } else if (authRepository.isLoggedIn) {
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

        binding = ActivityLoginInternalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
        InsetUtils.applyImeAndSystemInsets(binding.root)

        setupUI()
        observeViewModel()
        updateOtpSessionUI(binding.etEmail.text?.toString().orEmpty())
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
            binding.tilOtp.error = null
            val email = it?.toString()?.trim().orEmpty()
            updateOtpSessionUI(email)
        }
        binding.etPassword.doAfterTextChanged {
            binding.tilPassword.error = null
        }
        binding.etOtp.doAfterTextChanged { s ->
            binding.tilOtp.error = null
            val code = s?.toString()?.trim().orEmpty()
            if (code.length == 6 && code.all { it.isDigit() } && viewModel.isLoading.value != true) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(binding.etOtp.windowToken, 0)
                val email = binding.etEmail.text?.toString()?.trim().orEmpty()
                val password = binding.etPassword.text?.toString().orEmpty()
                if (email.isNotBlank() && password.isNotBlank()) {
                    binding.btnLogin.performClick()
                }
            }
        }

        binding.tvReVerifyOtp.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            OtpSessionManager.clearSession(this, email)
            updateOtpSessionUI(email)
            binding.etOtp.requestFocus()
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
            viewModel.requestLoginOtp(email)
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

            if (OtpSessionManager.isSessionValid(this, email)) {
                // Email này đã có phiên xác minh OTP hợp lệ trong 24h -> Bỏ qua OTP, đăng nhập thẳng
                viewModel.login(email, password)
            } else {
                // Chưa xác minh hoặc phiên 24h đã hết hạn -> Bắt buộc nhập OTP 6 số
                val otp = binding.etOtp.text.toString().trim()
                if (otp.length != 6 || !otp.all { it.isDigit() }) {
                    binding.tilOtp.error = "Vui lòng bấm 'Lấy mã' và nhập mã OTP 6 số để đăng nhập"
                    binding.etOtp.requestFocus()
                    return@setOnClickListener
                }
                viewModel.verifyLoginOtp(email, password, otp)
            }
        }

        binding.tvGoToRegister.setOnClickListener {
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
                navigateToDashboard()
            }.onFailure { error ->
                if (error is com.chuyen_de_2.foodorder.core.data.firebase.EmailOtpRequiredException) {
                    startOtpCountdown()
                    binding.tilOtp.error = "Đã gửi mã OTP đến email. Vui lòng nhập 6 chữ số OTP."
                    binding.etOtp.requestFocus()
                    Toast.makeText(this, "Đã gửi mã OTP đến email. Vui lòng nhập 6 số OTP!", Toast.LENGTH_LONG).show()
                } else {
                    val msg = error.message.orEmpty()
                    if (msg.contains("password", ignoreCase = true) || msg.contains("credential", ignoreCase = true)) {
                        binding.tilPassword.error = "Mật khẩu không chính xác"
                    }
                    Toast.makeText(this, msg.ifBlank { "Đăng nhập thất bại" }, Toast.LENGTH_LONG).show()
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
                val errorMsg = error.message.orEmpty()
                if (errorMsg.contains("chưa được đăng ký", ignoreCase = true)) {
                    binding.tilEmail.error = errorMsg
                    binding.etEmail.requestFocus()
                }
                Toast.makeText(this, errorMsg.ifBlank { "Không gửi được mã OTP." }, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.loginResult.observe(this) { result ->
            result.onSuccess {
                val email = binding.etEmail.text.toString().trim()
                OtpSessionManager.markEmailVerified(this, email)
                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                navigateToDashboard()
            }.onFailure { error ->
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

        viewModel.loginOtpResult.observe(this) { result ->
            result.onSuccess {
                val email = binding.etEmail.text.toString().trim()
                OtpSessionManager.markEmailVerified(this, email)
                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                navigateToDashboard()
            }.onFailure { error ->
                val msg = error.message.orEmpty()
                val userMsg = if (
                    msg.contains("password", ignoreCase = true) ||
                    msg.contains("credential", ignoreCase = true) ||
                    msg.contains("mật khẩu", ignoreCase = true)
                ) {
                    binding.tilPassword.error = "Mật khẩu không chính xác"
                    "Mật khẩu không chính xác"
                } else if (
                    msg.contains("token", ignoreCase = true) ||
                    msg.contains("otp", ignoreCase = true) ||
                    msg.contains("expired", ignoreCase = true) ||
                    msg.contains("invalid", ignoreCase = true)
                ) {
                    binding.tilOtp.error = "Mã OTP không đúng hoặc đã hết hạn. Vui lòng bấm 'Lấy mã' để nhận mã mới."
                    "Mã OTP không đúng hoặc đã hết hạn"
                } else {
                    msg.ifBlank { "Đăng nhập thất bại" }
                }
                Toast.makeText(this, userMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateOtpSessionUI(email: String) {
        val cleanEmail = email.trim().lowercase()
        val isAdmin = cleanEmail == "admin@gmail.com"
        val hasSession = isAdmin || OtpSessionManager.isSessionValid(this, cleanEmail)
        if (hasSession) {
            val remainingHours = OtpSessionManager.getRemainingHours(this, cleanEmail)
            binding.layoutOtp.visibility = View.GONE
            binding.layoutOtpSessionNotice.visibility = View.VISIBLE
            if (isAdmin) {
                binding.tvOtpSessionNotice.text = "Tài khoản Quản trị viên (admin@gmail.com) được miễn xác thực OTP."
                binding.tvReVerifyOtp.visibility = View.GONE
            } else {
                binding.tvOtpSessionNotice.text = "Email đã được xác minh OTP trên thiết bị (còn hạn ${remainingHours}h). Bỏ qua bước nhập OTP."
                binding.tvReVerifyOtp.visibility = View.VISIBLE
            }
        } else {
            binding.layoutOtp.visibility = View.VISIBLE
            binding.layoutOtpSessionNotice.visibility = View.GONE
            binding.tvReVerifyOtp.visibility = View.VISIBLE
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

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        otpCountDownTimer?.cancel()
    }
}
