package com.chuyen_de_2.foodorder.admin.ui.monitor

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.chuyen_de_2.foodorder.admin.databinding.DialogEditUserBinding
import com.chuyen_de_2.foodorder.core.data.model.Customer
import com.chuyen_de_2.foodorder.core.data.model.User
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

    private val roleOptions = listOf(
        User.Role.CUSTOMER,
        User.Role.MERCHANT,
        User.Role.SHIPPER,
        User.Role.ADMIN
    )

    private val statusOptions = listOf(
        User.Status.ACTIVE,
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
            android.R.layout.simple_spinner_dropdown_item,
            listOf("🛍️ Khách Hàng (CUSTOMER)", "🏪 Chủ Quán (MERCHANT)", "🛵 Tài Xế Giao Hàng (SHIPPER)", "👑 Quản Trị Viên (ADMIN)")
        )
        binding.spnUserRole.adapter = roleAdapter

        val statusAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("🟢 Đang Hoạt Động (ACTIVE)", "⚪ Ngừng Hoạt Động (INACTIVE)", "⚫ Ngoại Tuyến (OFFLINE)")
        )
        binding.spnUserStatus.adapter = statusAdapter
    }

    private fun populateCurrentData() {
        binding.tvUserUidSubtitle.text = "UID: ${user.uid}"
        binding.etUserName.setText(user.name)
        binding.etUserEmail.setText(user.email)
        binding.etUserPhone.setText(user.phone)
        binding.etUserStoreId.setText(user.storeId)
        binding.etUserAvatarUrl.setText(user.avatarUrl)

        val currentScore = customer?.trustScore ?: 100
        val currentCompleted = customer?.completedOrders ?: 0
        val currentBoom = customer?.boomOrders ?: 0

        binding.etUserTrustScore.setText(currentScore.toString())
        binding.etUserCompletedOrders.setText(currentCompleted.toString())
        binding.etUserBoomOrders.setText(currentBoom.toString())

        val roleIndex = when (user.role.uppercase()) {
            User.Role.CUSTOMER -> 0
            User.Role.MERCHANT -> 1
            User.Role.SHIPPER, User.Role.DRIVER -> 2
            User.Role.ADMIN -> 3
            else -> 0
        }
        binding.spnUserRole.setSelection(roleIndex)

        val statusIndex = when (user.status.uppercase()) {
            User.Status.ACTIVE -> 0
            User.Status.INACTIVE -> 1
            User.Status.OFFLINE -> 2
            else -> if (user.isActive) 0 else 1
        }
        binding.spnUserStatus.setSelection(statusIndex)

        binding.switchUserActive.isChecked = user.isActive
    }

    private fun setupListeners() {
        binding.btnCloseUserDialog.setOnClickListener { dismiss() }
        binding.btnCancelEditUser.setOnClickListener { dismiss() }

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
            val avatarUrl = binding.etUserAvatarUrl.text?.toString().orEmpty().trim()

            val trustScore = (binding.etUserTrustScore.text?.toString()?.toIntOrNull() ?: 100).coerceIn(0, 100)
            val completedOrders = (binding.etUserCompletedOrders.text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val boomOrders = (binding.etUserBoomOrders.text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val adjustReason = binding.etTrustAdjustReason.text?.toString().orEmpty().trim()

            val selectedRole = roleOptions.getOrElse(binding.spnUserRole.selectedItemPosition) { User.Role.CUSTOMER }
            val selectedStatus = statusOptions.getOrElse(binding.spnUserStatus.selectedItemPosition) { User.Status.ACTIVE }
            val isActive = binding.switchUserActive.isChecked

            val roleId = when (selectedRole) {
                User.Role.ADMIN -> 1
                User.Role.CUSTOMER -> 2
                User.Role.MERCHANT -> 3
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
                avatarUrl = avatarUrl,
                status = selectedStatus,
                isActive = isActive
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
