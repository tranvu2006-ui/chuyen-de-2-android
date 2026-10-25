package com.chuyen_de_2.foodorder.core.data.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName

/**
 * Danh mục ngành hàng thực phẩm
 * Giao diện: khách/b1.html — Grid danh mục (Cơm, Trà sữa, Phở, Gà...)
 * Mapping: Bảng 4 - categories (database_schema.sql)
 */
@IgnoreExtraProperties
data class Category(
    @SerializedName("categoryId", alternate = ["id"])
    val categoryId: String = "",

    @SerializedName("categoryName", alternate = ["name"])
    val categoryName: String = "",

    @SerializedName("iconUrl", alternate = ["icon"])
    val iconUrl: String = "",

    @SerializedName("displayOrder")
    val displayOrder: Int = 0,

    @SerializedName("isActive")
    val isActive: Boolean = true
) {
    @get:Exclude
    val id: String get() = categoryId

    @get:Exclude
    val name: String get() = categoryName

    @get:Exclude
    val icon: String get() = iconUrl
}
