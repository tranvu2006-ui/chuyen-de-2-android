package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Món ăn — dữ liệu tĩnh lấy từ REST API qua Retrofit
 * API Contract (Section 5 - doc-adr): GET /api/v1/shops/{shopId}/menu
 */
data class MenuItem(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("name")
    val name: String = "",

    @SerializedName("description")
    val description: String = "",

    @SerializedName("price")
    val price: Long = 0,

    @SerializedName("imageUrl")
    val imageUrl: String = "",

    @SerializedName("category")
    val category: String = ""
)
