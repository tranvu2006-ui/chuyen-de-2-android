package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Hồ sơ khách hàng — lưu tại: customers/{customerId}
 * Giao diện: khách/b11.html — Trang cá nhân + Thẻ Điểm Uy Tín
 * Mapping: Bảng 2 - customers (database_schema.sql)
 */
data class Customer(
    @SerializedName("customerId")
    val customerId: String = "",

    @SerializedName("avatarUrl")
    val avatarUrl: String = "",

    @SerializedName("trustScore")
    val trustScore: Int = 0,

    @SerializedName("totalOrders")
    val totalOrders: Int = 0,

    @SerializedName("completedOrders")
    val completedOrders: Int = 0,

    @SerializedName("boomOrders")
    val boomOrders: Int = 0
)
