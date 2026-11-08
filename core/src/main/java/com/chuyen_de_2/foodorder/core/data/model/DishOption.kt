package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Tùy chọn tùy biến món ăn (ShopeeFood-style)
 * Giao diện: khách/b6.html — Modal tùy chọn Size, Đường, Đá, Topping
 * Mapping: Bảng 7 - dish_options (database_schema.sql)
 */
data class DishOption(
    @SerializedName("optionId")
    val optionId: String = "",

    @SerializedName("dishId")
    val dishId: String = "",

    @SerializedName("optionGroup")
    val optionGroup: String = "",

    @SerializedName("optionName")
    val optionName: String = "",

    @SerializedName("extraPrice")
    val extraPrice: Long = 0,

    @SerializedName("isDefault")
    val isDefault: Boolean = false
) {
    /** Nhóm tùy chọn */
    object Group {
        const val SIZE = "SIZE"
        const val SUGAR = "SUGAR"
        const val ICE = "ICE"
        const val TOPPING = "TOPPING"
    }
}
