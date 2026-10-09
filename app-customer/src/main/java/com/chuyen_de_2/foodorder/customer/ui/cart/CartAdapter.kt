package com.chuyen_de_2.foodorder.customer.ui.cart

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Cart
import com.chuyen_de_2.foodorder.core.data.model.CartItem
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.databinding.ItemCartBinding

class CartAdapter(
    private val onQuantityChange: (shopId: String, shopName: String, itemId: String, newQty: Int) -> Unit
) : RecyclerView.Adapter<CartAdapter.ViewHolder>() {

    private val items = mutableListOf<CartDisplayItem>()

    data class CartDisplayItem(
        val shopId: String,
        val shopName: String,
        val itemId: String,
        val cartItem: CartItem
    )

    fun submitCarts(carts: List<Cart>) {
        items.clear()
        carts.forEach { cart ->
            cart.items.forEach { (itemId, cartItem) ->
                items.add(CartDisplayItem(cart.shopId, cart.shopName, itemId, cartItem))
            }
        }
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemCartBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CartDisplayItem) {
            binding.tvName.text = item.cartItem.name
            binding.tvPrice.text = item.cartItem.price.toVndCurrency()
            binding.tvQuantity.text = item.cartItem.quantity.toString()
            binding.tvSubtotal.text = (item.cartItem.price * item.cartItem.quantity).toVndCurrency()

            binding.btnIncrease.setOnClickListener {
                onQuantityChange(item.shopId, item.shopName, item.itemId, item.cartItem.quantity + 1)
            }

            binding.btnDecrease.setOnClickListener {
                onQuantityChange(item.shopId, item.shopName, item.itemId, item.cartItem.quantity - 1)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCartBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
