package com.chuyen_de_2.foodorder.customer.ui.auth

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.PhoneNumberUtils
import com.chuyen_de_2.foodorder.customer.databinding.ActivityRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels()
    private var pendingRegistration: ((String, String) -> Unit)? = null
    private var pendingEmail = ""
    private var pendingPhone = ""
    private var emailOtpSent = false
    private var emailVerified = false
    private var phoneOtpSent = false
    private var otpDialog: AlertDialog? = null
    private var phoneOtpDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val normalizedPhone = try {
                PhoneNumberUtils.toE164Vietnam(phone)
            } catch (error: IllegalArgumentException) {
                binding.tilPhone.error = error.message
                return@setOnClickListener
            }
            binding.tilPhone.error = null
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            val role = when (binding.rgRole.checkedRadioButtonId) {
                com.chuyen_de_2.foodorder.customer.R.id.rbMerchant -> User.Role.MERCHANT
                com.chuyen_de_2.foodorder.customer.R.id.rbDriver -> User.Role.DRIVER
                else -> User.Role.CUSTOMER
            }
            if (name.isBlank() || email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Vui lòng nhập họ tên, số điện thoại, email và mật khẩu", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (password != confirmPassword || password.length < 6) {
                Toast.makeText(
                    this,
                    if (password != confirmPassword) "Mật khẩu không khớp" else "Mật khẩu phải ít nhất 6 ký tự",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            pendingRegistration = { verifiedEmail, verifiedPhone ->
                viewModel.register(name, email, password, confirmPassword, role, phone, verifiedEmail, verifiedPhone)
            }
            if (pendingEmail.equals(email, ignoreCase = true) && pendingPhone == normalizedPhone) {
                when {
                    !emailOtpSent -> viewModel.requestEmailOtp(email)
                    !emailVerified -> showOtpDialog()
                    !phoneOtpSent -> viewModel.requestPhoneOtp(normalizedPhone)
                    else -> showPhoneOtpDialog()
                }
                return@setOnClickListener
            }
            emailOtpSent = false
            emailVerified = false
            phoneOtpSent = false
            pendingEmail = email
            pendingPhone = normalizedPhone
            viewModel.requestEmailOtp(email)
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
                emailOtpSent = true
                val dialog = otpDialog
                if (dialog?.isShowing == true) {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = true
                    dialog.setMessage("Mã mới đã gửi đến $pendingEmail. Nhập mã mới nhất.")
                    Toast.makeText(this, "Đã gửi mã OTP mới", Toast.LENGTH_SHORT).show()
                } else {
                    showOtpDialog()
                }
            }.onFailure { error ->
                otpDialog?.takeIf { it.isShowing }
                    ?.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = true
                Toast.makeText(this, error.message ?: "Không gửi được mã OTP", Toast.LENGTH_LONG).show()
                if (emailOtpSent && otpDialog?.isShowing != true && !emailVerified) showOtpDialog()
                else if (!emailOtpSent) {
                    pendingRegistration = null
                    pendingEmail = ""
                    pendingPhone = ""
                }
            }
        }

        viewModel.otpVerifyResult.observe(this) { result ->
            result.onSuccess { verifiedEmail ->
                emailVerified = true
                Toast.makeText(this, "Email đã xác minh. Đang gửi OTP qua SMS...", Toast.LENGTH_SHORT).show()
                viewModel.requestPhoneOtp(pendingPhone)
            }.onFailure { error ->
                val message = error.message.orEmpty()
                val userMessage = if (
                    message.contains("expired", ignoreCase = true) ||
                    message.contains("invalid", ignoreCase = true) ||
                    message.contains("token", ignoreCase = true)
                ) {
                    "Mã OTP sai hoặc hết hạn. Chọn “Gửi mã mới” rồi nhập mã mới nhất."
                } else {
                    message.ifBlank { "Không xác minh được mã OTP" }
                }
                Toast.makeText(this, userMessage, Toast.LENGTH_LONG).show()
                if (pendingRegistration != null && !emailVerified) showOtpDialog()
            }
        }

        viewModel.phoneOtpRequestResult.observe(this) { result ->
            result.onSuccess {
                phoneOtpSent = true
                val dialog = phoneOtpDialog
                if (dialog?.isShowing == true) {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = true
                    dialog.setMessage("Mã SMS mới đã gửi đến $pendingPhone. Nhập mã mới nhất.")
                    Toast.makeText(this, "Đã gửi SMS OTP mới", Toast.LENGTH_SHORT).show()
                } else {
                    showPhoneOtpDialog()
                }
            }.onFailure { error ->
                phoneOtpDialog?.takeIf { it.isShowing }
                    ?.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = true
                Toast.makeText(
                    this,
                    error.message ?: "Không gửi được SMS OTP. Kiểm tra cấu hình nhà cung cấp SMS trong Supabase.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        viewModel.phoneOtpVerifyResult.observe(this) { result ->
            result.onSuccess { verifiedPhone ->
                val verifiedEmail = pendingEmail
                pendingRegistration?.invoke(verifiedEmail, verifiedPhone)
                    ?: Toast.makeText(this, "Vui lòng đăng ký lại để tiếp tục", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "Mã SMS OTP không hợp lệ", Toast.LENGTH_LONG).show()
                if (pendingRegistration != null) showPhoneOtpDialog()
            }
        }

        viewModel.registerResult.observe(this) { result ->
            result.onSuccess {
                pendingRegistration = null
                pendingEmail = ""
                resetOtpState()
                Toast.makeText(
                    this,
                    "Email và số điện thoại đã xác nhận. Tài khoản đã tạo, bạn có thể đăng nhập.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }.onFailure { error ->
                pendingRegistration = null
                pendingEmail = ""
                resetOtpState()
                Toast.makeText(this, error.message ?: "Đăng ký thất bại", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showOtpDialog() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val codeInput = EditText(this).apply {
            hint = "Nhập mã OTP 6 hoặc 8 chữ số"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(8))
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Xác nhận email")
            .setMessage("Nhập mã OTP đã gửi đến $email")
            .setView(codeInput)
            .setPositiveButton("Xác nhận", null)
            .setNeutralButton("Gửi mã mới", null)
            .setNegativeButton("Hủy", null)
            .create()
        otpDialog = dialog
        dialog.setOnDismissListener {
            if (otpDialog === dialog) otpDialog = null
        }
        dialog.setOnShowListener {
            codeInput.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                it.isEnabled = false
                codeInput.text?.clear()
                viewModel.requestEmailOtp(email)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = codeInput.text.toString().trim()
                if (!code.matches(Regex("(\\d{6}|\\d{8})"))) {
                    codeInput.error = "Mã OTP phải gồm đúng 6 hoặc 8 chữ số"
                } else {
                    dialog.dismiss()
                    viewModel.verifyEmailOtp(email, code)
                }
            }
        }
        dialog.show()
    }

    private fun showPhoneOtpDialog() {
        val phone = pendingPhone
        val codeInput = EditText(this).apply {
            hint = "Nhập mã SMS OTP 6 chữ số"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Xác nhận số điện thoại")
            .setMessage("Nhập mã SMS đã gửi đến $phone")
            .setView(codeInput)
            .setPositiveButton("Xác nhận", null)
            .setNeutralButton("Gửi mã mới", null)
            .setNegativeButton("Hủy", null)
            .create()
        phoneOtpDialog = dialog
        dialog.setOnDismissListener {
            if (phoneOtpDialog === dialog) phoneOtpDialog = null
        }
        dialog.setOnShowListener {
            codeInput.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                it.isEnabled = false
                codeInput.text?.clear()
                viewModel.requestPhoneOtp(phone)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = codeInput.text.toString().trim()
                if (!code.matches(Regex("\\d{6}"))) {
                    codeInput.error = "Mã SMS OTP phải gồm đúng 6 chữ số"
                } else {
                    dialog.dismiss()
                    viewModel.verifyPhoneOtp(phone, code)
                }
            }
        }
        dialog.show()
    }

    private fun resetOtpState() {
        pendingRegistration = null
        pendingEmail = ""
        pendingPhone = ""
        emailOtpSent = false
        emailVerified = false
        phoneOtpSent = false
    }
}
