package com.chuyen_de_2.foodorder.core.data.model

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName
import com.google.gson.annotations.SerializedName

/**
 * Model Mã Giảm Giá / Voucher của Quán ăn và Hệ Thống
 * Node lưu trữ trên Firebase Realtime Database: /vouchers/{storeId}/{voucherId}
 */
@IgnoreExtraProperties
data class Voucher(
    @get:PropertyName("voucherId")
    @set:PropertyName("voucherId")
    @SerializedName("voucherId")
    var voucherId: String = "",

    @get:PropertyName("storeId")
    @set:PropertyName("storeId")
    @SerializedName("storeId")
    var storeId: String = "",

    @get:PropertyName("code")
    @set:PropertyName("code")
    @SerializedName("code")
    var code: String = "",

    @get:PropertyName("discountAmount")
    @set:PropertyName("discountAmount")
    @SerializedName("discountAmount")
    var discountAmount: Long = 0L,

    @get:PropertyName("minOrderAmount")
    @set:PropertyName("minOrderAmount")
    @SerializedName("minOrderAmount")
    var minOrderAmount: Long = 0L,

    @get:PropertyName("title")
    @set:PropertyName("title")
    @SerializedName("title")
    var title: String = "",

    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    @SerializedName("isActive")
    var isActive: Boolean = true,

    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    @SerializedName("createdAt")
    var createdAt: Long = System.currentTimeMillis()
)
