package com.chuyen_de_2.foodorder.internal.ui.shop

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Dish
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.FragmentMenuManageBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Tên chức năng: Quản Lý Thực Đơn & Điểm Danh Mở Ca Quán (Menu Management)
 * Cách hoạt động:
 *  - Cung cấp tính năng Check-in mở ca bán hàng / tạm nghỉ ngơi cho Quán.
 *  - Lắng nghe danh sách món ăn từ sub-collection Firestore `stores/{storeId}/dishes`.
 *  - Cho phép thêm món ăn mới hoặc chỉnh sửa món hiện có (tên, giá, phân loại, mô tả, ảnh từ thư viện hoặc URL).
 *  - Hỗ trợ xóa món ăn khỏi thực đơn và cập nhật Realtime sang app Khách hàng.
 * Luồng hướng tới:
 *  - Dữ liệu món ăn đồng bộ trực tiếp tới Menu của app Customer.
 *  - Bấm banner đơn hàng mới -> Chuyển hướng sang tab Quản lý đơn hàng ([OrderListFragment]).
 */
@AndroidEntryPoint
class MenuManageFragment : Fragment() {

    private var _binding: FragmentMenuManageBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var auth: FirebaseAuth

    private lateinit var adapter: ManageDishAdapter
    private val dishList = mutableListOf<Dish>()
    private var isCheckedIn = true
    private var currentStoreId = ""
    private var storeCategories: MutableList<String> = mutableListOf("Món chính")
    private var selectedStoreCategory: String = "ALL"
    private var platformCategories: MutableList<String> = mutableListOf(
        "Cơm", "Bún - Phở", "Trà sữa & Nước", "Gà rán", "Bánh mì", "Pizza", "Cà phê", "Đồ ăn vặt", "Món chay", "Hải sản", "Tráng miệng", "Lẩu / Món Nước", "Món Khác"
    )
    private var platformCategoryObjects: List<com.chuyen_de_2.foodorder.core.data.model.Category> = emptyList()

