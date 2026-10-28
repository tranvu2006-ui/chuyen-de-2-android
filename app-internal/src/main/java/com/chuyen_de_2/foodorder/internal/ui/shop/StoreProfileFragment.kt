package com.chuyen_de_2.foodorder.internal.ui.shop

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.content.Intent
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.FragmentStoreProfileBinding
import com.chuyen_de_2.foodorder.internal.ui.auth.SelectLocationActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import java.io.ByteArrayOutputStream

/**
 * Tên chức năng: Quản Lý Hồ Sơ & Cài Đặt Quán Ăn (Merchant Profile & Store Settings)
 * Cách hoạt động:
 *  - Hiển thị đầy đủ thông tin pháp lý và nhận diện của quán: Tên, địa chỉ, số điện thoại, chủ hộ kinh doanh, CCCD, ảnh đại diện.
 *  - Cung cấp cấu hình vận hành: Giờ mở cửa, thời gian chuẩn bị món ăn trung bình.
 *  - Quản lý thông tin tài khoản ngân hàng thụ hưởng (VietQR / Napas) để hệ thống đối soát doanh thu.
 *  - Cho phép chủ quán mở hộp thoại chỉnh sửa trực tiếp và đồng bộ lên Firebase.
 * Luồng hướng tới:
 *  - Lưu thông tin vào Firestore -> Dữ liệu cập nhật ngay lập tức trên app Khách hàng và app Tài xế.
 *  - Bấm vào badge xác minh -> Mở màn hình [StoreVerificationActivity] để bổ sung thông tin nếu thiếu.
 */
@AndroidEntryPoint
class StoreProfileFragment : Fragment() {

    private var _binding: FragmentStoreProfileBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var authRepository: com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository

    private var currentStoreId = ""
    private var currentStore: Store? = null

    private var editingLat: Double? = null
    private var editingLng: Double? = null
    private var currentDialogEtAddress: EditText? = null
    private var currentDialogEtDistrict: EditText? = null

