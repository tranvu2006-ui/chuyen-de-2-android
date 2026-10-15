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
                    auth.signOut()
                    val intent = android.content.Intent(requireContext(), com.chuyen_de_2.foodorder.internal.ui.auth.LoginActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    requireActivity().finish()
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

    private fun bindStoreData(store: Store) {
        binding.tvProfileStoreName.text = store.name.ifBlank { "Hồng Trà Ngô Gia" }
        binding.tvProfileStoreAddress.text = "📍 " + store.address.ifBlank { "28 Tô Vĩnh Diện, P. Linh Chiểu, TP. Thủ Đức" }
        binding.tvProfileOwnerName.text = store.ownerName.ifBlank { "Ngô Quốc Huy" }
        binding.tvProfileCccd.text = store.cccdNumber.ifBlank { "079088012456" }
        binding.tvProfilePhone.text = store.phone.ifBlank { "0908.889.999" }
        binding.tvProfileOpenHours.text = store.openHours.ifBlank { "08:00 - 22:30" }
        binding.tvProfilePrepTime.text = store.prepTime.ifBlank { "15 phút" }

        if (store.isOpen) {
            binding.tvProfileStatus.text = "🟢 ĐANG HOẠT ĐỘNG (OPEN)"
            binding.tvProfileStatus.setTextColor(Color.parseColor("#10B981"))
        } else {
            binding.tvProfileStatus.text = "⚪ TẠM ĐÓNG CỬA (CLOSED)"
            binding.tvProfileStatus.setTextColor(Color.parseColor("#64748B"))
        }
    }

    private fun getDefaultStore(): Store {
        return Store(
            id = currentStoreId,
            name = "Hồng Trà Ngô Gia",
            phone = "0908889999",
            address = "28 Tô Vĩnh Diện, P. Linh Chiểu, TP. Thủ Đức",
            district = "TP. Thủ Đức",
            ownerName = "Ngô Quốc Huy",
            openHours = "08:00 - 22:30",
            prepTime = "15 phút",
            isVerified = true,
            cccdNumber = "079088012456",
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
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseDialog)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSave = dialog.findViewById<MaterialButton>(R.id.btnSaveStoreProfile)

        // Điền dữ liệu hiện tại
        etName.setText(store.name)
        etPhone.setText(store.phone)
        etAddress.setText(store.address)
        etDistrict.setText(store.district.ifBlank { "TP. Thủ Đức" })
        etOwner.setText(store.ownerName.ifBlank { "Ngô Quốc Huy" })
        etOpenHours.setText(store.openHours.ifBlank { "08:00 - 22:30" })
        etPrepTime.setText(store.prepTime.ifBlank { "15 phút" })

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
                    prepTime = newPrepTime
                )

                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "Đã cập nhật thông tin quán thành công!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
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
