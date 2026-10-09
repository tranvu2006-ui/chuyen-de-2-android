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

@AndroidEntryPoint
class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding
    private val viewModel: MenuViewModel by viewModels()
    private lateinit var adapter: MenuAdapter
    private var shopName: String = "Nhà hàng"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)
        InsetUtils.applyToolbarInsets(binding.layoutTopNav)

        val shopId = intent.getStringExtra(Constants.EXTRA_SHOP_ID) ?: run {
            Toast.makeText(this, "Lỗi: không tìm thấy nhà hàng", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        shopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME) ?: "Nhà hàng"

        viewModel.setShopInfo(shopId, shopName)

        setupTopNav()
        setupCategoryChips()
        setupRecyclerView()
        setupBottomCheckoutBar()
        observeViewModel()
    }

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

    private fun setupRecyclerView() {
        adapter = MenuAdapter(
            onAddClick = { dish ->
                viewModel.addToCart(dish)
            },
            onRemoveClick = { itemId ->
                viewModel.removeFromCart(itemId)
            },
            getQuantity = { itemId ->
                viewModel.getItemQuantity(itemId)
            },
            onItemClick = { dish ->
                viewModel.selectDish(dish)
                adapter.setSelectedDishId(dish.id)
                binding.nestedScroll.smoothScrollTo(0, 0)
            }
        )

        binding.rvMenu.apply {
            layoutManager = LinearLayoutManager(this@MenuActivity)
            adapter = this@MenuActivity.adapter
        }
    }

    private fun setupBottomCheckoutBar() {
        binding.btnCheckoutNow.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    private fun observeViewModel() {
        // 1. Quan sát danh sách món ăn
        viewModel.dishes.observe(this) { items ->
            adapter.submitList(items)
            binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.tvDishesCountBadge.text = "${items.size} MÓN"
        }

        // 2. Quan sát món ăn đang được chọn (Nửa trên Top Detail Section - b5.html)
        viewModel.selectedDish.observe(this) { dish ->
            if (dish != null) {
                bindTopDetailSection(dish)
                adapter.setSelectedDishId(dish.id)
            }
        }

        // 3. Loading
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // 4. Giỏ hàng & Thanh đặt món cố định dưới cùng
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
     * Hiển thị chi tiết món ăn ở nửa trên theo b5.html
     */
    private fun bindTopDetailSection(dish: Dish) {
        binding.tvDetailDishTitle.text = dish.name
        binding.tvDetailDishStore.text = "Quán $shopName"
        binding.tvDetailPriceMain.text = dish.price.toVndCurrency()

        // Giá gốc giả lập gạch ngang nếu có
        val originalPrice = (dish.price * 1.3).toLong()
        binding.tvDetailPriceOld.paintFlags = binding.tvDetailPriceOld.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        binding.tvDetailPriceOld.text = originalPrice.toVndCurrency()

        // Rating
        val rating = if (dish.ratingAvg > 0) dish.ratingAvg else 4.8f
        val reviewCount = if (dish.reviewCount > 0) dish.reviewCount else 128
        binding.tvDetailRatingText.text = "⭐ $rating"
        binding.btnViewDishReviews.text = "⭐ Đánh giá ($reviewCount) ➔"

        // Mô tả món
        binding.tvDetailDesc.text = if (dish.description.isNotBlank()) {
            dish.description
        } else {
            "Món ngon thơm lừng chế biến từ nguyên liệu tươi sạch, giữ trọn hương vị đặc trưng truyền thống."
        }

        // Badge promo
        if (dish.category.contains("mới", ignoreCase = true) || dish.soldCount > 500) {
            binding.tvDetailPromoBadge.visibility = View.VISIBLE
            binding.tvDetailPromoBadge.text = if (dish.soldCount > 1000) "HOT" else "-25%"
        } else {
            binding.tvDetailPromoBadge.visibility = View.GONE
        }

        // Load ảnh lớn 100x100
        Glide.with(this)
            .load(dish.imageUrl)
            .transform(CenterCrop(), RoundedCorners(28))
            .placeholder(R.drawable.placeholder_food)
            .error(R.drawable.placeholder_food)
            .into(binding.ivDetailFood)

        // Nút mở modal BottomSheet đánh giá chi tiết
        binding.btnViewDishReviews.setOnClickListener {
            val sheet = DishReviewsBottomSheetDialogFragment.newInstance(
                dishName = dish.name,
                dishRating = rating,
                reviewCount = reviewCount
            )
            sheet.show(supportFragmentManager, "DishReviewsBottomSheet")
        }
    }
}
