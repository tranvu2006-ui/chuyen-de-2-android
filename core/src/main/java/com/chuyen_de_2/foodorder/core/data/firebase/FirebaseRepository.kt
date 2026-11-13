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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

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
            database.getReference("categories").keepSynced(true)
            database.getReference("stores").keepSynced(true)
            database.getReference("users").keepSynced(true)
            database.getReference("customers").keepSynced(true)
            database.getReference("drivers").keepSynced(true)
        } catch (_: Exception) {}
    }

    /** Đọc nhanh từ bộ nhớ đệm đã keepSynced (<1ms) thay vì chờ round-trip mạng từ .get().await() */
    suspend fun readCacheFirst(ref: com.google.firebase.database.Query): DataSnapshot? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (cont.isActive) cont.resume(snapshot, null)
                }
                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) cont.resume(null, null)
                }
            })
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
     * Sinh mã đơn hàng chuẩn thống nhất toàn hệ thống: OISHI_******
     * - Tiền tố: OISHI_
     * - ******: 6 ký tự ngẫu nhiên gồm chữ số (0-9) hoặc chữ cái in hoa (A-Z)
     */
    fun generateOrderCode(): String {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val randomPart = (1..6)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
        return "OISHI_$randomPart"
    }

    /**
     * Tạo đơn hàng mới
     * Mã đơn hàng đồng bộ toàn hệ thống: OISHI_******
     * Node Firebase: orders/{orderId}
     * Auth: Phải có Role CUSTOMER
     */
    suspend fun createOrder(order: Order): Result<String> {
        return try {
            val orderId = if (order.orderId.isNotBlank() && order.orderId.startsWith("OISHI", ignoreCase = true)) {
                order.orderId
            } else {
                generateOrderCode()
            }
            val ref = database.getReference("orders").child(orderId)

            val now = System.currentTimeMillis()
            val targetStoreId = order.storeId.ifBlank { order.shopId }
            val targetAddress = order.deliveryAddress.ifBlank { order.address }
            val targetTotal = if (order.totalPayment > 0) order.totalPayment else order.total
            val targetStatus = order.status.ifBlank { order.orderStatus.ifBlank { Order.Status.PENDING } }
            val targetTimestamp = if (order.createdAt > 0) order.createdAt else if (order.timestamp > 0) order.timestamp else now

            var targetShopPhone = order.shopPhone.trim()
            var targetShopName = order.shopName.trim()
            var targetShopImageUrl = order.shopImageUrl.trim()
            if ((targetShopPhone.isBlank() || targetShopName.isBlank() || targetShopImageUrl.isBlank()) && targetStoreId.isNotBlank()) {
                try {
                    val storeSnap = readCacheFirst(database.getReference("stores").child(targetStoreId))
                    if (storeSnap != null) {
                        if (targetShopPhone.isBlank()) {
                            targetShopPhone = storeSnap.child("phone").getValue(String::class.java).orEmpty().trim()
                        }
                        if (targetShopName.isBlank()) {
                            targetShopName = storeSnap.child("name").getValue(String::class.java).orEmpty().trim()
                        }
                        if (targetShopImageUrl.isBlank()) {
                            val storeObj = storeSnap.getValue(com.chuyen_de_2.foodorder.core.data.model.Store::class.java)
                            targetShopImageUrl = storeObj?.effectiveImageUrl.orEmpty().trim()
                        }
                    }
                } catch (_: Exception) {}
            }

            val orderWithId = order.copy(
                orderId = orderId,
                storeId = targetStoreId,
                shopId = targetStoreId,
                shopName = targetShopName.ifBlank { order.shopName },
                shopPhone = targetShopPhone,
                shopImageUrl = targetShopImageUrl,
                deliveryAddress = targetAddress,
                address = targetAddress,
                totalPayment = targetTotal,
                total = targetTotal,
                status = targetStatus,
                orderStatus = targetStatus,
                createdAt = targetTimestamp,
                timestamp = targetTimestamp
            )
            // Ghi tức thời vào cache cục bộ + đẩy qua WebSocket không chặn UI bởi độ trễ ACK
            ref.setValue(orderWithId)

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

    /** Khách hàng hủy đơn trực tiếp khi đơn chưa được quán xác nhận (PENDING, PAID, AWAITING_PAYMENT) */
    suspend fun cancelOrderImmediately(orderId: String, reason: String = "") {
        val finalReason = reason.ifBlank { "Khách hàng hủy do đặt nhầm (trước khi quán xác nhận)" }
        val updates = mapOf<String, Any>(
            "status" to Order.Status.CANCELLED,
            "orderStatus" to Order.Status.CANCELLED,
            "cancelReason" to finalReason,
            "cancelRequested" to false,
            "cancelRequestStatus" to "",
            "updatedAt" to System.currentTimeMillis()
        )
        database.getReference("orders").child(orderId).updateChildren(updates)
        try { database.getReference("tracking").child(orderId).removeValue() } catch (_: Exception) {}
    }

    /** Khách hàng gửi yêu cầu hủy đơn đến quán khi đơn đã được quán xác nhận đang nấu (PREPARING, READY) */
    suspend fun requestCancelOrder(orderId: String, reason: String = "") {
        val finalReason = reason.ifBlank { "Khách hàng yêu cầu hủy đơn do đặt nhầm" }
        val updates = mapOf<String, Any>(
            "cancelRequested" to true,
            "cancelRequestReason" to finalReason,
            "cancelRequestTime" to System.currentTimeMillis(),
            "cancelRequestStatus" to Order.CancelRequestStatus.PENDING,
            "cancelRejectReason" to "",
            "updatedAt" to System.currentTimeMillis()
        )
        database.getReference("orders").child(orderId).updateChildren(updates)
    }

    /** Khách hàng rút lại yêu cầu hủy đơn */
    suspend fun withdrawCancelRequest(orderId: String) {
        val updates = mapOf<String, Any>(
            "cancelRequested" to false,
            "cancelRequestStatus" to Order.CancelRequestStatus.WITHDRAWN,
            "updatedAt" to System.currentTimeMillis()
        )
        database.getReference("orders").child(orderId).updateChildren(updates)
    }

    /** Quán đồng ý yêu cầu hủy đơn của khách (chuyển sang CANCELLED, không phạt điểm uy tín) */
    suspend fun approveCancelOrder(orderId: String, reason: String = "") {
        val finalReason = if (reason.isNotBlank()) {
            "Quán đồng ý hủy theo yêu cầu của khách: $reason"
        } else {
            "Quán đồng ý hủy theo yêu cầu của khách"
        }
        val updates = mapOf<String, Any>(
            "status" to Order.Status.CANCELLED,
            "orderStatus" to Order.Status.CANCELLED,
            "cancelReason" to finalReason,
            "cancelRequested" to false,
            "cancelRequestStatus" to Order.CancelRequestStatus.APPROVED,
            "updatedAt" to System.currentTimeMillis()
        )
        database.getReference("orders").child(orderId).updateChildren(updates)
        try { database.getReference("tracking").child(orderId).removeValue() } catch (_: Exception) {}
    }

    /** Quán từ chối yêu cầu hủy đơn của khách */
    suspend fun rejectCancelOrder(orderId: String, rejectReason: String = "") {
        val finalReject = rejectReason.ifBlank { "Quán đã nấu xong món ăn, không thể hủy" }
        val updates = mapOf<String, Any>(
            "cancelRequested" to false,
            "cancelRequestStatus" to Order.CancelRequestStatus.REJECTED,
            "cancelRejectReason" to finalReject,
            "updatedAt" to System.currentTimeMillis()
        )
        database.getReference("orders").child(orderId).updateChildren(updates)
    }

    /** Cập nhật trạng thái đơn hàng tức thời (<10ms) và xử lý điểm uy tín bất đồng bộ ở nền */
    suspend fun updateOrderStatus(
        orderId: String,
        status: String,
        reason: String = "",
        targetCustomerId: String = ""
    ) {
        val normalizedStatus = status.uppercase()
        val needsTrustScoreUpdate = normalizedStatus == Order.Status.COMPLETED ||
                normalizedStatus == Order.Status.BOOM ||
                normalizedStatus == Order.Status.RETURNING_TO_STORE ||
                normalizedStatus == Order.Status.RETURNED_TO_STORE ||
                normalizedStatus == Order.Status.CANCELLED

        // Chỉ đọc snapshot trước đó từ bộ nhớ đệm (<1ms) khi cần tính điểm uy tín
        val prevOrderSnap = if (needsTrustScoreUpdate) {
            try { readCacheFirst(database.getReference("orders").child(orderId)) } catch (_: Exception) { null }
        } else null
        val prevStatus = prevOrderSnap?.child("status")?.getValue(String::class.java)?.uppercase().orEmpty()

        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "orderStatus" to status,
            "updatedAt" to System.currentTimeMillis()
        )
        if (reason.isNotBlank()) updates["cancelReason"] = reason

        // Cập nhật trạng thái đơn hàng NGAY LẬP TỨC vào cache + đẩy socket realtime (không chờ .await() chặn luồng)
        database.getReference("orders").child(orderId).updateChildren(updates)

        if (normalizedStatus == Order.Status.COMPLETED || normalizedStatus == Order.Status.BOOM || normalizedStatus == Order.Status.CANCELLED) {
            try { database.getReference("tracking").child(orderId).removeValue() } catch (_: Exception) {}
        }

        if (!needsTrustScoreUpdate || prevStatus == normalizedStatus) return

        // Xử lý cộng/trừ điểm uy tín ở luồng nền để UI chuyển bước tức thì 0ms
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val customerId = targetCustomerId.ifBlank {
                    prevOrderSnap?.child("customerId")?.getValue(String::class.java).orEmpty()
                        .ifBlank { prevOrderSnap?.child("userId")?.getValue(String::class.java).orEmpty() }
                }
                if (customerId.isNotBlank()) {
                    val userRef = database.getReference("users").child(customerId)
                    val custNodeRef = database.getReference("customers").child(customerId)
                    val userSnap = try { readCacheFirst(userRef) } catch (_: Exception) { null }
                    val custNodeSnap = try { readCacheFirst(custNodeRef) } catch (_: Exception) { null }

                    val currentCompleted = maxOf(
                        custNodeSnap?.child("completedOrders")?.getValue(Int::class.java)?.toLong()
                            ?: custNodeSnap?.child("completedOrders")?.getValue(Long::class.java)
                            ?: userSnap?.child("completedOrders")?.getValue(Int::class.java)?.toLong()
                            ?: userSnap?.child("completedOrders")?.getValue(Long::class.java)
                            ?: 0L
                    )
                    val currentTotal = maxOf(
                        custNodeSnap?.child("totalOrders")?.getValue(Int::class.java)?.toLong()
                            ?: custNodeSnap?.child("totalOrders")?.getValue(Long::class.java)
                            ?: userSnap?.child("totalOrders")?.getValue(Int::class.java)?.toLong()
                            ?: userSnap?.child("totalOrders")?.getValue(Long::class.java)
                            ?: 0L
                    )
                    val currentBoom = maxOf(
                        custNodeSnap?.child("boomOrders")?.getValue(Int::class.java)?.toLong()
                            ?: custNodeSnap?.child("boomOrders")?.getValue(Long::class.java)
                            ?: userSnap?.child("boomOrders")?.getValue(Int::class.java)?.toLong()
                            ?: userSnap?.child("boomOrders")?.getValue(Long::class.java)
                            ?: 0L
                    )
                    val currentScore = custNodeSnap?.child("trustScore")?.getValue(Int::class.java)?.toLong()
                        ?: custNodeSnap?.child("trustScore")?.getValue(Long::class.java)
                        ?: userSnap?.child("trustScore")?.getValue(Int::class.java)?.toLong()
                        ?: userSnap?.child("trustScore")?.getValue(Long::class.java)
                        ?: 100L

                    val shortCode = if (orderId.length > 5) orderId.takeLast(6).uppercase() else orderId
                    val statsUpdates = mutableMapOf<String, Any>()
                    var scoreChange = 0
                    var logReason = ""
                    var newScore = currentScore
                    when (normalizedStatus) {
                        Order.Status.COMPLETED -> {
                            newScore = minOf(100L, currentScore + 2L)
                            scoreChange = (newScore - currentScore).toInt()
                            logReason = "Hoàn thành nhận hàng & thanh toán đơn #$shortCode (+2 điểm)"
                            statsUpdates["completedOrders"] = currentCompleted + 1
                            statsUpdates["totalOrders"] = currentTotal + 1
                            statsUpdates["trustScore"] = newScore
                        }
                        Order.Status.BOOM, Order.Status.RETURNING_TO_STORE -> {
                            if (prevStatus != Order.Status.BOOM && prevStatus != Order.Status.RETURNING_TO_STORE && prevStatus != Order.Status.RETURNED_TO_STORE) {
                                newScore = maxOf(0L, currentScore - 30L)
                                scoreChange = (newScore - currentScore).toInt()
                                logReason = if (reason.isNotBlank()) "Bom hàng đơn #$shortCode: $reason (-30 điểm)" else "Không nhận hàng / Bom đơn #$shortCode (-30 điểm)"
                                statsUpdates["boomOrders"] = currentBoom + 1
                                statsUpdates["totalOrders"] = currentTotal + 1
                                statsUpdates["trustScore"] = newScore
                                if (newScore < 20L) {
                                    statsUpdates["status"] = "LOCKED"
                                    statsUpdates["isActive"] = false
                                    statsUpdates["lockReason"] = "Điểm uy tín dưới 20 điểm ($newScore/100) do bom hàng"
                                }
                            }
                        }
                        Order.Status.RETURNED_TO_STORE -> {
                            if (prevStatus != Order.Status.BOOM && prevStatus != Order.Status.RETURNING_TO_STORE && prevStatus != Order.Status.RETURNED_TO_STORE) {
                                newScore = maxOf(0L, currentScore - 30L)
                                scoreChange = (newScore - currentScore).toInt()
                                logReason = if (reason.isNotBlank()) "Bom hàng trả về quán #$shortCode: $reason (-30 điểm)" else "Không nhận hàng / Bom đơn #$shortCode (-30 điểm)"
                                statsUpdates["boomOrders"] = currentBoom + 1
                                statsUpdates["totalOrders"] = currentTotal + 1
                                statsUpdates["trustScore"] = newScore
                                if (newScore < 20L) {
                                    statsUpdates["status"] = "LOCKED"
                                    statsUpdates["isActive"] = false
                                    statsUpdates["lockReason"] = "Điểm uy tín dưới 20 điểm ($newScore/100) do bom hàng"
                                }
                            }
                        }
                        Order.Status.CANCELLED -> {
                            val isApprovedByShop = reason.startsWith("Quán đồng ý hủy", ignoreCase = true) ||
                                prevOrderSnap?.child("cancelRequestStatus")?.getValue(String::class.java) == Order.CancelRequestStatus.APPROVED ||
                                prevOrderSnap?.child("cancelRequested")?.getValue(Boolean::class.java) == true
                            if (!isApprovedByShop && (prevStatus == Order.Status.READY || prevStatus == Order.Status.ASSIGNED || prevStatus == Order.Status.DELIVERING || prevStatus == Order.Status.ARRIVED_AT_CUSTOMER)) {
                                newScore = maxOf(0L, currentScore - 10L)
                                scoreChange = (newScore - currentScore).toInt()
                                logReason = if (reason.isNotBlank()) "Hủy đơn muộn #$shortCode khi đã chế biến/giao: $reason (-10 điểm)" else "Hủy đơn muộn #$shortCode sau khi quán đã chế biến (-10 điểm)"
                                statsUpdates["boomOrders"] = currentBoom + 1
                                statsUpdates["totalOrders"] = currentTotal + 1
                                statsUpdates["trustScore"] = newScore
                            }
                        }
                    }
                    if (statsUpdates.isNotEmpty()) {
                        userRef.updateChildren(statsUpdates)
                        custNodeRef.updateChildren(statsUpdates)
                        database.getReference("orders").child(orderId).updateChildren(
                            mapOf(
                                "customerTrustScore" to newScore,
                                "trustScoreChange" to scoreChange
                            )
                        )
                        if (logReason.isNotBlank()) {
                            val logRef = database.getReference("trust_score_logs").push()
                            val logId = logRef.key ?: "log_${System.currentTimeMillis()}"
                            val logEntry = TrustScoreLog(
                                logId = logId,
                                customerId = customerId,
                                orderId = orderId,
                                changeAmount = if (scoreChange != 0) scoreChange else if (normalizedStatus == Order.Status.COMPLETED) 2 else -10,
                                oldScore = currentScore.toInt(),
                                newScore = newScore.toInt(),
                                reason = logReason,
                                createdAt = System.currentTimeMillis()
                            )
                            logRef.setValue(logEntry)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepo", "updateOrderStatus background error: ${e.message}")
            }
        }
    }
    private suspend fun legacyUpdateOrderStatusUnused(orderId: String, status: String) {
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

    /** Lấy thông tin User theo uid (one-shot) */
    suspend fun getUserById(uid: String): User? {
        if (uid.isBlank()) return null
        return try {
            val snap = database.getReference("users").child(uid).get().await()
            snap.getValue(User::class.java)
        } catch (_: Exception) {
            null
        }
    }

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
                        val rawImg = child.child("imageUrl").getValue(String::class.java)
                            ?: child.child("avatarUrl").getValue(String::class.java)
                            ?: child.child("avatar").getValue(String::class.java)
                            ?: child.child("logoUrl").getValue(String::class.java)
                            ?: child.child("image").getValue(String::class.java)
                            ?: store.effectiveImageUrl
                        val finalDishes = if (dishesMap.isNotEmpty()) dishesMap else store.dishes
                        val finalStore = store.copy(
                            id = storeId,
                            imageUrl = rawImg,
                            avatarUrl = rawImg,
                            avatar = rawImg,
                            logoUrl = rawImg,
                            image = rawImg,
                            dishes = finalDishes
                        )
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
            // 1. Kiểm tra users/{uid}/storeId và xác thực store đó có thực sự thuộc về uid này không
            val userSnap = database.getReference("users").child(uid).get().await()
            val userStoreId = userSnap.child("storeId").getValue(String::class.java)
            val userRole = userSnap.child("role").getValue(String::class.java).orEmpty().uppercase()
            if (!userStoreId.isNullOrBlank()) {
                val sSnap = database.getReference("stores").child(userStoreId).get().await()
                if (sSnap.exists()) {
                    val mId = sSnap.child("merchantId").getValue(String::class.java)
                        ?: sSnap.child("ownerId").getValue(String::class.java)
                    val bId = sSnap.child("bossId").getValue(String::class.java).orEmpty()
                    if (mId == uid || bId == uid || userRole == User.Role.BOSSMERCHANT) {
                        return userStoreId
                    }
                }
            }
            if (userRole == User.Role.BOSSMERCHANT) {
                // Với Boss, nếu chưa chọn chi nhánh cụ thể thì trả về rỗng, không tự ý gán quán linh tinh
                return ""
            }
            // 2. Kiểm tra stores bằng index merchantId / ownerId
            val byMerchant = try { database.getReference("stores").orderByChild("merchantId").equalTo(uid).get().await() } catch (_: Exception) { null }
            val matchedMerchantKey = byMerchant?.children?.firstOrNull { it.child("merchantId").getValue(String::class.java) == uid }?.key
            if (!matchedMerchantKey.isNullOrBlank()) {
                try { database.getReference("users").child(uid).child("storeId").setValue(matchedMerchantKey) } catch (_: Exception) {}
                return matchedMerchantKey
            }
            val byOwner = try { database.getReference("stores").orderByChild("ownerId").equalTo(uid).get().await() } catch (_: Exception) { null }
            val matchedOwnerKey = byOwner?.children?.firstOrNull { it.child("ownerId").getValue(String::class.java) == uid }?.key
            if (!matchedOwnerKey.isNullOrBlank()) {
                try { database.getReference("users").child(uid).child("storeId").setValue(matchedOwnerKey) } catch (_: Exception) {}
                return matchedOwnerKey
            }
            val storesSnap = database.getReference("stores").get().await()
            for (child in storesSnap.children) {
                val ownerId = child.child("ownerId").getValue(String::class.java)
                    ?: child.child("merchantId").getValue(String::class.java)
                if (ownerId == uid) {
                    val foundKey = child.key ?: ""
                    if (foundKey.isNotBlank()) {
                        try { database.getReference("users").child(uid).child("storeId").setValue(foundKey) } catch (_: Exception) {}
                    }
                    return foundKey
                }
            }
            // Không tìm thấy quán thuộc quyền sở hữu của user này (không fallback sang quán người khác)
            ""
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
                if (store != null) {
                    val rawImg = snapshot.child("imageUrl").getValue(String::class.java)
                        ?: snapshot.child("avatarUrl").getValue(String::class.java)
                        ?: snapshot.child("avatar").getValue(String::class.java)
                        ?: snapshot.child("logoUrl").getValue(String::class.java)
                        ?: snapshot.child("image").getValue(String::class.java)
                        ?: store.effectiveImageUrl
                    trySend(store.copy(
                        imageUrl = rawImg,
                        avatarUrl = rawImg,
                        avatar = rawImg,
                        logoUrl = rawImg,
                        image = rawImg
                    ))
                } else {
                    trySend(null)
                }
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
        momoPhone: String = "",
        momoAccountName: String = "",
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
            if (momoPhone.isNotBlank()) updates["momoPhone"] = momoPhone
            if (momoAccountName.isNotBlank()) updates["momoAccountName"] = momoAccountName
            if (imageUrl.isNotBlank()) {
                updates["imageUrl"] = imageUrl
                updates["avatarUrl"] = imageUrl
                updates["avatar"] = imageUrl
                updates["logoUrl"] = imageUrl
                updates["image"] = imageUrl
            }
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
                val rawImg = snap.child("imageUrl").getValue(String::class.java)
                    ?: snap.child("avatarUrl").getValue(String::class.java)
                    ?: snap.child("avatar").getValue(String::class.java)
                    ?: snap.child("logoUrl").getValue(String::class.java)
                    ?: snap.child("image").getValue(String::class.java)
                    ?: store?.effectiveImageUrl.orEmpty()
                store?.copy(
                    imageUrl = rawImg,
                    avatarUrl = rawImg,
                    avatar = rawImg,
                    logoUrl = rawImg,
                    image = rawImg,
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
        cccdFrontUrl: String = "",
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
                "isVerified" to false,
                "verified" to false,
                "storeStatus" to com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.PENDING,
                "status" to com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.PENDING,
                "open" to false
            )
            if (merchantUid.isNotBlank()) {
                val userSnap = try { database.getReference("users").child(merchantUid).get().await() } catch (_: Exception) { null }
                val userRole = userSnap?.child("role")?.getValue(String::class.java).orEmpty().uppercase()
                val isBoss = userRole == User.Role.BOSSMERCHANT || userRole == "BOSS"
                if (isBoss) {
                    updates["bossId"] = merchantUid
                    val storeSnap = try { database.getReference("stores").child(storeId).get().await() } catch (_: Exception) { null }
                    val existingMerchantId = storeSnap?.child("merchantId")?.getValue(String::class.java).orEmpty()
                    if (existingMerchantId == merchantUid) {
                        updates["merchantId"] = ""
                        updates["ownerId"] = ""
                        updates["merchantName"] = ""
                    }
                } else {
                    updates["merchantId"] = merchantUid
                    updates["ownerId"] = merchantUid
                }
                try {
                    val userUpdates = mapOf<String, Any>(
                        "storeId" to storeId,
                        "approvalStatus" to com.chuyen_de_2.foodorder.core.data.model.User.ApprovalStatus.PENDING,
                        "rejectReason" to ""
                    )
                    database.getReference("users").child(merchantUid).updateChildren(userUpdates).await()

                    val merchantData = mapOf<String, Any>(
                        "merchantId" to merchantUid,
                        "storeId" to storeId,
                        "cccdNumber" to cccdNumber.trim(),
                        "cccdFrontUrl" to cccdFrontUrl,
                        "bankName" to bankName.trim(),
                        "bankAccount" to bankAccountNumber.trim(),
                        "approvalStatus" to com.chuyen_de_2.foodorder.core.data.model.Merchant.ApprovalStatus.PENDING,
                        "createdAt" to System.currentTimeMillis()
                    )
                    database.getReference("merchants").child(merchantUid).updateChildren(merchantData).await()
                } catch (_: Exception) {}
            }
            if (bankBin.isNotBlank()) updates["bankBin"] = bankBin.trim()
            if (cccdFrontUrl.isNotBlank()) updates["cccdFrontUrl"] = cccdFrontUrl
            if (latitude != 0.0) updates["latitude"] = latitude
            if (longitude != 0.0) updates["longitude"] = longitude

            database.getReference("stores").child(storeId).updateChildren(updates).await()

            // Nếu là Boss, đồng bộ thông tin định danh sang các chi nhánh của Boss
            if (updates.containsKey("bossId")) {
                val bossUid = updates["bossId"] as String
                try {
                    val allStoresSnap = database.getReference("stores").get().await()
                    val legalUpdates = mutableMapOf<String, Any>(
                        "ownerName" to ownerName.trim(),
                        "cccdNumber" to cccdNumber.trim(),
                        "bankName" to bankName.trim(),
                        "bankAccountNumber" to bankAccountNumber.trim(),
                        "bankAccountName" to bankAccountName.trim().uppercase()
                    )
                    if (bankBin.isNotBlank()) legalUpdates["bankBin"] = bankBin.trim()
                    if (cccdFrontUrl.isNotBlank()) legalUpdates["cccdFrontUrl"] = cccdFrontUrl
                    for (child in allStoresSnap.children) {
                        val bId = child.child("bossId").getValue(String::class.java).orEmpty()
                        val cKey = child.key.orEmpty()
                        if (bId == bossUid && cKey.isNotBlank() && cKey != storeId) {
                            database.getReference("stores").child(cKey).updateChildren(legalUpdates).await()
                        }
                    }
                } catch (_: Exception) {}
            }

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

    /** Lưu / cập nhật danh mục nền tảng lên Firebase kèm cơ chế tự động chen vị trí & đẩy các danh mục phía sau lên +1 */
    suspend fun saveCategory(category: com.chuyen_de_2.foodorder.core.data.model.Category, desc: String = ""): Result<Unit> {
        return try {
            val targetId = category.categoryId.ifBlank { "cat-${System.currentTimeMillis()}" }
            val targetOrder = category.displayOrder.coerceAtLeast(1)
            val finalDesc = if (desc.isNotBlank()) desc else category.description

            // 1. Đọc danh sách tất cả các danh mục hiện có trên Firebase
            val snapshot = database.getReference("categories").get().await()
            val existingList = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.Category>()
            for (child in snapshot.children) {
                val id = child.key ?: continue
                if (id == targetId) continue // Bỏ qua danh mục đang thao tác (kể cả khi chỉnh sửa)

                val name = child.child("categoryName").getValue(String::class.java)
                    ?: child.child("name").getValue(String::class.java) ?: ""
                val icon = child.child("iconUrl").getValue(String::class.java)
                    ?: child.child("icon").getValue(String::class.java) ?: ""
                val d = child.child("description").getValue(String::class.java)
                    ?: child.child("desc").getValue(String::class.java) ?: ""
                val order = child.child("displayOrder").getValue(Int::class.java) ?: 99
                val active = child.child("isActive").getValue(Boolean::class.java)
                    ?: (child.child("status").getValue(String::class.java) != "HIDDEN")

                existingList.add(
                    com.chuyen_de_2.foodorder.core.data.model.Category(
                        categoryId = id,
                        categoryName = name,
                        iconUrl = icon,
                        displayOrder = order,
                        isActive = active,
                        description = d
                    )
                )
            }

            // Sắp xếp các danh mục hiện tại theo thứ tự hiển thị tăng dần
            existingList.sortWith(compareBy({ it.displayOrder }, { it.categoryName }))

            // 2. Tính toán cập nhật số thứ tự: Nếu có mục có thứ tự >= targetOrder, tự động đẩy lên
            val updates = mutableMapOf<String, Any>()
            var lastAssignedOrder = targetOrder

            for (otherCat in existingList) {
                if (otherCat.displayOrder < targetOrder) {
                    continue
                }

                // Nếu số thứ tự của danh mục khác nhỏ hơn hoặc bằng lastAssignedOrder -> phải đẩy lên tiếp theo
                val newOrder = if (otherCat.displayOrder <= lastAssignedOrder) {
                    lastAssignedOrder + 1
                } else {
                    otherCat.displayOrder
                }

                lastAssignedOrder = newOrder

                if (newOrder != otherCat.displayOrder) {
                    updates["categories/${otherCat.categoryId}/displayOrder"] = newOrder
                    updates["categories/${otherCat.categoryId}/updatedAt"] = System.currentTimeMillis()
                    updates["menus/categories/${otherCat.categoryId}/displayOrder"] = newOrder
                    updates["menus/categories/${otherCat.categoryId}/updatedAt"] = System.currentTimeMillis()
                }
            }

            // 3. Đóng gói danh mục mục tiêu cần lưu
            val targetMap = mapOf<String, Any>(
                "id" to targetId,
                "categoryId" to targetId,
                "name" to category.categoryName,
                "categoryName" to category.categoryName,
                "icon" to "",
                "iconUrl" to category.iconUrl,
                "desc" to finalDesc,
                "description" to finalDesc,
                "displayOrder" to targetOrder,
                "isActive" to category.isActive,
                "status" to (if (category.isActive) "ACTIVE" else "HIDDEN"),
                "updatedAt" to System.currentTimeMillis()
            )

            updates["categories/$targetId"] = targetMap
            updates["menus/categories/$targetId"] = targetMap

            // 4. Lưu đồng thời nguyên khối (atomic) lên Firebase Realtime Database
            database.reference.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Xóa danh mục nền tảng khỏi Firebase */
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

    // ==================== DANH MỤC RIÊNG CỦA QUÁN (STORE CATEGORIES) ====================

    /** Danh mục mặc định ban đầu nếu quán chưa tự thiết lập */
    val defaultStoreCategories: List<String> = listOf("Món chính")

    /** Lấy danh sách danh mục nội bộ của quán theo thời gian thực */
    fun observeStoreCategories(storeId: String): Flow<List<String>> = callbackFlow {
        val ref = database.getReference("stores").child(storeId).child("storeCategories")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<String>()
                for (child in snapshot.children) {
                    val cat = child.getValue(String::class.java)?.trim().orEmpty()
                    if (cat.isNotBlank() && !list.contains(cat)) {
                        list.add(cat)
                    }
                }
                if (list.isEmpty()) {
                    // Nếu chưa có bảng storeCategories, tự động sinh từ các món ăn đang có của quán
                    database.getReference("stores").child(storeId).child("dishes").get().addOnSuccessListener { dSnap ->
                        val fromDishes = mutableListOf<String>()
                        for (dChild in dSnap.children) {
                            val c = dChild.child("category").getValue(String::class.java)?.trim().orEmpty()
                            if (c.isNotBlank() && !fromDishes.contains(c)) {
                                fromDishes.add(c)
                            }
                        }
                        if (fromDishes.isNotEmpty()) {
                            trySend(fromDishes)
                        } else {
                            trySend(defaultStoreCategories)
                        }
                    }.addOnFailureListener {
                        trySend(defaultStoreCategories)
                    }
                } else {
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

    /** Lưu toàn bộ danh sách danh mục của quán */
    suspend fun saveStoreCategories(storeId: String, categories: List<String>) {
        val cleanList = categories.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        database.getReference("stores").child(storeId).child("storeCategories").setValue(cleanList).await()
    }

    /** Thêm danh mục mới cho quán */
    suspend fun addStoreCategory(storeId: String, newCategory: String): Result<Unit> {
        val trimmed = newCategory.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Tên danh mục không được để trống"))
        return try {
            val ref = database.getReference("stores").child(storeId).child("storeCategories")
            val snapshot = ref.get().await()
            val list = mutableListOf<String>()
            for (child in snapshot.children) {
                val cat = child.getValue(String::class.java)?.trim().orEmpty()
                if (cat.isNotBlank()) list.add(cat)
            }
            if (list.isEmpty()) {
                list.addAll(defaultStoreCategories)
            }
            if (list.any { it.equals(trimmed, ignoreCase = true) }) {
                return Result.failure(IllegalStateException("Danh mục '$trimmed' đã tồn tại"))
            }
            list.add(trimmed)
            ref.setValue(list).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Đổi tên danh mục và tự động cập nhật tất cả món ăn thuộc danh mục đó */
    suspend fun renameStoreCategory(storeId: String, oldName: String, newName: String): Result<Unit> {
        val oldTrimmed = oldName.trim()
        val newTrimmed = newName.trim()
        if (newTrimmed.isBlank()) return Result.failure(IllegalArgumentException("Tên mới không được để trống"))
        return try {
            val ref = database.getReference("stores").child(storeId).child("storeCategories")
            val snapshot = ref.get().await()
            val list = mutableListOf<String>()
            for (child in snapshot.children) {
                val cat = child.getValue(String::class.java)?.trim().orEmpty()
                if (cat.isNotBlank()) list.add(cat)
            }
            if (list.isEmpty()) {
                list.addAll(defaultStoreCategories)
            }
            val index = list.indexOfFirst { it.equals(oldTrimmed, ignoreCase = true) }
            if (index != -1) {
                list[index] = newTrimmed
            } else {
                list.add(newTrimmed)
            }
            ref.setValue(list.distinct()).await()

            // Đồng bộ cập nhật các món ăn đang mang oldName sang newName
            val dishesRef = database.getReference("stores").child(storeId).child("dishes")
            val dishesSnap = dishesRef.get().await()
            val updates = mutableMapOf<String, Any>()
            for (dishChild in dishesSnap.children) {
                val currentCat = dishChild.child("category").getValue(String::class.java)?.trim().orEmpty()
                if (currentCat.equals(oldTrimmed, ignoreCase = true)) {
                    val dishId = dishChild.key ?: continue
                    updates["$dishId/category"] = newTrimmed
                }
            }
            if (updates.isNotEmpty()) {
                dishesRef.updateChildren(updates).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Xóa danh mục của quán */
    suspend fun deleteStoreCategory(storeId: String, categoryToDelete: String): Result<Unit> {
        val trimmed = categoryToDelete.trim()
        return try {
            val ref = database.getReference("stores").child(storeId).child("storeCategories")
            val snapshot = ref.get().await()
            val list = mutableListOf<String>()
            for (child in snapshot.children) {
                val cat = child.getValue(String::class.java)?.trim().orEmpty()
                if (cat.isNotBlank()) list.add(cat)
            }
            if (list.isEmpty()) {
                list.addAll(defaultStoreCategories)
            }
            list.removeAll { it.equals(trimmed, ignoreCase = true) }
            ref.setValue(list).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Khôi phục danh mục mẫu mặc định cho quán */
    suspend fun restoreDefaultStoreCategories(storeId: String): Result<Unit> {
        return try {
            database.getReference("stores").child(storeId).child("storeCategories").setValue(defaultStoreCategories).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
                storeId = storeId,
                emailVerified = true
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
            database.getReference("drivers").child(driverUid).child("email").setValue(email.trim()).await()

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

    /** Lắng nghe thông tin tài xế theo thời gian thực */
    fun observeDriver(driverId: String): Flow<com.chuyen_de_2.foodorder.core.data.model.Driver?> = callbackFlow {
        if (driverId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val ref = database.getReference("drivers").child(driverId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val driver = snapshot.getValue(com.chuyen_de_2.foodorder.core.data.model.Driver::class.java)?.copy(driverId = driverId)
                    trySend(driver)
                } else {
                    database.getReference("users").child(driverId).get().addOnSuccessListener { uSnap ->
                        if (uSnap.exists()) {
                            val name = uSnap.child("name").getValue(String::class.java).orEmpty()
                            val phone = uSnap.child("phone").getValue(String::class.java).orEmpty()
                            val storeId = uSnap.child("storeId").getValue(String::class.java).orEmpty()
                            trySend(com.chuyen_de_2.foodorder.core.data.model.Driver(
                                driverId = driverId,
                                name = name,
                                phone = phone,
                                storeId = storeId
                            ))
                        } else {
                            trySend(null)
                        }
                    }.addOnFailureListener {
                        trySend(null)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close()
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Cập nhật hồ sơ thông tin tài xế */
    suspend fun updateDriverProfile(
        driverId: String,
        name: String,
        phone: String,
        vehicleModel: String,
        vehiclePlate: String,
        cccdNumber: String = "",
        licenseNumber: String = "",
        avatarUrl: String = ""
    ): Result<Unit> {
        return try {
            val driverUpdates = mutableMapOf<String, Any>(
                "driverId" to driverId,
                "name" to name.trim(),
                "phone" to phone.trim(),
                "vehicleModel" to vehicleModel.trim(),
                "vehiclePlate" to vehiclePlate.trim().uppercase()
            )
            if (cccdNumber.isNotBlank()) driverUpdates["cccdNumber"] = cccdNumber.trim()
            if (licenseNumber.isNotBlank()) driverUpdates["licenseNumber"] = licenseNumber.trim()
            if (avatarUrl.isNotBlank()) driverUpdates["avatarUrl"] = avatarUrl.trim()

            database.getReference("drivers").child(driverId).updateChildren(driverUpdates).await()

            // Đồng bộ sang users/{driverId}
            val userUpdates = mutableMapOf<String, Any>(
                "name" to name.trim(),
                "phone" to phone.trim()
            )
            if (avatarUrl.isNotBlank()) userUpdates["avatarUrl"] = avatarUrl.trim()
            database.getReference("users").child(driverId).updateChildren(userUpdates).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Cập nhật trạng thái ca trực (AVAILABLE, DELIVERING, OFF_DUTY) và mã đơn đang giao của tài xế */
    suspend fun updateDriverDutyStatus(driverId: String, dutyStatus: String, currentOrderCode: String = "") {
        if (driverId.isBlank()) return
        try {
            val updates = mutableMapOf<String, Any>(
                "dutyStatus" to dutyStatus,
                "currentOrderCode" to currentOrderCode
            )
            database.getReference("drivers").child(driverId).updateChildren(updates).await()
        } catch (_: Exception) {}
    }

    /** Thiết lập tự động chuyển trạng thái OFF_DUTY khi tài xế mất mạng hoặc thoát app */
    fun setupDriverPresence(driverId: String) {
        if (driverId.isBlank()) return
        try {
            val ref = database.getReference("drivers").child(driverId)
            ref.child("dutyStatus").onDisconnect().setValue(com.chuyen_de_2.foodorder.core.data.model.Driver.DutyStatus.OFF_DUTY)
            ref.child("currentOrderCode").onDisconnect().setValue("")
        } catch (_: Exception) {}
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

            // 6. Đồng bộ và chuẩn hóa dữ liệu giữa users <-> customers (trustScore, completedOrders, boomOrders, totalOrders)
            try {
                val customersSnap = database.getReference("customers").get().await()
                val customerMap = customersSnap.children.associateBy { it.key.orEmpty() }
                for (userChild in usersSnap.children) {
                    val uid = userChild.key ?: continue
                    val role = userChild.child("role").getValue(String::class.java).orEmpty().uppercase()
                    if (role == "CUSTOMER" || role.isBlank()) {
                        val cSnap = customerMap[uid]
                        val uScore = userChild.child("trustScore").getValue(Int::class.java)
                        val cScore = cSnap?.child("trustScore")?.getValue(Int::class.java)
                        val finalScore = (cScore ?: uScore ?: 100).coerceIn(0, 100)
                        val finalCompleted = maxOf(
                            userChild.child("completedOrders").getValue(Int::class.java) ?: 0,
                            cSnap?.child("completedOrders")?.getValue(Int::class.java) ?: 0
                        )
                        val finalBoom = maxOf(
                            userChild.child("boomOrders").getValue(Int::class.java) ?: 0,
                            cSnap?.child("boomOrders")?.getValue(Int::class.java) ?: 0
                        )
                        val finalTotal = maxOf(
                            userChild.child("totalOrders").getValue(Int::class.java) ?: 0,
                            cSnap?.child("totalOrders")?.getValue(Int::class.java) ?: 0,
                            finalCompleted + finalBoom
                        )
                        if (cSnap == null || uScore != finalScore || cScore != finalScore) {
                            val syncMap = mapOf(
                                "trustScore" to finalScore,
                                "completedOrders" to finalCompleted,
                                "boomOrders" to finalBoom,
                                "totalOrders" to finalTotal
                            )
                            database.getReference("users").child(uid).updateChildren(syncMap).await()
                            database.getReference("customers").child(uid).updateChildren(
                                syncMap + mapOf(
                                    "customerId" to uid,
                                    "uid" to uid,
                                    "name" to (userChild.child("name").getValue(String::class.java).orEmpty()),
                                    "email" to (userChild.child("email").getValue(String::class.java).orEmpty()),
                                    "phone" to (userChild.child("phone").getValue(String::class.java).orEmpty()),
                                    "status" to (userChild.child("status").getValue(String::class.java) ?: "ACTIVE")
                                )
                            ).await()
                            cleanedCount++
                        }
                    }
                }
            } catch (_: Exception) {}

            // 7. Dọn dẹp bản sao dư thừa menus/categories và trường icon trùng lặp trong categories
            try {
                val catsSnap = database.getReference("categories").get().await()
                if (catsSnap.hasChildren()) {
                    for (catChild in catsSnap.children) {
                        val iconVal = catChild.child("icon").getValue(String::class.java).orEmpty()
                        val iconUrlVal = catChild.child("iconUrl").getValue(String::class.java).orEmpty()
                        if (iconVal.isNotBlank() && iconVal == iconUrlVal) {
                            catChild.ref.child("icon").setValue("").await()
                            cleanedCount++
                        }
                    }
                    val legacyMenuCats = database.getReference("menus/categories").get().await()
                    if (legacyMenuCats.exists()) {
                        database.getReference("menus/categories").removeValue().await()
                        cleanedCount++
                    }
                }
            } catch (_: Exception) {}

            // 8. Dọn dẹp đánh giá (dish_reviews) và lịch sử điểm uy tín (trust_score_logs) mồ côi
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

    /** Lấy chi tiết 1 khiếu nại theo ID */
    suspend fun getComplaint(complaintId: String): com.chuyen_de_2.foodorder.core.data.model.Complaint? {
        return try {
            val snap = database.getReference("complaints").child(complaintId).get().await()
            val c = snap.getValue(com.chuyen_de_2.foodorder.core.data.model.Complaint::class.java)
            if (c != null && c.complaintId.isBlank()) {
                c.copy(complaintId = snap.key ?: complaintId)
            } else {
                c
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Lắng nghe 1 khiếu nại cụ thể theo thời gian thực */
    fun observeComplaint(complaintId: String): Flow<com.chuyen_de_2.foodorder.core.data.model.Complaint?> = callbackFlow {
        val ref = database.getReference("complaints").child(complaintId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val c = snapshot.getValue(com.chuyen_de_2.foodorder.core.data.model.Complaint::class.java)
                val finalComplaint = if (c != null && c.complaintId.isBlank()) {
                    c.copy(complaintId = snapshot.key ?: complaintId)
                } else {
                    c
                }
                trySend(finalComplaint)
            }

            override fun onCancelled(error: DatabaseError) {
                close()
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Phán quyết xử lý khiếu nại và áp dụng 3 mức chế tài vi phạm (hỗ trợ phạt ngược người gửi khi báo cáo sai sự thật) */
    suspend fun resolveComplaint(
        complaintId: String,
        status: String,
        penaltyApplied: String = "NONE",
        adminNote: String = "",
        storeId: String = "",
        targetType: String = com.chuyen_de_2.foodorder.core.data.model.Complaint.TargetType.STORE,
        targetId: String = "",
        customerId: String = "",
        driverId: String = "",
        orderId: String = "",
        isCounterPenalty: Boolean = false,
        counterPenaltyTier: String = "NONE"
    ): Result<Unit> {
        return try {
            val resolvedAt = System.currentTimeMillis()
            val effectiveCounterPenalty = if (isCounterPenalty) counterPenaltyTier else "NONE"
            val trustDeducted = when {
                !isCounterPenalty -> 0
                effectiveCounterPenalty == com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.WARN -> 10
                effectiveCounterPenalty == com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.HIDE -> 30
                effectiveCounterPenalty == com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.LOCK -> 50
                else -> 0
            }
            val updates = mutableMapOf<String, Any?>(
                "status" to status,
                "penaltyApplied" to if (isCounterPenalty) "NONE" else penaltyApplied,
                "adminNote" to adminNote,
                "isComplainantPenalized" to isCounterPenalty,
                "counterPenaltyApplied" to effectiveCounterPenalty,
                "counterPenaltyReason" to if (isCounterPenalty) adminNote else "",
                "trustScoreDeducted" to trustDeducted,
                "resolvedAt" to resolvedAt
            )
            database.getReference("complaints").child(complaintId).updateChildren(updates).await()

            // 1. Phạt ngược người gửi khiếu nại (Khách hàng báo cáo sai / vu khống / lạm dụng)
            if (isCounterPenalty && customerId.isNotBlank()) {
                when (effectiveCounterPenalty) {
                    com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.WARN -> {
                        deductCustomerTrustScore(customerId, 10, "Phạt báo cáo sai sự thật đơn #$orderId: $adminNote", orderId)
                    }
                    com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.HIDE -> {
                        deductCustomerTrustScore(customerId, 30, "Phạt nặng vu khống / lạm dụng khiếu nại đơn #$orderId: $adminNote", orderId)
                    }
                    com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.LOCK -> {
                        deductCustomerTrustScore(customerId, 50, "Khóa tài khoản do lạm dụng khiếu nại đơn #$orderId: $adminNote", orderId)
                        updateUserStatus(customerId, "LOCKED", false, "Tài khoản bị khóa vĩnh viễn do vu khống / lạm dụng hệ thống khiếu nại: $adminNote")
                    }
                }
            } else if (status.equals(com.chuyen_de_2.foodorder.core.data.model.Complaint.ComplaintStatus.RESOLVED, ignoreCase = true) && penaltyApplied != "NONE") {
                val effectiveStoreId = storeId.ifBlank { if (targetType == com.chuyen_de_2.foodorder.core.data.model.Complaint.TargetType.STORE) targetId else "" }
                val effectiveCustomerId = customerId.ifBlank { if (targetType == com.chuyen_de_2.foodorder.core.data.model.Complaint.TargetType.CUSTOMER) targetId else "" }
                val effectiveDriverId = driverId.ifBlank { if (targetType == com.chuyen_de_2.foodorder.core.data.model.Complaint.TargetType.DRIVER) targetId else "" }

                // 1. Áp dụng mức phạt với Quán ăn
                if (effectiveStoreId.isNotBlank()) {
                    updateStorePenalty(effectiveStoreId, penaltyApplied, adminNote)
                }

                // 2. Áp dụng mức phạt với Khách hàng (trừ điểm uy tín hoặc khóa tài khoản)
                if (effectiveCustomerId.isNotBlank()) {
                    when (penaltyApplied) {
                        com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.WARN -> {
                            deductCustomerTrustScore(effectiveCustomerId, 10, "Cảnh cáo vi phạm: $adminNote", orderId)
                        }
                        com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.HIDE -> {
                            setCustomerTrustScore(effectiveCustomerId, 30, "Tạm đình chỉ COD do vi phạm: $adminNote", orderId)
                        }
                        com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.LOCK -> {
                            updateUserStatus(effectiveCustomerId, "LOCKED", false, "Khóa tài khoản do vi phạm: $adminNote")
                        }
                    }
                }

                // 3. Áp dụng mức phạt với Tài xế
                if (effectiveDriverId.isNotBlank()) {
                    when (penaltyApplied) {
                        com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.WARN -> {
                            database.getReference("drivers").child(effectiveDriverId).child("warningNote").setValue("Cảnh cáo: $adminNote")
                        }
                        com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.HIDE,
                        com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.LOCK -> {
                            updateUserStatus(effectiveDriverId, "LOCKED", false, "Đình chỉ tài xế do vi phạm: $adminNote")
                            database.getReference("drivers").child(effectiveDriverId).updateChildren(
                                mapOf("isActive" to false, "dutyStatus" to "LOCKED", "lockReason" to adminNote)
                            ).await()
                        }
                    }
                }
            }

            if (orderId.isNotBlank()) {
                try {
                    val orderUpdates = mapOf<String, Any>(
                        "complaintStatus" to status,
                        "complaintPenalty" to if (isCounterPenalty) effectiveCounterPenalty else penaltyApplied,
                        "complaintNote" to adminNote
                    )
                    database.getReference("orders").child(orderId).updateChildren(orderUpdates).await()
                } catch (_: Exception) {}
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lắng nghe danh sách khiếu nại do 1 khách hàng cụ thể gửi */
    fun observeCustomerComplaints(customerId: String): Flow<List<com.chuyen_de_2.foodorder.core.data.model.Complaint>> = callbackFlow {
        val ref = database.getReference("complaints")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.Complaint>()
                for (child in snapshot.children) {
                    val c = child.getValue(com.chuyen_de_2.foodorder.core.data.model.Complaint::class.java)
                    if (c != null && c.customerId == customerId) {
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

    private suspend fun deductCustomerTrustScore(customerId: String, amount: Int, reason: String, orderId: String = "") {
        try {
            val custRef = database.getReference("customers").child(customerId)
            val snap = custRef.get().await()
            val currentScore = snap.child("trustScore").getValue(Int::class.java)
                ?: snap.child("trustScore").getValue(Long::class.java)?.toInt()
                ?: 100
            val newScore = (currentScore - amount).coerceIn(0, 100)
            custRef.child("trustScore").setValue(newScore).await()
            database.getReference("users").child(customerId).child("trustScore").setValue(newScore).await()

            val logRef = database.getReference("trust_score_logs").push()
            val logId = logRef.key ?: "log_${System.currentTimeMillis()}"
            val log = com.chuyen_de_2.foodorder.core.data.model.TrustScoreLog(
                logId = logId,
                customerId = customerId,
                orderId = orderId.ifBlank { null },
                changeAmount = -amount,
                oldScore = currentScore,
                newScore = newScore,
                reason = reason,
                createdAt = System.currentTimeMillis()
            )
            logRef.setValue(log).await()
        } catch (_: Exception) {}
    }

    private suspend fun setCustomerTrustScore(customerId: String, targetScore: Int, reason: String, orderId: String = "") {
        try {
            val custRef = database.getReference("customers").child(customerId)
            val snap = custRef.get().await()
            val currentScore = snap.child("trustScore").getValue(Int::class.java)
                ?: snap.child("trustScore").getValue(Long::class.java)?.toInt()
                ?: 100
            val newScore = targetScore.coerceIn(0, 100)
            custRef.child("trustScore").setValue(newScore).await()
            database.getReference("users").child(customerId).child("trustScore").setValue(newScore).await()

            val logRef = database.getReference("trust_score_logs").push()
            val logId = logRef.key ?: "log_${System.currentTimeMillis()}"
            val log = com.chuyen_de_2.foodorder.core.data.model.TrustScoreLog(
                logId = logId,
                customerId = customerId,
                orderId = orderId.ifBlank { null },
                changeAmount = newScore - currentScore,
                oldScore = currentScore,
                newScore = newScore,
                reason = reason,
                createdAt = System.currentTimeMillis()
            )
            logRef.setValue(log).await()
        } catch (_: Exception) {}
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
            val storeStatus = when (penaltyTier) {
                com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.LOCK -> com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.LOCKED
                com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.HIDE -> com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.CLOSED
                else -> com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.OPEN
            }
            val isOpen = (storeStatus == com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.OPEN)
            val updates = mapOf<String, Any>(
                "penaltyTier" to penaltyTier,
                "penaltyTierId" to tierId,
                "penaltyReason" to reason,
                "storeStatus" to storeStatus,
                "status" to storeStatus,
                "open" to isOpen
            )
            database.getReference("stores").child(storeId).updateChildren(updates).await()

            if (penaltyTier == com.chuyen_de_2.foodorder.core.data.model.Store.PenaltyTier.LOCK) {
                try {
                    val storeSnap = database.getReference("stores").child(storeId).get().await()
                    val ownerId = storeSnap.child("ownerId").getValue(String::class.java).orEmpty()
                    if (ownerId.isNotBlank()) {
                        updateUserStatus(ownerId, "LOCKED", false, "Cửa hàng đã bị khóa vĩnh viễn do vi phạm nghiêm trọng: $reason")
                    }
                } catch (_: Exception) {}
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Duyệt hồ sơ Quán ăn / Chủ chuỗi (Admin CCCD review) */
    suspend fun verifyStore(storeId: String, isApproved: Boolean): Result<Unit> {
        return try {
            val status = if (isApproved) com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.OPEN 
                         else com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.LOCKED
            val updates = mapOf<String, Any>(
                "isVerified" to isApproved,
                "verified" to isApproved,
                "storeStatus" to status,
                "status" to status,
                "open" to isApproved,
                "penaltyReason" to if (isApproved) "" else "Admin khóa hoặc từ chối"
            )
            database.getReference("stores").child(storeId).updateChildren(updates).await()

            // Cập nhật tài khoản Boss / Merchant sở hữu quán
            try {
                val storeSnap = database.getReference("stores").child(storeId).get().await()
                val bossId = storeSnap.child("bossId").getValue(String::class.java).orEmpty()
                val merchantId = storeSnap.child("merchantId").getValue(String::class.java).orEmpty()
                    .ifBlank { storeSnap.child("ownerId").getValue(String::class.java).orEmpty() }

                val targetUids = listOf(bossId, merchantId).filter { it.isNotBlank() }.distinct()
                for (tUid in targetUids) {
                    val userUpdates = mapOf<String, Any>(
                        "approvalStatus" to if (isApproved) com.chuyen_de_2.foodorder.core.data.model.User.ApprovalStatus.APPROVED else com.chuyen_de_2.foodorder.core.data.model.User.ApprovalStatus.PENDING,
                        "status" to if (isApproved) com.chuyen_de_2.foodorder.core.data.model.User.Status.ACTIVE else com.chuyen_de_2.foodorder.core.data.model.User.Status.LOCKED,
                        "isActive" to isApproved
                    )
                    database.getReference("users").child(tUid).updateChildren(userUpdates).await()

                    val merchantUpdates = mapOf<String, Any>(
                        "approvalStatus" to if (isApproved) com.chuyen_de_2.foodorder.core.data.model.Merchant.ApprovalStatus.APPROVED else com.chuyen_de_2.foodorder.core.data.model.Merchant.ApprovalStatus.PENDING
                    )
                    database.getReference("merchants").child(tUid).updateChildren(merchantUpdates).await()
                }
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Từ chối hồ sơ Quán ăn / Chủ chuỗi có kèm lý do */
    suspend fun rejectStore(storeId: String, reason: String): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "isVerified" to false,
                "verified" to false,
                "storeStatus" to com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.REJECTED,
                "status" to com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.REJECTED,
                "open" to false,
                "penaltyReason" to reason.trim()
            )
            database.getReference("stores").child(storeId).updateChildren(updates).await()

            try {
                val storeSnap = database.getReference("stores").child(storeId).get().await()
                val bossId = storeSnap.child("bossId").getValue(String::class.java).orEmpty()
                val merchantId = storeSnap.child("merchantId").getValue(String::class.java).orEmpty()
                    .ifBlank { storeSnap.child("ownerId").getValue(String::class.java).orEmpty() }

                val targetUids = listOf(bossId, merchantId).filter { it.isNotBlank() }.distinct()
                for (tUid in targetUids) {
                    val userUpdates = mapOf<String, Any>(
                        "approvalStatus" to com.chuyen_de_2.foodorder.core.data.model.User.ApprovalStatus.REJECTED,
                        "rejectReason" to reason.trim()
                    )
                    database.getReference("users").child(tUid).updateChildren(userUpdates).await()

                    val merchantUpdates = mapOf<String, Any>(
                        "approvalStatus" to com.chuyen_de_2.foodorder.core.data.model.Merchant.ApprovalStatus.REJECTED
                    )
                    database.getReference("merchants").child(tUid).updateChildren(merchantUpdates).await()
                }
            } catch (_: Exception) {}

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
                        val rawStatus = child.child("status").getValue(String::class.java)?.trim()?.uppercase() ?: u.status.uppercase()
                        val rawIsActive = child.child("isActive").getValue(Boolean::class.java)
                            ?: child.child("active").getValue(Boolean::class.java)
                            ?: true
                        val hasLockReason = !child.child("lockReason").getValue(String::class.java).isNullOrBlank() ||
                            !child.child("suspendedReason").getValue(String::class.java).isNullOrBlank()
                        val rawAvatar = child.child("avatarUrl").getValue(String::class.java)?.trim().orEmpty()
                            .ifBlank { child.child("photoUrl").getValue(String::class.java)?.trim().orEmpty() }
                            .ifBlank { child.child("avatar").getValue(String::class.java)?.trim().orEmpty() }
                            .ifBlank { child.child("profileImage").getValue(String::class.java)?.trim().orEmpty() }
                        val finalAvatar = u.avatarUrl.ifBlank { rawAvatar }
                        val isLocked = !rawIsActive || rawStatus == "LOCKED" || rawStatus == "SUSPENDED" || rawStatus == "INACTIVE" || hasLockReason
                        val finalStatus = if (isLocked) "LOCKED" else rawStatus.ifBlank { User.Status.ACTIVE }
                        list.add(u.copy(uid = uid, avatarUrl = finalAvatar, isActive = !isLocked, status = finalStatus))
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

    /** Khóa / Mở khóa tài khoản người dùng và đồng bộ toàn diện giữa users, customers và drivers */
    suspend fun updateUserStatus(
        uid: String,
        status: String,
        isActive: Boolean,
        lockReason: String = ""
    ): Result<Unit> {
        return try {
            val isUnlocked = isActive && !status.equals("LOCKED", ignoreCase = true) && !status.equals("SUSPENDED", ignoreCase = true) && !status.equals(User.Status.INACTIVE, ignoreCase = true)
            val reason = if (lockReason.isNotBlank()) lockReason else "Tài khoản của bạn đã bị Quản trị viên tạm khóa. Vui lòng liên hệ Admin qua email admin@gmail.com để được giải quyết."

            val userUpdates = mutableMapOf<String, Any?>(
                "status" to if (isUnlocked) User.Status.ACTIVE else "LOCKED",
                "isActive" to isUnlocked,
                "active" to isUnlocked,
                "lockReason" to if (isUnlocked) null else reason,
                "suspendedReason" to null
            )

            // 1. Cập nhật ngay lập tức users/{uid} để WebSocket phát tín hiệu tức thì
            database.getReference("users").child(uid).updateChildren(userUpdates).await()

            // 2. Đồng bộ sang customers/{uid} nếu tồn tại
            try {
                val custSnap = database.getReference("customers").child(uid).get().await()
                if (custSnap.exists()) {
                    val custUpdates = mutableMapOf<String, Any?>(
                        "status" to if (isUnlocked) User.Status.ACTIVE else "LOCKED",
                        "isActive" to isUnlocked,
                        "active" to isUnlocked,
                        "lockReason" to if (isUnlocked) null else reason,
                        "suspendedReason" to null
                    )
                    if (isUnlocked) {
                        val currentScore = custSnap.child("trustScore").getValue(Int::class.java)
                            ?: custSnap.child("trustScore").getValue(Long::class.java)?.toInt()
                            ?: 100
                        if (currentScore < 50) {
                            custUpdates["trustScore"] = 60
                            database.getReference("users").child(uid).child("trustScore").setValue(60)
                            val logRef = database.getReference("trust_score_logs").push()
                            val logId = logRef.key ?: "log_${System.currentTimeMillis()}"
                            val logEntry = TrustScoreLog(
                                logId = logId,
                                customerId = uid,
                                orderId = null,
                                changeAmount = 60 - currentScore,
                                oldScore = currentScore,
                                newScore = 60,
                                reason = "Quản trị viên mở khóa tài khoản và khôi phục điểm uy tín",
                                createdAt = System.currentTimeMillis()
                            )
                            logRef.setValue(logEntry)
                        }
                    }
                    database.getReference("customers").child(uid).updateChildren(custUpdates).await()
                }
            } catch (_: Exception) {}

            // 3. Đồng bộ nếu là driver
            try {
                val driverSnap = database.getReference("drivers").child(uid).get().await()
                if (driverSnap.exists()) {
                    val driverUpdates = mapOf<String, Any?>(
                        "isActive" to isUnlocked,
                        "dutyStatus" to if (isUnlocked) "OFFLINE" else "LOCKED"
                    )
                    database.getReference("drivers").child(uid).updateChildren(driverUpdates).await()
                }
            } catch (_: Exception) {}

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
                "imageUrl" to finalStore.effectiveImageUrl,
                "avatarUrl" to finalStore.effectiveImageUrl,
                "avatar" to finalStore.effectiveImageUrl,
                "logoUrl" to finalStore.effectiveImageUrl,
                "image" to finalStore.effectiveImageUrl,
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
                        val rawAvatar = child.child("avatarUrl").getValue(String::class.java)?.trim().orEmpty()
                            .ifBlank { child.child("photoUrl").getValue(String::class.java)?.trim().orEmpty() }
                            .ifBlank { child.child("avatar").getValue(String::class.java)?.trim().orEmpty() }
                            .ifBlank { child.child("profileImage").getValue(String::class.java)?.trim().orEmpty() }
                        val finalAvatar = c.avatarUrl.ifBlank { rawAvatar }
                        list.add(c.copy(customerId = cId, avatarUrl = finalAvatar))
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
    /** Lắng nghe hồ sơ Customer & Điểm uy tín theo thời gian thực cho 1 khách hàng */
    fun observeCustomerProfile(customerId: String): Flow<com.chuyen_de_2.foodorder.core.data.model.Customer> = callbackFlow {
        if (customerId.isBlank()) {
            trySend(com.chuyen_de_2.foodorder.core.data.model.Customer())
            close()
            return@callbackFlow
        }
        val custRef = database.getReference("customers").child(customerId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val score = snapshot.child("trustScore").getValue(Int::class.java) ?: 100
                val total = snapshot.child("totalOrders").getValue(Int::class.java) ?: 0
                val completed = snapshot.child("completedOrders").getValue(Int::class.java) ?: 0
                val boom = snapshot.child("boomOrders").getValue(Int::class.java) ?: 0
                val avatar = snapshot.child("avatarUrl").getValue(String::class.java).orEmpty()
                trySend(
                    com.chuyen_de_2.foodorder.core.data.model.Customer(
                        customerId = customerId,
                        avatarUrl = avatar,
                        trustScore = score.coerceIn(0, 100),
                        totalOrders = maxOf(total, completed + boom),
                        completedOrders = completed,
                        boomOrders = boom
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                close()
            }
        }
        custRef.addValueEventListener(listener)
        awaitClose { custRef.removeEventListener(listener) }
    }

    /** Lắng nghe lịch sử biến động Điểm Uy Tín (trust_score_logs) của khách hàng */
    fun observeTrustScoreLogs(customerId: String): Flow<List<TrustScoreLog>> = callbackFlow {
        if (customerId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = database.getReference("trust_score_logs")
            .orderByChild("customerId")
            .equalTo(customerId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val logs = snapshot.children.mapNotNull { child ->
                    val log = child.getValue(TrustScoreLog::class.java)
                    if (log != null) {
                        log.copy(logId = if (log.logId.isBlank()) child.key.orEmpty() else log.logId)
                    } else null
                }.sortedByDescending { it.createdAt }
                trySend(logs)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Quản trị viên cập nhật hồ sơ User kèm Điểm Uy Tín & Thống kê đơn hàng */
    suspend fun saveUserWithTrustScore(
        user: com.chuyen_de_2.foodorder.core.data.model.User,
        trustScore: Int,
        completedOrders: Int,
        boomOrders: Int,
        adjustReason: String = ""
    ): Result<Unit> {
        return try {
            val uid = user.uid.ifBlank { return Result.failure(IllegalArgumentException("User UID cannot be empty")) }
            val clampedScore = trustScore.coerceIn(0, 100)
            val totalOrders = maxOf(0, completedOrders + boomOrders)

            val custSnap = database.getReference("customers").child(uid).get().await()
            val oldScore = custSnap.child("trustScore").getValue(Int::class.java)
                ?: custSnap.child("trustScore").getValue(Long::class.java)?.toInt()
                ?: 100

            val userSnap = database.getReference("users").child(uid).get().await()
            val driverSnap = try { database.getReference("drivers").child(uid).get().await() } catch (_: Exception) { null }

            // BẢO TOÀN AVATAR: Tránh mất avatar khi Admin cập nhật thông tin người dùng
            val existingAvatar = userSnap.child("avatarUrl").getValue(String::class.java)?.trim().orEmpty()
                .ifBlank { userSnap.child("photoUrl").getValue(String::class.java)?.trim().orEmpty() }
                .ifBlank { userSnap.child("avatar").getValue(String::class.java)?.trim().orEmpty() }
                .ifBlank { custSnap.child("avatarUrl").getValue(String::class.java)?.trim().orEmpty() }
                .ifBlank { custSnap.child("photoUrl").getValue(String::class.java)?.trim().orEmpty() }
                .ifBlank { custSnap.child("avatar").getValue(String::class.java)?.trim().orEmpty() }
                .ifBlank { driverSnap?.child("avatarUrl")?.getValue(String::class.java)?.trim().orEmpty() }
                .ifBlank { driverSnap?.child("photoUrl")?.getValue(String::class.java)?.trim().orEmpty() }

            val effectiveAvatar = when {
                user.avatarUrl == "RESET_DEFAULT_AVATAR" -> ""
                user.avatarUrl.isNotBlank() -> user.avatarUrl.trim()
                else -> existingAvatar
            }

            val isUnlocked = user.isActive && (user.status.equals(User.Status.ACTIVE, ignoreCase = true) || user.status.equals(User.Status.OFFLINE, ignoreCase = true))
            val finalStatus = if (isUnlocked) user.status else "LOCKED"

            val userUpdates = mutableMapOf<String, Any?>(
                "uid" to uid,
                "name" to user.name,
                "email" to user.email,
                "phone" to user.phone,
                "role" to user.role,
                "roleId" to user.roleId,
                "isActive" to isUnlocked,
                "active" to isUnlocked,
                "status" to finalStatus,
                "avatarUrl" to effectiveAvatar,
                "photoUrl" to effectiveAvatar,
                "avatar" to effectiveAvatar,
                "profileImage" to effectiveAvatar,
                "storeId" to user.storeId,
                "trustScore" to clampedScore,
                "completedOrders" to completedOrders,
                "boomOrders" to boomOrders,
                "totalOrders" to totalOrders
            )
            if (isUnlocked) {
                userUpdates["lockReason"] = null
                userUpdates["suspendedReason"] = null
            } else {
                userUpdates["lockReason"] = if (adjustReason.isNotBlank()) adjustReason else "Tài khoản của bạn đã bị Quản trị viên khóa do vi phạm chính sách hoạt động."
            }
            database.getReference("users").child(uid).updateChildren(userUpdates).await()

            val custUpdates = mutableMapOf<String, Any?>(
                "customerId" to uid,
                "uid" to uid,
                "name" to user.name,
                "email" to user.email,
                "phone" to user.phone,
                "status" to finalStatus,
                "isActive" to isUnlocked,
                "active" to isUnlocked,
                "avatarUrl" to effectiveAvatar,
                "photoUrl" to effectiveAvatar,
                "avatar" to effectiveAvatar,
                "profileImage" to effectiveAvatar,
                "trustScore" to clampedScore,
                "completedOrders" to completedOrders,
                "boomOrders" to boomOrders,
                "totalOrders" to totalOrders
            )
            if (isUnlocked) {
                custUpdates["lockReason"] = null
                custUpdates["suspendedReason"] = null
            } else {
                custUpdates["lockReason"] = if (adjustReason.isNotBlank()) adjustReason else "Tài khoản của bạn đã bị Quản trị viên khóa do vi phạm chính sách hoạt động."
            }
            database.getReference("customers").child(uid).updateChildren(custUpdates).await()

            try {
                if (driverSnap != null && driverSnap.exists()) {
                    val driverUpdates = mutableMapOf<String, Any?>(
                        "isActive" to isUnlocked,
                        "dutyStatus" to if (isUnlocked) "OFFLINE" else "LOCKED",
                        "avatarUrl" to effectiveAvatar,
                        "photoUrl" to effectiveAvatar,
                        "avatar" to effectiveAvatar,
                        "profileImage" to effectiveAvatar,
                        "name" to user.name,
                        "phone" to user.phone
                    )
                    database.getReference("drivers").child(uid).updateChildren(driverUpdates).await()
                }
            } catch (_: Exception) {}

            try {
                if (user.storeId.isNotBlank()) {
                    val storeSnap = database.getReference("stores").child(user.storeId).get().await()
                    if (storeSnap.exists()) {
                        val storeUpdates = mutableMapOf<String, Any?>(
                            "ownerPhone" to user.phone,
                            "ownerName" to user.name
                        )
                        database.getReference("stores").child(user.storeId).updateChildren(storeUpdates).await()
                    }
                }
            } catch (_: Exception) {}

            if (oldScore != clampedScore || adjustReason.isNotBlank()) {
                val diff = clampedScore - oldScore
                val logRef = database.getReference("trust_score_logs").push()
                val logId = logRef.key ?: "log_${System.currentTimeMillis()}"
                val reasonText = if (adjustReason.isNotBlank()) {
                    "Quản trị viên điều chỉnh: $adjustReason"
                } else {
                    "Quản trị viên cập nhật điểm uy tín ($oldScore -> $clampedScore)"
                }
                val logEntry = TrustScoreLog(
                    logId = logId,
                    customerId = uid,
                    orderId = null,
                    changeAmount = diff,
                    oldScore = oldScore,
                    newScore = clampedScore,
                    reason = reasonText,
                    createdAt = System.currentTimeMillis()
                )
                logRef.setValue(logEntry).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    // ==================== BÌNH LUẬN & ĐÁNH GIÁ (DISH REVIEWS & RATINGS) ====================

    /** Lắng nghe danh sách đánh giá của món ăn theo thời gian thực (b5.html: reviews-sheet) */
    fun observeDishReviews(dishId: String, dishName: String = ""): Flow<List<DishReview>> = callbackFlow {
        if (dishId.isBlank() && dishName.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = database.getReference("dish_reviews")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<DishReview>()
                for (child in snapshot.children) {
                    val rev = child.getValue(DishReview::class.java)
                    if (rev != null) {
                        val matchesId = dishId.isNotBlank() && rev.dishId == dishId
                        val matchesName = dishName.isNotBlank() && rev.dishName.trim().equals(dishName.trim(), ignoreCase = true)
                        if (matchesId || matchesName) {
                            val finalRev = if (rev.reviewId.isBlank()) rev.copy(reviewId = child.key ?: "") else rev
                            list.add(finalRev)
                        }
                    }
                }
                list.sortByDescending { it.createdAt }
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
     * Gửi đánh giá món ăn lên Firebase (dish_reviews) và tự động tính toán lại ratingAvg, reviewCount
     * Tự động đồng bộ số sao vào thực đơn quán (stores/{storeId}/dishes/{dishId})
     */
    suspend fun submitDishReview(review: DishReview): Boolean {
        return try {
            val reviewId = review.reviewId.ifBlank {
                database.getReference("dish_reviews").push().key ?: "rev_${System.currentTimeMillis()}"
            }
            val finalReview = review.copy(reviewId = reviewId)
            database.getReference("dish_reviews").child(reviewId).setValue(finalReview).await()

            // Tự động tính toán lại ratingAvg và reviewCount của món ăn
            val targetDishId = review.dishId
            val snap = database.getReference("dish_reviews").get().await()
            var totalStars = 0
            var count = 0
            for (child in snap.children) {
                val r = child.getValue(DishReview::class.java)
                if (r != null) {
                    val matches = (targetDishId.isNotBlank() && r.dishId == targetDishId) ||
                            (review.dishName.isNotBlank() && r.dishName.trim().equals(review.dishName.trim(), ignoreCase = true))
                    if (matches) {
                        totalStars += r.rating
                        count++
                    }
                }
            }
            val newAvg = if (count > 0) {
                String.format(java.util.Locale.US, "%.1f", totalStars.toFloat() / count).toFloat()
            } else 5.0f

            val storeId = review.storeId
            if (storeId.isNotBlank()) {
                val resolvedDishKey = if (targetDishId.isNotBlank()) targetDishId else {
                    val storeDishesSnap = try { database.getReference("stores").child(storeId).child("dishes").get().await() } catch (_: Exception) { null }
                    storeDishesSnap?.children?.find {
                        it.child("name").getValue(String::class.java)?.trim()?.equals(review.dishName.trim(), ignoreCase = true) == true
                    }?.key ?: ""
                }
                if (resolvedDishKey.isNotBlank()) {
                    val dishRef = database.getReference("stores").child(storeId).child("dishes").child(resolvedDishKey)
                    dishRef.child("ratingAvg").setValue(newAvg).await()
                    dishRef.child("reviewCount").setValue(count).await()
                }

                // Tự động tính toán lại ĐIỂM TRUNG BÌNH CẢ QUÁN:
                // Điểm quán = Tổng tất cả số sao của các bình luận từng món cộng lại / Tổng số bình luận quán
                var storeTotalStars = 0
                var storeReviewCount = 0
                for (child in snap.children) {
                    val r = child.getValue(DishReview::class.java)
                    if (r != null && r.storeId == storeId) {
                        storeTotalStars += r.rating
                        storeReviewCount++
                    }
                }
                val storeRatingAvg = if (storeReviewCount > 0) {
                    String.format(java.util.Locale.US, "%.1f", storeTotalStars.toFloat() / storeReviewCount).toFloat()
                } else 5.0f

                val storeRef = database.getReference("stores").child(storeId)
                storeRef.child("ratingAvg").setValue(storeRatingAvg).await()
                storeRef.child("rating").setValue(storeRatingAvg).await()
                storeRef.child("reviewCount").setValue(storeReviewCount).await()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Tăng hoặc giảm lượt thích (Like / Hữu ích) của đánh giá */
    suspend fun toggleReviewLike(reviewId: String, currentLikes: Int, isLiked: Boolean): Boolean {
        return try {
            val newLikes = if (isLiked) currentLikes + 1 else maxOf(0, currentLikes - 1)
            database.getReference("dish_reviews").child(reviewId).child("likes").setValue(newLikes).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    // ==================== CẤU HÌNH THỜI GIAN ĐẾM NGƯỢC BOM HÀNG ====================

    private fun parseSafeInt(snapshot: DataSnapshot, key: String, default: Int): Int {
        val v = snapshot.child(key).value ?: return default
        return when (v) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }
    }

    private fun parseSafeLong(snapshot: DataSnapshot, key: String, default: Long): Long {
        val v = snapshot.child(key).value ?: return default
        return when (v) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: default
            else -> default
        }
    }

    /** Lắng nghe cấu hình thời gian đếm ngược sự cố / bom hàng (Lần 1 và Lần 2) */
    fun observeCountdownConfig(): Flow<com.chuyen_de_2.foodorder.core.data.model.DeliveryCountdownConfig> = callbackFlow {
        // Lắng nghe đồng thời cả 3 node để đảm bảo tương thích mọi tầng rules:
        // 1. orders/system_countdown_config: .read: auth != null, .write: auth != null (Tất cả tài khoản đều truy cập được)
        // 2. delivery_time_frames/countdown_config: .read: true (Quyền đọc công khai)
        // 3. system_config/delivery_countdown: node hệ thống gốc
        val refOrders = database.getReference("orders").child("system_countdown_config")
        val refTimeFrames = database.getReference("delivery_time_frames").child("countdown_config")
        val refSysConfig = database.getReference("system_config").child("delivery_countdown")

        var latestVersion = 0L

        fun handleSnapshot(snapshot: DataSnapshot) {
            if (snapshot.exists()) {
                val round1 = parseSafeInt(snapshot, "round1Minutes", 15)
                val round2 = parseSafeInt(snapshot, "round2Minutes", 15)
                val updatedAt = parseSafeLong(snapshot, "updatedAt", 0L)
                if (updatedAt >= latestVersion) {
                    latestVersion = updatedAt
                    trySend(
                        com.chuyen_de_2.foodorder.core.data.model.DeliveryCountdownConfig(
                            round1Minutes = round1,
                            round2Minutes = round2,
                            updatedAt = updatedAt
                        )
                    )
                }
            }
        }

        val l1 = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = handleSnapshot(snapshot)
            override fun onCancelled(error: DatabaseError) {}
        }
        val l2 = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = handleSnapshot(snapshot)
            override fun onCancelled(error: DatabaseError) {}
        }
        val l3 = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = handleSnapshot(snapshot)
            override fun onCancelled(error: DatabaseError) {}
        }

        refOrders.addValueEventListener(l1)
        refTimeFrames.addValueEventListener(l2)
        refSysConfig.addValueEventListener(l3)

        awaitClose {
            refOrders.removeEventListener(l1)
            refTimeFrames.removeEventListener(l2)
            refSysConfig.removeEventListener(l3)
        }
    }

    /** Lấy 1 lần cấu hình thời gian đếm ngược */
    suspend fun getCountdownConfig(): com.chuyen_de_2.foodorder.core.data.model.DeliveryCountdownConfig {
        return try {
            val snapOrders = try { database.getReference("orders").child("system_countdown_config").get().await() } catch (_: Exception) { null }
            val snapFrames = if (snapOrders != null && snapOrders.exists()) snapOrders else try { database.getReference("delivery_time_frames").child("countdown_config").get().await() } catch (_: Exception) { null }
            val snapshot = if (snapFrames != null && snapFrames.exists()) snapFrames else database.getReference("system_config").child("delivery_countdown").get().await()
            val round1 = parseSafeInt(snapshot, "round1Minutes", 15)
            val round2 = parseSafeInt(snapshot, "round2Minutes", 15)
            val updatedAt = parseSafeLong(snapshot, "updatedAt", 0L)
            com.chuyen_de_2.foodorder.core.data.model.DeliveryCountdownConfig(
                round1Minutes = round1,
                round2Minutes = round2,
                updatedAt = updatedAt
            )
        } catch (_: Exception) {
            com.chuyen_de_2.foodorder.core.data.model.DeliveryCountdownConfig()
        }
    }

    /** Cập nhật cấu hình thời gian đếm ngược giao hàng */
    suspend fun updateCountdownConfig(round1Minutes: Int, round2Minutes: Int): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "round1Minutes" to round1Minutes,
                "round2Minutes" to round2Minutes,
                "updatedAt" to System.currentTimeMillis()
            )
            // Cập nhật cả 3 node để đảm bảo mọi client đều đọc được tức thì
            try { database.getReference("orders").child("system_countdown_config").updateChildren(updates).await() } catch (_: Exception) {}
            try { database.getReference("delivery_time_frames").child("countdown_config").updateChildren(updates).await() } catch (_: Exception) {}
            try { database.getReference("system_config").child("delivery_countdown").updateChildren(updates).await() } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lấy danh sách chi nhánh do BOSSMERCHANT quản lý (Realtime Flow) */
    fun getStoresByBoss(bossUid: String): Flow<List<Store>> = callbackFlow {
        if (bossUid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val storesRef = database.getReference("stores")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Store>()
                for (child in snapshot.children) {
                    val store = child.getValue(Store::class.java)?.copy(id = child.key.orEmpty())
                    if (store != null) {
                        val bId = child.child("bossId").getValue(String::class.java).orEmpty()
                        val mId = child.child("merchantId").getValue(String::class.java)
                            ?: child.child("ownerId").getValue(String::class.java).orEmpty()
                        if (bId == bossUid || (bId.isBlank() && mId == bossUid)) {
                            list.add(store)
                        }
                    }
                }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        storesRef.addValueEventListener(listener)
        awaitClose { storesRef.removeEventListener(listener) }
    }

    /** Tạo chi nhánh mới cho BOSSMERCHANT */
    suspend fun createBranchStore(
        bossUid: String,
        storeName: String,
        address: String,
        district: String = "",
        phone: String = "",
        latitude: Double = 0.0,
        longitude: Double = 0.0
    ): Result<String> {
        return try {
            val primaryStoreId = resolveStoreIdForUser(bossUid)
            val primaryStore = if (primaryStoreId.isNotBlank()) getStoreOnce(primaryStoreId) else null

            // Fallback GPS nếu chưa có tọa độ từ map picker
            val (resolvedLat, resolvedLng) = if (latitude != 0.0 && longitude != 0.0) {
                latitude to longitude
            } else {
                val coords = com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.DISTRICT_COORDINATES[district.trim()]
                    ?: com.chuyen_de_2.foodorder.core.util.GoogleMapUtils.DEFAULT_HCMC_LATLNG
                coords.latitude to coords.longitude
            }

            val storeRef = database.getReference("stores").push()
            val newStoreId = storeRef.key ?: "store_${System.currentTimeMillis()}"
            val newStore = Store(
                id = newStoreId,
                bossId = bossUid,
                merchantId = "",
                merchantName = "",
                name = storeName.trim(),
                address = address.trim(),
                district = district.trim(),
                phone = phone.trim(),
                latitude = resolvedLat,
                longitude = resolvedLng,
                ownerName = primaryStore?.ownerName.orEmpty(),
                cccdNumber = primaryStore?.cccdNumber.orEmpty(),
                bankName = primaryStore?.bankName?.ifBlank { "MB Bank" } ?: "MB Bank",
                bankAccountNumber = primaryStore?.bankAccountNumber.orEmpty(),
                bankAccountName = primaryStore?.bankAccountName.orEmpty(),
                bankBin = primaryStore?.bankBin?.ifBlank { "970422" } ?: "970422",
                storeStatus = Store.StoreStatus.OPEN,
                isVerified = true,
                ratingAvg = 5.0f,
                deliveryTime = "20-30 phút",
                category = "Món chính",
                imageUrl = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=400"
            )
            storeRef.setValue(newStore).await()
            database.getReference("boss_stores").child(bossUid).child(newStoreId).setValue(true).await()
            Result.success(newStoreId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Phân công quản lý vào chi nhánh mới (tự động gỡ khỏi chi nhánh cũ và đồng bộ 2 chiều) */
    suspend fun assignMerchantToBranch(
        merchantUid: String,
        merchantName: String,
        targetStoreId: String,
        previousStoreId: String = ""
    ): Result<Unit> {
        return try {
            // 1. Nếu gán vào targetStoreId, kiểm tra xem targetStoreId đang có quản lý cũ nào khác không để gỡ storeId của họ
            if (targetStoreId.isNotBlank()) {
                val targetSnap = database.getReference("stores").child(targetStoreId).get().await()
                val oldManagerUid = targetSnap.child("merchantId").getValue(String::class.java).orEmpty()
                val bossUid = targetSnap.child("bossId").getValue(String::class.java).orEmpty()
                if (oldManagerUid.isNotBlank() && oldManagerUid != merchantUid && oldManagerUid != bossUid) {
                    database.getReference("users").child(oldManagerUid).child("storeId").setValue("").await()
                }
            }

            // 2. Quét toàn bộ bảng stores: bất kỳ store nào đang gán merchantUid này (ngoại trừ targetStoreId) đều phải xóa sạch merchantId
            if (merchantUid.isNotBlank()) {
                val storesSnap = try { database.getReference("stores").get().await() } catch (_: Exception) { null }
                if (storesSnap != null) {
                    for (child in storesSnap.children) {
                        val sKey = child.key.orEmpty()
                        if (sKey.isNotBlank() && sKey != targetStoreId) {
                            val mId = child.child("merchantId").getValue(String::class.java).orEmpty()
                            val oId = child.child("ownerId").getValue(String::class.java).orEmpty()
                            if (mId == merchantUid || oId == merchantUid) {
                                database.getReference("stores").child(sKey).updateChildren(
                                    mapOf("merchantId" to "", "ownerId" to "", "merchantName" to "")
                                ).await()
                            }
                        }
                    }
                }
            }

            // 3. Cập nhật gán mới hoặc gỡ
            if (targetStoreId.isNotBlank() && merchantUid.isNotBlank()) {
                database.getReference("stores").child(targetStoreId).updateChildren(
                    mapOf("merchantId" to merchantUid, "ownerId" to merchantUid, "merchantName" to merchantName)
                ).await()
                database.getReference("users").child(merchantUid).child("storeId").setValue(targetStoreId).await()
            } else if (merchantUid.isNotBlank()) {
                database.getReference("users").child(merchantUid).child("storeId").setValue("").await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Gỡ quản lý khỏi chi nhánh (đồng bộ xóa cả trên stores lẫn trên users) */
    suspend fun unassignMerchantFromBranch(merchantUid: String, storeId: String): Result<Unit> {
        return try {
            var actualMerchantUid = merchantUid
            var bossUid = ""
            if (storeId.isNotBlank()) {
                val storeSnap = database.getReference("stores").child(storeId).get().await()
                if (actualMerchantUid.isBlank()) {
                    actualMerchantUid = storeSnap.child("merchantId").getValue(String::class.java).orEmpty()
                }
                bossUid = storeSnap.child("bossId").getValue(String::class.java).orEmpty()
                database.getReference("stores").child(storeId).updateChildren(
                    mapOf("merchantId" to "", "ownerId" to "", "merchantName" to "")
                ).await()
            }
            if (actualMerchantUid.isNotBlank() && actualMerchantUid != bossUid) {
                val userOldStoreId = database.getReference("users").child(actualMerchantUid).child("storeId").get().await()
                    .getValue(String::class.java).orEmpty()
                if (storeId.isBlank() && userOldStoreId.isNotBlank()) {
                    database.getReference("stores").child(userOldStoreId).updateChildren(
                        mapOf("merchantId" to "", "ownerId" to "", "merchantName" to "")
                    ).await()
                }
                database.getReference("users").child(actualMerchantUid).child("storeId").setValue("").await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Khóa hoặc Mở khóa tài khoản Merchant bởi Boss */
    suspend fun toggleMerchantLockByBoss(merchant: User, shouldLock: Boolean): Result<Unit> {
        return try {
            val status = if (shouldLock) User.Status.LOCKED else User.Status.ACTIVE
            val reason = if (shouldLock) "Tài khoản Quản lý đã bị Chủ chuỗi (Boss) tạm khóa." else ""

            // Nếu khóa -> tự động gỡ khỏi chi nhánh đang phụ trách
            if (shouldLock && merchant.storeId.isNotBlank()) {
                database.getReference("stores").child(merchant.storeId).updateChildren(
                    mapOf("merchantId" to "", "ownerId" to "", "merchantName" to "")
                ).await()
                database.getReference("users").child(merchant.uid).child("storeId").setValue("").await()
            }

            updateUserStatus(merchant.uid, status, !shouldLock, reason)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Xóa tài khoản Merchant do Boss quản lý */
    suspend fun deleteMerchantByBoss(merchantUid: String, storeId: String = ""): Result<Unit> {
        return try {
            if (storeId.isNotBlank()) {
                database.getReference("stores").child(storeId).updateChildren(
                    mapOf("merchantId" to "", "ownerId" to "", "merchantName" to "")
                ).await()
            }
            database.getReference("users").child(merchantUid).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Gán tài khoản MERCHANT cho một Chi nhánh (hoặc gỡ nếu merchantUid rỗng) */
    suspend fun assignMerchantToStore(storeId: String, merchantUid: String?, merchantName: String = ""): Result<Unit> {
        return if (merchantUid.isNullOrBlank()) {
            unassignMerchantFromBranch("", storeId)
        } else {
            assignMerchantToBranch(
                merchantUid = merchantUid,
                merchantName = merchantName,
                targetStoreId = storeId,
                previousStoreId = ""
            )
        }
    }

    /** Lấy danh sách các tài khoản MERCHANT để Boss gán vào quán */
    fun getMerchantsForBoss(bossUid: String): Flow<List<User>> = callbackFlow {
        val usersRef = database.getReference("users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<User>()
                for (child in snapshot.children) {
                    val role = child.child("role").getValue(String::class.java).orEmpty().uppercase()
                    val bId = child.child("bossId").getValue(String::class.java).orEmpty()
                    if (role == User.Role.MERCHANT || role == User.Role.MANAGER) {
                        if (bId.isBlank() || bId == bossUid) {
                            child.getValue(User::class.java)?.let { list.add(it) }
                        }
                    }
                }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        usersRef.addValueEventListener(listener)
        awaitClose { usersRef.removeEventListener(listener) }
    }

    /** Tạo tài khoản MERCHANT trực tiếp bởi Boss bằng Firebase Auth phụ (giữ nguyên session Boss) */
    suspend fun createMerchantByBoss(
        context: android.content.Context,
        bossUid: String,
        email: String,
        password: String,
        name: String,
        phone: String,
        storeId: String = ""
    ): Result<User> {
        return try {
            val secondaryApp = try {
                com.google.firebase.FirebaseApp.getInstance("MerchantCreator")
            } catch (e: Exception) {
                val options = com.google.firebase.FirebaseApp.getInstance().options
                com.google.firebase.FirebaseApp.initializeApp(context.applicationContext, options, "MerchantCreator")
            }
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
            val secondaryDatabase = FirebaseDatabase.getInstance(secondaryApp)
            val authResult = try {
                secondaryAuth.createUserWithEmailAndPassword(email.trim(), password.trim()).await()
            } catch (_: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                return Result.failure(Exception("Email '$email' đã được đăng ký trên hệ thống. Không thể ghi đè tài khoản đã tồn tại, vui lòng sử dụng email khác!"))
            }
            val merchantUid = authResult.user?.uid ?: return Result.failure(Exception("Không lấy được UID của quản lý mới"))

            val updates = mutableMapOf<String, Any>(
                "uid" to merchantUid,
                "name" to name.trim(),
                "email" to email.trim(),
                "phone" to phone.trim(),
                "role" to User.Role.MERCHANT,
                "roleId" to 3,
                "storeId" to storeId,
                "bossId" to bossUid,
                "status" to User.Status.ACTIVE,
                "isActive" to true,
                "emailVerified" to true,
                "registrationVerified" to true
            )
            // Ghi trực tiếp bằng phiên của tài khoản mới để thỏa mãn điều kiện auth.uid == $userId
            secondaryDatabase.getReference("users").child(merchantUid).setValue(updates).await()
            secondaryAuth.signOut()
            if (storeId.isNotBlank()) {
                assignMerchantToStore(storeId, merchantUid, name.trim())
            }
            val merchantUser = User(
                uid = merchantUid,
                name = name.trim(),
                email = email.trim(),
                phone = phone.trim(),
                role = User.Role.MERCHANT,
                roleId = 3,
                storeId = storeId,
                status = User.Status.ACTIVE,
                isActive = true,
                emailVerified = true
            )
            Result.success(merchantUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Thiết lập chi nhánh đang active trên Realtime DB cho Boss */
    suspend fun setActiveStoreForBoss(bossUid: String, storeId: String): Result<Unit> {
        return try {
            database.getReference("users").child(bossUid).child("storeId").setValue(storeId).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Đánh dấu một store thuộc quyền sở hữu của Boss */
    suspend fun markStoreOwnedByBoss(bossUid: String, storeId: String): Result<Unit> {
        return try {
            database.getReference("stores").child(storeId).child("bossId").setValue(bossUid).await()
            database.getReference("boss_stores").child(bossUid).child(storeId).setValue(true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Xóa chi nhánh active để Boss quay về màn hình quản lý chi nhánh */
    suspend fun clearActiveStoreForBoss(bossUid: String): Result<Unit> {
        return try {
            database.getReference("users").child(bossUid).child("storeId").setValue("").await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
