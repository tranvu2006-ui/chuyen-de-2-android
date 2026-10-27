package com.chuyen_de_2.foodorder.admin.ui.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Customer
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserWithCustomerInfo(
    val user: User,
    val customer: Customer? = null
)

@HiltViewModel
class AdminMonitorViewModel @Inject constructor(
    private val firebaseRepo: FirebaseRepository
) : ViewModel() {

    private val _rawOrders = MutableStateFlow<List<Order>>(emptyList())
    private val _rawUsers = MutableStateFlow<List<UserWithCustomerInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")

    private val _filteredOrders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _filteredOrders.asStateFlow()

    private val _filteredUsers = MutableStateFlow<List<UserWithCustomerInfo>>(emptyList())
    val users: StateFlow<List<UserWithCustomerInfo>> = _filteredUsers.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            launch {
                firebaseRepo.observeAllOrders().collect { list ->
                    _rawOrders.value = list.sortedByDescending { it.createdAt }
                    applySearch()
                }
            }

            launch {
                combine(
                    firebaseRepo.observeAllUsers(),
                    firebaseRepo.observeAllCustomers()
                ) { userList, customerList ->
                    val customerMap = customerList.associateBy { it.customerId }
                    userList.map { u ->
                        UserWithCustomerInfo(
                            user = u,
                            customer = customerMap[u.uid]
                        )
                    }
                }.collect { combined ->
                    _rawUsers.value = combined
                    applySearch()
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applySearch()
    }

    private fun applySearch() {
        val q = _searchQuery.value.trim().lowercase()

        // Filter Orders
        if (q.isBlank()) {
            _filteredOrders.value = _rawOrders.value
        } else {
            _filteredOrders.value = _rawOrders.value.filter { o ->
                o.orderId.lowercase().contains(q) ||
                o.shopName.lowercase().contains(q) ||
                o.customerName.lowercase().contains(q) ||
                o.customerPhone.lowercase().contains(q) ||
                o.orderStatus.lowercase().contains(q)
            }
        }

        // Filter Users
        if (q.isBlank()) {
            _filteredUsers.value = _rawUsers.value
        } else {
            _filteredUsers.value = _rawUsers.value.filter { item ->
                item.user.name.lowercase().contains(q) ||
                item.user.email.lowercase().contains(q) ||
                item.user.phone.lowercase().contains(q) ||
                item.user.role.lowercase().contains(q) ||
                item.user.uid.lowercase().contains(q)
            }
        }
    }

    fun toggleUserLock(user: User) {
        viewModelScope.launch {
            val newIsActive = !user.isActive
            val newStatus = if (newIsActive) User.Status.ACTIVE else User.Status.INACTIVE
            val result = firebaseRepo.updateUserStatus(user.uid, newStatus, newIsActive)
            if (result.isSuccess) {
                _message.value = if (newIsActive) "Đã mở khóa tài khoản ${user.email}" else "Đã khóa tài khoản ${user.email}"
            } else {
                _message.value = "Cập nhật tài khoản thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun saveUserWithTrustScore(
        user: User,
        trustScore: Int,
        completedOrders: Int,
        boomOrders: Int,
        adjustReason: String
    ) {
        viewModelScope.launch {
            val result = firebaseRepo.saveUserWithTrustScore(
                user = user,
                trustScore = trustScore,
                completedOrders = completedOrders,
                boomOrders = boomOrders,
                adjustReason = adjustReason
            )
            if (result.isSuccess) {
                _message.value = "Đã lưu hồ sơ & Điểm uy tín ($trustScore/100) cho '${user.name.ifBlank { user.email }}'!"
            } else {
                _message.value = "Cập nhật thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun saveOrderFull(order: Order) {
        viewModelScope.launch {
            val result = firebaseRepo.saveOrderFull(order)
            if (result.isSuccess) {
                _message.value = "Đã lưu toàn bộ thông tin đơn hàng #${order.orderId.takeLast(6)}!"
            } else {
                _message.value = "Cập nhật đơn hàng thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
