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
import com.chuyen_de_2.foodorder.internal.databinding.DialogAssignBranchToMerchantBinding
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
 * Cho phép tạo chi nhánh, tạo tài khoản Merchant, xem và quản lý danh sách Merchant đã tạo,
 * phân công/gỡ Merchant cho từng chi nhánh, và truy cập trực tiếp vào bất kỳ chi nhánh nào để vận hành.
 */
@AndroidEntryPoint
class BossDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBossDashboardBinding

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private lateinit var branchAdapter: BossBranchAdapter
    private lateinit var merchantAdapter: BossMerchantAdapter
    private var currentBossUid: String = ""
    private var cachedBranches: List<Store> = emptyList()

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
        observeMerchants()
        observeBusinessProfile()
    }

    private fun observeBusinessProfile() {
        lifecycleScope.launch {
            val storeId = firebaseRepository.resolveStoreIdForUser(currentBossUid)
            if (storeId.isNotBlank()) {
                val store = firebaseRepository.getStoreOnce(storeId)
                val hasBank = store != null && store.bankAccountNumber.isNotBlank() && store.cccdNumber.isNotBlank()
                binding.cardMissingBankAlert.visibility = if (hasBank) View.GONE else View.VISIBLE
            } else {
                binding.cardMissingBankAlert.visibility = View.VISIBLE
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

        // Branch Adapter
        branchAdapter = BossBranchAdapter(
            onEnterBranchClick = { store -> enterBranchAsManager(store) },
            onAssignManagerClick = { store -> showAssignMerchantDialog(store) },
            onUnassignManagerClick = { store -> unassignManagerFromStore(store) }
        ).apply {
            currentBossUid = this@BossDashboardActivity.currentBossUid
        }
        binding.rvBossBranches.layoutManager = LinearLayoutManager(this)
        binding.rvBossBranches.adapter = branchAdapter

        // Merchant Adapter
        merchantAdapter = BossMerchantAdapter(
            onAssignBranchClick = { merchant -> showAssignBranchToMerchantDialog(merchant) },
            onUnassignBranchClick = { merchant -> unassignMerchant(merchant) },
            onToggleLockMerchantClick = { merchant -> confirmToggleLockMerchant(merchant) }
        )
        binding.rvBossMerchants.layoutManager = LinearLayoutManager(this)
        binding.rvBossMerchants.adapter = merchantAdapter

        // Tab Toggle Group
        binding.toggleGroupBossTab.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnTabBranches) {
                    binding.layoutBranchesSection.visibility = View.VISIBLE
                    binding.layoutMerchantsSection.visibility = View.GONE
                } else if (checkedId == R.id.btnTabMerchants) {
                    binding.layoutBranchesSection.visibility = View.GONE
                    binding.layoutMerchantsSection.visibility = View.VISIBLE
                }
            }
        }

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
                // Tự động dọn dẹp nếu chi nhánh bị lưu nhầm merchantId là UID của Boss
                for (st in stores) {
                    if (st.merchantId == currentBossUid) {
                        launch { firebaseRepository.unassignMerchantFromBranch("", st.id) }
                    }
                }
                cachedBranches = stores
                branchAdapter.submitList(stores)
                merchantAdapter.setStores(stores)

                val count = stores.size
                binding.btnTabBranches.text = "🏪 Chi Nhánh ($count)"
                binding.tvChainStats.text = "$count chi nhánh đang vận hành"
                binding.layoutEmptyBranches.visibility = if (count == 0) View.VISIBLE else View.GONE
                binding.rvBossBranches.visibility = if (count > 0) View.VISIBLE else View.GONE
            }
        }
    }

    private fun observeMerchants() {
        lifecycleScope.launch {
            firebaseRepository.getMerchantsForBoss(currentBossUid).collect { merchants ->
                merchantAdapter.submitList(merchants)
                merchantAdapter.setStores(cachedBranches)

                val count = merchants.size
                binding.btnTabMerchants.text = "👥 Quản Lý ($count)"
                binding.layoutEmptyMerchants.visibility = if (count == 0) View.VISIBLE else View.GONE
                binding.rvBossMerchants.visibility = if (count > 0) View.VISIBLE else View.GONE
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
            dialogBinding.tilMerchantName.error = null
            dialogBinding.tilMerchantEmail.error = null
            dialogBinding.tilMerchantPhone.error = null
            dialogBinding.tilMerchantPassword.error = null

            val name = dialogBinding.etMerchantName.text?.toString()?.trim().orEmpty()
            val email = dialogBinding.etMerchantEmail.text?.toString()?.trim().orEmpty()
            val phone = dialogBinding.etMerchantPhone.text?.toString()?.trim().orEmpty()
            val password = dialogBinding.etMerchantPassword.text?.toString()?.trim().orEmpty()

            var hasError = false
            if (name.isBlank()) {
                dialogBinding.tilMerchantName.error = "Vui lòng nhập họ tên"
                hasError = true
            }
            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                dialogBinding.tilMerchantEmail.error = "Vui lòng nhập email hợp lệ"
                hasError = true
            }
            if (phone.isBlank()) {
                dialogBinding.tilMerchantPhone.error = "Vui lòng nhập số điện thoại"
                hasError = true
            }
            if (password.length < 6) {
                dialogBinding.tilMerchantPassword.error = "Mật khẩu phải có tối thiểu 6 ký tự"
                hasError = true
            }

            if (hasError) return@setOnClickListener

            dialogBinding.btnSubmitCreateMerchant.isEnabled = false
            dialogBinding.btnSubmitCreateMerchant.text = "Đang tạo..."

            lifecycleScope.launch {
                val result = firebaseRepository.createMerchantByBoss(
                    context = this@BossDashboardActivity,
                    bossUid = currentBossUid,
                    email = email,
                    password = password,
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
                    // Tự động chuyển sang tab Quản lý để Boss xem danh sách
                    binding.toggleGroupBossTab.check(R.id.btnTabMerchants)
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
                val isLocked = !merchant.isActive || merchant.status == User.Status.LOCKED || merchant.status == "SUSPENDED"
                val rb = RadioButton(this@BossDashboardActivity).apply {
                    id = View.generateViewId()
                    text = if (isLocked) "🔒 ${merchant.name} (${merchant.email}) [Đã khóa]" else "👤 ${merchant.name} (${merchant.email})"
                    setTextColor(if (isLocked) Color.parseColor("#94A3B8") else Color.parseColor("#0F172A"))
                    textSize = 14f
                    setPadding(0, 16, 0, 16)
                    tag = merchant
                    isEnabled = !isLocked
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

    /**
     * Gỡ quản lý khỏi một chi nhánh cụ thể từ danh sách Chi Nhánh
     */
    private fun unassignManagerFromStore(store: Store) {
        val managerDisplayName = if (store.merchantName.isNotBlank()) store.merchantName else "quản lý này"
        AlertDialog.Builder(this)
            .setTitle("Gỡ Quản Lý")
            .setMessage("Bạn có chắc muốn gỡ $managerDisplayName khỏi chi nhánh '${store.name}' không? Chi nhánh sẽ chuyển về chế độ Boss tự quản lý.")
            .setPositiveButton("Gỡ") { _, _ ->
                lifecycleScope.launch {
                    val result = firebaseRepository.unassignMerchantFromBranch(store.merchantId, store.id)
                    result.onSuccess {
                        Toast.makeText(this@BossDashboardActivity, "✓ Đã gỡ quản lý khỏi chi nhánh '${store.name}'!", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(this@BossDashboardActivity, "Lỗi: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /**
     * Dialog phân công chi nhánh cho một Quản lý (Merchant) được chọn từ danh sách Quản lý
     */
    private fun showAssignBranchToMerchantDialog(merchant: User) {
        val isLocked = !merchant.isActive || merchant.status == User.Status.LOCKED || merchant.status == "SUSPENDED"
        if (isLocked) {
            Toast.makeText(this, "Tài khoản quản lý này đang bị khóa, không thể phân công chi nhánh!", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogAssignBranchToMerchantBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialogBinding.tvAssignTargetMerchant.text = "Quản lý: ${merchant.name.ifBlank { merchant.email }}"

        var selectedStoreId: String = merchant.storeId
        for (branch in cachedBranches) {
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = "🏪 ${branch.name} (${branch.address.ifBlank { "Chưa có địa chỉ" }})"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 14f
                setPadding(0, 16, 0, 16)
                tag = branch.id
                if (branch.id == merchant.storeId) {
                    isChecked = true
                }
            }
            dialogBinding.rgBranches.addView(rb)
        }

        if (merchant.storeId.isBlank()) {
            dialogBinding.rbNoBranch.isChecked = true
        }

        dialogBinding.rgBranches.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == dialogBinding.rbNoBranch.id) {
                selectedStoreId = ""
            } else {
                val checkedRb = group.findViewById<RadioButton>(checkedId)
                selectedStoreId = checkedRb?.tag as? String ?: ""
            }
        }

        dialogBinding.btnCancelAssignBranch.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSubmitAssignBranch.setOnClickListener {
            dialogBinding.btnSubmitAssignBranch.isEnabled = false
            dialogBinding.btnSubmitAssignBranch.text = "Đang lưu..."
            lifecycleScope.launch {
                val targetBranchName = cachedBranches.firstOrNull { it.id == selectedStoreId }?.name.orEmpty()
                val result = firebaseRepository.assignMerchantToBranch(
                    merchantUid = merchant.uid,
                    merchantName = merchant.name,
                    targetStoreId = selectedStoreId,
                    previousStoreId = merchant.storeId
                )
                dialog.dismiss()
                result.onSuccess {
                    val msg = if (selectedStoreId.isNotBlank()) {
                        "✓ Đã phân công quản lý '${merchant.name}' cho chi nhánh '$targetBranchName'!"
                    } else {
                        "✓ Đã chuyển quản lý '${merchant.name}' về trạng thái tự do!"
                    }
                    Toast.makeText(this@BossDashboardActivity, msg, Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(this@BossDashboardActivity, "Lỗi phân công: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    /**
     * Gỡ quản lý khỏi chi nhánh
     */
    private fun unassignMerchant(merchant: User) {
        AlertDialog.Builder(this)
            .setTitle("Gỡ Quản Lý")
            .setMessage("Bạn có chắc muốn gỡ quản lý '${merchant.name}' khỏi chi nhánh đang phụ trách không?")
            .setPositiveButton("Gỡ") { _, _ ->
                lifecycleScope.launch {
                    val result = firebaseRepository.unassignMerchantFromBranch(merchant.uid, merchant.storeId)
                    result.onSuccess {
                        Toast.makeText(this@BossDashboardActivity, "✓ Đã gỡ quản lý khỏi chi nhánh!", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(this@BossDashboardActivity, "Lỗi: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /**
     * Khóa hoặc Mở khóa tài khoản Quản lý (thay thế xóa tài khoản)
     */
    private fun confirmToggleLockMerchant(merchant: User) {
        val isCurrentlyLocked = !merchant.isActive || merchant.status == User.Status.LOCKED || merchant.status == "SUSPENDED"
        val shouldLock = !isCurrentlyLocked

        val title = if (shouldLock) "🔒 Khóa Tài Khoản Quản Lý" else "🔓 Mở Khóa Tài Khoản Quản Lý"
        val message = if (shouldLock) {
            "Bạn có chắc muốn KHÓA tài khoản quản lý '${merchant.name}' (${merchant.email}) không?\n\nQuản lý này sẽ bị gỡ khỏi chi nhánh (nếu có) và không thể đăng nhập vào ứng dụng."
        } else {
            "Bạn có chắc muốn MỞ KHÓA cho tài khoản quản lý '${merchant.name}' (${merchant.email}) không?\n\nSau khi mở khóa, quản lý này có thể đăng nhập và được phân công lại vào chi nhánh."
        }
        val positiveBtn = if (shouldLock) "Khóa tài khoản" else "Mở khóa"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveBtn) { _, _ ->
                lifecycleScope.launch {
                    val result = firebaseRepository.toggleMerchantLockByBoss(merchant, shouldLock)
                    result.onSuccess {
                        val msg = if (shouldLock) "✓ Đã khóa tài khoản quản lý '${merchant.name}'!" else "✓ Đã mở khóa cho quản lý '${merchant.name}'!"
                        Toast.makeText(this@BossDashboardActivity, msg, Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(this@BossDashboardActivity, "Lỗi: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
