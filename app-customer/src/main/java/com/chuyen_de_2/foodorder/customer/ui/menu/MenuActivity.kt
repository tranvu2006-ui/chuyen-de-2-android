package com.chuyen_de_2.foodorder.customer.ui.menu

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.chuyen_de_2.foodorder.core.data.model.Dish
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityMenuBinding
import com.chuyen_de_2.foodorder.customer.ui.cart.CartActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Tên chức năng: Chi Tiết Thực Đơn Quán & Đặt Món (Store Menu & Item Detail Screen)
 * Cách hoạt động:
 *  - Lấy danh sách món ăn từ Cửa hàng tương ứng theo `shopId` từ Firestore.
 *  - Khu vực phía trên (Top Detail Section): Hiển thị hình ảnh lớn, tên món, giá gốc gạch ngang, giá khuyến mãi, điểm đánh giá sao ⭐ và bình luận của khách hàng.
 *  - Khu vực phía dưới: Phân loại món ăn theo Chip danh mục (Mới, Combo, Cà phê, Trà, v.v.) và danh sách món dạng RecyclerView.
 *  - Bấm chọn món sẽ tự động thêm vào giỏ hàng hoặc mở modal tùy chọn Size/Topping [DishOptionBottomSheetDialogFragment].
 *  - Thanh đặt hàng cố định (Bottom Checkout Bar): Hiển thị tổng số lượng món, tổng số tiền và nút "Xem Giỏ Hàng".
 * Luồng hướng tới:
 *  - Bấm "Xem Giỏ Hàng" -> Chuyển sang màn hình giỏ hàng [CartActivity].
 *  - Bấm "Đánh giá" -> Mở BottomSheet xem chi tiết đánh giá [DishReviewsBottomSheetDialogFragment].
 */
