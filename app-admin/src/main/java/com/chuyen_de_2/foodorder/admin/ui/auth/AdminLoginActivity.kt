package com.chuyen_de_2.foodorder.admin.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chuyen_de_2.foodorder.admin.databinding.ActivityAdminLoginBinding
import com.chuyen_de_2.foodorder.admin.ui.main.AdminMainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminLoginBinding
    private val viewModel: AdminAuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupListeners()
        observeViewModel()

        viewModel.checkExistingSession()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Đặt chiều cao thanh spacer bằng đúng chiều cao status bar
            binding.viewLoginStatusBar.updateLayoutParams {
                height = insets.top
            }

            // Đặt padding dưới để tránh bị thanh cử chỉ hệ thống che
            binding.loginRootConstraint.updatePadding(
                bottom = insets.bottom + (24 * resources.displayMetrics.density).toInt()
            )

            windowInsets
        }
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text?.toString().orEmpty()
            val pass = binding.etPassword.text?.toString().orEmpty()
            binding.tvError.visibility = View.GONE
            viewModel.login(email, pass)
        }

        binding.chipAdmin1.setOnClickListener {
            binding.etEmail.setText("admin@gmail.com")
            binding.etPassword.setText("123456")
            binding.tvError.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { state ->
                    when (state) {
                        is AdminAuthState.Idle -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                        }
                        is AdminAuthState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.btnLogin.isEnabled = false
                            binding.tvError.visibility = View.GONE
                        }
                        is AdminAuthState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            val displayName = state.user.name.ifBlank { state.user.email }
                            Toast.makeText(
                                this@AdminLoginActivity,
                                "Chào mừng Quản trị viên: $displayName",
                                Toast.LENGTH_SHORT
                            ).show()
                            val intent = Intent(this@AdminLoginActivity, AdminMainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                        is AdminAuthState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                            binding.tvError.text = state.message
                            binding.tvError.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }
}
