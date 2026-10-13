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

@AndroidEntryPoint
class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding
    private val viewModel: CartViewModel by viewModels()
    private lateinit var adapter: CartAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, true)
        InsetUtils.applyToolbarInsets(binding.toolbar)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

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

    private fun observeViewModel() {
        viewModel.carts.observe(this) { carts ->
            adapter.submitCarts(carts)
            val hasItems = carts.isNotEmpty()
            binding.tvEmpty.visibility = if (hasItems) View.GONE else View.VISIBLE
            binding.tvSubNotice.visibility = if (hasItems) View.VISIBLE else View.GONE
            binding.rvCart.visibility = if (hasItems) View.VISIBLE else View.GONE

            binding.tvShopCountBadge.text = if (hasItems) "${carts.size} Quán Ăn" else ""
            binding.tvShopCountBadge.visibility = if (hasItems) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }
}
