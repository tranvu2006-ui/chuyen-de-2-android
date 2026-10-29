package com.chuyen_de_2.foodorder.admin.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Complaint
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardKpi(
    val totalRevenue: Long = 0L,
    val totalOrders: Int = 0,
    val completedOrders: Int = 0,
    val boomOrders: Int = 0,
    val totalStores: Int = 0,
    val activeStores: Int = 0,
    val pendingStores: Int = 0,
    val pendingComplaints: Int = 0,
    val totalCustomers: Int = 0,
    val totalDrivers: Int = 0
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val firebaseRepo: FirebaseRepository
) : ViewModel() {

    private val _kpi = MutableStateFlow(DashboardKpi())
    val kpi: StateFlow<DashboardKpi> = _kpi.asStateFlow()

    private val _countdownConfig = MutableStateFlow(com.chuyen_de_2.foodorder.core.data.model.DeliveryCountdownConfig())
    val countdownConfig: StateFlow<com.chuyen_de_2.foodorder.core.data.model.DeliveryCountdownConfig> = _countdownConfig.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        observePlatformData()
        observeCountdownConfig()
    }

    private fun observeCountdownConfig() {
        viewModelScope.launch {
            firebaseRepo.observeCountdownConfig().collect { config ->
                _countdownConfig.value = config
            }
        }
    }

    fun saveCountdownConfig(round1Minutes: Int, round2Minutes: Int) {
        viewModelScope.launch {
            val result = firebaseRepo.updateCountdownConfig(round1Minutes, round2Minutes)
            if (result.isSuccess) {
                _actionMessage.value = "Đã lưu cài đặt: Lần 1 = $round1Minutes phút, Lần 2 = $round2Minutes phút!"
            } else {
                _actionMessage.value = "Lưu thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    private fun observePlatformData() {
        viewModelScope.launch {
            combine(
                firebaseRepo.observeAllOrders(),
                firebaseRepo.observeAllStores(),
                firebaseRepo.observeAllComplaints(),
                firebaseRepo.observeAllUsers()
            ) { orders, stores, complaints, users ->
                val revenue = orders.filter {
                    it.orderStatus.equals(Order.OrderStatus.COMPLETED, ignoreCase = true)
                }.sumOf { it.totalPrice }

                val completed = orders.count {
                    it.orderStatus.equals(Order.OrderStatus.COMPLETED, ignoreCase = true)
                }

                val boomed = orders.count {
                    it.orderStatus.equals(Order.OrderStatus.BOOM, ignoreCase = true) ||
                    it.orderStatus.equals(Order.OrderStatus.CANCELLED, ignoreCase = true)
                }

                val activeS = stores.count { it.storeStatus.equals(Store.StoreStatus.OPEN, ignoreCase = true) }
                val pendingS = stores.count {
                    !it.isVerified || it.storeStatus.equals(Store.StoreStatus.PENDING, ignoreCase = true)
                }

                val pendingC = complaints.count {
                    it.status.equals(Complaint.ComplaintStatus.PENDING, ignoreCase = true)
                }

                val customers = users.count { it.role.equals(User.Role.CUSTOMER, ignoreCase = true) }
                val drivers = users.count {
                    it.role.equals(User.Role.DRIVER, ignoreCase = true) ||
                    it.role.equals(User.Role.SHIPPER, ignoreCase = true)
                }

                DashboardKpi(
                    totalRevenue = revenue,
                    totalOrders = orders.size,
                    completedOrders = completed,
                    boomOrders = boomed,
                    totalStores = stores.size,
                    activeStores = activeS,
                    pendingStores = pendingS,
                    pendingComplaints = pendingC,
                    totalCustomers = customers,
                    totalDrivers = drivers
                )
            }.collect { kpiData ->
                _kpi.value = kpiData
            }
        }
    }

    fun restoreDefaultCategories() {
        viewModelScope.launch {
            val result = firebaseRepo.restoreDefaultCategories()
            if (result.isSuccess) {
                _actionMessage.value = "Đã khôi phục thành công 8 danh mục nền tảng mặc định!"
            } else {
                _actionMessage.value = "Khôi phục danh mục thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
