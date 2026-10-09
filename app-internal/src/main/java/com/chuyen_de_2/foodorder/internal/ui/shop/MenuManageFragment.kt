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
import com.chuyen_de_2.foodorder.core.data.model.Dish
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.FragmentMenuManageBinding
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint

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

    private lateinit var adapter: ManageDishAdapter
    private val dishList = mutableListOf<Dish>()
    private var isCheckedIn = true
    private var autoDishId = 3

    var onNavigateToOrdersListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initDefaultDishes()
        setupUI()
        setupRecyclerView()
    }

    private fun initDefaultDishes() {
        if (dishList.isEmpty()) {
            dishList.add(
                Dish(
                    id = "dish-1",
                    name = "Trà Xí Muội Ô Long",
                    category = "Trà Trái Cây",
                    basePrice = 28000,
                    imageUrl = "https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=300&auto=format&fit=crop&q=60"
                )
            )
            dishList.add(
                Dish(
                    id = "dish-2",
                    name = "Trà Xí Muội Ngô Gia",
                    category = "Trà Trái Cây",
                    basePrice = 25000,
                    imageUrl = "https://images.unsplash.com/photo-1558857563-b371033873b8?w=300&auto=format&fit=crop&q=60"
                )
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

        val categories = arrayOf("Trà Trái Cây", "Trà Sữa", "Cơm Phần", "Ăn Vặt", "Món Khác")
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
                val index = dishList.indexOfFirst { it.id == editingDish.id }
                if (index >= 0) {
                    val updated = editingDish.copy(
                        name = name,
                        basePrice = price,
                        category = category
                    )
                    dishList[index] = updated
                    Toast.makeText(requireContext(), "✓ Đã cập nhật món [$name] thành công!", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Thêm món mới
                val newDish = Dish(
                    id = "dish-$autoDishId",
                    name = name,
                    basePrice = price,
                    category = category,
                    imageUrl = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=300&auto=format&fit=crop&q=60"
                )
                autoDishId++
                dishList.add(newDish)
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
