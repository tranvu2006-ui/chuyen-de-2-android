package com.chuyen_de_2.foodorder.customer.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.data.remote.RestBackendApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: RestBackendApi
) : ViewModel() {

    private val _stores = MutableLiveData<List<Store>>()
    val stores: LiveData<List<Store>> = _stores

    // Alias hỗ trợ tương thích ngược
    val restaurants: LiveData<List<Store>> get() = _stores

    private val _hotDishes = MutableLiveData<List<HotDish>>()
    val hotDishes: LiveData<List<HotDish>> = _hotDishes

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        loadStores()
    }

    fun loadStores() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val response = api.getStores()
                if (response.isSuccessful) {
                    val storeList = response.body() ?: emptyList()
                    _stores.value = storeList
                    loadHotDishes(storeList)
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

    private fun loadHotDishes(storeList: List<Store>) {
        viewModelScope.launch {
            val list = mutableListOf<HotDish>()

            // Thử lấy món ăn trực tiếp từ API của 5 quán đầu tiên
            for (store in storeList.take(5)) {
                try {
                    val res = api.getDishesByStore(store.id)
                    if (res.isSuccessful) {
                        val dishes = res.body() ?: emptyList()
                        dishes.firstOrNull()?.let { dish ->
                            list.add(
                                HotDish(
                                    id = dish.id,
                                    name = dish.name,
                                    price = dish.basePrice,
                                    storeId = store.id,
                                    storeName = store.name,
                                    imageUrl = dish.imageUrl.ifBlank { store.imageUrl },
                                    emoji = "🍲"
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            // Nếu API dishes chưa trả món hoặc rỗng, tạo món hot đại diện gắn với quán thực tế
            if (list.isEmpty() && storeList.isNotEmpty()) {
                val sampleDishes = listOf(
                    Triple("Trà Ô Long Sữa Đậm Vị", 55000L, "🧋"),
                    Triple("Cơm Sườn Bì Chả Đặc Biệt", 65000L, "🍛"),
                    Triple("Combo Gà Rán Giòn Cay", 79000L, "🍗"),
                    Triple("Bún Bò Giò Heo Sa Tế", 55000L, "🍜"),
                    Triple("Pizza Hải Sản Phô Mai", 129000L, "🍕")
                )
                storeList.take(5).forEachIndexed { index, store ->
                    val sample = sampleDishes[index % sampleDishes.size]
                    list.add(
                        HotDish(
                            id = "hot_${store.id}",
                            name = sample.first,
                            price = sample.second,
                            storeId = store.id,
                            storeName = store.name,
                            imageUrl = store.imageUrl,
                            emoji = sample.third
                        )
                    )
                }
            }

            _hotDishes.value = list
        }
    }

    // Alias cho loadStores
    fun loadRestaurants() = loadStores()
}
