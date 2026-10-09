package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Gói combo khuyến mãi — lưu tại: combos/{comboId}
 * Giao diện: Quán/3.html — Tạo gói Combo món ăn
 * Mapping: Bảng 8 - combos (database_schema.sql)
 */
data class Combo(
    @SerializedName("comboId")
    val comboId: String = "",

    @SerializedName("storeId")
    val storeId: String = "",

    @SerializedName("comboName")
    val comboName: String = "",

    @SerializedName("description")
    val description: String = "",

    @SerializedName("comboPrice")
    val comboPrice: Long = 0,

    @SerializedName("imageUrl")
    val imageUrl: String = "",

    @SerializedName("dishIds")
    val dishIds: List<String> = emptyList(),

    @SerializedName("items")
    val items: List<ComboItem> = emptyList(),

    @SerializedName("isActive")
    val isActive: Boolean = true
)

/**
 * Chi tiết món trong Combo (Bảng trung gian N-N)
 * Mapping: Bảng 9 - combo_items (database_schema.sql)
 */
data class ComboItem(
    @SerializedName("comboItemId")
    val comboItemId: String = "",

    @SerializedName("comboId")
    val comboId: String = "",

    @SerializedName("dishId")
    val dishId: String = "",

    @SerializedName("quantity")
    val quantity: Int = 1
)
