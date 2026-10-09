package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Món ăn — dữ liệu lấy từ REST API
 * Giao diện: khách/b5.html — Chi tiết menu quán
 * Mapping: Bảng 6 - dishes (database_schema.sql)
 */
data class Dish(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("storeId")
    val storeId: String = "",

    @SerializedName("categoryId")
    val categoryId: String = "",

    @SerializedName("name")
    val name: String = "",

    @SerializedName("description")
    val description: String = "",

    @SerializedName("imageUrl")
    val imageUrl: String = "",

    @SerializedName("basePrice", alternate = ["price"])
    val basePrice: Long = 0,

    @SerializedName("isAvailable")
    val isAvailable: Boolean = true,

    @SerializedName("isHidden")
    val isHidden: Boolean = false,

    @SerializedName("soldCount")
    val soldCount: Int = 0,

    @SerializedName("ratingAvg")
    val ratingAvg: Float = 5.0f,

    @SerializedName("reviewCount")
    val reviewCount: Int = 0,

    @SerializedName("category")
    val category: String = ""
) {
    /** Thuộc tính phụ trợ tương thích UI */
    val price: Long
        get() = basePrice
}