    private var pendingAvatarBase64: String? = null
    private var currentDialogAvatarPreview: ImageView? = null
    private var currentDialogAvatarPlaceholder: View? = null

    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processAndPreviewAvatar(uri)
        }
    }

    private val selectLocationLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val streetAddress = data.getStringExtra(SelectLocationActivity.EXTRA_STREET_ADDRESS)
            val fullAddress = data.getStringExtra(SelectLocationActivity.EXTRA_FULL_ADDRESS)
            val district = data.getStringExtra(SelectLocationActivity.EXTRA_DISTRICT)
            val lat = data.getDoubleExtra(SelectLocationActivity.EXTRA_LAT, 0.0)
            val lng = data.getDoubleExtra(SelectLocationActivity.EXTRA_LNG, 0.0)

            if (!streetAddress.isNullOrBlank()) {
                currentDialogEtAddress?.setText(streetAddress)
            } else if (!fullAddress.isNullOrBlank()) {
                currentDialogEtAddress?.setText(fullAddress)
            }

            if (!district.isNullOrBlank()) {
                currentDialogEtDistrict?.setText(district)
            }

            if (lat != 0.0 && lng != 0.0) {
                editingLat = lat
                editingLng = lng
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoreProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Tên chức năng: Khởi tạo View hồ sơ quán
     * Cách hoạt động: Thiết lập các sự kiện nút bấm (Quay lại, Sửa, Đăng xuất) và nạp dữ liệu quán từ Firebase.
     * Luồng hướng tới: Chuẩn bị hiển thị thông tin chi tiết.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        resolveCurrentStoreIdAndObserve()
    }

    /**
     * Tên chức năng: Xác định storeId của người dùng đăng nhập
     * Cách hoạt động: Lấy UID của Merchant hiện tại để tra cứu document store trên Firestore.
     * Luồng hướng tới: Gọi [observeStoreProfile].
     */
    private fun resolveCurrentStoreIdAndObserve() {
        val uid = auth.currentUser?.uid.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            currentStoreId = firebaseRepository.resolveStoreIdForUser(uid)
            observeStoreProfile()
        }
    }

    /**
     * Tên chức năng: Thiết lập các sự kiện tương tác trên màn hình hồ sơ
     * Cách hoạt động: Bắt sự kiện quay lại trang trước, mở modal sửa thông tin, và xác nhận đăng xuất tài khoản.
     * Luồng hướng tới: Điều hướng giao diện tương ứng.
     */
    private fun setupListeners() {
        binding.btnBackToOrders.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.btnTopEdit.setOnClickListener {
            showEditStoreDialog()
        }
        binding.btnMainEdit.setOnClickListener {
            showEditStoreDialog()
        }
        binding.btnLogoutStore.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("🚪 Đăng Xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản quán ăn không?")
                .setPositiveButton("Đăng xuất") { _, _ ->
                    authRepository.logout()
                    val intent = android.content.Intent(requireContext(), com.chuyen_de_2.foodorder.internal.ui.auth.LoginActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("LOGOUT", true)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    /**
     * Tên chức năng: Lắng nghe thông tin hồ sơ quán theo thời gian thực (Realtime Store Profile)
     * Cách hoạt động: Theo dõi document `stores/{storeId}` từ Firestore và cập nhật giao diện khi có thay đổi.
     * Luồng hướng tới: Gọi [bindStoreData].
     */
    private fun observeStoreProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (currentStoreId.isBlank()) {
                val activeStore = getDefaultStore()
                currentStore = activeStore
                bindStoreData(activeStore)
                return@launch
            }
            firebaseRepository.observeStore(currentStoreId).collectLatest { store ->
                val activeStore = store ?: getDefaultStore()
                currentStore = activeStore
                bindStoreData(activeStore)
            }
        }
    }

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

    private fun processAndPreviewAvatar(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                val maxDim = 300
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

                currentDialogAvatarPreview?.let { iv ->
                    iv.visibility = View.VISIBLE
                    currentDialogAvatarPlaceholder?.visibility = View.GONE
                    Glide.with(this)
                        .load(scaledBitmap)
                        .transform(CenterCrop(), RoundedCorners(24))
                        .into(iv)
                }
                Toast.makeText(requireContext(), "📷 Đã chọn ảnh đại diện quán!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Lỗi xử lý ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayStoreAvatar(avatarUrl: String) {
        if (avatarUrl.isNotBlank() && (avatarUrl.startsWith("data:image") || avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://"))) {
            binding.ivProfileStoreAvatar.visibility = View.VISIBLE
            binding.tvProfileStoreEmoji.visibility = View.GONE
            Glide.with(this)
                .load(avatarUrl)
                .transform(CenterCrop(), RoundedCorners(24))
                .placeholder(R.drawable.bg_badge_orange)
                .error(R.drawable.bg_badge_orange)
                .into(binding.ivProfileStoreAvatar)
        } else {
            binding.ivProfileStoreAvatar.visibility = View.GONE
            binding.tvProfileStoreEmoji.visibility = View.VISIBLE
        }
    }

    /**
     * Tên chức năng: Đổ dữ liệu thông tin quán lên giao diện
     * Cách hoạt động: Render tên quán, địa chỉ, chủ sở hữu, CCCD, thông tin ngân hàng thụ hưởng và badge trạng thái xác minh hồ sơ.
     * Luồng hướng tới: Hiển thị trực quan cho chủ quán.
     */
    private fun bindStoreData(store: Store) {
        displayStoreAvatar(store.imageUrl)
        binding.tvProfileStoreName.text = store.name.ifBlank { "Quán của bạn" }
        binding.tvProfileStoreAddress.text = if (store.address.isNotBlank()) "📍 ${store.address}" else "📍 Chưa cập nhật địa chỉ"
        binding.tvProfileOwnerName.text = store.ownerName.ifBlank { "Chưa cập nhật" }
        binding.tvProfileCccd.text = store.cccdNumber.ifBlank { "Chưa cập nhật" }
        binding.tvProfilePhone.text = store.phone.ifBlank { "Chưa cập nhật" }
        binding.tvProfileOpenHours.text = store.openHours.ifBlank { "08:00 - 22:00" }
        binding.tvProfilePrepTime.text = store.prepTime.ifBlank { "15 phút" }

        // Tài khoản ngân hàng & Ví MoMo
        binding.tvProfileBankName.text = store.bankName.ifBlank { "Chưa cập nhật" }
        binding.tvProfileBankAccountNumber.text = store.bankAccountNumber.ifBlank { "Chưa cập nhật" }
        binding.tvProfileBankAccountName.text = store.bankAccountName.ifBlank { "Chưa cập nhật" }
        binding.tvProfileMomoPhone.text = store.momoPhone.ifBlank { store.phone.ifBlank { "Chưa cập nhật" } }
        binding.tvProfileMomoAccountName.text = store.momoAccountName.ifBlank { store.bankAccountName.ifBlank { store.ownerName.ifBlank { "Chưa cập nhật" } } }

        if (store.isOpen) {
            binding.tvProfileStatus.text = "🟢 ĐANG HOẠT ĐỘNG (OPEN)"
            binding.tvProfileStatus.setTextColor(Color.parseColor("#10B981"))
        } else {
            binding.tvProfileStatus.text = "⚪ TẠM ĐÓNG CỬA (CLOSED)"
            binding.tvProfileStatus.setTextColor(Color.parseColor("#64748B"))
        }

        if (store.isVerified && store.address.isNotBlank() && store.district.isNotBlank()) {
            binding.tvProfileVerifiedBadge.text = "✓ ĐÃ XÁC MINH HỒ SƠ"
            binding.tvProfileVerifiedBadge.setTextColor(Color.parseColor("#10B981"))
            binding.tvProfileVerifiedBadge.setBackgroundResource(R.drawable.bg_badge_free)
        } else {
            binding.tvProfileVerifiedBadge.text = "⚠️ CHỜ BỔ SUNG ĐỊA CHỈ & XÁC MINH"
            binding.tvProfileVerifiedBadge.setTextColor(Color.parseColor("#D97706"))
            binding.tvProfileVerifiedBadge.setBackgroundColor(Color.parseColor("#FEF3C7"))
        }

        binding.tvProfileVerifiedBadge.setOnClickListener {
            val intent = android.content.Intent(requireContext(), com.chuyen_de_2.foodorder.internal.ui.auth.StoreVerificationActivity::class.java).apply {
                putExtra("STORE_ID", currentStoreId)
            }
            startActivity(intent)
        }
    }

    private fun getDefaultStore(): Store {
        val user = authRepository.currentUser
        return Store(
            id = currentStoreId,
            name = user?.displayName?.ifBlank { "Quán của bạn" } ?: "Quán của bạn",
            phone = user?.phoneNumber ?: "",
            address = "",
            district = "",
            ownerName = user?.displayName ?: "",
            openHours = "08:00 - 22:00",
            prepTime = "15 phút",
            isVerified = false,
            cccdNumber = "",
            storeStatus = Store.StoreStatus.OPEN
        )
    }

    /**
     * Tên chức năng: Hiển thị Dialog chỉnh sửa thông tin quán
     * Cách hoạt động: Cho phép sửa tên, địa chỉ, quận huyện, tên chủ quán, giờ mở cửa, thời gian chuẩn bị món và tài khoản ngân hàng.
     * Luồng hướng tới: Gọi [FirebaseRepository.updateStoreProfile] để lưu Firestore và cập nhật giao diện ngay lập tức.
     */
    private fun showEditStoreDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_edit_store_profile)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val store = currentStore ?: getDefaultStore()
        editingLat = store.latitude.takeIf { it != 0.0 }
        editingLng = store.longitude.takeIf { it != 0.0 }

        pendingAvatarBase64 = null

        val ivAvatarPreview = dialog.findViewById<ImageView>(R.id.ivEditStoreAvatarPreview)
        val tvAvatarPlaceholder = dialog.findViewById<View>(R.id.tvEditStoreAvatarPlaceholder)
        val cardAvatar = dialog.findViewById<View>(R.id.cardEditStoreAvatar)
        val btnPickAvatar = dialog.findViewById<View>(R.id.btnPickStoreAvatar)

        currentDialogAvatarPreview = ivAvatarPreview
        currentDialogAvatarPlaceholder = tvAvatarPlaceholder

        val currentAvatarUrl = store.imageUrl
        if (currentAvatarUrl.isNotBlank() && (currentAvatarUrl.startsWith("data:image") || currentAvatarUrl.startsWith("http"))) {
            ivAvatarPreview?.visibility = View.VISIBLE
            tvAvatarPlaceholder?.visibility = View.GONE
            ivAvatarPreview?.let {
                Glide.with(this)
                    .load(currentAvatarUrl)
                    .transform(CenterCrop(), RoundedCorners(24))
                    .into(it)
            }
        } else {
            ivAvatarPreview?.visibility = View.GONE
            tvAvatarPlaceholder?.visibility = View.VISIBLE
        }

        val openImagePickerAction = {
            pickAvatarLauncher.launch("image/*")
        }
        btnPickAvatar?.setOnClickListener { openImagePickerAction() }
        cardAvatar?.setOnClickListener { openImagePickerAction() }

        val etName = dialog.findViewById<EditText>(R.id.etStoreName)
        val etPhone = dialog.findViewById<EditText>(R.id.etStorePhone)
        val etAddress = dialog.findViewById<EditText>(R.id.etStoreAddress)
        val etDistrict = dialog.findViewById<EditText>(R.id.etStoreDistrict)
        val etOwner = dialog.findViewById<EditText>(R.id.etOwnerName)
        val etOpenHours = dialog.findViewById<EditText>(R.id.etOpenHours)
        val etPrepTime = dialog.findViewById<EditText>(R.id.etPrepTime)

        currentDialogEtAddress = etAddress
        currentDialogEtDistrict = etDistrict

        val openMapAction = {
            val intent = Intent(requireContext(), SelectLocationActivity::class.java).apply {
                val addr = etAddress.text.toString().trim()
                if (addr.isNotBlank()) putExtra(SelectLocationActivity.EXTRA_FULL_ADDRESS, addr)
                val dist = etDistrict.text.toString().trim()
                if (dist.isNotBlank()) putExtra(SelectLocationActivity.EXTRA_DISTRICT, dist)
                editingLat?.let { putExtra(SelectLocationActivity.EXTRA_LAT, it) }
                editingLng?.let { putExtra(SelectLocationActivity.EXTRA_LNG, it) }
            }
            selectLocationLauncher.launch(intent)
        }

        val btnPickLocation = dialog.findViewById<MaterialButton>(R.id.btnPickStoreLocationMap)
        btnPickLocation?.setOnClickListener { openMapAction() }
        etAddress.setOnClickListener { openMapAction() }
        dialog.findViewById<View>(R.id.tilStoreAddress)?.setOnClickListener { openMapAction() }

        // Các view tài khoản ngân hàng & Ví MoMo
        val spBank = dialog.findViewById<android.widget.Spinner>(R.id.spBankName)
        val etBankNo = dialog.findViewById<EditText>(R.id.etBankAccountNumber)
        val etBankOwner = dialog.findViewById<EditText>(R.id.etBankAccountName)
        val etMomoPhone = dialog.findViewById<EditText>(R.id.etMomoPhone)
        val etMomoOwner = dialog.findViewById<EditText>(R.id.etMomoAccountName)

        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseDialog)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSave = dialog.findViewById<MaterialButton>(R.id.btnSaveStoreProfile)

        // Dropdown ngân hàng
        val bankAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            supportedBanks.map { it.name }
        )
        spBank.adapter = bankAdapter

        val currentBankIdx = supportedBanks.indexOfFirst {
            it.name.contains(store.bankName, ignoreCase = true) ||
            store.bankName.contains(it.name.substringBefore(" "), ignoreCase = true)
        }
        if (currentBankIdx != -1) {
            spBank.setSelection(currentBankIdx)
        }

        // Điền dữ liệu hiện tại
        etName.setText(store.name)
        etPhone.setText(store.phone)
        etAddress.setText(store.address)
        etDistrict.setText(store.district)
        etOwner.setText(store.ownerName)
        etOpenHours.setText(store.openHours.ifBlank { "08:00 - 22:30" })
        etPrepTime.setText(store.prepTime.ifBlank { "15 phút" })
        etBankNo.setText(store.bankAccountNumber)
        etBankOwner.setText(store.bankAccountName)
        etMomoPhone.setText(store.momoPhone.ifBlank { store.phone })
        etMomoOwner.setText(store.momoAccountName.ifBlank { store.bankAccountName.ifBlank { store.ownerName } })

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newName = etName.text.toString().trim()
            val newPhone = etPhone.text.toString().trim()
            val newAddress = etAddress.text.toString().trim()
            val newDistrict = etDistrict.text.toString().trim()
            val newOwner = etOwner.text.toString().trim()
            val newOpenHours = etOpenHours.text.toString().trim().ifBlank { "08:00 - 22:30" }
            val newPrepTime = etPrepTime.text.toString().trim().ifBlank { "15 phút" }

            val selectedBank = supportedBanks.getOrNull(spBank.selectedItemPosition) ?: supportedBanks[0]
            val newBankName = selectedBank.name
            val newBankBin = selectedBank.bin
            val newBankNo = etBankNo.text.toString().trim()
            val newBankOwnerName = etBankOwner.text.toString().trim().uppercase()
            val newMomoPhone = etMomoPhone.text.toString().trim().ifBlank { newPhone }
            val newMomoOwnerName = etMomoOwner.text.toString().trim().uppercase().ifBlank { newBankOwnerName.ifBlank { newOwner.uppercase() } }

            if (newName.isBlank()) {
                etName.error = "Vui lòng nhập tên quán"
                return@setOnClickListener
            }
            if (newAddress.isBlank()) {
                etAddress.error = "Vui lòng nhập địa chỉ"
                return@setOnClickListener
            }

            btnSave.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val (resolvedLat, resolvedLng) = if (editingLat != null && editingLng != null && editingLat != 0.0 && editingLng != 0.0) {
                    editingLat!! to editingLng!!
                } else {
                    val fullAddress = if (newAddress.isNotBlank()) "$newAddress, $newDistrict, TP.HCM" else "$newDistrict, TP.HCM"
                    val coords = try {
                        val resolved = com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.geocodeAddress(requireContext(), fullAddress)
                        if (resolved.latitude != 0.0 && resolved.longitude != 0.0) resolved
                        else com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.DISTRICT_COORDINATES[newDistrict] ?: com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.DEFAULT_HCMC_LATLNG
                    } catch (_: Exception) {
                        com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.DISTRICT_COORDINATES[newDistrict] ?: com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.DEFAULT_HCMC_LATLNG
                    }
                    coords.latitude to coords.longitude
                }

                val avatarToSave = pendingAvatarBase64 ?: store.imageUrl

                val result = firebaseRepository.updateStoreProfile(
                    storeId = currentStoreId,
                    name = newName,
                    phone = newPhone,
                    address = newAddress,
                    district = newDistrict,
                    ownerName = newOwner,
                    openHours = newOpenHours,
                    prepTime = newPrepTime,
                    imageUrl = avatarToSave,
                    bankName = newBankName,
                    bankAccountNumber = newBankNo,
                    bankAccountName = newBankOwnerName,
                    bankBin = newBankBin,
                    momoPhone = newMomoPhone,
                    momoAccountName = newMomoOwnerName,
                    latitude = resolvedLat,
                    longitude = resolvedLng
                )

                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "✅ Đã cập nhật thông tin & ảnh quán thành công!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "❌ Lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    btnSave.isEnabled = true
                }
            }
        }

        dialog.setOnDismissListener {
            currentDialogEtAddress = null
            currentDialogEtDistrict = null
            currentDialogAvatarPreview = null
            currentDialogAvatarPlaceholder = null
            pendingAvatarBase64 = null
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
