package com.chuyen_de_2.foodorder.internal.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.internal.databinding.ActivityRegisterInternalBinding
import com.chuyen_de_2.foodorder.internal.ui.dashboard.DashboardActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterInternalBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterInternalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnSubmitRegister.setOnClickListener {
            val name = binding.etRegisterName.text.toString().trim()
            val phone = binding.etRegisterPhone.text.toString().trim()
            val email = binding.etRegisterEmail.text.toString().trim()
            val pass = binding.etRegisterPassword.text.toString().trim()
            val confirmPass = binding.etRegisterConfirmPassword.text.toString().trim()

            val role = User.Role.MERCHANT

            if (phone.isBlank()) {
                Toast.makeText(this, "Vui lòng nhập số điện thoại liên hệ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.register(name, email, pass, confirmPass, role, phone)
        }

        binding.tvBackToLogin.setOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSubmitRegister.isEnabled = !isLoading
        }

        viewModel.registerResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "🎉 Đăng ký tài khoản thành công! Vui lòng hoàn tất phiếu địa chỉ và xác minh thông tin quán.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, StoreVerificationActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "Đăng ký thất bại", Toast.LENGTH_LONG).show()
            }
        }
    }
}
