package com.chuyen_de_2.foodorder.core.data.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName

/**
 * Đơn hàng — lưu tại: orders/{orderId}
 * Giao diện: khách/b9.html — Theo dõi đơn + Mã QR Token
 *            Quán/4.html — Tiếp nhận & Duyệt đơn
 * Vòng đời: PENDING → PREPARING → ASSIGNED → DELIVERING → COMPLETED / BOOM / CANCELLED
 * Mapping: Bảng 12 - orders (database_schema.sql)
 */
@IgnoreExtraProperties
data class Order(
    @SerializedName("orderId")
    val orderId: String = "",

    @SerializedName("customerId")
    val customerId: String = "",

    @SerializedName("customerName")
    val customerName: String = "",

    @SerializedName("customerPhone")
    val customerPhone: String = "",

    @SerializedName("storeId")
    val storeId: String = "",

    @SerializedName("shopId")
    val shopId: String = "",

    @SerializedName("shopName")
    val shopName: String = "",

    @SerializedName("shopPhone")
    val shopPhone: String = "",

    @SerializedName("driverId")
    var driverId: String? = null,

    @SerializedName("shipperId")
    var shipperId: String? = null,

    @SerializedName("shipperName")
    var shipperName: String = "",

    @SerializedName("shipperPhone")
    var shipperPhone: String = "",

    @SerializedName("address")
    val address: String = "",

    @SerializedName("deliveryAddress")
    val deliveryAddress: String = "",

    @SerializedName("total")
    val total: Long = 0,

    @SerializedName("subtotalAmount")
    val subtotalAmount: Long = 0,

    @SerializedName("shippingFee")
    val shippingFee: Long = 0,

    @SerializedName("totalPayment")
    val totalPayment: Long = 0,

    @SerializedName("paymentMethod")
    val paymentMethod: String = PaymentMethod.COD,

    @SerializedName("paymentMode")
    val paymentMode: String = PaymentMode.FULL,

    @SerializedName("depositAmount")
    val depositAmount: Long = 0,

    @SerializedName("remainAmount")
    val remainAmount: Long = 0,

    @SerializedName("isDepositPaid")
    val isDepositPaid: Boolean = false,

    @SerializedName("isPaid")
    val isPaid: Boolean = false,

    @SerializedName("status")
    var status: String = Status.PENDING,

    @SerializedName("orderStatus")
    var orderStatus: String = Status.PENDING,

    @SerializedName("deliveryToken")
    val deliveryToken: String = "",

    @SerializedName("items")
    val items: Map<String, OrderItem> = emptyMap(),

    @SerializedName("note")
    val note: String = "",

    @SerializedName("timestamp")
    val timestamp: Long = 0,

    @SerializedName("createdAt")
    val createdAt: Long = 0,

    @SerializedName("updatedAt")
    val updatedAt: Long? = null,

    @SerializedName("completedAt")
    val completedAt: Long? = null
) {
    @get:Exclude
    val effectiveStoreId: String
        get() = storeId.ifBlank { shopId }

    @get:Exclude
    val effectiveShopName: String
        get() = shopName.ifBlank { storeId }

    @get:Exclude
    val effectiveDriverId: String
        get() = driverId ?: shipperId ?: ""

    @get:Exclude
    val effectiveDriverName: String
        get() = shipperName

    @get:Exclude
    val effectiveStatus: String
        get() = status.ifBlank { orderStatus }

    @get:Exclude
    val effectiveAddress: String
        get() = deliveryAddress.ifBlank { address }

    @get:Exclude
    val effectiveTotal: Long
        get() = if (totalPayment > 0) totalPayment else total

    @get:Exclude
    val totalPrice: Long
        get() = effectiveTotal

    @get:Exclude
    val effectiveTimestamp: Long
        get() = if (createdAt > 0) createdAt else timestamp

    @get:Exclude
    val orderCode: String
        get() = if (orderId.isNotBlank()) "#ORD_OISHI_${orderId.takeLast(6).uppercase()}" else ""

    /** Trạng thái đơn hàng — Vòng đời đầy đủ các trạng thái */
    object Status {
        const val AWAITING_PAYMENT = "AWAITING_PAYMENT"
        const val PENDING = "PENDING"
        const val PREPARING = "PREPARING"
        const val READY = "READY"
        const val ASSIGNED = "ASSIGNED"
        const val ARRIVED_AT_SHOP = "ARRIVED_AT_SHOP"
        const val DELIVERING = "DELIVERING"
        const val ARRIVED_AT_CUSTOMER = "ARRIVED_AT_CUSTOMER"
        const val COMPLETED = "COMPLETED"
        const val BOOM = "BOOM"
        const val CANCELLED = "CANCELLED"
    }

    object OrderStatus {
        const val AWAITING_PAYMENT = "AWAITING_PAYMENT"
        const val PENDING = "PENDING"
        const val PREPARING = "PREPARING"
        const val READY = "READY"
        const val ASSIGNED = "ASSIGNED"
        const val ARRIVED_AT_SHOP = "ARRIVED_AT_SHOP"
        const val DELIVERING = "DELIVERING"
        const val ARRIVED_AT_CUSTOMER = "ARRIVED_AT_CUSTOMER"
        const val COMPLETED = "COMPLETED"
        const val BOOM = "BOOM"
        const val CANCELLED = "CANCELLED"
    }

    /** Chế độ thanh toán: Trả thẳng 100%, Cọc 20%, hoặc COD (chuẩn b8.html) */
    object PaymentMode {
        const val FULL = "FULL"
        const val DEPOSIT = "DEPOSIT"
        const val COD = "COD"
    }

    /** Phương thức thanh toán */
    object PaymentMethod {
        const val COD = "COD"
        const val BANKING = "BANKING"
        const val DEPOSIT = "DEPOSIT"
    }
}

/**
 * Chi tiết món trong đơn hàng
 * Giao diện: khách/b7.html — Giỏ hàng
 * Mapping: Bảng 13 - order_items (database_schema.sql)
 */
@IgnoreExtraProperties
data class OrderItem(
    @SerializedName("itemId")
    val itemId: String = "",

    @SerializedName("orderItemId")
    val orderItemId: String = "",

    @SerializedName("dishId")
    val dishId: String = "",

    @SerializedName("name")
    val name: String = "",

    @SerializedName("price")
    val price: Long = 0,

    @SerializedName("quantity")
    val quantity: Int = 0,

    @SerializedName("unitPrice")
    val unitPrice: Long = 0,

    @SerializedName("selectedOptions")
    val selectedOptions: Map<String, Any>? = null,

    @SerializedName("imageUrl")
    val imageUrl: String = "",

    @SerializedName("itemSubtotal")
    val itemSubtotal: Long = 0
) {
    @get:Exclude
    val dishName: String get() = name
}
