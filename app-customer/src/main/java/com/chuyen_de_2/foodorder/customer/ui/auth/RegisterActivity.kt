package com.chuyen_de_2.foodorder.customer.ui.auth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Màn hình đăng ký tài khoản khách hàng, tích hợp luồng xác thực mã OTP qua Email.
 */
@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels()

    private var pendingRegistration: ((String) -> Unit)? = null
    private var pendingTarget = ""
    private var otpSent = false
    private var isTargetVerified = false
    private var otpDialog: AlertDialog? = null

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

            pendingRegistration = { vEmail ->
                viewModel.register(
                    name = name,
                    email = email,
                    password = password,
                    confirmPassword = confirmPassword,
                    role = role,
                    phone = phone,
                    verifiedEmail = vEmail
                )
            }

            if (pendingTarget.equals(email, ignoreCase = true)) {
                if (otpSent) showOtpDialog() else viewModel.requestEmailOtp(email)
                return@setOnClickListener
            }
            otpSent = false
            isTargetVerified = false
            pendingTarget = email
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
                    dialog.setMessage("Mã mới đã gửi đến $pendingTarget. Nhập mã mới nhất.")
                    Toast.makeText(this, "Đã gửi lại mã OTP!", Toast.LENGTH_SHORT).show()
                } else {
                    showOtpDialog()
                }
            }.onFailure { error ->
                otpDialog?.takeIf { it.isShowing }
                    ?.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = true
                Toast.makeText(this, error.message ?: "Không gửi được mã OTP", Toast.LENGTH_LONG).show()
                if (otpSent && otpDialog?.isShowing != true && !isTargetVerified) {
                    showOtpDialog()
                }
            }
        }

        viewModel.otpVerifyResult.observe(this) { result ->
            result.onSuccess { verifiedValue ->
                isTargetVerified = true
                pendingRegistration?.invoke(verifiedValue)
                    ?: Toast.makeText(this, "Vui lòng bấm Đăng ký lại để hoàn tất", Toast.LENGTH_LONG).show()
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
                resetOtpState()
                Toast.makeText(
                    this,
                    "Đã xác minh thành công! Tài khoản đã được tạo, bạn có thể đăng nhập.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }.onFailure { error ->
                resetOtpState()
                Toast.makeText(this, error.message ?: "Đăng ký thất bại", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Hiển thị hộp thoại nhập mã OTP 6 số qua Email.
     */
    private fun showOtpDialog() {
        val target = binding.etEmail.text?.toString()?.trim().orEmpty()
        val codeInput = EditText(this).apply {
            hint = "Nhập mã OTP 6 chữ số"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        val title = "Xác nhận Email"
        val message = "Nhập mã OTP đã gửi đến $target"

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
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
                viewModel.requestEmailOtp(target)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = codeInput.text.toString().trim()
                if (!code.matches(Regex("\\d{6}"))) {
                    codeInput.error = "Mã OTP phải gồm đúng 6 chữ số"
                } else {
                    dialog.dismiss()
                    viewModel.verifyEmailOtp(target, code)
                }
            }
        }
        dialog.show()
    }

    private fun resetOtpState() {
        pendingRegistration = null
        pendingTarget = ""
        otpSent = false
        isTargetVerified = false
    }
}
