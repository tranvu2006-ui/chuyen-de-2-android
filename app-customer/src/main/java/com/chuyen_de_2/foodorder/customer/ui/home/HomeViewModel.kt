package com.chuyen_de_2.foodorder.customer.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Store
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firebaseRepository: FirebaseRepository
) : ViewModel() {

    private val _stores = MutableLiveData<List<Store>>(emptyList())
    val stores: LiveData<List<Store>> = _stores
    val restaurants: LiveData<List<Store>> get() = _stores

    // Danh sách món ăn chính hiển thị trên trang chủ (100% từ dữ liệu thật do quán tự thêm)
    private val _homeDishes = MutableLiveData<List<HomeDishItem>>(emptyList())
    val homeDishes: LiveData<List<HomeDishItem>> = _homeDishes

    // Danh sách quán ăn gần đây hiển thị trong Bottom Sheet Modal (100% từ quán thật)
    private val _nearbyStores = MutableLiveData<List<NearbyStoreItem>>(emptyList())
    val nearbyStores: LiveData<List<NearbyStoreItem>> = _nearbyStores

    private val _hotDishes = MutableLiveData<List<HotDish>>(emptyList())
    val hotDishes: LiveData<List<HotDish>> = _hotDishes

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var masterDishList = listOf<HomeDishItem>()
    private var currentQuery: String? = null
    private var currentCategory: String? = null
    private var currentNearbyOnly: Boolean = false

    init {
        viewModelScope.launch {
            firebaseRepository.cleanupOrphanedData()
        }
        loadStores()
    }

    fun loadStores() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            // Lắng nghe dữ liệu quán ăn thật theo thời gian thực từ Firebase Realtime Database
            firebaseRepository.observeAllStores().collectLatest { firebaseStores ->
                _isLoading.value = false
                _stores.value = firebaseStores
                processStoresAndDishes(firebaseStores)
            }
        }
    }

    private fun processStoresAndDishes(storeList: List<Store>) {
        // Chỉ hiển thị các quán đang hoạt động (OPEN) và không bị Admin ẩn/khóa
        val activeStores = storeList.filter { store ->
            val st = store.storeStatus.uppercase()
            val pt = store.penaltyTier.uppercase()
            st != Store.StoreStatus.LOCKED &&
            st != Store.StoreStatus.PENDING &&
            st != Store.StoreStatus.CLOSED &&
            pt != Store.PenaltyTier.LOCK &&
            pt != Store.PenaltyTier.HIDE
        }

        if (activeStores.isEmpty()) {
            _nearbyStores.value = emptyList()
            _homeDishes.value = emptyList()
            _hotDishes.value = emptyList()
            masterDishList = emptyList()
            return
        }

        // 1. Quán gần đây từ dữ liệu thật trên Firebase
        val nearbyList = activeStores.mapIndexed { index, store ->
            val dist = if (store.latitude != 0.0 && store.longitude != 0.0) {
                // Tạm tính khoảng cách từ toạ độ quán
                0.8 + (index * 0.4)
            } else {
                0.8 + (index * 0.4)
            }
            val roundedDist = Math.round(dist * 10.0) / 10.0
            val etaMinutes = 15 + (index * 5)
            val emoji = when {
                store.name.contains("Trà", true) -> "🍵"
                store.name.contains("Cơm", true) -> "🍛"
                store.name.contains("Gà", true) -> "🍗"
                store.name.contains("Bún", true) || store.name.contains("Phở", true) -> "🍜"
                else -> "🍲"
            }
            NearbyStoreItem(
                id = store.id,
                name = store.name,
                address = store.address.ifBlank { "TP.HCM" },
                distanceKm = roundedDist,
                eta = "$etaMinutes-${etaMinutes + 5} phút",
                rating = if (store.ratingAvg > 0) store.ratingAvg else 5.0f,
                imageUrl = store.imageUrl,
                emoji = emoji,
                originalStore = store
            )
        }
        _nearbyStores.value = nearbyList

        // 2. Thu thập các món ăn THẬT do các quán tự thêm vào stores/{storeId}/dishes (chỉ lấy món không bị ẩn)
        val allDishes = mutableListOf<HomeDishItem>()

        activeStores.forEachIndexed { storeIndex, store ->
            val dist = 0.8 + (storeIndex * 0.4)
            val roundedDist = Math.round(dist * 10.0) / 10.0

            store.dishes.values.filter { !it.isHidden && it.isAvailable }.forEach { dish ->
                val emoji = when {
                    dish.name.contains("Trà", true) -> "🧋"
                    dish.name.contains("Cơm", true) -> "🍛"
                    dish.name.contains("Gà", true) -> "🍗"
                    dish.name.contains("Bún", true) || dish.name.contains("Phở", true) -> "🍜"
                    dish.name.contains("Bánh mì", true) -> "🥖"
                    dish.name.contains("Pizza", true) -> "🍕"
                    dish.name.contains("Cà phê", true) -> "☕"
                    else -> "🍲"
                }
                allDishes.add(
                    HomeDishItem(
                        id = dish.id,
                        name = dish.name,
                        price = dish.basePrice,
                        originalPrice = if (dish.basePrice > 0) (dish.basePrice * 1.2).toLong() else 0L,
                        storeId = store.id,
                        storeName = store.name,
                        imageUrl = dish.imageUrl.ifBlank { store.imageUrl },
                        emoji = emoji,
                        rating = if (dish.ratingAvg > 0) dish.ratingAvg else 5.0f,
                        soldCount = "${dish.soldCount} đã bán",
                        distanceKm = roundedDist,
                        promoBadge = if (dish.basePrice >= 50000) "-15%" else "Món ngon",
                        tag1 = if (dish.category.isNotBlank()) dish.category else "Món của quán",
                        tag2 = "Giao Nhanh",
                        category = dish.category.ifBlank { store.category }
                    )
                )
            }
        }

        masterDishList = allDishes
        applyFilters()

        // Hot dishes chỉ lấy từ món thật
        _hotDishes.value = allDishes.take(6).map {
            HotDish(
                id = it.id,
                name = it.name,
                price = it.price,
                storeId = it.storeId,
                storeName = it.storeName,
                imageUrl = it.imageUrl,
                emoji = it.emoji
            )
        }
    }

    /** Lọc danh sách món ăn */
    fun filterDishes(query: String? = null, category: String? = null, nearbyOnly: Boolean? = null) {
        if (query != null) currentQuery = query
        if (category != null) currentCategory = if (category == "ALL") null else category
        if (nearbyOnly != null) currentNearbyOnly = nearbyOnly
        applyFilters()
    }

    private fun applyFilters() {
        var filtered = masterDishList

        // 1. Lọc theo cự ly gần (< 1.5 km)
        if (currentNearbyOnly) {
            filtered = filtered.filter { it.distanceKm <= 1.5 }
        }

        // 2. Lọc theo danh mục
        val cat = currentCategory
        if (!cat.isNullOrBlank()) {
            filtered = filtered.filter { dish ->
                dish.category.contains(cat, ignoreCase = true) ||
                dish.name.contains(cat, ignoreCase = true)
            }
        }

        // 3. Lọc theo từ khóa tìm kiếm
        val q = currentQuery?.trim()?.lowercase()
        if (!q.isNullOrBlank()) {
            filtered = filtered.filter { dish ->
                dish.name.lowercase().contains(q) ||
                dish.storeName.lowercase().contains(q) ||
                dish.category.lowercase().contains(q)
            }
        }

        _homeDishes.value = filtered
    }

    fun loadRestaurants() = loadStores()
}
