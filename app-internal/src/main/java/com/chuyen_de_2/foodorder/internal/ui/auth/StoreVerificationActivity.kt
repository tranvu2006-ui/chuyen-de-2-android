package com.chuyen_de_2.foodorder.internal.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.internal.databinding.ActivityStoreVerificationBinding
import com.chuyen_de_2.foodorder.internal.ui.boss.BossDashboardActivity
import com.chuyen_de_2.foodorder.internal.ui.dashboard.DashboardActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tên chức năng: Phiếu Xác Minh Danh Tính Chủ Quán & Tài Khoản Đối Soát (VietQR)
 * Cách hoạt động:
 *  - Thu thập thông tin định danh chủ sở hữu: Họ tên chủ hộ kinh doanh, CCCD (12 số).
 *  - Thu thập thông tin Tài khoản ngân hàng (VietQR) để hệ thống quyết toán doanh thu.
 *  - Thông tin tên quán và địa chỉ chi nhánh sẽ được tạo/quản lý tại phần Chi nhánh.
 * Luồng hướng tới:
 *  - BOSSMERCHANT -> [BossDashboardActivity] (Quản lý chuỗi chi nhánh).
 *  - MERCHANT -> [DashboardActivity] (Vận hành đơn hàng).
 *  - Bấm Đăng xuất -> [LoginActivity].
 */
