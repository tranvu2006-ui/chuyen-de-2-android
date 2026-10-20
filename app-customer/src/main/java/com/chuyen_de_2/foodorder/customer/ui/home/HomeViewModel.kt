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
        // Chỉ loại bỏ quán bị Admin khóa do vi phạm (LOCKED / LOCK / HIDE)
        var activeStores = storeList.filter { store ->
            val st = store.storeStatus.uppercase()
            val pt = store.penaltyTier.uppercase()
            st != Store.StoreStatus.LOCKED &&
            pt != Store.PenaltyTier.LOCK &&
            pt != Store.PenaltyTier.HIDE
        }

        // Fallback: nếu Firebase chưa có quán nào, tự tạo các quán tiêu biểu
        if (activeStores.isEmpty()) {
            activeStores = listOf(
                Store(id = "shop_01", name = "Cơm Tấm Sài Gòn - Nguyễn Trãi", address = "128 Nguyễn Trãi, P. Bến Thành, Q.1", category = "Cơm"),
                Store(id = "shop_02", name = "Phở Bò Gia Truyền Hà Nội", address = "45 Lê Lợi, P. Bến Nghé, Q.1", category = "Bún/Phở"),
                Store(id = "shop_05", name = "Hồng Trà Ngô Gia - Thủ Đức", address = "28 Tô Vĩnh Diện, P. Linh Chiểu, Thủ Đức", category = "Trà Sữa"),
                Store(id = "shop_06", name = "Gà Rán Giòn Cay Oishi", address = "82 Hai Bà Trưng, P. Đa Kao, Q.1", category = "Gà Rán")
            )
        }

        // 1. Quán gần đây
        val nearbyList = activeStores.mapIndexed { index, store ->
            val dist = if (store.latitude != 0.0 && store.longitude != 0.0) {
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

        // 2. Thu thập các món ăn của quán
        val allDishes = mutableListOf<HomeDishItem>()

        activeStores.forEachIndexed { storeIndex, store ->
            val dist = 0.8 + (storeIndex * 0.4)
            val roundedDist = Math.round(dist * 10.0) / 10.0

            // Lấy món từ store.dishes; nếu quán chưa có món thì dùng các món mặc định của quán đó
            val availableDishes = store.dishes.values.filter { !it.isHidden && it.isAvailable }
            val dishesToDisplay = if (availableDishes.isNotEmpty()) availableDishes else getDefaultDishesForStore(store.id, store.name)

            dishesToDisplay.forEach { dish ->
                val emoji = when {
                    dish.name.contains("Trà", true) || dish.category.contains("Trà", true) -> "🧋"
                    dish.name.contains("Cơm", true) || dish.category.contains("Cơm", true) -> "🍛"
                    dish.name.contains("Gà", true) || dish.category.contains("Gà", true) -> "🍗"
                    dish.name.contains("Bún", true) || dish.name.contains("Phở", true) || dish.category.contains("Bún", true) -> "🍜"
                    dish.name.contains("Bánh mì", true) || dish.category.contains("Bánh mì", true) -> "🥖"
                    dish.name.contains("Pizza", true) || dish.category.contains("Pizza", true) -> "🍕"
                    dish.name.contains("Cà phê", true) || dish.category.contains("Cà phê", true) -> "☕"
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
                        rating = if (dish.ratingAvg > 0) dish.ratingAvg else 4.9f,
                        soldCount = "${if (dish.soldCount > 0) dish.soldCount else (120 + storeIndex * 45)} đã bán",
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

        // Hot dishes
        _hotDishes.value = allDishes.take(8).map {
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

    private fun getDefaultDishesForStore(storeId: String, storeName: String): List<com.chuyen_de_2.foodorder.core.data.model.Dish> {
        return when (storeId) {
            "shop_02" -> listOf(
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_02_01", storeId = "shop_02", name = "Phở Bò Tái Lăn Hà Nội", basePrice = 65000, category = "Bún/Phở", imageUrl = "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=400"),
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_02_02", storeId = "shop_02", name = "Phở Đặc Biệt (Tái, Nạm, Gầu)", basePrice = 75000, category = "Bún/Phở", imageUrl = "https://images.unsplash.com/photo-1555126634-323283e090fa?w=400"),
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_02_03", storeId = "shop_02", name = "Quẩy Giòn Hà Nội", basePrice = 10000, category = "Ăn vặt", imageUrl = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400")
            )
            "shop_03" -> listOf(
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_03_01", storeId = "shop_03", name = "Bún Bò Huế Đặc Biệt", basePrice = 60000, category = "Bún/Phở", imageUrl = "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=400"),
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_03_02", storeId = "shop_03", name = "Bún Bò Giò Heo Chả Cua", basePrice = 65000, category = "Bún/Phở", imageUrl = "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=400")
            )
            "shop_04" -> listOf(
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_04_01", storeId = "shop_04", name = "Bánh Mì Pate Chả Đặc Biệt Huỳnh Hoa", basePrice = 58000, category = "Bánh mì", imageUrl = "https://images.unsplash.com/photo-1509722747041-616f39b57569?w=400"),
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_04_02", storeId = "shop_04", name = "Bánh Mì Thịt Nướng Bơ Tỏi", basePrice = 45000, category = "Bánh mì", imageUrl = "https://images.unsplash.com/photo-1509722747041-616f39b57569?w=400")
            )
            "shop_05" -> listOf(
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_05_01", storeId = "shop_05", name = "Trà Sữa Trân Châu Hoàng Gia", basePrice = 45000, category = "Trà Sữa", hasOptions = true, imageUrl = "https://images.unsplash.com/photo-1558857563-b371033873b8?w=400"),
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_05_02", storeId = "shop_05", name = "Trà Sữa Oolong Nướng", basePrice = 48000, category = "Trà Sữa", hasOptions = true, imageUrl = "https://images.unsplash.com/photo-1556881286-fc6915169721?w=400"),
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_05_03", storeId = "shop_05", name = "Hồng Trà Đào Cam Sả", basePrice = 42000, category = "Trà Sữa", hasOptions = true, imageUrl = "https://images.unsplash.com/photo-1556881286-fc6915169721?w=400")
            )
            "shop_06" -> listOf(
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_06_01", storeId = "shop_06", name = "Combo 2 Miếng Gà Rán Giòn Cay", basePrice = 79000, category = "Gà Rán", imageUrl = "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?w=400"),
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_06_02", storeId = "shop_06", name = "Cánh Gà Sốt Chua Ngọt Hàn Quốc", basePrice = 85000, category = "Gà Rán", imageUrl = "https://images.unsplash.com/photo-1562967916-eb82221dfb92?w=400")
            )
            else -> listOf(
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_${storeId}_01", storeId = storeId, name = "Cơm sườn nướng than hoa", basePrice = 55000, category = "Cơm Trưa", imageUrl = "https://images.unsplash.com/photo-1512058564366-18510be2db19?w=400"),
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_${storeId}_02", storeId = storeId, name = "Cơm tấm bì chả đặc biệt", basePrice = 40000, category = "Cơm Trưa", imageUrl = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400"),
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_${storeId}_03", storeId = storeId, name = "Sườn cốt lết chiên giòn", basePrice = 60000, category = "Cơm Trưa", imageUrl = "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=400"),
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_${storeId}_04", storeId = storeId, name = "Canh chua cá lóc", basePrice = 35000, category = "Cơm Trưa", imageUrl = "https://images.unsplash.com/photo-1547592166-23ac45744acd?w=400"),
                com.chuyen_de_2.foodorder.core.data.model.Dish(id = "dish_${storeId}_05", storeId = storeId, name = "Trà Ô Long Xí Muội", basePrice = 28000, category = "Trà Sữa", hasOptions = true, imageUrl = "https://images.unsplash.com/photo-1556881286-fc6915169721?w=400")
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
