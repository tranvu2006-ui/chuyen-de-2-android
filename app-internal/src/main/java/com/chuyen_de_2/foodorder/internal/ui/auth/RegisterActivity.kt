package com.chuyen_de_2.foodorder.internal.ui.auth

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
import com.chuyen_de_2.foodorder.core.util.OtpSessionManager
import com.chuyen_de_2.foodorder.internal.databinding.ActivityRegisterInternalBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Màn hình đăng ký đối tác (Chủ quán/Tài xế), tích hợp xác thực mã OTP trực tiếp trên giao diện (inline OTP).
 */
@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterInternalBinding
    private val viewModel: AuthViewModel by viewModels()

    private var otpCountDownTimer: CountDownTimer? = null
    private var pendingRegistration: ((String) -> Unit)? = null
    private var currentOtpEmail = ""

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterInternalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)
        checkNotificationPermission()
        setupUI()
        observeViewModel()
        updateOtpSessionUI(binding.etRegisterEmail.text?.toString().orEmpty())
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupUI() {
        binding.etRegisterName.doAfterTextChanged { binding.tilRegisterName.error = null }
        binding.etRegisterPhone.doAfterTextChanged { binding.tilRegisterPhone.error = null }
        binding.etRegisterEmail.doAfterTextChanged {
            binding.tilRegisterEmail.error = null
            binding.tilRegisterOtp.error = null
            val email = it?.toString()?.trim().orEmpty()
            updateOtpSessionUI(email)
        }

        binding.tvReVerifyOtp.setOnClickListener {
            val email = binding.etRegisterEmail.text?.toString()?.trim().orEmpty()
            OtpSessionManager.clearSession(this, email)
            updateOtpSessionUI(email)
            binding.etRegisterOtp.requestFocus()
        }
        binding.etRegisterPassword.doAfterTextChanged { binding.tilRegisterPassword.error = null }
        binding.etRegisterConfirmPassword.doAfterTextChanged { binding.tilRegisterConfirmPassword.error = null }
        binding.etRegisterOtp.doAfterTextChanged { binding.tilRegisterOtp.error = null }

        // Nút "Lấy mã" kế bên ô nhập mã OTP
        binding.btnGetRegisterOtp.setOnClickListener {
            val email = binding.etRegisterEmail.text.toString().trim()
            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilRegisterEmail.error = "Vui lòng nhập địa chỉ email hợp lệ"
                binding.etRegisterEmail.requestFocus()
                return@setOnClickListener
            }
            binding.tilRegisterEmail.error = null
            binding.tilRegisterOtp.error = null
            currentOtpEmail = email
            binding.btnGetRegisterOtp.isEnabled = false
            binding.btnGetRegisterOtp.text = "Đang gửi..."
            viewModel.requestEmailOtp(email)
        }

        // Nút "Đăng ký"
        binding.btnSubmitRegister.setOnClickListener {
            val name = binding.etRegisterName.text.toString().trim()
            val phone = binding.etRegisterPhone.text.toString().trim()
            val email = binding.etRegisterEmail.text.toString().trim()
            val password = binding.etRegisterPassword.text.toString()
            val confirmPassword = binding.etRegisterConfirmPassword.text.toString()
            val otpCode = binding.etRegisterOtp.text.toString().trim()

            if (name.isBlank()) {
                binding.tilRegisterName.error = "Vui lòng nhập họ tên"
                binding.etRegisterName.requestFocus()
                return@setOnClickListener
            }
            if (phone.isBlank()) {
                binding.tilRegisterPhone.error = "Vui lòng nhập số điện thoại"
                binding.etRegisterPhone.requestFocus()
                return@setOnClickListener
            }
            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilRegisterEmail.error = "Vui lòng nhập email hợp lệ"
                binding.etRegisterEmail.requestFocus()
                return@setOnClickListener
            }
            if (password.length < 6) {
                binding.tilRegisterPassword.error = "Mật khẩu phải từ 6 ký tự trở lên"
                binding.etRegisterPassword.requestFocus()
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                binding.tilRegisterConfirmPassword.error = "Mật khẩu xác nhận không khớp"
                binding.etRegisterConfirmPassword.requestFocus()
                return@setOnClickListener
            }
            currentOtpEmail = email
            val hasSession = OtpSessionManager.isSessionValid(this, email)
            if (hasSession) {
                // Email đã có phiên xác minh OTP hợp lệ trong 24h -> Bỏ qua OTP, đăng ký thẳng
                viewModel.register(
                    name = name,
                    email = email,
                    pass = password,
                    confirmPass = confirmPassword,
                    role = User.Role.MERCHANT,
                    phone = phone
                )
            } else {
                if (otpCode.length != 6 || !otpCode.all { it.isDigit() }) {
                    binding.tilRegisterOtp.error = "Vui lòng bấm 'Lấy mã' và nhập đúng 6 chữ số OTP"
                    binding.etRegisterOtp.requestFocus()
                    return@setOnClickListener
                }
                viewModel.registerWithOtp(
                    name = name,
                    email = email,
                    pass = password,
                    confirmPass = confirmPassword,
                    role = User.Role.MERCHANT,
                    phone = phone,
                    otpCode = otpCode
                )
            }
        }

        binding.tvBackToLogin.setOnClickListener { finish() }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSubmitRegister.isEnabled = !isLoading
        }

        viewModel.otpRequestResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "Đã gửi mã OTP đến $currentOtpEmail. Vui lòng kiểm tra email!", Toast.LENGTH_LONG).show()
                startOtpCountdown()
                binding.etRegisterOtp.requestFocus()
            }.onFailure { error ->
                binding.btnGetRegisterOtp.isEnabled = true
                binding.btnGetRegisterOtp.text = "Lấy mã"
                val msg = error.message ?: "Không gửi được mã OTP"
                binding.tilRegisterEmail.error = msg
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.otpVerifyResult.observe(this) { result ->
            result.onSuccess { verifiedValue ->
                binding.tilRegisterOtp.error = null
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
                binding.tilRegisterOtp.error = userMessage
                binding.etRegisterOtp.requestFocus()
                Toast.makeText(this, userMessage, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.registerResult.observe(this) { result ->
            result.onSuccess {
                otpCountDownTimer?.cancel()
                val email = binding.etRegisterEmail.text.toString().trim()
                OtpSessionManager.markEmailVerified(this, email)
                Toast.makeText(
                    this,
                    "Đăng ký đối tác thành công! Bạn có thể đăng nhập ngay.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "Đăng ký thất bại", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateOtpSessionUI(email: String) {
        val hasSession = OtpSessionManager.isSessionValid(this, email)
        if (hasSession) {
            val remainingHours = OtpSessionManager.getRemainingHours(this, email)
            binding.layoutRegisterOtp.visibility = View.GONE
            binding.layoutOtpSessionNotice.visibility = View.VISIBLE
            binding.tvOtpSessionNotice.text = "Email đã được xác minh OTP trên thiết bị (còn hạn ${remainingHours}h). Bỏ qua bước nhập OTP."
        } else {
            binding.layoutRegisterOtp.visibility = View.VISIBLE
            binding.layoutOtpSessionNotice.visibility = View.GONE
        }
    }

    private fun startOtpCountdown() {
        otpCountDownTimer?.cancel()
        binding.btnGetRegisterOtp.isEnabled = false
        otpCountDownTimer = object : CountDownTimer(60_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                binding.btnGetRegisterOtp.text = "${sec}s"
            }

            override fun onFinish() {
                binding.btnGetRegisterOtp.isEnabled = true
                binding.btnGetRegisterOtp.text = "Gửi lại"
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        otpCountDownTimer?.cancel()
    }
}
