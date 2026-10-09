package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Nhà hàng — dữ liệu tĩnh lấy từ REST API qua Retrofit
 * API Contract (Section 5 - doc-adr): GET /api/v1/restaurants
 */
data class Restaurant(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("name")
    val name: String = "",

    @SerializedName("address")
    val address: String = "",

    @SerializedName("imageUrl")
    val imageUrl: String = "",

    @SerializedName("rating")
    val rating: Float = 0f,

    @SerializedName("category")
    val category: String = "",

    @SerializedName("deliveryTime")
    val deliveryTime: String = "30-45 phút",

    @SerializedName("isOpen")
    val isOpen: Boolean = true
)
