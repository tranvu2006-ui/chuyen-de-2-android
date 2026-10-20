package com.chuyen_de_2.foodorder.customer.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.CustomerAddress
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityProfileBinding
import com.chuyen_de_2.foodorder.customer.databinding.DialogAddressBookBinding
import com.chuyen_de_2.foodorder.customer.databinding.DialogEditAddressBinding
import com.chuyen_de_2.foodorder.customer.databinding.DialogEditProfileBinding
import com.chuyen_de_2.foodorder.customer.ui.auth.LoginActivity
import com.chuyen_de_2.foodorder.customer.ui.complaint.ComplaintActivity
import com.chuyen_de_2.foodorder.customer.ui.order.OrderHistoryActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Trang cá nhân & Điểm uy tín chuẩn b11.html
 * Hỗ trợ cập nhật Avatar, Tên, Số điện thoại và Quản lý Sổ địa chỉ giao hàng
 */
@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private var currentUserData: User? = null

    private val hcmDistricts = listOf(
        "Quận 1", "Quận 3", "Quận 4", "Quận 5", "Quận 6", "Quận 7", "Quận 8",
        "Quận 10", "Quận 11", "Quận 12", "Bình Thạnh", "Gò Vấp", "Phú Nhuận",
        "Tân Bình", "Tân Phú", "Bình Tân", "TP. Thủ Đức", "Củ Chi", "Hóc Môn",
        "Bình Chánh", "Nhà Bè", "Cần Giờ"
    )

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

            lifecycleScope.launch {
                val userResult = authRepository.getCurrentUserData()
                userResult.onSuccess { user ->
                    currentUserData = user
                    binding.tvUserName.text = user.name.ifBlank { currentUser.displayName ?: "Người dùng Oishi" }
                    binding.tvUserPhone.text = if (user.phone.isNotBlank()) user.phone else "Chưa cập nhật SĐT"
                    
                    // Cập nhật avatar nếu có
                    if (user.avatarUrl.isNotBlank()) {
                        binding.tvUserAvatar.text = user.avatarUrl
                    }

                    // Hiển thị vai trò nếu là Merchant / Driver
                    when (user.role) {
                        User.Role.MERCHANT,
                        User.Role.MANAGER -> {
                            binding.tvTrustBadge.text = "🏪 Đối tác Quán"
                        }
                        User.Role.DRIVER,
                        User.Role.SHIPPER -> {
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
                    if (customer.avatarUrl.isNotBlank() && (currentUserData?.avatarUrl.isNullOrBlank())) {
                        binding.tvUserAvatar.text = customer.avatarUrl
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        // Nút hoặc Avatar để chỉnh sửa thông tin cá nhân
        binding.btnEditProfile.setOnClickListener { showEditProfileDialog() }
        binding.flAvatar.setOnClickListener { showEditProfileDialog() }

        // Lịch sử đơn hàng
        binding.btnOrderHistory.setOnClickListener {
            startActivity(Intent(this, OrderHistoryActivity::class.java))
        }

        // Gửi khiếu nại
        binding.btnComplaint.setOnClickListener {
            startActivity(Intent(this, ComplaintActivity::class.java))
        }

        // Quản lý Sổ địa chỉ nhận hàng
        binding.btnAddressBook.setOnClickListener {
            showAddressBookBottomSheet()
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

    /** Mở Dialog chỉnh sửa thông tin cá nhân: Avatar, Tên, Số điện thoại */
    private fun showEditProfileDialog() {
        val dialogBinding = DialogEditProfileBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val currentName = currentUserData?.name ?: binding.tvUserName.text.toString()
        val currentPhone = currentUserData?.phone ?: if (binding.tvUserPhone.text.toString().startsWith("0")) binding.tvUserPhone.text.toString() else ""
        val currentAvatar = currentUserData?.avatarUrl ?: binding.tvUserAvatar.text.toString()

        dialogBinding.etEditName.setText(currentName)
        dialogBinding.etEditPhone.setText(currentPhone)

        // Đánh dấu avatar hiện tại trong RadioGroup
        for (i in 0 until dialogBinding.rgAvatars.childCount) {
            val rb = dialogBinding.rgAvatars.getChildAt(i) as? RadioButton
            if (rb?.text?.toString() == currentAvatar) {
                rb.isChecked = true
                break
            }
        }

        dialogBinding.btnCancelEdit.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSaveProfile.setOnClickListener {
            val newName = dialogBinding.etEditName.text.toString().trim()
            val newPhone = dialogBinding.etEditPhone.text.toString().trim()

            if (newName.isBlank()) {
                dialogBinding.tilEditName.error = "Vui lòng nhập họ và tên"
                return@setOnClickListener
            }
            dialogBinding.tilEditName.error = null

            // Lấy avatar được chọn
            val selectedRbId = dialogBinding.rgAvatars.checkedRadioButtonId
            val selectedRb = dialogBinding.root.findViewById<RadioButton>(selectedRbId)
            val selectedAvatar = selectedRb?.text?.toString() ?: "👤"

            dialogBinding.btnSaveProfile.isEnabled = false
            dialogBinding.btnSaveProfile.text = "Đang lưu..."

            lifecycleScope.launch {
                val result = authRepository.updateUserProfile(
                    name = newName,
                    phone = newPhone,
                    avatarUrl = selectedAvatar
                )

                if (result.isSuccess) {
                    Toast.makeText(this@ProfileActivity, "✅ Đã cập nhật thông tin thành công!", Toast.LENGTH_SHORT).show()
                    binding.tvUserName.text = newName
                    binding.tvUserPhone.text = if (newPhone.isNotBlank()) newPhone else "Chưa cập nhật SĐT"
                    binding.tvUserAvatar.text = selectedAvatar
                    currentUserData = currentUserData?.copy(name = newName, phone = newPhone, avatarUrl = selectedAvatar)
                    dialog.dismiss()
                } else {
                    dialogBinding.btnSaveProfile.isEnabled = true
                    dialogBinding.btnSaveProfile.text = "Lưu Thay Đổi"
                    Toast.makeText(this@ProfileActivity, "❌ Lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    /** Mở BottomSheet Sổ Địa Chỉ Nhận Hàng */
    private fun showAddressBookBottomSheet() {
        val uid = firebaseAuth.currentUser?.uid ?: run {
            Toast.makeText(this, "Vui lòng đăng nhập để xem sổ địa chỉ", Toast.LENGTH_SHORT).show()
            return
        }

        val sheetBinding = DialogAddressBookBinding.inflate(LayoutInflater.from(this))
        val bottomSheet = BottomSheetDialog(this)
        bottomSheet.setContentView(sheetBinding.root)

        val addressAdapter = AddressBookAdapter(
            onSelectClick = { address ->
                // Khi bấm vào địa chỉ: hỏi muốn đặt làm mặc định không
                if (!address.isDefault) {
                    setDefaultAddressAction(uid, address.addressId)
                }
            },
            onSetDefaultClick = { address ->
                setDefaultAddressAction(uid, address.addressId)
            },
            onDeleteClick = { address ->
                deleteAddressAction(uid, address)
            }
        )

        sheetBinding.rvAddressBook.apply {
            layoutManager = LinearLayoutManager(this@ProfileActivity)
            adapter = addressAdapter
        }

        // Lắng nghe realtime danh sách địa chỉ từ Firebase
        lifecycleScope.launch {
            firebaseRepository.observeCustomerAddresses(uid).collectLatest { list ->
                addressAdapter.submitList(list)
                if (list.isEmpty()) {
                    sheetBinding.tvEmptyAddress.visibility = View.VISIBLE
                    sheetBinding.rvAddressBook.visibility = View.GONE
                } else {
                    sheetBinding.tvEmptyAddress.visibility = View.GONE
                    sheetBinding.rvAddressBook.visibility = View.VISIBLE
                }
            }
        }

        sheetBinding.btnCloseAddressBook.setOnClickListener { bottomSheet.dismiss() }

        sheetBinding.btnAddNewAddress.setOnClickListener {
            showAddAddressDialog(uid)
        }

        bottomSheet.show()
    }

    /** Mở dialog thêm mới địa chỉ */
    private fun showAddAddressDialog(uid: String) {
        val dialogBinding = DialogEditAddressBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Nạp dropdown Quận/Huyện TP.HCM
        val districtAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, hcmDistricts)
        dialogBinding.spAddressDistrict.adapter = districtAdapter

        // Tự động điền tên và SĐT của user nếu có
        dialogBinding.etReceiverName.setText(currentUserData?.name ?: "")
        dialogBinding.etReceiverPhone.setText(currentUserData?.phone ?: "")

        dialogBinding.btnCancelAddress.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSaveAddress.setOnClickListener {
            val label = when {
                dialogBinding.rbLabelWork.isChecked -> "Công ty"
                dialogBinding.rbLabelOther.isChecked -> "Khác"
                else -> "Nhà riêng"
            }
            val receiverName = dialogBinding.etReceiverName.text.toString().trim()
            val receiverPhone = dialogBinding.etReceiverPhone.text.toString().trim()
            val streetAddress = dialogBinding.etStreetAddress.text.toString().trim()
            val selectedDistrict = dialogBinding.spAddressDistrict.selectedItem?.toString() ?: "Quận 1"
            val isDefault = dialogBinding.cbIsDefault.isChecked

            if (streetAddress.isBlank()) {
                dialogBinding.tilStreetAddress.error = "Vui lòng nhập địa chỉ cụ thể"
                return@setOnClickListener
            }
            dialogBinding.tilStreetAddress.error = null

            dialogBinding.btnSaveAddress.isEnabled = false
            dialogBinding.btnSaveAddress.text = "Đang lưu..."

            val newAddress = CustomerAddress(
                customerId = uid,
                label = label,
                receiverName = receiverName.ifBlank { currentUserData?.name ?: "Khách hàng" },
                receiverPhone = receiverPhone.ifBlank { currentUserData?.phone ?: "" },
                streetAddress = streetAddress,
                district = selectedDistrict,
                isDefault = isDefault
            )

            lifecycleScope.launch {
                val result = firebaseRepository.saveCustomerAddress(uid, newAddress)
                if (result.isSuccess) {
                    Toast.makeText(this@ProfileActivity, "✅ Đã lưu địa chỉ vào sổ!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    dialogBinding.btnSaveAddress.isEnabled = true
                    dialogBinding.btnSaveAddress.text = "Lưu Địa Chỉ"
                    Toast.makeText(this@ProfileActivity, "❌ Lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun setDefaultAddressAction(uid: String, addressId: String) {
        lifecycleScope.launch {
            val result = firebaseRepository.setDefaultAddress(uid, addressId)
            if (result.isSuccess) {
                Toast.makeText(this@ProfileActivity, "⭐ Đã đặt làm địa chỉ mặc định!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteAddressAction(uid: String, address: CustomerAddress) {
        AlertDialog.Builder(this)
            .setTitle("Xóa địa chỉ")
            .setMessage("Bạn có chắc chắn muốn xóa địa chỉ '${address.label}: ${address.streetAddress}'?")
            .setPositiveButton("Xóa") { _, _ ->
                lifecycleScope.launch {
                    val result = firebaseRepository.deleteCustomerAddress(uid, address.addressId)
                    if (result.isSuccess) {
                        Toast.makeText(this@ProfileActivity, "🗑️ Đã xóa địa chỉ", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
