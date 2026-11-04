package com.chuyen_de_2.foodorder.admin.ui.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Store
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StoreFilter {
    ALL, PENDING, OPEN, LOCKED
}

data class StoreCounts(
    val total: Int = 0,
    val pending: Int = 0,
    val open: Int = 0,
    val locked: Int = 0
)

@HiltViewModel
class AdminStoreViewModel @Inject constructor(
    private val firebaseRepo: FirebaseRepository
) : ViewModel() {

    private val _rawStores = MutableStateFlow<List<Store>>(emptyList())
    private val _filter = MutableStateFlow(StoreFilter.ALL)
    private val _searchQuery = MutableStateFlow("")

    private val _filteredStores = MutableStateFlow<List<Store>>(emptyList())
    val filteredStores: StateFlow<List<Store>> = _filteredStores.asStateFlow()

    private val _counts = MutableStateFlow(StoreCounts())
    val counts: StateFlow<StoreCounts> = _counts.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        observeStores()
    }

    private fun observeStores() {
        viewModelScope.launch {
            firebaseRepo.observeAllStores().collect { list ->
                _rawStores.value = list
                updateCounts(list)
                applyFilterAndSearch()
            }
        }
    }

    private fun updateCounts(list: List<Store>) {
        val total = list.size
        val pending = list.count { !it.isVerified || it.storeStatus.equals(Store.StoreStatus.PENDING, ignoreCase = true) }
        val open = list.count { it.isVerified && it.storeStatus.equals(Store.StoreStatus.OPEN, ignoreCase = true) }
        val locked = list.count { it.storeStatus.equals(Store.StoreStatus.LOCKED, ignoreCase = true) }
        _counts.value = StoreCounts(total, pending, open, locked)
    }

    fun setFilter(filter: StoreFilter) {
        _filter.value = filter
        applyFilterAndSearch()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilterAndSearch()
    }

    private fun applyFilterAndSearch() {
        val currentList = _rawStores.value
        val filter = _filter.value
        val query = _searchQuery.value.trim().lowercase()

        val filtered = currentList.filter { store ->
            val matchesFilter = when (filter) {
                StoreFilter.ALL -> true
                StoreFilter.PENDING -> !store.isVerified || store.storeStatus.equals(Store.StoreStatus.PENDING, ignoreCase = true)
                StoreFilter.OPEN -> store.isVerified && store.storeStatus.equals(Store.StoreStatus.OPEN, ignoreCase = true)
                StoreFilter.LOCKED -> store.storeStatus.equals(Store.StoreStatus.LOCKED, ignoreCase = true)
            }

            val matchesSearch = if (query.isBlank()) true else {
                store.name.lowercase().contains(query) ||
                store.ownerName.lowercase().contains(query) ||
                store.phone.lowercase().contains(query) ||
                store.district.lowercase().contains(query) ||
                store.cccdNumber.lowercase().contains(query)
            }

            matchesFilter && matchesSearch
        }

        _filteredStores.value = filtered
    }

    fun approveStore(storeId: String) {
        viewModelScope.launch {
            val result = firebaseRepo.verifyStore(storeId, isApproved = true)
            if (result.isSuccess) {
                _message.value = "Đã phê duyệt và mở quyền hoạt động cho quán!"
            } else {
                _message.value = "Phê duyệt thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun lockStore(storeId: String) {
        viewModelScope.launch {
            val result = firebaseRepo.verifyStore(storeId, isApproved = false)
            if (result.isSuccess) {
                _message.value = "Đã khóa quán thành công!"
            } else {
                _message.value = "Khóa quán thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun applyPenalty(storeId: String, penaltyTier: String, reason: String) {
        viewModelScope.launch {
            val result = firebaseRepo.updateStorePenalty(storeId, penaltyTier, reason)
            if (result.isSuccess) {
                _message.value = "Đã áp dụng mức phạt '$penaltyTier' cho quán!"
            } else {
                _message.value = "Áp dụng chế tài thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun saveStoreFull(store: Store) {
        viewModelScope.launch {
            val result = firebaseRepo.saveStoreFull(store)
            if (result.isSuccess) {
                _message.value = "Đã lưu toàn bộ thông tin quán '${store.name}' thành công!"
            } else {
                _message.value = "Cập nhật quán thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
