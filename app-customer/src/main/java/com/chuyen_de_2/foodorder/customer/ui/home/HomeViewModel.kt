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
                    _stores.value = response.body() ?: emptyList()
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

    // Alias cho loadStores
    fun loadRestaurants() = loadStores()
}
