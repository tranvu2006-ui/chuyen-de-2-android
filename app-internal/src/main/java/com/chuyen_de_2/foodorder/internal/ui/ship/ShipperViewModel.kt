package com.chuyen_de_2.foodorder.internal.ui.ship

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShipperViewModel @Inject constructor(
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _assignedOrders = MutableLiveData<List<Order>>()
    val assignedOrders: LiveData<List<Order>> = _assignedOrders

    private val _currentDeliveryOrderId = MutableLiveData<String?>()
    val currentDeliveryOrderId: LiveData<String?> = _currentDeliveryOrderId

    init {
        auth.currentUser?.uid?.let { shipperId ->
            viewModelScope.launch {
                firebaseRepository.observeOrdersByShipper(shipperId).collectLatest { orders ->
                    _assignedOrders.value = orders

                    // Tìm đơn đang giao (status = DELIVERING)
                    val delivering = orders.find { it.status == Order.Status.DELIVERING }
                    _currentDeliveryOrderId.value = delivering?.orderId
                }
            }
        }
    }

    /** Shipper xác nhận hoàn thành giao hàng */
    fun completeDelivery(orderId: String) {
        viewModelScope.launch {
            firebaseRepository.updateOrderStatus(orderId, Order.Status.COMPLETED)
            // Xóa dữ liệu tracking cũ (Section 12 - doc-adr: mitigation quota)
            firebaseRepository.clearTracking(orderId)
        }
    }
}
