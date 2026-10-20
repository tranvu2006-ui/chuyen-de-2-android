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
        val shipperId = auth.currentUser?.uid
        if (shipperId.isNullOrBlank()) {
            _assignedOrders.value = emptyList()
            return
        }
        viewModelScope.launch {
            firebaseRepository.observeOrdersByShipper(shipperId).collectLatest { orders ->
                // Chỉ nhận những đơn có đúng shipperId của tài xế này
                val myOrders = orders.filter { it.shipperId == shipperId }
                _assignedOrders.value = myOrders

                // Tìm đơn đang giao (ASSIGNED, ARRIVED_AT_SHOP, DELIVERING, ARRIVED_AT_CUSTOMER)
                val delivering = myOrders.find {
                    it.status == Order.Status.DELIVERING ||
                    it.status == Order.Status.ARRIVED_AT_CUSTOMER ||
                    it.status == Order.Status.ARRIVED_AT_SHOP ||
                    it.status == Order.Status.ASSIGNED
                }
                _currentDeliveryOrderId.value = delivering?.orderId
            }
        }
    }

    private fun observePendingStoreOrders() {
        viewModelScope.launch {
            firebaseRepository.observeAllOrders().collectLatest { allOrders ->
                // Chỉ phát các đơn Quán đã nấu xong (READY) và CHƯA có shipper nhận
                val pending = allOrders.filter { order ->
                    (order.shipperId.isNullOrBlank() || order.shipperId == "null") &&
                            order.status == Order.Status.READY
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
            it.status == Order.Status.DELIVERING ||
            it.status == Order.Status.ARRIVED_AT_CUSTOMER ||
            it.status == Order.Status.ARRIVED_AT_SHOP ||
            it.status == Order.Status.ASSIGNED
        } ?: 0

        if (currentActiveCount >= 3) {
            onResult(false, "Tài xế chỉ được nhận tối đa 3 đơn cùng lúc để đảm bảo thời gian giao!")
            return
        }

        val shipperId = auth.currentUser?.uid ?: ""
        if (shipperId.isBlank()) {
            onResult(false, "Vui lòng đăng nhập lại tài khoản tài xế để nhận đơn!")
            return
        }
        val shipperName = auth.currentUser?.displayName?.ifBlank { "Tài xế" } ?: "Tài xế"

        val acceptedOrder = _storePendingOrders.value?.find { it.orderId == orderId }
        // Optimistic UI: Cập nhật ngay danh sách để giao diện phản hồi 0ms
        if (acceptedOrder != null) {
            _storePendingOrders.value = _storePendingOrders.value?.filter { it.orderId != orderId }
            val updatedOrder = acceptedOrder.copy(
                shipperId = shipperId,
                driverId = shipperId,
                shipperName = shipperName,
                status = Order.Status.ASSIGNED,
                orderStatus = Order.Status.ASSIGNED
            )
            val currentAssigned = _assignedOrders.value.orEmpty().toMutableList()
            if (currentAssigned.none { it.orderId == orderId }) {
                currentAssigned.add(0, updatedOrder)
                _assignedOrders.value = currentAssigned
            }
            _currentDeliveryOrderId.value = orderId
        }

        viewModelScope.launch {
            firebaseRepository.assignShipper(orderId, shipperId, shipperName) { success, msg ->
                if (success) {
                    _currentDeliveryOrderId.value = orderId
                } else {
                    // Nếu thất bại (xung đột tài xế khác), khôi phục lại danh sách
                    if (acceptedOrder != null) {
                        _assignedOrders.value = _assignedOrders.value?.filter { it.orderId != orderId }
                    }
                }
                onResult(success, msg)
            }
        }
    }

    /** Hủy nhận đơn khi chưa lấy hàng (trả về danh sách chờ của quán) */
    fun cancelAssignedOrder(orderId: String, reason: String, onResult: (Boolean, String) -> Unit) {
        val removedOrder = _assignedOrders.value?.find { it.orderId == orderId }
        // Optimistic UI: Xóa khỏi danh sách nhận ngay lập tức
        _assignedOrders.value = _assignedOrders.value?.filter { it.orderId != orderId }

        viewModelScope.launch {
            firebaseRepository.unassignShipper(orderId) { success, msg ->
                if (!success && removedOrder != null) {
                    val current = _assignedOrders.value.orEmpty().toMutableList()
                    current.add(removedOrder)
                    _assignedOrders.value = current
                }
                onResult(success, msg)
            }
        }
    }

    /** Shipper xác nhận lấy hàng thành công tại quán */
    fun confirmPickedUpAtShop(orderId: String) {
        // Optimistic UI
        _assignedOrders.value = _assignedOrders.value?.map {
            if (it.orderId == orderId) it.copy(status = Order.Status.DELIVERING, orderStatus = Order.Status.DELIVERING) else it
        }
        viewModelScope.launch {
            firebaseRepository.updateOrderStatus(orderId, Order.Status.DELIVERING)
        }
    }

    /** Shipper xác nhận hoàn thành giao hàng */
    fun completeDelivery(orderId: String, onResult: ((Boolean) -> Unit)? = null) {
        // Optimistic UI
        _assignedOrders.value = _assignedOrders.value?.map {
            if (it.orderId == orderId) it.copy(status = Order.Status.COMPLETED, orderStatus = Order.Status.COMPLETED) else it
        }
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
