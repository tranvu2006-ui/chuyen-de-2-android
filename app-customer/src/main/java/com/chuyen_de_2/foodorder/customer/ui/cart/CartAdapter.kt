package com.chuyen_de_2.foodorder.customer.ui.cart

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Cart
import com.chuyen_de_2.foodorder.core.data.model.CartItem
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.databinding.ItemShopCartBinding
import com.chuyen_de_2.foodorder.customer.databinding.ItemShopDishBinding

class CartAdapter(
    private val onQuantityChange: (shopId: String, shopName: String, itemId: String, newQty: Int) -> Unit,
    private val onDeleteShopCart: (shopId: String, shopName: String) -> Unit,
    private val onCheckoutShop: (shopId: String, shopName: String, subtotal: Long) -> Unit
) : RecyclerView.Adapter<CartAdapter.ShopViewHolder>() {

    private val carts = mutableListOf<Cart>()

    fun submitCarts(newCarts: List<Cart>) {
        carts.clear()
        carts.addAll(newCarts)
        notifyDataSetChanged()
    }

    inner class ShopViewHolder(private val binding: ItemShopCartBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(cart: Cart) {
            val displayName = if (cart.shopName.startsWith("🏪")) {
                cart.shopName
            } else {
                "🏪 ${cart.shopName.ifBlank { "Quán ăn" }}"
            }
            binding.tvShopName.text = displayName

            val locationText = when {
                cart.shopDistrict.isNotBlank() -> "Vị trí: ${cart.shopDistrict}, TP.HCM"
                cart.shopAddress.isNotBlank() -> "Vị trí: ${cart.shopAddress}"
                else -> "Vị trí: TP.HCM"
            }
            binding.tvShopTag.text = locationText

            binding.tvSubtotal.text = cart.totalAmount.toVndCurrency()

            binding.btnDeleteShopCart.setOnClickListener {
                onDeleteShopCart(cart.shopId, cart.shopName)
            }

            binding.btnCheckoutShop.setOnClickListener {
                onCheckoutShop(cart.shopId, cart.shopName, cart.totalAmount)
            }

            // Danh sách món trong quán
            val dishList = cart.items.values.toList()
            val dishAdapter = CartDishAdapter(
                shopId = cart.shopId,
                shopName = cart.shopName,
                dishes = dishList,
                onQuantityChange = onQuantityChange
            )
            binding.rvDishes.apply {
                layoutManager = LinearLayoutManager(itemView.context)
                isNestedScrollingEnabled = false
                adapter = dishAdapter
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        val binding = ItemShopCartBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ShopViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        holder.bind(carts[position])
    }

    override fun getItemCount() = carts.size

    /**
     * Adapter cho các món ăn bên trong từng Shop Card (theo mockup a1.html)
     */
    private class CartDishAdapter(
        private val shopId: String,
        private val shopName: String,
        private val dishes: List<CartItem>,
        private val onQuantityChange: (shopId: String, shopName: String, itemId: String, newQty: Int) -> Unit
    ) : RecyclerView.Adapter<CartDishAdapter.DishViewHolder>() {

        inner class DishViewHolder(val binding: ItemShopDishBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DishViewHolder {
            val binding = ItemShopDishBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return DishViewHolder(binding)
        }

        override fun onBindViewHolder(holder: DishViewHolder, position: Int) {
            val item = dishes[position]
            val binding = holder.binding

            binding.tvDishName.text = "${item.name} x${item.quantity}"

            if (item.options.isNotBlank()) {
                binding.tvDishNote.text = item.options
                binding.tvDishNote.visibility = View.VISIBLE
            } else {
                binding.tvDishNote.visibility = View.GONE
            }

            binding.tvQuantity.text = item.quantity.toString()
            binding.tvDishPrice.text = (item.price * item.quantity).toVndCurrency()

            binding.btnDecrease.setOnClickListener {
                onQuantityChange(shopId, shopName, item.itemId, item.quantity - 1)
            }

            binding.btnIncrease.setOnClickListener {
                onQuantityChange(shopId, shopName, item.itemId, item.quantity + 1)
            }
        }

        override fun getItemCount() = dishes.size
    }
}
