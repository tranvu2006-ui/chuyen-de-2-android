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

    private val _storePendingOrders = MutableLiveData<List<Order>>()
    val storePendingOrders: LiveData<List<Order>> = _storePendingOrders

    private val _currentDeliveryOrderId = MutableLiveData<String?>()
    val currentDeliveryOrderId: LiveData<String?> = _currentDeliveryOrderId

    private val _isOnline = MutableLiveData(true)
    val isOnline: LiveData<Boolean> = _isOnline

    private val _messageEvent = MutableLiveData<String>()
    val messageEvent: LiveData<String> = _messageEvent

    init {
        observeShipperOrders()
        observePendingStoreOrders()
    }

    private fun observeShipperOrders() {
        val shipperId = auth.currentUser?.uid ?: "default_driver"
        viewModelScope.launch {
            firebaseRepository.observeOrdersByShipper(shipperId).collectLatest { orders ->
                _assignedOrders.value = orders

                // Tìm đơn đang giao
                val delivering = orders.find { it.status == Order.Status.DELIVERING }
                    ?: orders.find { it.status == Order.Status.ASSIGNED || it.status == Order.Status.READY }
                _currentDeliveryOrderId.value = delivering?.orderId
            }
        }
    }

    private fun observePendingStoreOrders() {
        viewModelScope.launch {
            firebaseRepository.observeAllOrders().collectLatest { allOrders ->
                // Lọc các đơn chưa có shipper và ở trạng thái READY, PREPARING, PENDING
                val pending = allOrders.filter { order ->
                    (order.shipperId.isNullOrBlank() || order.shipperId == "null") &&
                            order.status != Order.Status.COMPLETED &&
                            order.status != Order.Status.CANCELLED &&
                            order.status != Order.Status.BOOM
                }
                _storePendingOrders.value = pending
            }
        }
    }

    fun toggleDuty(online: Boolean) {
        _isOnline.value = online
    }

    /** Nhận đơn mới từ quán (ghép chuyến) */
    fun acceptOrder(orderId: String, onResult: (Boolean, String) -> Unit) {
        val currentActiveCount = _assignedOrders.value?.count {
            it.status == Order.Status.DELIVERING || it.status == Order.Status.ASSIGNED || it.status == Order.Status.READY
        } ?: 0

        if (currentActiveCount >= 3) {
            onResult(false, "Tài xế chỉ được nhận tối đa 3 đơn cùng lúc để đảm bảo thời gian giao!")
            return
        }

        val shipperId = auth.currentUser?.uid ?: "default_driver"
        val shipperName = auth.currentUser?.displayName?.ifBlank { "Trần Văn Nam (TX-14)" } ?: "Trần Văn Nam (TX-14)"

        viewModelScope.launch {
            firebaseRepository.assignShipper(orderId, shipperId, shipperName) { success, msg ->
                if (success) {
                    _currentDeliveryOrderId.value = orderId
                }
                onResult(success, msg)
            }
        }
    }

    /** Hủy nhận đơn khi chưa lấy hàng (trả về danh sách chờ của quán) */
    fun cancelAssignedOrder(orderId: String, reason: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            firebaseRepository.unassignShipper(orderId) { success, msg ->
                onResult(success, msg)
            }
        }
    }

    /** Shipper xác nhận lấy hàng thành công tại quán */
    fun confirmPickedUpAtShop(orderId: String) {
        viewModelScope.launch {
            firebaseRepository.updateOrderStatus(orderId, Order.Status.DELIVERING)
        }
    }

    /** Shipper xác nhận hoàn thành giao hàng */
    fun completeDelivery(orderId: String, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                firebaseRepository.updateOrderStatus(orderId, Order.Status.COMPLETED)
                firebaseRepository.clearTracking(orderId)
                onResult?.invoke(true)
            } catch (e: Exception) {
                onResult?.invoke(false)
            }
        }
    }
}
