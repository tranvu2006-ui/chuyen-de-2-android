package com.chuyen_de_2.foodorder.admin.ui.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.DialogAddEditCategoryBinding
import com.chuyen_de_2.foodorder.core.data.model.Category

class AddEditCategoryDialogFragment(
    private val categoryToEdit: Category? = null,
    private val onSave: (Category) -> Unit
) : DialogFragment() {

    private var _binding: DialogAddEditCategoryBinding? = null
    private val binding get() = _binding!!

    private var currentEmoji: String = "🍚"
    private val emojiList = listOf(
        "🍚", "🍜", "🧋", "🍟", "🥖", "🥗", "🦐", "🍰",
        "🍕", "🍔", "🥤", "🍣", "🍗", "☕", "🍧", "🍲",
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
        setupActions()
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
                    currentEmoji = emoji
                    binding.tvSelectedEmoji.text = emoji
                }
            }
            binding.llEmojiPalette.addView(tv)
        }
    }

    private fun populateExistingData() {
        if (categoryToEdit != null) {
            binding.tvDialogTitle.text = "Chỉnh Sửa Danh Mục"
            binding.etCatName.setText(categoryToEdit.categoryName)
            binding.etCatOrder.setText(categoryToEdit.displayOrder.toString())
            binding.cbIsActive.isChecked = categoryToEdit.isActive
            currentEmoji = categoryToEdit.iconUrl.ifBlank { "🍽️" }
            binding.tvSelectedEmoji.text = currentEmoji
        } else {
            binding.tvDialogTitle.text = "Thêm Danh Mục Mới"
            currentEmoji = "🍚"
            binding.tvSelectedEmoji.text = currentEmoji
        }
    }

    private fun setupActions() {
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

            val finalCat = Category(
                categoryId = categoryToEdit?.categoryId ?: "",
                categoryName = name,
                iconUrl = currentEmoji,
                displayOrder = order,
                isActive = isActive
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
