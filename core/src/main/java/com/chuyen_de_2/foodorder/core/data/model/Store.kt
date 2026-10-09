package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Cửa hàng / Quán ăn — lưu tại: stores/{storeId}
 * Giao diện: Quán/5.html — Dashboard quán, Quán/8.html — Hồ sơ CCCD
 * 4 trạng thái: PENDING → OPEN / LOCKED / CLOSED
 * Mapping: Bảng 5 - stores (database_schema.sql)
 */
data class Store(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("merchantId", alternate = ["ownerId"])
    val merchantId: String = "",

    @SerializedName("name")
    val name: String = "",

    @SerializedName("phone")
    val phone: String = "",

    @SerializedName("address")
    val address: String = "",

    @SerializedName("district")
    val district: String = "",

    @SerializedName("latitude")
    val latitude: Double = 0.0,

    @SerializedName("longitude")
    val longitude: Double = 0.0,

    @SerializedName("imageUrl")
    val imageUrl: String = "",

    @SerializedName("storeStatus")
    val storeStatus: String = StoreStatus.PENDING,

    @SerializedName("statusId")
    val statusId: Int = 1,

    @SerializedName("cccdNumber")
    val cccdNumber: String = "",

    @SerializedName("cccdFrontUrl")
    val cccdFrontUrl: String = "",

    @SerializedName("cccdBackUrl")
    val cccdBackUrl: String = "",

    @SerializedName("cccdHoldUrl")
    val cccdHoldUrl: String = "",

    @SerializedName("cccdPortraitUrl")
    val cccdPortraitUrl: String = "",

    @SerializedName("penaltyTierId")
    val penaltyTierId: Int = 1,

    @SerializedName("penaltyTier")
    val penaltyTier: String = PenaltyTier.NONE,

    @SerializedName("penaltyReason")
    val penaltyReason: String = "",

    @SerializedName("ratingAvg", alternate = ["rating"])
    val ratingAvg: Float = 5.0f,

    @SerializedName("deliveryTimeFrameId")
    val deliveryTimeFrameId: Int = 2,

    @SerializedName("deliveryTime")
    val deliveryTime: String = "20-30 phút",

    @SerializedName("category")
    val category: String = ""
) {
    /** Thuộc tính phụ trợ tương thích UI */
    val isOpen: Boolean
        get() = storeStatus.equals(StoreStatus.OPEN, ignoreCase = true)

    val rating: Float
        get() = ratingAvg
    /** Trạng thái quán ăn — Admin duyệt */
    object StoreStatus {
        const val PENDING = "PENDING"
        const val OPEN = "OPEN"
        const val LOCKED = "LOCKED"
        const val CLOSED = "CLOSED"
    }

    /** Chế tài xử phạt — Admin áp đặt */
    object PenaltyTier {
        const val NONE = "NONE"
        const val WARN = "WARN"
        const val HIDE = "HIDE"
        const val LOCK = "LOCK"
    }
}
