package com.chuyen_de_2.foodorder.admin.ui.store

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.BottomSheetAdminStoreDetailBinding
import com.chuyen_de_2.foodorder.admin.databinding.DialogApplyStorePenaltyBinding
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AdminStoreDetailBottomSheetDialogFragment(
    private val store: Store,
    private val onApprove: (Store) -> Unit,
    private val onLock: (Store) -> Unit,
    private val onApplyPenalty: (storeId: String, tier: String, reason: String) -> Unit,
    private val onEditAll: ((Store) -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAdminStoreDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAdminStoreDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateData()
        setupActions()
    }

    private fun populateData() {
        val context = requireContext()
        binding.tvDetailStoreName.text = store.name.ifBlank { "Quán ăn #${store.id.takeLast(6)}" }
        binding.tvDetailStoreId.text = "Mã quán: ${store.id}"
        binding.tvDetailAddress.text = "Địa chỉ: ${store.address.ifBlank { "Chưa cập nhật" }} (${store.district.ifBlank { "Quận chưa rõ" }})"
        binding.tvDetailPhone.text = "Số điện thoại: ${store.phone.ifBlank { "Chưa cập nhật" }}"
        binding.tvDetailHours.text = "Giờ mở cửa: ${store.openHours.ifBlank { "08:00 - 22:00" }} (Thời gian làm món: ${store.prepTime.ifBlank { "15 phút" }})"

        // Bank
        val bankInfo = StringBuilder()
        if (store.bankName.isNotBlank()) bankInfo.append(store.bankName).append(" • ")
        if (store.bankAccountNumber.isNotBlank()) bankInfo.append(store.bankAccountNumber).append(" • ")
        bankInfo.append(store.bankAccountName.ifBlank { store.ownerName.ifBlank { "Chưa đăng ký" } })
        binding.tvDetailBank.text = bankInfo.toString()

        // CCCD
        binding.tvDetailCccdInfo.text = "Họ tên chủ: ${store.ownerName.ifBlank { "Chưa cập nhật" }} • Số CCCD: ${store.cccdNumber.ifBlank { "Chưa cập nhật" }}"

        // Status badge
        if (!store.isVerified || store.storeStatus.equals(Store.StoreStatus.PENDING, ignoreCase = true)) {
            binding.tvDetailStatusBadge.text = "CHỜ DUYỆT CCCD"
            binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_orange)
            binding.tvDetailStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
        } else if (store.storeStatus.equals(Store.StoreStatus.LOCKED, ignoreCase = true)) {
            binding.tvDetailStatusBadge.text = "BỊ KHÓA"
            binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
            binding.tvDetailStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.danger))
        } else if (store.storeStatus.equals(Store.StoreStatus.OPEN, ignoreCase = true)) {
            binding.tvDetailStatusBadge.text = "ĐANG MỞ CỬA"
            binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
            binding.tvDetailStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.brand_green))
        } else {
            binding.tvDetailStatusBadge.text = "TẠM ĐÓNG CỬA"
            binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_duty_off)
            binding.tvDetailStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }

        // CCCD Photos
        val cccdFront = store.cccdFrontUrl.ifBlank { store.cccdHoldUrl }
        if (cccdFront.isNotBlank()) {
            Glide.with(this).load(cccdFront).placeholder(R.drawable.ic_image).into(binding.ivCccdFront)
        }
        if (store.cccdBackUrl.isNotBlank()) {
            Glide.with(this).load(store.cccdBackUrl).placeholder(R.drawable.ic_image).into(binding.ivCccdBack)
        }

        // Current Penalty
        if (store.penaltyTier != Store.PenaltyTier.NONE && store.penaltyTier.isNotBlank()) {
            binding.tvCurrentPenalty.visibility = View.VISIBLE
            binding.tvCurrentPenalty.text = "⚠️ Chế tài đang hiệu lực: ${store.penaltyTier}\nLý do: ${store.penaltyReason.ifBlank { "Vi phạm quy định sàn" }}"
        } else {
            binding.tvCurrentPenalty.visibility = View.GONE
        }
    }

    private fun setupActions() {
        binding.btnEditAllStoreDetails.setOnClickListener {
            dismiss()
            onEditAll?.invoke(store)
        }

        binding.btnApproveStore.setOnClickListener {
            onApprove(store)
            dismiss()
        }

        binding.btnLockStore.setOnClickListener {
            onLock(store)
            dismiss()
        }

        binding.btnApplyPenalty.setOnClickListener {
            showPenaltyDialog()
        }
    }

    private fun showPenaltyDialog() {
        val dialogBinding = DialogApplyStorePenaltyBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelPenalty.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmPenalty.setOnClickListener {
            val selectedTier = when (dialogBinding.rgPenaltyTiers.checkedRadioButtonId) {
                R.id.rbWarn -> Store.PenaltyTier.WARN
                R.id.rbHide -> Store.PenaltyTier.HIDE
                R.id.rbLock -> Store.PenaltyTier.LOCK
                else -> Store.PenaltyTier.WARN
            }
            val reason = dialogBinding.etPenaltyReason.text?.toString().orEmpty().ifBlank {
                "Quản trị viên xử lý chế tài kiểm duyệt"
            }

            onApplyPenalty(store.id, selectedTier, reason)
            dialog.dismiss()
            dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AdminStoreDetailBottomSheet"
    }
}
