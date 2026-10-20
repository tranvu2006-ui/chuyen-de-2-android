package com.chuyen_de_2.foodorder.internal.ui.shop

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.FragmentStoreProfileBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * StoreProfileFragment — Hồ Sơ & Cài Đặt Quán Ăn
 * Thiết kế và cấu trúc chuẩn theo Quán/8.html:
 * - Thông tin nhận diện quán (Tên, địa chỉ, badge đã xác minh)
 * - Thông tin pháp lý & Chủ quán (Chủ hộ, CCCD, SĐT, trạng thái)
 * - Cấu hình vận hành (Giờ mở cửa, thời gian chuẩn bị món)
 * - Chức năng Chỉnh Sửa Thông Tin Quán lưu trực tiếp lên Firebase Realtime Database
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

    private var currentStoreId = "shop_01"
    private var currentStore: Store? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoreProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        resolveCurrentStoreIdAndObserve()
    }

    private fun resolveCurrentStoreIdAndObserve() {
        val uid = auth.currentUser?.uid.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            currentStoreId = firebaseRepository.resolveStoreIdForUser(uid)
            observeStoreProfile()
        }
    }

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

    private fun observeStoreProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
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

    private fun bindStoreData(store: Store) {
        binding.tvProfileStoreName.text = store.name.ifBlank { "Quán của bạn" }
        binding.tvProfileStoreAddress.text = if (store.address.isNotBlank()) "📍 ${store.address}" else "📍 Chưa cập nhật địa chỉ"
        binding.tvProfileOwnerName.text = store.ownerName.ifBlank { "Chưa cập nhật" }
        binding.tvProfileCccd.text = store.cccdNumber.ifBlank { "Chưa cập nhật" }
        binding.tvProfilePhone.text = store.phone.ifBlank { "Chưa cập nhật" }
        binding.tvProfileOpenHours.text = store.openHours.ifBlank { "08:00 - 22:00" }
        binding.tvProfilePrepTime.text = store.prepTime.ifBlank { "15 phút" }

        // Tài khoản ngân hàng
        binding.tvProfileBankName.text = store.bankName.ifBlank { "Chưa cập nhật" }
        binding.tvProfileBankAccountNumber.text = store.bankAccountNumber.ifBlank { "Chưa cập nhật" }
        binding.tvProfileBankAccountName.text = store.bankAccountName.ifBlank { "Chưa cập nhật" }

        if (store.isOpen) {
            binding.tvProfileStatus.text = "🟢 ĐANG HOẠT ĐỘNG (OPEN)"
            binding.tvProfileStatus.setTextColor(Color.parseColor("#10B981"))
        } else {
            binding.tvProfileStatus.text = "⚪ TẠM ĐÓNG CỬA (CLOSED)"
            binding.tvProfileStatus.setTextColor(Color.parseColor("#64748B"))
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

        val etName = dialog.findViewById<EditText>(R.id.etStoreName)
        val etPhone = dialog.findViewById<EditText>(R.id.etStorePhone)
        val etAddress = dialog.findViewById<EditText>(R.id.etStoreAddress)
        val etDistrict = dialog.findViewById<EditText>(R.id.etStoreDistrict)
        val etOwner = dialog.findViewById<EditText>(R.id.etOwnerName)
        val etOpenHours = dialog.findViewById<EditText>(R.id.etOpenHours)
        val etPrepTime = dialog.findViewById<EditText>(R.id.etPrepTime)

        // Các view tài khoản ngân hàng
        val spBank = dialog.findViewById<android.widget.Spinner>(R.id.spBankName)
        val etBankNo = dialog.findViewById<EditText>(R.id.etBankAccountNumber)
        val etBankOwner = dialog.findViewById<EditText>(R.id.etBankAccountName)

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
        etDistrict.setText(store.district.ifBlank { "TP. Thủ Đức" })
        etOwner.setText(store.ownerName.ifBlank { "Ngô Quốc Huy" })
        etOpenHours.setText(store.openHours.ifBlank { "08:00 - 22:30" })
        etPrepTime.setText(store.prepTime.ifBlank { "15 phút" })
        etBankNo.setText(store.bankAccountNumber)
        etBankOwner.setText(store.bankAccountName)

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
                val result = firebaseRepository.updateStoreProfile(
                    storeId = currentStoreId,
                    name = newName,
                    phone = newPhone,
                    address = newAddress,
                    district = newDistrict,
                    ownerName = newOwner,
                    openHours = newOpenHours,
                    prepTime = newPrepTime,
                    bankName = newBankName,
                    bankAccountNumber = newBankNo,
                    bankAccountName = newBankOwnerName,
                    bankBin = newBankBin
                )

                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "✅ Đã cập nhật thông tin quán & tài khoản ngân hàng!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "❌ Lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    btnSave.isEnabled = true
                }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
