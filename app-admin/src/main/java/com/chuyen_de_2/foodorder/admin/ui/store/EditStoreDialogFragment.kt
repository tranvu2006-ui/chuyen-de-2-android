package com.chuyen_de_2.foodorder.admin.ui.store

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.DialogEditStoreBinding
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EditStoreDialogFragment(
    private val store: Store,
    private val onSave: (Store) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogEditStoreBinding? = null
    private val binding get() = _binding!!

    private val statusOptions = listOf(
        Store.StoreStatus.OPEN,
        Store.StoreStatus.CLOSED,
        Store.StoreStatus.LOCKED,
        Store.StoreStatus.PENDING
    )

    private val penaltyOptions = listOf(
        Store.PenaltyTier.NONE,
        Store.PenaltyTier.WARN,
        Store.PenaltyTier.HIDE,
        Store.PenaltyTier.LOCK
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
        _binding = DialogEditStoreBinding.inflate(inflater, container, false)
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

        val statusAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("🟢 Đang Mở Cửa (OPEN)", "⚪ Tạm Nghỉ (CLOSED)", "⛔ Đang Bị Khóa (LOCKED)", "🟡 Chờ Duyệt (PENDING)")
        )
        binding.spnStoreStatus.adapter = statusAdapter

        val penaltyAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("✅ Bình Thường (NONE)", "⚠️ Cảnh Cáo (WARN)", "👁️ Ẩn Khỏi Tìm Kiếm (HIDE)", "🔒 Khóa Quyền Bán (LOCK)")
        )
        binding.spnPenaltyTier.adapter = penaltyAdapter
    }

    private fun populateCurrentData() {
        binding.tvStoreIdSubtitle.text = "Mã cửa hàng: ${store.id}"
        binding.etStoreName.setText(store.name)
        binding.etStoreCategory.setText(store.category)
        binding.etStorePhone.setText(store.phone)
        binding.etStoreAddress.setText(store.address)
        binding.etStoreDistrict.setText(store.district)
        binding.etStoreImageUrl.setText(store.imageUrl)
        binding.etStoreOpenHours.setText(store.openHours)
        binding.etStorePrepTime.setText(store.prepTime)
        binding.etStoreDeliveryTime.setText(store.deliveryTime)
        binding.etStoreRatingAvg.setText(store.ratingAvg.toString())

        // Trạng thái
        val statusIndex = when (store.storeStatus.uppercase()) {
            Store.StoreStatus.OPEN -> 0
            Store.StoreStatus.CLOSED -> 1
            Store.StoreStatus.LOCKED -> 2
            Store.StoreStatus.PENDING -> 3
            else -> 0
        }
        binding.spnStoreStatus.setSelection(statusIndex)

        binding.switchStoreVerified.isChecked = store.isVerified

        // Chế tài
        val penaltyIndex = when (store.penaltyTier.uppercase()) {
            Store.PenaltyTier.NONE -> 0
            Store.PenaltyTier.WARN -> 1
            Store.PenaltyTier.HIDE -> 2
            Store.PenaltyTier.LOCK -> 3
            else -> 0
        }
        binding.spnPenaltyTier.setSelection(penaltyIndex)

        binding.etPenaltyReason.setText(store.penaltyReason)
        binding.etOwnerName.setText(store.ownerName)
        binding.etCccdNumber.setText(store.cccdNumber)
        binding.etBankName.setText(store.bankName)
        binding.etBankAccountNumber.setText(store.bankAccountNumber)
        binding.etBankAccountName.setText(store.bankAccountName)
    }

    private fun setupListeners() {
        binding.btnCloseDialog.setOnClickListener { dismiss() }
        binding.btnCancelEditStore.setOnClickListener { dismiss() }

        binding.btnSaveEditStore.setOnClickListener {
            val name = binding.etStoreName.text?.toString().orEmpty().trim()
            if (name.isBlank()) {
                Toast.makeText(requireContext(), "Vui lòng nhập tên quán", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val category = binding.etStoreCategory.text?.toString().orEmpty().trim()
            val phone = binding.etStorePhone.text?.toString().orEmpty().trim()
            val address = binding.etStoreAddress.text?.toString().orEmpty().trim()
            val district = binding.etStoreDistrict.text?.toString().orEmpty().trim()
            val imageUrl = binding.etStoreImageUrl.text?.toString().orEmpty().trim()
            val openHours = binding.etStoreOpenHours.text?.toString().orEmpty().trim().ifBlank { "08:00 - 22:00" }
            val prepTime = binding.etStorePrepTime.text?.toString().orEmpty().trim().ifBlank { "15-20 phút" }
            val deliveryTime = binding.etStoreDeliveryTime.text?.toString().orEmpty().trim()
            val ratingAvg = binding.etStoreRatingAvg.text?.toString().orEmpty().toFloatOrNull() ?: store.ratingAvg

            val selectedStatus = statusOptions.getOrElse(binding.spnStoreStatus.selectedItemPosition) { Store.StoreStatus.OPEN }
            val isVerified = binding.switchStoreVerified.isChecked

            val selectedPenalty = penaltyOptions.getOrElse(binding.spnPenaltyTier.selectedItemPosition) { Store.PenaltyTier.NONE }
            val penaltyReason = binding.etPenaltyReason.text?.toString().orEmpty().trim()

            val ownerName = binding.etOwnerName.text?.toString().orEmpty().trim()
            val cccdNumber = binding.etCccdNumber.text?.toString().orEmpty().trim()
            val bankName = binding.etBankName.text?.toString().orEmpty().trim()
            val bankAccountNumber = binding.etBankAccountNumber.text?.toString().orEmpty().trim()
            val bankAccountName = binding.etBankAccountName.text?.toString().orEmpty().trim()

            val updatedStore = store.copy(
                name = name,
                category = category,
                phone = phone,
                address = address,
                district = district,
                imageUrl = imageUrl,
                openHours = openHours,
                prepTime = prepTime,
                deliveryTime = deliveryTime,
                ratingAvg = ratingAvg,
                storeStatus = selectedStatus,
                isVerified = isVerified,
                penaltyTier = selectedPenalty,
                penaltyReason = penaltyReason,
                ownerName = ownerName,
                cccdNumber = cccdNumber,
                bankName = bankName,
                bankAccountNumber = bankAccountNumber,
                bankAccountName = bankAccountName
            )

            onSave(updatedStore)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditStoreDialogFragment"
    }
}
