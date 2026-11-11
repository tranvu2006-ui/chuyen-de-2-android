package com.chuyen_de_2.foodorder.customer.ui.auth

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.KeyboardUtils
import com.chuyen_de_2.foodorder.core.util.OtpSessionManager
import com.chuyen_de_2.foodorder.core.util.RegistrationOtpManager
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityOtpVerificationBinding
import com.chuyen_de_2.foodorder.customer.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Màn hình xác thực OTP dùng chung cho cả luồng Đăng ký và Đăng nhập.
 * Thiết kế giao diện hiện đại với 6 ô vuông nhập số OTP, thẻ email nổi bật,
 * bộ đếm ngược thời gian và nút gửi lại trực quan.
 */
@AndroidEntryPoint
class OtpVerificationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FLOW = "extra_flow"
        const val FLOW_REGISTER = "flow_register"
        const val FLOW_LOGIN = "flow_login"

        const val EXTRA_NAME = "extra_name"
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_CONFIRM_PASSWORD = "extra_confirm_password"
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_ROLE = "extra_role"
    }

    private lateinit var binding: ActivityOtpVerificationBinding
    private val viewModel: AuthViewModel by viewModels()

    private var flow: String = FLOW_REGISTER
    private var name: String = ""
    private var email: String = ""
    private var password: String = ""
    private var confirmPassword: String = ""
    private var phone: String = ""
    private var role: String = User.Role.CUSTOMER

    private var countDownTimer: CountDownTimer? = null
    private var isResendEnabled = false

    private val otpBoxes: List<TextView> by lazy {
        listOf(
            binding.tvOtp1,
            binding.tvOtp2,
            binding.tvOtp3,
            binding.tvOtp4,
            binding.tvOtp5,
            binding.tvOtp6
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
        InsetUtils.applyImeAndSystemInsets(binding.root)

        flow = intent.getStringExtra(EXTRA_FLOW) ?: FLOW_REGISTER
        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty().trim().lowercase()
        password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()

        if (flow == FLOW_LOGIN) {
            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Thông tin đăng nhập không hợp lệ", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        } else {
            name = intent.getStringExtra(EXTRA_NAME).orEmpty()
            confirmPassword = intent.getStringExtra(EXTRA_CONFIRM_PASSWORD).orEmpty()
            phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
            role = intent.getStringExtra(EXTRA_ROLE)?.takeIf { it.isNotBlank() } ?: User.Role.CUSTOMER

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Thông tin đăng ký không hợp lệ", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        setupUI()
        observeViewModel()
        startCountdown()
    }

    private fun setupUI() {
        binding.tvTargetEmail.text = email

        if (flow == FLOW_LOGIN) {
            binding.tvTitle.text = "Xác Thực Đăng Nhập"
            binding.tvSubtitle.text = "Mã xác thực gồm 6 chữ số đã được gửi đến:"
            binding.btnConfirmRegister.text = "Xác Nhận & Đăng Nhập"
        } else {
            binding.tvTitle.text = "Xác Thực Mã OTP"
            binding.tvSubtitle.text = "Mã xác thực gồm 6 chữ số đã được gửi đến:"
            binding.btnConfirmRegister.text = "Xác Nhận & Tạo Tài Khoản"
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        // Khởi tạo trạng thái 6 ô số OTP
        updateOtpBoxes("")

        // Nhấn vào khung OTP để kích hoạt bàn phím số
        binding.layoutOtpContainer.setOnClickListener {
            binding.etOtp.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.etOtp, InputMethodManager.SHOW_IMPLICIT)
        }

        // Tự động focus và mở bàn phím số khi vào màn hình
        binding.etOtp.requestFocus()
        binding.etOtp.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.etOtp, InputMethodManager.SHOW_IMPLICIT)
        }, 250)

        binding.etOtp.doAfterTextChanged { s ->
            showOtpError(null)
            val code = s?.toString()?.trim().orEmpty()
            updateOtpBoxes(code)

            if (code.length == 6 && code.all { it.isDigit() } && viewModel.isLoading.value != true) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.etOtp.windowToken, 0)
                submitOtpVerification(code)
            }
        }

        binding.btnResendOtp.setOnClickListener {
            if (!isResendEnabled) return@setOnClickListener
            showOtpError(null)
            isResendEnabled = false
            binding.btnResendOtp.isEnabled = false
            if (flow == FLOW_LOGIN) {
                viewModel.requestLoginOtp(email)
            } else {
                viewModel.requestEmailOtp(email)
            }
        }

        binding.btnConfirmRegister.setOnClickListener {
            val code = binding.etOtp.text?.toString()?.trim().orEmpty()
            if (code.length != 6 || !code.all { it.isDigit() }) {
                showOtpError("Vui lòng nhập đúng 6 chữ số mã OTP")
                binding.etOtp.requestFocus()
                return@setOnClickListener
            }
            submitOtpVerification(code)
        }
    }

    /**
     * Cập nhật giao diện 6 ô số tương ứng với chuỗi ký tự đã nhập.
     */
    private fun updateOtpBoxes(code: String, hasError: Boolean = false) {
        val cleanCode = code.filter { it.isDigit() }
        for (i in otpBoxes.indices) {
            val box = otpBoxes[i]
            if (i < cleanCode.length) {
                box.text = cleanCode[i].toString()
                box.setBackgroundResource(
                    if (hasError) R.drawable.bg_otp_box_error else R.drawable.bg_otp_box_filled
                )
            } else if (i == cleanCode.length) {
                box.text = ""
                box.setBackgroundResource(
                    if (hasError) R.drawable.bg_otp_box_error else R.drawable.bg_otp_box_active
                )
            } else {
                box.text = ""
                box.setBackgroundResource(
                    if (hasError) R.drawable.bg_otp_box_error else R.drawable.bg_otp_box_empty
                )
            }
        }
    }

    private fun showOtpError(errorMsg: String?) {
        if (errorMsg.isNullOrBlank()) {
            binding.tvOtpError.visibility = View.GONE
            binding.tvOtpError.text = ""
            updateOtpBoxes(binding.etOtp.text?.toString().orEmpty(), hasError = false)
        } else {
            binding.tvOtpError.visibility = View.VISIBLE
            binding.tvOtpError.text = errorMsg
            updateOtpBoxes(binding.etOtp.text?.toString().orEmpty(), hasError = true)
        }
    }

    private fun submitOtpVerification(code: String) {
        if (flow == FLOW_LOGIN) {
            viewModel.verifyLoginOtp(email, password, code)
        } else {
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
                    setResendAvailableState()
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
                showOtpError(userMsg)
                binding.etOtp.requestFocus()
                Toast.makeText(this, userMsg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.loginOtpResult.observe(this) { result ->
            result.onSuccess {
                countDownTimer?.cancel()
                RegistrationOtpManager.clearPendingOtp(this, email)
                OtpSessionManager.markEmailVerified(this, email)
                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                checkRoleAndRoute()
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
                    rawMsg.ifBlank { "Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin." }
                }
                showOtpError(userMsg)
                binding.etOtp.requestFocus()
                Toast.makeText(this, userMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkRoleAndRoute() {
        lifecycleScope.launch {
            try {
                val role = viewModel.getUserRole()
                if (role == User.Role.CUSTOMER || role.isNullOrBlank()) {
                    val intent = Intent(this@OtpVerificationActivity, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@OtpVerificationActivity, "Tài khoản đối tác vui lòng dùng App Quán/Tài xế.", Toast.LENGTH_LONG).show()
                    viewModel.logout()
                }
            } catch (_: Exception) {}
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
            setResendAvailableState()
            return
        }

        isResendEnabled = false
        binding.btnResendOtp.isEnabled = false
        binding.ivResendIcon.setImageResource(R.drawable.ic_clock)
        binding.ivResendIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.text_secondary)
        binding.btnResendOtp.setTextColor(ContextCompat.getColor(this, R.color.text_muted))

        countDownTimer = object : CountDownTimer(remainingMs, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished + 999) / 1000
                binding.btnResendOtp.text = "Gửi lại (${sec}s)"
            }

            override fun onFinish() {
                setResendAvailableState()
            }
        }.start()
    }

    private fun setResendAvailableState() {
        isResendEnabled = true
        binding.btnResendOtp.isEnabled = true
        binding.btnResendOtp.text = "Gửi lại ngay"
        binding.ivResendIcon.setImageResource(R.drawable.ic_refresh)
        binding.ivResendIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.primary)
        binding.btnResendOtp.setTextColor(ContextCompat.getColor(this, R.color.primary))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        KeyboardUtils.handleTouchOutsideToHideKeyboard(this, ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
