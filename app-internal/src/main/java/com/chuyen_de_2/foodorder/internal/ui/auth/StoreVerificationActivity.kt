package com.chuyen_de_2.foodorder.internal.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.util.GoogleMapUtils
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.ActivityStoreVerificationBinding
import com.chuyen_de_2.foodorder.internal.ui.dashboard.DashboardActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phiếu Thiết Lập Địa Chỉ Bắt Buộc & Xác Minh Thông Tin Quán
 * Yêu cầu đối tác Chủ Quán (MERCHANT) hoàn tất trước khi được phép vào Dashboard nhận đơn.
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

    private val districts = listOf(
        "Quận 1", "Quận 3", "Quận 4", "Quận 5", "Quận 6",
        "Quận 7", "Quận 8", "Quận 10", "Quận 11", "Quận 12",
        "Bình Thạnh", "Gò Vấp", "Phú Nhuận", "Tân Bình", "Tân Phú",
        "Bình Tân", "Thủ Đức", "Hóc Môn", "Bình Chánh", "Nhà Bè"
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
        // Spinner Quận/Huyện
        val districtAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, districts)
        districtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spStoreDistrict.adapter = districtAdapter

        // Spinner Ngân hàng
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
                    if (it.name.isNotBlank() && it.name != "Quán Ăn Mới") {
                        binding.etStoreName.setText(it.name)
                    } else if (!user.displayName.isNullOrBlank()) {
                        binding.etStoreName.setText(user.displayName)
                    }

                    if (it.phone.isNotBlank()) {
                        binding.etStorePhone.setText(it.phone)
                    } else if (!user.phoneNumber.isNullOrBlank()) {
                        binding.etStorePhone.setText(user.phoneNumber)
                    }

                    if (it.address.isNotBlank()) binding.etStoreAddress.setText(it.address)
                    if (it.ownerName.isNotBlank()) {
                        binding.etOwnerName.setText(it.ownerName)
                    } else if (!user.displayName.isNullOrBlank()) {
                        binding.etOwnerName.setText(user.displayName)
                    }

                    if (it.cccdNumber.isNotBlank()) binding.etCccdNumber.setText(it.cccdNumber)
                    if (it.openHours.isNotBlank()) binding.etOpenHours.setText(it.openHours)
                    if (it.prepTime.isNotBlank()) binding.etPrepTime.setText(it.prepTime)

                    if (it.district.isNotBlank()) {
                        val dIndex = districts.indexOfFirst { d -> d.equals(it.district, ignoreCase = true) }
                        if (dIndex >= 0) binding.spStoreDistrict.setSelection(dIndex)
                    }

                    if (it.bankAccountNumber.isNotBlank()) binding.etBankAccountNumber.setText(it.bankAccountNumber)
                    if (it.bankAccountName.isNotBlank()) binding.etBankAccountName.setText(it.bankAccountName)
                    if (it.bankName.isNotBlank()) {
                        val bIndex = supportedBanks.indexOfFirst { b -> b.name.contains(it.bankName, ignoreCase = true) }
                        if (bIndex >= 0) binding.spBankName.setSelection(bIndex)
                    }
                }
            } else {
                if (!user.displayName.isNullOrBlank()) {
                    binding.etStoreName.setText(user.displayName)
                    binding.etOwnerName.setText(user.displayName)
                }
                if (!user.phoneNumber.isNullOrBlank()) {
                    binding.etStorePhone.setText(user.phoneNumber)
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnSubmitVerification.setOnClickListener {
            validateAndSubmit()
        }

        binding.btnLogout.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🚪 Đăng Xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản quán ăn không?")
                .setPositiveButton("Đăng xuất") { _, _ ->
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
        // Reset errors
        binding.tilStoreName.error = null
        binding.tilStorePhone.error = null
        binding.tilStoreAddress.error = null
        binding.tilOwnerName.error = null
        binding.tilCccdNumber.error = null
        binding.tilBankAccountNumber.error = null
        binding.tilBankAccountName.error = null

        val name = binding.etStoreName.text.toString().trim()
        val phone = binding.etStorePhone.text.toString().trim()
        val address = binding.etStoreAddress.text.toString().trim()
        val district = binding.spStoreDistrict.selectedItem?.toString().orEmpty()
        val openHours = binding.etOpenHours.text.toString().trim().ifBlank { "08:00 - 22:30" }
        val prepTime = binding.etPrepTime.text.toString().trim().ifBlank { "15 phút" }

        val ownerName = binding.etOwnerName.text.toString().trim()
        val cccd = binding.etCccdNumber.text.toString().trim()

        val selectedBank = supportedBanks.getOrNull(binding.spBankName.selectedItemPosition) ?: supportedBanks[0]
        val bankAccount = binding.etBankAccountNumber.text.toString().trim()
        val bankAccountName = binding.etBankAccountName.text.toString().trim().uppercase()

        var hasError = false

        if (name.isBlank()) {
            binding.tilStoreName.error = "Vui lòng nhập tên cửa hàng / quán ăn"
            hasError = true
        }

        if (phone.isBlank()) {
            binding.tilStorePhone.error = "Vui lòng nhập số điện thoại quán"
            hasError = true
        }

        if (address.isBlank()) {
            binding.tilStoreAddress.error = "Địa chỉ quán là bắt buộc để tài xế đến lấy món"
            hasError = true
        }

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

        // Tọa độ GPS theo quận TP.HCM
        val coords = GoogleMapUtils.DISTRICT_COORDINATES[district] ?: GoogleMapUtils.DEFAULT_HCMC_LATLNG

        binding.progressBarVerification.visibility = View.VISIBLE
        binding.btnSubmitVerification.isEnabled = false

        lifecycleScope.launch {
            val user = authRepository.currentUser
            val uid = user?.uid.orEmpty()
            if (currentStoreId.isBlank()) {
                currentStoreId = firebaseRepository.resolveStoreIdForUser(uid)
            }
            if (currentStoreId.isBlank()) {
                currentStoreId = "store_${System.currentTimeMillis()}"
            }

            val result = firebaseRepository.verifyAndCompleteStoreProfile(
                storeId = currentStoreId,
                merchantUid = uid,
                name = name,
                phone = phone,
                address = address,
                district = district,
                ownerName = ownerName,
                cccdNumber = cccd,
                bankName = selectedBank.name,
                bankAccountNumber = bankAccount,
                bankAccountName = bankAccountName,
                bankBin = selectedBank.bin,
                latitude = coords.latitude,
                longitude = coords.longitude,
                openHours = openHours,
                prepTime = prepTime
            )

            binding.progressBarVerification.visibility = View.GONE
            binding.btnSubmitVerification.isEnabled = true

            result.onSuccess {
                Toast.makeText(this@StoreVerificationActivity, "🎉 Xác minh hồ sơ và thiết lập địa chỉ quán thành công!", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@StoreVerificationActivity, DashboardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }.onFailure { err ->
                Toast.makeText(this@StoreVerificationActivity, "Lỗi khi cập nhật hồ sơ: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
