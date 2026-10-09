package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Danh mục ngành hàng thực phẩm
 * Giao diện: khách/b1.html — Grid danh mục (Cơm, Trà sữa, Phở, Gà...)
 * Mapping: Bảng 4 - categories (database_schema.sql)
 */
data class Category(
    @SerializedName("categoryId")
    val categoryId: String = "",

    @SerializedName("categoryName")
    val categoryName: String = "",

    @SerializedName("iconUrl")
    val iconUrl: String = "",

    @SerializedName("displayOrder")
    val displayOrder: Int = 0,

    @SerializedName("isActive")
    val isActive: Boolean = true
)
