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
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
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
import com.chuyen_de_2.foodorder.customer.ui.complaint.CustomerComplaintTrackingActivity
import com.chuyen_de_2.foodorder.customer.ui.home.HomeActivity
import com.chuyen_de_2.foodorder.customer.ui.order.OrderHistoryActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Tên chức năng: Fragment Trang Cá Nhân, Điểm Uy Tín & Sổ Địa Chỉ Khách Hàng (Customer Profile Fragment)
 * Cách hoạt động:
 *  - Tích hợp trực tiếp vào thanh Bottom Navigation của HomeActivity.
 *  - Hiển thị thông tin cá nhân (Ảnh đại diện, Họ tên, Email, SĐT).
 *  - Quản lý Điểm tín nhiệm / Uy tín (Trust Score).
 *  - Quản lý Sổ địa chỉ giao hàng nhận món (GPS + bản đồ).
 *  - Chuyển hướng tới Lịch sử đơn hàng, Khiếu nại và Đăng xuất.
 */
@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: ActivityProfileBinding? = null
    private val binding get() = _binding!!

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
                Toast.makeText(requireContext(), "📍 Đã lấy vị trí từ bản đồ thành công!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        InsetUtils.applyToolbarInsets(binding.toolbar)

        setupToolbar()
        loadUserProfile()
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            (activity as? HomeActivity)?.selectTab(R.id.nav_home)
                ?: activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    private fun loadUserProfile() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            _binding?.tvUserEmail?.text = currentUser.email ?: ""

            viewLifecycleOwner.lifecycleScope.launch {
                val userResult = authRepository.getCurrentUserData()
                userResult.onSuccess { user ->
                    currentUserData = user
                    _binding?.let { b ->
                        b.tvUserName.text = user.name.ifBlank {
                            currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "Người dùng Oishi"
                        }
                        b.tvUserPhone.text = if (user.phone.isNotBlank()) user.phone else "Chưa cập nhật SĐT"

                        displayAvatar(user.avatarUrl)

                        when (user.role) {
                            User.Role.MERCHANT,
                            User.Role.MANAGER -> {
                                b.tvTrustBadge.text = "🏪 Đối tác Quán"
                            }
                            User.Role.DRIVER,
                            User.Role.SHIPPER -> {
                                b.tvTrustBadge.text = "🛵 Tài xế Giao Hàng"
                            }
                            else -> {
                                // CUSTOMER
                            }
                        }
                    }
                }

                launch {
                    firebaseRepository.observeCustomerProfile(currentUser.uid).collectLatest { customer ->
                        _binding?.let { b ->
                            b.tvTrustScoreNumber.text = customer.trustScore.toString()
                            b.progressTrustScore.progress = customer.trustScore
                            b.tvTrustBadge.text = customer.tierBadge
                            b.tvTrustTotalOrders.text = "Tổng đơn: ${customer.totalOrders}"
                            b.tvTrustCompletedOrders.text = "✓ Nhận: ${customer.completedOrders}"
                            b.tvTrustBoomOrders.text = "⚠️ Bom: ${customer.boomOrders}"
                            b.tvTrustRuleDesc.text = customer.privilegeDescription
                            val scoreColor = when {
                                customer.trustScore >= 95 -> android.graphics.Color.parseColor("#059669")
                                customer.trustScore >= 80 -> android.graphics.Color.parseColor("#D97706")
                                customer.trustScore >= 50 -> android.graphics.Color.parseColor("#EA580C")
                                else -> android.graphics.Color.parseColor("#DC2626")
                            }
                            b.tvTrustScoreNumber.setTextColor(scoreColor)
                            b.progressTrustScore.progressTintList = android.content.res.ColorStateList.valueOf(scoreColor)
                            if (customer.avatarUrl.isNotBlank() && (currentUserData?.avatarUrl.isNullOrBlank())) {
                                displayAvatar(customer.avatarUrl)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Hiển thị Avatar: Ảnh thật (URL/Base64) hoặc Biểu tượng Emoji */
    private fun displayAvatar(avatarUrl: String) {
        val b = _binding ?: return
        if (avatarUrl.startsWith("data:image") || avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            b.ivUserAvatar.visibility = View.VISIBLE
            b.ivUserAvatarPlaceholder.visibility = View.GONE
            Glide.with(this)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.bg_badge_orange)
                .error(R.drawable.bg_badge_orange)
                .into(b.ivUserAvatar)
        } else {
            b.ivUserAvatar.visibility = View.GONE
            b.ivUserAvatarPlaceholder.visibility = View.VISIBLE
        }
    }

    private fun setupListeners() {
        binding.btnEditProfile.setOnClickListener { showEditProfileDialog() }
        binding.flAvatar.setOnClickListener { showEditProfileDialog() }
        binding.btnViewTrustHistory.setOnClickListener { showTrustScoreHistoryDialog() }
        binding.cardTrustScore.setOnClickListener { showTrustScoreHistoryDialog() }

        // Lịch sử đơn hàng: chuyển trực tiếp sang tab Orders của HomeActivity
        binding.btnOrderHistory.setOnClickListener {
            (activity as? HomeActivity)?.selectTab(R.id.nav_orders)
                ?: startActivity(Intent(requireContext(), OrderHistoryActivity::class.java))
        }

        // Trung tâm khiếu nại & Theo dõi xử lý
        binding.btnComplaint.setOnClickListener {
            startActivity(Intent(requireContext(), CustomerComplaintTrackingActivity::class.java))
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
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Hotline: 1900 1234", Toast.LENGTH_SHORT).show()
            }
        }

        // Đăng xuất
        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Đăng xuất tài khoản")
                .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi Oishi Food?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Đăng xuất") { _, _ ->
                    authRepository.logout()
                    val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("LOGOUT", true)
                    }
                    startActivity(intent)
                    activity?.finish()
                }
                .show()
        }
    }

    private fun showTrustScoreHistoryDialog() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val cust = authRepository.getCurrentCustomerData().getOrNull()
                ?: com.chuyen_de_2.foodorder.core.data.model.Customer(customerId = uid, trustScore = 100)
            val logs = try { firebaseRepository.observeTrustScoreLogs(uid).first() } catch (_: Exception) { emptyList() }

            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            val sb = StringBuilder()
            sb.append("🛡️ ĐIỂM HIỆN TẠI: ${cust.trustScore}/100 (${cust.tierBadge})\n")
            sb.append("• Tổng đơn: ${cust.totalOrders} | Thành công: ${cust.completedOrders} | Bom/Vi phạm: ${cust.boomOrders}\n\n")
            sb.append("📌 QUY TẮC HẠNG & QUYỀN LỢI:\n")
            sb.append("• 💎 Kim Cương (95-100đ): Mở khóa COD & Giảm 5.000đ phí ship mọi đơn\n")
            sb.append("• 🥇 Hạng Vàng (90-94đ): Đạt từ 90 điểm: Mở khóa thanh toán tiền mặt COD\n")
            sb.append("• 🥈 Dưới 90 điểm (0-89đ): CẤM COD (Khóa hoàn toàn COD, chọn Đặt cọc 20% hoặc Trả thẳng)\n")
            sb.append("• 🚫 Đình Chỉ (<20đ): Tự động khóa tài khoản do bom hàng\n\n")
            sb.append("⚡ CÁCH TÍNH ĐIỂM TỰ ĐỘNG:\n")
            sb.append("• Nhận đơn & thanh toán thành công: +2 điểm\n")
            sb.append("• Hủy đơn muộn sau khi quán đã chế biến: -10 điểm\n")
            sb.append("• Không nhận hàng / Bom đơn: -30 điểm\n\n")
            sb.append("📜 LỊCH SỬ BIẾN ĐỘNG GẦN ĐÂY:\n")
            if (logs.isEmpty()) {
                sb.append("• Chưa có biến động điểm nào được ghi nhận (Mặc định: 100/100 điểm).")
            } else {
                logs.take(15).forEach { log ->
                    val sign = if (log.changeAmount >= 0) "+${log.changeAmount}" else "${log.changeAmount}"
                    val dateStr = sdf.format(java.util.Date(log.createdAt))
                    sb.append("• [$dateStr] $sign điểm (${log.oldScore} ➔ ${log.newScore}): ${log.reason}\n")
                }
            }

            if (isAdded) {
                AlertDialog.Builder(requireContext())
                    .setTitle("🛡️ Hồ Sơ & Lịch Sử Điểm Uy Tín")
                    .setMessage(sb.toString().trim())
                    .setPositiveButton("Đã Hiểu", null)
                    .show()
            }
        }
    }

    private fun showEditProfileDialog() {
        val ctx = requireContext()
        val dialogBinding = DialogEditProfileBinding.inflate(LayoutInflater.from(ctx))
        currentEditDialogBinding = dialogBinding
        pendingAvatarBase64 = null

        val dialog = AlertDialog.Builder(ctx)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val currentName = currentUserData?.name ?: binding.tvUserName.text.toString()
        val currentPhone = currentUserData?.phone
            ?: if (binding.tvUserPhone.text.toString().startsWith("0")) binding.tvUserPhone.text.toString() else ""
        val currentAvatar = currentUserData?.avatarUrl.orEmpty()

        dialogBinding.etEditName.setText(currentName)
        dialogBinding.etEditPhone.setText(currentPhone)

        if (currentAvatar.startsWith("data:image") || currentAvatar.startsWith("http")) {
            dialogBinding.ivEditAvatarPreview.visibility = View.VISIBLE
            dialogBinding.ivEditAvatarPlaceholder.visibility = View.GONE
            Glide.with(this).load(currentAvatar).circleCrop().into(dialogBinding.ivEditAvatarPreview)
        } else {
            dialogBinding.ivEditAvatarPreview.visibility = View.GONE
            dialogBinding.ivEditAvatarPlaceholder.visibility = View.VISIBLE
        }

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

            val finalAvatar = when {
                pendingAvatarBase64 != null -> pendingAvatarBase64!!
                currentAvatar.startsWith("data:image") || currentAvatar.startsWith("http") -> currentAvatar
                else -> ""
            }

            dialogBinding.btnSaveProfile.isEnabled = false
            dialogBinding.btnSaveProfile.text = "Đang lưu..."

            viewLifecycleOwner.lifecycleScope.launch {
                val result = authRepository.updateUserProfile(
                    name = newName,
                    phone = newPhone,
                    avatarUrl = finalAvatar
                )

                if (result.isSuccess) {
                    Toast.makeText(ctx, "✅ Đã cập nhật thông tin thành công!", Toast.LENGTH_SHORT).show()
                    _binding?.let { b ->
                        b.tvUserName.text = newName
                        b.tvUserPhone.text = if (newPhone.isNotBlank()) newPhone else "Chưa cập nhật SĐT"
                        displayAvatar(finalAvatar)
                    }
                    currentUserData = currentUserData?.copy(name = newName, phone = newPhone, avatarUrl = finalAvatar)
                    dialog.dismiss()
                } else {
                    dialogBinding.btnSaveProfile.isEnabled = true
                    dialogBinding.btnSaveProfile.text = "Lưu Thay Đổi"
                    Toast.makeText(ctx, "❌ Lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun processAndPreviewAvatar(uri: Uri) {
        val ctx = context ?: return
        try {
            val inputStream = ctx.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
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

                currentEditDialogBinding?.let { dBinding ->
                    dBinding.ivEditAvatarPreview.visibility = View.VISIBLE
                    dBinding.ivEditAvatarPlaceholder.visibility = View.GONE
                    Glide.with(this)
                        .load(scaledBitmap)
                        .circleCrop()
                        .into(dBinding.ivEditAvatarPreview)
                }
                Toast.makeText(ctx, "📷 Đã chọn ảnh đại diện!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Không thể đọc ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddressBookBottomSheet() {
        val ctx = requireContext()
        val uid = firebaseAuth.currentUser?.uid ?: run {
            Toast.makeText(ctx, "Vui lòng đăng nhập để xem sổ địa chỉ", Toast.LENGTH_SHORT).show()
            return
        }

        val sheetBinding = DialogAddressBookBinding.inflate(LayoutInflater.from(ctx))
        val bottomSheet = BottomSheetDialog(ctx)
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
            layoutManager = LinearLayoutManager(ctx)
            adapter = addressAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
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
        sheetBinding.btnAddNewAddress.setOnClickListener { showAddAddressDialog(uid) }

        bottomSheet.show()
    }

    private fun showAddAddressDialog(uid: String) {
        val ctx = requireContext()
        val dialogBinding = DialogEditAddressBinding.inflate(LayoutInflater.from(ctx))
        currentAddressDialogBinding = dialogBinding

        val dialog = AlertDialog.Builder(ctx)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val districtAdapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, hcmDistricts)
        dialogBinding.spAddressDistrict.adapter = districtAdapter

        dialogBinding.etReceiverName.setText(currentUserData?.name ?: "")
        dialogBinding.etReceiverPhone.setText(currentUserData?.phone ?: "")

        dialogBinding.btnPickLocationOnMap.setOnClickListener {
            val intent = Intent(ctx, SelectLocationActivity::class.java)
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

            viewLifecycleOwner.lifecycleScope.launch {
                val result = firebaseRepository.saveCustomerAddress(uid, newAddress)
                if (result.isSuccess) {
                    Toast.makeText(ctx, "✅ Đã lưu địa chỉ vào sổ!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    dialogBinding.btnSaveAddress.isEnabled = true
                    dialogBinding.btnSaveAddress.text = "Lưu Địa Chỉ"
                    Toast.makeText(ctx, "❌ Lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun setDefaultAddressAction(uid: String, addressId: String) {
        val ctx = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = firebaseRepository.setDefaultAddress(uid, addressId)
            if (result.isSuccess) {
                Toast.makeText(ctx, "⭐ Đã đặt làm địa chỉ mặc định!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteAddressAction(uid: String, address: CustomerAddress) {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle("Xóa địa chỉ")
            .setMessage("Bạn có chắc chắn muốn xóa địa chỉ '${address.label}: ${address.streetAddress}'?")
            .setPositiveButton("Xóa") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = firebaseRepository.deleteCustomerAddress(uid, address.addressId)
                    if (result.isSuccess) {
                        Toast.makeText(ctx, "🗑️ Đã xóa địa chỉ", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
