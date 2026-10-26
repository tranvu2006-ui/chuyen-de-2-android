package com.chuyen_de_2.foodorder.admin.ui.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.DialogAddEditCategoryBinding
import com.chuyen_de_2.foodorder.core.data.model.Category

class AddEditCategoryDialogFragment(
    private val categoryToEdit: Category? = null,
    private val onSave: (Category) -> Unit
) : DialogFragment() {

    private var _binding: DialogAddEditCategoryBinding? = null
    private val binding get() = _binding!!

    private val emojiList = listOf(
        "🍚", "🍜", "🧋", "🍟", "🥖", "🥗", "🦐", "🍰",
        "🍕", "🍗", "☕", "🍲", "🍔", "🥤", "🍣", "🍧",
        "🍳", "🥟", "🌮", "🍢"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddEditCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupEmojiPalette()
        populateExistingData()
        setupListeners()
    }

    private fun setupEmojiPalette() {
        val density = resources.displayMetrics.density
        val sizePx = (44 * density).toInt()
        val marginPx = (4 * density).toInt()

        for (emoji in emojiList) {
            val tv = TextView(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                }
                text = emoji
                textSize = 22f
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.bg_choice_card_idle)
                setOnClickListener {
                    binding.etCatIconUrl.setText(emoji)
                    updateIconPreview(emoji)
                }
            }
            binding.llEmojiPalette.addView(tv)
        }
    }

    private fun populateExistingData() {
        if (categoryToEdit != null) {
            binding.tvDialogTitle.text = "Chỉnh Sửa Danh Mục"
            binding.etCatName.setText(categoryToEdit.categoryName)
            binding.etCatId.setText(categoryToEdit.categoryId)
            binding.etCatDescription.setText(categoryToEdit.description)
            binding.etCatOrder.setText(categoryToEdit.displayOrder.toString())
            binding.cbIsActive.isChecked = categoryToEdit.isActive
            val icon = categoryToEdit.iconUrl.ifBlank { "🍽️" }
            binding.etCatIconUrl.setText(icon)
            updateIconPreview(icon)
        } else {
            binding.tvDialogTitle.text = "Thêm Danh Mục Mới"
            binding.etCatIconUrl.setText("🍚")
            updateIconPreview("🍚")
        }
    }

    private fun updateIconPreview(iconText: String) {
        val trimmed = iconText.trim()
        val isUrl = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("data:image", ignoreCase = true)

        if (isUrl) {
            binding.tvSelectedEmoji.visibility = View.GONE
            binding.ivSelectedIcon.visibility = View.VISIBLE
            Glide.with(this)
                .load(trimmed)
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .into(binding.ivSelectedIcon)
        } else {
            binding.ivSelectedIcon.visibility = View.GONE
            binding.tvSelectedEmoji.visibility = View.VISIBLE
            binding.tvSelectedEmoji.text = trimmed.ifBlank { "🍽️" }
        }
    }

    private fun setupListeners() {
        binding.etCatIconUrl.doAfterTextChanged { s ->
            updateIconPreview(s?.toString().orEmpty())
        }

        binding.btnCancelCat.setOnClickListener {
            dismiss()
        }

        binding.btnSaveCat.setOnClickListener {
            val name = binding.etCatName.text?.toString().orEmpty().trim()
            if (name.isBlank()) {
                Toast.makeText(requireContext(), "Vui lòng nhập tên danh mục!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val order = binding.etCatOrder.text?.toString()?.toIntOrNull() ?: 1
            val isActive = binding.cbIsActive.isChecked
            val icon = binding.etCatIconUrl.text?.toString().orEmpty().trim().ifBlank { "🍽️" }
            val desc = binding.etCatDescription.text?.toString().orEmpty().trim()
            val customId = binding.etCatId.text?.toString().orEmpty().trim()

            val finalId = if (customId.isNotBlank()) customId
            else if (categoryToEdit != null && categoryToEdit.categoryId.isNotBlank()) categoryToEdit.categoryId
            else "cat_" + System.currentTimeMillis()

            val finalCat = Category(
                categoryId = finalId,
                categoryName = name,
                iconUrl = icon,
                displayOrder = order,
                isActive = isActive,
                description = desc
            )

            onSave(finalCat)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddEditCategoryDialogFragment"
    }
}
