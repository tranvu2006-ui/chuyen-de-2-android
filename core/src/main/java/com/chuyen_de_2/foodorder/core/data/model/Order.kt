package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Đơn hàng — lưu tại: orders/{orderId}
 * Giao diện: khách/b9.html — Theo dõi đơn + Mã QR Token
 *            Quán/4.html — Tiếp nhận & Duyệt đơn
 * Vòng đời: PENDING → PREPARING → ASSIGNED → DELIVERING → COMPLETED / BOOM / CANCELLED
 * Mapping: Bảng 12 - orders (database_schema.sql)
 */
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

    @SerializedName("completedAt")
    val completedAt: Long? = null
) {
    val effectiveStoreId: String
        get() = storeId.ifBlank { shopId }

    val effectiveShopName: String
        get() = shopName.ifBlank { storeId }

    val effectiveDriverId: String
        get() = driverId ?: shipperId ?: ""

    val effectiveDriverName: String
        get() = shipperName

    val effectiveStatus: String
        get() = status.ifBlank { orderStatus }

    val effectiveAddress: String
        get() = deliveryAddress.ifBlank { address }

    val effectiveTotal: Long
        get() = if (totalPayment > 0) totalPayment else total

    /** Đơn online (VNPay/VietQR) đã tạo nhưng chưa nhận tiền. */
    val isAwaitingOnlinePayment: Boolean
        get() = paymentMethod != PaymentMethod.COD &&
            totalPayment == 0L &&
            (status == Status.PENDING || orderStatus == Status.PENDING)

    val effectiveTimestamp: Long
        get() = if (createdAt > 0) createdAt else timestamp

    /** Trạng thái đơn hàng — Vòng đời đầy đủ các trạng thái */
    object Status {
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

    /** Phương thức thanh toán */
    object PaymentMethod {
        const val COD = "COD"
        const val BANKING = "BANKING"
        const val VNPAY = "VNPAY"
    }
}

/**
 * Chi tiết món trong đơn hàng
 * Giao diện: khách/b7.html — Giỏ hàng
 * Mapping: Bảng 13 - order_items (database_schema.sql)
 */
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
    val dishName: String get() = name
}
