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

    enum class HotTab {
        BEST_SELLER,
        FAVORITE
    }

    private val _currentHotTab = MutableLiveData(HotTab.BEST_SELLER)
    val currentHotTab: LiveData<HotTab> = _currentHotTab

    private var cachedOrders = listOf<com.chuyen_de_2.foodorder.core.data.model.Order>()
    private var topBestSellerDishes = listOf<HotDish>()
    private var topFavoriteDishes = listOf<HotDish>()

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
    var currentNearbyMaxDistanceKm: Double = 1.5
        private set
    var currentMinPrice: Long? = null
        private set
    var currentMaxPrice: Long? = null
        private set
    var currentMinRating: Float = 1.0f
        private set
    var currentMaxRating: Float = 5.0f
        private set
    private var currentPriceSort: PriceSort = PriceSort.NONE
    private var currentUserLatLng: com.google.android.gms.maps.model.LatLng =
        com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.DEFAULT_HCMC_LATLNG

    fun getUserLocation(): com.google.android.gms.maps.model.LatLng = currentUserLatLng

    fun setUserLocation(lat: Double, lng: Double) {
        if (lat != 0.0 && lng != 0.0) {
            currentUserLatLng = com.google.android.gms.maps.model.LatLng(lat, lng)
            _stores.value?.let { processStoresAndDishes(it) }
        }
    }

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
            // Lắng nghe đơn hàng để tính toán món bán chạy trong tuần
            firebaseRepository.observeAllOrders().collectLatest { orders ->
                cachedOrders = orders
                _stores.value?.let { recalculateHotDishes(it) }
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

        // 1. Quán gần đây (Sắp xếp theo thứ tự khoảng cách từ gần đến xa)
        val nearbyList = activeStores.map { store ->
            val storeLatLng = resolveStoreLatLng(store)
            val roundedDist = calculateDistanceForStore(storeLatLng)
            val eta = if (store.prepTime.isNotBlank()) store.prepTime else if (roundedDist > 0.0) {
                val baseTime = (10 + (roundedDist * 4).toInt()).coerceAtLeast(15)
                "$baseTime-${baseTime + 10} phút"
            } else "15-25 phút"
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
                eta = eta,
                rating = store.ratingAvg,
                imageUrl = store.imageUrl,
                emoji = emoji,
                originalStore = store
            )
        }

        val sortedNearbyList = nearbyList.sortedWith(
            compareBy<NearbyStoreItem> { if (it.distanceKm > 0.0) 0 else 1 }
                .thenBy { it.distanceKm }
        )
        _nearbyStores.value = sortedNearbyList

        // 2. Thu thập các món ăn của quán
        val allDishes = mutableListOf<HomeDishItem>()

        activeStores.forEach { store ->
            val storeLatLng = resolveStoreLatLng(store)
            val roundedDist = calculateDistanceForStore(storeLatLng)

            val availableDishes = store.dishes.values.filter { !it.isHidden && it.isAvailable }

            availableDishes.forEach { dish ->
                val platformCat = dish.platformCategory.ifBlank {
                    inferPlatformCategory(dish.name, dish.category)
                }
                val emoji = when {
                    platformCat.contains("Trà", true) || platformCat.contains("Nước", true) || dish.name.contains("Trà", true) || dish.category.contains("Trà", true) -> "🧋"
                    platformCat.contains("Cơm", true) || dish.name.contains("Cơm", true) || dish.category.contains("Cơm", true) -> "🍚"
                    platformCat.contains("Gà", true) || dish.name.contains("Gà", true) || dish.category.contains("Gà", true) -> "🍗"
                    platformCat.contains("Bún", true) || platformCat.contains("Phở", true) || dish.name.contains("Bún", true) || dish.name.contains("Phở", true) || dish.category.contains("Bún", true) -> "🍜"
                    platformCat.contains("Bánh mì", true) || dish.name.contains("Bánh mì", true) || dish.category.contains("Bánh mì", true) -> "🥖"
                    platformCat.contains("Pizza", true) || dish.name.contains("Pizza", true) || dish.category.contains("Pizza", true) -> "🍕"
                    platformCat.contains("Cà phê", true) || dish.name.contains("Cà phê", true) || dish.category.contains("Cà phê", true) -> "☕"
                    platformCat.contains("Chay", true) -> "🥗"
                    platformCat.contains("Hải sản", true) -> "🦐"
                    platformCat.contains("Ăn vặt", true) -> "🍟"
                    platformCat.contains("Tráng miệng", true) -> "🍰"
                    platformCat.contains("Lẩu", true) -> "🍲"
                    else -> "🍽️"
                }
                allDishes.add(
                    HomeDishItem(
                        id = dish.id,
                        name = dish.name,
                        price = dish.basePrice,
                        originalPrice = dish.basePrice,
                        storeId = store.id,
                        storeName = store.name,
                        imageUrl = dish.imageUrl.ifBlank { store.imageUrl },
                        emoji = emoji,
                        rating = if (dish.ratingAvg > 0f) dish.ratingAvg else if (store.ratingAvg > 0.0) store.ratingAvg.toFloat() else 4.8f,
                        soldCount = if (dish.soldCount > 0) "${dish.soldCount} đã bán" else "",
                        distanceKm = roundedDist,
                        promoBadge = "",
                        tag1 = platformCat.ifBlank { dish.category.ifBlank { "Món của quán" } },
                        tag2 = if (roundedDist in 0.1..3.0) "Gần bạn" else "",
                        category = dish.category.ifBlank { store.category },
                        platformCategory = platformCat
                    )
                )
            }
        }

        masterDishList = allDishes
        applyFilters()

        // Cập nhật danh sách món hot theo tab (Top Bán Chạy / Top Yêu Thích - 1 món mỗi quán)
        recalculateHotDishes(activeStores)
    }

    private fun recalculateHotDishes(storeList: List<Store>) {
        val activeStores = storeList.filter { store ->
            val st = store.storeStatus.uppercase()
            val pt = store.penaltyTier.uppercase()
            st != Store.StoreStatus.LOCKED &&
            pt != Store.PenaltyTier.LOCK &&
            pt != Store.PenaltyTier.HIDE
        }

        // Tính lượt bán trong 7 ngày gần nhất từ các đơn hàng
        val oneWeekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val weeklyDishSales = mutableMapOf<String, Int>()
        cachedOrders.filter { order ->
            val ts = order.effectiveTimestamp
            ts >= oneWeekAgo &&
            order.effectiveStatus != com.chuyen_de_2.foodorder.core.data.model.Order.Status.CANCELLED &&
            order.effectiveStatus != com.chuyen_de_2.foodorder.core.data.model.Order.Status.BOOM
        }.forEach { order ->
            order.items.values.forEach { item ->
                if (item.dishId.isNotBlank()) {
                    weeklyDishSales[item.dishId] = (weeklyDishSales[item.dishId] ?: 0) + item.quantity
                }
            }
        }

        // 1. Top Bán Chạy: Mỗi quán chỉ lên 1 món có lượt bán cao nhất trong tuần
        val bestSellerList = mutableListOf<Pair<HotDish, Long>>()
        activeStores.forEach { store ->
            val availableDishes = store.dishes.values.filter { !it.isHidden && it.isAvailable }
            if (availableDishes.isNotEmpty()) {
                val bestDish = availableDishes.maxWithOrNull(
                    compareBy({ weeklyDishSales[it.id] ?: 0 }, { it.soldCount })
                )
                if (bestDish != null) {
                    val weekCount = weeklyDishSales[bestDish.id] ?: 0
                    val totalCount = bestDish.soldCount
                    val salesScore = weekCount.toLong() * 10000L + totalCount.toLong()
                    val badgeText = when {
                        weekCount > 0 -> "🔥 $weekCount bán/tuần"
                        totalCount > 0 -> "🔥 $totalCount đã bán"
                        else -> "🔥 Bán chạy"
                    }
                    val hotDish = HotDish(
                        id = bestDish.id,
                        name = bestDish.name,
                        price = bestDish.basePrice,
                        storeId = store.id,
                        storeName = store.name,
                        imageUrl = bestDish.imageUrl.ifBlank { store.imageUrl },
                        emoji = inferDishEmoji(bestDish.name, bestDish.category, bestDish.platformCategory),
                        badgeText = badgeText
                    )
                    bestSellerList.add(Pair(hotDish, salesScore))
                }
            }
        }
        topBestSellerDishes = bestSellerList
            .sortedByDescending { it.second }
            .map { it.first }

        // 2. Top Yêu Thích: Mỗi quán chỉ lên 1 món có đánh giá cao nhất
        val favoriteList = mutableListOf<Pair<HotDish, Float>>()
        activeStores.forEach { store ->
            val availableDishes = store.dishes.values.filter { !it.isHidden && it.isAvailable }
            if (availableDishes.isNotEmpty()) {
                val topRatedDish = availableDishes.maxWithOrNull(
                    compareBy(
                        { if (it.ratingAvg > 0f) it.ratingAvg else if (store.ratingAvg > 0.0) store.ratingAvg.toFloat() else 4.5f },
                        { it.reviewCount },
                        { it.soldCount }
                    )
                )
                if (topRatedDish != null) {
                    val rating = if (topRatedDish.ratingAvg > 0f) topRatedDish.ratingAvg
                        else if (store.ratingAvg > 0.0) store.ratingAvg.toFloat()
                        else 5.0f
                    val formattedRating = String.format(java.util.Locale.US, "%.1f", rating)
                    val badgeText = "⭐ $formattedRating"
                    val hotDish = HotDish(
                        id = topRatedDish.id,
                        name = topRatedDish.name,
                        price = topRatedDish.basePrice,
                        storeId = store.id,
                        storeName = store.name,
                        imageUrl = topRatedDish.imageUrl.ifBlank { store.imageUrl },
                        emoji = inferDishEmoji(topRatedDish.name, topRatedDish.category, topRatedDish.platformCategory),
                        badgeText = badgeText
                    )
                    favoriteList.add(Pair(hotDish, rating))
                }
            }
        }
        topFavoriteDishes = favoriteList
            .sortedByDescending { it.second }
            .map { it.first }

        _hotDishes.value = if (_currentHotTab.value == HotTab.BEST_SELLER) topBestSellerDishes else topFavoriteDishes
    }

    fun switchHotTab(tab: HotTab) {
        if (_currentHotTab.value == tab) return
        _currentHotTab.value = tab
        _hotDishes.value = if (tab == HotTab.BEST_SELLER) topBestSellerDishes else topFavoriteDishes
    }

    private fun inferDishEmoji(dishName: String, category: String, platformCat: String): String {
        val cat = platformCat.ifBlank { inferPlatformCategory(dishName, category) }
        return when {
            cat.contains("Trà sữa", true) || dishName.contains("Trà sữa", true) -> "🧋"
            cat.contains("Nước ép", true) || dishName.contains("Nước ép", true) || dishName.contains("Sinh tố", true) || cat.contains("Nước", true) -> "🍹"
            cat.contains("Cơm", true) || dishName.contains("Cơm", true) -> "🍚"
            cat.contains("Bún đậu", true) || dishName.contains("Bún đậu", true) -> "🥢"
            cat.contains("Phở", true) || dishName.contains("Phở", true) || cat.contains("Bún", true) -> "🍜"
            else -> "🍽️"
        }
    }

    enum class PriceSort {
        NONE, ASC, DESC
    }

    /** Lọc danh sách món ăn */
    fun filterDishes(
        query: String? = null,
        category: String? = null,
        nearbyOnly: Boolean? = null,
        maxDistanceKm: Double? = null,
        minPrice: Long? = null,
        maxPrice: Long? = null,
        minRating: Float? = null,
        maxRating: Float? = null,
        resetPrice: Boolean = false,
        resetRating: Boolean = false,
        priceSort: PriceSort? = null
    ) {
        if (query != null) currentQuery = query
        if (category != null) currentCategory = if (category == "ALL") null else category
        if (nearbyOnly != null) currentNearbyOnly = nearbyOnly
        if (maxDistanceKm != null) currentNearbyMaxDistanceKm = maxDistanceKm
        if (resetPrice) {
            currentMinPrice = null
            currentMaxPrice = null
            currentPriceSort = PriceSort.NONE
        } else {
            if (minPrice != null || maxPrice != null) {
                currentMinPrice = if (minPrice != null && minPrice > 0L) minPrice else null
                currentMaxPrice = if (maxPrice != null && maxPrice > 0L) maxPrice else null
            }
            if (priceSort != null) {
                currentPriceSort = priceSort
            }
        }
        if (resetRating) {
            currentMinRating = 1.0f
            currentMaxRating = 5.0f
        } else {
            if (minRating != null) currentMinRating = minRating
            if (maxRating != null) currentMaxRating = maxRating
        }
        applyFilters()
    }

    /** Tính số lượng món phù hợp với bộ lọc trước khi bấm Áp Dụng (phục vụ Bottom Sheet) */
    fun getFilteredDishesCount(
        minPrice: Long?,
        maxPrice: Long?,
        minRating: Float,
        maxRating: Float
    ): Int {
        var list = masterDishList

        val cat = currentCategory
        if (!cat.isNullOrBlank()) {
            list = list.filter { matchesCategory(it, cat) }
        }

        if (currentNearbyOnly && currentNearbyMaxDistanceKm < 999.0) {
            list = list.filter { it.distanceKm in 0.001..currentNearbyMaxDistanceKm }
        }

        val q = currentQuery?.trim()
        if (!q.isNullOrBlank()) {
            list = list.filter { matchesSearch(it, q) }
        }

        if (minPrice != null && minPrice > 0) {
            list = list.filter { it.price >= minPrice }
        }
        if (maxPrice != null && maxPrice > 0) {
            list = list.filter { it.price <= maxPrice }
        }

        if (minRating > 1.05f || maxRating < 4.95f) {
            list = list.filter { it.rating in (minRating - 0.05f)..(maxRating + 0.05f) }
        }

        return list.size
    }

    /**
     * Khớp tìm kiếm thông minh: Hỗ trợ tiếng Việt có dấu, không dấu và tìm đa từ khóa (token search)
     */
    private fun matchesSearch(dish: HomeDishItem, query: String): Boolean {
        val qClean = query.trim().lowercase()
        if (qClean.isBlank()) return true

        val qNoAccent = com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.removeAccent(qClean)

        val name = dish.name.lowercase()
        val store = dish.storeName.lowercase()
        val cat = dish.category.lowercase()
        val platCat = dish.platformCategory.lowercase()

        val fullWithAccent = "$name $store $cat $platCat"
        val fullNoAccent = com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.removeAccent(fullWithAccent)

        // 1. Khớp cụm từ trực tiếp (có dấu hoặc không dấu)
        if (fullWithAccent.contains(qClean) || fullNoAccent.contains(qNoAccent)) {
            return true
        }

        // 2. Khớp tất cả các từ khóa nhập vào (token search - không phụ thuộc thứ tự từ)
        val tokens = qNoAccent.split("\\s+".toRegex()).filter { it.length > 1 }
        if (tokens.isNotEmpty() && tokens.all { token -> fullNoAccent.contains(token) }) {
            return true
        }

        return false
    }

    private fun applyFilters() {
        var filtered = masterDishList

        // 1. Lọc theo cự ly gần
        if (currentNearbyOnly) {
            val maxDist = currentNearbyMaxDistanceKm
            filtered = if (maxDist >= 999.0) {
                filtered.sortedBy { if (it.distanceKm > 0.0) it.distanceKm else 999.0 }
            } else {
                val nearbyDishes = filtered.filter { it.distanceKm in 0.001..maxDist }
                if (nearbyDishes.isNotEmpty()) {
                    nearbyDishes.sortedBy { it.distanceKm }
                } else {
                    filtered.sortedBy { if (it.distanceKm > 0.0) it.distanceKm else 999.0 }
                }
            }
        }

        // 2. Lọc theo danh mục
        val cat = currentCategory
        if (!cat.isNullOrBlank()) {
            filtered = filtered.filter { dish ->
                matchesCategory(dish, cat)
            }
        }

        // 3. Lọc theo từ khóa tìm kiếm (thông minh, không dấu, đa từ)
        val q = currentQuery?.trim()
        if (!q.isNullOrBlank()) {
            filtered = filtered.filter { dish ->
                matchesSearch(dish, q)
            }
        }

        // 4. Lọc theo khoảng giá tiền
        val minP = currentMinPrice
        val maxP = currentMaxPrice
        if (minP != null || maxP != null) {
            filtered = filtered.filter { dish ->
                val p = dish.price
                (minP == null || p >= minP) && (maxP == null || p <= maxP)
            }
        }

        // 5. Lọc theo khoảng sao đánh giá
        val minR = currentMinRating
        val maxR = currentMaxRating
        if (minR > 1.05f || maxR < 4.95f) {
            filtered = filtered.filter { dish ->
                dish.rating in (minR - 0.05f)..(maxR + 0.05f)
            }
        }

        // 5. Sắp xếp theo giá tiền nếu có
        when (currentPriceSort) {
            PriceSort.ASC -> filtered = filtered.sortedBy { it.price }
            PriceSort.DESC -> filtered = filtered.sortedByDescending { it.price }
            PriceSort.NONE -> { /* Giữ nguyên thứ tự */ }
        }

        _homeDishes.value = filtered
    }

    private fun matchesCategory(dish: HomeDishItem, filterCat: String): Boolean {
        val target = filterCat.trim().lowercase()
        val pCat = dish.platformCategory.lowercase()
        val sCat = dish.category.lowercase()
        val name = dish.name.lowercase()
        val store = dish.storeName.lowercase()
        val combined = "$name $pCat $sCat $store"

        if (pCat.isNotBlank() && (pCat.contains(target) || target.contains(pCat))) return true
        if (sCat.isNotBlank() && (sCat.contains(target) || target.contains(sCat))) return true
        if (name.contains(target)) return true

        if (target.contains("cơm")) {
            return combined.contains("cơm") || combined.contains("com")
        }
        if (target.contains("bún đậu")) {
            return combined.contains("bún đậu") || combined.contains("bun dau") || (combined.contains("bún") && combined.contains("đậu"))
        }
        if (target.contains("phở")) {
            return combined.contains("phở") || combined.contains("pho") || (combined.contains("bún") && !combined.contains("đậu")) || combined.contains("hủ tiếu") || combined.contains("mì")
        }
        if (target.contains("trà sữa")) {
            return combined.contains("trà sữa") || combined.contains("tra sua") || combined.contains("trân châu") || combined.contains("milktea") || (combined.contains("trà") && !combined.contains("nước ép"))
        }
        if (target.contains("nước ép")) {
            return combined.contains("nước ép") || combined.contains("nuoc ep") || combined.contains("sinh tố") || combined.contains("sinh to") || combined.contains("juice") || combined.contains("nước cam") || combined.contains("nước mía") || combined.contains("nước dừa") || combined.contains("detox") || combined.contains("ép")
        }

        val keywords = target.split(" ", "-", "/", "&", ",").map { it.trim() }.filter { it.length > 1 }
        return keywords.any { kw -> combined.contains(kw) }
    }

    private fun inferPlatformCategory(name: String, category: String): String {
        val text = "$name $category".lowercase()
        return when {
            text.contains("cơm") -> "Cơm"
            text.contains("bún đậu") || (text.contains("bún") && text.contains("đậu")) -> "Bún đậu"
            text.contains("phở") || text.contains("bún") || text.contains("hủ tiếu") || text.contains("mì") -> "Phở"
            text.contains("trà sữa") || text.contains("tra sua") || text.contains("trân châu") -> "Trà sữa"
            text.contains("nước ép") || text.contains("nuoc ep") || text.contains("sinh tố") || text.contains("nước") || text.contains("ép") -> "Nước ép"
            text.contains("trà") -> "Trà sữa"
            else -> "Cơm"
        }
    }

    private fun resolveStoreLatLng(store: Store): com.google.android.gms.maps.model.LatLng? {
        if (store.latitude != 0.0 && store.longitude != 0.0) {
            return com.google.android.gms.maps.model.LatLng(store.latitude, store.longitude)
        }
        val distName = store.district.trim()
        if (distName.isNotBlank()) {
            val exact = com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.DISTRICT_COORDINATES[distName]
            if (exact != null) return exact
            val fuzzy = com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.DISTRICT_COORDINATES.entries
                .find { distName.contains(it.key, ignoreCase = true) || it.key.contains(distName, ignoreCase = true) }?.value
            if (fuzzy != null) return fuzzy
        }
        return null
    }

    private fun calculateDistanceForStore(storeLatLng: com.google.android.gms.maps.model.LatLng?): Double {
        if (storeLatLng == null) return 0.0
        val dist = com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.calculateDistanceKm(
            currentUserLatLng,
            storeLatLng
        ).toDouble()
        return Math.round(dist * 10.0) / 10.0
    }

    fun loadRestaurants() = loadStores()
}
