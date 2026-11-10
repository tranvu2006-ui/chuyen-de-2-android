package com.chuyen_de_2.foodorder.internal.ui.boss

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.internal.databinding.ActivityBossBusinessProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Màn hình Quản Lý Hồ Sơ Kinh Doanh, Pháp Lý & Tài Khoản Ngân Hàng (STK) của BOSSMERCHANT.
 * Nơi Boss nhập thông tin CCCD, Tên chủ doanh nghiệp và STK nhận doanh thu đối soát từ Oishi Food.
 */
@AndroidEntryPoint
class BossBusinessProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBossBusinessProfileBinding

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

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

    private var isInitialSetup = false
    private var bossUid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InsetUtils.initEdgeToEdge(this, true)
        binding = ActivityBossBusinessProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isInitialSetup = intent.getBooleanExtra("IS_INITIAL_SETUP", false)
        bossUid = authRepository.currentUser?.uid.orEmpty()

        setupToolbar()
        setupBankSpinner()
        loadExistingProfile()
        setupSaveButton()
    }

    private fun setupToolbar() {
        binding.toolbarBossBusiness.setNavigationOnClickListener {
            if (isInitialSetup) {
                // Đăng ký lần đầu nếu back sẽ vào thẳng BossDashboard
                startActivity(Intent(this, BossDashboardActivity::class.java))
                finish()
            } else {
                finish()
            }
        }
    }

    private fun setupBankSpinner() {
        val bankNames = supportedBanks.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bankNames)
        binding.spBossBankName.adapter = adapter
    }

    private fun loadExistingProfile() {
        if (bossUid.isBlank()) return
        lifecycleScope.launch {
            binding.progressBarBossBusiness.visibility = View.VISIBLE
            val user = firebaseRepository.getBossBusinessProfile(bossUid).firstOrNull()
            binding.progressBarBossBusiness.visibility = View.GONE

            if (user != null) {
                if (user.ownerName.isNotBlank()) {
                    binding.etBossOwnerName.setText(user.ownerName)
                } else if (user.name.isNotBlank()) {
                    binding.etBossOwnerName.setText(user.name)
                }

                binding.etBossCccd.setText(user.cccdNumber)
                binding.etBossTaxCode.setText(user.businessLicense)
                binding.etBossBankAccountNumber.setText(user.bankAccountNumber)
                binding.etBossBankAccountName.setText(user.bankAccountName)

                if (user.bankName.isNotBlank()) {
                    val index = supportedBanks.indexOfFirst {
                        it.name.contains(user.bankName, ignoreCase = true) || user.bankName.contains(it.name, ignoreCase = true)
                    }
                    if (index >= 0) {
                        binding.spBossBankName.setSelection(index)
                    }
                }
            }
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveBossBusinessProfile.setOnClickListener {
            val ownerName = binding.etBossOwnerName.text?.toString()?.trim().orEmpty()
            val cccd = binding.etBossCccd.text?.toString()?.trim().orEmpty()
            val taxCode = binding.etBossTaxCode.text?.toString()?.trim().orEmpty()
            val bankAccountNumber = binding.etBossBankAccountNumber.text?.toString()?.trim().orEmpty()
            val bankAccountName = binding.etBossBankAccountName.text?.toString()?.trim().orEmpty()

            var hasError = false

            if (ownerName.isBlank()) {
                binding.tilBossOwnerName.error = "Vui lòng nhập tên chủ sở hữu"
                hasError = true
            } else {
                binding.tilBossOwnerName.error = null
            }

            if (cccd.length < 9) {
                binding.tilBossCccd.error = "Số CCCD/CMND không hợp lệ (từ 9 đến 12 số)"
                hasError = true
            } else {
                binding.tilBossCccd.error = null
            }

            if (bankAccountNumber.isBlank()) {
                binding.tilBossBankAccountNumber.error = "Vui lòng nhập số tài khoản ngân hàng"
                hasError = true
            } else {
                binding.tilBossBankAccountNumber.error = null
            }

            if (bankAccountName.isBlank()) {
                binding.tilBossBankAccountName.error = "Vui lòng nhập tên chủ tài khoản"
                hasError = true
            } else {
                binding.tilBossBankAccountName.error = null
            }

            if (hasError) {
                Toast.makeText(this, "⚠️ Vui lòng điền đầy đủ các thông tin bắt buộc!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedBank = supportedBanks[binding.spBossBankName.selectedItemPosition]

            binding.progressBarBossBusiness.visibility = View.VISIBLE
            binding.btnSaveBossBusinessProfile.isEnabled = false

            lifecycleScope.launch {
                val result = firebaseRepository.updateBossBusinessProfile(
                    bossUid = bossUid,
                    ownerName = ownerName,
                    cccdNumber = cccd,
                    businessLicense = taxCode,
                    bankName = selectedBank.name,
                    bankAccountNumber = bankAccountNumber,
                    bankAccountName = bankAccountName,
                    bankBin = selectedBank.bin
                )

                binding.progressBarBossBusiness.visibility = View.GONE
                binding.btnSaveBossBusinessProfile.isEnabled = true

                result.onSuccess {
                    Toast.makeText(
                        this@BossBusinessProfileActivity,
                        "🎉 Đã lưu thông tin kinh doanh & STK ngân hàng thành công!",
                        Toast.LENGTH_LONG
                    ).show()

                    if (isInitialSetup) {
                        startActivity(Intent(this@BossBusinessProfileActivity, BossDashboardActivity::class.java))
                        finish()
                    } else {
                        finish()
                    }
                }.onFailure { err ->
                    Toast.makeText(
                        this@BossBusinessProfileActivity,
                        "Lỗi khi lưu thông tin: ${err.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

