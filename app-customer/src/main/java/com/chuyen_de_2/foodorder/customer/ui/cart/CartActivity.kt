package com.chuyen_de_2.foodorder.customer.ui.cart

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
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

        InsetUtils.setLightStatusBar(this, false)
        InsetUtils.applyToolbarInsets(binding.toolbar)
        InsetUtils.applyNavigationBarPadding(binding.layoutCheckout)

        setupToolbar()
        setupRecyclerView()
        setupCheckout()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Giỏ hàng"
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = CartAdapter(
            onQuantityChange = { shopId, shopName, itemId, newQty ->
                viewModel.updateItemQuantity(shopId, shopName, itemId, newQty)
            }
        )

        binding.rvCart.apply {
            layoutManager = LinearLayoutManager(this@CartActivity)
            adapter = this@CartActivity.adapter
        }
    }

    private fun setupCheckout() {
        binding.btnCheckout.setOnClickListener {
            startActivity(Intent(this, CheckoutActivity::class.java))
        }
    }

    private fun observeViewModel() {
        viewModel.carts.observe(this) { carts ->
            adapter.submitCarts(carts)
            binding.tvEmpty.visibility = if (carts.isEmpty()) View.VISIBLE else View.GONE
            binding.layoutCheckout.visibility = if (carts.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.totalAmount.observe(this) { total ->
            binding.tvTotal.text = total.toVndCurrency()
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }
}
