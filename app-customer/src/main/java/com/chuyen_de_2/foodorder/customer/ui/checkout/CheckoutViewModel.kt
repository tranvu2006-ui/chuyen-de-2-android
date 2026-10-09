package com.chuyen_de_2.foodorder.customer.ui.checkout

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Cart
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ViewModel() {

    private val _carts = MutableLiveData<List<Cart>>()
    val carts: LiveData<List<Cart>> = _carts

    private val _orderResult = MutableLiveData<Result<String>>()
    val orderResult: LiveData<Result<String>> = _orderResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadCarts()
    }

    private fun loadCarts() {
        val userId = auth.currentUser?.uid ?: return

        database.getReference("carts").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val cartList = mutableListOf<Cart>()
                    for (shopSnapshot in snapshot.children) {
                        val cart = shopSnapshot.getValue(Cart::class.java)
                        if (cart != null && cart.items.isNotEmpty()) {
                            cartList.add(cart.copy(shopId = shopSnapshot.key ?: ""))
                        }
                    }
                    _carts.value = cartList
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /** Đặt hàng — tạo Order trên Firebase và xóa giỏ hàng */
    fun placeOrder(
        address: String,
        note: String,
        shippingFee: Long = 15000L,
        discount: Long = 0L,
        paymentMethod: String = Order.PaymentMethod.COD
    ) {
        val userId = auth.currentUser?.uid ?: return
        val cartList = _carts.value ?: return

        if (cartList.isEmpty()) {
            _orderResult.value = Result.failure(Exception("Giỏ hàng trống"))
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                var lastCreatedOrderId = ""
                // Tạo 1 order cho mỗi shop
                for (cart in cartList) {
                    val token = (100000..999999).random().toString()
                    val finalPayment = maxOf(0L, cart.totalAmount + shippingFee - discount)

                    val order = Order(
                        customerId = userId,
                        shopId = cart.shopId,
                        shopName = cart.shopName,
                        status = Order.Status.PENDING,
                        orderStatus = Order.Status.PENDING,
                        total = finalPayment,
                        subtotalAmount = cart.totalAmount,
                        shippingFee = shippingFee,
                        totalPayment = finalPayment,
                        paymentMethod = paymentMethod,
                        deliveryToken = token,
                        items = cart.items.mapValues { (_, item) ->
                            com.chuyen_de_2.foodorder.core.data.model.OrderItem(
                                itemId = item.itemId,
                                name = if (item.options.isNotBlank()) "${item.name} (${item.options})" else item.name,
                                price = item.price,
                                quantity = item.quantity,
                                imageUrl = item.imageUrl
                            )
                        },
                        address = address,
                        note = note,
                        createdAt = System.currentTimeMillis()
                    )

                    val result = firebaseRepository.createOrder(order)
                    if (result.isFailure) {
                        _orderResult.value = Result.failure(
                            result.exceptionOrNull() ?: Exception("Lỗi tạo đơn")
                        )
                        _isLoading.value = false
                        return@launch
                    }

                    lastCreatedOrderId = result.getOrNull() ?: ""

                    // Xóa giỏ hàng sau khi đặt thành công
                    firebaseRepository.clearCart(userId, cart.shopId)
                }

                _orderResult.value = Result.success(lastCreatedOrderId.ifBlank { "OK" })
            } catch (e: Exception) {
                _orderResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
