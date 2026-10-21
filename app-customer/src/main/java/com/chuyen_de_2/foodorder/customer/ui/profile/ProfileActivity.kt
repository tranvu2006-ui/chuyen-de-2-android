package com.chuyen_de_2.foodorder.customer.ui.profile

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
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
import com.chuyen_de_2.foodorder.customer.ui.checkout.SelectLocationActivity
import com.chuyen_de_2.foodorder.customer.ui.complaint.ComplaintActivity
import com.chuyen_de_2.foodorder.customer.ui.order.OrderHistoryActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Trang cá nhân & Điểm uy tín chuẩn b11.html
 * Hỗ trợ:
 * - Upload ảnh avatar từ máy hoặc chọn Emoji avatar
 * - Quản lý Sổ địa chỉ giao hàng
 * - Lấy GPS và kéo bản đồ để chọn địa chỉ nhận hàng
 */
/**
 * Tên chức năng: Trang Cá Nhân, Điểm Uy Tín & Sổ Địa Chỉ Khách Hàng (Customer Profile & Trust Score)
 * Cách hoạt động:
 *  - Hiển thị thông tin cá nhân (Ảnh đại diện, Họ tên, Email, Số điện thoại).
 *  - Quản lý Điểm tín nhiệm / Uy tín (Trust Score) giúp đánh giá độ tin cậy của tài khoản khi đặt đơn COD.
 *  - Quản lý Sổ địa chỉ giao hàng nhận món (Thêm, sửa, xóa, đặt làm mặc định).
 *  - Tích hợp chọn vị trí qua bản đồ vệ tinh / Google Maps [SelectLocationActivity].
 *  - Cung cấp lối tắt tới Lịch sử đơn hàng, Khiếu nại và nút Đăng xuất.
 * Luồng hướng tới:
 *  - Chuyển sang [SelectLocationActivity] khi thêm địa chỉ bản đồ.
 *  - Chuyển sang [OrderHistoryActivity], [ComplaintActivity], hoặc [LoginActivity] khi đăng xuất.
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

    // Biến tạm lưu ảnh avatar vừa chọn từ thiết bị
    private var pendingAvatarBase64: String? = null
    private var currentEditDialogBinding: DialogEditProfileBinding? = null
    private var currentAddressDialogBinding: DialogEditAddressBinding? = null

    private val hcmDistricts = listOf(
        "Quận 1", "Quận 3", "Quận 4", "Quận 5", "Quận 6", "Quận 7", "Quận 8",
        "Quận 10", "Quận 11", "Quận 12", "Bình Thạnh", "Gò Vấp", "Phú Nhuận",
        "Tân Bình", "Tân Phú", "Bình Tân", "TP. Thủ Đức", "Củ Chi", "Hóc Môn",
        "Bình Chánh", "Nhà Bè", "Cần Giờ"
    )

    // Launcher chọn ảnh avatar từ thư viện
    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processAndPreviewAvatar(uri)
        }
    }

    // Launcher mở màn hình bản đồ để lấy vị trí GPS và kéo chọn
    private val selectLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val street = data.getStringExtra(SelectLocationActivity.EXTRA_STREET_ADDRESS) ?: ""
            val district = data.getStringExtra(SelectLocationActivity.EXTRA_DISTRICT) ?: ""

            currentAddressDialogBinding?.let { dBinding ->
                if (street.isNotBlank()) {
                    dBinding.etStreetAddress.setText(street)
                }
                if (district.isNotBlank()) {
                    val index = hcmDistricts.indexOfFirst { it.contains(district, ignoreCase = true) }
                    if (index != -1) {
                        dBinding.spAddressDistrict.setSelection(index)
                    }
                }
                Toast.makeText(this, "📍 Đã lấy vị trí từ bản đồ thành công!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
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
                    binding.tvUserName.text = user.name.ifBlank { currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "Người dùng Oishi" }
                    binding.tvUserPhone.text = if (user.phone.isNotBlank()) user.phone else "Chưa cập nhật SĐT"
                    
                    // Hiển thị Avatar (Ảnh hoặc Emoji)
                    displayAvatar(user.avatarUrl)

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
                        displayAvatar(customer.avatarUrl)
                    }
                }
            }
        }
    }

    /** Hiển thị Avatar: Ảnh thật (URL/Base64) hoặc Biểu tượng Emoji */
    private fun displayAvatar(avatarUrl: String) {
        if (avatarUrl.startsWith("data:image") || avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            binding.ivUserAvatar.visibility = View.VISIBLE
            binding.ivUserAvatarPlaceholder.visibility = View.GONE
            Glide.with(this)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.bg_badge_orange)
                .error(R.drawable.bg_badge_orange)
                .into(binding.ivUserAvatar)
        } else {
            binding.ivUserAvatar.visibility = View.GONE
            binding.ivUserAvatarPlaceholder.visibility = View.VISIBLE
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

    /** Mở Dialog chỉnh sửa thông tin cá nhân: Upload ảnh / Chọn Emoji, Tên, Số điện thoại */
    private fun showEditProfileDialog() {
        val dialogBinding = DialogEditProfileBinding.inflate(LayoutInflater.from(this))
        currentEditDialogBinding = dialogBinding
        pendingAvatarBase64 = null

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val currentName = currentUserData?.name ?: binding.tvUserName.text.toString()
        val currentPhone = currentUserData?.phone ?: if (binding.tvUserPhone.text.toString().startsWith("0")) binding.tvUserPhone.text.toString() else ""
        val currentAvatar = currentUserData?.avatarUrl.orEmpty()

        dialogBinding.etEditName.setText(currentName)
        dialogBinding.etEditPhone.setText(currentPhone)

        // Hiển thị Preview avatar hiện tại
        if (currentAvatar.startsWith("data:image") || currentAvatar.startsWith("http")) {
            dialogBinding.ivEditAvatarPreview.visibility = View.VISIBLE
            dialogBinding.ivEditAvatarPlaceholder.visibility = View.GONE
            Glide.with(this).load(currentAvatar).circleCrop().into(dialogBinding.ivEditAvatarPreview)
        } else {
            dialogBinding.ivEditAvatarPreview.visibility = View.GONE
            dialogBinding.ivEditAvatarPlaceholder.visibility = View.VISIBLE
        }

        // Nút chọn ảnh từ thiết bị
        dialogBinding.btnPickImage.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
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

            // Xác định Avatar cuối cùng: Ảnh vừa upload > Ảnh cũ nếu là ảnh > rỗng
            val finalAvatar = when {
                pendingAvatarBase64 != null -> pendingAvatarBase64!!
                currentAvatar.startsWith("data:image") || currentAvatar.startsWith("http") -> currentAvatar
                else -> ""
            }

            dialogBinding.btnSaveProfile.isEnabled = false
            dialogBinding.btnSaveProfile.text = "Đang lưu..."

            lifecycleScope.launch {
                val result = authRepository.updateUserProfile(
                    name = newName,
                    phone = newPhone,
                    avatarUrl = finalAvatar
                )

                if (result.isSuccess) {
                    Toast.makeText(this@ProfileActivity, "✅ Đã cập nhật thông tin thành công!", Toast.LENGTH_SHORT).show()
                    binding.tvUserName.text = newName
                    binding.tvUserPhone.text = if (newPhone.isNotBlank()) newPhone else "Chưa cập nhật SĐT"
                    displayAvatar(finalAvatar)
                    currentUserData = currentUserData?.copy(name = newName, phone = newPhone, avatarUrl = finalAvatar)
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

    /** Xử lý ảnh vừa chọn từ thư viện, nén và chuyển sang Base64 */
    private fun processAndPreviewAvatar(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                // Resize ảnh về tối đa 180x180 để tối ưu dung lượng lưu trữ trên Firebase
                val maxDim = 180
                val width = originalBitmap.width
                val height = originalBitmap.height
                val ratio = width.toFloat() / height.toFloat()
                val targetWidth = if (width > height) maxDim else (maxDim * ratio).toInt()
                val targetHeight = if (height >= width) maxDim else (maxDim / ratio).toInt()

                val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, maxOf(1, targetWidth), maxOf(1, targetHeight), true)

                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
                val byteArray = outputStream.toByteArray()

                val base64String = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
                pendingAvatarBase64 = base64String

                // Cập nhật lên preview trong dialog
                currentEditDialogBinding?.let { dBinding ->
                    dBinding.ivEditAvatarPreview.visibility = View.VISIBLE
                    dBinding.ivEditAvatarPlaceholder.visibility = View.GONE
                    Glide.with(this)
                        .load(scaledBitmap)
                        .circleCrop()
                        .into(dBinding.ivEditAvatarPreview)
                }
                Toast.makeText(this, "📷 Đã chọn ảnh đại diện!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể đọc ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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

    /** Mở dialog thêm mới địa chỉ — có tích hợp GPS và kéo bản đồ để chọn */
    private fun showAddAddressDialog(uid: String) {
        val dialogBinding = DialogEditAddressBinding.inflate(LayoutInflater.from(this))
        currentAddressDialogBinding = dialogBinding

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

        // NÚT MỞ BẢN ĐỒ LẤY GPS VÀ KÉO CHỌN ĐỊA CHỈ
        dialogBinding.btnPickLocationOnMap.setOnClickListener {
            val intent = Intent(this, SelectLocationActivity::class.java)
            selectLocationLauncher.launch(intent)
        }

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
