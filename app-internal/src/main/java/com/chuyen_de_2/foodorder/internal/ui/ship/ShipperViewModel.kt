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
import kotlinx.coroutines.tasks.await
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

    private val _shipperStore = MutableLiveData<Pair<String, String>>() // storeId, storeName
    val shipperStore: LiveData<Pair<String, String>> = _shipperStore

    private val _isOnline = MutableLiveData(true)
    val isOnline: LiveData<Boolean> = _isOnline

    private val _messageEvent = MutableLiveData<String>()
    val messageEvent: LiveData<String> = _messageEvent

    init {
        loadShipperStoreAndOrders()
    }

    private fun loadShipperStoreAndOrders() {
        val shipperId = auth.currentUser?.uid.orEmpty()
        viewModelScope.launch {
            if (shipperId.isNotBlank()) {
                firebaseRepository.setupDriverPresence(shipperId)
                val isCurrentlyOnline = _isOnline.value ?: true
                val initialStatus = if (isCurrentlyOnline) com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.AVAILABLE else com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.OFF_DUTY
                firebaseRepository.updateDriverDutyStatus(shipperId, initialStatus, "")
            }
            val storeInfo = firebaseRepository.resolveStoreForDriver(shipperId)
            _shipperStore.value = storeInfo
            observeShipperOrders()
            observePendingStoreOrders(storeInfo.first)
        }
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

                // Tìm đơn đang giao (ASSIGNED, ARRIVED_AT_SHOP, DELIVERING, ARRIVED_AT_CUSTOMER, RETURNING_TO_STORE)
                val delivering = myOrders.find {
                    it.status == Order.Status.DELIVERING ||
                    it.status == Order.Status.ARRIVED_AT_CUSTOMER ||
                    it.status == Order.Status.ARRIVED_AT_SHOP ||
                    it.status == Order.Status.ASSIGNED ||
                    it.status == Order.Status.RETURNING_TO_STORE
                }
                _currentDeliveryOrderId.value = delivering?.orderId

                // Đồng bộ trạng thái thật (DELIVERING / AVAILABLE / OFF_DUTY) lên Firebase cho Quán thấy
                val isCurrentlyOnline = _isOnline.value ?: true
                if (delivering != null) {
                    val shortCode = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.getShortOrderCode(delivering.orderId)
                    val displayCode = if (delivering.status == Order.Status.RETURNING_TO_STORE) {
                        "#ORD-$shortCode (Trả hàng về quán)"
                    } else {
                        "#ORD-$shortCode (${delivering.customerName})"
                    }
                    firebaseRepository.updateDriverDutyStatus(shipperId, com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.DELIVERING, displayCode)
                } else {
                    val status = if (isCurrentlyOnline) com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.AVAILABLE else com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.OFF_DUTY
                    firebaseRepository.updateDriverDutyStatus(shipperId, status, "")
                }
            }
        }
    }

    private fun observePendingStoreOrders(storeId: String) {
        viewModelScope.launch {
            firebaseRepository.observeAllOrders().collectLatest { allOrders ->
                // Phân quyền chuẩn: Chỉ nhận đơn thuộc đúng QUÁN CỦA SHIPPER NÀY, đã READY và CHƯA có shipper nhận
                if (storeId.isNotBlank()) {
                    val pending = allOrders.filter { order ->
                        (order.shipperId.isNullOrBlank() || order.shipperId == "null") &&
                                order.status == Order.Status.READY &&
                                order.storeId.isNotBlank() &&
                                order.storeId == storeId
                    }
                    _storePendingOrders.value = pending
                } else {
                    // Tài xế chưa được gán vào quán ăn nào -> không nhận đơn của quán khác
                    _storePendingOrders.value = emptyList()
                }
            }
        }
    }

    fun toggleDuty(online: Boolean) {
        _isOnline.value = online
        val shipperId = auth.currentUser?.uid.orEmpty()
        if (shipperId.isNotBlank()) {
            viewModelScope.launch {
                val hasDelivering = _assignedOrders.value?.any {
                    it.status == Order.Status.DELIVERING ||
                    it.status == Order.Status.ARRIVED_AT_CUSTOMER ||
                    it.status == Order.Status.ARRIVED_AT_SHOP ||
                    it.status == Order.Status.ASSIGNED ||
                    it.status == Order.Status.RETURNING_TO_STORE
                } == true
                val status = if (hasDelivering) {
                    com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.DELIVERING
                } else if (online) {
                    com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.AVAILABLE
                } else {
                    com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.OFF_DUTY
                }
                firebaseRepository.updateDriverDutyStatus(shipperId, status)
            }
        }
    }

    /** Nhận đơn mới từ quán (ghép chuyến) */
    fun acceptOrder(orderId: String, onResult: (Boolean, String) -> Unit) {
        val currentActiveCount = _assignedOrders.value?.count {
            it.status == Order.Status.DELIVERING ||
            it.status == Order.Status.ARRIVED_AT_CUSTOMER ||
            it.status == Order.Status.ARRIVED_AT_SHOP ||
            it.status == Order.Status.ASSIGNED ||
            it.status == Order.Status.RETURNING_TO_STORE
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

        val myStoreId = _shipperStore.value?.first.orEmpty()
        val myStoreName = _shipperStore.value?.second.orEmpty()

        if (myStoreId.isBlank()) {
            onResult(false, "Tài xế chưa được liên kết với Quán nào. Vui lòng liên hệ Chủ quán để được thêm vào đội ngũ giao hàng!")
            return
        }

        val acceptedOrder = _storePendingOrders.value?.find { it.orderId == orderId }
        if (acceptedOrder != null && acceptedOrder.storeId.isNotBlank() && acceptedOrder.storeId != myStoreId) {
            val storeDisplay = if (myStoreName.isNotBlank()) myStoreName else myStoreId
            onResult(false, "Phân quyền: Bạn chỉ có thể nhận đơn từ quán của mình ($storeDisplay)!")
            return
        }

        val baseShipperName = auth.currentUser?.displayName?.ifBlank { "Tài xế" } ?: "Tài xế"
        val baseShipperPhone = auth.currentUser?.phoneNumber.orEmpty()

        viewModelScope.launch {
            var resolvedShipperName = baseShipperName
            var resolvedShipperPhone = baseShipperPhone

            if (resolvedShipperPhone.isBlank() || resolvedShipperName == "Tài xế") {
                try {
                    val userSnap = firebaseRepository.database.getReference("users").child(shipperId).get().await()
                    val dbPhone = userSnap.child("phone").getValue(String::class.java).orEmpty().trim()
                    val dbName = userSnap.child("name").getValue(String::class.java).orEmpty().trim()
                    if (dbPhone.isNotBlank()) resolvedShipperPhone = dbPhone
                    if (dbName.isNotBlank()) resolvedShipperName = dbName
                } catch (_: Exception) {}
            }

            // Optimistic UI: Cập nhật ngay danh sách để giao diện phản hồi 0ms
            if (acceptedOrder != null) {
                _storePendingOrders.value = _storePendingOrders.value?.filter { it.orderId != orderId }
                val updatedOrder = acceptedOrder.copy(
                    shipperId = shipperId,
                    driverId = shipperId,
                    shipperName = resolvedShipperName,
                    shipperPhone = resolvedShipperPhone,
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

            firebaseRepository.assignShipper(orderId, shipperId, resolvedShipperName, resolvedShipperPhone) { success, msg ->
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
