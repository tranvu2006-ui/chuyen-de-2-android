package com.chuyen_de_2.foodorder.customer.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.databinding.ActivityProfileBinding
import com.chuyen_de_2.foodorder.customer.ui.auth.LoginActivity
import com.chuyen_de_2.foodorder.customer.ui.complaint.ComplaintActivity
import com.chuyen_de_2.foodorder.customer.ui.order.OrderHistoryActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Trang cá nhân & Điểm uy tín chuẩn b11.html
 */
@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)
        InsetUtils.applyToolbarInsets(binding.toolbar)

        setupToolbar()
        loadUserProfile()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadUserProfile() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            binding.tvUserEmail.text = currentUser.email ?: "customer@oishifood.vn"
            
            // Tải thông tin chi tiết từ node users/{uid} và customers/{uid}
            lifecycleScope.launch {
                val userResult = authRepository.getCurrentUserData()
                userResult.onSuccess { user ->
                    binding.tvUserName.text = user.name.ifBlank { currentUser.displayName ?: "Người dùng Oishi" }
                    if (user.phone.isNotBlank()) {
                        binding.tvUserPhone.text = user.phone
                    } else {
                        binding.tvUserPhone.text = "Chưa cập nhật SĐT"
                    }
                    
                    // Hiển thị vai trò nếu là Merchant / Driver
                    when (user.role) {
                        com.chuyen_de_2.foodorder.core.data.model.User.Role.MERCHANT,
                        com.chuyen_de_2.foodorder.core.data.model.User.Role.MANAGER -> {
                            binding.tvTrustBadge.text = "🏪 Đối tác Quán"
                        }
                        com.chuyen_de_2.foodorder.core.data.model.User.Role.DRIVER,
                        com.chuyen_de_2.foodorder.core.data.model.User.Role.SHIPPER -> {
                            binding.tvTrustBadge.text = "🛵 Tài xế Giao Hàng"
                        }
                        else -> {
                            // CUSTOMER
                        }
                    }
                }

                val customerResult = authRepository.getCurrentCustomerData()
                customerResult.onSuccess { customer ->
                    binding.tvTrustScoreNumber.text = customer.trustScore.toString()
                    binding.tvTrustBadge.text = when {
                        customer.trustScore >= 90 -> "Uy Tín Cao 🛡️"
                        customer.trustScore >= 70 -> "Uy Tín Tốt ✨"
                        else -> "Cần Cải Thiện ⚠️"
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        // Lịch sử đơn hàng
        binding.btnOrderHistory.setOnClickListener {
            startActivity(Intent(this, OrderHistoryActivity::class.java))
        }

        // Gửi khiếu nại
        binding.btnComplaint.setOnClickListener {
            startActivity(Intent(this, ComplaintActivity::class.java))
        }

        // Địa chỉ nhận hàng
        binding.btnAddressBook.setOnClickListener {
            Toast.makeText(this, "📍 Địa chỉ mặc định: 88 Đồng Khởi, P. Bến Nghé, Quận 1, TP.HCM", Toast.LENGTH_LONG).show()
        }

        // Hotline
        binding.btnHotline.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:19001234")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Hotline: 1900 1234", Toast.LENGTH_SHORT).show()
            }
        }

        // Đăng xuất
        binding.btnLogout.setOnClickListener {
            authRepository.logout()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }
}
