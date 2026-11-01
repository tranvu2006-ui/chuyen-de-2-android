package com.chuyen_de_2.foodorder.core.data.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName

/**
 * Cửa hàng / Quán ăn — lưu tại: stores/{storeId}
 * Giao diện: Quán/5.html — Dashboard quán, Quán/8.html — Hồ sơ CCCD
 * 4 trạng thái: PENDING → OPEN / LOCKED / CLOSED
 * Mapping: Bảng 5 - stores (database_schema.sql)
 */
@IgnoreExtraProperties
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

    @get:com.google.firebase.database.PropertyName("imageUrl")
    @set:com.google.firebase.database.PropertyName("imageUrl")
    @SerializedName("imageUrl", alternate = ["avatarUrl", "logoUrl", "avatar", "image"])
    var imageUrl: String = "",

    @get:com.google.firebase.database.PropertyName("avatarUrl")
    @set:com.google.firebase.database.PropertyName("avatarUrl")
    @SerializedName("avatarUrl")
    var avatarUrl: String = "",

    @get:com.google.firebase.database.PropertyName("avatar")
    @set:com.google.firebase.database.PropertyName("avatar")
    @SerializedName("avatar")
    var avatar: String = "",

    @get:com.google.firebase.database.PropertyName("logoUrl")
    @set:com.google.firebase.database.PropertyName("logoUrl")
    @SerializedName("logoUrl")
    var logoUrl: String = "",

    @get:com.google.firebase.database.PropertyName("image")
    @set:com.google.firebase.database.PropertyName("image")
    @SerializedName("image")
    var image: String = "",

    @SerializedName("storeStatus")
    val storeStatus: String = StoreStatus.PENDING,

    @SerializedName("statusId")
    val statusId: Int = 1,

    @SerializedName("ownerName")
    val ownerName: String = "",

    @SerializedName("openHours")
    val openHours: String = "",

    @SerializedName("prepTime")
    val prepTime: String = "",

    @get:com.google.firebase.database.PropertyName("isVerified")
    @set:com.google.firebase.database.PropertyName("isVerified")
    @SerializedName("isVerified")
    var isVerified: Boolean = false,

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

    @SerializedName("reviewCount", alternate = ["ratingCount"])
    val reviewCount: Int = 0,

    @SerializedName("deliveryTimeFrameId")
    val deliveryTimeFrameId: Int = 2,

    @SerializedName("deliveryTime")
    val deliveryTime: String = "20-30 phút",

    @SerializedName("category")
    val category: String = "",

    @SerializedName("bankName")
    val bankName: String = "MB Bank",

    @SerializedName("bankAccountNumber", alternate = ["bankAccount", "bankNo"])
    val bankAccountNumber: String = "",

    @SerializedName("bankAccountName", alternate = ["bankOwner", "accountHolderName"])
    val bankAccountName: String = "",

    @SerializedName("bankBin")
    val bankBin: String = "970422",

    @SerializedName("storeCategories")
    val storeCategories: List<String> = emptyList(),

    @SerializedName("momoPhone")
    val momoPhone: String = "",

    @SerializedName("momoAccountName")
    val momoAccountName: String = "",

    @SerializedName("dishes")
    val dishes: Map<String, Dish> = emptyMap()
) {
    /** Thuộc tính phụ trợ tương thích UI */
    @get:Exclude
    val isOpen: Boolean
        get() = storeStatus.equals(StoreStatus.OPEN, ignoreCase = true)

    @get:Exclude
    val rating: Float
        get() = ratingAvg

    /** Trả về URL hoặc Base64 ảnh đại diện quán từ bất kỳ trường nào có dữ liệu */
    @get:Exclude
    val effectiveImageUrl: String
        get() = imageUrl.ifBlank { avatarUrl.ifBlank { avatar.ifBlank { logoUrl.ifBlank { image } } } }

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
