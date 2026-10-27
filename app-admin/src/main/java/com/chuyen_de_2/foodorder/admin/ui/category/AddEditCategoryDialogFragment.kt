package com.chuyen_de_2.foodorder.admin.ui.category

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.DialogAddEditCategoryBinding
import com.chuyen_de_2.foodorder.core.data.model.Category
import java.io.ByteArrayOutputStream

class AddEditCategoryDialogFragment(
    private val categoryToEdit: Category? = null,
    private val onSave: (Category) -> Unit
) : DialogFragment() {

    private var _binding: DialogAddEditCategoryBinding? = null
    private val binding get() = _binding!!

    private var uploadedBase64Icon: String = ""

    private val pickIconLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val base64 = convertUriToBase64Icon(uri)
            if (base64 != null) {
                uploadedBase64Icon = base64
                binding.etCatIconUrl.setText("")
                updateIconPreview(base64)
                binding.tvUploadStatus.text = "✓ Đã tải ảnh Icon từ thiết bị"
                binding.btnClearIcon.visibility = View.VISIBLE
            } else {
                Toast.makeText(requireContext(), "Không thể đọc tệp ảnh, vui lòng thử ảnh khác.", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
        populateExistingData()
        setupListeners()
    }

    private fun convertUriToBase64Icon(uri: Uri): String? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (originalBitmap == null) return null

            val maxDim = 256
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = (maxDim.toFloat() / maxOf(width, height)).coerceAtMost(1.0f)
            val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
            val scaledHeight = (height * scale).toInt().coerceAtLeast(1)

            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
            val outputStream = ByteArrayOutputStream()
            // Nén PNG để giữ nguyên nền trong suốt (transparent background) cho icon danh mục
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
            val byteArray = outputStream.toByteArray()
            "data:image/png;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
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

            val existingIcon = categoryToEdit.iconUrl.trim()
            if (existingIcon.startsWith("data:image", ignoreCase = true) || existingIcon.length > 100) {
                uploadedBase64Icon = existingIcon
                binding.etCatIconUrl.setText("")
                binding.tvUploadStatus.text = "✓ Đang dùng ảnh tải lên"
                binding.btnClearIcon.visibility = View.VISIBLE
                updateIconPreview(existingIcon)
            } else if (existingIcon.startsWith("http://", ignoreCase = true) || existingIcon.startsWith("https://", ignoreCase = true)) {
                uploadedBase64Icon = ""
                binding.etCatIconUrl.setText(existingIcon)
                binding.tvUploadStatus.text = "✓ Đang dùng URL ảnh"
                binding.btnClearIcon.visibility = View.VISIBLE
                updateIconPreview(existingIcon)
            } else {
                uploadedBase64Icon = ""
                binding.etCatIconUrl.setText("")
                binding.tvUploadStatus.text = "Chưa có ảnh (Nhấn để chọn PNG/JPG)"
                binding.btnClearIcon.visibility = View.GONE
                updateIconPreview("")
            }
        } else {
            binding.tvDialogTitle.text = "Thêm Danh Mục Mới"
            uploadedBase64Icon = ""
            binding.etCatIconUrl.setText("")
            updateIconPreview("")
        }
    }

    private fun updateIconPreview(iconSource: String) {
        val trimmed = iconSource.trim()
        if (trimmed.isBlank()) {
            binding.ivSelectedIcon.setPadding(24, 24, 24, 24)
            binding.ivSelectedIcon.setImageResource(R.drawable.ic_image)
            return
        }

        binding.ivSelectedIcon.setPadding(12, 12, 12, 12)
        val cachedBmp = com.chuyen_de_2.foodorder.core.util.CategoryIconCache.getOrDecode(trimmed)
        if (cachedBmp != null) {
            Glide.with(this).clear(binding.ivSelectedIcon)
            binding.ivSelectedIcon.setImageBitmap(cachedBmp)
            return
        }

        if (trimmed.startsWith("data:image", ignoreCase = true) || trimmed.length > 100) {
            try {
                val clean = if (trimmed.contains(",")) trimmed.substringAfter(",") else trimmed
                val decoded = Base64.decode(clean, Base64.DEFAULT)
                Glide.with(this)
                    .asBitmap()
                    .load(decoded)
                    .dontAnimate()
                    .error(R.drawable.ic_image)
                    .into(binding.ivSelectedIcon)
                return
            } catch (_: Exception) {}
        }

        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            Glide.with(this)
                .load(trimmed)
                .dontAnimate()
                .error(R.drawable.ic_image)
                .into(binding.ivSelectedIcon)
        } else {
            binding.ivSelectedIcon.setImageResource(R.drawable.ic_image)
        }
    }

    private fun setupListeners() {
        binding.btnUploadIcon.setOnClickListener {
            pickIconLauncher.launch("image/*")
        }

        binding.cardIconPreview.setOnClickListener {
            pickIconLauncher.launch("image/*")
        }

        binding.btnClearIcon.setOnClickListener {
            uploadedBase64Icon = ""
            binding.etCatIconUrl.setText("")
            binding.tvUploadStatus.text = "Đã xóa ảnh Icon"
            binding.btnClearIcon.visibility = View.GONE
            updateIconPreview("")
        }

        binding.etCatIconUrl.doAfterTextChanged { s ->
            val urlText = s?.toString().orEmpty().trim()
            if (urlText.isNotBlank()) {
                uploadedBase64Icon = ""
                binding.tvUploadStatus.text = "✓ Đang dùng URL ảnh"
                binding.btnClearIcon.visibility = View.VISIBLE
                updateIconPreview(urlText)
            } else if (uploadedBase64Icon.isBlank()) {
                binding.tvUploadStatus.text = "Chưa có ảnh (Nhấn để chọn PNG/JPG)"
                binding.btnClearIcon.visibility = View.GONE
                updateIconPreview("")
            }
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
            val typedUrl = binding.etCatIconUrl.text?.toString().orEmpty().trim()
            val finalIconUrl = if (typedUrl.isNotBlank()) typedUrl else uploadedBase64Icon
            val desc = binding.etCatDescription.text?.toString().orEmpty().trim()
            val customId = binding.etCatId.text?.toString().orEmpty().trim()

            val finalId = if (customId.isNotBlank()) customId
            else if (categoryToEdit != null && categoryToEdit.categoryId.isNotBlank()) categoryToEdit.categoryId
            else "cat_" + System.currentTimeMillis()

            val finalCat = Category(
                categoryId = finalId,
                categoryName = name,
                iconUrl = finalIconUrl,
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
