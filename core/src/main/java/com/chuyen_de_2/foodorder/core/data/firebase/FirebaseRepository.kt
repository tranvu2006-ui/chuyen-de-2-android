package com.chuyen_de_2.foodorder.core.data.firebase

import com.chuyen_de_2.foodorder.core.data.model.Cart
import com.chuyen_de_2.foodorder.core.data.model.CartItem
import com.chuyen_de_2.foodorder.core.data.model.CartPayload
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.data.model.TrackingLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository xử lý toàn bộ CRUD trên Firebase Realtime Database
 * Bao gồm: Giỏ hàng, Đơn hàng, GPS Tracking
 *
 * API Contract (Section 5 - doc-adr):
 * - PUT carts/{userId}/{shopId}
 * - POST orders/
 * - POST orders/{orderId}/assign (Transaction)
 * - GET tracking/{orderId} (ValueEventListener)
 */
@Singleton
class FirebaseRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) {
    // ==================== GIỎ HÀNG (CART) ====================

    /**
     * Đồng bộ giỏ hàng lên Firebase — được gọi bởi thuật toán Debounce
     * API Contract: PUT carts/{userId}/{shopId} (SDK setValue)
     * Auth: Firebase Rules: auth.uid == userId
     */
    suspend fun syncCartToFirebase(payload: CartPayload) {
        database.getReference("carts")
            .child(payload.userId)
            .child(payload.shopId)
            .setValue(payload.cart)
            .await()
    }