@AndroidEntryPoint
class StoreVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoreVerificationBinding

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private var currentStoreId: String = ""

    data class SupportedBank(val name: String, val bin: String)

    private val supportedBanks = listOf(
        SupportedBank("MB Bank (Quân Đội)", "970422"),
        SupportedBank("Vietcombank (Ngoại Thương)", "970436"),
        SupportedBank("Techcombank (Kỹ Thương)", "970407"),
        SupportedBank("VPBank (Việt Nam Thịnh Vượng)", "970432"),
        SupportedBank("ACB (Á Châu)", "970416"),
        SupportedBank("BIDV (Đầu Tư & Phát Triển)", "970418"),
        SupportedBank("Agribank (Nông Nghiệp)", "970405"),
        SupportedBank("TPBank (Tiên Phong)", "970423"),
        SupportedBank("Sacombank (Sài Gòn Thương Tín)", "970403"),
        SupportedBank("VIB (Quốc Tế)", "970441"),
        SupportedBank("OCB (Phương Đông)", "970448")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InsetUtils.initEdgeToEdge(this, true)
        binding = ActivityStoreVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val statusBarHeight = InsetUtils.getFallbackStatusBarHeight(this)
        binding.rootVerification.setPadding(0, statusBarHeight, 0, 0)
        InsetUtils.setLightStatusBar(this, true)

        currentStoreId = intent.getStringExtra("STORE_ID").orEmpty()

        setupSpinners()
        setupListeners()
        loadInitialData()
    }

    private fun setupSpinners() {
        val bankAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, supportedBanks.map { it.name })
        bankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spBankName.adapter = bankAdapter
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            val user = authRepository.currentUser
            if (user == null) {
                finish()
                return@launch
            }

            if (currentStoreId.isBlank()) {
                currentStoreId = firebaseRepository.resolveStoreIdForUser(user.uid)
            }

            if (currentStoreId.isNotBlank()) {
                val store = firebaseRepository.getStoreOnce(currentStoreId)
                store?.let {
                    if (it.ownerName.isNotBlank()) {
                        binding.etOwnerName.setText(it.ownerName)
                    } else if (!user.displayName.isNullOrBlank()) {
                        binding.etOwnerName.setText(user.displayName)
                    }

                    if (it.cccdNumber.isNotBlank()) binding.etCccdNumber.setText(it.cccdNumber)
                    if (it.bankAccountNumber.isNotBlank()) binding.etBankAccountNumber.setText(it.bankAccountNumber)
                    if (it.bankAccountName.isNotBlank()) binding.etBankAccountName.setText(it.bankAccountName)
                    if (it.bankName.isNotBlank()) {
                        val bIndex = supportedBanks.indexOfFirst { b -> b.name.contains(it.bankName, ignoreCase = true) }
                        if (bIndex >= 0) binding.spBankName.setSelection(bIndex)
                    }
                }
            } else {
                if (!user.displayName.isNullOrBlank()) {
                    binding.etOwnerName.setText(user.displayName)
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnSubmitVerification.setOnClickListener {
            validateAndSubmit()
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("🚪 Đăng Xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất tài khoản không?")
                .setPositiveButton("Đăng xuất") { _, _ ->
                    getSharedPreferences("boss_session", MODE_PRIVATE).edit().clear().apply()
                    authRepository.logout()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("LOGOUT", true)
                    })
                    finish()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    private fun validateAndSubmit() {
        binding.tilOwnerName.error = null
        binding.tilCccdNumber.error = null
        binding.tilBankAccountNumber.error = null
        binding.tilBankAccountName.error = null

        val ownerName = binding.etOwnerName.text.toString().trim()
        val cccd = binding.etCccdNumber.text.toString().trim()

        val selectedBank = supportedBanks.getOrNull(binding.spBankName.selectedItemPosition) ?: supportedBanks[0]
        val bankAccount = binding.etBankAccountNumber.text.toString().trim()
        val bankAccountName = binding.etBankAccountName.text.toString().trim().uppercase()

        var hasError = false

        if (ownerName.isBlank()) {
            binding.tilOwnerName.error = "Vui lòng nhập họ và tên chủ hộ kinh doanh"
            hasError = true
        }

        if (cccd.length != 12) {
            binding.tilCccdNumber.error = "Số CCCD phải có đúng 12 chữ số"
            hasError = true
        }

        if (bankAccount.isBlank()) {
            binding.tilBankAccountNumber.error = "Vui lòng nhập số tài khoản ngân hàng"
            hasError = true
        }

        if (bankAccountName.isBlank()) {
            binding.tilBankAccountName.error = "Vui lòng nhập tên chủ tài khoản"
            hasError = true
        }

        if (hasError) {
            Toast.makeText(this, "⚠️ Vui lòng điền đầy đủ các thông tin bắt buộc!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.progressBarVerification.visibility = View.VISIBLE
            binding.btnSubmitVerification.isEnabled = false

            val user = authRepository.currentUser
            val uid = user?.uid.orEmpty()
            if (currentStoreId.isBlank()) {
                currentStoreId = firebaseRepository.resolveStoreIdForUser(uid)
            }
            if (currentStoreId.isBlank()) {
                currentStoreId = "store_${uid}"
            }

            val existingStore = firebaseRepository.getStoreOnce(currentStoreId)
            val storeName = existingStore?.name?.takeIf { it.isNotBlank() } ?: (ownerName.ifBlank { "Quán Ăn" })
            val storePhone = existingStore?.phone?.takeIf { it.isNotBlank() } ?: user?.phoneNumber.orEmpty()
            val storeAddress = existingStore?.address.orEmpty()
            val storeDistrict = existingStore?.district.orEmpty()
            val openHours = existingStore?.openHours?.takeIf { it.isNotBlank() } ?: "08:00 - 22:30"
            val prepTime = existingStore?.prepTime?.takeIf { it.isNotBlank() } ?: "15 phút"
            val lat = existingStore?.latitude ?: 0.0
            val lng = existingStore?.longitude ?: 0.0

            val result = firebaseRepository.verifyAndCompleteStoreProfile(
                storeId = currentStoreId,
                merchantUid = uid,
                name = storeName,
                phone = storePhone,
                address = storeAddress,
                district = storeDistrict,
                ownerName = ownerName,
                cccdNumber = cccd,
                bankName = selectedBank.name,
                bankAccountNumber = bankAccount,
                bankAccountName = bankAccountName,
                bankBin = selectedBank.bin,
                latitude = lat,
                longitude = lng,
                openHours = openHours,
                prepTime = prepTime
            )

            binding.progressBarVerification.visibility = View.GONE
            binding.btnSubmitVerification.isEnabled = true

            result.onSuccess {
                Toast.makeText(this@StoreVerificationActivity, "🎉 Xác minh hồ sơ định danh và STK thành công!", Toast.LENGTH_LONG).show()
                val role = authRepository.getUserRole()?.uppercase()
                if (role == User.Role.BOSSMERCHANT || role == "BOSS") {
                    firebaseRepository.markStoreOwnedByBoss(uid, currentStoreId)
                    startActivity(Intent(this@StoreVerificationActivity, BossDashboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                } else {
                    startActivity(Intent(this@StoreVerificationActivity, DashboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
                finish()
            }.onFailure { err ->
                Toast.makeText(this@StoreVerificationActivity, "Lỗi khi cập nhật hồ sơ: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
