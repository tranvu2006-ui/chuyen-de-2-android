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

    // Danh sách danh mục nền tảng thời gian thực từ Firebase
    private val _platformCategories = MutableLiveData<List<com.chuyen_de_2.foodorder.core.data.model.Category>>(emptyList())
    val platformCategories: LiveData<List<com.chuyen_de_2.foodorder.core.data.model.Category>> = _platformCategories

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

        viewModelScope.launch {
            // Lắng nghe danh mục nền tảng theo thời gian thực từ Firebase (quản lý bởi Admin)
            firebaseRepository.observeCategories().collectLatest { cats ->
                _platformCategories.value = cats
            }
        }
    }

    private fun processStoresAndDishes(storeList: List<Store>) {
        // Chỉ loại bỏ quán bị Admin khóa do vi phạm (LOCKED / LOCK / HIDE)
        val activeStores = storeList.filter { store ->
            val st = store.storeStatus.uppercase()
            val pt = store.penaltyTier.uppercase()
            st != Store.StoreStatus.LOCKED &&
            pt != Store.PenaltyTier.LOCK &&
            pt != Store.PenaltyTier.HIDE
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
            val dishesToDisplay = availableDishes

            dishesToDisplay.forEach { dish ->
                val platformCat = dish.platformCategory.ifBlank {
                    inferPlatformCategory(dish.name, dish.category)
                }
                val emoji = when {
                    platformCat.contains("Trà", true) || dish.name.contains("Trà", true) || dish.category.contains("Trà", true) -> "🧋"
                    platformCat.contains("Cơm", true) || dish.name.contains("Cơm", true) || dish.category.contains("Cơm", true) -> "🍛"
                    platformCat.contains("Gà", true) || dish.name.contains("Gà", true) || dish.category.contains("Gà", true) -> "🍗"
                    platformCat.contains("Bún", true) || dish.name.contains("Bún", true) || dish.name.contains("Phở", true) || dish.category.contains("Bún", true) -> "🍜"
                    platformCat.contains("Bánh mì", true) || dish.name.contains("Bánh mì", true) || dish.category.contains("Bánh mì", true) -> "🥖"
                    platformCat.contains("Pizza", true) || dish.name.contains("Pizza", true) || dish.category.contains("Pizza", true) -> "🍕"
                    platformCat.contains("Cà phê", true) || dish.name.contains("Cà phê", true) || dish.category.contains("Cà phê", true) -> "☕"
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
                        soldCount = "${dish.soldCount} đã bán",
                        distanceKm = roundedDist,
                        promoBadge = if (dish.basePrice >= 50000) "-15%" else "Món ngon",
                        tag1 = platformCat.ifBlank { dish.category.ifBlank { "Món của quán" } },
                        tag2 = "Giao Nhanh",
                        category = dish.category.ifBlank { store.category },
                        platformCategory = platformCat
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
                matchesCategory(dish, cat)
            }
        }

        // 3. Lọc theo từ khóa tìm kiếm
        val q = currentQuery?.trim()?.lowercase()
        if (!q.isNullOrBlank()) {
            filtered = filtered.filter { dish ->
                dish.name.lowercase().contains(q) ||
                dish.storeName.lowercase().contains(q) ||
                dish.category.lowercase().contains(q) ||
                dish.platformCategory.lowercase().contains(q)
            }
        }

        _homeDishes.value = filtered
    }

    private fun matchesCategory(dish: HomeDishItem, filterCat: String): Boolean {
        val target = filterCat.trim().lowercase()
        val pCat = dish.platformCategory.lowercase()
        val sCat = dish.category.lowercase()
        val name = dish.name.lowercase()

        return when (target) {
            "cơm", "cơm trưa" -> pCat.contains("cơm") || sCat.contains("cơm") || name.contains("cơm")
            "trà", "trà sữa" -> pCat.contains("trà") || sCat.contains("trà") || name.contains("trà") || pCat.contains("nước") || sCat.contains("nước")
            "bún", "bún/phở", "phở" -> pCat.contains("bún") || pCat.contains("phở") || sCat.contains("bún") || sCat.contains("phở") || name.contains("bún") || name.contains("phở") || name.contains("mì")
            "gà", "gà rán" -> pCat.contains("gà") || sCat.contains("gà") || name.contains("gà")
            "bánh mì" -> pCat.contains("bánh mì") || sCat.contains("bánh mì") || name.contains("bánh mì")
            "pizza" -> pCat.contains("pizza") || sCat.contains("pizza") || name.contains("pizza")
            "cà phê", "cafe" -> pCat.contains("cà phê") || pCat.contains("cafe") || sCat.contains("cà phê") || sCat.contains("cafe") || name.contains("cà phê") || name.contains("cafe") || name.contains("coffee")
            "tráng miệng", "ăn vặt" -> pCat.contains("tráng miệng") || pCat.contains("ăn vặt") || sCat.contains("tráng miệng") || sCat.contains("ăn vặt") || name.contains("chè") || name.contains("kem") || name.contains("bánh") || name.contains("tráng miệng")
            else -> pCat.contains(target) || sCat.contains(target) || name.contains(target)
        }
    }

    private fun inferPlatformCategory(name: String, category: String): String {
        val text = "$name $category".lowercase()
        return when {
            text.contains("cơm") -> "Cơm Trưa"
            text.contains("trà") -> "Trà Sữa"
            text.contains("bún") || text.contains("phở") || text.contains("hủ tiếu") || text.contains("mì") -> "Bún/Phở"
            text.contains("gà") -> "Gà Rán"
            text.contains("bánh mì") -> "Bánh Mì"
            text.contains("pizza") -> "Pizza"
            text.contains("cà phê") || text.contains("cafe") || text.contains("coffee") -> "Cà Phê"
            text.contains("lẩu") -> "Lẩu / Món Nước"
            text.contains("chay") -> "Đồ Chay"
            text.contains("chè") || text.contains("kem") || text.contains("bánh") || text.contains("ăn vặt") || text.contains("tráng miệng") -> "Tráng Miệng / Ăn Vặt"
            else -> "Món Khác"
        }
    }

    fun loadRestaurants() = loadStores()
}
