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
import com.chuyen_de_2.foodorder.customer.databinding.ActivityRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels()
    private var pendingRegistration: ((String) -> Unit)? = null
    private var pendingEmail = ""
    private var otpSent = false
    private var otpDialog: AlertDialog? = null

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
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            val role = when (binding.rgRole.checkedRadioButtonId) {
                com.chuyen_de_2.foodorder.customer.R.id.rbMerchant -> User.Role.MERCHANT
                com.chuyen_de_2.foodorder.customer.R.id.rbDriver -> User.Role.DRIVER
                else -> User.Role.CUSTOMER
            }
            if (name.isBlank() || email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Vui lòng nhập họ tên, email và mật khẩu", Toast.LENGTH_LONG).show()
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
            pendingRegistration = { verifiedEmail ->
                viewModel.register(name, email, password, confirmPassword, role, phone, verifiedEmail)
            }
            if (otpSent && pendingEmail.equals(email, ignoreCase = true)) {
                showOtpDialog()
                return@setOnClickListener
            }
            otpSent = false
            pendingEmail = email
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
                otpSent = true
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
                if (otpSent && otpDialog?.isShowing != true) showOtpDialog()
                else if (!otpSent) {
                    pendingRegistration = null
                    pendingEmail = ""
                }
            }
        }

        viewModel.otpVerifyResult.observe(this) { result ->
            result.onSuccess { verifiedEmail ->
                pendingRegistration?.invoke(verifiedEmail)
                    ?: Toast.makeText(this, "Vui lòng đăng ký lại để tiếp tục", Toast.LENGTH_LONG).show()
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
                if (pendingRegistration != null) showOtpDialog()
            }
        }

        viewModel.registerResult.observe(this) { result ->
            result.onSuccess {
                pendingRegistration = null
                pendingEmail = ""
                otpSent = false
                Toast.makeText(
                    this,
                    "Email đã được xác nhận. Tài khoản đã tạo, bạn có thể đăng nhập.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }.onFailure { error ->
                pendingRegistration = null
                pendingEmail = ""
                otpSent = false
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
}
