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
import kotlinx.coroutines.tasks.await
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

    fun loadCarts(filterShopId: String? = null) {
        val userId = auth.currentUser?.uid ?: return

        database.getReference("carts").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val cartList = mutableListOf<Cart>()
                    for (shopSnapshot in snapshot.children) {
                        val currentShopId = shopSnapshot.key ?: ""
                        if (!filterShopId.isNullOrBlank() && currentShopId != filterShopId) {
                            continue
                        }
                        val cart = shopSnapshot.getValue(Cart::class.java)
                        if (cart != null && cart.items.isNotEmpty()) {
                            cartList.add(cart.copy(shopId = currentShopId))
                        }
                    }
                    _carts.value = cartList
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /** Đặt hàng — tạo Order trên Firebase, đồng bộ thông tin khách hàng và xóa giỏ hàng */
    fun placeOrder(
        address: String,
        note: String,
        shippingFee: Long = 15000L,
        discount: Long = 0L,
        paymentMethod: String = Order.PaymentMethod.COD,
        customerPhone: String = "",
        customerName: String = ""
    ) {
        val firebaseUser = auth.currentUser ?: return
        val userId = firebaseUser.uid
        val cartList = _carts.value ?: return

        if (cartList.isEmpty()) {
            _orderResult.value = Result.failure(Exception("Giỏ hàng trống"))
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Lấy thông tin tên, SĐT và Gmail của khách để gắn vào đơn hàng và đồng bộ lên hồ sơ
                var customerName = firebaseUser.displayName.orEmpty().trim()
                var customerPhone = firebaseUser.phoneNumber.orEmpty().trim()
                val customerEmail = firebaseUser.email.orEmpty().trim()

                try {
                    val userSnap = database.getReference("users").child(userId).get().await()
                    val status = userSnap.child("status").getValue(String::class.java).orEmpty().uppercase()
                    val isActive = userSnap.child("isActive").getValue(Boolean::class.java)
                        ?: userSnap.child("active").getValue(Boolean::class.java)
                        ?: true
                    if (status == "LOCKED" || status == "SUSPENDED" || !isActive) {
                        val lockReason = userSnap.child("lockReason").getValue(String::class.java)
                            ?: "Tài khoản của bạn đã bị khóa hoặc tạm ngưng bởi Quản trị viên."
                        _orderResult.value = Result.failure(Exception(lockReason))
                        _isLoading.value = false
                        return@launch
                    }

                    val trustScore = userSnap.child("trustScore").getValue(Long::class.java) ?: 100L
                    if (paymentMethod == Order.PaymentMethod.COD && trustScore < 50L) {
                        _orderResult.value = Result.failure(
                            Exception("Điểm uy tín của bạn hiện đang dưới 50 điểm ($trustScore/100). Hình thức COD bị khóa, vui lòng chọn Chuyển khoản ngân hàng (BANKING)!")
                        )
                        _isLoading.value = false
                        return@launch
                    }

                    val dbName = userSnap.child("name").getValue(String::class.java).orEmpty().trim()
                    val dbPhone = userSnap.child("phone").getValue(String::class.java).orEmpty().trim()
                    if (dbName.isNotBlank()) customerName = dbName
                    if (dbPhone.isNotBlank()) customerPhone = dbPhone

                    val profileUpdates = mutableMapOf<String, Any>("uid" to userId)
                    if (customerEmail.isNotBlank()) profileUpdates["email"] = customerEmail
                    if (customerName.isBlank() && customerEmail.isNotBlank()) {
                        customerName = customerEmail.substringBefore("@")
                    }
                    if (customerName.isNotBlank()) profileUpdates["name"] = customerName
                    if (address.isNotBlank()) profileUpdates["address"] = address
                    database.getReference("users").child(userId).updateChildren(profileUpdates)
                    database.getReference("customers").child(userId).updateChildren(profileUpdates)
                } catch (e: Exception) {
                    if (e.message?.contains("khóa") == true || e.message?.contains("uy tín") == true) {
                        _orderResult.value = Result.failure(e)
                        _isLoading.value = false
                        return@launch
                    }
                    if (customerName.isBlank()) customerName = customerEmail.substringBefore("@")
                }

                var lastCreatedOrderId = ""
                // Tạo 1 order cho mỗi shop
                for (cart in cartList) {
                    val token = (100000..999999).random().toString()
                    val finalPayment = maxOf(0L, cart.totalAmount + shippingFee - discount)

                    val order = Order(
                        customerId = userId,
                        customerName = customerName.ifBlank { firebaseUser.displayName?.ifBlank { "Khách hàng" } ?: "Khách hàng" },
                        customerPhone = customerPhone,
                        storeId = cart.shopId,
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
                        deliveryAddress = address,
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
