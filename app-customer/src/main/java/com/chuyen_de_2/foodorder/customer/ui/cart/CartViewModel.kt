package com.chuyen_de_2.foodorder.customer.ui.cart

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Cart
import com.chuyen_de_2.foodorder.core.data.model.CartItem
import com.chuyen_de_2.foodorder.core.data.model.CartPayload
import com.chuyen_de_2.foodorder.core.util.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CartViewModel — Quản lý giỏ hàng với Debounce Algorithm (Section 6 - doc-adr)
 *
 * Lắng nghe realtime giỏ hàng từ Firebase, cho phép sửa số lượng.
 * Mỗi thay đổi được gom qua debounce(1500ms) trước khi sync lên server.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class CartViewModel @Inject constructor(
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ViewModel() {

    private val _carts = MutableLiveData<List<Cart>>()
    val carts: LiveData<List<Cart>> = _carts

    private val _totalAmount = MutableLiveData(0L)
    val totalAmount: LiveData<Long> = _totalAmount

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val cartUpdates = MutableSharedFlow<CartPayload>(extraBufferCapacity = 1)

    private var cartListener: ValueEventListener? = null

    init {
        // Debounce sync (Section 6 - doc-adr)
        viewModelScope.launch {
            cartUpdates
                .debounce(Constants.DEBOUNCE_TIMEOUT_MS)
                .distinctUntilChanged()
                .collectLatest { payload ->
                    firebaseRepository.syncCartToFirebase(payload)
                }
        }

        loadCarts()
    }

    private fun loadCarts() {
        val userId = auth.currentUser?.uid ?: return
        _isLoading.value = true

        val ref = database.getReference("carts").child(userId)

        cartListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cartList = mutableListOf<Cart>()
                for (shopSnapshot in snapshot.children) {
                    val cart = shopSnapshot.getValue(Cart::class.java)
                    if (cart != null && cart.items.isNotEmpty()) {
                        cartList.add(cart.copy(shopId = shopSnapshot.key ?: ""))
                    }
                }
                _carts.value = cartList
                _totalAmount.value = cartList.sumOf { it.totalAmount }
                _isLoading.value = false
            }

            override fun onCancelled(error: DatabaseError) {
                _isLoading.value = false
            }
        }

        ref.addValueEventListener(cartListener!!)
    }

    fun updateItemQuantity(shopId: String, shopName: String, itemId: String, newQty: Int) {
        val userId = auth.currentUser?.uid ?: return
        val currentCarts = _carts.value?.toMutableList() ?: return
        val cartIndex = currentCarts.indexOfFirst { it.shopId == shopId }
        if (cartIndex < 0) return

        val cart = currentCarts[cartIndex]
        val items = cart.items.toMutableMap()

        if (newQty <= 0) {
            items.remove(itemId)
        } else {
            items[itemId]?.let { items[itemId] = it.copy(quantity = newQty) }
        }

        val total = items.values.sumOf { it.price * it.quantity }
        val updatedCart = cart.copy(totalAmount = total, items = items)
        currentCarts[cartIndex] = updatedCart

        _carts.value = currentCarts
        _totalAmount.value = currentCarts.sumOf { it.totalAmount }

        // Debounce sync
        cartUpdates.tryEmit(CartPayload(userId, shopId, updatedCart))
    }

    fun clearCart(shopId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            firebaseRepository.clearCart(userId, shopId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cartListener?.let {
            val userId = auth.currentUser?.uid ?: return
            database.getReference("carts").child(userId).removeEventListener(it)
        }
    }
}
