package com.chuyen_de_2.foodorder.customer.ui.menu

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Cart
import com.chuyen_de_2.foodorder.core.data.model.CartItem
import com.chuyen_de_2.foodorder.core.data.model.CartPayload
import com.chuyen_de_2.foodorder.core.data.model.Dish
import com.chuyen_de_2.foodorder.core.data.model.MenuItem
import com.chuyen_de_2.foodorder.core.data.remote.RestBackendApi
import com.chuyen_de_2.foodorder.core.util.Constants
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel cho màn hình Menu — load dữ liệu từ Retrofit, thêm vào giỏ hàng.
 * Hỗ trợ giao diện b5.html: Nửa trên (selectedDish), Nửa dưới (filteredDishes), Bottom Bar (cartTotal).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class MenuViewModel @Inject constructor(
    private val api: RestBackendApi,
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val allDishes = mutableListOf<Dish>()

    private val _dishes = MutableLiveData<List<Dish>>()
    val dishes: LiveData<List<Dish>> = _dishes

    val menuItems: LiveData<List<Dish>> get() = _dishes

    // Món đang được chọn hiển thị ở nửa trên (Top Detail Section - b5.html)
    private val _selectedDish = MutableLiveData<Dish?>()
    val selectedDish: LiveData<Dish?> = _selectedDish

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Trạng thái giỏ hàng local — phản hồi UI ngay lập tức
    private val _cartItems = MutableLiveData<MutableMap<String, CartItem>>(mutableMapOf())
    val cartItems: LiveData<MutableMap<String, CartItem>> = _cartItems

    private val _cartCount = MutableLiveData(0)
    val cartCount: LiveData<Int> = _cartCount

    private val _cartTotalPrice = MutableLiveData(0L)
    val cartTotalPrice: LiveData<Long> = _cartTotalPrice

    private var currentShopId: String = ""
    private var currentShopName: String = ""
    private var currentCategory: String = "ALL"

    private val cartUpdates = MutableSharedFlow<CartPayload>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            cartUpdates
                .debounce(Constants.DEBOUNCE_TIMEOUT_MS)
                .distinctUntilChanged()
                .collectLatest { payload ->
                    firebaseRepository.syncCartToFirebase(payload)
                }
        }
    }

    fun setShopInfo(shopId: String, shopName: String) {
        currentShopId = shopId
        currentShopName = shopName
        loadMenu(shopId)
    }

    private fun loadMenu(shopId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = api.getDishesByStore(shopId)
                if (response.isSuccessful) {
                    val list = response.body() ?: emptyList()
                    allDishes.clear()
                    allDishes.addAll(list)

                    applyFilter()

                    // Chọn món đầu tiên làm mặc định hiển thị ở nửa trên nếu chưa có
                    if (_selectedDish.value == null && list.isNotEmpty()) {
                        _selectedDish.value = list.first()
                    }
                }
            } catch (e: Exception) {
                // Handled by UI
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectDish(dish: Dish) {
        _selectedDish.value = dish
    }

    fun filterByCategory(categoryName: String) {
        currentCategory = categoryName
        applyFilter()
    }

    private fun applyFilter() {
        if (currentCategory.equals("ALL", ignoreCase = true) || currentCategory.isEmpty()) {
            _dishes.value = allDishes
        } else {
            _dishes.value = allDishes.filter {
                it.category.contains(currentCategory, ignoreCase = true)
            }
        }
    }

    fun addToCart(dish: Dish) {
        val currentMap = _cartItems.value ?: mutableMapOf()
        val existing = currentMap[dish.id]

        if (existing != null) {
            currentMap[dish.id] = existing.copy(quantity = existing.quantity + 1)
        } else {
            currentMap[dish.id] = CartItem(
                itemId = dish.id,
                name = dish.name,
                price = dish.price,
                quantity = 1,
                imageUrl = dish.imageUrl
            )
        }

        _cartItems.value = currentMap
        updateCartTotals()
        emitCartUpdate()
    }

    fun addCustomCartItem(cartItem: CartItem) {
        val currentMap = _cartItems.value ?: mutableMapOf()
        val existing = currentMap[cartItem.itemId]

        if (existing != null) {
            currentMap[cartItem.itemId] = existing.copy(quantity = existing.quantity + cartItem.quantity)
        } else {
            currentMap[cartItem.itemId] = cartItem
        }

        _cartItems.value = currentMap
        updateCartTotals()
        emitCartUpdate()
    }

    fun addToCart(menuItem: MenuItem) {
        val dish = Dish(
            id = menuItem.id,
            name = menuItem.name,
            basePrice = menuItem.price,
            imageUrl = menuItem.imageUrl,
            description = menuItem.description,
            category = menuItem.category
        )
        addToCart(dish)
    }

    fun removeFromCart(itemId: String) {
        val currentMap = _cartItems.value ?: mutableMapOf()
        val existing = currentMap[itemId] ?: return

        if (existing.quantity > 1) {
            currentMap[itemId] = existing.copy(quantity = existing.quantity - 1)
        } else {
            currentMap.remove(itemId)
        }

        _cartItems.value = currentMap
        updateCartTotals()
        emitCartUpdate()
    }

    fun getItemQuantity(itemId: String): Int {
        return _cartItems.value?.get(itemId)?.quantity ?: 0
    }

    private fun updateCartTotals() {
        val items = _cartItems.value?.values ?: emptyList()
        _cartCount.value = items.sumOf { it.quantity }
        _cartTotalPrice.value = items.sumOf { it.price * it.quantity }
    }

    private fun emitCartUpdate() {
        val userId = auth.currentUser?.uid ?: return
        val items = _cartItems.value ?: return
        val total = items.values.sumOf { it.price * it.quantity }

        val cart = Cart(
            shopId = currentShopId,
            shopName = currentShopName,
            totalAmount = total,
            items = items
        )

        cartUpdates.tryEmit(CartPayload(userId, currentShopId, cart))
    }
}
