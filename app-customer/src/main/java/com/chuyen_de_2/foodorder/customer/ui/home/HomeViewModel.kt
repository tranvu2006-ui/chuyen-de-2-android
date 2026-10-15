package com.chuyen_de_2.foodorder.customer.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.data.remote.RestBackendApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: RestBackendApi,
    private val firebaseRepository: FirebaseRepository
) : ViewModel() {

    private val _stores = MutableLiveData<List<Store>>()
    val stores: LiveData<List<Store>> = _stores
    val restaurants: LiveData<List<Store>> get() = _stores

    // Danh sách món ăn chính hiển thị trên trang chủ (theo chuẩn b1.html)
    private val _homeDishes = MutableLiveData<List<HomeDishItem>>()
    val homeDishes: LiveData<List<HomeDishItem>> = _homeDishes

    // Danh sách quán ăn gần đây hiển thị trong Bottom Sheet Modal
    private val _nearbyStores = MutableLiveData<List<NearbyStoreItem>>()
    val nearbyStores: LiveData<List<NearbyStoreItem>> = _nearbyStores

    private val _hotDishes = MutableLiveData<List<HotDish>>()
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
            // Lắng nghe danh sách quán ăn theo thời gian thực từ Firebase Realtime Database
            firebaseRepository.observeAllStores().collectLatest { firebaseStores ->
                if (firebaseStores.isNotEmpty()) {
                    _stores.value = firebaseStores
                    processStoresAndDishes(firebaseStores)
                    _isLoading.value = false
                } else {
                    // Dự phòng nếu Firebase chưa có quán nào thì lấy từ Mock REST API
                    try {
                        val response = api.getStores()
                        if (response.isSuccessful) {
                            val storeList = response.body() ?: emptyList()
                            _stores.value = storeList
                            processStoresAndDishes(storeList)
                        } else {
                            _error.value = "Lỗi tải dữ liệu: ${response.code()}"
                        }
                    } catch (e: Exception) {
                        _error.value = e.message ?: "Lỗi kết nối"
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    private fun processStoresAndDishes(storeList: List<Store>) {
        // 1. Tạo danh sách Quán Gần Đây cho Bottom Sheet (b1.html NEARBY_STORES_DATA)
        val nearbyList = mutableListOf<NearbyStoreItem>()
        storeList.forEachIndexed { index, store ->
            val dist = 0.8 + (index * 0.4)
            val roundedDist = Math.round(dist * 10.0) / 10.0
            val etaMinutes = 12 + (index * 3)
            val emoji = when {
                store.name.contains("Trà", true) || store.name.contains("Phúc Long", true) -> "🍵"
                store.name.contains("Cơm", true) -> "🍛"
                store.name.contains("Gà", true) -> "🍗"
                store.name.contains("Bún", true) || store.name.contains("Phở", true) -> "🍜"
                else -> "🍲"
            }
            nearbyList.add(
                NearbyStoreItem(
                    id = store.id,
                    name = store.name,
                    address = store.address.ifBlank { "Quận 1, TP.HCM" },
                    distanceKm = roundedDist,
                    eta = "$etaMinutes-${etaMinutes + 4} phút",
                    rating = if (store.ratingAvg > 0) store.ratingAvg else 4.8f,
                    imageUrl = store.imageUrl,
                    emoji = emoji,
                    originalStore = store
                )
            )
        }
        _nearbyStores.value = nearbyList

        // 2. Thu thập danh sách Món Ăn chính cho trang chủ
        val allDishes = mutableListOf<HomeDishItem>()

        // 2.1 Lấy các món thực tế từ Firebase con của từng store
        storeList.forEachIndexed { storeIndex, store ->
            val dist = 0.8 + (storeIndex * 0.4)
            val roundedDist = Math.round(dist * 10.0) / 10.0

            store.dishes.values.forEach { dish ->
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
                        originalPrice = (dish.basePrice * 1.3).toLong(),
                        storeId = store.id,
                        storeName = store.name,
                        imageUrl = dish.imageUrl.ifBlank { store.imageUrl },
                        emoji = emoji,
                        rating = dish.ratingAvg,
                        soldCount = "${dish.soldCount.coerceAtLeast(120)} đã bán",
                        distanceKm = roundedDist,
                        promoBadge = if (dish.basePrice >= 50000) "-25%" else "Bán chạy",
                        tag1 = "Bán Chạy Nhất",
                        tag2 = "Freeship Extra",
                        category = dish.category.ifBlank { store.category }
                    )
                )
            }
        }

        // 2.2 Bổ sung danh sách 8 món ăn chuẩn nhất từ b1.html nếu số lượng món còn ít
        val sampleB1Dishes = listOf(
            HomeDishItem(
                id = "b1_01",
                name = "Hồng Trà Sữa Trân Châu Hoàng Kim",
                price = 28000L,
                originalPrice = 56000L,
                storeId = storeList.firstOrNull { it.name.contains("Trà", true) }?.id ?: storeList.firstOrNull()?.id ?: "shop_01",
                storeName = "Hồng Trà Ngô Gia - 28 Tô Vĩnh Diện",
                emoji = "🧋",
                rating = 4.8f,
                soldCount = "1.2k đã bán",
                distanceKm = 1.2,
                promoBadge = "-50%",
                tag1 = "Bán Chạy Nhất",
                tag2 = "Freeship Extra",
                category = "Trà Sữa"
            ),
            HomeDishItem(
                id = "b1_02",
                name = "Trà Xí Muội Ô Long Ngô Gia",
                price = 32000L,
                originalPrice = 42000L,
                storeId = storeList.firstOrNull { it.name.contains("Trà", true) }?.id ?: storeList.firstOrNull()?.id ?: "shop_01",
                storeName = "Hồng Trà Ngô Gia - 28 Tô Vĩnh Diện",
                emoji = "🍹",
                rating = 4.8f,
                soldCount = "850 đã bán",
                distanceKm = 1.2,
                promoBadge = "-25%",
                tag1 = "Vị Chua Ngọt Đậm Đà",
                tag2 = "Món Mới",
                category = "Trà Sữa"
            ),
            HomeDishItem(
                id = "b1_03",
                name = "Cơm Tấm Sườn Cốt Lết Ba Ghiền",
                price = 55000L,
                originalPrice = 65000L,
                storeId = storeList.firstOrNull { it.name.contains("Cơm", true) }?.id ?: storeList.getOrNull(1)?.id ?: "shop_02",
                storeName = "Cơm Tấm Sườn Ba Ghiền",
                emoji = "🍛",
                rating = 4.8f,
                soldCount = "2.4k đã bán",
                distanceKm = 2.5,
                promoBadge = "Giảm 10k",
                tag1 = "Sườn Nướng Than",
                tag2 = "Món Nóng Hổi",
                category = "Cơm Trưa"
            ),
            HomeDishItem(
                id = "b1_04",
                name = "Cơm Tấm Đặc Biệt (Sườn Bì Chả Trứng)",
                price = 75000L,
                originalPrice = 85000L,
                storeId = storeList.firstOrNull { it.name.contains("Cơm", true) }?.id ?: storeList.getOrNull(1)?.id ?: "shop_02",
                storeName = "Cơm Tấm Sườn Ba Ghiền",
                emoji = "🍱",
                rating = 4.8f,
                soldCount = "1.8k đã bán",
                distanceKm = 2.5,
                promoBadge = "Tặng Canh",
                tag1 = "Phần Ăn Đại Bổ",
                tag2 = "Bán Chạy Nhất",
                category = "Cơm Trưa"
            ),
            HomeDishItem(
                id = "b1_05",
                name = "Trà Ô Long Hạt Sen Phúc Long",
                price = 50000L,
                originalPrice = 55000L,
                storeId = storeList.firstOrNull { it.name.contains("Phúc Long", true) }?.id ?: storeList.getOrNull(2)?.id ?: "shop_03",
                storeName = "Phúc Long Tea & Coffee - Huỳnh Thúc Kháng",
                emoji = "🍵",
                rating = 4.9f,
                soldCount = "3.1k đã bán",
                distanceKm = 0.8,
                promoBadge = "Freeship",
                tag1 = "Signature 5 Sao",
                tag2 = "Hạt Sen Bùi",
                category = "Trà Sữa"
            ),
            HomeDishItem(
                id = "b1_06",
                name = "Trà Đào Cam Sả Đặc Biệt",
                price = 55000L,
                originalPrice = 60000L,
                storeId = storeList.firstOrNull { it.name.contains("Phúc Long", true) }?.id ?: storeList.getOrNull(2)?.id ?: "shop_03",
                storeName = "Phúc Long Tea & Coffee - Huỳnh Thúc Kháng",
                emoji = "🍑",
                rating = 4.9f,
                soldCount = "2.7k đã bán",
                distanceKm = 0.8,
                promoBadge = "Freeship",
                tag1 = "Đào Miếng Giòn",
                tag2 = "Uống Là Ghiền",
                category = "Trà Sữa"
            ),
            HomeDishItem(
                id = "b1_07",
                name = "Bún Bò Huế Bắp Bò Nạm Chả Cua",
                price = 65000L,
                originalPrice = 80000L,
                storeId = storeList.firstOrNull { it.name.contains("Bún", true) || it.name.contains("Huế", true) }?.id ?: storeList.getOrNull(3)?.id ?: "shop_04",
                storeName = "Bún Bò Huế Đông Ba - Nguyễn Du",
                emoji = "🍜",
                rating = 4.7f,
                soldCount = "940 đã bán",
                distanceKm = 1.5,
                promoBadge = "Giảm 15k",
                tag1 = "Nước Dùng Đậm Đà",
                tag2 = "Gia Truyền",
                category = "Bún/Phở"
            ),
            HomeDishItem(
                id = "b1_08",
                name = "Combo Gà Rán Giòn Cay Popeyes",
                price = 79000L,
                originalPrice = 99000L,
                storeId = storeList.firstOrNull { it.name.contains("Gà", true) }?.id ?: storeList.getOrNull(4)?.id ?: "shop_05",
                storeName = "Gà Rán Giòn Cay Popeyes - Hai Bà Trưng",
                emoji = "🍗",
                rating = 4.6f,
                soldCount = "720 đã bán",
                distanceKm = 1.6,
                promoBadge = "-20%",
                tag1 = "Combo Tiết Kiệm",
                tag2 = "Giòn Rụm",
                category = "Gà Rán"
            )
        )

        // Ghép các món thực tế lên đầu, bổ sung món mẫu b1 nếu chưa trùng tên
        val existingNames = allDishes.map { it.name.trim().lowercase() }.toSet()
        sampleB1Dishes.forEach { sample ->
            if (!existingNames.contains(sample.name.trim().lowercase())) {
                allDishes.add(sample)
            }
        }

        masterDishList = allDishes
        applyFilters()

        // Đồng bộ hot dishes
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
                dish.name.contains(cat, ignoreCase = true) ||
                (cat.contains("Cơm", true) && (dish.name.contains("Cơm", true) || dish.category.contains("Cơm", true))) ||
                (cat.contains("Trà", true) && (dish.name.contains("Trà", true) || dish.category.contains("Trà", true))) ||
                (cat.contains("Bún", true) && (dish.name.contains("Bún", true) || dish.name.contains("Phở", true))) ||
                (cat.contains("Gà", true) && dish.name.contains("Gà", true)) ||
                (cat.contains("Bánh mì", true) && dish.name.contains("Bánh mì", true)) ||
                (cat.contains("Pizza", true) && dish.name.contains("Pizza", true)) ||
                (cat.contains("Cà phê", true) && (dish.name.contains("Cà phê", true) || dish.name.contains("Coffee", true)))
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