    var onNavigateToOrdersListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Tên chức năng: Khởi tạo View quản lý thực đơn
     * Cách hoạt động: Gắn sự kiện các nút chức năng, khởi tạo RecyclerView và truy vấn storeId của tài khoản.
     * Luồng hướng tới: Chuẩn bị hiển thị danh sách món ăn của quán.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupRecyclerView()
        resolveCurrentStoreIdAndObserve()
        observePlatformCategories()
    }

    /**
     * Tên chức năng: Định danh Cửa hàng và giám sát món ăn
     * Cách hoạt động: Lấy UID người dùng hiện tại để resolve ra storeId tương ứng, sau đó lắng nghe Firestore snapshot.
     * Luồng hướng tới: Gọi [observeStoreDishes].
     */
    private fun resolveCurrentStoreIdAndObserve() {
        val uid = auth.currentUser?.uid.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            currentStoreId = firebaseRepository.resolveStoreIdForUser(uid)
            observeStoreDishes()
            observeStoreCategories()
        }
    }

    /**
     * Tên chức năng: Lắng nghe danh mục nội bộ của quán theo thời gian thực
     */
    private fun observeStoreCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (currentStoreId.isBlank()) return@launch
            firebaseRepository.observeStoreCategories(currentStoreId).collectLatest { cats ->
                if (cats.isNotEmpty()) {
                    storeCategories.clear()
                    storeCategories.addAll(cats)
                    setupCategoryFilterChips()
                    updateList()
                }
            }
        }
    }

    /**
     * Tên chức năng: Lắng nghe danh sách món ăn theo thời gian thực (Realtime Menu Observer)
     * Cách hoạt động: Lắng nghe flow từ `firebaseRepository.observeStoreDishes`. Nếu quán mới chưa có món, tự động sinh bộ món mẫu.
     * Luồng hướng tới: Cập nhật RecyclerView hiển thị danh sách món.
     */
    private fun observePlatformCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            firebaseRepository.observeCategories(includeInactive = false).collectLatest { cats ->
                if (cats.isNotEmpty()) {
                    platformCategoryObjects = cats
                    val names = cats.map { it.categoryName }.toMutableList()
                    if (!names.contains("Món Khác")) names.add("Món Khác")
                    platformCategories = names
                }
            }
        }
    }

    private fun observeStoreDishes() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (currentStoreId.isBlank()) return@launch
            firebaseRepository.observeStoreDishes(currentStoreId).collectLatest { dishes ->
                dishList.clear()
                dishList.addAll(dishes)
                updateList()
                setupCategoryFilterChips()
            }
        }
    }

    /**
     * Tên chức năng: Thiết lập giao diện điều khiển và các nút thao tác menu
     * Cách hoạt động: Bắt sự kiện Check-in mở ca, chuyển nhanh sang đơn hàng, thêm món ăn và tạo combo.
     * Luồng hướng tới: Tương tác người dùng với thực đơn.
     */
    private fun setupUI() {
        // 1. Nút Check-in mở ca
        binding.btnCheckin.setOnClickListener {
            toggleCheckin()
        }

        // 2. Banner nhận đơn mới -> chuyển sang tab Đơn hàng
        val goToOrdersAction = View.OnClickListener {
            onNavigateToOrdersListener?.invoke()
        }
        binding.bannerNewOrderIncoming.setOnClickListener(goToOrdersAction)
        binding.btnBannerGoToOrders.setOnClickListener(goToOrdersAction)

        // 3. Nút Quản lý danh mục quán
        binding.btnManageCategories.setOnClickListener {
            showManageCategoriesDialog()
        }

        // 4. Nút Tạo Combo & Thêm Món
        binding.btnCreateCombo.setOnClickListener {
            showCreateComboDialog()
        }

        binding.btnAddFood.setOnClickListener {
            showDishDialog(null)
        }

        updateDishCountText()
    }

    private fun createCategoryChip(label: String, isSelected: Boolean, onClick: () -> Unit): Chip {
        val chip = layoutInflater.inflate(R.layout.item_store_category_chip, binding.chipGroupStoreCategories, false) as Chip
        chip.text = label
        chip.isChecked = isSelected
        chip.setOnClickListener { onClick() }
        return chip
    }

    /**
     * Tên chức năng: Thiết lập các chip lọc danh mục quán
     */
    private fun setupCategoryFilterChips() {
        try {
            binding.chipGroupStoreCategories.removeAllViews()

            val allChip = createCategoryChip("Tất cả (${dishList.size})", selectedStoreCategory == "ALL") {
                selectedStoreCategory = "ALL"
                updateList()
            }
            binding.chipGroupStoreCategories.addView(allChip)

            storeCategories.forEach { catName ->
                val count = dishList.count { it.category.equals(catName, ignoreCase = true) }
                val label = if (count > 0) "$catName ($count)" else catName
                val chip = createCategoryChip(label, selectedStoreCategory.equals(catName, ignoreCase = true)) {
                    selectedStoreCategory = catName
                    updateList()
                }
                binding.chipGroupStoreCategories.addView(chip)
            }
        } catch (e: Exception) {
            android.util.Log.e("MenuManageFragment", "Error setting up category chips", e)
        }
    }

    /**
     * Tên chức năng: Chuyển đổi trạng thái Điểm danh / Mở ca (Check-in Toggle)
     * Cách hoạt động: Bật/tắt trạng thái ca làm việc của nhân viên quán, đổi giao diện nút bấm và thông báo.
     * Luồng hướng tới: Cập nhật visual feedback cho quản lý quán.
     */
    private fun toggleCheckin() {
        isCheckedIn = !isCheckedIn
        if (isCheckedIn) {
            binding.btnCheckin.setBackgroundResource(R.drawable.bg_btn_checkin_on)
            binding.dotCheckinStatus.setBackgroundResource(R.drawable.bg_badge_green)
            binding.tvCheckinText.text = "ĐÃ CHECK-IN (MỞ CỬA)"
            binding.tvCheckinText.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_green))
            Toast.makeText(
                requireContext(),
                "🟢 Quán đã Check-in vào ca thành công! Khách hàng có thể đặt món trên Oishi Food.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            binding.btnCheckin.setBackgroundResource(R.drawable.bg_btn_checkin_off)
            binding.dotCheckinStatus.setBackgroundResource(R.drawable.bg_order_tab_idle)
            binding.tvCheckinText.text = "TẠM NGHỈ (ĐÓNG CỬA)"
            binding.tvCheckinText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            Toast.makeText(
                requireContext(),
                "⚪ Quán đã chuyển sang trạng thái tạm nghỉ. Khách hàng tạm thời không thể đặt món.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Tên chức năng: Thiết lập danh sách RecyclerView món ăn
     * Cách hoạt động: Khởi tạo [ManageDishAdapter] với các callback Chỉnh sửa món và Xóa món.
     * Luồng hướng tới: Render các thẻ món ăn [item_manage_dish.xml].
     */
    private fun setupRecyclerView() {
        adapter = ManageDishAdapter(
            onEditClick = { dish -> showDishDialog(dish) },
            onDeleteClick = { dish -> confirmDeleteDish(dish) }
        )

        binding.rvDishes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MenuManageFragment.adapter
        }

        updateList()
    }

    /**
     * Tên chức năng: Làm mới danh sách hiển thị món ăn có lọc theo danh mục
     */
    private fun updateList() {
        val filtered = if (selectedStoreCategory == "ALL" || selectedStoreCategory.isBlank()) {
            dishList
        } else {
            dishList.filter { it.category.equals(selectedStoreCategory, ignoreCase = true) }
        }
        adapter.submitList(filtered.toList())
        binding.layoutEmptyDishes.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvDishes.visibility = if (filtered.isNotEmpty()) View.VISIBLE else View.GONE
        updateDishCountText()
    }

    private fun updateDishCountText() {
        val total = dishList.size
        val filtered = if (selectedStoreCategory == "ALL" || selectedStoreCategory.isBlank()) total else dishList.count { it.category.equals(selectedStoreCategory, ignoreCase = true) }
        binding.tvDishTotalCount.text = if (selectedStoreCategory == "ALL" || selectedStoreCategory.isBlank()) {
            "$total món đang mở bán"
        } else {
            "$filtered/$total món • $selectedStoreCategory"
        }
    }

    private var onImagePickedCallback: ((String) -> Unit)? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = convertUriToBase64(uri)
            if (base64 != null) {
                onImagePickedCallback?.invoke(base64)
            } else {
                Toast.makeText(requireContext(), "Không thể tải ảnh, vui lòng thử lại!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Tên chức năng: Nén và chuyển đổi ảnh Uri sang Base64
     * Cách hoạt động: Đọc Stream từ Uri, scale kích thước về tối đa 450px và nén JPEG 80% để lưu trữ trực tiếp vào Firestore dạng chuỗi base64 nhẹ.
     * Luồng hướng tới: Cung cấp dữ liệu ảnh lưu trữ cho Dish.
     */
    private fun convertUriToBase64(uri: Uri): String? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            val maxDim = 450
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = (maxDim.toFloat() / maxOf(width, height)).coerceAtMost(1.0f)
            val scaledWidth = (width * scale).toInt()
            val scaledHeight = (height * scale).toInt()

            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()
            "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Tên chức năng: Tải và hiển thị ảnh xem trước món ăn
     * Cách hoạt động: Dùng Glide tải ảnh từ Base64 hoặc URL trực tuyến với cơ chế fallback icon mặc định.
     * Luồng hướng tới: Cập nhật ImageView trong modal chỉnh sửa món.
     */
    private fun loadPreviewImage(targetView: ImageView, urlOrBase64: String) {
        if (urlOrBase64.isBlank()) {
            targetView.setImageResource(R.drawable.bg_badge_orange)
            return
        }
        if (urlOrBase64.startsWith("data:image") || urlOrBase64.length > 500) {
            try {
                val clean = if (urlOrBase64.contains(",")) urlOrBase64.substringAfter(",") else urlOrBase64
                val decoded = Base64.decode(clean, Base64.DEFAULT)
                Glide.with(this)
                    .asBitmap()
                    .load(decoded)
                    .placeholder(R.drawable.bg_badge_orange)
                    .error(R.drawable.bg_badge_orange)
                    .centerCrop()
                    .into(targetView)
                return
            } catch (_: Exception) {}
        }
        Glide.with(this)
            .load(urlOrBase64)
            .placeholder(R.drawable.bg_badge_orange)
            .error(R.drawable.bg_badge_orange)
            .centerCrop()
            .into(targetView)
    }

    /**
     * Tên chức năng: Hiển thị Dialog Thêm mới / Chỉnh sửa Món ăn
     * Cách hoạt động:
     *  - Nếu editingDish != null: Nạp thông tin hiện có để sửa.
     *  - Nếu editingDish == null: Mở form trống để tạo món mới.
     *  - Hỗ trợ chọn ảnh từ máy hoặc nhập URL trực tuyến.
     *  - Khi bấm Lưu: Gọi [FirebaseRepository.saveStoreDish] để đẩy lên Firestore.
     * Luồng hướng tới: Cập nhật trực tiếp thực đơn trên Firestore và thông báo thành công.
     */
    private fun showDishDialog(editingDish: Dish?) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_edit_dish)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvTitle = dialog.findViewById<TextView>(R.id.tvModalTitle)
        val ivPreview = dialog.findViewById<ImageView>(R.id.ivDishPreview)
        val btnPickImage = dialog.findViewById<MaterialButton>(R.id.btnPickDishImage)
        val btnClearImage = dialog.findViewById<TextView>(R.id.btnClearDishImage)
        val etName = dialog.findViewById<EditText>(R.id.etDishName)
        val etPrice = dialog.findViewById<EditText>(R.id.etDishPrice)
        val spinnerCategory = dialog.findViewById<Spinner>(R.id.spinnerDishCategory)
        val spinnerPlatformCategory = dialog.findViewById<Spinner>(R.id.spinnerPlatformCategory)
        val etDescription = dialog.findViewById<EditText>(R.id.etDishDescription)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancelDish)
        val btnSave = dialog.findViewById<MaterialButton>(R.id.btnSaveDish)

        var currentImageUrl = editingDish?.imageUrl.orEmpty()

        val storeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, storeCategories.toTypedArray())
        spinnerCategory.adapter = storeAdapter

        val tvQuickManage = dialog.findViewById<TextView>(R.id.tvQuickManageCategories)
        tvQuickManage?.setOnClickListener {
            showQuickAddCategoryDialog { newCat ->
                val updatedAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, storeCategories.toTypedArray())
                spinnerCategory.adapter = updatedAdapter
                val newIndex = storeCategories.indexOfFirst { it.equals(newCat, ignoreCase = true) }
                if (newIndex >= 0) spinnerCategory.setSelection(newIndex)
            }
        }

        val platformAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, platformCategories.toTypedArray())
        spinnerPlatformCategory.adapter = platformAdapter

        var userSelectedPlatformCategory = false
        spinnerPlatformCategory.setOnTouchListener { _, _ ->
            userSelectedPlatformCategory = true
            false
        }

        // Tự động gợi ý danh mục nền tảng khi gõ tên món nếu tạo mới
        etName.doAfterTextChanged { text ->
            if (!userSelectedPlatformCategory && editingDish == null) {
                val input = text?.toString().orEmpty().lowercase()
                val suggestedIndex = when {
                    input.contains("cơm") -> platformCategories.indexOfFirst { it.contains("cơm", true) }
                    input.contains("trà") || input.contains("nước") || input.contains("uống") -> platformCategories.indexOfFirst { it.contains("trà", true) || it.contains("nước", true) }
                    input.contains("bún") || input.contains("phở") || input.contains("hủ tiếu") || input.contains("mì") -> platformCategories.indexOfFirst { it.contains("bún", true) || it.contains("phở", true) || it.contains("mì", true) }
                    input.contains("gà") -> platformCategories.indexOfFirst { it.contains("gà", true) }
                    input.contains("bánh mì") -> platformCategories.indexOfFirst { it.contains("bánh mì", true) }
                    input.contains("pizza") -> platformCategories.indexOfFirst { it.contains("pizza", true) }
                    input.contains("cà phê") || input.contains("cafe") || input.contains("coffee") -> platformCategories.indexOfFirst { it.contains("cà phê", true) || it.contains("cafe", true) }
                    input.contains("lẩu") -> platformCategories.indexOfFirst { it.contains("lẩu", true) }
                    input.contains("chay") -> platformCategories.indexOfFirst { it.contains("chay", true) }
                    input.contains("hải sản") || input.contains("tôm") || input.contains("cua") || input.contains("cá") || input.contains("mực") -> platformCategories.indexOfFirst { it.contains("hải sản", true) }
                    input.contains("chè") || input.contains("kem") || input.contains("bánh") || input.contains("ăn vặt") || input.contains("tráng miệng") -> platformCategories.indexOfFirst { it.contains("ăn vặt", true) || it.contains("tráng miệng", true) }
                    else -> -1
                }
                if (suggestedIndex >= 0) {
                    spinnerPlatformCategory.setSelection(suggestedIndex)
                }
            }
        }

        // Thiết lập dữ liệu ban đầu
        if (editingDish != null) {
            tvTitle.text = "Chỉnh Sửa Món Ăn"
            etName.setText(editingDish.name)
            val currentPrice = if (editingDish.basePrice > 0) editingDish.basePrice else editingDish.price
            etPrice.setText(currentPrice.toString())
            etDescription.setText(editingDish.description)

            loadPreviewImage(ivPreview, editingDish.imageUrl)
            val catIndex = storeCategories.indexOfFirst { it.equals(editingDish.category, ignoreCase = true) }
            if (catIndex >= 0) spinnerCategory.setSelection(catIndex)

            val platIndex = platformCategories.indexOfFirst {
                it.equals(editingDish.platformCategory, ignoreCase = true) ||
                it.equals(editingDish.categoryId, ignoreCase = true)
            }
            if (platIndex >= 0) {
                spinnerPlatformCategory.setSelection(platIndex)
            } else {
                val fallbackIndex = platformCategories.indexOfFirst {
                    editingDish.platformCategory.isNotBlank() && it.contains(editingDish.platformCategory, true)
                }
                if (fallbackIndex >= 0) spinnerPlatformCategory.setSelection(fallbackIndex)
            }
        } else {
            tvTitle.text = "Thêm Món Mới Vào Menu"
            loadPreviewImage(ivPreview, "")
        }

        // Tải ảnh từ thư viện thiết bị
        btnPickImage.setOnClickListener {
            onImagePickedCallback = { base64Data ->
                currentImageUrl = base64Data
                loadPreviewImage(ivPreview, base64Data)
                Toast.makeText(requireContext(), "📷 Đã chọn ảnh món ăn thành công!", Toast.LENGTH_SHORT).show()
            }
            pickImageLauncher.launch("image/*")
        }

        // Đặt lại ảnh mặc định
        btnClearImage.setOnClickListener {
            currentImageUrl = ""
            ivPreview.setImageResource(R.drawable.bg_badge_orange)
        }



        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val priceStr = etPrice.text.toString().trim()
            val category = spinnerCategory.selectedItem?.toString().orEmpty().ifBlank { "Món chính" }
            val platformCategory = spinnerPlatformCategory.selectedItem?.toString().orEmpty().ifBlank { "Món Khác" }
            val description = etDescription.text.toString().trim()
            val finalImageUrl = currentImageUrl.ifBlank { "" }

            if (name.isBlank() || priceStr.isBlank()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ tên và giá món!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val price = priceStr.toLongOrNull() ?: 0L
            val matchedCategory = platformCategoryObjects.find { it.categoryName.equals(platformCategory, ignoreCase = true) }
            val finalCategoryId = matchedCategory?.categoryId?.ifBlank { null } ?: "cat_other"

            if (editingDish != null) {
                // Sửa món
                val updated = editingDish.copy(
                    name = name,
                    basePrice = price,
                    category = category,
                    platformCategory = platformCategory,
                    categoryId = finalCategoryId,
                    description = description,
                    imageUrl = finalImageUrl,
                    storeId = currentStoreId
                )
                val index = dishList.indexOfFirst { it.id == editingDish.id }
                if (index >= 0) {
                    dishList[index] = updated
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    firebaseRepository.saveStoreDish(currentStoreId, updated)
                }
                Toast.makeText(requireContext(), "✓ Đã cập nhật món [$name] thành công!", Toast.LENGTH_SHORT).show()
            } else {
                // Thêm món mới
                val newDishId = "dish_${currentStoreId}_${System.currentTimeMillis()}"
                val newDish = Dish(
                    id = newDishId,
                    storeId = currentStoreId,
                    name = name,
                    basePrice = price,
                    category = category,
                    platformCategory = platformCategory,
                    categoryId = finalCategoryId,
                    description = description,
                    imageUrl = finalImageUrl,
                    isAvailable = true
                )
                dishList.add(newDish)
                viewLifecycleOwner.lifecycleScope.launch {
                    firebaseRepository.saveStoreDish(currentStoreId, newDish)
                }
                Toast.makeText(requireContext(), "✓ Đã thêm món [$name] vào thực đơn quán!", Toast.LENGTH_SHORT).show()
            }

            updateList()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Tên chức năng: Hộp thoại xác nhận và Xóa món ăn
     * Cách hoạt động: Hiển thị AlertDialog cảnh báo; nếu xác nhận, xóa món trên Firestore via [FirebaseRepository.deleteStoreDish].
     * Luồng hướng tới: Gỡ bỏ món khỏi thực đơn của quán.
     */
    private fun confirmDeleteDish(dish: Dish) {
        AlertDialog.Builder(requireContext())
            .setTitle("Xác Nhận Xóa")
            .setMessage("Bạn có chắc chắn muốn xóa món [${dish.name}] khỏi thực đơn quán?")
            .setPositiveButton("Xóa") { _, _ ->
                dishList.removeAll { it.id == dish.id }
                viewLifecycleOwner.lifecycleScope.launch {
                    firebaseRepository.deleteStoreDish(currentStoreId, dish.id)
                }
                updateList()
                Toast.makeText(requireContext(), "Đã xóa món khỏi thực đơn!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /**
     * Tên chức năng: Hộp thoại Quản lý danh mục quán (Thêm, Sửa tên, Xóa, Khôi phục)
     */
    private fun showManageCategoriesDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_manage_store_categories)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etNewCategory = dialog.findViewById<EditText>(R.id.etNewCategory)
        val btnAddCategory = dialog.findViewById<MaterialButton>(R.id.btnAddCategory)
        val rvCategories = dialog.findViewById<RecyclerView>(R.id.rvStoreCategories)
        val layoutEmpty = dialog.findViewById<View>(R.id.layoutEmptyCategories)
        val btnDone = dialog.findViewById<MaterialButton>(R.id.btnDone)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseDialog)

        lateinit var catAdapter: ManageStoreCategoryAdapter

        fun refreshCategoryList() {
            val items = storeCategories.map { cat ->
                StoreCategoryItem(cat, dishList.count { it.category.equals(cat, ignoreCase = true) })
            }
            catAdapter.submitList(items)
            layoutEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            rvCategories.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
        }

        catAdapter = ManageStoreCategoryAdapter(
            onEditClick = { oldName ->
                showRenameCategoryDialog(oldName) {
                    refreshCategoryList()
                }
            },
            onDeleteClick = { catName, dishCount ->
                confirmDeleteCategory(catName, dishCount) {
                    refreshCategoryList()
                }
            }
        )

        rvCategories.layoutManager = LinearLayoutManager(requireContext())
        rvCategories.adapter = catAdapter
        refreshCategoryList()

        btnAddCategory.setOnClickListener {
            val newName = etNewCategory.text.toString().trim()
            if (newName.isBlank()) {
                Toast.makeText(requireContext(), "Vui lòng nhập tên danh mục!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (storeCategories.any { it.equals(newName, ignoreCase = true) }) {
                Toast.makeText(requireContext(), "Danh mục [$newName] đã tồn tại!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val result = firebaseRepository.addStoreCategory(currentStoreId, newName)
                if (result.isSuccess) {
                    if (!storeCategories.contains(newName)) {
                        storeCategories.add(newName)
                    }
                    etNewCategory.setText("")
                    refreshCategoryList()
                    setupCategoryFilterChips()
                    Toast.makeText(requireContext(), "✓ Đã thêm danh mục [$newName]!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val dismissAction = View.OnClickListener { dialog.dismiss() }
        btnClose.setOnClickListener(dismissAction)
        btnDone.setOnClickListener(dismissAction)

        dialog.show()
    }

    /**
     * Hộp thoại Đổi tên danh mục
     */
    private fun showRenameCategoryDialog(oldName: String, onUpdated: () -> Unit) {
        val renameDialog = Dialog(requireContext())
        renameDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        renameDialog.setContentView(R.layout.dialog_edit_category_name)
        renameDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        renameDialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etRename = renameDialog.findViewById<EditText>(R.id.etRenameCategory)
        val btnCancel = renameDialog.findViewById<MaterialButton>(R.id.btnCancelRename)
        val btnConfirm = renameDialog.findViewById<MaterialButton>(R.id.btnConfirmRename)

        etRename.setText(oldName)
        etRename.setSelection(oldName.length)

        btnCancel.setOnClickListener { renameDialog.dismiss() }

        btnConfirm.setOnClickListener {
            val newName = etRename.text.toString().trim()
            if (newName.isBlank()) {
                Toast.makeText(requireContext(), "Tên danh mục không được để trống!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newName.equals(oldName, ignoreCase = true)) {
                renameDialog.dismiss()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val result = firebaseRepository.renameStoreCategory(currentStoreId, oldName, newName)
                if (result.isSuccess) {
                    val idx = storeCategories.indexOfFirst { it.equals(oldName, ignoreCase = true) }
                    if (idx != -1) storeCategories[idx] = newName
                    if (selectedStoreCategory.equals(oldName, ignoreCase = true)) {
                        selectedStoreCategory = newName
                    }
                    // Cập nhật cục bộ các món ăn
                    dishList.forEachIndexed { i, dish ->
                        if (dish.category.equals(oldName, ignoreCase = true)) {
                            dishList[i] = dish.copy(category = newName)
                        }
                    }
                    setupCategoryFilterChips()
                    updateList()
                    onUpdated()
                    renameDialog.dismiss()
                    Toast.makeText(requireContext(), "✓ Đã đổi tên thành [$newName]!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        renameDialog.show()
    }

    /**
     * Xác nhận và xóa danh mục quán
     */
    private fun confirmDeleteCategory(catName: String, dishCount: Int, onDeleted: () -> Unit) {
        val msg = if (dishCount > 0) {
            "Danh mục [$catName] đang có $dishCount món ăn. Khi xóa danh mục này, các món ăn trên sẽ được tự động chuyển sang nhóm 'Món Khác'."
        } else {
            "Bạn có chắc chắn muốn xóa danh mục [$catName]?"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Xác Nhận Xóa Danh Mục")
            .setMessage(msg)
            .setPositiveButton("Xóa") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val res = firebaseRepository.deleteStoreCategory(currentStoreId, catName)
                    if (res.isSuccess) {
                        storeCategories.removeAll { it.equals(catName, ignoreCase = true) }
                        if (selectedStoreCategory.equals(catName, ignoreCase = true)) {
                            selectedStoreCategory = "ALL"
                        }
                        // Chuyển các món thuộc catName sang "Món Khác"
                        if (dishCount > 0) {
                            dishList.forEachIndexed { i, dish ->
                                if (dish.category.equals(catName, ignoreCase = true)) {
                                    val updated = dish.copy(category = "Món Khác")
                                    dishList[i] = updated
                                    firebaseRepository.saveStoreDish(currentStoreId, updated)
                                }
                            }
                        }
                        setupCategoryFilterChips()
                        updateList()
                        onDeleted()
                        Toast.makeText(requireContext(), "Đã xóa danh mục [$catName]!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /**
     * Thêm nhanh danh mục khi đang tạo/sửa món ăn
     */
    private fun showQuickAddCategoryDialog(onCategoryAdded: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            hint = "Nhập tên danh mục mới (vd: Trà Hoa Quả)"
            setSingleLine(true)
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Thêm Nhanh Danh Mục Mới")
            .setView(input)
            .setPositiveButton("Thêm") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    if (storeCategories.any { it.equals(newName, ignoreCase = true) }) {
                        onCategoryAdded(newName)
                        return@setPositiveButton
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        val res = firebaseRepository.addStoreCategory(currentStoreId, newName)
                        if (res.isSuccess) {
                            if (!storeCategories.contains(newName)) {
                                storeCategories.add(newName)
                            }
                            setupCategoryFilterChips()
                            onCategoryAdded(newName)
                            Toast.makeText(requireContext(), "✓ Đã thêm danh mục [$newName]!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Lỗi thêm danh mục", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /**
     * Tên chức năng: Hộp thoại Tạo Combo Món Mới
     * Đặc điểm:
     *  - Quán chọn các món lẻ đưa vào combo kèm số lượng.
     *  - Hệ thống tự động tính tổng giá gốc của các món lẻ.
     *  - Quán TỰ DO CHỈNH GIÁ BÁN combo theo ý muốn.
     *  - Hệ thống tính toán và hiển thị % tiết kiệm cho khách hàng.
     */
    private fun showCreateComboDialog() {
        val availableDishes = dishList.filter { !it.isCombo }
        if (availableDishes.size < 2) {
            Toast.makeText(requireContext(), "Quán cần có ít nhất 2 món lẻ trong thực đơn để tạo Combo!", Toast.LENGTH_LONG).show()
            return
        }

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_create_combo)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etComboName = dialog.findViewById<EditText>(R.id.etComboName)
        val tvSelectCountNote = dialog.findViewById<TextView>(R.id.tvSelectCountNote)
        val rvSelectDishes = dialog.findViewById<RecyclerView>(R.id.rvSelectDishes)
        val tvOriginalPriceTotal = dialog.findViewById<TextView>(R.id.tvOriginalPriceTotal)
        val etComboPrice = dialog.findViewById<EditText>(R.id.etComboPrice)
        val tvSavingsNote = dialog.findViewById<TextView>(R.id.tvSavingsNote)
        val ivComboPreview = dialog.findViewById<ImageView>(R.id.ivComboPreview)
        val btnPickComboImage = dialog.findViewById<MaterialButton>(R.id.btnPickComboImage)
        val etComboDescription = dialog.findViewById<EditText>(R.id.etComboDescription)
        val btnCancelCombo = dialog.findViewById<MaterialButton>(R.id.btnCancelCombo)
        val btnSaveCombo = dialog.findViewById<MaterialButton>(R.id.btnSaveCombo)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseComboDialog)

        var pickedImageBase64: String = ""

        lateinit var comboAdapter: SelectComboDishAdapter

        fun updatePriceCalculation() {
            val totalOriginal = comboAdapter.calculateTotalOriginalPrice()
            tvOriginalPriceTotal.text = totalOriginal.toVndCurrency()
            val selected = comboAdapter.getSelectedItems()
            tvSelectCountNote.text = if (selected.isEmpty()) {
                "Chọn ít nhất 2 món ăn / thức uống"
            } else {
                "Đã chọn ${selected.size} món (tổng ${selected.sumOf { it.quantity }} phần)"
            }

            val priceInput = etComboPrice.text.toString().trim().toLongOrNull() ?: 0L
            if (priceInput > 0L && totalOriginal > priceInput) {
                val diff = totalOriginal - priceInput
                val pct = ((diff.toDouble() / totalOriginal.toDouble()) * 100).toInt()
                tvSavingsNote.text = "🎉 Tiết kiệm ${diff.toVndCurrency()} (-$pct%) cho khách!"
                tvSavingsNote.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_green))
            } else if (priceInput >= totalOriginal && totalOriginal > 0L) {
                tvSavingsNote.text = "💡 Giá bán nên thấp hơn tổng giá gốc để thu hút khách mua hơn"
                tvSavingsNote.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            } else {
                tvSavingsNote.text = "💡 Giá bán Combo do Quán tự quyết định theo ý muốn"
                tvSavingsNote.setTextColor(Color.parseColor("#9A3412"))
            }

            // Nếu chưa chọn ảnh và có chọn món, lấy ảnh món đầu tiên làm preview
            if (pickedImageBase64.isBlank()) {
                val firstWithImg = selected.firstOrNull { it.dish.imageUrl.isNotBlank() }
                if (firstWithImg != null) {
                    ivComboPreview.setPadding(0, 0, 0, 0)
                    ivComboPreview.imageTintList = null
                    loadPreviewImage(ivComboPreview, firstWithImg.dish.imageUrl)
                } else {
                    val pad = (12 * resources.displayMetrics.density).toInt()
                    ivComboPreview.setPadding(pad, pad, pad, pad)
                    ivComboPreview.setImageResource(R.drawable.ic_utensils_crossed)
                    ivComboPreview.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
                }
            }
        }

        comboAdapter = SelectComboDishAdapter {
            updatePriceCalculation()
        }

        rvSelectDishes.layoutManager = LinearLayoutManager(requireContext())
        rvSelectDishes.adapter = comboAdapter
        comboAdapter.submitDishes(availableDishes)

        etComboPrice.doAfterTextChanged {
            updatePriceCalculation()
        }

        btnPickComboImage.setOnClickListener {
            onImagePickedCallback = { base64 ->
                pickedImageBase64 = base64
                ivComboPreview.setPadding(0, 0, 0, 0)
                ivComboPreview.imageTintList = null
                loadPreviewImage(ivComboPreview, base64)
            }
            pickImageLauncher.launch("image/*")
        }

        btnCancelCombo.setOnClickListener { dialog.dismiss() }
        btnClose.setOnClickListener { dialog.dismiss() }

        btnSaveCombo.setOnClickListener {
            val name = etComboName.text.toString().trim()
            if (name.isBlank()) {
                Toast.makeText(requireContext(), "Vui lòng nhập tên Combo!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedItems = comboAdapter.getSelectedItems()
            if (selectedItems.size < 2) {
                Toast.makeText(requireContext(), "Vui lòng chọn ít nhất 2 món đưa vào Combo!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val price = etComboPrice.text.toString().trim().toLongOrNull() ?: 0L
            if (price <= 0L) {
                Toast.makeText(requireContext(), "Vui lòng nhập giá bán hợp lệ của Combo!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalOriginal = comboAdapter.calculateTotalOriginalPrice()
            val desc = etComboDescription.text.toString().trim()
            val finalImageUrl = if (pickedImageBase64.isNotBlank()) {
                pickedImageBase64
            } else {
                selectedItems.firstOrNull { it.dish.imageUrl.isNotBlank() }?.dish?.imageUrl.orEmpty()
            }

            val comboItemsList = selectedItems.map {
                com.chuyen_de_2.foodorder.core.data.model.ComboItem(
                    dishId = it.dish.id,
                    name = it.dish.name,
                    quantity = it.quantity,
                    price = if (it.dish.basePrice > 0) it.dish.basePrice else it.dish.price
                )
            }

            val comboDish = Dish(
                id = "combo_${System.currentTimeMillis()}",
                storeId = currentStoreId,
                name = name,
                description = desc,
                imageUrl = finalImageUrl,
                basePrice = price,
                originalPrice = totalOriginal,
                isCombo = true,
                comboItems = comboItemsList,
                category = "Combo",
                platformCategory = "Combo",
                isAvailable = true
            )

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    firebaseRepository.saveStoreDish(currentStoreId, comboDish)
                    if (!storeCategories.contains("Combo")) {
                        firebaseRepository.addStoreCategory(currentStoreId, "Combo")
                        storeCategories.add("Combo")
                        setupCategoryFilterChips()
                    }
                    dishList.add(comboDish)
                    updateList()
                    Toast.makeText(requireContext(), "✓ Đã tạo Combo [$name] thành công!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Lỗi tạo Combo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = MenuManageFragment()
    }
}
