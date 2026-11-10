package com.chuyen_de_2.foodorder.internal.ui.boss

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.ActivityBossDashboardBinding
import com.chuyen_de_2.foodorder.internal.databinding.DialogAssignMerchantBinding
import com.chuyen_de_2.foodorder.internal.databinding.DialogCreateBranchBinding
import com.chuyen_de_2.foodorder.internal.databinding.DialogCreateMerchantBinding
import com.chuyen_de_2.foodorder.internal.ui.auth.LoginActivity
import com.chuyen_de_2.foodorder.internal.ui.dashboard.DashboardActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Màn hình Bảng điều khiển Quản lý Chuỗi dành riêng cho BOSSMERCHANT.
 * Cho phép tạo chi nhánh, tạo tài khoản Merchant, gán Merchant vào chi nhánh,
 * và truy cập trực tiếp vào bất kỳ chi nhánh nào để vận hành.
 */
@AndroidEntryPoint
class BossDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBossDashboardBinding

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private lateinit var branchAdapter: BossBranchAdapter
    private var currentBossUid: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InsetUtils.initEdgeToEdge(this, true)
        binding = ActivityBossDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentUser = authRepository.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        currentBossUid = currentUser.uid
        setupUI()
        observeBranches()
        observeBusinessProfile()
    }

    private fun observeBusinessProfile() {
        lifecycleScope.launch {
            firebaseRepository.getBossBusinessProfile(currentBossUid).collect { user ->
                val hasBank = user != null && user.isBusinessVerified && user.bankAccountNumber.isNotBlank()
                binding.cardMissingBankAlert.visibility = if (hasBank) View.GONE else View.VISIBLE
            }
        }
    }

    private fun setupUI() {
        val email = authRepository.currentUser?.email.orEmpty()
        val displayName = authRepository.currentUser?.displayName.orEmpty()
        binding.tvBossSubtitle.text = buildString {
            append("BOSSMERCHANT • ")
            append(displayName.ifBlank { email })
        }

        branchAdapter = BossBranchAdapter(
            onEnterBranchClick = { store -> enterBranchAsManager(store) },
            onAssignManagerClick = { store -> showAssignMerchantDialog(store) }
        )

        binding.rvBossBranches.layoutManager = LinearLayoutManager(this)
        binding.rvBossBranches.adapter = branchAdapter

        binding.btnCreateBranch.setOnClickListener {
            showCreateBranchDialog()
        }

        binding.btnCreateMerchant.setOnClickListener {
            showCreateMerchantDialog()
        }

        binding.btnBossBusinessProfile.setOnClickListener {
            val uid = authRepository.currentUser?.uid.orEmpty()
            lifecycleScope.launch {
                val storeId = firebaseRepository.resolveStoreIdForUser(uid).ifBlank { "store_$uid" }
                val intent = Intent(this@BossDashboardActivity, com.chuyen_de_2.foodorder.internal.ui.auth.StoreVerificationActivity::class.java).apply {
                    putExtra("STORE_ID", storeId)
                }
                startActivity(intent)
            }
        }

        binding.cardMissingBankAlert.setOnClickListener {
            binding.btnBossBusinessProfile.performClick()
        }

        binding.btnBossLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("🚪 Đăng Xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất tài khoản Chủ chuỗi (Boss) không?")
                .setPositiveButton("Đăng xuất") { _, _ ->
                    getSharedPreferences("boss_session", Context.MODE_PRIVATE).edit().clear().apply()
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

    private fun observeBranches() {
        binding.progressBarBoss.visibility = View.VISIBLE
        lifecycleScope.launch {
            firebaseRepository.getStoresByBoss(currentBossUid).collect { stores ->
                binding.progressBarBoss.visibility = View.GONE
                branchAdapter.submitList(stores)

                val count = stores.size
                binding.tvChainStats.text = "$count chi nhánh đang vận hành"
                binding.layoutEmptyBranches.visibility = if (count == 0) View.VISIBLE else View.GONE
                binding.rvBossBranches.visibility = if (count > 0) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * Boss chọn một chi nhánh để vào làm việc thay cho Merchant.
     * Lưu chi nhánh vào SharedPreferences để khi thoát ra vào lại vẫn ở chi nhánh này.
     */
    private fun enterBranchAsManager(store: Store) {
        lifecycleScope.launch {
            // Lưu vào SharedPreferences cho Session Persistence
            getSharedPreferences("boss_session", Context.MODE_PRIVATE)
                .edit()
                .putString("selected_branch_store_id", store.id)
                .putString("selected_branch_store_name", store.name)
                .apply()

            // Đồng bộ lên Realtime DB
            firebaseRepository.setActiveStoreForBoss(currentBossUid, store.id)

            Toast.makeText(
                this@BossDashboardActivity,
                "✓ Đã vào chi nhánh: ${store.name}",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(this@BossDashboardActivity, DashboardActivity::class.java).apply {
                putExtra("STORE_ID", store.id)
                putExtra("FROM_BOSS", true)
            }
            startActivity(intent)
            finish()
        }
    }

    /**
     * Dialog tạo chi nhánh mới: TÊN CHI NHÁNH, Địa chỉ...
     */
    private fun showCreateBranchDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogCreateBranchBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialogBinding.btnCancelCreateBranch.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSubmitCreateBranch.setOnClickListener {
            val name = dialogBinding.etBranchName.text?.toString()?.trim().orEmpty()
            val address = dialogBinding.etBranchAddress.text?.toString()?.trim().orEmpty()
            val district = dialogBinding.etBranchDistrict.text?.toString()?.trim().orEmpty()
            val phone = dialogBinding.etBranchPhone.text?.toString()?.trim().orEmpty()

            if (name.isBlank()) {
                dialogBinding.tilBranchName.error = "Vui lòng nhập tên chi nhánh"
                return@setOnClickListener
            }
            if (address.isBlank()) {
                dialogBinding.tilBranchAddress.error = "Vui lòng nhập địa chỉ chi nhánh"
                return@setOnClickListener
            }

            dialogBinding.btnSubmitCreateBranch.isEnabled = false
            dialogBinding.btnSubmitCreateBranch.text = "Đang tạo..."

            lifecycleScope.launch {
                val result = firebaseRepository.createBranchStore(
                    bossUid = currentBossUid,
                    storeName = name,
                    address = address,
                    district = district,
                    phone = phone
                )
                dialog.dismiss()
                result.onSuccess { newId ->
                    Toast.makeText(
                        this@BossDashboardActivity,
                        "🎉 Tạo chi nhánh '$name' thành công!",
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { err ->
                    Toast.makeText(
                        this@BossDashboardActivity,
                        "Lỗi tạo chi nhánh: ${err.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        dialog.show()
    }

    /**
     * Dialog tạo tài khoản MERCHANT (Quản lý) mới do Boss tạo.
     */
    private fun showCreateMerchantDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogCreateMerchantBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialogBinding.btnCancelCreateMerchant.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSubmitCreateMerchant.setOnClickListener {
            val name = dialogBinding.etMerchantName.text?.toString()?.trim().orEmpty()
            val email = dialogBinding.etMerchantEmail.text?.toString()?.trim().orEmpty()
            val phone = dialogBinding.etMerchantPhone.text?.toString()?.trim().orEmpty()

            if (name.isBlank()) {
                dialogBinding.tilMerchantName.error = "Vui lòng nhập họ tên"
                return@setOnClickListener
            }
            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                dialogBinding.tilMerchantEmail.error = "Vui lòng nhập email hợp lệ"
                return@setOnClickListener
            }
            if (phone.isBlank()) {
                dialogBinding.tilMerchantPhone.error = "Vui lòng nhập số điện thoại"
                return@setOnClickListener
            }

            dialogBinding.btnSubmitCreateMerchant.isEnabled = false
            dialogBinding.btnSubmitCreateMerchant.text = "Đang tạo..."

            lifecycleScope.launch {
                val result = firebaseRepository.createMerchantByBoss(
                    bossUid = currentBossUid,
                    email = email,
                    name = name,
                    phone = phone
                )
                dialog.dismiss()
                result.onSuccess { user ->
                    Toast.makeText(
                        this@BossDashboardActivity,
                        "✓ Tạo tài khoản quản lý '${user.name}' thành công! Giờ bạn có thể phân công quản lý này vào chi nhánh.",
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { err ->
                    Toast.makeText(
                        this@BossDashboardActivity,
                        "Lỗi tạo quản lý: ${err.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        dialog.show()
    }

    /**
     * Dialog gán tài khoản MERCHANT cho một Chi nhánh cụ thể (hoặc để trống - null Merchant).
     */
    private fun showAssignMerchantDialog(store: Store) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogAssignMerchantBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialogBinding.tvAssignTargetBranch.text = "Chi nhánh: ${store.name}"

        var selectedUid: String? = null
        var selectedName: String = ""

        lifecycleScope.launch {
            val merchants = firebaseRepository.getMerchantsForBoss(currentBossUid).firstOrNull().orEmpty()

            for (merchant in merchants) {
                val rb = RadioButton(this@BossDashboardActivity).apply {
                    id = View.generateViewId()
                    text = "👤 ${merchant.name} (${merchant.email})"
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 14f
                    setPadding(0, 16, 0, 16)
                    tag = merchant
                    if (merchant.uid == store.merchantId) {
                        isChecked = true
                        selectedUid = merchant.uid
                        selectedName = merchant.name
                    }
                }
                dialogBinding.rgMerchants.addView(rb)
            }

            if (store.merchantId.isBlank()) {
                dialogBinding.rbNoMerchant.isChecked = true
            }
        }

        dialogBinding.rgMerchants.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == dialogBinding.rbNoMerchant.id) {
                selectedUid = null
                selectedName = ""
            } else {
                val checkedRb = group.findViewById<RadioButton>(checkedId)
                val merchant = checkedRb?.tag as? User
                if (merchant != null) {
                    selectedUid = merchant.uid
                    selectedName = merchant.name
                }
            }
        }

        dialogBinding.btnCancelAssign.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSubmitAssign.setOnClickListener {
            dialogBinding.btnSubmitAssign.isEnabled = false
            dialogBinding.btnSubmitAssign.text = "Đang lưu..."

            lifecycleScope.launch {
                val result = firebaseRepository.assignMerchantToStore(
                    storeId = store.id,
                    merchantUid = selectedUid,
                    merchantName = selectedName
                )
                dialog.dismiss()
                result.onSuccess {
                    val msg = if (selectedUid != null) {
                        "✓ Đã phân công quản lý '$selectedName' cho chi nhánh '${store.name}'!"
                    } else {
                        "✓ Đã chuyển chi nhánh '${store.name}' về chế độ Boss tự quản lý!"
                    }
                    Toast.makeText(this@BossDashboardActivity, msg, Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(
                        this@BossDashboardActivity,
                        "Lỗi phân công: ${err.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        dialog.show()
    }
}

