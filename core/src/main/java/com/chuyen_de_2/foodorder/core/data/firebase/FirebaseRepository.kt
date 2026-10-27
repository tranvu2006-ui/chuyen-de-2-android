package com.chuyen_de_2.foodorder.core.data.firebase

import com.chuyen_de_2.foodorder.core.data.model.Cart
import com.chuyen_de_2.foodorder.core.data.model.CartItem
import com.chuyen_de_2.foodorder.core.data.model.CartPayload
import com.chuyen_de_2.foodorder.core.data.model.Category
import com.chuyen_de_2.foodorder.core.data.model.CustomerAddress
import com.chuyen_de_2.foodorder.core.data.model.DishReview
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.data.model.Store
import com.chuyen_de_2.foodorder.core.data.model.User
import com.chuyen_de_2.foodorder.core.data.model.TrackingLocation
import com.chuyen_de_2.foodorder.core.data.model.TrustScoreLog
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
    val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) {
    init {
        try {
            // Giữ kết nối socket và cache sync liên tục để tránh độ trễ khi chuyển màn hình
            database.getReference("orders").keepSynced(true)
            database.getReference("tracking").keepSynced(true)
        } catch (_: Exception) {}
    }

    // ==================== GIỎ HÀNG (CART) ====================

    /**
     * Đồng bộ giỏ hàng lên Firebase — được gọi bởi thuật toán Debounce
     * API Contract: PUT carts/{userId}/{shopId} (SDK setValue)
     * Auth: Firebase Rules: auth.uid == userId
     */
    suspend fun syncCartToFirebase(payload: CartPayload) {
        val cartRef = database.getReference("carts")
            .child(payload.userId)
            .child(payload.shopId)

        if (payload.cart.items.isEmpty()) {
            cartRef.removeValue().await()
        } else {
            cartRef.setValue(payload.cart).await()
        }
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

            val now = System.currentTimeMillis()
            val targetStoreId = order.storeId.ifBlank { order.shopId }
            val targetAddress = order.deliveryAddress.ifBlank { order.address }
            val targetTotal = if (order.totalPayment > 0) order.totalPayment else order.total
            val targetStatus = order.status.ifBlank { order.orderStatus.ifBlank { Order.Status.PENDING } }
            val targetTimestamp = if (order.createdAt > 0) order.createdAt else if (order.timestamp > 0) order.timestamp else now

            var targetShopPhone = order.shopPhone.trim()
            var targetShopName = order.shopName.trim()
            if ((targetShopPhone.isBlank() || targetShopName.isBlank()) && targetStoreId.isNotBlank()) {
                try {
                    val storeSnap = database.getReference("stores").child(targetStoreId).get().await()
                    if (targetShopPhone.isBlank()) {
                        targetShopPhone = storeSnap.child("phone").getValue(String::class.java).orEmpty().trim()
                    }
                    if (targetShopName.isBlank()) {
                        targetShopName = storeSnap.child("name").getValue(String::class.java).orEmpty().trim()
                    }
                } catch (_: Exception) {}
            }

            val orderWithId = order.copy(
                orderId = orderId,
                storeId = targetStoreId,
                shopId = targetStoreId,
                shopName = targetShopName.ifBlank { order.shopName },
                shopPhone = targetShopPhone,
                deliveryAddress = targetAddress,
                address = targetAddress,
                totalPayment = targetTotal,
                total = targetTotal,
                status = targetStatus,
                orderStatus = targetStatus,
                createdAt = targetTimestamp,
                timestamp = targetTimestamp
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
        shipperPhone: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        val dbRef = database.getReference("orders").child(orderId)

        dbRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                // Nếu cache cục bộ chưa có dữ liệu, tiếp tục để Firebase tải từ server về
                if (currentData.value == null) {
                    return Transaction.success(currentData)
                }

                val currentShipperId = currentData.child("shipperId").getValue(String::class.java)

                // Kiểm tra xem đơn đã bị tài xế khác nhận chưa
                if (!currentShipperId.isNullOrBlank() && currentShipperId != "null") {
                    return Transaction.abort() // Xung đột, tài xế khác đã nhận
                }

                val now = System.currentTimeMillis()
                currentData.child("shipperId").value = shipperId
                currentData.child("driverId").value = shipperId
                currentData.child("shipperName").value = shipperName
                if (shipperPhone.isNotBlank()) {
                    currentData.child("shipperPhone").value = shipperPhone
                }
                currentData.child("status").value = Order.Status.ASSIGNED
                currentData.child("orderStatus").value = Order.Status.ASSIGNED
                currentData.child("updatedAt").value = now

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
            "orderStatus" to Order.Status.READY,
            "updatedAt" to System.currentTimeMillis()
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
    /** Lấy danh sách tất cả các quán ăn theo thời gian thực (cho Khách hàng mua món) */
    fun observeAllStores(): Flow<List<com.chuyen_de_2.foodorder.core.data.model.Store>> = callbackFlow {
        val ref = database.getReference("stores")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.Store>()
                for (child in snapshot.children) {
                    val store = child.getValue(com.chuyen_de_2.foodorder.core.data.model.Store::class.java)
                    if (store != null) {
                        val storeId = if (store.id.isBlank()) child.key ?: "" else store.id

                        // Đọc danh sách món ăn từ child "dishes" để tránh lỗi deserialization Map
                        val dishesMap = mutableMapOf<String, com.chuyen_de_2.foodorder.core.data.model.Dish>()
                        for (dChild in child.child("dishes").children) {
                            try {
                                val d = dChild.getValue(com.chuyen_de_2.foodorder.core.data.model.Dish::class.java)
                                if (d != null) {
                                    val dId = if (d.id.isBlank()) dChild.key ?: "" else d.id
                                    dishesMap[dId] = d.copy(id = dId, storeId = storeId)
                                }
                            } catch (e: Exception) {
                                val dName = dChild.child("name").getValue(String::class.java) ?: ""
                                val dPrice = dChild.child("basePrice").getValue(Long::class.java)
                                    ?: dChild.child("price").getValue(Long::class.java)
                                    ?: 0L
                                val dImg = dChild.child("imageUrl").getValue(String::class.java) ?: ""
                                val dCat = dChild.child("category").getValue(String::class.java) ?: ""
                                val dPlatformCat = dChild.child("platformCategory").getValue(String::class.java)
                                    ?: dChild.child("categoryId").getValue(String::class.java) ?: ""
                                if (dName.isNotBlank()) {
                                    val dId = dChild.key ?: ""
                                    dishesMap[dId] = com.chuyen_de_2.foodorder.core.data.model.Dish(
                                        id = dId,
                                        storeId = storeId,
                                        name = dName,
                                        basePrice = dPrice,
                                        imageUrl = dImg,
                                        category = dCat,
                                        platformCategory = dPlatformCat,
                                        categoryId = dPlatformCat
                                    )
                                }
                            }
                        }
                        val finalDishes = if (dishesMap.isNotEmpty()) dishesMap else store.dishes
                        val finalStore = store.copy(id = storeId, dishes = finalDishes)
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
        if (uid.isBlank()) return ""
        return try {
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
                    return child.key ?: ""
                }
            }
            // 3. Fallback lấy quán đầu tiên có trong database nếu có
            storesSnap.children.firstOrNull()?.key ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Xác định Quán ăn mà Tài xế (Shipper) trực thuộc.
     * Đảm bảo tài xế chỉ nhận đơn từ đúng quán đó theo phân quyền.
     * Trả về Pair(storeId, storeName).
     */
    suspend fun resolveStoreForDriver(driverUid: String): Pair<String, String> {
        if (driverUid.isBlank()) return Pair("", "")
        return try {
            var storeId = ""
            // 1. Kiểm tra users/{driverUid}/storeId
            val userSnap = database.getReference("users").child(driverUid).get().await()
            storeId = userSnap.child("storeId").getValue(String::class.java).orEmpty()

            // 2. Kiểm tra drivers/{driverUid}/storeId
            if (storeId.isBlank()) {
                val driverSnap = database.getReference("drivers").child(driverUid).get().await()
                storeId = driverSnap.child("storeId").getValue(String::class.java).orEmpty()
            }

            // 3. Tìm kiếm theo email hoặc phone trong danh sách drivers
            val userEmail = userSnap.child("email").getValue(String::class.java).orEmpty()
            val userPhone = userSnap.child("phone").getValue(String::class.java).orEmpty()
            if (storeId.isBlank() && (userEmail.isNotBlank() || userPhone.isNotBlank())) {
                val allDriversSnap = database.getReference("drivers").get().await()
                for (dSnap in allDriversSnap.children) {
                    val dEmail = dSnap.child("email").getValue(String::class.java).orEmpty()
                    val dPhone = dSnap.child("phone").getValue(String::class.java).orEmpty()
                    val sId = dSnap.child("storeId").getValue(String::class.java).orEmpty()
                    if (sId.isNotBlank() && ((userEmail.isNotBlank() && dEmail.equals(userEmail, ignoreCase = true)) ||
                                (userPhone.isNotBlank() && dPhone == userPhone))) {
                        storeId = sId
                        try {
                            database.getReference("users").child(driverUid).child("storeId").setValue(storeId)
                        } catch (_: Exception) {}
                        break
                    }
                }
            }

            var storeName = ""
            if (storeId.isNotBlank()) {
                val storeSnap = database.getReference("stores").child(storeId).get().await()
                storeName = storeSnap.child("name").getValue(String::class.java).orEmpty()
                if (storeName.isBlank()) {
                    storeName = storeSnap.child("storeName").getValue(String::class.java).orEmpty()
                }
            }

            Pair(storeId, storeName)
        } catch (_: Exception) {
            Pair("", "")
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

    /** Cập nhật thông tin hồ sơ quán ăn lên Firebase (bao gồm tài khoản ngân hàng) */
    suspend fun updateStoreProfile(
        storeId: String,
        name: String,
        phone: String,
        address: String,
        district: String,
        ownerName: String,
        openHours: String,
        prepTime: String,
        bankName: String = "",
        bankAccountNumber: String = "",
        bankAccountName: String = "",
        bankBin: String = "",
        imageUrl: String = "",
        latitude: Double = 0.0,
        longitude: Double = 0.0
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "name" to name,
                "phone" to phone,
                "address" to address,
                "district" to district,
                "ownerName" to ownerName,
                "openHours" to openHours,
                "prepTime" to prepTime
            )
            if (bankName.isNotBlank()) updates["bankName"] = bankName
            if (bankAccountNumber.isNotBlank()) updates["bankAccountNumber"] = bankAccountNumber
            if (bankAccountName.isNotBlank()) updates["bankAccountName"] = bankAccountName
            if (bankBin.isNotBlank()) updates["bankBin"] = bankBin
            if (imageUrl.isNotBlank()) updates["imageUrl"] = imageUrl
            if (latitude != 0.0) updates["latitude"] = latitude
            if (longitude != 0.0) updates["longitude"] = longitude

            database.getReference("stores").child(storeId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lấy thông tin hồ sơ quán một lần (Snapshot) */
    suspend fun getStoreOnce(storeId: String): com.chuyen_de_2.foodorder.core.data.model.Store? {
        if (storeId.isBlank()) return null
        return try {
            val snap = database.getReference("stores").child(storeId).get().await()
            if (!snap.exists()) null
            else {
                val store = snap.getValue(com.chuyen_de_2.foodorder.core.data.model.Store::class.java)
                val isVer = snap.child("isVerified").getValue(Boolean::class.java)
                    ?: snap.child("verified").getValue(Boolean::class.java)
                    ?: store?.isVerified
                    ?: false
                val addr = snap.child("address").getValue(String::class.java) ?: store?.address.orEmpty()
                val dist = snap.child("district").getValue(String::class.java) ?: store?.district.orEmpty()
                val cccd = snap.child("cccdNumber").getValue(String::class.java) ?: store?.cccdNumber.orEmpty()
                val owner = snap.child("ownerName").getValue(String::class.java) ?: store?.ownerName.orEmpty()
                store?.copy(
                    isVerified = isVer,
                    address = addr,
                    district = dist,
                    cccdNumber = cccd,
                    ownerName = owner
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Xác minh danh tính và cập nhật đầy đủ phiếu thông tin địa chỉ quán */
    suspend fun verifyAndCompleteStoreProfile(
        storeId: String,
        merchantUid: String = "",
        name: String,
        phone: String,
        address: String,
        district: String,
        ownerName: String,
        cccdNumber: String,
        bankName: String,
        bankAccountNumber: String,
        bankAccountName: String,
        bankBin: String = "",
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        openHours: String = "08:00 - 22:30",
        prepTime: String = "15 phút"
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "id" to storeId,
                "name" to name.trim(),
                "phone" to phone.trim(),
                "address" to address.trim(),
                "district" to district.trim(),
                "ownerName" to ownerName.trim(),
                "cccdNumber" to cccdNumber.trim(),
                "bankName" to bankName.trim(),
                "bankAccountNumber" to bankAccountNumber.trim(),
                "bankAccountName" to bankAccountName.trim().uppercase(),
                "openHours" to openHours.trim(),
                "prepTime" to prepTime.trim(),
                "isVerified" to true,
                "verified" to true,
                "storeStatus" to com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.OPEN,
                "status" to com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.OPEN,
                "open" to true
            )
            if (merchantUid.isNotBlank()) {
                updates["merchantId"] = merchantUid
                updates["ownerId"] = merchantUid
                try {
                    database.getReference("users").child(merchantUid).child("storeId").setValue(storeId).await()
                } catch (_: Exception) {}
            }
            if (bankBin.isNotBlank()) updates["bankBin"] = bankBin.trim()
            if (latitude != 0.0) updates["latitude"] = latitude
            if (longitude != 0.0) updates["longitude"] = longitude

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

    // ==================== DANH MỤC NỀN TẢNG (CATEGORIES) ====================

    /** Lắng nghe danh sách danh mục nền tảng theo thời gian thực (từ Firebase) */
    fun observeCategories(includeInactive: Boolean = false): Flow<List<com.chuyen_de_2.foodorder.core.data.model.Category>> = callbackFlow {
        val ref = database.getReference("categories")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.Category>()
                for (child in snapshot.children) {
                    val catId = child.key ?: ""
                    val catName = child.child("categoryName").getValue(String::class.java)
                        ?: child.child("name").getValue(String::class.java) ?: ""
                    val iconUrl = (child.child("iconUrl").getValue(String::class.java)
                        ?: child.child("icon").getValue(String::class.java) ?: "").let { if (it.startsWith("http", true) || it.startsWith("data:image", true) || it.length > 100) it else "" }
                    val desc = child.child("description").getValue(String::class.java) ?: child.child("desc").getValue(String::class.java) ?: ""; val displayOrder = child.child("displayOrder").getValue(Int::class.java) ?: 99
                    val isActive = child.child("isActive").getValue(Boolean::class.java)
                        ?: (child.child("status").getValue(String::class.java) != "HIDDEN")

                    if (catName.isNotBlank() && (includeInactive || isActive)) {
                        list.add(com.chuyen_de_2.foodorder.core.data.model.Category(
                            categoryId = catId,
                            categoryName = catName,
                            iconUrl = iconUrl,
                            displayOrder = displayOrder,
                            isActive = isActive, description = desc
                        ))
                    }
                }
                if (list.isEmpty()) {
                    // Fallback sang menus/categories nếu categories chưa có
                    database.getReference("menus/categories").get().addOnSuccessListener { mSnap ->
                        val fallbackList = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.Category>()
                        for (child in mSnap.children) {
                            val catId = child.key ?: ""
                            val catName = child.child("categoryName").getValue(String::class.java)
                                ?: child.child("name").getValue(String::class.java) ?: ""
                            val iconUrl = (child.child("iconUrl").getValue(String::class.java)
                                ?: child.child("icon").getValue(String::class.java) ?: "").let { if (it.startsWith("http", true) || it.startsWith("data:image", true) || it.length > 100) it else "" }
                            val desc = child.child("description").getValue(String::class.java) ?: child.child("desc").getValue(String::class.java) ?: ""; val displayOrder = child.child("displayOrder").getValue(Int::class.java) ?: 99
                            val isActive = child.child("isActive").getValue(Boolean::class.java)
                                ?: (child.child("status").getValue(String::class.java) != "HIDDEN")

                            if (catName.isNotBlank() && (includeInactive || isActive)) {
                                fallbackList.add(com.chuyen_de_2.foodorder.core.data.model.Category(
                                    categoryId = catId,
                                    categoryName = catName,
                                    iconUrl = iconUrl,
                                    displayOrder = displayOrder,
                                    isActive = isActive, description = desc
                                ))
                            }
                        }
                        if (fallbackList.isNotEmpty()) {
                            fallbackList.sortBy { it.displayOrder }
                            trySend(fallbackList)
                        } else {
                            trySend(Category.getDefaultCategories().filter { includeInactive || it.isActive })
                        }
                    }.addOnFailureListener {
                        trySend(Category.getDefaultCategories().filter { includeInactive || it.isActive })
                    }
                } else {
                    list.sortBy { it.displayOrder }
                    trySend(list)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close()
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Lưu / cập nhật danh mục nền tảng lên Firebase */
    suspend fun saveCategory(category: com.chuyen_de_2.foodorder.core.data.model.Category, desc: String = ""): Result<Unit> {
        return try {
            val id = category.categoryId.ifBlank { "cat-${System.currentTimeMillis()}" }
            val finalDesc = if (desc.isNotBlank()) desc else category.description
            val map = mapOf<String, Any>(
                "id" to id,
                "categoryId" to id,
                "name" to category.categoryName,
                "categoryName" to category.categoryName,
                "icon" to category.iconUrl,
                "iconUrl" to category.iconUrl,
                "desc" to finalDesc,
                "description" to finalDesc,
                "displayOrder" to category.displayOrder,
                "isActive" to category.isActive,
                "status" to (if (category.isActive) "ACTIVE" else "HIDDEN"),
                "updatedAt" to System.currentTimeMillis()
            )
            val updates = mapOf<String, Any>(
                "categories/$id" to map,
                "menus/categories/$id" to map
            )
            database.reference.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Xóa danh mục nền tảng khỏi Firebase */
    /** Bật / tắt hiển thị danh mục trên ứng dụng Khách */
    suspend fun toggleCategoryActive(categoryId: String, isActive: Boolean): Result<Unit> {
        return try {
            val status = if (isActive) "ACTIVE" else "HIDDEN"
            val updates = mapOf<String, Any?>(
                "categories/$categoryId/isActive" to isActive,
                "categories/$categoryId/status" to status,
                "menus/categories/$categoryId/isActive" to isActive,
                "menus/categories/$categoryId/status" to status
            )
            database.reference.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        return try {
            val updates = mapOf<String, Any?>(
                "categories/$categoryId" to null,
                "menus/categories/$categoryId" to null
            )
            database.reference.updateChildren(updates).await()
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

    /** Xóa tài xế khỏi đội ngũ của quán và thu hồi các đơn đang gán */
    suspend fun deleteDriver(driverId: String): Result<Unit> {
        return try {
            database.getReference("drivers").child(driverId).removeValue().await()
            database.getReference("users").child(driverId).removeValue().await()

            // Thu hồi các đơn hàng đang giao của tài xế này về trạng thái READY để tài xế khác nhận
            val ordersSnap = database.getReference("orders")
                .orderByChild("shipperId")
                .equalTo(driverId)
                .get()
                .await()

            for (child in ordersSnap.children) {
                val order = child.getValue(Order::class.java) ?: continue
                if (order.status != Order.Status.COMPLETED &&
                    order.status != Order.Status.CANCELLED &&
                    order.status != Order.Status.BOOM
                ) {
                    val updates = mapOf<String, Any?>(
                        "shipperId" to null,
                        "shipperName" to "",
                        "status" to Order.Status.READY
                    )
                    database.getReference("orders").child(order.orderId).updateChildren(updates).await()
                    database.getReference("tracking").child(order.orderId).removeValue().await()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Dọn dẹp toàn bộ dữ liệu của các tài khoản đã bị xóa:
     * 1. Xóa giỏ hàng mồ côi (carts)
     * 2. Xóa hồ sơ tài xế mồ côi (drivers)
     * 3. Xóa hồ sơ khách hàng mồ côi (customers)
     * 4. Xóa đơn hàng của khách đã bị xóa tài khoản
     * 5. Thu hồi đơn hàng đang bị gán bởi tài xế đã bị xóa (trả về READY)
     * 6. Xóa tracking GPS mồ côi
     */
    suspend fun cleanupOrphanedData(): Result<Int> {
        return try {
            val usersSnap = database.getReference("users").get().await()
            val validUserIds = usersSnap.children.mapNotNull { it.key }.toSet()

            var cleanedCount = 0

            // 1. Dọn dẹp giỏ hàng mồ côi (carts)
            try {
                val cartsSnap = database.getReference("carts").get().await()
                for (cartChild in cartsSnap.children) {
                    val userId = cartChild.key ?: continue
                    if (!validUserIds.contains(userId)) {
                        database.getReference("carts").child(userId).removeValue().await()
                        cleanedCount++
                    }
                }
            } catch (_: Exception) {}

            // 2. Dọn dẹp hồ sơ khách hàng mồ côi (customers)
            try {
                val customersSnap = database.getReference("customers").get().await()
                for (cusChild in customersSnap.children) {
                    val cusId = cusChild.key ?: continue
                    if (!validUserIds.contains(cusId)) {
                        database.getReference("customers").child(cusId).removeValue().await()
                        cleanedCount++
                    }
                }
            } catch (_: Exception) {}

            // 3. Dọn dẹp node drivers mồ côi
            try {
                val driversSnap = database.getReference("drivers").get().await()
                for (driverChild in driversSnap.children) {
                    val driverId = driverChild.key ?: continue
                    if (!validUserIds.contains(driverId)) {
                        database.getReference("drivers").child(driverId).removeValue().await()
                        cleanedCount++
                    }
                }
            } catch (_: Exception) {}

            // 4. Kiểm tra đơn hàng (orders) và thu hồi đơn nếu tài xế bị xóa tài khoản (TUYỆT ĐỐI KHÔNG XÓA ĐƠN HÀNG)
            val validOrderIds = mutableSetOf<String>()
            try {
                val ordersSnap = database.getReference("orders").get().await()
                for (orderChild in ordersSnap.children) {
                    val order = orderChild.getValue(Order::class.java) ?: continue
                    val orderId = orderChild.key ?: order.orderId
                    validOrderIds.add(orderId)

                    // Đơn đang bị gán bởi tài xế đã bị xóa -> thu hồi đơn, đưa về READY để tài xế khác nhận
                    val shipperId = order.shipperId
                    if (!shipperId.isNullOrBlank() && shipperId != "null" && !validUserIds.contains(shipperId)) {
                        if (order.status != Order.Status.COMPLETED &&
                            order.status != Order.Status.CANCELLED &&
                            order.status != Order.Status.BOOM
                        ) {
                            val updates = mapOf<String, Any?>(
                                "shipperId" to null,
                                "shipperName" to "",
                                "status" to Order.Status.READY
                            )
                            database.getReference("orders").child(orderId).updateChildren(updates).await()
                            database.getReference("tracking").child(orderId).removeValue().await()
                            cleanedCount++
                        }
                    }
                }
            } catch (_: Exception) {}

            // 5. Dọn dẹp tracking mồ côi không thuộc về đơn nào
            try {
                val trackingSnap = database.getReference("tracking").get().await()
                for (trackChild in trackingSnap.children) {
                    val trackOrderId = trackChild.key ?: continue
                    if (!validOrderIds.contains(trackOrderId)) {
                        database.getReference("tracking").child(trackOrderId).removeValue().await()
                        cleanedCount++
                    }
                }
            } catch (_: Exception) {}

            // 6. Dọn dẹp đánh giá (dish_reviews) và lịch sử điểm uy tín (trust_score_logs) mồ côi
            try {
                val reviewsSnap = database.getReference("dish_reviews").get().await()
                for (revChild in reviewsSnap.children) {
                    val rev = revChild.getValue(DishReview::class.java) ?: continue
                    val revKey = revChild.key ?: rev.reviewId
                    if (rev.customerId.isNotBlank() && !validUserIds.contains(rev.customerId)) {
                        database.getReference("dish_reviews").child(revKey).removeValue().await()
                        cleanedCount++
                    }
                }
            } catch (_: Exception) {}

            try {
                val logsSnap = database.getReference("trust_score_logs").get().await()
                for (logChild in logsSnap.children) {
                    val log = logChild.getValue(TrustScoreLog::class.java) ?: continue
                    val logKey = logChild.key ?: log.logId
                    if (log.customerId.isNotBlank() && !validUserIds.contains(log.customerId)) {
                        database.getReference("trust_score_logs").child(logKey).removeValue().await()
                        cleanedCount++
                    }
                }
            } catch (_: Exception) {}

            Result.success(cleanedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Khởi tạo tài xế mẫu nếu cần (đã tắt để ưu tiên dữ liệu thật do quán tự tạo) */
    suspend fun seedDefaultDriversIfEmpty(storeId: String) {
        // Tắt seed tự động để bảo toàn dữ liệu thật của quán
    }

    // ==================== SỔ ĐỊA CHỈ KHÁCH HÀNG (CUSTOMER ADDRESSES) ====================

    /** Lắng nghe danh sách địa chỉ đã lưu của khách hàng */
    fun observeCustomerAddresses(customerId: String): Flow<List<CustomerAddress>> = callbackFlow {
        if (customerId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = database.getReference("customer_addresses").child(customerId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<CustomerAddress>()
                for (child in snapshot.children) {
                    val addr = child.getValue(CustomerAddress::class.java)
                    if (addr != null) {
                        list.add(addr.copy(addressId = child.key ?: addr.addressId, customerId = customerId))
                    }
                }
                // Sắp xếp: địa chỉ mặc định lên đầu
                list.sortByDescending { it.isDefault }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Lấy danh sách địa chỉ 1 lần */
    suspend fun getCustomerAddresses(customerId: String): List<CustomerAddress> {
        if (customerId.isBlank()) return emptyList()
        return try {
            val snapshot = database.getReference("customer_addresses").child(customerId).get().await()
            val list = mutableListOf<CustomerAddress>()
            for (child in snapshot.children) {
                val addr = child.getValue(CustomerAddress::class.java)
                if (addr != null) {
                    list.add(addr.copy(addressId = child.key ?: addr.addressId, customerId = customerId))
                }
            }
            list.sortedByDescending { it.isDefault }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Thêm hoặc cập nhật địa chỉ đã lưu */
    suspend fun saveCustomerAddress(customerId: String, address: CustomerAddress): Result<String> {
        if (customerId.isBlank()) return Result.failure(Exception("Chưa đăng nhập"))
        return try {
            val ref = database.getReference("customer_addresses").child(customerId)
            val addrId = if (address.addressId.isNotBlank()) address.addressId else ref.push().key ?: "addr_${System.currentTimeMillis()}"

            // Nếu đặt làm mặc định -> bỏ mặc định của các địa chỉ khác
            if (address.isDefault) {
                val snapshot = ref.get().await()
                for (child in snapshot.children) {
                    if (child.key != addrId) {
                        child.ref.child("isDefault").setValue(false).await()
                    }
                }
            }

            val toSave = address.copy(addressId = addrId, customerId = customerId)
            ref.child(addrId).setValue(toSave).await()
            Result.success(addrId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Xóa địa chỉ */
    suspend fun deleteCustomerAddress(customerId: String, addressId: String): Result<Unit> {
        if (customerId.isBlank() || addressId.isBlank()) return Result.failure(Exception("Tham số không hợp lệ"))
        return try {
            database.getReference("customer_addresses").child(customerId).child(addressId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Đặt làm địa chỉ mặc định */
    suspend fun setDefaultAddress(customerId: String, addressId: String): Result<Unit> {
        if (customerId.isBlank() || addressId.isBlank()) return Result.failure(Exception("Tham số không hợp lệ"))
        return try {
            val ref = database.getReference("customer_addresses").child(customerId)
            val snapshot = ref.get().await()
            for (child in snapshot.children) {
                val isTarget = (child.key == addressId)
                child.ref.child("isDefault").setValue(isTarget).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== QUẢN TRỊ VIÊN HỆ THỐNG (ADMIN PORTAL) ====================

    /** Lắng nghe toàn bộ khiếu nại theo thời gian thực (cho Admin xử lý) */
    fun observeAllComplaints(): Flow<List<com.chuyen_de_2.foodorder.core.data.model.Complaint>> = callbackFlow {
        val ref = database.getReference("complaints")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.Complaint>()
                for (child in snapshot.children) {
                    val c = child.getValue(com.chuyen_de_2.foodorder.core.data.model.Complaint::class.java)
                    if (c != null) {
                        val cId = if (c.complaintId.isBlank()) child.key ?: "" else c.complaintId
                        list.add(c.copy(complaintId = cId))
                    }
                }
                trySend(list.sortedByDescending { it.createdAt })
            }

            override fun onCancelled(error: DatabaseError) {
                close()
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Xử lý khiếu nại và áp dụng mức phạt cho Quán ăn */
    suspend fun resolveComplaint(
        complaintId: String,
        status: String,
        penaltyApplied: String = "NONE",
        adminNote: String = "",
        storeId: String = ""
    ): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "status" to status,
                "penaltyApplied" to penaltyApplied,
                "adminNote" to adminNote
            )
            database.getReference("complaints").child(complaintId).updateChildren(updates).await()

            if (storeId.isNotBlank() && penaltyApplied != "NONE") {
                updateStorePenalty(storeId, penaltyApplied, adminNote)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Cập nhật mức chế tài xử phạt Quán ăn (WARN, HIDE, LOCK) */
    suspend fun updateStorePenalty(
        storeId: String,
        penaltyTier: String,
        reason: String
    ): Result<Unit> {
        return try {
            val tierId = when (penaltyTier) {
                com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.WARN -> 1
                com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.HIDE -> 2
                com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.LOCK -> 3
                else -> 0
            }
            val storeStatus = if (penaltyTier == com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.LOCK) {
                com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.LOCKED
            } else {
                com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.OPEN
            }
            val updates = mapOf<String, Any>(
                "penaltyTier" to penaltyTier,
                "penaltyTierId" to tierId,
                "penaltyReason" to reason,
                "storeStatus" to storeStatus,
                "status" to storeStatus,
                "open" to (storeStatus == com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.OPEN)
            )
            database.getReference("stores").child(storeId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Duyệt hoặc từ chối hồ sơ Quán ăn (Admin CCCD review) */
    suspend fun verifyStore(storeId: String, isApproved: Boolean): Result<Unit> {
        return try {
            val status = if (isApproved) com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.OPEN 
                         else com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.LOCKED
            val updates = mapOf<String, Any>(
                "isVerified" to isApproved,
                "verified" to isApproved,
                "storeStatus" to status,
                "status" to status,
                "open" to isApproved
            )
            database.getReference("stores").child(storeId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lắng nghe toàn bộ tài khoản Users */
    fun observeAllUsers(): Flow<List<com.chuyen_de_2.foodorder.core.data.model.User>> = callbackFlow {
        val ref = database.getReference("users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.User>()
                for (child in snapshot.children) {
                    val u = child.getValue(com.chuyen_de_2.foodorder.core.data.model.User::class.java)
                    if (u != null) {
                        val emailLower = u.email.trim().lowercase(); if (emailLower == "admin@foodorder.com" || emailLower == "admin.long@oishifood.vn") { try { child.ref.removeValue() } catch (_: Exception) {}; continue }; val uid = if (u.uid.isBlank()) child.key ?: "" else u.uid
                        list.add(u.copy(uid = uid))
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

    /** Khóa / Mở khóa tài khoản người dùng */
    suspend fun updateUserStatus(uid: String, status: String, isActive: Boolean): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "status" to status,
                "isActive" to isActive
            )
            database.getReference("users").child(uid).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Quản trị viên cập nhật toàn bộ thuộc tính của quán ăn */
    suspend fun saveStoreFull(store: com.chuyen_de_2.foodorder.core.data.model.Store): Result<Unit> {
        return try {
            val storeId = store.id.ifBlank { "store_${System.currentTimeMillis()}" }
            val finalStore = store.copy(id = storeId)
            val updates = mapOf<String, Any?>(
                "id" to finalStore.id,
                "name" to finalStore.name,
                "phone" to finalStore.phone,
                "address" to finalStore.address,
                "district" to finalStore.district,
                "latitude" to finalStore.latitude,
                "longitude" to finalStore.longitude,
                "imageUrl" to finalStore.imageUrl,
                "storeStatus" to finalStore.storeStatus,
                "status" to finalStore.storeStatus,
                "open" to (finalStore.storeStatus == com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.OPEN),
                "statusId" to finalStore.statusId,
                "ownerName" to finalStore.ownerName,
                "openHours" to finalStore.openHours,
                "prepTime" to finalStore.prepTime,
                "isVerified" to finalStore.isVerified,
                "verified" to finalStore.isVerified,
                "cccdNumber" to finalStore.cccdNumber,
                "cccdFrontUrl" to finalStore.cccdFrontUrl,
                "cccdBackUrl" to finalStore.cccdBackUrl,
                "cccdHoldUrl" to finalStore.cccdHoldUrl,
                "cccdPortraitUrl" to finalStore.cccdPortraitUrl,
                "penaltyTier" to finalStore.penaltyTier,
                "penaltyReason" to finalStore.penaltyReason,
                "ratingAvg" to finalStore.ratingAvg,
                "rating" to finalStore.ratingAvg,
                "deliveryTime" to finalStore.deliveryTime,
                "category" to finalStore.category,
                "bankName" to finalStore.bankName,
                "bankAccountNumber" to finalStore.bankAccountNumber,
                "bankAccountName" to finalStore.bankAccountName
            )
            database.getReference("stores").child(storeId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Quản trị viên cập nhật toàn bộ thuộc tính của người dùng */
    suspend fun saveUserFull(user: com.chuyen_de_2.foodorder.core.data.model.User): Result<Unit> {
        return try {
            val uid = user.uid.ifBlank { return Result.failure(IllegalArgumentException("User UID cannot be empty")) }
            val updates = mapOf<String, Any?>(
                "uid" to uid,
                "name" to user.name,
                "email" to user.email,
                "phone" to user.phone,
                "role" to user.role,
                "roleId" to user.roleId,
                "isActive" to user.isActive,
                "status" to user.status,
                "avatarUrl" to user.avatarUrl,
                "storeId" to user.storeId
            )
            database.getReference("users").child(uid).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Quản trị viên cập nhật toàn bộ thuộc tính của đơn hàng */
    suspend fun saveOrderFull(order: com.chuyen_de_2.foodorder.core.data.model.Order): Result<Unit> {
        return try {
            val orderId = order.orderId.ifBlank { return Result.failure(IllegalArgumentException("Order ID cannot be empty")) }
            val updates = mapOf<String, Any?>(
                "orderStatus" to order.orderStatus,
                "status" to order.orderStatus,
                "paymentMethod" to order.paymentMethod,
                "note" to order.note,
                "address" to order.address,
                "deliveryAddress" to order.deliveryAddress,
                "shippingFee" to order.shippingFee,
                "totalPayment" to order.totalPayment,
                "shipperName" to order.shipperName,
                "shipperPhone" to order.shipperPhone
            )
            database.getReference("orders").child(orderId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lắng nghe hồ sơ Customers kèm điểm uy tín */
    fun observeAllCustomers(): Flow<List<com.chuyen_de_2.foodorder.core.data.model.Customer>> = callbackFlow {
        val ref = database.getReference("customers")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.Customer>()
                for (child in snapshot.children) {
                    val c = child.getValue(com.chuyen_de_2.foodorder.core.data.model.Customer::class.java)
                    if (c != null) {
                        val cId = if (c.customerId.isBlank()) child.key ?: "" else c.customerId
                        list.add(c.copy(customerId = cId))
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

    /** Danh mục nền tảng mặc định tiêu chuẩn */
    fun getDefaultCategories(): List<com.chuyen_de_2.foodorder.core.data.model.Category> =
        com.chuyen_de_2.foodorder.core.data.model.Category.getDefaultCategories()

    /** Khôi phục 12 danh mục chuẩn hệ thống Oishi Food */
    suspend fun restoreDefaultCategories(): Result<Unit> {
        return try {
            val defaults = getDefaultCategories()
            for (cat in defaults) {
                saveCategory(cat)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

