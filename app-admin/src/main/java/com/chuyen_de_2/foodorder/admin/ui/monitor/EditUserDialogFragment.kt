package com.chuyen_de_2.foodorder.admin.ui.monitor

import android.app.Dialog
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import com.chuyen_de_2.foodorder.core.util.ImagePickerUtils
import java.io.ByteArrayOutputStream
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.chuyen_de_2.foodorder.admin.databinding.DialogEditUserBinding
import com.chuyen_de_2.foodorder.core.data.model.Customer
import com.chuyen_de_2.foodorder.core.data.model.User
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.admin.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EditUserDialogFragment(
    private val user: User,
    private val customer: Customer? = null,
    private val onSave: (User, Int, Int, Int, String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogEditUserBinding? = null
    private val binding get() = _binding!!

    private var currentAvatar: String = ""
    private var isAvatarResetToDefault = false

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val ctx = context ?: return@registerForActivityResult
        uri ?: return@registerForActivityResult
        val base64 = uriToBase64(uri)
        if (!base64.isNullOrBlank()) {
            currentAvatar = base64
            isAvatarResetToDefault = false
            updateAvatarPreview(currentAvatar)
            binding.tvAvatarStatusHint.text = "Đã chọn ảnh mới từ thư viện"
            binding.tvAvatarStatusHint.setTextColor(ContextCompat.getColor(ctx, R.color.brand_green))
            android.widget.Toast.makeText(ctx, "Đã cập nhật ảnh đại diện mới!", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(ctx, "Không thể đọc ảnh đã chọn. Vui lòng thử ảnh khác!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun uriToBase64(uri: Uri): String? {
        val ctx = context ?: return null
        return ImagePickerUtils.compressUriToBase64(ctx, uri, maxDimension = 260, quality = 80)
    }

    private val roleOptions = listOf(
        User.Role.CUSTOMER,
        User.Role.MERCHANT,
        User.Role.BOSSMERCHANT,
        User.Role.SHIPPER,
        User.Role.ADMIN
    )

    private val statusOptions = listOf(
        User.Status.ACTIVE,
        "LOCKED",
        User.Status.INACTIVE,
        User.Status.OFFLINE
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()
        populateCurrentData()
        setupListeners()
    }

    private fun setupSpinners() {
        val context = requireContext()

        val roleAdapter = ArrayAdapter(
            context,
            com.chuyen_de_2.foodorder.admin.R.layout.item_admin_spinner,
            listOf(
                "Khách Hàng (CUSTOMER)",
                "Chủ Quán / Quản Lý (MERCHANT)",
                "Chủ Chuỗi Cửa Hàng (BOSSMERCHANT)",
                "Tài Xế Giao Hàng (SHIPPER)",
                "Quản Trị Viên (ADMIN)"
            )
        ).apply {
            setDropDownViewResource(com.chuyen_de_2.foodorder.admin.R.layout.item_admin_spinner_dropdown)
        }
        binding.spnUserRole.adapter = roleAdapter

        val statusAdapter = ArrayAdapter(
            context,
            com.chuyen_de_2.foodorder.admin.R.layout.item_admin_spinner,
            listOf(
                "Đang Hoạt Động (ACTIVE)",
                "Đã Bị Khóa (LOCKED)",
                "Tạm Ngừng (INACTIVE)",
                "Ngoại Tuyến (OFFLINE)"
            )
        ).apply {
            setDropDownViewResource(com.chuyen_de_2.foodorder.admin.R.layout.item_admin_spinner_dropdown)
        }
        binding.spnUserStatus.adapter = statusAdapter
    }

    private fun updateAvatarPreview(avatar: String) {
        val context = context ?: return
        com.chuyen_de_2.foodorder.core.util.ImageLoadUtils.loadUserAvatarWithTintRes(
            binding.ivUserAvatarPreview,
            avatar,
            placeholderResId = com.chuyen_de_2.foodorder.core.R.drawable.ic_user,
            tintColorResId = R.color.secondary
        )
        com.chuyen_de_2.foodorder.core.util.ImageLoadUtils.loadUserAvatarWithTintRes(
            binding.ivDialogUserAvatar,
            avatar,
            placeholderResId = com.chuyen_de_2.foodorder.core.R.drawable.ic_user,
            tintColorResId = R.color.secondary
        )

        if (avatar.isNotBlank()) {
            when {
                avatar.startsWith("data:image") -> {
                    binding.tvAvatarStatusHint.text = "✓ Ảnh tải lên từ máy (Base64)"
                    binding.tvAvatarStatusHint.setTextColor(ContextCompat.getColor(context, R.color.brand_blue))
                }
                avatar.startsWith("http://") || avatar.startsWith("https://") -> {
                    binding.tvAvatarStatusHint.text = "✓ Ảnh trực tuyến (URL)"
                    binding.tvAvatarStatusHint.setTextColor(ContextCompat.getColor(context, R.color.brand_green))
                }
                else -> {
                    binding.tvAvatarStatusHint.text = "✓ Ảnh đại diện người dùng"
                    binding.tvAvatarStatusHint.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                }
            }
        } else {
            binding.tvAvatarStatusHint.text = "Chưa có ảnh (Đang dùng icon mặc định)"
            binding.tvAvatarStatusHint.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
    }

    private fun populateCurrentData() {
        currentAvatar = user.avatarUrl.ifBlank { customer?.avatarUrl.orEmpty() }
        isAvatarResetToDefault = false

        binding.tvUserUidSubtitle.text = "UID: ${user.uid}"
        binding.etUserName.setText(user.name)
        binding.etUserEmail.setText(user.email)
        binding.etUserPhone.setText(user.phone)
        binding.etUserStoreId.setText(user.storeId)
        binding.etUserBossId.setText(user.bossId)



        updateAvatarPreview(currentAvatar)

        val currentScore = customer?.trustScore ?: 100
        val currentCompleted = customer?.completedOrders ?: 0
        val currentBoom = customer?.boomOrders ?: 0

        binding.etUserTrustScore.setText(currentScore.toString())
        binding.etUserCompletedOrders.setText(currentCompleted.toString())
        binding.etUserBoomOrders.setText(currentBoom.toString())

        val roleIndex = when (user.role.uppercase()) {
            User.Role.CUSTOMER -> 0
            User.Role.MERCHANT, User.Role.MANAGER -> 1
            User.Role.BOSSMERCHANT, "BOSS" -> 2
            User.Role.SHIPPER, User.Role.DRIVER -> 3
            User.Role.ADMIN -> 4
            else -> 0
        }
        binding.spnUserRole.setSelection(roleIndex)

        val isLocked = !user.isActive ||
            user.status.equals("LOCKED", ignoreCase = true) ||
            user.status.equals("SUSPENDED", ignoreCase = true) ||
            user.status.equals(User.Status.INACTIVE, ignoreCase = true)

        val statusIndex = when {
            user.status.equals("LOCKED", ignoreCase = true) || user.status.equals("SUSPENDED", ignoreCase = true) -> 1
            user.status.equals(User.Status.INACTIVE, ignoreCase = true) -> 2
            user.status.equals(User.Status.OFFLINE, ignoreCase = true) -> 3
            !user.isActive -> 1
            else -> 0
        }
        binding.spnUserStatus.setSelection(statusIndex)
        binding.switchUserActive.isChecked = !isLocked
    }

    private fun setupListeners() {
        binding.btnCloseUserDialog.setOnClickListener { dismiss() }
        binding.btnCancelEditUser.setOnClickListener { dismiss() }

        binding.cardUserAvatarContainer.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }
        binding.ivUserAvatarPreview.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }
        binding.ivDialogUserAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }
        binding.btnQuickPickAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }
        binding.btnPickAvatarFromGallery.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }

        binding.btnResetAvatarDefault.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            isAvatarResetToDefault = true
            currentAvatar = ""
            updateAvatarPreview("")
            android.widget.Toast.makeText(ctx, "Đã đặt lại icon mặc định", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Đồng bộ giữa Switch Kích hoạt và Spinner Trạng thái
        binding.switchUserActive.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                if (binding.spnUserStatus.selectedItemPosition == 0) {
                    binding.spnUserStatus.setSelection(1) // LOCKED
                }
            } else {
                if (binding.spnUserStatus.selectedItemPosition == 1 || binding.spnUserStatus.selectedItemPosition == 2) {
                    binding.spnUserStatus.setSelection(0) // ACTIVE
                }
            }
        }

        binding.spnUserStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 1 || position == 2) {
                    if (binding.switchUserActive.isChecked) {
                        binding.switchUserActive.isChecked = false
                    }
                } else if (position == 0) {
                    if (!binding.switchUserActive.isChecked) {
                        binding.switchUserActive.isChecked = true
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnQuickPlus10.setOnClickListener {
            val cur = binding.etUserTrustScore.text?.toString()?.toIntOrNull() ?: 100
            binding.etUserTrustScore.setText((cur + 10).coerceAtMost(100).toString())
            if (binding.etTrustAdjustReason.text.isNullOrBlank()) {
                binding.etTrustAdjustReason.setText("Cộng thưởng điểm tín nhiệm tích cực")
            }
        }

        binding.btnQuickMinus20.setOnClickListener {
            val cur = binding.etUserTrustScore.text?.toString()?.toIntOrNull() ?: 100
            binding.etUserTrustScore.setText((cur - 20).coerceAtLeast(0).toString())
            if (binding.etTrustAdjustReason.text.isNullOrBlank()) {
                binding.etTrustAdjustReason.setText("Trừ điểm do vi phạm quy định đặt hàng / bom đơn")
            }
        }

        binding.btnQuickReset100.setOnClickListener {
            binding.etUserTrustScore.setText("100")
            binding.switchUserActive.isChecked = true
            binding.spnUserStatus.setSelection(0)
            if (binding.etTrustAdjustReason.text.isNullOrBlank()) {
                binding.etTrustAdjustReason.setText("Khôi phục 100 điểm uy tín & mở khóa tài khoản")
            }
        }

        binding.btnSaveEditUser.setOnClickListener {
            val name = binding.etUserName.text?.toString().orEmpty().trim()
            val email = binding.etUserEmail.text?.toString().orEmpty().trim()
            val phone = binding.etUserPhone.text?.toString().orEmpty().trim()
            val storeId = binding.etUserStoreId.text?.toString().orEmpty().trim()
            val bossId = binding.etUserBossId.text?.toString().orEmpty().trim()

            val finalAvatar = if (isAvatarResetToDefault) "RESET_DEFAULT_AVATAR" else currentAvatar

            val trustScore = (binding.etUserTrustScore.text?.toString()?.toIntOrNull() ?: 100).coerceIn(0, 100)
            val completedOrders = (binding.etUserCompletedOrders.text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val boomOrders = (binding.etUserBoomOrders.text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val adjustReason = binding.etTrustAdjustReason.text?.toString().orEmpty().trim()

            val selectedRole = roleOptions.getOrElse(binding.spnUserRole.selectedItemPosition) { User.Role.CUSTOMER }
            val rawStatus = statusOptions.getOrElse(binding.spnUserStatus.selectedItemPosition) { User.Status.ACTIVE }
            val isCheckedActive = binding.switchUserActive.isChecked
            val isUnlocked = isCheckedActive && rawStatus != "LOCKED" && rawStatus != User.Status.INACTIVE
            val finalStatus = if (isUnlocked) rawStatus else "LOCKED"

            val roleId = when (selectedRole) {
                User.Role.ADMIN -> 1
                User.Role.CUSTOMER -> 2
                User.Role.MERCHANT, User.Role.MANAGER -> 3
                User.Role.BOSSMERCHANT -> 5
                User.Role.SHIPPER, User.Role.DRIVER -> 4
                else -> 2
            }

            val updatedUser = user.copy(
                name = name,
                email = email,
                phone = phone,
                role = selectedRole,
                roleId = roleId,
                storeId = storeId,
                bossId = bossId,
                avatarUrl = finalAvatar,
                status = finalStatus,
                isActive = isUnlocked
            )

            onSave(updatedUser, trustScore, completedOrders, boomOrders, adjustReason)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditUserDialogFragment"
    }
}
