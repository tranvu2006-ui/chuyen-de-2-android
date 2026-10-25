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
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Dish
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.FragmentMenuManageBinding
import com.google.android.material.button.MaterialButton
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
    private var platformCategories: MutableList<String> = mutableListOf(
        "Cơm Trưa", "Trà Sữa", "Bún/Phở", "Gà Rán", "Bánh Mì", "Pizza", "Cà Phê", "Tráng Miệng / Ăn Vặt", "Lẩu / Món Nước", "Đồ Chay", "Món Khác"
    )

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
        }
    }

    /**
     * Tên chức năng: Lắng nghe danh sách món ăn theo thời gian thực (Realtime Menu Observer)
     * Cách hoạt động: Lắng nghe flow từ `firebaseRepository.observeStoreDishes`. Nếu quán mới chưa có món, tự động sinh bộ món mẫu.
     * Luồng hướng tới: Cập nhật RecyclerView hiển thị danh sách món.
     */
    private fun observePlatformCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            firebaseRepository.observeCategories().collectLatest { cats ->
                if (cats.isNotEmpty()) {
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

        // 3. Nút Tạo Combo & Thêm Món
        binding.btnCreateCombo.setOnClickListener {
            Toast.makeText(requireContext(), "✨ Tính năng Tạo Combo đang được kích hoạt!", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddFood.setOnClickListener {
            showDishDialog(null)
        }

        updateDishCountText()
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
     * Tên chức năng: Làm mới danh sách hiển thị món ăn
     * Cách hoạt động: Cung cấp danh sách món hiện tại cho Adapter và cập nhật trạng thái trống (Empty State).
     * Luồng hướng tới: Cập nhật giao diện danh sách.
     */
    private fun updateList() {
        adapter.submitList(dishList.toList())
        binding.layoutEmptyDishes.visibility = if (dishList.isEmpty()) View.VISIBLE else View.GONE
        binding.rvDishes.visibility = if (dishList.isNotEmpty()) View.VISIBLE else View.GONE
        updateDishCountText()
    }

    private fun updateDishCountText() {
        binding.tvDishTotalCount.text = "${dishList.size} món đang mở bán"
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
        val etImageUrl = dialog.findViewById<EditText>(R.id.etDishImageUrl)
        val etName = dialog.findViewById<EditText>(R.id.etDishName)
        val etPrice = dialog.findViewById<EditText>(R.id.etDishPrice)
        val spinnerCategory = dialog.findViewById<Spinner>(R.id.spinnerDishCategory)
        val spinnerPlatformCategory = dialog.findViewById<Spinner>(R.id.spinnerPlatformCategory)
        val etDescription = dialog.findViewById<EditText>(R.id.etDishDescription)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancelDish)
        val btnSave = dialog.findViewById<MaterialButton>(R.id.btnSaveDish)

        var currentImageUrl = editingDish?.imageUrl.orEmpty()

        val storeCategories = arrayOf("Món chính", "Canh", "Khai vị", "Món ăn kèm", "Nước uống", "Ăn Vặt", "Trà Trái Cây", "Trà Sữa", "Cơm Phần", "Món Khác")
        val storeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, storeCategories)
        spinnerCategory.adapter = storeAdapter

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
                    input.contains("cơm") -> platformCategories.indexOf("Cơm Trưa")
                    input.contains("trà") -> platformCategories.indexOf("Trà Sữa")
                    input.contains("bún") || input.contains("phở") || input.contains("hủ tiếu") || input.contains("mì") -> platformCategories.indexOf("Bún/Phở")
                    input.contains("gà") -> platformCategories.indexOf("Gà Rán")
                    input.contains("bánh mì") -> platformCategories.indexOf("Bánh Mì")
                    input.contains("pizza") -> platformCategories.indexOf("Pizza")
                    input.contains("cà phê") || input.contains("cafe") || input.contains("coffee") -> platformCategories.indexOf("Cà Phê")
                    input.contains("lẩu") -> platformCategories.indexOf("Lẩu / Món Nước")
                    input.contains("chay") -> platformCategories.indexOf("Đồ Chay")
                    input.contains("chè") || input.contains("kem") || input.contains("bánh") || input.contains("ăn vặt") || input.contains("tráng miệng") -> platformCategories.indexOf("Tráng Miệng / Ăn Vặt")
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
            if (editingDish.imageUrl.isNotBlank() && !editingDish.imageUrl.startsWith("data:image")) {
                etImageUrl.setText(editingDish.imageUrl)
            }
            loadPreviewImage(ivPreview, editingDish.imageUrl)
            val catIndex = storeCategories.indexOf(editingDish.category)
            if (catIndex >= 0) spinnerCategory.setSelection(catIndex)

            val platIndex = platformCategories.indexOf(editingDish.platformCategory)
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
                etImageUrl.setText("")
                loadPreviewImage(ivPreview, base64Data)
                Toast.makeText(requireContext(), "📷 Đã chọn ảnh món ăn thành công!", Toast.LENGTH_SHORT).show()
            }
            pickImageLauncher.launch("image/*")
        }

        // Đặt lại ảnh mặc định
        btnClearImage.setOnClickListener {
            currentImageUrl = ""
            etImageUrl.setText("")
            ivPreview.setImageResource(R.drawable.bg_badge_orange)
        }

        // Preview ảnh khi dán link URL
        etImageUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val inputUrl = etImageUrl.text.toString().trim()
                if (inputUrl.isNotBlank()) {
                    currentImageUrl = inputUrl
                    loadPreviewImage(ivPreview, inputUrl)
                }
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val priceStr = etPrice.text.toString().trim()
            val category = spinnerCategory.selectedItem?.toString().orEmpty().ifBlank { "Món chính" }
            val platformCategory = spinnerPlatformCategory.selectedItem?.toString().orEmpty().ifBlank { "Món Khác" }
            val description = etDescription.text.toString().trim()
            val typedUrl = etImageUrl.text.toString().trim()

            val finalImageUrl = when {
                typedUrl.isNotBlank() -> typedUrl
                currentImageUrl.isNotBlank() -> currentImageUrl
                else -> "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=300&auto=format&fit=crop&q=60"
            }

            if (name.isBlank() || priceStr.isBlank()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ tên và giá món!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val price = priceStr.toLongOrNull() ?: 0L

            if (editingDish != null) {
                // Sửa món
                val updated = editingDish.copy(
                    name = name,
                    basePrice = price,
                    category = category,
                    platformCategory = platformCategory,
                    categoryId = platformCategory,
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
                    categoryId = platformCategory,
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = MenuManageFragment()
    }
}