@AndroidEntryPoint
class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding
    private val viewModel: MenuViewModel by viewModels()
    private lateinit var adapter: MenuAdapter
    private var shopName: String = "Nhà hàng"

    /**
     * Tên chức năng: Khởi tạo màn hình thực đơn quán
     * Cách hoạt động: Nhận `shopId`, `shopName` từ Intent, nạp dữ liệu menu từ ViewModel và thiết lập giao diện.
     * Luồng hướng tới: Chuẩn bị hiển thị danh sách món ăn của quán.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)
        InsetUtils.applyToolbarInsets(binding.layoutTopNav)
        InsetUtils.applyNavigationBarPadding(binding.layoutBottomCheckoutBar)

        val shopId = intent.getStringExtra(Constants.EXTRA_SHOP_ID) ?: run {
            Toast.makeText(this, "Lỗi: không tìm thấy nhà hàng", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        shopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME) ?: "Nhà hàng"
        val initialDishId = intent.getStringExtra(Constants.EXTRA_DISH_ID)

        viewModel.setShopInfo(shopId, shopName, initialDishId)

        setupTopNav()
        setupCategoryChips()
        setupRecyclerView()
        setupBottomCheckoutBar()
        observeViewModel()
    }

    /**
     * Tên chức năng: Thiết lập thanh điều hướng đỉnh (Top Navigation Bar)
     * Cách hoạt động: Bắt sự kiện nút quay lại, chia sẻ liên kết quán và thêm quán vào danh sách yêu thích.
     * Luồng hướng tới: Tương tác người dùng.
     */
    private fun setupTopNav() {
        binding.btnNavBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.tvShopNavTitle.text = shopName

        binding.btnShare.setOnClickListener {
            Toast.makeText(this, "Chia sẻ liên kết quán $shopName", Toast.LENGTH_SHORT).show()
        }

        binding.btnFavorite.setOnClickListener {
            Toast.makeText(this, "Đã thêm $shopName vào danh sách yêu thích!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Tên chức năng: Bộ lọc danh mục món ăn (Category Filter Chips)
     * Cách hoạt động: Lắng nghe sự kiện chọn Chip để lọc danh sách món: Món mới, Combo, Cà phê, Trà hoặc Tất cả.
     * Luồng hướng tới: Gọi [MenuViewModel.filterByCategory].
     */
    private fun setupCategoryChips() {
        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedId = checkedIds.first()
            val category = when (selectedId) {
                R.id.chipNewDrink -> "mới"
                R.id.chipCombo -> "Combo"
                R.id.chipCoffee -> "Cà phê"
                R.id.chipTea -> "Trà"
                else -> "ALL"
            }
            viewModel.filterByCategory(category)
        }
    }

    /**
     * Tên chức năng: Khởi tạo RecyclerView thực đơn món ăn
     * Cách hoạt động: Cấu hình [MenuAdapter] với các hành động thêm/bớt món, lấy số lượng đã chọn trong giỏ và xem chi tiết món.
     * Luồng hướng tới: Render từng món ăn trong menu.
     */
    private fun setupRecyclerView() {
        adapter = MenuAdapter(
            onAddClick = { dish ->
                handleDishSelection(dish)
            },
            onRemoveClick = { itemId ->
                viewModel.removeFromCart(itemId)
            },
            getQuantity = { itemId ->
                viewModel.getItemQuantity(itemId)
            },
            onItemClick = { dish ->
                handleDishSelection(dish)
            }
        )

        binding.rvMenu.apply {
            layoutManager = LinearLayoutManager(this@MenuActivity)
            adapter = this@MenuActivity.adapter
        }
    }

    /**
     * Tên chức năng: Cấu hình thanh Đặt Món / Xem Giỏ Hàng phía dưới
     * Cách hoạt động: Đồng bộ dữ liệu giỏ hàng vào Room/Shared ViewModel và chuyển hướng sang [CartActivity].
     * Luồng hướng tới: [CartActivity].
     */
    private fun setupBottomCheckoutBar() {
        binding.btnCheckoutNow.setOnClickListener {
            viewModel.flushCart()
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    /**
     * Tên chức năng: Lắng nghe trạng thái dữ liệu từ MenuViewModel
     * Cách hoạt động:
     *  - Cập nhật danh sách món ăn hiển thị.
     *  - Cập nhật thông tin món ăn đang chọn ở phần chi tiết đỉnh.
     *  - Hiển thị hoặc ẩn thanh đặt hàng dưới cùng kèm tổng tiền.
     * Luồng hướng tới: Đồng bộ giao diện tức thời.
     */
    private fun observeViewModel() {
        viewModel.dishes.observe(this) { items ->
            adapter.submitList(items)
            binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.tvDishesCountBadge.text = "${items.size} MÓN"
        }

        viewModel.selectedDish.observe(this) { dish ->
            if (dish != null) {
                bindTopDetailSection(dish)
                adapter.setSelectedDishId(dish.id)
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.cartCount.observe(this) { count ->
            if (count > 0) {
                binding.layoutBottomCheckoutBar.visibility = View.VISIBLE
                binding.tvCheckoutCartSub.text = "Đã chọn $count món • $shopName"
            } else {
                binding.layoutBottomCheckoutBar.visibility = View.GONE
            }
        }

        viewModel.cartTotalPrice.observe(this) { total ->
            binding.tvCheckoutTotalPrice.text = total.toVndCurrency()
        }

        viewModel.cartItems.observe(this) {
            adapter.notifyDataSetChanged()
        }
    }

    /**
     * Tên chức năng: Hiển thị chi tiết món ăn ở khung nổi bật phía trên (Top Detail Card)
     * Cách hoạt động: Render tên, giá, ảnh món ăn, mô tả, huy hiệu khuyến mãi và điểm sao đánh giá.
     * Luồng hướng tới: Cung cấp thông tin cận cảnh về món ăn.
     */
    private fun bindTopDetailSection(dish: Dish) {
        binding.tvDetailDishTitle.text = dish.name
        binding.tvDetailDishStore.text = "Quán $shopName"
        binding.tvDetailPriceMain.text = dish.price.toVndCurrency()

        val originalPrice = (dish.price * 1.3).toLong()
        binding.tvDetailPriceOld.paintFlags = binding.tvDetailPriceOld.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        binding.tvDetailPriceOld.text = originalPrice.toVndCurrency()

        val rating = if (dish.ratingAvg > 0) dish.ratingAvg else 4.8f
        val reviewCount = if (dish.reviewCount > 0) dish.reviewCount else 128
        binding.tvDetailRatingText.text = "⭐ $rating"
        binding.btnViewDishReviews.text = "⭐ Đánh giá ($reviewCount) ➔"

        binding.tvDetailDesc.text = if (dish.description.isNotBlank()) {
            dish.description
        } else {
            "Món ngon thơm lừng chế biến từ nguyên liệu tươi sạch, giữ trọn hương vị đặc trưng truyền thống."
        }

        if (dish.category.contains("mới", ignoreCase = true) || dish.soldCount > 500) {
            binding.tvDetailPromoBadge.visibility = View.VISIBLE
            binding.tvDetailPromoBadge.text = if (dish.soldCount > 1000) "HOT" else "-25%"
        } else {
            binding.tvDetailPromoBadge.visibility = View.GONE
        }

        Glide.with(this)
            .load(dish.imageUrl)
            .transform(CenterCrop(), RoundedCorners(28))
            .placeholder(R.drawable.placeholder_food)
            .error(R.drawable.placeholder_food)
            .into(binding.ivDetailFood)

        binding.btnViewDishReviews.setOnClickListener {
            val sheet = DishReviewsBottomSheetDialogFragment.newInstance(
                dishName = dish.name,
                dishRating = rating,
                reviewCount = reviewCount
            )
            sheet.show(supportFragmentManager, "DishReviewsBottomSheet")
        }

        binding.cardTopDetail.setOnClickListener {
            handleDishSelection(dish)
        }
    }

    /**
     * Tên chức năng: Xử lý sự kiện chọn món và thêm vào giỏ hàng
     * Cách hoạt động: Cập nhật món đang chọn lên ViewModel, tự động cộng thêm số lượng vào giỏ và thông báo cho người dùng.
     * Luồng hướng tới: Thêm món vào [CartViewModel].
     */
    private fun handleDishSelection(dish: Dish) {
        viewModel.selectDish(dish)
        adapter.setSelectedDishId(dish.id)

        viewModel.addToCart(dish)
        adapter.notifyDataSetChanged()

        val currentQty = viewModel.getItemQuantity(dish.id)
        Toast.makeText(this, "✓ Đã chọn: ${dish.name} (x$currentQty)", Toast.LENGTH_SHORT).show()
    }

    private fun showDishOptionBottomSheet(dish: Dish) {
        val bottomSheet = DishOptionBottomSheetDialogFragment(dish) { cartItem ->
            viewModel.addCustomCartItem(cartItem)
            adapter.notifyDataSetChanged()
        }
        bottomSheet.show(supportFragmentManager, DishOptionBottomSheetDialogFragment.TAG)
    }
}
