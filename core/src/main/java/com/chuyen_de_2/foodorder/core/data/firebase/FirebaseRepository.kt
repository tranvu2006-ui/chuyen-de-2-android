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
                close()
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
                close()
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
                close()
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
                close()
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Lấy thông tin đơn hàng 1 lần */
    suspend fun getOrder(orderId: String): Order? {
        return try {
            val snap = database.getReference("orders").child(orderId).get().await()
            snap.getValue(Order::class.java)
        } catch (_: Exception) {
            null
        }
    }

    /** Lắng nghe 1 đơn hàng cụ thể */
    fun observeOrder(orderId: String): Flow<Order?> = callbackFlow {
        val ref = database.getReference("orders").child(orderId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Order::class.java))
            }

            override fun onCancelled(error: DatabaseError) {
                close()
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

                // Kỹ thuật Lock: Đơn phải ở trạng thái READY (quán đã nấu xong) và CHƯA có shipper nào nhận
                if (!order.shipperId.isNullOrBlank() || order.status != Order.Status.READY) {
                    return Transaction.abort() // Xung đột, tài xế khác đã nhận hoặc quán chưa nấu xong
                }

                order.shipperId = shipperId
                order.shipperName = shipperName
                order.status = Order.Status.ASSIGNED
                order.orderStatus = Order.Status.ASSIGNED
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
            "status" to Order.Status.READY,
            "orderStatus" to Order.Status.READY
        )
        dbRef.updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResult(true, "Đã hoàn trả đơn về danh sách chờ của quán!")
            } else {
                onResult(false, task.exception?.message ?: "Lỗi hủy nhận đơn")
            }
        }
    }

    /** Cập nhật trạng thái đơn hàng và đồng bộ hai chiều status/orderStatus */
    suspend fun updateOrderStatus(orderId: String, status: String) {
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "orderStatus" to status,
            "updatedAt" to System.currentTimeMillis()
        )
        database.getReference("orders")
            .child(orderId)
            .updateChildren(updates)
            .await()

        // Tự động cập nhật thống kê đơn hàng và điểm uy tín khách hàng khi hoàn thành hoặc bom hàng
        try {
            val orderSnap = database.getReference("orders").child(orderId).get().await()
            val customerId = orderSnap.child("customerId").getValue(String::class.java).orEmpty()
            if (customerId.isNotBlank()) {
                val custRef = database.getReference("users").child(customerId)
                val custSnap = custRef.get().await()
                if (custSnap.exists()) {
                    val currentCompleted = custSnap.child("completedOrders").getValue(Long::class.java) ?: 0L
                    val currentTotal = custSnap.child("totalOrders").getValue(Long::class.java) ?: 0L
                    val currentBoom = custSnap.child("boomOrders").getValue(Long::class.java) ?: 0L
                    val currentScore = custSnap.child("trustScore").getValue(Long::class.java) ?: 100L

                    val statsUpdates = mutableMapOf<String, Any>()
                    if (status == Order.Status.COMPLETED) {
                        statsUpdates["completedOrders"] = currentCompleted + 1
                        statsUpdates["totalOrders"] = currentTotal + 1
                        statsUpdates["trustScore"] = minOf(100L, currentScore + 2L)
                    } else if (status == Order.Status.BOOM) {
                        statsUpdates["boomOrders"] = currentBoom + 1
                        statsUpdates["totalOrders"] = currentTotal + 1
                        val newScore = maxOf(0L, currentScore - 30L)
                        statsUpdates["trustScore"] = newScore
                        if (newScore < 20L) {
                            statsUpdates["status"] = "LOCKED"
                            statsUpdates["isActive"] = false
                            statsUpdates["lockReason"] = "Điểm uy tín dưới 20 do bom hàng"
                        }
                    }
                    if (statsUpdates.isNotEmpty()) {
                        custRef.updateChildren(statsUpdates)
                        database.getReference("customers").child(customerId).updateChildren(statsUpdates)
                    }
                }
            }
        } catch (_: Exception) {}
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
                close()
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
                close()
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ==================== HỒ SƠ QUÁN ĂN (STORES - Quán/8.html) ====================

    /** Lấy danh sách tất cả các quán ăn theo thời gian thực (cho Khách hàng mua món) */
    fun observeAllStores(): Flow<List<com.chuyen_de_2.foodorder.core.data.model.Store>> = callbackFlow {
        val ref = database.getReference("stores")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.Store>()
                for (child in snapshot.children) {
                    val store = child.getValue(com.chuyen_de_2.foodorder.core.data.model.Store::class.java)
                    if (store != null) {
                        val finalStore = if (store.id.isBlank()) store.copy(id = child.key ?: "") else store
                        list.add(finalStore)
                    }
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close()
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Tìm Store ID tương ứng cho tài khoản hiện tại */
    suspend fun resolveStoreIdForUser(uid: String): String {
        if (uid.isBlank()) return "shop_01"
        return when (uid) {
            "whjjZ5osJhTNtMCovwgp8JXdJK73" -> "shop_01"
            "AdBaJDWpzDYJRxVdl2qbWX8jAUR2" -> "shop_02"
            "c3AlIlYhEDcGKC9Z4EYOlWqfeCN2" -> "shop_03"
            "uNzJQswcLSNnuF4GpQAlMdQnS1P2" -> "shop_04"
            "QCXRaghsICbmi1Pngptv3s0mWO32" -> "shop_05"
            "J5Cz86Vxd8RRfwp0iK8F1b2Rslx1" -> "shop_06"
            else -> {
                try {
                    // 1. Kiểm tra users/{uid}/storeId
                    val userSnap = database.getReference("users").child(uid).get().await()
                    val userStoreId = userSnap.child("storeId").getValue(String::class.java)
                    if (!userStoreId.isNullOrBlank()) {
                        return userStoreId
                    }
                    // 2. Kiểm tra stores với ownerId hoặc merchantId == uid
                    val storesSnap = database.getReference("stores").get().await()
                    for (child in storesSnap.children) {
                        val ownerId = child.child("ownerId").getValue(String::class.java)
                            ?: child.child("merchantId").getValue(String::class.java)
                        if (ownerId == uid) {
                            return child.key ?: "shop_01"
                        }
                    }
                } catch (_: Exception) {}
                "shop_01"
            }
        }
    }

    /** Lắng nghe thông tin hồ sơ quán theo thời gian thực */
    fun observeStore(storeId: String): Flow<com.chuyen_de_2.foodorder.core.data.model.Store?> = callbackFlow {
        val ref = database.getReference("stores").child(storeId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val store = snapshot.getValue(com.chuyen_de_2.foodorder.core.data.model.Store::class.java)
                trySend(store)
            }

            override fun onCancelled(error: DatabaseError) {
                close()
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

    /** Cập nhật trạng thái đóng/mở cửa của quán lên Firebase (OPEN / CLOSED) */
    suspend fun updateStoreStatus(storeId: String, status: String): Result<Unit> {
        return try {
            val isOpen = status.equals("OPEN", ignoreCase = true)
            val updates = mapOf<String, Any>(
                "storeStatus" to status,
                "status" to status,
                "open" to isOpen
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
                close()
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
                close()
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Tạo tài khoản tài xế mới cho quán:
     * 1. Tạo tài khoản đăng nhập Firebase Auth (dùng secondary FirebaseApp để giữ nguyên đăng nhập của quán)
     * 2. Lưu thông tin vào users/{driverUid} (role = DRIVER, storeId = storeId)
     * 3. Lưu hồ sơ vào drivers/{driverUid}
     */
    suspend fun createDriverAccount(
        context: android.content.Context,
        storeId: String,
        name: String,
        phone: String,
        driverCode: String,
        vehiclePlate: String,
        vehicleModel: String,
        email: String,
        pass: String
    ): Result<String> {
        return try {
            val secondaryApp = try {
                com.google.firebase.FirebaseApp.getInstance("DriverCreator")
            } catch (e: Exception) {
                val options = com.google.firebase.FirebaseApp.getInstance().options
                com.google.firebase.FirebaseApp.initializeApp(context.applicationContext, options, "DriverCreator")
            }
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
            val authResult = secondaryAuth.createUserWithEmailAndPassword(email, pass).await()
            val driverUid = authResult.user?.uid ?: return Result.failure(Exception("Không lấy được UID của tài xế mới"))
            secondaryAuth.signOut()

            // 1. Lưu thông tin vào users
            val user = com.chuyen_de_2.foodorder.core.data.model.User(
                uid = driverUid,
                roleId = 4,
                role = com.chuyen_de_2.foodorder.core.data.model.User.Role.DRIVER,
                name = name,
                email = email,
                phone = phone,
                statusId = 1,
                status = com.chuyen_de_2.foodorder.core.data.model.User.Status.ACTIVE,
                isActive = true,
                storeId = storeId
            )
            database.getReference("users").child(driverUid).setValue(user).await()

            // 2. Lưu thông tin vào drivers
            val driver = com.chuyen_de_2.foodorder.core.data.model.Driver(
                driverId = driverUid,
                storeId = storeId,
                driverCode = driverCode,
                name = name,
                phone = phone,
                avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80",
                vehicleModel = if (vehicleModel.isNotBlank()) "$vehicleModel ($vehiclePlate)" else vehiclePlate,
                vehiclePlate = vehiclePlate,
                dutyStatus = com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.AVAILABLE,
                todayOrdersCount = 0,
                totalOrdersCount = 0,
                ratingAvg = 5.0f,
                note = "Tài xế nội bộ của quán"
            )
            database.getReference("drivers").child(driverUid).setValue(driver).await()

            Result.success(driverUid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Xóa tài xế khỏi đội ngũ của quán */
    suspend fun deleteDriver(driverId: String): Result<Unit> {
        return try {
            database.getReference("drivers").child(driverId).removeValue().await()
            database.getReference("users").child(driverId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Khởi tạo tài xế mẫu nếu cần (đã tắt để ưu tiên dữ liệu thật do quán tự tạo) */
    suspend fun seedDefaultDriversIfEmpty(storeId: String) {
        // Tắt seed tự động để bảo toàn dữ liệu thật của quán
    }
}
