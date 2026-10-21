package com.chuyen_de_2.foodorder.customer.ui.cart

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.databinding.ActivityCartBinding
import com.chuyen_de_2.foodorder.customer.ui.checkout.CheckoutActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Tên chức năng: Màn Hình Quản Lý Giỏ Hàng Đa Quán (Multi-Store Shopping Cart)
 * Cách hoạt động:
 *  - Hỗ trợ giỏ hàng phân tách theo từng quán ăn (Multi-Store Architecture).
 *  - Cho phép tăng/giảm số lượng từng món ăn hoặc xóa sạch giỏ hàng của từng quán.
 *  - Tính toán tổng tiền theo từng quán độc lập và cung cấp nút "Thanh Toán" riêng cho từng quán để đảm bảo quy trình giao nhận đơn quán chuẩn xác.
 * Luồng hướng tới:
 *  - Bấm nút "Thanh Toán" của quán tương ứng -> Chuyển sang màn hình xác nhận và đặt hàng [CheckoutActivity].
 *  - Bấm nút Back -> Quay về màn hình trước đó.
 */
@AndroidEntryPoint
class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding
    private val viewModel: CartViewModel by viewModels()
    private lateinit var adapter: CartAdapter

    /**
     * Tên chức năng: Khởi tạo View giỏ hàng
     * Cách hoạt động: Thiết lập Insets an toàn, cấu hình toolbar và RecyclerView giỏ hàng, lắng nghe ViewModel.
     * Luồng hướng tới: Chuẩn bị hiển thị danh sách các giỏ hàng.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)
        InsetUtils.applyToolbarInsets(binding.toolbar)
        InsetUtils.applyNavigationBarPadding(binding.rvCart)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * Tên chức năng: Cấu hình RecyclerView giỏ hàng và các Callback thao tác
     * Cách hoạt động:
     *  - Callback thay đổi số lượng món: Cập nhật trực tiếp vào cơ sở dữ liệu giỏ hàng.
     *  - Callback xóa giỏ hàng: Hiển thị hộp thoại xác nhận trước khi xóa.
     *  - Callback thanh toán: Truyền `shopId` và `shopName` sang màn hình thanh toán.
     * Luồng hướng tới: [CheckoutActivity].
     */
    private fun setupRecyclerView() {
        adapter = CartAdapter(
            onQuantityChange = { shopId, shopName, itemId, newQty ->
                viewModel.updateItemQuantity(shopId, shopName, itemId, newQty)
            },
            onDeleteShopCart = { shopId, shopName ->
                AlertDialog.Builder(this)
                    .setTitle("Xoá giỏ hàng quán")
                    .setMessage("Bạn có chắc muốn xoá toàn bộ món ăn của quán \"$shopName\" khỏi giỏ hàng?")
                    .setPositiveButton("Xoá") { _, _ ->
                        viewModel.clearCart(shopId)
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            },
            onCheckoutShop = { shopId, shopName, _ ->
                val intent = Intent(this, CheckoutActivity::class.java).apply {
                    putExtra(Constants.EXTRA_SHOP_ID, shopId)
                    putExtra(Constants.EXTRA_SHOP_NAME, shopName)
                }
                startActivity(intent)
            }
        )

        binding.rvCart.apply {
            layoutManager = LinearLayoutManager(this@CartActivity)
            adapter = this@CartActivity.adapter
        }
    }

    /**
     * Tên chức năng: Lắng nghe danh sách giỏ hàng từ CartViewModel
     * Cách hoạt động: Cập nhật danh sách các quán trong giỏ hàng, hiển thị Empty State nếu giỏ trống.
     * Luồng hướng tới: Đồng bộ hiển thị giao diện.
     */
    private fun observeViewModel() {
        viewModel.carts.observe(this) { carts ->
            adapter.submitCarts(carts)
            val hasItems = carts.isNotEmpty()
            binding.tvEmpty.visibility = if (hasItems) View.GONE else View.VISIBLE
            binding.tvSubNotice.visibility = if (hasItems) View.VISIBLE else View.GONE
            binding.rvCart.visibility = if (hasItems) View.GONE else View.VISIBLE

            binding.tvShopCountBadge.text = if (hasItems) "${carts.size} Quán Ăn" else ""
            binding.tvShopCountBadge.visibility = if (hasItems) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }
}
