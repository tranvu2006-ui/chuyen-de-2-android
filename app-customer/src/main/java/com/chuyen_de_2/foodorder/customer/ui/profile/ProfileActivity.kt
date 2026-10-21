package com.chuyen_de_2.foodorder.customer.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    private var currentName: String = ""
    private var currentPhone: String = ""

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
            binding.tvUserEmail.text = currentUser.email ?: "Chưa có email"

            // Tải thông tin chi tiết từ node users/{uid} và customers/{uid} (đồng thời tự động đồng bộ email/name thật)
            lifecycleScope.launch {
                val userResult = authRepository.getCurrentUserData()
                userResult.onSuccess { user ->
                    currentName = user.name.ifBlank { currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "Khách hàng" }
                    currentPhone = user.phone
                    binding.tvUserName.text = currentName
                    if (user.phone.isNotBlank()) {
                        binding.tvUserPhone.text = user.phone
                    } else {
                        binding.tvUserPhone.text = "Chưa cập nhật SĐT (Chạm để thêm)"
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

    private fun showEditProfileDialog() {
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (12 * density).toInt(), pad, 0)
        }

        val etName = EditText(this).apply {
            hint = "Họ và tên"
            setText(currentName)
        }
        val etPhone = EditText(this).apply {
            hint = "Số điện thoại liên hệ"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(currentPhone)
        }

        container.addView(etName)
        container.addView(etPhone)

        AlertDialog.Builder(this)
            .setTitle("Cập nhật thông tin cá nhân")
            .setView(container)
            .setPositiveButton("Lưu") { _, _ ->
                val newName = etName.text.toString().trim()
                val newPhone = etPhone.text.toString().trim()
                if (newName.isBlank()) {
                    Toast.makeText(this, "Vui lòng nhập họ tên", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val res = authRepository.updateUserProfile(newName, newPhone)
                    res.onSuccess {
                        currentName = newName
                        currentPhone = newPhone
                        binding.tvUserName.text = newName
                        binding.tvUserPhone.text = newPhone.ifBlank { "Chưa cập nhật SĐT (Chạm để thêm)" }
                        Toast.makeText(this@ProfileActivity, "Đã cập nhật hồ sơ lên hệ thống!", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this@ProfileActivity, "Lỗi cập nhật: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun setupListeners() {
        binding.tvUserName.setOnClickListener { showEditProfileDialog() }
        binding.tvUserPhone.setOnClickListener { showEditProfileDialog() }

        // Lịch sử đơn hàng
        binding.btnOrderHistory.setOnClickListener {
            startActivity(Intent(this, OrderHistoryActivity::class.java))
        }

        // Gửi khiếu nại
        binding.btnComplaint.setOnClickListener {
            startActivity(Intent(this, ComplaintActivity::class.java))
        }

        // Địa chỉ nhận hàng & SĐT
        binding.btnAddressBook.setOnClickListener {
            showEditProfileDialog()
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
                putExtra("LOGOUT", true)
            }
            startActivity(intent)
        }
    }
}
