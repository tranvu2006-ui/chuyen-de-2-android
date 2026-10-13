package com.chuyen_de_2.foodorder.internal.ui.shop

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Dish
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.FragmentMenuManageBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MenuManageFragment — Quản lý Thực Đơn & Điểm Danh Mở Ca Quán
 * Thiết kế và tính năng chuẩn theo Quán/2.html:
 * - Nút Check-in mở ca / tạm nghỉ
 * - Banner nhận đơn hàng mới
 * - Thao tác Thêm Món & Tạo Combo
 * - Danh sách món ăn mở bán kèm nút Sửa & Xóa
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
    private var currentStoreId = "shop_01"

    var onNavigateToOrdersListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        resolveCurrentStoreId()
        setupUI()
        setupRecyclerView()
        observeStoreDishes()
    }

    private fun resolveCurrentStoreId() {
        val uid = auth.currentUser?.uid.orEmpty()
        currentStoreId = when (uid) {
            "whjjZ5osJhTNtMCovwgp8JXdJK73" -> "shop_01"
            "AdBaJDWpzDYJRxVdl2qbWX8jAUR2" -> "shop_02"
            "c3AlIlYhEDcGKC9Z4EYOlWqfeCN2" -> "shop_03"
            "uNzJQswcLSNnuF4GpQAlMdQnS1P2" -> "shop_04"
            "QCXRaghsICbmi1Pngptv3s0mWO32" -> "shop_05"
            "J5Cz86Vxd8RRfwp0iK8F1b2Rslx1" -> "shop_06"
            else -> "shop_01"
        }
    }

    private fun observeStoreDishes() {
        viewLifecycleOwner.lifecycleScope.launch {
            firebaseRepository.observeStoreDishes(currentStoreId).collectLatest { dishes ->
                dishList.clear()
                if (dishes.isNotEmpty()) {
                    dishList.addAll(dishes)
                } else {
                    dishList.addAll(getDefaultShopDishes(currentStoreId))
                    // Tự động đẩy món mặc định lên Firebase nếu quán chưa có món
                    dishList.forEach { dish ->
                        firebaseRepository.saveStoreDish(currentStoreId, dish)
                    }
                }
                updateList()
            }
        }
    }

    private fun getDefaultShopDishes(storeId: String): List<Dish> {
        return when (storeId) {
            "shop_02" -> listOf(
                Dish(id = "dish_02_01", storeId = "shop_02", name = "Phở Bò Tái Lăn Hà Nội", basePrice = 65000, category = "Món chính", imageUrl = "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=400"),
                Dish(id = "dish_02_02", storeId = "shop_02", name = "Phở Đặc Biệt (Tái, Nạm, Gầu)", basePrice = 75000, category = "Món chính", imageUrl = "https://images.unsplash.com/photo-1555126634-323283e090fa?w=400"),
                Dish(id = "dish_02_03", storeId = "shop_02", name = "Quẩy Giòn Hà Nội", basePrice = 10000, category = "Kèm", imageUrl = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400")
            )
            "shop_03" -> listOf(
                Dish(id = "dish_03_01", storeId = "shop_03", name = "Bún Bò Huế Đặc Biệt", basePrice = 60000, category = "Món chính", imageUrl = "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=400"),
                Dish(id = "dish_03_02", storeId = "shop_03", name = "Bún Bò Giò Heo Chả Cua", basePrice = 65000, category = "Món chính", imageUrl = "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=400")
            )
            "shop_04" -> listOf(
                Dish(id = "dish_04_01", storeId = "shop_04", name = "Bánh Mì Pate Chả Đặc Biệt Huỳnh Hoa", basePrice = 58000, category = "Món chính", imageUrl = "https://images.unsplash.com/photo-1509722747041-616f39b57569?w=400"),
                Dish(id = "dish_04_02", storeId = "shop_04", name = "Bánh Mì Thịt Nướng Bơ Tỏi", basePrice = 45000, category = "Món chính", imageUrl = "https://images.unsplash.com/photo-1509722747041-616f39b57569?w=400")
            )
            "shop_05" -> listOf(
                Dish(id = "dish_05_01", storeId = "shop_05", name = "Trà Sữa Trân Châu Hoàng Gia", basePrice = 45000, category = "Nước uống", hasOptions = true, imageUrl = "https://images.unsplash.com/photo-1558857563-b371033873b8?w=400"),
                Dish(id = "dish_05_02", storeId = "shop_05", name = "Trà Sữa Oolong Nướng", basePrice = 48000, category = "Nước uống", hasOptions = true, imageUrl = "https://images.unsplash.com/photo-1556881286-fc6915169721?w=400")
            )
            "shop_06" -> listOf(
                Dish(id = "dish_06_01", storeId = "shop_06", name = "Combo 2 Miếng Gà Rán Giòn Cay", basePrice = 79000, category = "Món chính", imageUrl = "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?w=400"),
                Dish(id = "dish_06_02", storeId = "shop_06", name = "Cánh Gà Sốt Chua Ngọt Hàn Quốc", basePrice = 85000, category = "Món chính", imageUrl = "https://images.unsplash.com/photo-1562967916-eb82221dfb92?w=400")
            )
            else -> listOf(
                Dish(id = "dish_01_01", storeId = "shop_01", name = "Cơm sườn nướng than hoa", basePrice = 55000, category = "Món chính", imageUrl = "https://images.unsplash.com/photo-1512058564366-18510be2db19?w=400"),
                Dish(id = "dish_01_02", storeId = "shop_01", name = "Cơm tấm bì chả đặc biệt", basePrice = 40000, category = "Món chính", imageUrl = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400"),
                Dish(id = "dish_01_03", storeId = "shop_01", name = "Sườn cốt lết chiên giòn", basePrice = 60000, category = "Món chính", imageUrl = "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=400"),
                Dish(id = "dish_01_04", storeId = "shop_01", name = "Canh chua cá lóc", basePrice = 35000, category = "Canh", imageUrl = "https://images.unsplash.com/photo-1547592166-23ac45744acd?w=400"),
                Dish(id = "dish_01_05", storeId = "shop_01", name = "Gỏi cuốn tôm thịt", basePrice = 30000, category = "Khai vị", imageUrl = "https://images.unsplash.com/photo-1562967916-eb82221dfb92?w=400"),
                Dish(id = "dish_01_06", storeId = "shop_01", name = "Trà Ô Long Xí Muội", basePrice = 28000, category = "Nước uống", hasOptions = true, imageUrl = "https://images.unsplash.com/photo-1556881286-fc6915169721?w=400")
            )
        }
    }

    private fun setupUI() {
        // 1. Nút Check-in mở ca (Quán/2.html: toggleCheckin)
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

    private fun updateList() {
        adapter.submitList(dishList.toList())
        binding.layoutEmptyDishes.visibility = if (dishList.isEmpty()) View.VISIBLE else View.GONE
        binding.rvDishes.visibility = if (dishList.isNotEmpty()) View.VISIBLE else View.GONE
        updateDishCountText()
    }

    private fun updateDishCountText() {
        binding.tvDishTotalCount.text = "${dishList.size} món đang mở bán"
    }

    private fun showDishDialog(editingDish: Dish?) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_edit_dish)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvTitle = dialog.findViewById<TextView>(R.id.tvModalTitle)
        val etName = dialog.findViewById<EditText>(R.id.etDishName)
        val etPrice = dialog.findViewById<EditText>(R.id.etDishPrice)
        val spinnerCategory = dialog.findViewById<Spinner>(R.id.spinnerDishCategory)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancelDish)
        val btnSave = dialog.findViewById<MaterialButton>(R.id.btnSaveDish)

        val categories = arrayOf("Món chính", "Canh", "Khai vị", "Nước uống", "Ăn Vặt", "Trà Trái Cây", "Trà Sữa", "Cơm Phần", "Món Khác")
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        spinnerCategory.adapter = spinnerAdapter

        if (editingDish != null) {
            tvTitle.text = "Chỉnh Sửa Món Ăn"
            etName.setText(editingDish.name)
            val currentPrice = if (editingDish.basePrice > 0) editingDish.basePrice else editingDish.price
            etPrice.setText(currentPrice.toString())
            val catIndex = categories.indexOf(editingDish.category)
            if (catIndex >= 0) spinnerCategory.setSelection(catIndex)
        } else {
            tvTitle.text = "Thêm Món Mới Vào Menu"
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val priceStr = etPrice.text.toString().trim()
            val category = spinnerCategory.selectedItem.toString()

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
                    isAvailable = true,
                    imageUrl = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=300&auto=format&fit=crop&q=60"
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
