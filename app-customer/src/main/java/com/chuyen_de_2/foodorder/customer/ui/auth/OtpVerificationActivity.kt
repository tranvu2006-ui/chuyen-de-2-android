package com.chuyen_de_2.foodorder.customer.ui.auth

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.OtpSessionManager
import com.chuyen_de_2.foodorder.core.util.RegistrationOtpManager
import com.chuyen_de_2.foodorder.customer.databinding.ActivityOtpVerificationBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Màn hình xác thực OTP riêng biệt cho luồng đăng ký tài khoản khách hàng.
 * Tài khoản Firebase chỉ thực sự được tạo sau khi xác minh mã OTP thành công tại màn hình này.
 */
@AndroidEntryPoint
class OtpVerificationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_CONFIRM_PASSWORD = "extra_confirm_password"
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_ROLE = "extra_role"
    }

    private lateinit var binding: ActivityOtpVerificationBinding
    private val viewModel: AuthViewModel by viewModels()

    private var name: String = ""
    private var email: String = ""
    private var password: String = ""
    private var confirmPassword: String = ""
    private var phone: String = ""
    private var role: String = User.Role.CUSTOMER

    private var countDownTimer: CountDownTimer? = null
    private var isResendEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)

        name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty().trim().lowercase()
        password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
        confirmPassword = intent.getStringExtra(EXTRA_CONFIRM_PASSWORD).orEmpty()
        phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        role = intent.getStringExtra(EXTRA_ROLE)?.takeIf { it.isNotBlank() } ?: User.Role.CUSTOMER

        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Thông tin đăng ký không hợp lệ", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        observeViewModel()
        startCountdown()
    }

    private fun setupUI() {
        binding.tvTargetEmail.text = email

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.etOtp.doAfterTextChanged { s ->
            binding.tilOtp.error = null
            val code = s?.toString()?.trim().orEmpty()
            if (code.length == 6 && code.all { it.isDigit() } && viewModel.isLoading.value != true) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.etOtp.windowToken, 0)
                submitOtpVerification(code)
            }
        }

        binding.btnResendOtp.setOnClickListener {
            if (!isResendEnabled) return@setOnClickListener
            binding.tilOtp.error = null
            isResendEnabled = false
            binding.btnResendOtp.isEnabled = false
            viewModel.requestEmailOtp(email)
        }

        binding.btnConfirmRegister.setOnClickListener {
            val code = binding.etOtp.text?.toString()?.trim().orEmpty()
            if (code.length != 6 || !code.all { it.isDigit() }) {
                binding.tilOtp.error = "Vui lòng nhập đúng 6 chữ số mã OTP"
                binding.etOtp.requestFocus()
                return@setOnClickListener
            }
            submitOtpVerification(code)
        }
    }

    private fun submitOtpVerification(code: String) {
        viewModel.registerWithOtp(
            name = name,
            email = email,
            password = password,
            confirmPassword = confirmPassword,
            role = role,
            phone = phone,
            otpCode = code
        )
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnConfirmRegister.isEnabled = !isLoading
            binding.etOtp.isEnabled = !isLoading
        }

        viewModel.otpRequestResult.observe(this) { result ->
            result.onSuccess {
                RegistrationOtpManager.recordOtpSent(this, email)
                Toast.makeText(this, "Đã gửi lại mã OTP đến $email. Vui lòng kiểm tra email!", Toast.LENGTH_LONG).show()
                startCountdown(60_000L)
                binding.etOtp.requestFocus()
            }.onFailure { error ->
                val rawMsg = error.message.orEmpty()
                if (RegistrationOtpManager.isRateLimitError(rawMsg)) {
                    val remaining = RegistrationOtpManager.getRemainingCooldownMs(this, email)
                    Toast.makeText(this, "Mã OTP đã được gửi trước đó vẫn còn hiệu lực. Vui lòng kiểm tra email!", Toast.LENGTH_SHORT).show()
                    startCountdown(if (remaining > 0L) remaining else 30_000L)
                } else {
                    isResendEnabled = true
                    binding.btnResendOtp.isEnabled = true
                    binding.btnResendOtp.text = "Gửi lại ngay"
                    Toast.makeText(this, error.message ?: "Không gửi được mã OTP", Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.registerResult.observe(this) { result ->
            result.onSuccess {
                countDownTimer?.cancel()
                RegistrationOtpManager.clearPendingOtp(this, email)
                OtpSessionManager.markEmailVerified(this, email)
                Toast.makeText(
                    this,
                    "Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay.",
                    Toast.LENGTH_LONG
                ).show()
                setResult(RESULT_OK)
                finish()
            }.onFailure { error ->
                val rawMsg = error.message.orEmpty()
                val userMsg = if (
                    rawMsg.contains("expired", ignoreCase = true) ||
                    rawMsg.contains("invalid", ignoreCase = true) ||
                    rawMsg.contains("token", ignoreCase = true) ||
                    rawMsg.contains("otp", ignoreCase = true)
                ) {
                    "Mã OTP không đúng hoặc đã hết hạn. Vui lòng bấm 'Gửi lại' để nhận mã mới."
                } else {
                    rawMsg.ifBlank { "Xác thực đăng ký thất bại." }
                }
                binding.tilOtp.error = userMsg
                binding.etOtp.requestFocus()
                Toast.makeText(this, userMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startCountdown(durationMs: Long = -1L) {
        countDownTimer?.cancel()
        val remainingMs = if (durationMs >= 0L) {
            durationMs
        } else {
            val cooldown = RegistrationOtpManager.getRemainingCooldownMs(this, email)
            if (cooldown > 0L) cooldown else 0L
        }

        if (remainingMs <= 0L) {
            isResendEnabled = true
            binding.btnResendOtp.isEnabled = true
            binding.btnResendOtp.text = "Gửi lại ngay"
            return
        }

        isResendEnabled = false
        binding.btnResendOtp.isEnabled = false
        countDownTimer = object : CountDownTimer(remainingMs, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished + 999) / 1000
                binding.btnResendOtp.text = "Gửi lại (${sec}s)"
            }

            override fun onFinish() {
                isResendEnabled = true
                binding.btnResendOtp.isEnabled = true
                binding.btnResendOtp.text = "Gửi lại ngay"
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