    /** Lắng nghe giỏ hàng realtime */
    fun observeCart(userId: String, shopId: String): Flow<Cart?> = callbackFlow {
        val ref = database.getReference("carts")
            .child(userId)
            .child(shopId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cart = snapshot.getValue(Cart::class.java)
                trySend(cart)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Xóa giỏ hàng sau khi checkout */
    suspend fun clearCart(userId: String, shopId: String) {
        database.getReference("carts")
            .child(userId)
            .child(shopId)
            .removeValue()
            .await()
    }

    // ==================== ĐƠN HÀNG (ORDER) ====================

    /**
     * Tạo đơn hàng mới
     * API Contract: POST orders/ (SDK push)
     * Auth: Phải có Role CUSTOMER
     */
    suspend fun createOrder(order: Order): Result<String> {
        return try {
            val ref = database.getReference("orders").push()
            val orderId = ref.key ?: return Result.failure(Exception("Không tạo được order ID"))

            val orderWithId = order.copy(
                orderId = orderId,
                timestamp = System.currentTimeMillis()
            )
            ref.setValue(orderWithId).await()

            Result.success(orderId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lắng nghe tất cả đơn hàng (dùng cho Shop Dashboard) */
    fun observeAllOrders(): Flow<List<Order>> = callbackFlow {
        val ref = database.getReference("orders")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = snapshot.children.mapNotNull {
                    it.getValue(Order::class.java)
                }.sortedByDescending { it.timestamp }
                trySend(orders)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Lắng nghe đơn hàng của 1 khách hàng */
    fun observeOrdersByCustomer(customerId: String): Flow<List<Order>> = callbackFlow {
        val ref = database.getReference("orders")
            .orderByChild("customerId")
            .equalTo(customerId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = snapshot.children.mapNotNull {
                    it.getValue(Order::class.java)
                }.sortedByDescending { it.timestamp }
                trySend(orders)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Lắng nghe đơn hàng được gán cho 1 shipper */
    fun observeOrdersByShipper(shipperId: String): Flow<List<Order>> = callbackFlow {
        val ref = database.getReference("orders")
            .orderByChild("shipperId")
            .equalTo(shipperId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = snapshot.children.mapNotNull {
                    it.getValue(Order::class.java)
                }.sortedByDescending { it.timestamp }
                trySend(orders)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Lắng nghe 1 đơn hàng cụ thể */
    fun observeOrder(orderId: String): Flow<Order?> = callbackFlow {
        val ref = database.getReference("orders").child(orderId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Order::class.java))
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Gán shipper cho đơn hàng — Firebase Transaction (Section 14 - doc-adr)
     *
     * Kỹ thuật Lock: Đơn phải ở trạng thái READY và CHƯA có shipper.
     * Nếu 2 quản lý bấm gán đồng thời → chỉ 1 người thành công,
     * người còn lại nhận lỗi "Xung đột".
     *
     * API Contract: POST orders/{orderId}/assign (SDK runTransaction)
     * Auth: Role MANAGER
     */
    fun assignShipper(
        orderId: String,
        shipperId: String,
        shipperName: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val dbRef = database.getReference("orders").child(orderId)

        dbRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val order = currentData.getValue(Order::class.java)
                    ?: return Transaction.abort()

                // Kỹ thuật Lock: Đơn phải PENDING/READY và CHƯA có shipper
                if (order.shipperId != null || order.status == Order.Status.DELIVERING) {
                    return Transaction.abort() // Xung đột, người khác đã gán
                }

                order.shipperId = shipperId
                order.status = Order.Status.DELIVERING
                currentData.value = order

                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                if (committed) {
                    onResult(true, "Gán shipper thành công!")
                } else {
                    onResult(false, error?.message ?: "Lỗi xung đột, đơn đã được xử lý!")
                }
            }
        })
    }

    /**
     * Shipper hủy nhận đơn khi chưa lấy hàng (trả về danh sách chờ của quán)
     */
    fun unassignShipper(
        orderId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val dbRef = database.getReference("orders").child(orderId)
        val updates = mapOf<String, Any?>(
            "shipperId" to null,
            "shipperName" to "",
            "driverId" to null,
            "status" to Order.Status.READY
        )
        dbRef.updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResult(true, "Đã hoàn trả đơn về danh sách chờ của quán!")
            } else {
                onResult(false, task.exception?.message ?: "Lỗi hủy nhận đơn")
            }
        }
    }

    /** Cập nhật trạng thái đơn hàng */
    suspend fun updateOrderStatus(orderId: String, status: String) {
        database.getReference("orders")
            .child(orderId)
            .child("status")
            .setValue(status)
            .await()
    }

    // ==================== GPS TRACKING ====================

    /**
     * Cập nhật tọa độ GPS — gọi bởi Foreground Service của Shipper
     * Path: tracking/{orderId}
     */
    suspend fun updateTracking(orderId: String, location: TrackingLocation) {
        database.getReference("tracking")
            .child(orderId)
            .setValue(location)
            .await()
    }

    /**
     * Lắng nghe GPS realtime — dùng bởi Customer app
     * API Contract (Section 5 - doc-adr): GET tracking/{orderId} (addValueEventListener)
     * Response: Stream trả về liên tục {lat, lng, updatedAt}
     */
    fun observeTracking(orderId: String): Flow<TrackingLocation?> = callbackFlow {
        val ref = database.getReference("tracking").child(orderId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tracking = snapshot.getValue(TrackingLocation::class.java)
                trySend(tracking)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Xóa dữ liệu tracking cũ — mitigation quota Firebase (Section 12 - doc-adr) */
    suspend fun clearTracking(orderId: String) {
        database.getReference("tracking")
            .child(orderId)
            .removeValue()
            .await()
    }

    // ==================== USERS ====================

    /** Lấy danh sách shipper đang ACTIVE (hỗ trợ cả role SHIPPER và DRIVER) */
    fun observeActiveShippers(): Flow<List<com.chuyen_de_2.foodorder.core.data.model.User>> = callbackFlow {
        val ref = database.getReference("users")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val shippers = snapshot.children.mapNotNull {
                    it.getValue(com.chuyen_de_2.foodorder.core.data.model.User::class.java)
                }.filter {
                    (it.role == com.chuyen_de_2.foodorder.core.data.model.User.Role.SHIPPER ||
                     it.role == com.chuyen_de_2.foodorder.core.data.model.User.Role.DRIVER) &&
                    it.status == com.chuyen_de_2.foodorder.core.data.model.User.Status.ACTIVE
                }
                trySend(shippers)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ==================== HỒ SƠ QUÁN ĂN (STORES - Quán/8.html) ====================

    /** Lắng nghe thông tin hồ sơ quán theo thời gian thực */
    fun observeStore(storeId: String): Flow<com.chuyen_de_2.foodorder.core.data.model.Store?> = callbackFlow {
        val ref = database.getReference("stores").child(storeId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val store = snapshot.getValue(com.chuyen_de_2.foodorder.core.data.model.Store::class.java)
                trySend(store)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Cập nhật thông tin hồ sơ quán ăn lên Firebase */
    suspend fun updateStoreProfile(
        storeId: String,
        name: String,
        phone: String,
        address: String,
        district: String,
        ownerName: String,
        openHours: String,
        prepTime: String
    ): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "name" to name,
                "phone" to phone,
                "address" to address,
                "district" to district,
                "ownerName" to ownerName,
                "openHours" to openHours,
                "prepTime" to prepTime
            )
            database.getReference("stores").child(storeId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== THỰC ĐƠN QUÁN (DISHES) ====================

    /** Lấy danh sách món ăn của quán theo thời gian thực */
    fun observeStoreDishes(storeId: String): Flow<List<com.chuyen_de_2.foodorder.core.data.model.Dish>> = callbackFlow {
        val ref = database.getReference("stores").child(storeId).child("dishes")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.Dish>()
                for (child in snapshot.children) {
                    val dish = child.getValue(com.chuyen_de_2.foodorder.core.data.model.Dish::class.java)
                    if (dish != null) {
                        list.add(if (dish.id.isBlank()) dish.copy(id = child.key ?: "") else dish)
                    }
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Lưu / Cập nhật món ăn cho quán vào Firebase */
    suspend fun saveStoreDish(storeId: String, dish: com.chuyen_de_2.foodorder.core.data.model.Dish) {
        val dishId = dish.id.ifBlank {
            database.getReference("stores").child(storeId).child("dishes").push().key ?: "dish_${System.currentTimeMillis()}"
        }
        val finalDish = dish.copy(id = dishId, storeId = storeId)
        database.getReference("stores").child(storeId).child("dishes").child(dishId).setValue(finalDish).await()
    }

    /** Xóa món ăn khỏi thực đơn quán trên Firebase */
    suspend fun deleteStoreDish(storeId: String, dishId: String) {
        database.getReference("stores").child(storeId).child("dishes").child(dishId).removeValue().await()
    }

    // ==================== KHIẾU NẠI (COMPLAINTS) ====================

    /** Gửi khiếu nại đơn hàng lên Firebase (Actor 2 -> Actor 1) */
    suspend fun submitComplaint(complaint: com.chuyen_de_2.foodorder.core.data.model.Complaint): Result<String> {
        return try {
            val ref = database.getReference("complaints").push()
            val complaintId = ref.key ?: System.currentTimeMillis().toString()
            val finalComplaint = complaint.copy(
                complaintId = complaintId,
                createdAt = if (complaint.createdAt > 0) complaint.createdAt else System.currentTimeMillis()
            )
            ref.setValue(finalComplaint).await()
            Result.success(complaintId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== ĐỘI NGŨ TÀI XẾ QUÁN (DRIVERS - Quán/1.html) ====================

    /** Lấy danh sách tài xế nội bộ của quán theo thời gian thực */
    fun observeStoreDrivers(storeId: String): Flow<List<com.chuyen_de_2.foodorder.core.data.model.Driver>> = callbackFlow {
        val ref = database.getReference("drivers")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull {
                    it.getValue(com.chuyen_de_2.foodorder.core.data.model.Driver::class.java)
                }.filter {
                    it.storeId.isEmpty() || it.storeId == storeId || storeId.isEmpty()
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Lưu hoặc thêm mới tài xế */
    suspend fun saveDriver(driver: com.chuyen_de_2.foodorder.core.data.model.Driver): Result<String> {
        return try {
            val ref = if (driver.driverId.isNotBlank()) {
                database.getReference("drivers").child(driver.driverId)
            } else {
                database.getReference("drivers").push()
            }
            val id = ref.key ?: System.currentTimeMillis().toString()
            val finalDriver = driver.copy(driverId = id)
            ref.setValue(finalDriver).await()
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Khởi tạo 3 tài xế mẫu chuẩn theo Quán/1.html nếu database chưa có */
    suspend fun seedDefaultDriversIfEmpty(storeId: String) {
        try {
            val snapshot = database.getReference("drivers").get().await()
            if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                val demoDrivers = listOf(
                    com.chuyen_de_2.foodorder.core.data.model.Driver(
                        driverId = "driver_nam_14",
                        storeId = storeId,
                        driverCode = "TX-14",
                        name = "Trần Văn Nam",
                        phone = "0903882910",
                        avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80",
                        vehicleModel = "Yamaha Sirius Fi (59-T1 882.91)",
                        vehiclePlate = "59-T1 882.91",
                        dutyStatus = com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.AVAILABLE,
                        todayOrdersCount = 12,
                        totalOrdersCount = 1240,
                        ratingAvg = 4.9f,
                        note = "✓ Tự nhận đơn của quán"
                    ),
                    com.chuyen_de_2.foodorder.core.data.model.Driver(
                        driverId = "driver_tuan_19",
                        storeId = storeId,
                        driverCode = "TX-19",
                        name = "Nguyễn Văn Tuấn",
                        phone = "0909888777",
                        avatarUrl = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150&auto=format&fit=crop&q=80",
                        vehicleModel = "Yamaha Sirius (59-K2 654.32)",
                        vehiclePlate = "59-K2 654.32",
                        dutyStatus = com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.DELIVERING,
                        todayOrdersCount = 8,
                        totalOrdersCount = 860,
                        currentOrderCode = "#ORD_OISHI_88290 (Giao Q.1)",
                        ratingAvg = 4.8f,
                        note = "Đang thực hiện cuốc xe"
                    ),
                    com.chuyen_de_2.foodorder.core.data.model.Driver(
                        driverId = "driver_nghia_08",
                        storeId = storeId,
                        driverCode = "TX-08",
                        name = "Lê Hữu Nghĩa",
                        phone = "0918765432",
                        avatarUrl = "https://images.unsplash.com/photo-1527980965255-d3b416303d12?w=150&auto=format&fit=crop&q=80",
                        vehicleModel = "Honda AirBlade (59-P1 123.89)",
                        vehiclePlate = "59-P1 123.89",
                        dutyStatus = com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.OFF_DUTY,
                        todayOrdersCount = 0,
                        totalOrdersCount = 95,
                        ratingAvg = 4.9f,
                        note = "Vào ca lúc 14:00"
                    )
                )
                demoDrivers.forEach { driver ->
                    database.getReference("drivers").child(driver.driverId).setValue(driver).await()
                }
            }
        } catch (_: Exception) {}
    }
}
